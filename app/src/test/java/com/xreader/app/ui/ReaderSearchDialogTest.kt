package com.xreader.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSearchDialogTest {
    @Test
    fun statusTextGuidesBeforeSearch() {
        assertEquals(
            "Enter a word or phrase.",
            readerSearchStatusText(
                query = "",
                searchRunning = false,
                searchPerformed = false,
                resultCount = 0
            )
        )

        assertEquals(
            "Press Search or use the keyboard action.",
            readerSearchStatusText(
                query = "darrow",
                searchRunning = false,
                searchPerformed = false,
                resultCount = 0
            )
        )
    }

    @Test
    fun statusTextReportsSearchProgressAndResults() {
        assertEquals(
            "Searching...",
            readerSearchStatusText(
                query = "darrow",
                searchRunning = true,
                searchPerformed = false,
                resultCount = 0
            )
        )
        assertEquals(
            "No matches found.",
            readerSearchStatusText(
                query = "darrow",
                searchRunning = false,
                searchPerformed = true,
                resultCount = 0
            )
        )
        assertEquals(
            "1 match",
            readerSearchStatusText(
                query = "darrow",
                searchRunning = false,
                searchPerformed = true,
                resultCount = 1
            )
        )
        assertEquals(
            "3 matches",
            readerSearchStatusText(
                query = "darrow",
                searchRunning = false,
                searchPerformed = true,
                resultCount = 3
            )
        )
    }
}
