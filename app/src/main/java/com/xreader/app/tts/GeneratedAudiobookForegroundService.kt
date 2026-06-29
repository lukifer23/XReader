package com.xreader.app.tts

import android.app.Notification
import android.app.Notification.Action
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import com.xreader.app.MainActivity
import com.xreader.app.R
import com.xreader.app.XReaderApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class GeneratedAudiobookForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val playback: GeneratedAudiobookPlaybackController
        get() = (application as XReaderApplication).container.generatedAudiobookPlayback
    private var foregroundStarted = false
    private var startCommandReceived = false

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        serviceScope.launch {
            playback.state
                .distinctUntilChanged(::samePlaybackNotificationState)
                .collectLatest(::renderState)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startCommandReceived = true
        startForegroundIfNeeded(playback.state.value)
        handleAction(intent?.action)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleAction(action: String?) {
        when (action) {
            ACTION_PLAY -> playback.resume()
            ACTION_PAUSE -> playback.pause()
            ACTION_STOP -> {
                playback.stop()
                stopForegroundAndSelf(removeNotification = true)
            }
            ACTION_PREVIOUS -> playback.skipPrevious()
            ACTION_NEXT -> playback.skipNext()
        }
    }

    private fun renderState(state: AudiobookPlaybackUiState) {
        if (!startCommandReceived && !state.foregroundActive) return
        if (!state.foregroundActive) {
            stopForegroundAndSelf(removeNotification = true)
            return
        }
        val notification = buildNotification(state)
        when (foregroundNotificationOperation(foregroundStarted)) {
            ForegroundNotificationOperation.START_FOREGROUND -> {
                startForegroundCompat(notification)
                foregroundStarted = true
            }
            ForegroundNotificationOperation.UPDATE_NOTIFICATION ->
                postForegroundNotificationUpdate(
                    context = this,
                    notificationManager = notificationManager,
                    notificationId = NOTIFICATION_ID,
                    notification = notification
                )
        }
    }

    private fun startForegroundIfNeeded(state: AudiobookPlaybackUiState) {
        if (foregroundStarted) return
        startForegroundCompat(buildNotification(state))
        foregroundStarted = true
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundAndSelf(removeNotification: Boolean) {
        if (foregroundStarted) {
            stopForeground(if (removeNotification) Service.STOP_FOREGROUND_REMOVE else Service.STOP_FOREGROUND_DETACH)
            foregroundStarted = false
        }
        stopSelf()
    }

    private fun buildNotification(state: AudiobookPlaybackUiState): Notification {
        val actions = notificationActions(state)
        val compactIndexes = compactNotificationActionIndexes(actions.size)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(state.bookTitle?.takeIf { it.isNotBlank() } ?: "XReader audiobook")
            .setContentText(notificationStatusText(state))
            .setSubText(notificationProgressText(state))
            .setContentIntent(openAppIntent())
            .setDeleteIntent(serviceIntent(ACTION_STOP, REQUEST_STOP))
            .setOngoing(state.playing)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(playback.mediaSessionToken)
                    .setShowActionsInCompactView(*compactIndexes)
            )
            .apply { actions.forEach(::addAction) }
            .build()
    }

    private fun notificationActions(state: AudiobookPlaybackUiState): List<Action> {
        if (!state.foregroundActive) return emptyList()
        val actions = mutableListOf<Action>()
        if (state.segmentIndex > 0) {
            actions += notificationAction(android.R.drawable.ic_media_previous, "Previous", ACTION_PREVIOUS, REQUEST_PREVIOUS)
        }
        actions += if (state.playing) {
            notificationAction(android.R.drawable.ic_media_pause, "Pause", ACTION_PAUSE, REQUEST_PAUSE)
        } else {
            notificationAction(android.R.drawable.ic_media_play, "Play", ACTION_PLAY, REQUEST_PLAY)
        }
        actions += notificationAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", ACTION_STOP, REQUEST_STOP)
        if (state.segmentCount > 0 && state.segmentIndex < state.segmentCount - 1) {
            actions += notificationAction(android.R.drawable.ic_media_next, "Next", ACTION_NEXT, REQUEST_NEXT)
        }
        return actions
    }

    private fun notificationAction(icon: Int, title: String, action: String, requestCode: Int): Action =
        Action.Builder(Icon.createWithResource(this, icon), title, serviceIntent(action, requestCode)).build()

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, GeneratedAudiobookForegroundService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            REQUEST_OPEN_APP,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun ensureNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Generated audiobooks", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Generated audiobook playback controls"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private val notificationManager: NotificationManager
        get() = getSystemService(NotificationManager::class.java)

    companion object {
        private const val CHANNEL_ID = "generated_audiobooks"
        private const val NOTIFICATION_ID = 42_401
        private const val ACTION_START = "com.xreader.app.tts.AUDIOBOOK_START"
        private const val ACTION_PLAY = "com.xreader.app.tts.AUDIOBOOK_PLAY"
        private const val ACTION_PAUSE = "com.xreader.app.tts.AUDIOBOOK_PAUSE"
        private const val ACTION_STOP = "com.xreader.app.tts.AUDIOBOOK_STOP"
        private const val ACTION_PREVIOUS = "com.xreader.app.tts.AUDIOBOOK_PREVIOUS"
        private const val ACTION_NEXT = "com.xreader.app.tts.AUDIOBOOK_NEXT"
        private const val REQUEST_OPEN_APP = 42_410
        private const val REQUEST_PLAY = 42_411
        private const val REQUEST_PAUSE = 42_412
        private const val REQUEST_STOP = 42_413
        private const val REQUEST_PREVIOUS = 42_414
        private const val REQUEST_NEXT = 42_415

        fun start(context: Context) {
            val intent = Intent(context.applicationContext, GeneratedAudiobookForegroundService::class.java)
                .setAction(ACTION_START)
            androidx.core.content.ContextCompat.startForegroundService(context.applicationContext, intent)
        }
    }
}

