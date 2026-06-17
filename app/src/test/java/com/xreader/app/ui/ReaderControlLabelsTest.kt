package com.xreader.app.ui

import com.xreader.app.tts.ReadAloudState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderControlLabelsTest {
    @Test
    fun bookmarkActionLabelMatchesCurrentState() {
        assertEquals("Add bookmark", readerBookmarkActionLabel(bookmarked = false))
        assertEquals("Remove bookmark", readerBookmarkActionLabel(bookmarked = true))
    }

    @Test
    fun readAloudToggleLabelMatchesPlaybackState() {
        assertEquals("Read aloud", readAloudToggleLabel(ReadAloudState()))
        assertEquals("Preparing read aloud", readAloudToggleLabel(ReadAloudState(initializing = true)))
        assertEquals("Pause read aloud", readAloudToggleLabel(ReadAloudState(playing = true)))
        assertEquals("Resume read aloud", readAloudToggleLabel(ReadAloudState(paused = true)))
    }

    @Test
    fun readerPageActionsRespectBounds() {
        assertFalse(readerCanGoPreviousPage(page = 0, pageCount = 1))
        assertFalse(readerCanGoNextPage(page = 0, pageCount = 1))
        assertFalse(readerCanSeekPages(pageCount = 1))

        assertFalse(readerCanGoPreviousPage(page = 0, pageCount = 4))
        assertTrue(readerCanGoNextPage(page = 0, pageCount = 4))
        assertTrue(readerCanSeekPages(pageCount = 4))

        assertTrue(readerCanGoPreviousPage(page = 2, pageCount = 4))
        assertTrue(readerCanGoNextPage(page = 2, pageCount = 4))

        assertTrue(readerCanGoPreviousPage(page = 3, pageCount = 4))
        assertFalse(readerCanGoNextPage(page = 3, pageCount = 4))
    }

    @Test
    fun readerPageStatusLabelStaysValidForStartupAndMalformedCounts() {
        assertEquals("Page -", readerPageStatusLabel(page = 0, pageCount = 0))
        assertEquals("Page -", readerPageStatusLabel(page = 4, pageCount = -1))
        assertEquals("1/4", readerPageStatusLabel(page = -3, pageCount = 4))
        assertEquals("4/4", readerPageStatusLabel(page = 99, pageCount = 4))
        assertEquals("3/4", readerPageStatusLabel(page = 2, pageCount = 4))
    }
}
