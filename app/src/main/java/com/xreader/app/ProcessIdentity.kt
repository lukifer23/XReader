package com.xreader.app

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build

internal fun isMainApplicationProcess(context: Context): Boolean =
    isMainApplicationProcess(
        packageName = context.packageName,
        processName = currentProcessName(context)
    )

internal fun isMainApplicationProcess(packageName: String, processName: String?): Boolean =
    processName == null || processName == packageName

private fun currentProcessName(context: Context): String? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        return Application.getProcessName()
    }
    val pid = android.os.Process.myPid()
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    return activityManager
        ?.runningAppProcesses
        ?.firstOrNull { it.pid == pid }
        ?.processName
}
