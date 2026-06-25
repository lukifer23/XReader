package com.xreader.app.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.xreader.app.core.releaseQuietlyAsync
import com.xreader.app.core.speechMediaPlayerForFile
import com.xreader.app.data.BookAudioEntity
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AudiobookPlaybackUiState(
    val audioId: Long? = null,
    val bookId: Long? = null,
    val bookTitle: String? = null,
    val profileLabel: String? = null,
    val playing: Boolean = false,
    val segmentIndex: Int = 0,
    val segmentCount: Int = 0,
    val chapterIndex: Int? = null,
    val chapterCount: Int = 0,
    val chapterTitle: String? = null,
    val segmentPositionMs: Int = 0,
    val segmentDurationMs: Int = 0,
    val preparing: Boolean = false,
    val error: String? = null,
) {
    val active: Boolean get() = audioId != null
    val paused: Boolean get() = active && !playing && !preparing && error == null
}

val EMPTY_AUDIOBOOK_PLAYBACK_UI_STATE = AudiobookPlaybackUiState()

class GeneratedAudiobookPlaybackController(
    private val context: Context,
    private val repository: NeuralTtsRepository,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(EMPTY_AUDIOBOOK_PLAYBACK_UI_STATE)
    val state: StateFlow<AudiobookPlaybackUiState> = _state.asStateFlow()

    private var player: MediaPlayer? = null
    private var activeAudio: BookAudioEntity? = null
    private var segmentQueue: List<File> = emptyList()
    private var segmentChapterIndexes: List<Int> = emptyList()
    private var segmentChapters: List<GeneratedAudiobookChapter?> = emptyList()
    private var segmentPauseMillis: List<Long> = emptyList()
    private var chapters: List<GeneratedAudiobookChapter> = emptyList()
    private var playbackStartJob: Job? = null
    private var transitionJob: Job? = null
    private var segmentPrepareJob: Job? = null
    private var positionSaveJob: Job? = null
    private var positionPersistJob: Job? = null
    private var preparedPlaybackCache: PreparedAudiobookPlaybackCache? = null
    private val positionPersistQueue = GeneratedAudiobookPositionPersistQueue()
    @Volatile
    private var lastPersistedPosition: PendingGeneratedAudiobookPositionPersist? = null
    private var foregroundServiceRequested = false
    private var preparingSegment = false
    private val mediaSession = GeneratedAudiobookMediaSessionController(
        context = appContext,
        callbacks = object : GeneratedAudiobookMediaCallbacks {
            override fun onPlayRequested() = resume()
            override fun onPauseRequested() = pause()
            override fun onPlayPauseRequested() {
                if (_state.value.playing) pause() else resume()
            }
            override fun onStopRequested() = stop()
            override fun onSkipToPreviousRequested() = skipPrevious()
            override fun onSkipToNextRequested() = skipNext()
        }
    )

    internal val mediaSessionToken: android.media.session.MediaSession.Token
        get() = mediaSession.token

    fun play(bookTitle: String, audio: BookAudioEntity) {
        playFromSegment(bookTitle = bookTitle, audio = audio, segmentIndex = audio.playbackSegmentIndex)
    }

    fun playFromSegment(bookTitle: String, audio: BookAudioEntity, segmentIndex: Int) {
        val current = _state.value
        if (current.audioId == audio.id && player != null && segmentIndex == current.segmentIndex) {
            if (current.preparing || preparingSegment) return
            if (current.playing) return
            resume()
            return
        }

        persistCurrentPosition(current)
        resetPlaybackResources(cancelPlaybackStart = true)
        setState(AudiobookPlaybackUiState(
            audioId = audio.id,
            bookId = audio.bookId,
            bookTitle = bookTitle,
            profileLabel = audio.profileLabel(),
            playing = false,
            segmentIndex = segmentIndex.coerceAtLeast(0),
            segmentCount = audio.playableSegmentCount(),
            preparing = true,
            error = null
        ))
        playbackStartJob = scope.launch(Dispatchers.Main.immediate) {
            val prepared = runCatching {
                withContext(Dispatchers.IO) { audio.preparePlaybackFiles() }
            }.getOrElse { error ->
                Log.e("XReader", "Generated audiobook segments missing for ${audio.id}", error)
                setState(AudiobookPlaybackUiState(audioId = audio.id, bookId = audio.bookId, error = error.message ?: "Generated audio files are missing."))
                return@launch
            }
            if (!isActive) return@launch
            releasePlayer()
            activeAudio = audio
            segmentQueue = prepared.segments
            chapters = prepared.chapters
            segmentChapterIndexes = prepared.segmentChapterIndexes
            segmentChapters = prepared.segmentChapters
            segmentPauseMillis = prepared.segmentPauseMillis
            val startIndex = generatedAudiobookStartSegmentIndex(
                requestedSegmentIndex = segmentIndex,
                segmentCount = prepared.segments.size
            )
            val startPositionMs = if (segmentIndex == audio.playbackSegmentIndex && segmentIndex in prepared.segments.indices) {
                audio.playbackPositionMs.coerceAtLeast(0)
            } else {
                0
            }
            startSegment(bookTitle = bookTitle, audio = audio, index = startIndex, startPositionMs = startPositionMs)
        }
    }

    fun resume() {
        val audio = activeAudio ?: return
        val current = _state.value
        if (current.preparing || preparingSegment) return
        val existing = player
        if (existing != null) {
            runCatching {
                existing.start()
                setState(_state.value.copy(
                    playing = true,
                    segmentPositionMs = existing.currentPosition.coerceAtLeast(0),
                    segmentDurationMs = existing.duration.coerceAtLeast(0),
                    error = null
                ))
            }.onFailure { error ->
                Log.e("XReader", "Generated audiobook resume failed for ${audio.id}", error)
                stopWithError(error.message ?: "Could not resume audiobook playback")
            }
        } else {
            val title = current.bookTitle ?: "Generated audiobook"
            startSegment(title, audio, current.segmentIndex, 0)
        }
    }

    fun pause() {
        val current = _state.value
        val existing = player ?: return
        if (!current.playing || current.preparing || preparingSegment) {
            setState(current.copy(playing = false, error = null))
            return
        }
        runCatching {
            if (existing.isPlaying) existing.pause()
            persist(current.audioId, current.segmentIndex, existing.currentPosition)
            setState(_state.value.copy(
                playing = false,
                segmentPositionMs = existing.currentPosition.coerceAtLeast(0),
                segmentDurationMs = existing.duration.coerceAtLeast(0),
                error = null
            ))
        }.onFailure { error ->
            Log.e("XReader", "Generated audiobook pause failed for ${current.audioId}", error)
            stopWithError(error.message ?: "Could not pause audiobook playback")
        }
    }

    fun stop() {
        val current = _state.value
        persist(current.audioId, current.segmentIndex, runCatching { player?.currentPosition ?: 0 }.getOrDefault(0))
        resetPlaybackResources(cancelPlaybackStart = true)
        setState(EMPTY_AUDIOBOOK_PLAYBACK_UI_STATE)
    }

    fun skipPrevious() {
        val audio = activeAudio ?: return
        val current = _state.value
        val nextIndex = (current.segmentIndex - 1).coerceAtLeast(0)
        startSegment(current.bookTitle ?: "Generated audiobook", audio, nextIndex, 0)
    }

    fun skipNext() {
        val audio = activeAudio ?: return
        val current = _state.value
        val nextIndex = (current.segmentIndex + 1).coerceAtMost((segmentQueue.size - 1).coerceAtLeast(0))
        startSegment(current.bookTitle ?: "Generated audiobook", audio, nextIndex, 0)
    }

    fun release() {
        persistCurrentPosition(_state.value)
        positionSaveJob?.cancel()
        positionSaveJob = null
        resetPlaybackResources(cancelPlaybackStart = true)
        foregroundServiceRequested = false
        _state.value = EMPTY_AUDIOBOOK_PLAYBACK_UI_STATE
        mediaSession.release()
        preparedPlaybackCache = null
    }

    private fun startSegment(bookTitle: String, audio: BookAudioEntity, index: Int, startPositionMs: Int = 0) {
        transitionJob?.cancel()
        transitionJob = null
        if (index !in segmentQueue.indices) {
            persist(audio.id, index, 0)
            resetPlaybackResources(cancelPlaybackStart = false)
            setState(EMPTY_AUDIOBOOK_PLAYBACK_UI_STATE)
            return
        }
        val file = segmentQueue[index]
        releasePlayer()
        preparingSegment = true
        setState(playbackStateForSegment(
            bookTitle = bookTitle,
            audio = audio,
            index = index,
            playing = false,
            segmentPositionMs = startPositionMs.coerceAtLeast(0),
            segmentDurationMs = 0,
            preparing = true,
            error = null
        ))
        segmentPrepareJob?.cancel()
        segmentPrepareJob = scope.launch(Dispatchers.Main.immediate) {
            val preparedPlayer = runCatching {
                withContext(Dispatchers.IO) {
                    speechMediaPlayerForFile(
                        file = file,
                        usage = AudioAttributes.USAGE_MEDIA
                    )
                }
            }.getOrElse { error ->
                Log.e("XReader", "Generated audiobook segment datasource failed for ${audio.id}/$index", error)
                if (_state.value.audioId == audio.id && _state.value.segmentIndex == index) {
                    preparingSegment = false
                    stopWithError(error.message ?: "Could not load generated audio segment ${index + 1}.")
                }
                return@launch
            }
            if (!isActive) {
                preparedPlayer.releaseQuietlyAsync(scope)
                return@launch
            }
            if (_state.value.audioId != audio.id || _state.value.segmentIndex != index || !preparingSegment) {
                preparedPlayer.releaseQuietlyAsync(scope)
                return@launch
            }
            player = preparedPlayer.configureSegmentCallbacks(
                bookTitle = bookTitle,
                audio = audio,
                index = index,
                startPositionMs = startPositionMs
            )
            runCatching { preparedPlayer.prepareAsync() }
                .onFailure { error ->
                    preparingSegment = false
                    Log.e("XReader", "Generated audiobook segment prepare failed for ${audio.id}/$index", error)
                    stopWithError(error.message ?: "Could not prepare generated audio segment ${index + 1}.")
                }
        }
    }

    private fun MediaPlayer.configureSegmentCallbacks(
        bookTitle: String,
        audio: BookAudioEntity,
        index: Int,
        startPositionMs: Int,
    ): MediaPlayer = apply {
        setOnCompletionListener { completedPlayer ->
            if (player !== completedPlayer) return@setOnCompletionListener
            val nextIndex = index + 1
            persist(audio.id, nextIndex, 0)
            val completedDuration = runCatching { completedPlayer.duration.coerceAtLeast(0) }
                .getOrDefault(_state.value.segmentDurationMs)
            setState(playbackStateForSegment(
                bookTitle = bookTitle,
                audio = audio,
                index = index,
                playing = false,
                segmentPositionMs = completedDuration,
                segmentDurationMs = completedDuration,
                preparing = false,
                error = null
            ))
            transitionJob = scope.launch(Dispatchers.Main.immediate) {
                delay(transitionPauseMillis(index, nextIndex))
                startSegment(bookTitle, audio, nextIndex)
            }
        }
        setOnErrorListener { failedPlayer, _, _ ->
            if (player !== failedPlayer) return@setOnErrorListener true
            player = null
            failedPlayer.releaseQuietlyAsync(scope)
            setState(AudiobookPlaybackUiState(
                audioId = audio.id,
                bookId = audio.bookId,
                bookTitle = bookTitle,
                profileLabel = audio.profileLabel(),
                playing = false,
                segmentIndex = index,
                segmentCount = segmentQueue.size,
                preparing = false,
                error = "Could not play generated audio segment ${index + 1}."
            ))
            true
        }
        setOnPreparedListener { preparedPlayer ->
            if (player !== preparedPlayer) return@setOnPreparedListener
            runCatching {
                preparingSegment = false
                if (startPositionMs > 0) preparedPlayer.seekTo(startPositionMs)
                preparedPlayer.start()
                persist(audio.id, index, startPositionMs)
                setState(playbackStateForSegment(
                    bookTitle = bookTitle,
                    audio = audio,
                    index = index,
                    playing = true,
                    segmentPositionMs = preparedPlayer.currentPosition.coerceAtLeast(0),
                    segmentDurationMs = preparedPlayer.duration.coerceAtLeast(0),
                    preparing = false,
                    error = null
                ))
            }.onFailure { error ->
                preparingSegment = false
                Log.e("XReader", "Generated audiobook segment start failed for ${audio.id}/$index", error)
                stopWithError(error.message ?: "Could not start generated audio segment ${index + 1}.")
            }
        }
    }

    fun skipPreviousChapter() {
        val audio = activeAudio ?: return
        val current = _state.value
        val previous = chapters.previousChapterStart(current.segmentIndex) ?: return
        startSegment(current.bookTitle ?: "Generated audiobook", audio, previous, 0)
    }

    fun skipNextChapter() {
        val audio = activeAudio ?: return
        val current = _state.value
        val next = chapters.nextChapterStart(current.segmentIndex) ?: return
        startSegment(current.bookTitle ?: "Generated audiobook", audio, next, 0)
    }

    private fun stopWithError(message: String) {
        val current = _state.value
        val position = runCatching { player?.currentPosition ?: current.segmentPositionMs }.getOrDefault(current.segmentPositionMs)
        persist(current.audioId, current.segmentIndex, position)
        resetPlaybackResources(cancelPlaybackStart = false, clearQueue = false)
        setState(current.copy(playing = false, preparing = false, segmentPositionMs = position.coerceAtLeast(0), error = message))
    }

    private fun setState(state: AudiobookPlaybackUiState) {
        if (!shouldEmitAudiobookPlaybackState(current = _state.value, next = state)) return
        _state.value = state
        mediaSession.update(state)
        syncPeriodicPositionSave(state)
        syncForegroundService(state)
    }

    private fun syncForegroundService(state: AudiobookPlaybackUiState) {
        if (!shouldRequestGeneratedAudiobookForegroundService(
                state = state,
                foregroundServiceRequested = foregroundServiceRequested
            )
        ) {
            foregroundServiceRequested = generatedAudiobookForegroundServiceRequestedAfterState(
                state = state,
                foregroundServiceRequested = foregroundServiceRequested
            )
            return
        }
        foregroundServiceRequested = true
        runCatching {
            GeneratedAudiobookForegroundService.start(appContext)
        }.onFailure { error ->
            foregroundServiceRequested = false
            Log.e("XReader", "Generated audiobook foreground service start failed", error)
        }
    }

    private fun persistCurrentPosition(state: AudiobookPlaybackUiState) {
        if (!state.active) return
        val position = runCatching { player?.currentPosition ?: state.segmentPositionMs }
            .getOrDefault(state.segmentPositionMs)
        persist(state.audioId, state.segmentIndex, position)
    }

    private fun releasePlayer() {
        val current = player ?: return
        player = null
        current.releaseQuietlyAsync(scope)
    }

    private fun resetPlaybackResources(
        cancelPlaybackStart: Boolean,
        clearQueue: Boolean = true,
    ) {
        if (cancelPlaybackStart) {
            playbackStartJob?.cancel()
            playbackStartJob = null
        }
        transitionJob?.cancel()
        transitionJob = null
        segmentPrepareJob?.cancel()
        segmentPrepareJob = null
        preparingSegment = false
        releasePlayer()
        if (clearQueue) {
            activeAudio = null
            segmentQueue = emptyList()
            segmentChapterIndexes = emptyList()
            segmentChapters = emptyList()
            segmentPauseMillis = emptyList()
            chapters = emptyList()
        }
    }

    private fun transitionPauseMillis(currentIndex: Int, nextIndex: Int): Long {
        if (nextIndex !in segmentQueue.indices) return 0L
        val currentChapter = segmentChapterIndexes.getOrNull(currentIndex)
        val nextChapter = segmentChapterIndexes.getOrNull(nextIndex)
        return if (currentChapter != null && nextChapter != null && currentChapter != nextChapter) {
            CHAPTER_TRANSITION_PAUSE_MS
        } else {
            segmentPauseMillis.getOrElse(currentIndex) { SEGMENT_TRANSITION_PAUSE_MS }
        }
    }

    private fun playbackStateForSegment(
        bookTitle: String,
        audio: BookAudioEntity,
        index: Int,
        playing: Boolean,
        segmentPositionMs: Int,
        segmentDurationMs: Int,
        preparing: Boolean,
        error: String?,
    ): AudiobookPlaybackUiState {
        val chapter = segmentChapters.getOrNull(index)
        return AudiobookPlaybackUiState(
            audioId = audio.id,
            bookId = audio.bookId,
            bookTitle = bookTitle,
            profileLabel = audio.profileLabel(),
            playing = playing,
            segmentIndex = index,
            segmentCount = segmentQueue.size,
            chapterIndex = chapter?.index,
            chapterCount = chapters.size,
            chapterTitle = chapter?.title,
            segmentPositionMs = segmentPositionMs,
            segmentDurationMs = segmentDurationMs,
            preparing = preparing,
            error = error
        )
    }

    private fun persist(audioId: Long?, segmentIndex: Int, positionMs: Int, coalesce: Boolean = false) {
        if (audioId == null) return
        val position = generatedAudiobookPersistedPlaybackPosition(
            requestedSegmentIndex = segmentIndex,
            positionMs = positionMs,
            segmentCount = segmentQueue.size
        )
        val pending = PendingGeneratedAudiobookPositionPersist(
            audioId = audioId,
            segmentIndex = position.segmentIndex,
            positionMs = position.positionMs,
            playableSegmentCount = segmentQueue.size
        )
        if (!shouldOfferGeneratedAudiobookPositionPersist(pending, lastPersistedPosition)) return
        if (!positionPersistQueue.offer(pending, coalesce = coalesce)) return
        positionPersistJob = scope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val next = positionPersistQueue.poll() ?: break
                    runCatching {
                        repository.updateBookAudioPlayback(
                            audioId = next.audioId,
                            segmentIndex = next.segmentIndex,
                            positionMs = next.positionMs,
                            playableSegmentCount = next.playableSegmentCount
                        )
                        lastPersistedPosition = next
                    }.onFailure { error ->
                        Log.e("XReader", "Generated audiobook position save failed for ${next.audioId}", error)
                    }
                }
            } finally {
                if (!isActive) positionPersistQueue.finishCanceledDrain()
            }
        }
    }

    private fun syncPeriodicPositionSave(state: AudiobookPlaybackUiState) {
        if (!state.playing || state.audioId == null) {
            positionSaveJob?.cancel()
            positionSaveJob = null
            return
        }
        if (positionSaveJob?.isActive == true) return
        positionSaveJob = scope.launch(Dispatchers.Main.immediate) {
            var lastPersistedAtMillis = System.currentTimeMillis()
            while (isActive) {
                delay(PLAYBACK_POSITION_UI_UPDATE_INTERVAL_MS)
                val current = _state.value
                val currentPlayer = player
                if (!current.playing || current.audioId == null || currentPlayer == null) continue
                val position = runCatching { currentPlayer.currentPosition.coerceAtLeast(0) }.getOrNull() ?: continue
                val duration = runCatching { currentPlayer.duration.coerceAtLeast(0) }.getOrDefault(current.segmentDurationMs)
                val nowMillis = System.currentTimeMillis()
                if (shouldPersistGeneratedAudiobookPlaybackPosition(lastPersistedAtMillis, nowMillis)) {
                    persist(current.audioId, current.segmentIndex, position, coalesce = true)
                    lastPersistedAtMillis = nowMillis
                }
                val updated = current.copy(segmentPositionMs = position, segmentDurationMs = duration)
                if (shouldEmitAudiobookPlaybackState(current = current, next = updated)) {
                    _state.value = updated
                    mediaSession.update(updated)
                }
            }
        }
    }

    private fun BookAudioEntity.segmentFiles(): List<File> {
        filePath?.let { path ->
            require(File(path).isDirectory) { "Generated audio files are missing." }
        } ?: error("Generated audio files are missing.")
        val files = expectedPlayableSegmentFiles()
        require(files.isNotEmpty()) { "Generated audio segments are missing." }
        return files
    }

    private fun BookAudioEntity.preparePlaybackFiles(): PreparedAudiobookPlayback {
        val key = generatedAudiobookPlaybackPreparationKey()
        preparedPlaybackCache
            ?.takeIf { it.key == key }
            ?.let { return it.prepared }
        val segments = segmentFiles()
        val chapters = generatedAudiobookChapters(segments.size)
        val metadata = generatedAudiobookPlaybackMetadata(segmentCount = segments.size, chapters = chapters)
        val chaptersByIndex = chapters.associateBy { it.index }
        val prepared = PreparedAudiobookPlayback(
            segments = segments,
            chapters = chapters,
            segmentChapterIndexes = metadata.chapterIndexes,
            segmentChapters = metadata.chapterIndexes.map { chapterIndex -> chaptersByIndex[chapterIndex] },
            segmentPauseMillis = metadata.pauseAfterMillis.sanitizedSegmentPauseMillis(segments.size)
        )
        preparedPlaybackCache = PreparedAudiobookPlaybackCache(key = key, prepared = prepared)
        return prepared
    }

    private fun List<Long>.sanitizedSegmentPauseMillis(segmentCount: Int): List<Long> =
        if (segmentCount <= 0) {
            emptyList()
        } else {
            List(segmentCount) { index ->
                getOrElse(index) { SEGMENT_TRANSITION_PAUSE_MS }
                    .coerceIn(MIN_SEGMENT_TRANSITION_PAUSE_MS, MAX_SEGMENT_TRANSITION_PAUSE_MS)
            }
        }
}

