package com.xreader.app.analytics

import com.xreader.app.data.ReadingSessionEntity
import kotlin.math.roundToInt

internal const val MIN_READING_SESSION_ACTIVE_MILLIS = 15_000L
internal const val MIN_RELIABLE_WPM_ACTIVE_MILLIS = 30_000L
private const val MIN_RELIABLE_WPM_WORDS = 40
private const val MIN_RELIABLE_WPM = 20.0
private const val MAX_RELIABLE_WPM = 900.0

internal fun estimatedReadingWpm(wordsRead: Int, activeMillis: Long): Int {
    val raw = rawReadingWpm(wordsRead = wordsRead, activeMillis = activeMillis) ?: return 0
    if (raw !in MIN_RELIABLE_WPM..MAX_RELIABLE_WPM) return 0
    return raw.roundToInt()
}

internal fun ReadingSessionEntity.isReliablePaceSample(): Boolean =
    estimatedReadingWpm(wordsRead = wordsRead, activeMillis = activeMillis) > 0

private fun rawReadingWpm(wordsRead: Int, activeMillis: Long): Double? {
    if (activeMillis < MIN_RELIABLE_WPM_ACTIVE_MILLIS || wordsRead < MIN_RELIABLE_WPM_WORDS) return null
    val minutes = activeMillis / 60_000.0
    if (minutes <= 0.0) return null
    return wordsRead / minutes
}
