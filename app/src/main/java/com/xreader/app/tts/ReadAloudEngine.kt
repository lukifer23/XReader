package com.xreader.app.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class ReadAloudVoiceOption(
    val name: String,
    val label: String,
    val localeTag: String,
    val quality: Int,
    val latency: Int,
)

data class ReadAloudState(
    val activeBookId: Long? = null,
    val initializing: Boolean = false,
    val playing: Boolean = false,
    val currentChunk: Int = 0,
    val currentUnit: Int = 0,
    val totalChunks: Int = 0,
    val currentHeading: String? = null,
    val currentLocator: String? = null,
    val sleepTimerEndsAtMillis: Long? = null,
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
    private val _voices = MutableStateFlow<List<ReadAloudVoiceOption>>(emptyList())
    val voices: StateFlow<List<ReadAloudVoiceOption>> = _voices.asStateFlow()

    private var tts: TextToSpeech? = null
    private var activeSpeech: ActiveSpeech? = null
    private var pendingUtteranceId: String? = null
    private var sleepTimerJob: Job? = null
    private var sleepTimerEndsAtMillis: Long? = null

    suspend fun play(
        bookId: Long,
        chunks: List<ReadAloudChunk>,
        currentUnit: Int,
        currentLocator: String? = null,
        speechRate: Float = DEFAULT_SPEECH_RATE,
        voiceName: String? = null,
        sleepTimerDurationMillis: Long? = null,
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

            setVoiceInternal(voiceName)
            setSpeechRateInternal(speechRate)
            val chunkIndex = ReadAloudPlanner.startIndex(
                chunks = chunks,
                currentUnit = currentUnit,
                currentLocator = currentLocator
            )
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
            scheduleSleepTimerInternal(sleepTimerDurationMillis)
            speakCurrentSegment()
        }
    }

    fun setSpeechRate(value: Float) {
        scope.launch(Dispatchers.Main.immediate) {
            setSpeechRateInternal(value)
        }
    }

    suspend fun refreshVoices(): List<ReadAloudVoiceOption> =
        withContext(Dispatchers.Main.immediate) {
            if (!ensureReady()) {
                _voices.value = emptyList()
                return@withContext emptyList()
            }
            updateVoiceOptions()
        }

    fun setVoice(voiceName: String?) {
        scope.launch(Dispatchers.Main.immediate) {
            if (ensureReady()) setVoiceInternal(voiceName)
        }
    }

    fun setSleepTimer(durationMillis: Long?) {
        scope.launch(Dispatchers.Main.immediate) {
            scheduleSleepTimerInternal(durationMillis)
        }
    }

    fun stop(bookId: Long? = null) {
        scope.launch(Dispatchers.Main.immediate) {
            stopInternal(bookId)
        }
    }

    suspend fun shutdown() {
        withContext(Dispatchers.Main.immediate) {
            stopInternal()
            tts?.shutdown()
            tts = null
            _voices.value = emptyList()
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
        _state.value = ReadAloudState(activeBookId = bookId, sleepTimerEndsAtMillis = sleepTimerEndsAtMillis, message = message)
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

        if (!setDefaultLanguage(engine)) {
            engine.shutdown()
            return false
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
        updateVoiceOptions(engine)
        return true
    }

    private fun setVoiceInternal(voiceName: String?) {
        val engine = tts ?: return
        if (voiceName.isNullOrBlank()) {
            setDefaultLanguage(engine)
            return
        }
        val voice = runCatching {
            engine.voices.orEmpty().firstOrNull { it.name == voiceName && !it.isNetworkConnectionRequired }
        }.getOrNull()
        if (voice != null) {
            engine.setVoice(voice)
        } else {
            setDefaultLanguage(engine)
        }
    }

    private fun setSpeechRateInternal(value: Float) {
        tts?.setSpeechRate(value.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE))
    }

    private fun scheduleSleepTimerInternal(durationMillis: Long?) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimerEndsAtMillis = null
        val bookId = activeSpeech?.bookId ?: _state.value.activeBookId
        if (bookId == null || durationMillis == null || durationMillis <= 0L) {
            _state.value = _state.value.copy(sleepTimerEndsAtMillis = null)
            return
        }
        val endsAt = System.currentTimeMillis() + durationMillis
        sleepTimerEndsAtMillis = endsAt
        _state.value = _state.value.copy(sleepTimerEndsAtMillis = endsAt)
        sleepTimerJob = scope.launch(Dispatchers.Main.immediate) {
            delay(durationMillis)
            val activeBookId = activeSpeech?.bookId ?: _state.value.activeBookId
            if (activeBookId != bookId) return@launch
            sleepTimerJob = null
            sleepTimerEndsAtMillis = null
            stopInternal(bookId = bookId, cancelSleepTimer = false)
            _state.value = ReadAloudState(
                activeBookId = bookId,
                message = "Read aloud stopped after the sleep timer."
            )
        }
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
            currentUnit = chunk.unitIndex,
            totalChunks = current.chunks.size,
            currentHeading = chunk.heading,
            currentLocator = chunk.locator,
            sleepTimerEndsAtMillis = sleepTimerEndsAtMillis
        )
        val result = tts?.speak(segment, TextToSpeech.QUEUE_FLUSH, Bundle.EMPTY, utteranceId)
        if (result == TextToSpeech.ERROR) {
            handleSpeechError(utteranceId)
        }
    }

    private fun stopInternal(bookId: Long? = null, cancelSleepTimer: Boolean = true) {
        if (bookId != null) {
            val activeBookId = activeSpeech?.bookId ?: _state.value.activeBookId
            if (activeBookId != null && activeBookId != bookId) return
        }
        if (cancelSleepTimer) {
            sleepTimerJob?.cancel()
            sleepTimerJob = null
            sleepTimerEndsAtMillis = null
        }
        tts?.stop()
        activeSpeech = null
        pendingUtteranceId = null
        _state.value = ReadAloudState()
    }

    private fun updateVoiceOptions(engine: TextToSpeech? = tts): List<ReadAloudVoiceOption> {
        val activeEngine = engine ?: return emptyList()
        val options = runCatching {
            activeEngine.voices.orEmpty()
                .filterNot { it.isNetworkConnectionRequired }
                .sortedWith(
                    compareBy<Voice> { it.locale?.getDisplayName(Locale.getDefault()).orEmpty() }
                        .thenBy { it.name }
                )
                .map {
                    ReadAloudVoiceOption(
                        name = it.name,
                        label = it.displayLabel(),
                        localeTag = it.locale?.toLanguageTag().orEmpty(),
                        quality = it.quality,
                        latency = it.latency
                    )
                }
        }.getOrDefault(emptyList())
        _voices.value = options
        return options
    }

    private fun setDefaultLanguage(engine: TextToSpeech): Boolean {
        val locales = listOf(Locale.getDefault(), Locale.US).distinctBy { it.toLanguageTag() }
        locales.forEach { locale ->
            val languageResult = engine.setLanguage(locale)
            if (languageResult != TextToSpeech.LANG_MISSING_DATA && languageResult != TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.bestOfflineVoice(locale)?.let { engine.setVoice(it) }
                return true
            }
        }
        return false
    }

    private fun TextToSpeech.bestOfflineVoice(locale: Locale): Voice? =
        runCatching {
            voices.orEmpty()
                .filterNot { it.isNetworkConnectionRequired }
                .filter { it.locale.matches(locale) }
                .sortedWith(
                    compareByDescending<Voice> { it.quality }
                        .thenBy { it.latency }
                        .thenBy { it.name }
                )
                .firstOrNull()
        }.getOrNull()

    private fun Locale?.matches(requested: Locale): Boolean {
        if (this == null) return false
        if (!language.equals(requested.language, ignoreCase = true)) return false
        return requested.country.isBlank() ||
            country.isBlank() ||
            country.equals(requested.country, ignoreCase = true)
    }

    private fun Voice.displayLabel(): String {
        val localeName = locale?.getDisplayName(Locale.getDefault()).orEmpty()
        val cleanedName = name
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        return when {
            localeName.isBlank() -> cleanedName
            cleanedName.contains(localeName, ignoreCase = true) -> cleanedName
            cleanedName.isBlank() -> localeName
            else -> "$localeName - $cleanedName"
        }
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
