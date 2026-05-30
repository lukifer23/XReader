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
    fun statusTextIncludesActiveSleepTimerCompactly() {
        val state = ReadAloudState(
            playing = true,
            currentChunk = 2,
            totalChunks = 8,
            currentHeading = " Chapter   Three ",
            sleepTimerRemainingMillis = 14 * 60_000L + 1L
        )

        assertEquals("Read aloud - 3/8 - Sleep in 15m - Chapter Three", readAloudStatusText(state))
    }

    @Test
    fun statusTextShowsPausedStateWithoutLosingPosition() {
        val state = ReadAloudState(
            paused = true,
            currentChunk = 1,
            totalChunks = 4,
            currentHeading = "Chapter Two"
        )

        assertTrue(state.active)
        assertEquals("Paused - 2/4 - Chapter Two", readAloudStatusText(state))
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
    fun sleepTimerTextFallsBackToEndTimeAndFormatsHours() {
        assertEquals(null, readAloudSleepTimerText(ReadAloudState(), nowMillis = 1_000L))
        assertEquals(
            "Sleep in 1m",
            readAloudSleepTimerText(
                ReadAloudState(sleepTimerEndsAtMillis = 61_000L),
                nowMillis = 1_000L
            )
        )
        assertEquals(
            "Sleep in 1h 15m",
            readAloudSleepTimerText(
                ReadAloudState(sleepTimerRemainingMillis = 75 * 60_000L),
                nowMillis = 1_000L
            )
        )
        assertEquals(
            "Sleep in <1m",
            readAloudSleepTimerText(
                ReadAloudState(sleepTimerRemainingMillis = 0L),
                nowMillis = 1_000L
            )
        )
    }

    @Test
    fun skipControlsOnlyEnableWhileActiveInsideBounds() {
        assertFalse(readAloudCanSkipPrevious(ReadAloudState(playing = false, currentChunk = 1, totalChunks = 3)))
        assertFalse(readAloudCanSkipPrevious(ReadAloudState(playing = true, currentChunk = 0, totalChunks = 3)))
        assertTrue(readAloudCanSkipPrevious(ReadAloudState(playing = true, currentChunk = 1, totalChunks = 3)))
        assertTrue(readAloudCanSkipPrevious(ReadAloudState(paused = true, currentChunk = 1, totalChunks = 3)))

        assertFalse(readAloudCanSkipNext(ReadAloudState(playing = false, currentChunk = 1, totalChunks = 3)))
        assertFalse(readAloudCanSkipNext(ReadAloudState(playing = true, currentChunk = 2, totalChunks = 3)))
        assertTrue(readAloudCanSkipNext(ReadAloudState(playing = true, currentChunk = 1, totalChunks = 3)))
        assertTrue(readAloudCanSkipNext(ReadAloudState(paused = true, currentChunk = 1, totalChunks = 3)))
    }
}
