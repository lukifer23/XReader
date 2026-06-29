package com.xreader.app.tts

internal fun compactNotificationActionIndexes(actionCount: Int): IntArray {
    val boundedCount = actionCount.coerceAtLeast(0)
    val compactCount = boundedCount.coerceAtMost(3)
    val start = boundedCount - compactCount
    return IntArray(compactCount) { offset -> start + offset }
}
