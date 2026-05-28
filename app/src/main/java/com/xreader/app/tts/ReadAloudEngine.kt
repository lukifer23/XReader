package com.xreader.app.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class ReadAloudState(
    val activeBookId: Long? = null,
    val initializing: Boolean = false,
    val playing: Boolean = false,
    val currentChunk: Int = 0,
    val totalChunks: Int = 0,
    val currentHeading: String? = null,
    val currentLocator: String? = null,
    val message: String? = null,
)

class ReadAloudEngine(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val utteranceCounter = AtomicLong(0)
    private val _state = MutableStateFlow(ReadAloudState())
    val state: StateFlow<ReadAloudState> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    private var activeSpeech: ActiveSpeech? = null
    private var pendingUtteranceId: String? = null

    suspend fun play(
        bookId: Long,
        chunks: List<ReadAloudChunk>,
        currentUnit: Int,
        speechRate: Float = DEFAULT_SPEECH_RATE,
    ) {
        withContext(Dispatchers.Main.immediate) {
            if (chunks.isEmpty()) {
                showMessage(bookId, "No readable text is indexed for this book. Repair the book from its details screen and try again.")
                return@withContext
            }

            stopInternal()
            _state.value = ReadAloudState(
                activeBookId = bookId,
                initializing = true,
                totalChunks = chunks.size
            )

            if (!ensureReady()) {
                _state.value = ReadAloudState(
                    activeBookId = bookId,
                    message = "Android text-to-speech is not available on this device."
                )
                return@withContext
            }

            setSpeechRateInternal(speechRate)
            val chunkIndex = ReadAloudPlanner.startIndex(chunks, currentUnit)
            val segments = ReadAloudPlanner.splitForSpeech(chunks[chunkIndex].text)
            if (segments.isEmpty()) {
                showMessage(bookId, "No readable text is indexed for this position.")
                return@withContext
            }
            activeSpeech = ActiveSpeech(
                bookId = bookId,
                chunks = chunks,
                chunkIndex = chunkIndex,
                segments = segments,
                segmentIndex = 0
            )
            speakCurrentSegment()
        }
    }

    fun setSpeechRate(value: Float) {
        scope.launch(Dispatchers.Main.immediate) {
            setSpeechRateInternal(value)
        }
    }

    fun stop(bookId: Long? = null) {
        scope.launch(Dispatchers.Main.immediate) {
            stopInternal(bookId)
        }
    }

    fun clearMessage(bookId: Long) {
        scope.launch(Dispatchers.Main.immediate) {
            if (_state.value.activeBookId == bookId) {
                _state.value = _state.value.copy(message = null)
            }
        }
    }

    fun showMessage(bookId: Long, message: String) {
        _state.value = ReadAloudState(activeBookId = bookId, message = message)
    }

    private suspend fun ensureReady(): Boolean {
        tts?.let { return true }

        val status = CompletableDeferred<Int>()
        val engine = TextToSpeech(appContext) { initStatus ->
            if (!status.isCompleted) status.complete(initStatus)
        }
        val initialized = withTimeoutOrNull(TTS_INIT_TIMEOUT_MILLIS) { status.await() } == TextToSpeech.SUCCESS
        if (!initialized) {
            engine.shutdown()
            return false
        }

        val languageResult = engine.setLanguage(Locale.getDefault())
        if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            val fallbackResult = engine.setLanguage(Locale.US)
            if (fallbackResult == TextToSpeech.LANG_MISSING_DATA || fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.shutdown()
                return false
            }
        }

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                scope.launch(Dispatchers.Main.immediate) {
                    if (utteranceId != null && utteranceId == pendingUtteranceId) {
                        advanceSpeech()
                    }
                }
            }

            @Deprecated("Deprecated in Android SDK")
            override fun onError(utteranceId: String?) {
                handleSpeechError(utteranceId)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                handleSpeechError(utteranceId)
            }
        })
        tts = engine
        return true
    }

    private fun setSpeechRateInternal(value: Float) {
        tts?.setSpeechRate(value.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE))
    }

    private fun handleSpeechError(utteranceId: String?) {
        scope.launch(Dispatchers.Main.immediate) {
            if (utteranceId != null && utteranceId != pendingUtteranceId) return@launch
            val bookId = activeSpeech?.bookId ?: _state.value.activeBookId
            stopInternal()
            if (bookId != null) {
                _state.value = ReadAloudState(
                    activeBookId = bookId,
                    message = "Read aloud stopped because Android text-to-speech could not speak this passage."
                )
            }
        }
    }

    private fun advanceSpeech() {
        val current = activeSpeech ?: return
        val nextSegment = current.segmentIndex + 1
        if (nextSegment < current.segments.size) {
            activeSpeech = current.copy(segmentIndex = nextSegment)
            speakCurrentSegment()
            return
        }

        val nextChunk = current.chunkIndex + 1
        if (nextChunk >= current.chunks.size) {
            stopInternal(current.bookId)
            return
        }

        val segments = ReadAloudPlanner.splitForSpeech(current.chunks[nextChunk].text)
        activeSpeech = current.copy(
            chunkIndex = nextChunk,
            segments = segments,
            segmentIndex = 0
        )
        speakCurrentSegment()
    }

    private fun speakCurrentSegment() {
        val current = activeSpeech ?: return
        val chunk = current.chunks.getOrNull(current.chunkIndex) ?: run {
            stopInternal(current.bookId)
            return
        }
        val segment = current.segments.getOrNull(current.segmentIndex) ?: run {
            advanceSpeech()
            return
        }
        val utteranceId = "xreader-${current.bookId}-${chunk.unitIndex}-${current.segmentIndex}-${utteranceCounter.incrementAndGet()}"
        pendingUtteranceId = utteranceId
        _state.value = ReadAloudState(
            activeBookId = current.bookId,
            playing = true,
            currentChunk = current.chunkIndex,
            totalChunks = current.chunks.size,
            currentHeading = chunk.heading,
            currentLocator = chunk.locator
        )
        val result = tts?.speak(segment, TextToSpeech.QUEUE_FLUSH, Bundle.EMPTY, utteranceId)
        if (result == TextToSpeech.ERROR) {
            handleSpeechError(utteranceId)
        }
    }

    private fun stopInternal(bookId: Long? = null) {
        if (bookId != null) {
            val activeBookId = activeSpeech?.bookId ?: _state.value.activeBookId
            if (activeBookId != null && activeBookId != bookId) return
        }
        tts?.stop()
        activeSpeech = null
        pendingUtteranceId = null
        _state.value = ReadAloudState()
    }

    private data class ActiveSpeech(
        val bookId: Long,
        val chunks: List<ReadAloudChunk>,
        val chunkIndex: Int,
        val segments: List<String>,
        val segmentIndex: Int,
    )

    companion object {
        private const val TTS_INIT_TIMEOUT_MILLIS = 5_000L
        private const val DEFAULT_SPEECH_RATE = 1.0f
        private const val MIN_SPEECH_RATE = 0.7f
        private const val MAX_SPEECH_RATE = 1.4f
    }
}
