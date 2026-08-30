package com.xreader.app.tts

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import com.xreader.app.R
import com.xreader.app.XReaderApplication
import com.xreader.app.audiobook.ImportedAudiobookPlaybackController
import com.xreader.app.audiobook.ImportedAudiobookPlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ImportedAudiobookForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val playback: ImportedAudiobookPlaybackController
        get() = (application as XReaderApplication).container.importedAudiobookPlayback
    private lateinit var mediaSession: MediaSession
    private var ticker: Job? = null
    private var foreground = false

    override fun onCreate() {
        super.onCreate()
        notificationManager.ensureLowImportanceChannel(CHANNEL_ID, "Imported audiobooks", "Imported audiobook playback controls")
        mediaSession = MediaSession(this, "XReader imported audiobook").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = playback.resume()
                override fun onPause() = playback.pause()
                override fun onStop() = playback.stop()
                override fun onSkipToNext() = playback.skipNext()
                override fun onSkipToPrevious() = playback.skipPrevious()
                override fun onSeekTo(pos: Long) = playback.seekTo(pos.coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
            })
            isActive = true
        }
        serviceScope.launch { playback.state.collectLatest(::render) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> playback.resume()
            ACTION_PAUSE -> playback.pause()
            ACTION_STOP -> playback.stop()
            ACTION_PREVIOUS -> playback.skipPrevious()
            ACTION_NEXT -> playback.skipNext()
        }
        startForegroundCompat(buildNotification(playback.state.value))
        foreground = true
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        ticker?.cancel()
        mediaSession.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun render(state: ImportedAudiobookPlaybackState) {
        updateMediaSession(state)
        ticker?.cancel()
        if (state.playing) ticker = serviceScope.launch {
            while (true) {
                delay(1_000)
                playback.refreshPosition()
            }
        }
        if (!state.active) {
            foreground = stopForegroundAndSelfCompat(foreground, removeNotification = true)
            return
        }
        val notification = buildNotification(state)
        if (!foreground) {
            startForegroundCompat(notification)
            foreground = true
        } else {
            postForegroundNotificationUpdate(this, notificationManager, NOTIFICATION_ID, notification)
        }
    }

    private fun updateMediaSession(state: ImportedAudiobookPlaybackState) {
        var actions = PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP or PlaybackState.ACTION_SEEK_TO
        if (state.trackIndex > 0) actions = actions or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        if (state.trackIndex < state.trackCount - 1) actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
        val playbackState = when {
            state.playing -> PlaybackState.STATE_PLAYING
            state.preparing -> PlaybackState.STATE_BUFFERING
            state.paused -> PlaybackState.STATE_PAUSED
            else -> PlaybackState.STATE_STOPPED
        }
        mediaSession.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(playbackState, state.positionMs.toLong(), if (state.playing) state.speed else 0f)
                .build()
        )
    }

    private fun buildNotification(state: ImportedAudiobookPlaybackState): Notification {
        val actions = mutableListOf<Notification.Action>()
        if (state.trackIndex > 0) actions += action(android.R.drawable.ic_media_previous, "Previous", ACTION_PREVIOUS, 1)
        actions += if (state.playing) action(android.R.drawable.ic_media_pause, "Pause", ACTION_PAUSE, 2)
            else action(android.R.drawable.ic_media_play, "Play", ACTION_PLAY, 3)
        actions += action(android.R.drawable.ic_menu_close_clear_cancel, "Stop", ACTION_STOP, 4)
        if (state.trackIndex < state.trackCount - 1) actions += action(android.R.drawable.ic_media_next, "Next", ACTION_NEXT, 5)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(state.title.ifBlank { "XReader audiobook" })
            .setContentText(if (state.error != null) state.error else "Track ${state.trackIndex + 1} of ${state.trackCount}")
            .setContentIntent(openXReaderIntent(6))
            .setOngoing(state.playing)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(Notification.MediaStyle().setMediaSession(mediaSession.sessionToken).setShowActionsInCompactView(*compactNotificationActionIndexes(actions.size)))
            .apply { actions.forEach(::addAction) }
            .build()
    }

    private fun action(icon: Int, title: String, action: String, requestCode: Int): Notification.Action =
        Notification.Action.Builder(
            Icon.createWithResource(this, icon),
            title,
            PendingIntent.getService(this, requestCode, Intent(this, ImportedAudiobookForegroundService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
        ).build()

    @SuppressLint("InlinedApi")
    private fun startForegroundCompat(notification: Notification) {
        startForegroundWithTypeCompat(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    }

    private val notificationManager get() = getSystemService(android.app.NotificationManager::class.java)

    companion object {
        private const val CHANNEL_ID = "imported_audiobooks"
        private const val NOTIFICATION_ID = 42_402
        private const val ACTION_START = "com.xreader.app.tts.IMPORTED_AUDIOBOOK_START"
        private const val ACTION_PLAY = "com.xreader.app.tts.IMPORTED_AUDIOBOOK_PLAY"
        private const val ACTION_PAUSE = "com.xreader.app.tts.IMPORTED_AUDIOBOOK_PAUSE"
        private const val ACTION_STOP = "com.xreader.app.tts.IMPORTED_AUDIOBOOK_STOP"
        private const val ACTION_PREVIOUS = "com.xreader.app.tts.IMPORTED_AUDIOBOOK_PREVIOUS"
        private const val ACTION_NEXT = "com.xreader.app.tts.IMPORTED_AUDIOBOOK_NEXT"

        fun start(context: Context) {
            androidx.core.content.ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(context.applicationContext, ImportedAudiobookForegroundService::class.java).setAction(ACTION_START),
            )
        }
    }
}
