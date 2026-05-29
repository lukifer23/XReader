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
}
