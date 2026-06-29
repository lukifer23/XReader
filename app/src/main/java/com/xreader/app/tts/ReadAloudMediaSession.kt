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
    private var lastMetadataKey: ReadAloudMetadataKey? = null
    private var lastPlaybackStateKey: ReadAloudPlaybackStateKey? = null
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
    val token: MediaSession.Token
        get() = session.sessionToken

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
        val metadataKey = readAloudMetadataKey(bookTitle = bookTitle, heading = heading)
        if (metadataKey != lastMetadataKey) {
            session.setMetadata(readAloudMediaMetadata(bookTitle = bookTitle, heading = heading))
            lastMetadataKey = metadataKey
        }
        val playbackStateKey = readAloudPlaybackStateKey(
            playing = playing,
            paused = paused,
            currentChunk = currentChunk,
            totalChunks = totalChunks
        )
        if (playbackStateKey != lastPlaybackStateKey) {
            session.setPlaybackState(
                readAloudPlaybackState(
                    key = playbackStateKey
                )
            )
            lastPlaybackStateKey = playbackStateKey
        }
        session.isActive = true
    }

    fun stop() {
        lastMetadataKey = null
        lastPlaybackStateKey = null
        session.setPlaybackState(stoppedPlaybackState())
        session.isActive = false
    }

    fun release() {
        lastMetadataKey = null
        lastPlaybackStateKey = null
        session.setPlaybackState(stoppedPlaybackState())
        session.isActive = false
        session.release()
    }
}

internal data class ReadAloudMetadataKey(
    val bookTitle: String,
    val heading: String?,
)

internal data class ReadAloudPlaybackStateKey(
    val playing: Boolean,
    val paused: Boolean,
    val boundedChunk: Int,
    val totalChunks: Int,
)

internal fun readAloudMetadataKey(bookTitle: String, heading: String?): ReadAloudMetadataKey =
    ReadAloudMetadataKey(
        bookTitle = bookTitle,
        heading = normalizedTtsMediaSubtitle(heading),
    )

internal fun readAloudPlaybackStateKey(
    playing: Boolean,
    paused: Boolean,
    currentChunk: Int,
    totalChunks: Int,
): ReadAloudPlaybackStateKey {
    val boundedTotal = totalChunks.coerceAtLeast(0)
    val boundedChunk = if (boundedTotal <= 0) 0 else currentChunk.coerceIn(0, boundedTotal - 1)
    return ReadAloudPlaybackStateKey(
        playing = playing,
        paused = paused,
        boundedChunk = boundedChunk,
        totalChunks = boundedTotal
    )
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

private fun readAloudPlaybackState(key: ReadAloudPlaybackStateKey): PlaybackState {
    val state = when {
        key.playing -> PlaybackState.STATE_PLAYING
        key.paused -> PlaybackState.STATE_PAUSED
        else -> PlaybackState.STATE_STOPPED
    }
    return PlaybackState.Builder()
        .setActions(
            readAloudMediaActions(
                playing = key.playing,
                paused = key.paused,
                canSkipPrevious = key.boundedChunk > 0,
                canSkipNext = key.totalChunks > 0 && key.boundedChunk < key.totalChunks - 1
            )
        )
        .setState(state, key.boundedChunk.toLong(), if (key.playing) 1f else 0f)
        .build()
}

private fun readAloudMediaMetadata(bookTitle: String, heading: String?): MediaMetadata =
    MediaMetadata.Builder()
        .putString(MediaMetadata.METADATA_KEY_TITLE, bookTitle)
        .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, bookTitle)
        .apply {
            normalizedTtsMediaSubtitle(heading)
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
