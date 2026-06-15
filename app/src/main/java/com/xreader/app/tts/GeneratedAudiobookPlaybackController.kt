package com.xreader.app.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
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

class GeneratedAudiobookPlaybackController(
    private val context: Context,
    private val repository: NeuralTtsRepository,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(AudiobookPlaybackUiState())
    val state: StateFlow<AudiobookPlaybackUiState> = _state.asStateFlow()

    private var player: MediaPlayer? = null
    private var activeAudio: BookAudioEntity? = null
    private var segmentQueue: List<File> = emptyList()
    private var segmentChapterIndexes: List<Int> = emptyList()
    private var segmentPauseMillis: List<Long> = emptyList()
    private var chapters: List<GeneratedAudiobookChapter> = emptyList()
    private var playbackStartJob: Job? = null
    private var transitionJob: Job? = null
    private var positionSaveJob: Job? = null
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
        if (audio.playableSegmentCount() <= 0) {
            setState(AudiobookPlaybackUiState(error = "Generate at least one audiobook segment before playing it."))
            return
        }
        val current = _state.value
        if (current.audioId == audio.id && player != null && segmentIndex == current.segmentIndex) {
            if (current.preparing || preparingSegment) return
            if (current.playing) return
            resume()
            return
        }

        playbackStartJob?.cancel()
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
        playbackStartJob = scope.launch {
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
            segmentPauseMillis = prepared.segmentPauseMillis
            val startIndex = segmentIndex.coerceIn(0, (prepared.segments.size - 1).coerceAtLeast(0))
            val startPositionMs = if (segmentIndex == audio.playbackSegmentIndex && segmentIndex in prepared.segments.indices) {
                audio.playbackPositionMs.coerceAtLeast(0)
            } else {
                0
            }
            startSegment(bookTitle = bookTitle, audio = audio, index = startIndex, startPositionMs = startPositionMs)
            GeneratedAudiobookForegroundService.start(appContext)
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
                GeneratedAudiobookForegroundService.start(appContext)
            }.onFailure { error ->
                Log.e("XReader", "Generated audiobook resume failed for ${audio.id}", error)
                stopWithError(error.message ?: "Could not resume audiobook playback")
            }
        } else {
            val title = current.bookTitle ?: "Generated audiobook"
            startSegment(title, audio, current.segmentIndex, 0)
            GeneratedAudiobookForegroundService.start(appContext)
        }
    }

    fun pause() {
        val current = _state.value
        val existing = player ?: return
        if (!current.playing || current.preparing || preparingSegment) {
            setState(current.copy(playing = false, error = null))
            GeneratedAudiobookForegroundService.start(appContext)
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
            GeneratedAudiobookForegroundService.start(appContext)
        }.onFailure { error ->
            Log.e("XReader", "Generated audiobook pause failed for ${current.audioId}", error)
            stopWithError(error.message ?: "Could not pause audiobook playback")
        }
    }

    fun stop() {
        val current = _state.value
        persist(current.audioId, current.segmentIndex, runCatching { player?.currentPosition ?: 0 }.getOrDefault(0))
        playbackStartJob?.cancel()
        playbackStartJob = null
        preparingSegment = false
        releasePlayer()
        activeAudio = null
        segmentQueue = emptyList()
        segmentChapterIndexes = emptyList()
        segmentPauseMillis = emptyList()
        chapters = emptyList()
        transitionJob?.cancel()
        transitionJob = null
        setState(AudiobookPlaybackUiState())
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
        positionSaveJob?.cancel()
        positionSaveJob = null
        playbackStartJob?.cancel()
        playbackStartJob = null
        preparingSegment = false
        releasePlayer()
        activeAudio = null
        segmentQueue = emptyList()
        segmentChapterIndexes = emptyList()
        segmentPauseMillis = emptyList()
        chapters = emptyList()
        transitionJob?.cancel()
        transitionJob = null
        _state.value = AudiobookPlaybackUiState()
        mediaSession.release()
    }

    private fun startSegment(bookTitle: String, audio: BookAudioEntity, index: Int, startPositionMs: Int = 0) {
        transitionJob?.cancel()
        transitionJob = null
        if (index !in segmentQueue.indices) {
            persist(audio.id, 0, 0)
            preparingSegment = false
            releasePlayer()
            activeAudio = null
            segmentQueue = emptyList()
            segmentChapterIndexes = emptyList()
            segmentPauseMillis = emptyList()
            chapters = emptyList()
            setState(AudiobookPlaybackUiState())
            return
        }
        val file = segmentQueue[index]
        releasePlayer()
        preparingSegment = true
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            setDataSource(file.absolutePath)
            setOnCompletionListener { completedPlayer ->
                val nextIndex = index + 1
                persist(audio.id, nextIndex, 0)
                val completedDuration = runCatching { completedPlayer.duration.coerceAtLeast(0) }
                    .getOrDefault(_state.value.segmentDurationMs)
                setState(_state.value.copy(
                    playing = false,
                    segmentIndex = index,
                    chapterIndex = chapters.chapterForSegment(index)?.index,
                    chapterCount = chapters.size,
                    chapterTitle = chapters.chapterForSegment(index)?.title,
                    segmentPositionMs = completedDuration,
                    segmentDurationMs = completedDuration,
                    preparing = false
                ))
                transitionJob = scope.launch {
                    delay(transitionPauseMillis(index, nextIndex))
                    startSegment(bookTitle, audio, nextIndex)
                }
            }
            setOnErrorListener { failedPlayer, _, _ ->
                failedPlayer.release()
                if (player === failedPlayer) player = null
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
                    setState(AudiobookPlaybackUiState(
                        audioId = audio.id,
                        bookId = audio.bookId,
                        bookTitle = bookTitle,
                        profileLabel = audio.profileLabel(),
                        playing = true,
                        segmentIndex = index,
                        segmentCount = segmentQueue.size,
                        chapterIndex = chapters.chapterForSegment(index)?.index,
                        chapterCount = chapters.size,
                        chapterTitle = chapters.chapterForSegment(index)?.title,
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
            runCatching { prepareAsync() }
                .onFailure { error ->
                    preparingSegment = false
                    Log.e("XReader", "Generated audiobook segment prepare failed for ${audio.id}/$index", error)
                    stopWithError(error.message ?: "Could not prepare generated audio segment ${index + 1}.")
                }
        }
        setState(AudiobookPlaybackUiState(
            audioId = audio.id,
            bookId = audio.bookId,
            bookTitle = bookTitle,
            profileLabel = audio.profileLabel(),
            playing = false,
            segmentIndex = index,
            segmentCount = segmentQueue.size,
            chapterIndex = chapters.chapterForSegment(index)?.index,
            chapterCount = chapters.size,
            chapterTitle = chapters.chapterForSegment(index)?.title,
            segmentPositionMs = startPositionMs.coerceAtLeast(0),
            segmentDurationMs = 0,
            preparing = true,
            error = null
        ))
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
        preparingSegment = false
        releasePlayer()
        setState(current.copy(playing = false, preparing = false, segmentPositionMs = position.coerceAtLeast(0), error = message))
    }

    private fun setState(state: AudiobookPlaybackUiState) {
        _state.value = state
        mediaSession.update(state)
        syncPeriodicPositionSave(state)
    }

    private fun releasePlayer() {
        player?.release()
        player = null
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

    private fun persist(audioId: Long?, segmentIndex: Int, positionMs: Int) {
        if (audioId == null) return
        scope.launch {
            runCatching {
                repository.updateBookAudioPlayback(
                    audioId = audioId,
                    segmentIndex = segmentIndex.coerceAtLeast(0),
                    positionMs = positionMs.coerceAtLeast(0)
                )
            }.onFailure { error ->
                Log.e("XReader", "Generated audiobook position save failed for $audioId", error)
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
        positionSaveJob = scope.launch {
            while (isActive) {
                delay(POSITION_SAVE_INTERVAL_MS)
                val current = _state.value
                val currentPlayer = player
                if (!current.playing || current.audioId == null || currentPlayer == null) continue
                val position = runCatching { currentPlayer.currentPosition.coerceAtLeast(0) }.getOrNull() ?: continue
                val duration = runCatching { currentPlayer.duration.coerceAtLeast(0) }.getOrDefault(current.segmentDurationMs)
                persist(current.audioId, current.segmentIndex, position)
                val updated = current.copy(segmentPositionMs = position, segmentDurationMs = duration)
                _state.value = updated
                mediaSession.update(updated)
            }
        }
    }

    private fun BookAudioEntity.segmentFiles(): List<File> {
        filePath?.let { path ->
            require(File(path).isDirectory) { "Generated audio files are missing." }
        } ?: error("Generated audio files are missing.")
        val files = playableSegmentFiles()
        require(files.isNotEmpty()) { "Generated audio segments are missing." }
        return files
    }

    private fun BookAudioEntity.preparePlaybackFiles(): PreparedAudiobookPlayback {
        val segments = segmentFiles()
        val chapters = generatedAudiobookChapters()
        return PreparedAudiobookPlayback(
            segments = segments,
            chapters = chapters,
            segmentChapterIndexes = generatedAudiobookSegmentChapterIndexes(segments.size, chapters),
            segmentPauseMillis = segmentPauseMillis(segments.size)
        )
    }

    private fun BookAudioEntity.segmentPauseMillis(segmentCount: Int): List<Long> {
        if (segmentCount <= 0) return emptyList()
        val root = filePath?.let(::File)?.takeIf { it.isDirectory }
            ?: return List(segmentCount) { SEGMENT_TRANSITION_PAUSE_MS }
        val metadata = File(root, "segments.tsv")
        if (!metadata.isFile) return List(segmentCount) { SEGMENT_TRANSITION_PAUSE_MS }
        val pauses = MutableList(segmentCount) { SEGMENT_TRANSITION_PAUSE_MS }
        runCatching {
            metadata.readLines()
                .drop(1)
                .forEach { line ->
                    val columns = line.split('\t')
                    val index = columns.getOrNull(0)?.toIntOrNull() ?: return@forEach
                    val pause = columns.getOrNull(2)?.toLongOrNull() ?: return@forEach
                    if (index in pauses.indices) {
                        pauses[index] = pause.coerceIn(MIN_SEGMENT_TRANSITION_PAUSE_MS, MAX_SEGMENT_TRANSITION_PAUSE_MS)
                    }
                }
        }.onFailure { error ->
            Log.w("XReader", "Could not read audiobook segment timing metadata for $id", error)
        }
        return pauses
    }
}

private data class PreparedAudiobookPlayback(
    val segments: List<File>,
    val chapters: List<GeneratedAudiobookChapter>,
    val segmentChapterIndexes: List<Int>,
    val segmentPauseMillis: List<Long>,
)

internal fun BookAudioEntity.profileLabel(): String =
    listOf(
        modelDisplayName,
        scopeLabel.takeUnless { it.equals("Full book", ignoreCase = true) },
        tone.lowercase().replaceFirstChar { it.titlecase() },
        "%.2fx".format(java.util.Locale.US, speed)
    ).filterNotNull().joinToString(" ")

private const val POSITION_SAVE_INTERVAL_MS = 5_000L
private const val SEGMENT_TRANSITION_PAUSE_MS = 220L
private const val MIN_SEGMENT_TRANSITION_PAUSE_MS = 120L
private const val MAX_SEGMENT_TRANSITION_PAUSE_MS = 900L
private const val CHAPTER_TRANSITION_PAUSE_MS = 850L