private data class PreparedAudiobookPlayback(
    val segments: List<File>,
    val chapters: List<GeneratedAudiobookChapter>,
    val segmentChapterIndexes: List<Int>,
    val segmentChapters: List<GeneratedAudiobookChapter?>,
    val segmentPauseMillis: List<Long>,
)

private data class PreparedAudiobookPlaybackCache(
    val key: GeneratedAudiobookPlaybackPreparationKey,
    val prepared: PreparedAudiobookPlayback,
)

internal data class GeneratedAudiobookPlaybackPreparationKey(
    val audioId: Long,
    val filePath: String?,
    val status: String,
    val segmentCount: Int,
    val playableSegmentCount: Int,
    val scope: String,
    val generatedAt: Long?,
)

internal fun BookAudioEntity.generatedAudiobookPlaybackPreparationKey(): GeneratedAudiobookPlaybackPreparationKey =
    GeneratedAudiobookPlaybackPreparationKey(
        audioId = id,
        filePath = filePath,
        status = status.name,
        segmentCount = segmentCount,
        playableSegmentCount = playableSegmentCount(),
        scope = scope,
        generatedAt = generatedAt
    )

internal data class PendingGeneratedAudiobookPositionPersist(
    val audioId: Long,
    val segmentIndex: Int,
    val positionMs: Int,
    val playableSegmentCount: Int,
)

