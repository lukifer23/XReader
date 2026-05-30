package com.xreader.app.ui

import com.xreader.app.tts.ReadAloudState
import kotlin.math.ceil

internal val ReadAloudState.active: Boolean
    get() = playing || paused || initializing

internal fun readAloudStatusText(readAloud: ReadAloudState): String {
    val progress = readAloudProgressText(readAloud)
    val sleepTimer = readAloudSleepTimerText(readAloud)
    val heading = readAloud.currentHeading
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    val status = if (readAloud.paused) "Paused" else "Read aloud"
    return listOfNotNull(status, progress, sleepTimer, heading).joinToString(" - ")
}

internal fun readAloudProgressText(readAloud: ReadAloudState): String? {
    val total = readAloud.totalChunks
    if (total <= 0) return null
    val current = readAloud.currentChunk.coerceIn(0, total - 1) + 1
    return "$current/$total"
}

internal fun readAloudCanSkipPrevious(readAloud: ReadAloudState): Boolean =
    (readAloud.playing || readAloud.paused) && readAloud.currentChunk > 0

internal fun readAloudCanSkipNext(readAloud: ReadAloudState): Boolean =
    (readAloud.playing || readAloud.paused) && readAloud.totalChunks > 0 && readAloud.currentChunk < readAloud.totalChunks - 1

internal fun readAloudSleepTimerText(
    readAloud: ReadAloudState,
    nowMillis: Long = System.currentTimeMillis(),
): String? {
    val remainingMillis = readAloud.sleepTimerRemainingMillis
        ?: readAloud.sleepTimerEndsAtMillis?.let { it - nowMillis }
        ?: return null
    if (remainingMillis <= 0L) return "Sleep in <1m"
    val minutes = ceil(remainingMillis / 60_000.0).toLong().coerceAtLeast(1L)
    return "Sleep in ${formatSleepTimerMinutes(minutes)}"
}

private fun formatSleepTimerMinutes(minutes: Long): String {
    if (minutes < 60L) return "${minutes}m"
    val hours = minutes / 60L
    val remaining = minutes % 60L
    return if (remaining == 0L) "${hours}h" else "${hours}h ${remaining}m"
}
