package com.xreader.app.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.session.MediaSession
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

data class ReadAloudEngineOption(
    val name: String?,
    val label: String,
    val isDefault: Boolean = false,
)

data class ReadAloudOptions(
    val engines: List<ReadAloudEngineOption>,
    val voices: List<ReadAloudVoiceOption>,
)

data class ReadAloudState(
    val activeBookId: Long? = null,
    val bookTitle: String? = null,
    val initializing: Boolean = false,
    val playing: Boolean = false,
    val paused: Boolean = false,
    val currentChunk: Int = 0,
    val currentUnit: Int = 0,
    val totalChunks: Int = 0,
    val currentHeading: String? = null,
    val currentLocator: String? = null,
    val sleepTimerEndsAtMillis: Long? = null,
    val sleepTimerRemainingMillis: Long? = null,
    val message: String? = null,
)

internal fun readAloudNoReadableTextMessage(wordCount: Int?): String =
    if ((wordCount ?: 0) <= 0) {
        "Read aloud is unavailable because this book has no extractable text."
    } else {
        "No readable text is indexed for this book. Repair the book from its details screen and try again."
    }

class ReadAloudEngine(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val speechAudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(speechAudioAttributes)
        .setAcceptsDelayedFocusGain(false)
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener { focusChange ->
            scope.launch(Dispatchers.Main.immediate) {
                handleAudioFocusChange(focusChange)
            }
        }
        .build()
    private val utteranceCounter = AtomicLong(0)
    private val _state = MutableStateFlow(ReadAloudState())
    val state: StateFlow<ReadAloudState> = _state.asStateFlow()
    private val _voices = MutableStateFlow<List<ReadAloudVoiceOption>>(emptyList())
    val voices: StateFlow<List<ReadAloudVoiceOption>> = _voices.asStateFlow()
    private val _engines = MutableStateFlow<List<ReadAloudEngineOption>>(emptyList())
    val engines: StateFlow<List<ReadAloudEngineOption>> = _engines.asStateFlow()
    private val mediaSession = ReadAloudMediaSessionController(
        context = appContext,
        callbacks = object : ReadAloudMediaSessionCallbacks {
            override fun onPlayRequested() {
                val state = _state.value
                if (state.paused && state.activeBookId != null) resumeInternal(state.activeBookId)
            }

            override fun onPauseRequested() {
                val state = _state.value
                if (state.playing && state.activeBookId != null) pauseInternal(state.activeBookId)
            }

            override fun onPlayPauseRequested() {
                val state = _state.value
                val bookId = state.activeBookId ?: return
                when {
                    state.playing -> pauseInternal(bookId)
                    state.paused -> resumeInternal(bookId)
                }
            }

            override fun onStopRequested() {
                _state.value.activeBookId?.let(::stopInternal)
            }

            override fun onSkipToPreviousRequested() {
                _state.value.activeBookId?.let { skipBy(bookId = it, delta = -1) }
            }

            override fun onSkipToNextRequested() {
                _state.value.activeBookId?.let { skipBy(bookId = it, delta = 1) }
            }
        }
    )

    private var tts: TextToSpeech? = null
    private var activeEngineName: String? = null
    private var activeSpeech: ActiveSpeech? = null
    private var pendingUtteranceId: String? = null
    private var sleepTimerJob: Job? = null
    private var segmentPreparationJob: Job? = null
    private var sleepTimerEndsAtMillis: Long? = null
    private var hasAudioFocus = false
    private var foregroundServiceRequested = false
    internal val mediaSessionToken: MediaSession.Token
        get() = mediaSession.token

    suspend fun play(
        bookId: Long,
        chunks: List<ReadAloudChunk>,
        currentUnit: Int,
        currentLocator: String? = null,
        bookTitle: String,
        speechRate: Float = DEFAULT_SPEECH_RATE,
        voiceName: String? = null,
        engineName: String? = null,
        sleepTimerDurationMillis: Long? = null,
        emptyChunksMessage: String = readAloudNoReadableTextMessage(null),
    ) {
        withContext(Dispatchers.Main.immediate) {
            if (chunks.isEmpty()) {
                showMessage(bookId, emptyChunksMessage)
                return@withContext
            }

            stopInternal()
            _state.value = ReadAloudState(
                activeBookId = bookId,
                bookTitle = bookTitle,
                initializing = true,
                totalChunks = chunks.size
            )

            if (!ensureReady(engineName)) {
                _state.value = ReadAloudState(
                    activeBookId = bookId,
                    message = "Android text-to-speech is not available on this device."
                )
                return@withContext
            }

            setVoiceInternal(voiceName)
            setSpeechRateInternal(speechRate)
            val startIndex = ReadAloudPlanner.startIndex(
                chunks = chunks,
                currentUnit = currentUnit,
                currentLocator = currentLocator
            )
            val prepared = prepareSpeakableChunk(
                chunks = chunks,
                startIndex = startIndex,
                delta = 1
            )
            if (prepared == null) {
                showMessage(bookId, "No readable text is indexed for this position.")
                return@withContext
            }
            if (!requestAudioFocus()) {
                _state.value = ReadAloudState(
                    activeBookId = bookId,
                    message = "Read aloud could not start because another app is using audio."
                )
                return@withContext
            }
            activeSpeech = ActiveSpeech(
                bookId = bookId,
                chunks = chunks,
                bookTitle = bookTitle,
                chunkIndex = prepared.chunkIndex,
                segments = prepared.segments,
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

    suspend fun refreshEngines(): List<ReadAloudEngineOption> =
        withContext(Dispatchers.Main.immediate) {
            if (!ensureReady(activeEngineName)) {
                _engines.value = emptyList()
                return@withContext emptyList()
            }
            updateEngineOptions()
        }

    suspend fun refreshVoices(engineName: String? = activeEngineName): List<ReadAloudVoiceOption> =
        withContext(Dispatchers.Main.immediate) {
            if (!ensureReady(engineName)) {
                _voices.value = emptyList()
                return@withContext emptyList()
            }
            updateVoiceOptions()
        }

    suspend fun refreshOptions(engineName: String? = activeEngineName): ReadAloudOptions =
        withContext(Dispatchers.Main.immediate) {
            if (!ensureReady(engineName)) {
                _engines.value = emptyList()
                _voices.value = emptyList()
                return@withContext ReadAloudOptions(engines = emptyList(), voices = emptyList())
            }
            ReadAloudOptions(
                engines = updateEngineOptions(),
                voices = updateVoiceOptions()
            )
        }

    fun setEngine(engineName: String?) {
        scope.launch(Dispatchers.Main.immediate) {
            ensureReady(engineName)
            updateVoiceOptions()
        }
    }

    fun setVoice(voiceName: String?) {
        scope.launch(Dispatchers.Main.immediate) {
            if (ensureReady(activeEngineName)) setVoiceInternal(voiceName)
        }
    }

    fun setSleepTimer(durationMillis: Long?) {
        scope.launch(Dispatchers.Main.immediate) {
            scheduleSleepTimerInternal(durationMillis)
        }
    }

    fun skipToPrevious(bookId: Long? = null) {
        scope.launch(Dispatchers.Main.immediate) {
            skipBy(bookId = bookId, delta = -1)
        }
    }

    fun skipToNext(bookId: Long? = null) {
        scope.launch(Dispatchers.Main.immediate) {
            skipBy(bookId = bookId, delta = 1)
        }
    }

    fun pause(bookId: Long? = null) {
        scope.launch(Dispatchers.Main.immediate) {
            pauseInternal(bookId)
        }
    }

    fun resume(bookId: Long? = null) {
        scope.launch(Dispatchers.Main.immediate) {
            resumeInternal(bookId)
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
            activeEngineName = null
            _engines.value = emptyList()
            mediaSession.release()
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
        _state.value = ReadAloudState(
            activeBookId = bookId,
            bookTitle = activeSpeech?.bookTitle,
            sleepTimerEndsAtMillis = sleepTimerEndsAtMillis,
            sleepTimerRemainingMillis = currentSleepTimerRemainingMillis(),
            message = message
        )
    }

    private suspend fun ensureReady(engineName: String? = activeEngineName): Boolean {
        val requestedEngine = engineName?.takeIf { it.isNotBlank() }
        tts?.let {
            if (activeEngineName == requestedEngine) return true
            stopInternal()
            it.shutdown()
            tts = null
            activeEngineName = null
            _voices.value = emptyList()
        }

        val status = CompletableDeferred<Int>()
        val engine = if (requestedEngine == null) {
            TextToSpeech(appContext) { initStatus ->
                if (!status.isCompleted) status.complete(initStatus)
            }
        } else {
            TextToSpeech(appContext, { initStatus ->
                if (!status.isCompleted) status.complete(initStatus)
            }, requestedEngine)
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
        engine.setAudioAttributes(speechAudioAttributes)
        tts = engine
        activeEngineName = requestedEngine
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

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true
        val granted = audioManager.requestAudioFocus(audioFocusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        hasAudioFocus = granted
        return granted
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
        hasAudioFocus = false
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        val bookId = activeSpeech?.bookId ?: _state.value.activeBookId ?: return
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                stopInternal(bookId)
                _state.value = ReadAloudState(
                    activeBookId = bookId,
                    message = readAloudAudioFocusStopMessage(focusChange)
                )
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pauseInternal(bookId)
                _state.value = _state.value.copy(
                    message = readAloudAudioFocusPauseMessage(focusChange)
                )
            }
        }
    }

    private fun scheduleSleepTimerInternal(durationMillis: Long?) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimerEndsAtMillis = null
        val bookId = activeSpeech?.bookId ?: _state.value.activeBookId
        if (bookId == null || durationMillis == null || durationMillis <= 0L) {
            _state.value = _state.value.copy(
                sleepTimerEndsAtMillis = null,
                sleepTimerRemainingMillis = null
            )
            return
        }
        val endsAt = System.currentTimeMillis() + durationMillis
        sleepTimerEndsAtMillis = endsAt
        _state.value = _state.value.copy(
            sleepTimerEndsAtMillis = endsAt,
            sleepTimerRemainingMillis = durationMillis
        )
        sleepTimerJob = scope.launch(Dispatchers.Main.immediate) {
            while (true) {
                val remaining = endsAt - System.currentTimeMillis()
                if (remaining <= 0L) break
                delay(remaining.coerceAtMost(SLEEP_TIMER_STATUS_TICK_MILLIS))
                val activeBookId = activeSpeech?.bookId ?: _state.value.activeBookId
                if (activeBookId != bookId) return@launch
                val updatedRemaining = endsAt - System.currentTimeMillis()
                if (updatedRemaining > 0L) {
                    _state.value = _state.value.copy(
                        sleepTimerEndsAtMillis = endsAt,
                        sleepTimerRemainingMillis = updatedRemaining
                    )
                }
            }
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

    private fun skipBy(bookId: Long?, delta: Int) {
        val current = activeSpeech ?: return
        if (bookId != null && current.bookId != bookId) return
        val paused = _state.value.paused && !_state.value.playing
        val targetIndex = readAloudSkipTargetIndex(
            currentChunk = current.chunkIndex,
            totalChunks = current.chunks.size,
            delta = delta
        ) ?: return
        segmentPreparationJob?.cancel()
        segmentPreparationJob = scope.launch {
            val prepared = prepareSpeakableChunk(
                chunks = current.chunks,
                startIndex = targetIndex,
                delta = delta
            )
            withContext(Dispatchers.Main.immediate) {
                val latest = activeSpeech ?: return@withContext
                if (latest.bookId != current.bookId || latest.chunks !== current.chunks) return@withContext
                if (prepared == null) {
                    if (delta > 0) stopInternal(current.bookId)
                    return@withContext
                }
                activeSpeech = latest.copy(
                    chunkIndex = prepared.chunkIndex,
                    segments = prepared.segments,
                    segmentIndex = 0
                )
                if (paused) {
                    pendingUtteranceId = null
                    emitSpeechState(
                        current = requireNotNull(activeSpeech),
                        playing = false,
                        paused = true
                    )
                } else {
                    speakCurrentSegment()
                }
            }
        }
    }

    private fun pauseInternal(bookId: Long? = null) {
        val current = activeSpeech ?: return
        if (bookId != null && current.bookId != bookId) return
        tts?.stop()
        abandonAudioFocus()
        pendingUtteranceId = null
        emitSpeechState(
            current = current,
            playing = false,
            paused = true
        )
    }

    private fun resumeInternal(bookId: Long? = null) {
        val current = activeSpeech ?: return
        if (bookId != null && current.bookId != bookId) return
        if (!requestAudioFocus()) {
            emitSpeechState(
                current = current,
                playing = false,
                paused = true,
                message = "Read aloud could not resume because another app is using audio."
            )
            return
        }
        speakCurrentSegment()
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

        segmentPreparationJob?.cancel()
        segmentPreparationJob = scope.launch {
            val prepared = prepareSpeakableChunk(
                chunks = current.chunks,
                startIndex = nextChunk,
                delta = 1
            )
            withContext(Dispatchers.Main.immediate) {
                val latest = activeSpeech ?: return@withContext
                if (
                    latest.bookId != current.bookId ||
                    latest.chunks !== current.chunks ||
                    latest.chunkIndex != current.chunkIndex ||
                    latest.segmentIndex != current.segmentIndex
                ) {
                    return@withContext
                }
                if (prepared == null) {
                    stopInternal(current.bookId)
                    return@withContext
                }
                activeSpeech = latest.copy(
                    chunkIndex = prepared.chunkIndex,
                    segments = prepared.segments,
                    segmentIndex = 0
                )
                speakCurrentSegment()
            }
        }
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
        emitSpeechState(current = current, playing = true, paused = false)
        val result = tts?.speak(segment, TextToSpeech.QUEUE_FLUSH, Bundle.EMPTY, utteranceId)
        if (result == TextToSpeech.ERROR) {
            handleSpeechError(utteranceId)
        }
    }

    private fun emitSpeechState(
        current: ActiveSpeech,
        playing: Boolean,
        paused: Boolean,
        message: String? = null,
    ) {
        val chunk = current.chunks.getOrNull(current.chunkIndex)
        val nextState = ReadAloudState(
            activeBookId = current.bookId,
            bookTitle = current.bookTitle,
            playing = playing,
            paused = paused,
            currentChunk = current.chunkIndex,
            currentUnit = chunk?.unitIndex ?: 0,
            totalChunks = current.chunks.size,
            currentHeading = chunk?.heading,
            currentLocator = chunk?.locator,
            sleepTimerEndsAtMillis = sleepTimerEndsAtMillis,
            sleepTimerRemainingMillis = currentSleepTimerRemainingMillis(),
            message = message
        )
        if (!shouldEmitReadAloudState(current = _state.value, next = nextState)) return
        _state.value = nextState
        if (playing || paused) startForegroundServiceIfNeeded()
        mediaSession.update(
            bookTitle = current.bookTitle,
            heading = chunk?.heading,
            playing = playing,
            paused = paused,
            currentChunk = current.chunkIndex,
            totalChunks = current.chunks.size
        )
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
        abandonAudioFocus()
        segmentPreparationJob?.cancel()
        segmentPreparationJob = null
        activeSpeech = null
        pendingUtteranceId = null
        foregroundServiceRequested = false
        _state.value = ReadAloudState()
        mediaSession.stop()
    }

    private fun currentSleepTimerRemainingMillis(): Long? =
        sleepTimerEndsAtMillis?.let { (it - System.currentTimeMillis()).coerceAtLeast(0L) }

    private fun startForegroundServiceIfNeeded() {
        if (foregroundServiceRequested) return
        foregroundServiceRequested = true
        runCatching {
            ReadAloudForegroundService.start(appContext)
        }.onFailure {
            _state.value = _state.value.copy(
                message = "Read aloud is playing, but Android would not start background playback controls."
            )
            foregroundServiceRequested = false
        }
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

    private fun updateEngineOptions(engine: TextToSpeech? = tts): List<ReadAloudEngineOption> {
        val activeEngine = engine ?: return emptyList()
        val defaultEngine = activeEngine.defaultEngine
        val installed = runCatching {
            activeEngine.engines.orEmpty()
                .sortedWith(
                    compareBy<TextToSpeech.EngineInfo> { it.label.orEmpty().lowercase(Locale.getDefault()) }
                        .thenBy { it.name.orEmpty() }
                )
                .mapNotNull { info ->
                    val packageName = info.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    ReadAloudEngineOption(
                        name = packageName,
                        label = info.label?.takeIf { it.isNotBlank() } ?: packageName,
                        isDefault = packageName == defaultEngine
                    )
                }
        }.getOrDefault(emptyList())
        val options = listOf(
            ReadAloudEngineOption(
                name = null,
                label = "Device default",
                isDefault = activeEngineName == null
            )
        ) + installed
        _engines.value = options.distinctBy { it.name }
        return _engines.value
    }

    private fun setDefaultLanguage(engine: TextToSpeech): Boolean {
        val locales = listOf(Locale.getDefault(), Locale.US).distinctBy { it.toLanguageTag() }
        locales.forEach { locale ->
            val languageResult = engine.setLanguage(locale)
            if (languageResult != TextToSpeech.LANG_MISSING_DATA && languageResult != TextToSpeech.LANG_NOT_SUPPORTED) {
                return true
            }
        }
        return false
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
        val bookTitle: String,
        val chunkIndex: Int,
        val segments: List<String>,
        val segmentIndex: Int,
    )

    private data class PreparedSpeechChunk(
        val chunkIndex: Int,
        val segments: List<String>,
    )

    private suspend fun prepareSpeakableChunk(
        chunks: List<ReadAloudChunk>,
        startIndex: Int,
        delta: Int,
    ): PreparedSpeechChunk? =
        withContext(Dispatchers.Default) {
            var candidate = startIndex
            while (candidate in chunks.indices) {
                val segments = ReadAloudPlanner.splitForSpeech(chunks[candidate].text)
                if (segments.isNotEmpty()) {
                    return@withContext PreparedSpeechChunk(candidate, segments)
                }
                candidate += delta
            }
            null
        }

    companion object {
        private const val TTS_INIT_TIMEOUT_MILLIS = 5_000L
        private const val DEFAULT_SPEECH_RATE = 1.0f
        private const val MIN_SPEECH_RATE = 0.7f
        private const val MAX_SPEECH_RATE = 1.4f
        private const val SLEEP_TIMER_STATUS_TICK_MILLIS = 30_000L
    }
}

internal fun readAloudAudioFocusStopMessage(focusChange: Int): String? =
    when (focusChange) {
        AudioManager.AUDIOFOCUS_LOSS -> "Read aloud stopped because another app took audio focus."
        else -> null
    }

internal fun readAloudAudioFocusPauseMessage(focusChange: Int): String? =
    when (focusChange) {
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "Read aloud paused because another app needed audio."
        else -> null
    }

internal fun readAloudSkipTargetIndex(
    currentChunk: Int,
    totalChunks: Int,
    delta: Int,
): Int? {
    if (totalChunks <= 0 || delta == 0) return null
    val target = currentChunk + delta
    return target.takeIf { it in 0 until totalChunks }
}

internal fun shouldEmitReadAloudState(
    current: ReadAloudState,
    next: ReadAloudState,
): Boolean =
    current != next