internal fun shouldOfferGeneratedAudiobookPositionPersist(
    pending: PendingGeneratedAudiobookPositionPersist,
    lastPersisted: PendingGeneratedAudiobookPositionPersist?,
): Boolean =
    pending != lastPersisted

internal fun PendingGeneratedAudiobookPositionPersist?.coalescedWith(
    next: PendingGeneratedAudiobookPositionPersist,
): PendingGeneratedAudiobookPositionPersist =
    when {
        this == null -> next
        audioId != next.audioId -> next
        next.segmentIndex > segmentIndex -> next
        next.segmentIndex == segmentIndex && next.positionMs >= positionMs -> next
        else -> this
    }

internal class GeneratedAudiobookPositionPersistQueue {
    private var pending: PendingGeneratedAudiobookPositionPersist? = null
    private var draining: Boolean = false

    fun offer(next: PendingGeneratedAudiobookPositionPersist, coalesce: Boolean): Boolean =
        synchronized(this) {
            pending = if (coalesce) {
                pending.coalescedWith(next)
            } else {
                next
            }
            if (draining) {
                false
            } else {
                draining = true
                true
            }
        }

    fun poll(): PendingGeneratedAudiobookPositionPersist? =
        synchronized(this) {
            val next = pending
            pending = null
            if (next == null) draining = false
            next
        }

