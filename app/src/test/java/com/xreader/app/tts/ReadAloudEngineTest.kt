package com.xreader.app.tts

import android.media.AudioManager
import android.media.session.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadAloudEngineTest {
    @Test
    fun audioFocusStopMessageOnlyStopsForPermanentLoss() {
        assertEquals(
            "Read aloud stopped because another app took audio focus.",
            readAloudAudioFocusStopMessage(AudioManager.AUDIOFOCUS_LOSS)
        )
        assertNull(readAloudAudioFocusStopMessage(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT))
        assertNull(readAloudAudioFocusStopMessage(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK))
        assertNull(readAloudAudioFocusStopMessage(AudioManager.AUDIOFOCUS_GAIN))
    }

    @Test
    fun audioFocusPauseMessageOnlyPausesForTransientLoss() {
        assertEquals(
            "Read aloud paused because another app needed audio.",
            readAloudAudioFocusPauseMessage(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        )
        assertNull(readAloudAudioFocusPauseMessage(AudioManager.AUDIOFOCUS_LOSS))
        assertNull(readAloudAudioFocusPauseMessage(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK))
        assertNull(readAloudAudioFocusPauseMessage(AudioManager.AUDIOFOCUS_GAIN))
    }

    @Test
    fun skipTargetStaysInsideAvailableChunks() {
        assertEquals(0, readAloudSkipTargetIndex(currentChunk = 1, totalChunks = 3, delta = -1))
        assertEquals(2, readAloudSkipTargetIndex(currentChunk = 1, totalChunks = 3, delta = 1))
        assertNull(readAloudSkipTargetIndex(currentChunk = 0, totalChunks = 3, delta = -1))
        assertNull(readAloudSkipTargetIndex(currentChunk = 2, totalChunks = 3, delta = 1))
        assertNull(readAloudSkipTargetIndex(currentChunk = 0, totalChunks = 0, delta = 1))
        assertNull(readAloudSkipTargetIndex(currentChunk = 0, totalChunks = 3, delta = 0))
    }

    @Test
    fun noReadableTextMessageDoesNotSuggestRepairForImageOnlyBooks() {
        assertEquals(
            "Read aloud is unavailable because this book has no extractable text.",
            readAloudNoReadableTextMessage(0)
        )
        assertEquals(
            "No readable text is indexed for this book. Repair the book from its details screen and try again.",
            readAloudNoReadableTextMessage(1200)
        )
    }

    @Test
    fun mediaActionsReflectReadAloudTransportState() {
        val playing = readAloudMediaActions(
            playing = true,
            paused = false,
            canSkipPrevious = true,
            canSkipNext = true
        )
        assertHasAction(playing, PlaybackState.ACTION_PLAY_PAUSE)
        assertHasAction(playing, PlaybackState.ACTION_PAUSE)
        assertHasAction(playing, PlaybackState.ACTION_STOP)
        assertHasAction(playing, PlaybackState.ACTION_SKIP_TO_PREVIOUS)
        assertHasAction(playing, PlaybackState.ACTION_SKIP_TO_NEXT)

        val paused = readAloudMediaActions(
            playing = false,
            paused = true,
            canSkipPrevious = false,
            canSkipNext = true
        )
        assertHasAction(paused, PlaybackState.ACTION_PLAY_PAUSE)
        assertHasAction(paused, PlaybackState.ACTION_PLAY)
        assertHasAction(paused, PlaybackState.ACTION_STOP)
        assertNoAction(paused, PlaybackState.ACTION_SKIP_TO_PREVIOUS)
        assertHasAction(paused, PlaybackState.ACTION_SKIP_TO_NEXT)

        assertEquals(
            0L,
            readAloudMediaActions(
                playing = false,
                paused = false,
                canSkipPrevious = true,
                canSkipNext = true
            )
        )
    }

    @Test
    fun foregroundNotificationTextReflectsReadAloudState() {
        val preparing = ReadAloudState(initializing = true, totalChunks = 12)
        assertEquals("Preparing read aloud", readAloudNotificationStatusText(preparing))
        assertEquals("1/12", readAloudNotificationProgressText(preparing))

        val playing = ReadAloudState(
            playing = true,
            currentChunk = 2,
            totalChunks = 12,
            currentHeading = "  Chapter   Seven  "
        )
        assertEquals("Chapter Seven", readAloudNotificationStatusText(playing))
        assertEquals("3/12", readAloudNotificationProgressText(playing))

        val paused = playing.copy(playing = false, paused = true)
        assertEquals("Paused", readAloudNotificationStatusText(paused))
    }

    @Test
    fun foregroundNotificationSkipActionsMatchPlaybackBounds() {
        val first = ReadAloudState(playing = true, currentChunk = 0, totalChunks = 3)
        assertEquals(false, readAloudNotificationCanSkipPrevious(first))
        assertEquals(true, readAloudNotificationCanSkipNext(first))

        val middlePaused = ReadAloudState(paused = true, currentChunk = 1, totalChunks = 3)
        assertEquals(true, readAloudNotificationCanSkipPrevious(middlePaused))
        assertEquals(true, readAloudNotificationCanSkipNext(middlePaused))

        val inactive = ReadAloudState(currentChunk = 1, totalChunks = 3)
        assertEquals(false, readAloudNotificationCanSkipPrevious(inactive))
        assertEquals(false, readAloudNotificationCanSkipNext(inactive))
    }

    @Test
    fun foregroundNotificationKeyIgnoresNonVisibleStateChangesOnly() {
        val state = ReadAloudState(
            activeBookId = 7,
            bookTitle = "Takedown",
            playing = true,
            currentChunk = 2,
            totalChunks = 12,
            currentHeading = "  Chapter   Seven  ",
            sleepTimerRemainingMillis = 60_000,
            message = "Not shown in notification"
        )

        assertEquals(
            state.readAloudNotificationKey(),
            state.copy(
                currentHeading = "Chapter Seven",
                sleepTimerRemainingMillis = 58_000,
                message = "Different unseen message"
            ).readAloudNotificationKey()
        )
        assertTrue(
            state.readAloudNotificationKey() !=
                state.copy(currentChunk = 3).readAloudNotificationKey()
        )
        assertTrue(
            state.readAloudNotificationKey() !=
                state.copy(playing = false, paused = true).readAloudNotificationKey()
        )
        assertTrue(
            state.readAloudNotificationKey() !=
                state.copy(bookTitle = "Another Book").readAloudNotificationKey()
        )
    }

    @Test
    fun mediaSessionMetadataKeyIgnoresHeadingWhitespaceOnlyChanges() {
        assertEquals(
            readAloudMetadataKey(bookTitle = "Takedown", heading = "  Chapter   Seven  "),
            readAloudMetadataKey(bookTitle = "Takedown", heading = "Chapter Seven")
        )
        assertEquals(
            readAloudMetadataKey(bookTitle = "Takedown", heading = null),
            readAloudMetadataKey(bookTitle = "Takedown", heading = "   ")
        )
        assertTrue(
            readAloudMetadataKey(bookTitle = "Takedown", heading = "Chapter Seven") !=
                readAloudMetadataKey(bookTitle = "Takedown", heading = "Chapter Eight")
        )
    }

    private fun assertHasAction(actions: Long, action: Long) {
        assertTrue(actions and action != 0L)
    }

    private fun assertNoAction(actions: Long, action: Long) {
        assertTrue(actions and action == 0L)
    }
}
