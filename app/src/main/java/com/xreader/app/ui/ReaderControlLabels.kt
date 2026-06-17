package com.xreader.app.ui

import com.xreader.app.tts.ReadAloudState

internal fun readerBookmarkActionLabel(bookmarked: Boolean): String =
    if (bookmarked) "Remove bookmark" else "Add bookmark"

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

internal fun readAloudToggleLabel(readAloud: ReadAloudState): String =
    when {
        readAloud.initializing -> "Preparing read aloud"
        readAloud.playing -> "Pause read aloud"
        readAloud.paused -> "Resume read aloud"
        else -> "Read aloud"
    }
