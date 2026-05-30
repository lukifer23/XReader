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
import kotlinx.coroutines.launch

class ReadAloudForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val engine: ReadAloudEngine
        get() = (application as XReaderApplication).container.readAloudEngine
    private var foregroundStarted = false
    private var startCommandReceived = false

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        serviceScope.launch {
            engine.state.collectLatest(::renderState)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startCommandReceived = true
        startForegroundIfNeeded(engine.state.value)
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
            ACTION_PLAY -> engine.resume()
            ACTION_PAUSE -> engine.pause()
            ACTION_STOP -> {
                engine.stop()
                stopForegroundAndSelf(removeNotification = true)
            }
            ACTION_PREVIOUS -> engine.skipToPrevious()
            ACTION_NEXT -> engine.skipToNext()
        }
    }

    private fun renderState(state: ReadAloudState) {
        if (!startCommandReceived && !state.readAloudForegroundActive) return
        if (!state.readAloudForegroundActive) {
            stopForegroundAndSelf(removeNotification = true)
            return
        }
        val notification = buildReadAloudNotification(state)
        if (foregroundStarted) {
            startForegroundCompat(notification)
        } else {
            startForegroundCompat(notification)
            foregroundStarted = true
        }
    }

    private fun startForegroundIfNeeded(state: ReadAloudState) {
        if (foregroundStarted) return
        startForegroundCompat(buildReadAloudNotification(state))
        foregroundStarted = true
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundAndSelf(removeNotification: Boolean) {
        if (foregroundStarted) {
            stopForeground(
                if (removeNotification) {
                    Service.STOP_FOREGROUND_REMOVE
                } else {
                    Service.STOP_FOREGROUND_DETACH
                }
            )
            foregroundStarted = false
        }
        stopSelf()
    }

    private fun buildReadAloudNotification(state: ReadAloudState): Notification {
        val actions = readAloudNotificationActions(state)
        val compactIndexes = actions.indices
            .toList()
            .takeLast(actions.size.coerceAtMost(3))
            .toIntArray()
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(state.bookTitle?.takeIf { it.isNotBlank() } ?: "XReader read aloud")
            .setContentText(readAloudNotificationStatusText(state))
            .setSubText(readAloudNotificationProgressText(state))
            .setContentIntent(openAppIntent())
            .setDeleteIntent(serviceIntent(ACTION_STOP, REQUEST_STOP))
            .setOngoing(state.playing || state.initializing)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(engine.mediaSessionToken)
                    .setShowActionsInCompactView(*compactIndexes)
            )
            .apply { actions.forEach(::addAction) }
            .build()
    }

    private fun readAloudNotificationActions(state: ReadAloudState): List<Action> {
        if (!state.readAloudForegroundActive) return emptyList()
        val actions = mutableListOf<Action>()
        if (readAloudNotificationCanSkipPrevious(state)) {
            actions += notificationAction(
                icon = android.R.drawable.ic_media_previous,
                title = "Previous",
                action = ACTION_PREVIOUS,
                requestCode = REQUEST_PREVIOUS
            )
        }
        actions += if (state.playing || state.initializing) {
            notificationAction(
                icon = android.R.drawable.ic_media_pause,
                title = "Pause",
                action = ACTION_PAUSE,
                requestCode = REQUEST_PAUSE
            )
        } else {
            notificationAction(
                icon = android.R.drawable.ic_media_play,
                title = "Play",
                action = ACTION_PLAY,
                requestCode = REQUEST_PLAY
            )
        }
        actions += notificationAction(
            icon = android.R.drawable.ic_menu_close_clear_cancel,
            title = "Stop",
            action = ACTION_STOP,
            requestCode = REQUEST_STOP
        )
        if (readAloudNotificationCanSkipNext(state)) {
            actions += notificationAction(
                icon = android.R.drawable.ic_media_next,
                title = "Next",
                action = ACTION_NEXT,
                requestCode = REQUEST_NEXT
            )
        }
        return actions
    }

    private fun notificationAction(
        icon: Int,
        title: String,
        action: String,
        requestCode: Int,
    ): Action =
        Action.Builder(
            Icon.createWithResource(this, icon),
            title,
            serviceIntent(action, requestCode)
        ).build()

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, ReadAloudForegroundService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            REQUEST_OPEN_APP,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun ensureNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Read aloud",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Read-aloud playback controls"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private val notificationManager: NotificationManager
        get() = getSystemService(NotificationManager::class.java)

    companion object {
        private const val CHANNEL_ID = "read_aloud"
        private const val NOTIFICATION_ID = 42_301
        private const val ACTION_START = "com.xreader.app.tts.START"
        private const val ACTION_PLAY = "com.xreader.app.tts.PLAY"
        private const val ACTION_PAUSE = "com.xreader.app.tts.PAUSE"
        private const val ACTION_STOP = "com.xreader.app.tts.STOP"
        private const val ACTION_PREVIOUS = "com.xreader.app.tts.PREVIOUS"
        private const val ACTION_NEXT = "com.xreader.app.tts.NEXT"
        private const val REQUEST_OPEN_APP = 42_310
        private const val REQUEST_PLAY = 42_311
        private const val REQUEST_PAUSE = 42_312
        private const val REQUEST_STOP = 42_313
        private const val REQUEST_PREVIOUS = 42_314
        private const val REQUEST_NEXT = 42_315

        fun start(context: Context) {
            val intent = Intent(context.applicationContext, ReadAloudForegroundService::class.java)
                .setAction(ACTION_START)
            androidx.core.content.ContextCompat.startForegroundService(context.applicationContext, intent)
        }
    }
}

internal val ReadAloudState.readAloudForegroundActive: Boolean
    get() = initializing || playing || paused

internal fun readAloudNotificationStatusText(state: ReadAloudState): String =
    when {
        state.initializing -> "Preparing read aloud"
        state.paused -> "Paused"
        state.playing -> state.currentHeading
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "Reading aloud"
        else -> "Read aloud stopped"
    }

internal fun readAloudNotificationProgressText(state: ReadAloudState): String? {
    val total = state.totalChunks
    if (total <= 0) return null
    val current = state.currentChunk.coerceIn(0, total - 1) + 1
    return "$current/$total"
}

internal fun readAloudNotificationCanSkipPrevious(state: ReadAloudState): Boolean =
    (state.playing || state.paused) && state.currentChunk > 0

internal fun readAloudNotificationCanSkipNext(state: ReadAloudState): Boolean =
    (state.playing || state.paused) && state.totalChunks > 0 && state.currentChunk < state.totalChunks - 1
