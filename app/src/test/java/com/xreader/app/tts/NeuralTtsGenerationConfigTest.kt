package com.xreader.app.tts

import com.xreader.app.settings.NeuralTtsPace
import com.xreader.app.settings.NeuralTtsTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NeuralTtsGenerationConfigTest {
    @Test
    fun generationConfigCarriesRealKokoroControls() {
        val config = neuralTtsGenerationConfig(
            speakerId = 3,
            pace = NeuralTtsPace.BRISK,
            tone = NeuralTtsTone.CALM
        )

        assertEquals(3, config.sid)
        assertEquals(NeuralTtsPace.BRISK.speed, config.speed, 0.0001f)
        assertEquals(NeuralTtsTone.CALM.silenceScale, config.silenceScale, 0.0001f)
        assertNull(config.extra)
    }

    @Test
    fun generationConfigBoundsPaceForRuntimeSafety() {
        val config = neuralTtsGenerationConfig(
            speakerId = 0,
            pace = NeuralTtsPace.RELAXED,
            tone = NeuralTtsTone.BRIGHT
        )

        assertEquals(0.92f, config.speed, 0.0001f)
        assertEquals(0.18f, config.silenceScale, 0.0001f)
    }
}
