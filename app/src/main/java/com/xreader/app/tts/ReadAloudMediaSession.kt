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

internal interface ReadAloudMediaSessionCallbacks {
    fun onPlayRequested()
    fun onPauseRequested()
    fun onPlayPauseRequested()
    fun onStopRequested()
    fun onSkipToPreviousRequested()
    fun onSkipToNextRequested()
}

internal class ReadAloudMediaSessionController(
    context: Context,
    callbacks: ReadAloudMediaSessionCallbacks,
) {
    private val session = MediaSession(context.applicationContext, "XReader read aloud").apply {
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
                    handleReadAloudMediaButton(mediaButtonIntent, callbacks)
            },
            Handler(Looper.getMainLooper())
        )
        setPlaybackState(stoppedPlaybackState())
        isActive = false
    }

    fun update(
        bookTitle: String,
        heading: String?,
        playing: Boolean,
        paused: Boolean,
        currentChunk: Int,
        totalChunks: Int,
    ) {
        if (!playing && !paused) {
            stop()
            return
        }
        session.setMetadata(readAloudMediaMetadata(bookTitle = bookTitle, heading = heading))
        session.setPlaybackState(
            readAloudPlaybackState(
                playing = playing,
                paused = paused,
                currentChunk = currentChunk,
                totalChunks = totalChunks
            )
        )
        session.isActive = true
    }

    fun stop() {
        session.setPlaybackState(stoppedPlaybackState())
        session.isActive = false
    }

    fun release() {
        session.setPlaybackState(stoppedPlaybackState())
        session.isActive = false
        session.release()
    }
}

internal fun readAloudMediaActions(
    playing: Boolean,
    paused: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
): Long {
    if (!playing && !paused) return 0L
    var actions = PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP
    if (playing) actions = actions or PlaybackState.ACTION_PAUSE
    if (paused) actions = actions or PlaybackState.ACTION_PLAY
    if (canSkipPrevious) actions = actions or PlaybackState.ACTION_SKIP_TO_PREVIOUS
    if (canSkipNext) actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
    return actions
}

private fun readAloudPlaybackState(
    playing: Boolean,
    paused: Boolean,
    currentChunk: Int,
    totalChunks: Int,
): PlaybackState {
    val boundedChunk = if (totalChunks <= 0) 0 else currentChunk.coerceIn(0, totalChunks - 1)
    val state = when {
        playing -> PlaybackState.STATE_PLAYING
        paused -> PlaybackState.STATE_PAUSED
        else -> PlaybackState.STATE_STOPPED
    }
    return PlaybackState.Builder()
        .setActions(
            readAloudMediaActions(
                playing = playing,
                paused = paused,
                canSkipPrevious = boundedChunk > 0,
                canSkipNext = totalChunks > 0 && boundedChunk < totalChunks - 1
            )
        )
        .setState(state, boundedChunk.toLong(), if (playing) 1f else 0f)
        .build()
}

private fun readAloudMediaMetadata(bookTitle: String, heading: String?): MediaMetadata =
    MediaMetadata.Builder()
        .putString(MediaMetadata.METADATA_KEY_TITLE, bookTitle)
        .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, bookTitle)
        .apply {
            heading
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, it)
                }
        }
        .build()

private fun stoppedPlaybackState(): PlaybackState =
    PlaybackState.Builder()
        .setActions(0L)
        .setState(PlaybackState.STATE_STOPPED, 0L, 0f)
        .build()

@Suppress("DEPRECATION")
private fun handleReadAloudMediaButton(
    intent: Intent?,
    callbacks: ReadAloudMediaSessionCallbacks,
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
