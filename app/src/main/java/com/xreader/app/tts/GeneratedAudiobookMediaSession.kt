package com.xreader.app.tts

import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent

internal interface GeneratedAudiobookMediaCallbacks {
    fun onPlayRequested()
    fun onPauseRequested()
    fun onPlayPauseRequested()
    fun onStopRequested()
    fun onSkipToPreviousRequested()
    fun onSkipToNextRequested()
}

internal class GeneratedAudiobookMediaSessionController(
    context: Context,
    private val callbacks: GeneratedAudiobookMediaCallbacks,
) {
    private var lastMetadataKey: GeneratedAudiobookMetadataKey? = null
    private var lastPlaybackStateKey: GeneratedAudiobookPlaybackStateKey? = null
    private var stopped = true
    private var active = false
    private val session = MediaSession(context.applicationContext, "XReader generated audiobook").apply {
        @Suppress("DEPRECATION")
        setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        setCallback(
            object : MediaSession.Callback() {
                override fun onPlay() = callbacks.onPlayRequested()
                override fun onPause() = callbacks.onPauseRequested()
                override fun onStop() = callbacks.onStopRequested()
                override fun onSkipToPrevious() = callbacks.onSkipToPreviousRequested()
                override fun onSkipToNext() = callbacks.onSkipToNextRequested()
                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) = callbacks.onPlayRequested()
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean =
                    handleGeneratedAudiobookMediaButton(mediaButtonIntent, callbacks)
            },
            Handler(Looper.getMainLooper())
        )
        setPlaybackState(generatedAudiobookStoppedPlaybackState())
        isActive = false
    }

    val token: MediaSession.Token
        get() = session.sessionToken

    fun update(state: AudiobookPlaybackUiState) {
        if (!state.foregroundActive) {
            stop()
            return
        }
        val metadataKey = state.generatedAudiobookMetadataKey()
        if (metadataKey != lastMetadataKey) {
            session.setMetadata(generatedAudiobookMetadata(state))
            lastMetadataKey = metadataKey
        }
        val playbackStateKey = state.generatedAudiobookPlaybackStateKey()
        if (playbackStateKey != lastPlaybackStateKey) {
            session.setPlaybackState(generatedAudiobookPlaybackState(state))
            lastPlaybackStateKey = playbackStateKey
        }
        if (!active) {
            session.isActive = true
            active = true
        }
        stopped = false
    }

    fun stop() {
        if (stopped) return
        lastMetadataKey = null
        lastPlaybackStateKey = null
        session.setPlaybackState(generatedAudiobookStoppedPlaybackState())
        if (active) {
            session.isActive = false
            active = false
        }
        stopped = true
    }

    fun release() {
        stop()
        session.release()
    }
}

internal data class GeneratedAudiobookMetadataKey(
    val bookTitle: String?,
    val profileLabel: String?,
    val segmentDurationMs: Int,
)

internal fun AudiobookPlaybackUiState.generatedAudiobookMetadataKey(): GeneratedAudiobookMetadataKey =
    GeneratedAudiobookMetadataKey(
        bookTitle = bookTitle,
        profileLabel = profileLabel,
        segmentDurationMs = segmentDurationMs.coerceAtLeast(0),
    )

internal data class GeneratedAudiobookPlaybackStateKey(
    val foregroundActive: Boolean,
    val playing: Boolean,
    val segmentIndex: Int,
    val segmentCount: Int,
    val segmentDurationMs: Int,
)

internal fun AudiobookPlaybackUiState.generatedAudiobookPlaybackStateKey(): GeneratedAudiobookPlaybackStateKey =
    GeneratedAudiobookPlaybackStateKey(
        foregroundActive = foregroundActive,
        playing = playing,
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        segmentDurationMs = segmentDurationMs.coerceAtLeast(0),
    )

private fun generatedAudiobookPlaybackState(state: AudiobookPlaybackUiState): PlaybackState {
    val total = state.segmentCount
    val boundedSegment = if (total <= 0) 0 else state.segmentIndex.coerceIn(0, total - 1)
    val positionMs = when {
        state.segmentDurationMs > 0 -> state.segmentPositionMs.coerceIn(0, state.segmentDurationMs)
        else -> state.segmentPositionMs.coerceAtLeast(0)
    }
    val playbackState = if (state.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
    return PlaybackState.Builder()
        .setActions(generatedAudiobookMediaActions(state, boundedSegment))
        .setState(playbackState, positionMs.toLong(), if (state.playing) 1f else 0f)
        .build()
}

private fun generatedAudiobookMediaActions(state: AudiobookPlaybackUiState, boundedSegment: Int): Long {
    if (!state.foregroundActive) return 0L
    var actions = PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP
    if (state.playing) actions = actions or PlaybackState.ACTION_PAUSE
    if (state.paused) actions = actions or PlaybackState.ACTION_PLAY
    if (boundedSegment > 0) actions = actions or PlaybackState.ACTION_SKIP_TO_PREVIOUS
    if (state.segmentCount > 0 && boundedSegment < state.segmentCount - 1) actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
    return actions
}

private fun generatedAudiobookMetadata(state: AudiobookPlaybackUiState): MediaMetadata =
    MediaMetadata.Builder()
        .putString(MediaMetadata.METADATA_KEY_TITLE, state.bookTitle ?: "XReader audiobook")
        .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, state.bookTitle ?: "XReader audiobook")
        .apply {
            if (state.segmentDurationMs > 0) {
                putLong(MediaMetadata.METADATA_KEY_DURATION, state.segmentDurationMs.toLong())
            }
            state.profileLabel
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, it) }
        }
        .build()

private fun generatedAudiobookStoppedPlaybackState(): PlaybackState =
    PlaybackState.Builder()
        .setActions(0L)
        .setState(PlaybackState.STATE_STOPPED, 0L, 0f)
        .build()

@Suppress("DEPRECATION")
private fun handleGeneratedAudiobookMediaButton(
    intent: Intent?,
    callbacks: GeneratedAudiobookMediaCallbacks,
): Boolean {
    val event = intent?.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return false
    if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return true
    when (event.keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY -> callbacks.onPlayRequested()
        KeyEvent.KEYCODE_MEDIA_PAUSE -> callbacks.onPauseRequested()
        KeyEvent.KEYCODE_MEDIA_STOP -> callbacks.onStopRequested()
        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        KeyEvent.KEYCODE_MEDIA_REWIND,
        -> callbacks.onSkipToPreviousRequested()
        KeyEvent.KEYCODE_MEDIA_NEXT,
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
        -> callbacks.onSkipToNextRequested()
        KeyEvent.KEYCODE_HEADSETHOOK,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        -> callbacks.onPlayPauseRequested()
        else -> return false
    }
    return true
}