    fun finishCanceledDrain() {
        synchronized(this) {
            draining = false
        }
    }

}

internal data class GeneratedAudiobookPersistedPlaybackPosition(
    val segmentIndex: Int,
    val positionMs: Int,
)

internal fun generatedAudiobookPersistedPlaybackPosition(
    requestedSegmentIndex: Int,
    positionMs: Int,
    segmentCount: Int,
): GeneratedAudiobookPersistedPlaybackPosition {
    if (segmentCount <= 0) {
        return GeneratedAudiobookPersistedPlaybackPosition(segmentIndex = 0, positionMs = 0)
    }
    if (requestedSegmentIndex >= segmentCount) {
        return GeneratedAudiobookPersistedPlaybackPosition(segmentIndex = segmentCount, positionMs = 0)
    }
    return GeneratedAudiobookPersistedPlaybackPosition(
        segmentIndex = requestedSegmentIndex.coerceAtLeast(0),
        positionMs = positionMs.coerceAtLeast(0)
    )
}

internal fun generatedAudiobookStartSegmentIndex(
    requestedSegmentIndex: Int,
    segmentCount: Int,
): Int {
    if (segmentCount <= 0) return 0
    if (requestedSegmentIndex >= segmentCount) return 0
    return requestedSegmentIndex.coerceAtLeast(0)
}

