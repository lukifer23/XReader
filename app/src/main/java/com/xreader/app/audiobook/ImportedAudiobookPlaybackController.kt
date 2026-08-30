package com.xreader.app.audiobook

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import com.xreader.app.tts.ImportedAudiobookForegroundService
import com.xreader.app.core.releaseQuietlyAsync
import com.xreader.app.core.speechMediaPlayerForFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class ImportedAudiobookPlaybackState(
    val audiobookId: Long? = null,
    val title: String = "",
    val trackIndex: Int = 0,
    val trackCount: Int = 0,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val speed: Float = 1f,
    val preparing: Boolean = false,
    val playing: Boolean = false,
    val paused: Boolean = false,
    val error: String? = null,
) {
    val active: Boolean get() = audiobookId != null
}

class ImportedAudiobookPlaybackController(
    context: Context,
    private val repository: AudiobookRepository,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(audioAttributes)
        .setOnAudioFocusChangeListener { change ->
            if (change <= AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pause()
        }.build()
    private val _state = MutableStateFlow(ImportedAudiobookPlaybackState())
    val state: StateFlow<ImportedAudiobookPlaybackState> = _state.asStateFlow()
    private var player: MediaPlayer? = null
    private var activePackage: ImportedAudiobookPackage? = null

    fun play(audiobook: ImportedAudiobookPackage, trackIndex: Int = audiobook.audiobook.playbackTrackIndex, positionMs: Int = audiobook.audiobook.playbackPositionMs) {
        require(audiobook.tracks.isNotEmpty()) { "Audiobook has no imported tracks." }
        val index = trackIndex.coerceIn(0, audiobook.tracks.lastIndex)
        activePackage = audiobook
        startTrack(audiobook, index, positionMs.coerceAtLeast(0), autoplay = true)
    }

    fun resume() {
        val current = player ?: return
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        runCatching { current.start() }
            .onSuccess { _state.value = _state.value.copy(playing = true, paused = false, preparing = false, error = null) }
            .onFailure { _state.value = _state.value.copy(error = it.message, playing = false) }
    }

    fun pause() {
        val current = player ?: return
        runCatching { if (current.isPlaying) current.pause() }
        val position = runCatching { current.currentPosition }.getOrDefault(_state.value.positionMs)
        _state.value = _state.value.copy(positionMs = position, playing = false, paused = true, preparing = false)
        persistPosition()
    }

    fun stop() {
        persistPosition()
        player?.releaseQuietlyAsync(scope)
        player = null
        activePackage = null
        audioManager.abandonAudioFocusRequest(focusRequest)
        _state.value = ImportedAudiobookPlaybackState()
    }

    fun skipNext() {
        val audiobook = activePackage ?: return
        val next = (_state.value.trackIndex + 1).coerceAtMost(audiobook.tracks.lastIndex)
        if (next != _state.value.trackIndex) startTrack(audiobook, next, 0, autoplay = true)
    }

    fun skipPrevious() {
        val audiobook = activePackage ?: return
        val position = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)
        val previous = if (position > RESTART_TRACK_THRESHOLD_MS) _state.value.trackIndex else (_state.value.trackIndex - 1).coerceAtLeast(0)
        startTrack(audiobook, previous, 0, autoplay = true)
    }

    fun seekTo(positionMs: Int) {
        val current = player ?: return
        val bounded = positionMs.coerceIn(0, _state.value.durationMs.coerceAtLeast(0))
        runCatching { current.seekTo(bounded) }
        _state.value = _state.value.copy(positionMs = bounded)
        persistPosition()
    }

    fun setSpeed(speed: Float) {
        val bounded = speed.coerceIn(.5f, 3f)
        runCatching { player?.playbackParams = PlaybackParams().setSpeed(bounded) }
        _state.value = _state.value.copy(speed = bounded)
    }

    fun refreshPosition() {
        val current = player ?: return
        val position = runCatching { current.currentPosition }.getOrNull() ?: return
        if (position / POSITION_PERSIST_STEP_MS != _state.value.positionMs / POSITION_PERSIST_STEP_MS) {
            _state.value = _state.value.copy(positionMs = position)
            persistPosition()
        }
    }

    private fun startTrack(audiobook: ImportedAudiobookPackage, index: Int, positionMs: Int, autoplay: Boolean) {
        player?.releaseQuietlyAsync(scope)
        player = null
        val track = audiobook.tracks[index]
        val file = File(audiobook.audiobook.filePath, track.relativePath)
        require(file.isFile) { "Audiobook track is missing. Reimport the original audio to repair it." }
        val created = speechMediaPlayerForFile(file, AudioAttributes.USAGE_MEDIA)
        player = created
        _state.value = ImportedAudiobookPlaybackState(
            audiobookId = audiobook.audiobook.id,
            title = audiobook.audiobook.title,
            trackIndex = index,
            trackCount = audiobook.tracks.size,
            positionMs = positionMs,
            durationMs = track.durationMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            speed = audiobook.audiobook.playbackSpeed,
            preparing = true,
        )
        ImportedAudiobookForegroundService.start(appContext)
        created.setOnPreparedListener { prepared ->
            runCatching {
                prepared.playbackParams = PlaybackParams().setSpeed(_state.value.speed)
                if (positionMs > 0) prepared.seekTo(positionMs.coerceAtMost(_state.value.durationMs))
                if (autoplay && audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) prepared.start()
            }.onSuccess {
                _state.value = _state.value.copy(preparing = false, playing = autoplay, paused = !autoplay, error = null)
            }.onFailure { error ->
                _state.value = _state.value.copy(preparing = false, playing = false, error = error.message)
            }
        }
        created.setOnCompletionListener {
            persistPosition()
            if (index < audiobook.tracks.lastIndex) startTrack(audiobook, index + 1, 0, autoplay = true)
            else _state.value = _state.value.copy(positionMs = _state.value.durationMs, playing = false, paused = true)
        }
        created.setOnErrorListener { _, what, extra ->
            _state.value = _state.value.copy(preparing = false, playing = false, error = "Audio playback failed ($what/$extra).")
            true
        }
        created.prepareAsync()
    }

    private fun persistPosition() {
        val id = _state.value.audiobookId ?: return
        val track = _state.value.trackIndex
        val position = runCatching { player?.currentPosition ?: _state.value.positionMs }.getOrDefault(_state.value.positionMs)
        scope.launch(Dispatchers.IO) { repository.updatePlayback(id, track, position) }
    }

    companion object {
        private const val POSITION_PERSIST_STEP_MS = 5_000
        private const val RESTART_TRACK_THRESHOLD_MS = 5_000
    }
}
