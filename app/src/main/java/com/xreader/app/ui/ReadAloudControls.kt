package com.xreader.app.ui

import com.xreader.app.tts.ReadAloudState

internal val ReadAloudState.active: Boolean
    get() = playing || paused || initializing

internal fun readAloudStatusText(readAloud: ReadAloudState): String {
    val progress = readAloudProgressText(readAloud)
    val heading = readAloud.currentHeading
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    val status = if (readAloud.paused) "Paused" else "Read aloud"
    return listOfNotNull(status, progress, heading).joinToString(" - ")
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
