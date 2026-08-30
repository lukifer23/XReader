package com.xreader.app.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import com.xreader.app.MainActivity

internal fun Service.startForegroundWithTypeCompat(
    notificationId: Int,
    notification: Notification,
    serviceType: Int,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(notificationId, notification, serviceType)
    } else {
        startForeground(notificationId, notification)
    }
}

internal fun Service.stopForegroundAndSelfCompat(
    foregroundStarted: Boolean,
    removeNotification: Boolean,
): Boolean {
    if (foregroundStarted) {
        stopForeground(if (removeNotification) Service.STOP_FOREGROUND_REMOVE else Service.STOP_FOREGROUND_DETACH)
    }
    stopSelf()
    return false
}

internal fun NotificationManager.ensureLowImportanceChannel(
    id: String,
    name: String,
    description: String,
) {
    createNotificationChannel(
        NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW).apply {
            this.description = description
            setShowBadge(false)
        }
    )
}

internal fun Service.openXReaderIntent(requestCode: Int): PendingIntent =
    PendingIntent.getActivity(
        this,
        requestCode,
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
