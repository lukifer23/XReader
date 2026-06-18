package com.xreader.app.tts

import com.xreader.app.settings.NeuralTtsPace
import com.xreader.app.settings.NeuralTtsTone
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NeuralTtsGenerationConfigTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun webGpuRuntimeRotationAvoidsFrequentModelReloads() {
        assertEquals(128, ttsRuntimeRotationSegmentLimit("webgpu"))
        assertNull(ttsRuntimeRotationSegmentLimit("xnnpack"))
        assertNull(ttsRuntimeRotationSegmentLimit("cpu"))
    }

    @Test
    fun kokoroUsesRuntimeSupportedSentenceBatchForGenerationFlow() {
        assertEquals(1, KOKORO_MAX_NUM_SENTENCES)
    }

    @Test
    fun realtimeFactorUsesGeneratedAudioDurationAgainstComputeTime() {
        assertEquals(2.5f, generationRealtimeFactor(audioMillis = 4_000L, computeMillis = 10_000L)!!, 0.0001f)
        assertNull(generationRealtimeFactor(audioMillis = 0L, computeMillis = 10_000L))
        assertNull(generationRealtimeFactor(audioMillis = 4_000L, computeMillis = 0L))
    }

    @Test
    fun previewAudioCacheUsesStableVoiceSettingsFileNameAndRejectsHeaderOnlyFiles() {
        assertEquals(
            "kokoro-v1-s3-brisk-bright.wav",
            neuralPreviewAudioFileName(
                modelId = "kokoro-v1",
                speakerId = 3,
                pace = NeuralTtsPace.BRISK,
                tone = NeuralTtsTone.BRIGHT
            )
        )
        assertEquals(
            "kokoro-v1-s0-standard-natural.wav",
            neuralPreviewAudioFileName(
                modelId = "kokoro-v1",
                speakerId = -2,
                pace = NeuralTtsPace.STANDARD,
                tone = NeuralTtsTone.NATURAL
            )
        )

        val missing = File(temporaryFolder.root, "missing.wav")
        val headerOnly = temporaryFolder.newFile("header.wav").apply { writeBytes(ByteArray(WAV_HEADER_BYTES.toInt())) }
        val usable = temporaryFolder.newFile("usable.wav").apply { writeBytes(ByteArray(WAV_HEADER_BYTES.toInt() + 1)) }

        assertFalse(missing.hasUsableNeuralPreviewAudio())
        assertFalse(headerOnly.hasUsableNeuralPreviewAudio())
        assertTrue(usable.hasUsableNeuralPreviewAudio())
    }

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
