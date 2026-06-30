package com.xreader.app.tts

import com.xreader.app.settings.NeuralTtsPace
import com.xreader.app.settings.NeuralTtsTone
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
        assertEquals(1, kokoroMaxNumSentences(NeuralTtsRuntimeWorkload.PREVIEW))
        assertEquals(3, kokoroMaxNumSentences(NeuralTtsRuntimeWorkload.AUDIOBOOK_GENERATION))
    }

    @Test
    fun hardwareAcceleratedTtsProvidersUseOneHostThread() {
        assertEquals(1, neuralTtsHostThreadCount(provider = "qnn:/tmp/provider.config", availableProcessors = 8))
        assertEquals(1, neuralTtsHostThreadCount(provider = "nnapi", availableProcessors = 8))
        assertEquals(1, neuralTtsHostThreadCount(provider = "webgpu", availableProcessors = 8))
        assertEquals(
            1,
            neuralTtsHostThreadCount(
                provider = "qnn:/tmp/provider.config",
                workload = NeuralTtsRuntimeWorkload.AUDIOBOOK_GENERATION,
                availableProcessors = 8
            )
        )
    }

    @Test
    fun cpuBackedPreviewTtsProvidersReserveCoresForUiResponsiveness() {
        assertEquals(1, neuralTtsHostThreadCount(provider = "cpu", availableProcessors = 2))
        assertEquals(2, neuralTtsHostThreadCount(provider = "xnnpack", availableProcessors = 8))
        assertEquals(2, neuralTtsHostThreadCount(provider = "cpu", availableProcessors = 16))
    }

    @Test
    fun cpuBackedAudiobookGenerationUsesMoreBoundedHostThreads() {
        assertEquals(
            1,
            neuralTtsHostThreadCount(
                provider = "xnnpack",
                workload = NeuralTtsRuntimeWorkload.AUDIOBOOK_GENERATION,
                availableProcessors = 2
            )
        )
        assertEquals(
            3,
            neuralTtsHostThreadCount(
                provider = "xnnpack",
                workload = NeuralTtsRuntimeWorkload.AUDIOBOOK_GENERATION,
                availableProcessors = 4
            )
        )
        assertEquals(
            4,
            neuralTtsHostThreadCount(
                provider = "cpu",
                workload = NeuralTtsRuntimeWorkload.AUDIOBOOK_GENERATION,
                availableProcessors = 8
            )
        )
        assertEquals(
            4,
            neuralTtsHostThreadCount(
                provider = "cpu",
                workload = NeuralTtsRuntimeWorkload.AUDIOBOOK_GENERATION,
                availableProcessors = 16
            )
        )
    }

    @Test
    fun neuralTtsGenerationUsesSharedDedicatedDispatcher() {
        assertSame(neuralTtsGenerationDispatcher(), neuralTtsGenerationDispatcher())
        assertSame(neuralTtsPreviewDispatcher(), neuralTtsPreviewDispatcher())
        assertSame(neuralTtsAudioSaveDispatcher(), neuralTtsAudioSaveDispatcher())
        assertTrue(neuralTtsGenerationDispatcher() !== neuralTtsPreviewDispatcher())
        assertTrue(neuralTtsGenerationDispatcher() !== neuralTtsAudioSaveDispatcher())
        assertTrue(neuralTtsPreviewDispatcher() !== neuralTtsAudioSaveDispatcher())
    }

    @Test
    fun audioTimeFactorUsesGeneratedAudioDurationAgainstComputeTime() {
        assertEquals(2.5f, generationAudioTimeFactor(audioMillis = 4_000L, computeMillis = 10_000L)!!, 0.0001f)
        assertNull(generationAudioTimeFactor(audioMillis = 0L, computeMillis = 10_000L))
        assertNull(generationAudioTimeFactor(audioMillis = 4_000L, computeMillis = 0L))
    }

    @Test
    fun audiobookGenerationMetricsKeepSaveTimeOutOfComputeSpeed() {
        val totals = AudiobookGenerationMetricTotals(
            audioMillis = 1_000L,
            computeMillis = 500L,
            saveMillis = 250L
        ).plusSegment(
            AudiobookGeneratedSegmentMetrics(
                audioMillis = 2_000L,
                computeMillis = 700L,
                saveMillis = 900L
            )
        )

        assertEquals(3_000L, totals.audioMillis)
        assertEquals(1_200L, totals.computeMillis)
        assertEquals(1_150L, totals.saveMillis)
        assertEquals(0.4f, generationAudioTimeFactor(totals.audioMillis, totals.computeMillis)!!, 0.0001f)
    }

    @Test
    fun audiobookGenerationMetricAccumulatorIgnoresNegativeDurations() {
        assertEquals(1_000L, 1_000L.plusNonNegativeDuration(-25L))
        assertEquals(1_025L, 1_000L.plusNonNegativeDuration(25L))
    }

    @Test
    fun fullBookHardwareGenerationRejectsNearRealtimeProviders() {
        assertTrue(isUsableAudiobookHardwareAudioTimeFactor(MAX_AUDIOBOOK_HARDWARE_AUDIO_TIME_FACTOR))
        assertTrue(isUsableAudiobookHardwareAudioTimeFactor(0.25f))
        assertFalse(isUsableAudiobookHardwareAudioTimeFactor(0.56f))
        assertFalse(isUsableAudiobookHardwareAudioTimeFactor(1.0f))
        assertFalse(isUsableAudiobookHardwareAudioTimeFactor(Float.NaN))
    }

    @Test
    fun fullBookHardwareGenerationSpeedGateWaitsForSustainedEvidence() {
        assertFalse(
            isSustainedUnusableAudiobookHardwareGenerationSpeed(
                audioMillis = MIN_HARDWARE_SPEED_GATE_AUDIO_MS,
                computeMillis = (MIN_HARDWARE_SPEED_GATE_AUDIO_MS * 0.9f).toLong(),
                generatedSegments = MIN_HARDWARE_SPEED_GATE_SEGMENTS - 1
            )
        )
        assertFalse(
            isSustainedUnusableAudiobookHardwareGenerationSpeed(
                audioMillis = MIN_HARDWARE_SPEED_GATE_AUDIO_MS - 1,
                computeMillis = MIN_HARDWARE_SPEED_GATE_AUDIO_MS,
                generatedSegments = MIN_HARDWARE_SPEED_GATE_SEGMENTS
            )
        )
        assertTrue(
            isSustainedUnusableAudiobookHardwareGenerationSpeed(
                audioMillis = MIN_HARDWARE_SPEED_GATE_AUDIO_MS,
                computeMillis = (MIN_HARDWARE_SPEED_GATE_AUDIO_MS * 0.9f).toLong(),
                generatedSegments = MIN_HARDWARE_SPEED_GATE_SEGMENTS
            )
        )
        assertFalse(
            isSustainedUnusableAudiobookHardwareGenerationSpeed(
                audioMillis = MIN_HARDWARE_SPEED_GATE_AUDIO_MS,
                computeMillis = (MIN_HARDWARE_SPEED_GATE_AUDIO_MS * 0.4f).toLong(),
                generatedSegments = MIN_HARDWARE_SPEED_GATE_SEGMENTS
            )
        )
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
        assertEquals("kokoro-v1-s0-standard-natural.wav.tmp", neuralPreviewTempAudioFileName("kokoro-v1-s0-standard-natural.wav"))

        val missing = File(temporaryFolder.root, "missing.wav")
        val headerOnly = temporaryFolder.newFile("header.wav").apply { writeBytes(ByteArray(WAV_HEADER_BYTES.toInt())) }
        val usable = temporaryFolder.newFile("usable.wav").apply { writeBytes(ByteArray(WAV_HEADER_BYTES.toInt() + 1)) }

        assertFalse(missing.hasUsableNeuralPreviewAudio())
        assertFalse(headerOnly.hasUsableNeuralPreviewAudio())
        assertTrue(usable.hasUsableNeuralPreviewAudio())
    }

    @Test
    fun usableWavTempReplaceRejectsHeaderOnlyPreviewWithoutClobberingExistingFile() {
        val dir = temporaryFolder.newFolder()
        val output = File(dir, "preview.wav").apply { writeBytes(ByteArray(WAV_HEADER_BYTES.toInt() + 10) { 7 }) }
        val originalBytes = output.readBytes()
        val headerOnlyTemp = File(dir, neuralPreviewTempAudioFileName(output.name)).apply {
            writeBytes(ByteArray(WAV_HEADER_BYTES.toInt()))
        }

        assertEquals(0L, output.replaceWithUsableWavTemp(headerOnlyTemp))
        assertFalse(headerOnlyTemp.exists())
        assertEquals(originalBytes.toList(), output.readBytes().toList())
    }

    @Test
    fun usableWavTempReplaceAtomicallyPublishesCompletePreview() {
        val dir = temporaryFolder.newFolder()
        val output = File(dir, "preview.wav").apply { writeBytes(ByteArray(WAV_HEADER_BYTES.toInt() + 2) { 1 }) }
        val completeTemp = File(dir, neuralPreviewTempAudioFileName(output.name)).apply {
            writeBytes(ByteArray(WAV_HEADER_BYTES.toInt() + 5) { 9 })
        }

        assertEquals(WAV_HEADER_BYTES + 5L, output.replaceWithUsableWavTemp(completeTemp))
        assertFalse(completeTemp.exists())
        assertEquals(WAV_HEADER_BYTES + 5L, output.length())
        assertTrue(output.readBytes().all { it == 9.toByte() })
    }

    @Test
    fun deleteStaleNeuralPreviewTempAudioRemovesOnlyPreviewTemps() {
        val dir = temporaryFolder.newFolder()
        val usablePreview = File(dir, "preview.wav").apply { writeBytes(ByteArray(WAV_HEADER_BYTES.toInt() + 5)) }
        val stalePreviewTemp = File(dir, neuralPreviewTempAudioFileName(usablePreview.name)).apply {
            writeBytes(ByteArray(WAV_HEADER_BYTES.toInt() + 1))
        }
        val unrelatedTemp = File(dir, "manifest.txt.tmp").apply { writeText("keep") }
        val wrongExtension = File(dir, "preview.tmp").apply { writeText("keep") }

        assertEquals(1, dir.deleteStaleNeuralPreviewTempAudio())
        assertTrue(usablePreview.isFile)
        assertFalse(stalePreviewTemp.exists())
        assertTrue(unrelatedTemp.isFile)
        assertTrue(wrongExtension.isFile)
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

    @Test
    fun catalogDefinesQnnPreparedKokoroModelTarget() {
        val spec = NeuralTtsModelCatalog.requireModel(NeuralTtsModelCatalog.DEFAULT_MODEL_ID)

        assertEquals("model.onnx", spec.modelFile)
        assertEquals("model.qnn.onnx", spec.hardwareModelFile)
    }

    @Test
    fun hardwareModelSelectionIsProviderSpecific() {
        val spec = NeuralTtsModelCatalog.requireModel(NeuralTtsModelCatalog.DEFAULT_MODEL_ID)
        val modelDir = temporaryFolder.newFolder("kokoro")
        File(modelDir, spec.modelFile).writeBytes(byteArrayOf(1))
        File(modelDir, spec.hardwareModelFile).writeBytes(byteArrayOf(2))
        File(modelDir, spec.hardwareModelManifestFile).writeText(
            """{"output_model":"${spec.hardwareModelFile}","strict_qnn_compatible":true}"""
        )

        assertEquals(
            spec.modelFile,
            neuralTtsModelFileForProvider(spec, modelDir, "qnn:/tmp/xreader-qnn-gpu-strict-provider.config")
        )
        assertEquals(
            spec.hardwareModelFile,
            neuralTtsModelFileForProvider(spec, modelDir, "qnn:/tmp/xreader-qnn-htp-strict-provider.config")
        )
        assertEquals(spec.modelFile, neuralTtsModelFileForProvider(spec, modelDir, "nnapi"))
        assertEquals(spec.modelFile, neuralTtsModelFileForProvider(spec, modelDir, "webgpu"))
        assertEquals(spec.modelFile, neuralTtsModelFileForProvider(spec, modelDir, "xnnpack"))
        assertEquals(spec.modelFile, neuralTtsModelFileForProvider(spec, modelDir, "cpu"))
    }

    @Test
    fun htpProviderRequiresPreparedArtifactBeforeAudiobookGenerationCanUseIt() {
        val spec = NeuralTtsModelCatalog.requireModel(NeuralTtsModelCatalog.DEFAULT_MODEL_ID)
        val modelDir = temporaryFolder.newFolder("kokoro-stock")
        File(modelDir, spec.modelFile).writeBytes(byteArrayOf(1))

        assertTrue(
            neuralTtsProviderHasRequiredModelArtifact(
                spec,
                modelDir,
                "qnn:/tmp/xreader-qnn-gpu-strict-provider.config"
            )
        )
        assertFalse(
            neuralTtsProviderHasRequiredModelArtifact(
                spec,
                modelDir,
                "qnn:/tmp/xreader-qnn-htp-strict-provider.config"
            )
        )
        File(modelDir, spec.hardwareModelFile).writeBytes(byteArrayOf(2))
        assertFalse(
            neuralTtsProviderHasRequiredModelArtifact(
                spec,
                modelDir,
                "qnn:/tmp/xreader-qnn-htp-strict-provider.config"
            )
        )
        File(modelDir, spec.hardwareModelManifestFile).writeText(
            """{"output_model":"${spec.hardwareModelFile}","strict_qnn_compatible":true}"""
        )
        assertTrue(
            neuralTtsProviderHasRequiredModelArtifact(
                spec,
                modelDir,
                "qnn:/tmp/xreader-qnn-htp-strict-provider.config"
            )
        )
        assertEquals(
            spec.hardwareModelFile,
            neuralTtsModelFileForProvider(spec, modelDir, "qnn:/tmp/xreader-qnn-htp-strict-provider.config")
        )
    }

    @Test
    fun missingHardwareArtifactReasonNamesPreparedQnnModel() {
        val spec = NeuralTtsModelCatalog.requireModel(NeuralTtsModelCatalog.DEFAULT_MODEL_ID)

        val reason = neuralTtsMissingHardwareArtifactReason(
            spec = spec,
            providers = listOf("qnn:/tmp/xreader-qnn-htp-strict-provider.config")
        )

        assertTrue(reason.contains("qnn-htp"))
        assertTrue(reason.contains("Kokoro v1.0"))
        assertTrue(reason.contains("model.qnn.onnx"))
    }
}
