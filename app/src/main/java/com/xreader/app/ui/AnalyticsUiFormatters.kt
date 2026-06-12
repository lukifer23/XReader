package com.xreader.app.ui

import java.util.Locale

internal fun analyticsCountLabel(value: Int): String =
    "%,d".format(Locale.US, value.coerceAtLeast(0))

internal fun analyticsPaceValue(wpm: Int): String =
    if (wpm > 0) "$wpm" else "--"

internal fun analyticsPaceDetail(paceSampleSessions: Int): String =
    when {
        paceSampleSessions <= 0 -> "Reliable pace appears after enough active reading."
        paceSampleSessions == 1 -> "Based on 1 reliable session."
        else -> "Based on ${analyticsCountLabel(paceSampleSessions)} reliable sessions."
    }

internal fun analyticsRowDetail(
    sessions: Int,
    activeMillis: Long,
    wordsRead: Int,
    averageWpm: Int,
): String =
    listOf(
        "${analyticsCountLabel(sessions)} ${if (sessions == 1) "session" else "sessions"}",
        formatDuration(activeMillis),
        "${analyticsCountLabel(wordsRead)} words",
        if (averageWpm > 0) "$averageWpm WPM" else "pace pending"
    ).joinToString(" • ")
