package com.xreader.app.ui

import com.xreader.app.tts.ReadAloudState
import kotlin.math.roundToInt

internal fun readerBookmarkActionLabel(bookmarked: Boolean): String =
    if (bookmarked) "Remove bookmark" else "Add bookmark"

internal fun readerAppearanceScopeLabel(bookAppearanceEnabled: Boolean): String =
    if (bookAppearanceEnabled) {
        "Appearance changes apply only to this book."
    } else {
        "Appearance changes apply to every book."
    }

internal fun readerCanGoPreviousPage(page: Int, pageCount: Int): Boolean =
    pageCount > 1 && page > 0

internal fun readerCanGoNextPage(page: Int, pageCount: Int): Boolean =
    pageCount > 1 && page < pageCount - 1

internal fun readerCanSeekPages(pageCount: Int): Boolean =
    pageCount > 1

internal fun readerPageStatusLabel(page: Int, pageCount: Int): String =
    if (pageCount <= 0) {
        "Page -"
    } else {
        val boundedPage = page.coerceIn(0, pageCount - 1)
        "${boundedPage + 1}/$pageCount"
    }

internal fun readerChromeProgressLabel(progress: Double, eta: String?): String {
    val percentText = "${(progress.coerceIn(0.0, 1.0) * 100).roundToInt()}% read"
    val trimmedEta = eta?.trim()
    return if (trimmedEta.isNullOrEmpty()) {
        percentText
    } else {
        "$percentText • $trimmedEta"
    }
}

internal fun readAloudToggleLabel(readAloud: ReadAloudState): String =
    when {
        readAloud.initializing -> "Preparing read aloud"
        readAloud.playing -> "Pause read aloud"
        readAloud.paused -> "Resume read aloud"
        else -> "Read aloud"
    }