internal fun shouldEmitAudiobookPlaybackState(
    current: AudiobookPlaybackUiState,
    next: AudiobookPlaybackUiState,
): Boolean {
    if (current == next) return false
    if (
        current.copy(
            segmentPositionMs = 0,
            segmentDurationMs = 0
        ) != next.copy(
            segmentPositionMs = 0,
            segmentDurationMs = 0
        )
    ) {
        return true
    }
    if (current.segmentDurationMs != next.segmentDurationMs) return true
    return kotlin.math.abs(next.segmentPositionMs - current.segmentPositionMs) >= PLAYBACK_POSITION_UI_UPDATE_STEP_MS
}

internal fun shouldRequestGeneratedAudiobookForegroundService(
    state: AudiobookPlaybackUiState,
    foregroundServiceRequested: Boolean,
): Boolean =
    state.foregroundActive && !foregroundServiceRequested

internal fun generatedAudiobookForegroundServiceRequestedAfterState(
    state: AudiobookPlaybackUiState,
    foregroundServiceRequested: Boolean,
): Boolean =
    state.foregroundActive && foregroundServiceRequested

internal fun shouldPersistGeneratedAudiobookPlaybackPosition(
    lastPersistedAtMillis: Long,
    nowMillis: Long,
): Boolean =
    nowMillis - lastPersistedAtMillis >= POSITION_SAVE_INTERVAL_MS

internal fun BookAudioEntity.profileLabel(): String =
    listOf(
        modelDisplayName,
        scopeLabel.takeUnless { it.equals("Full book", ignoreCase = true) },
        tone.lowercase().replaceFirstChar { it.titlecase() },
        "%.2fx".format(java.util.Locale.US, speed)
    ).filterNotNull().joinToString(" ")

private const val POSITION_SAVE_INTERVAL_MS = 5_000L
private const val PLAYBACK_POSITION_UI_UPDATE_INTERVAL_MS = 1_000L
private const val PLAYBACK_POSITION_UI_UPDATE_STEP_MS = 1_000
private const val SEGMENT_TRANSITION_PAUSE_MS = 220L
private const val MIN_SEGMENT_TRANSITION_PAUSE_MS = 120L
private const val MAX_SEGMENT_TRANSITION_PAUSE_MS = 900L
private const val CHAPTER_TRANSITION_PAUSE_MS = 850L