internal val AudiobookPlaybackUiState.foregroundActive: Boolean
    get() = active && (playing || paused)

internal fun notificationStatusText(state: AudiobookPlaybackUiState): String =
    when {
        state.playing -> state.profileLabel ?: "Generated audiobook"
        state.paused -> "Paused"
        else -> "Audiobook stopped"
    }

internal fun notificationProgressText(state: AudiobookPlaybackUiState): String? {
    val total = state.segmentCount
    if (total <= 0) return null
    val current = state.segmentIndex.coerceIn(0, total - 1) + 1
    return "$current/$total"
}

private data class PlaybackNotificationKey(
    val foregroundActive: Boolean,
    val audioId: Long?,
    val bookTitle: String?,
    val profileLabel: String?,
    val playing: Boolean,
    val segmentIndex: Int,
    val segmentCount: Int,
    val preparing: Boolean,
    val error: String?,
)

internal fun AudiobookPlaybackUiState.toPlaybackNotificationKey(): Any =
    PlaybackNotificationKey(
        foregroundActive = foregroundActive,
        audioId = audioId,
        bookTitle = bookTitle,
        profileLabel = profileLabel,
        playing = playing,
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        preparing = preparing,
        error = error,
    )

internal fun samePlaybackNotificationState(
    previous: AudiobookPlaybackUiState,
    next: AudiobookPlaybackUiState,
): Boolean =
    previous.foregroundActive == next.foregroundActive &&
        previous.audioId == next.audioId &&
        previous.bookTitle == next.bookTitle &&
        previous.profileLabel == next.profileLabel &&
        previous.playing == next.playing &&
        previous.segmentIndex == next.segmentIndex &&
        previous.segmentCount == next.segmentCount &&
        previous.preparing == next.preparing &&
        previous.error == next.error
