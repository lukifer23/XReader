package com.xreader.app.ui

import com.xreader.app.tts.ReadAloudState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadAloudControlsTest {
    @Test
    fun statusTextIncludesProgressAndCleanHeading() {
        val state = ReadAloudState(
            playing = true,
            currentChunk = 2,
            totalChunks = 8,
            currentHeading = " Chapter   Three "
        )

        assertEquals("Read aloud - 3/8 - Chapter Three", readAloudStatusText(state))
    }

    @Test
    fun progressTextHandlesMissingOrOutOfRangeProgress() {
        assertEquals(null, readAloudProgressText(ReadAloudState(totalChunks = 0)))
        assertEquals(
            "4/4",
            readAloudProgressText(
                ReadAloudState(
                    currentChunk = 12,
                    totalChunks = 4
                )
            )
        )
    }

    @Test
    fun skipControlsOnlyEnableWhilePlayingInsideBounds() {
        assertFalse(readAloudCanSkipPrevious(ReadAloudState(playing = false, currentChunk = 1, totalChunks = 3)))
        assertFalse(readAloudCanSkipPrevious(ReadAloudState(playing = true, currentChunk = 0, totalChunks = 3)))
        assertTrue(readAloudCanSkipPrevious(ReadAloudState(playing = true, currentChunk = 1, totalChunks = 3)))

        assertFalse(readAloudCanSkipNext(ReadAloudState(playing = false, currentChunk = 1, totalChunks = 3)))
        assertFalse(readAloudCanSkipNext(ReadAloudState(playing = true, currentChunk = 2, totalChunks = 3)))
        assertTrue(readAloudCanSkipNext(ReadAloudState(playing = true, currentChunk = 1, totalChunks = 3)))
    }
}
