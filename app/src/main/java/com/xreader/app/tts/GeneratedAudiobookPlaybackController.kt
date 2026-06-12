package com.xreader.app.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.xreader.app.data.BookAudioEntity
import com.xreader.app.data.BookAudioStatus
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AudiobookPlaybackUiState(
    val audioId: Long? = null,
    val bookId: Long? = null,
    val bookTitle: String? = null,
    val profileLabel: String? = null,
    val playing: Boolean = false,
    val segmentIndex: Int = 0,
    val segmentCount: Int = 0,
    val segmentPositionMs: Int = 0,
    val segmentDurationMs: Int = 0,
    val error: String? = null,
) {
    val active: Boolean get() = audioId != null
    val paused: Boolean get() = active && !playing && error == null
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
    private var positionSaveJob: Job? = null
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
        if (audio.status != BookAudioStatus.GENERATED) {
            setState(AudiobookPlaybackUiState(error = "Generate this audiobook before playing it."))
            return
        }
        val current = _state.value
        if (current.audioId == audio.id && player != null) {
            resume()
            return
        }

        val segments = runCatching { audio.segmentFiles() }
            .getOrElse { error ->
                Log.e("XReader", "Generated audiobook segments missing for ${audio.id}", error)
                setState(AudiobookPlaybackUiState(audioId = audio.id, bookId = audio.bookId, error = error.message ?: "Generated audio files are missing."))
                return
            }
        releasePlayer()
        activeAudio = audio
        segmentQueue = segments
        val startIndex = audio.playbackSegmentIndex.coerceIn(0, (segments.size - 1).coerceAtLeast(0))
        val startPositionMs = if (audio.playbackSegmentIndex in segments.indices) audio.playbackPositionMs.coerceAtLeast(0) else 0
        startSegment(bookTitle = bookTitle, audio = audio, index = startIndex, startPositionMs = startPositionMs)
        GeneratedAudiobookForegroundService.start(appContext)
    }

    fun resume() {
        val audio = activeAudio ?: return
        val current = _state.value
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
        releasePlayer()
        activeAudio = null
        segmentQueue = emptyList()
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
        releasePlayer()
        activeAudio = null
        segmentQueue = emptyList()
        _state.value = AudiobookPlaybackUiState()
        mediaSession.release()
    }

    private fun startSegment(bookTitle: String, audio: BookAudioEntity, index: Int, startPositionMs: Int = 0) {
        if (index !in segmentQueue.indices) {
            persist(audio.id, 0, 0)
            releasePlayer()
            activeAudio = null
            segmentQueue = emptyList()
            setState(AudiobookPlaybackUiState())
            return
        }
        val file = segmentQueue[index]
        releasePlayer()
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            setDataSource(file.absolutePath)
            setOnCompletionListener {
                persist(audio.id, index + 1, 0)
                startSegment(bookTitle, audio, index + 1)
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
                    error = "Could not play generated audio segment ${index + 1}."
                ))
                true
            }
            prepare()
            if (startPositionMs > 0) seekTo(startPositionMs)
            start()
        }
        persist(audio.id, index, startPositionMs)
        val currentPlayer = requireNotNull(player)
        setState(AudiobookPlaybackUiState(
            audioId = audio.id,
            bookId = audio.bookId,
            bookTitle = bookTitle,
            profileLabel = audio.profileLabel(),
            playing = true,
            segmentIndex = index,
            segmentCount = segmentQueue.size,
            segmentPositionMs = currentPlayer.currentPosition.coerceAtLeast(0),
            segmentDurationMs = currentPlayer.duration.coerceAtLeast(0),
            error = null
        ))
    }

    private fun stopWithError(message: String) {
        val current = _state.value
        val position = runCatching { player?.currentPosition ?: current.segmentPositionMs }.getOrDefault(current.segmentPositionMs)
        persist(current.audioId, current.segmentIndex, position)
        releasePlayer()
        setState(current.copy(playing = false, segmentPositionMs = position.coerceAtLeast(0), error = message))
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
        val root = File(requireNotNull(filePath) { "Generated audio files are missing." })
        require(root.isDirectory) { "Generated audio files are missing." }
        val files = root.listFiles()
            ?.filter { it.isFile && it.extension.equals("wav", ignoreCase = true) && it.name.startsWith("segment-") }
            ?.sortedBy { it.name }
            .orEmpty()
        require(files.isNotEmpty()) { "Generated audio segments are missing." }
        return files
    }
}

internal fun BookAudioEntity.profileLabel(): String =
    listOf(
        modelDisplayName,
        tone.lowercase().replaceFirstChar { it.titlecase() },
        "%.2fx".format(java.util.Locale.US, speed)
    ).joinToString(" ")

private const val POSITION_SAVE_INTERVAL_MS = 5_000L
