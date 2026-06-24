package com.xreader.app.tts

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

internal enum class ForegroundNotificationOperation {
    START_FOREGROUND,
    UPDATE_NOTIFICATION,
}

internal fun foregroundNotificationOperation(foregroundStarted: Boolean): ForegroundNotificationOperation =
    if (foregroundStarted) {
        ForegroundNotificationOperation.UPDATE_NOTIFICATION
    } else {
        ForegroundNotificationOperation.START_FOREGROUND
    }

internal fun canPostForegroundNotificationUpdate(
    androidApiLevel: Int,
    postNotificationsGranted: Boolean,
): Boolean =
    androidApiLevel < Build.VERSION_CODES.TIRAMISU || postNotificationsGranted

internal fun postForegroundNotificationUpdate(
    context: Context,
    notificationManager: NotificationManager,
    notificationId: Int,
    notification: Notification,
) {
    val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
    if (canPostForegroundNotificationUpdate(Build.VERSION.SDK_INT, granted)) {
        notificationManager.notify(notificationId, notification)
    }
}
