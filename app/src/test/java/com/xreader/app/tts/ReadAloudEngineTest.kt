package com.xreader.app.tts

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadAloudEngineTest {
    @Test
    fun audioFocusStopMessageOnlyStopsForHardInterruptions() {
        assertEquals(
            "Read aloud stopped because another app took audio focus.",
            readAloudAudioFocusStopMessage(AudioManager.AUDIOFOCUS_LOSS)
        )
        assertEquals(
            "Read aloud stopped because another app needed audio.",
            readAloudAudioFocusStopMessage(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        )
        assertNull(readAloudAudioFocusStopMessage(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK))
        assertNull(readAloudAudioFocusStopMessage(AudioManager.AUDIOFOCUS_GAIN))
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
}
