package com.xreader.app.tts

import com.xreader.app.settings.NeuralTtsPace
import com.xreader.app.settings.NeuralTtsTone
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NeuralTtsGenerationConfigTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun kokoroUsesRuntimeSupportedSentenceBatchForGenerationFlow() {
        assertEquals(1, kokoroMaxNumSentences(NeuralTtsRuntimeWorkload.PREVIEW))
        assertEquals(3, kokoroMaxNumSentences(NeuralTtsRuntimeWorkload.AUDIOBOOK_GENERATION))
    }

    @Test
    fun hardwareAcceleratedTtsProvidersUseOneHostThread() {
        assertEquals(1, neuralTtsHostThreadCount(provider = "qnn:/tmp/provider.config", availableProcessors = 8))
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
    fun cpuBackedTtsProvidersFailClosed() {
        val cpu = assertThrows(IllegalStateException::class.java) {
            neuralTtsHostThreadCount(provider = "cpu", availableProcessors = 8)
        }
        assertTrue(cpu.message.orEmpty().contains("Provider 'cpu' is disabled"))

        val xnnpack = assertThrows(IllegalStateException::class.java) {
            neuralTtsHostThreadCount(
                provider = "xnnpack",
                workload = NeuralTtsRuntimeWorkload.AUDIOBOOK_GENERATION,
                availableProcessors = 8
            )
        }
        assertTrue(xnnpack.message.orEmpty().contains("Provider 'xnnpack' is disabled"))

        val nnapi = assertThrows(IllegalStateException::class.java) {
            neuralTtsHostThreadCount(provider = "nnapi", availableProcessors = 8)
        }
        assertTrue(nnapi.message.orEmpty().contains("strict QNN hardware acceleration"))

        val webgpu = assertThrows(IllegalStateException::class.java) {
            neuralTtsHostThreadCount(provider = "webgpu", availableProcessors = 8)
        }
        assertTrue(webgpu.message.orEmpty().contains("strict QNN hardware acceleration"))
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
    fun hardwareModelSelectionUsesPreparedArtifactsForStrictQnnProviders() {
        val spec = NeuralTtsModelCatalog.requireModel(NeuralTtsModelCatalog.DEFAULT_MODEL_ID)
        val modelDir = temporaryFolder.newFolder("kokoro")
        File(modelDir, spec.modelFile).writeBytes(byteArrayOf(1))
        File(modelDir, spec.hardwareModelFile).writeBytes(byteArrayOf(2))
        writeValidQnnManifest(modelDir, spec)

        assertEquals(
            spec.hardwareModelFile,
            neuralTtsModelFileForProvider(spec, modelDir, "qnn:/tmp/xreader-qnn-htp-strict-provider.config")
        )
        assertEquals(
            spec.modelFile,
            neuralTtsModelFileForProvider(spec, modelDir, "qnn:/tmp/custom-provider.config")
        )
        assertEquals(spec.modelFile, neuralTtsModelFileForProvider(spec, modelDir, "nnapi"))
        assertEquals(spec.modelFile, neuralTtsModelFileForProvider(spec, modelDir, "webgpu"))
        assertEquals(spec.modelFile, neuralTtsModelFileForProvider(spec, modelDir, "xnnpack"))
        assertEquals(spec.modelFile, neuralTtsModelFileForProvider(spec, modelDir, "cpu"))
    }

    @Test
    fun qnnProvidersRequirePreparedArtifactBeforeAudiobookGenerationCanUseThem() {
        val spec = NeuralTtsModelCatalog.requireModel(NeuralTtsModelCatalog.DEFAULT_MODEL_ID)
        val modelDir = temporaryFolder.newFolder("kokoro-stock")
        File(modelDir, spec.modelFile).writeBytes(byteArrayOf(1))

        assertFalse(
            neuralTtsProviderHasRequiredModelArtifact(
                spec,
                modelDir,
                "qnn:/tmp/xreader-qnn-htp-strict-provider.config"
            )
        )
        assertFalse(neuralTtsProviderHasRequiredModelArtifact(spec, modelDir, "qnn-htp"))
        assertFalse(neuralTtsProviderHasRequiredModelArtifact(spec, modelDir, "nnapi"))
        assertFalse(neuralTtsProviderHasRequiredModelArtifact(spec, modelDir, "qnn:/tmp/custom-provider.config"))
        File(modelDir, spec.hardwareModelFile).writeBytes(byteArrayOf(2))
        assertFalse(
            neuralTtsProviderHasRequiredModelArtifact(
                spec,
                modelDir,
                "qnn:/tmp/xreader-qnn-htp-strict-provider.config"
            )
        )
        assertFalse(neuralTtsProviderHasRequiredModelArtifact(spec, modelDir, "qnn-htp"))
        assertFalse(neuralTtsProviderHasRequiredModelArtifact(spec, modelDir, "nnapi"))
        assertFalse(neuralTtsProviderHasRequiredModelArtifact(spec, modelDir, "qnn:/tmp/custom-provider.config"))
        writeValidQnnManifest(modelDir, spec)
        assertTrue(
            neuralTtsProviderHasRequiredModelArtifact(
                spec,
                modelDir,
                "qnn:/tmp/xreader-qnn-htp-strict-provider.config"
            )
        )
        assertTrue(neuralTtsProviderHasRequiredModelArtifact(spec, modelDir, "qnn-htp"))
        assertFalse(neuralTtsProviderHasRequiredModelArtifact(spec, modelDir, "nnapi"))
        assertFalse(neuralTtsProviderHasRequiredModelArtifact(spec, modelDir, "qnn:/tmp/custom-provider.config"))
        assertEquals(
            spec.hardwareModelFile,
            neuralTtsModelFileForProvider(spec, modelDir, "qnn:/tmp/xreader-qnn-htp-strict-provider.config")
        )
        assertEquals(spec.hardwareModelFile, neuralTtsModelFileForProvider(spec, modelDir, "qnn-htp"))
    }

    @Test
    fun strictHardwareSelectionRequiresPreparedArtifactForPreviewAndAudiobookGeneration() {
        val spec = NeuralTtsModelCatalog.requireModel(NeuralTtsModelCatalog.DEFAULT_MODEL_ID)
        val modelDir = temporaryFolder.newFolder("kokoro-selection")
        val providers = listOf(
            "qnn:/tmp/xreader-qnn-htp-strict-provider.config",
            "nnapi",
            "xnnpack",
            "cpu"
        )

        val previewMissing = neuralTtsSelectStrictHardwareProviders(
            providers = providers,
            spec = spec,
            modelDir = modelDir,
            workload = NeuralTtsRuntimeWorkload.PREVIEW
        )
        assertTrue(previewMissing.usableProviders.isEmpty())
        assertEquals(
            listOf("qnn-htp"),
            previewMissing.missingArtifactProviders.map(TtsAccelerationRuntime::providerDisplayKey)
        )

        val audiobookMissing = neuralTtsSelectStrictHardwareProviders(
            providers = providers,
            spec = spec,
            modelDir = modelDir,
            workload = NeuralTtsRuntimeWorkload.AUDIOBOOK_GENERATION
        )
        assertTrue(audiobookMissing.usableProviders.isEmpty())
        assertEquals(
            listOf("qnn-htp"),
            audiobookMissing.missingArtifactProviders.map(TtsAccelerationRuntime::providerDisplayKey)
        )

        File(modelDir, spec.hardwareModelFile).writeBytes(byteArrayOf(2))
        writeValidQnnManifest(modelDir, spec)

        val previewReady = neuralTtsSelectStrictHardwareProviders(
            providers = providers,
            spec = spec,
            modelDir = modelDir,
            workload = NeuralTtsRuntimeWorkload.PREVIEW
        )
        assertEquals(
            listOf("qnn-htp"),
            previewReady.usableProviders.map(TtsAccelerationRuntime::providerDisplayKey)
        )

        val audiobookReady = neuralTtsSelectStrictHardwareProviders(
            providers = providers,
            spec = spec,
            modelDir = modelDir,
            workload = NeuralTtsRuntimeWorkload.AUDIOBOOK_GENERATION
        )
        assertEquals(
            listOf("qnn-htp"),
            audiobookReady.usableProviders.map(TtsAccelerationRuntime::providerDisplayKey)
        )
    }

    @Test
    fun noUsableHardwareProviderReasonIncludesWorkloadAndMissingArtifacts() {
        val selection = NeuralTtsStrictHardwareProviderSelection(
            candidateProviders = listOf("qnn-htp"),
            missingArtifactProviders = listOf("qnn-htp")
        )

        val previewReason = neuralTtsNoUsableHardwareProviderReason(
            selection = selection,
            workload = NeuralTtsRuntimeWorkload.PREVIEW
        )
        assertTrue(previewReason.contains("preview"))
        assertTrue(previewReason.contains("Missing prepared model artifacts for qnn-htp"))

        val audiobookReason = neuralTtsNoUsableHardwareProviderReason(
            selection = selection,
            workload = NeuralTtsRuntimeWorkload.AUDIOBOOK_GENERATION
        )
        assertTrue(audiobookReason.contains("Full-book"))
        assertTrue(audiobookReason.contains("Missing prepared model artifacts for qnn-htp"))
    }

    @Test
    fun missingHardwareArtifactReasonNamesPreparedQnnModel() {
        val spec = NeuralTtsModelCatalog.requireModel(NeuralTtsModelCatalog.DEFAULT_MODEL_ID)

        val reason = neuralTtsMissingHardwareArtifactReason(
            spec = spec,
            providers = listOf(
                "qnn:/tmp/xreader-qnn-htp-strict-provider.config"
            )
        )

        assertTrue(reason.contains("qnn-htp"))
        assertTrue(reason.contains("Kokoro v1.0"))
        assertTrue(reason.contains("model.qnn.onnx"))
    }

    private fun writeValidQnnManifest(modelDir: File, spec: NeuralTtsModelSpec) {
        val model = File(modelDir, spec.hardwareModelFile)
        val sha256 = java.security.MessageDigest.getInstance("SHA-256")
            .digest(model.readBytes())
            .joinToString("") { "%02x".format(it) }
        File(modelDir, spec.hardwareModelManifestFile).writeText(
            """{
              "schema_version": 1,
              "artifact_type": "xreader-kokoro-qnn",
              "output_model": "${spec.hardwareModelFile}",
              "output_model_sha256": "$sha256",
              "output_model_bytes": ${model.length()},
              "strict_qnn_compatible": true,
              "source_model": {"name":"model.onnx","sha256":"${"a".repeat(64)}","revision":"test-release"},
              "toolchain": {"onnx":"1.test","onnxruntime":"1.test","qairt":"2.test"},
              "token_buckets": [256],
              "blocker_analysis": {"strict_qnn_compatible":true,"blocking_ops":{},"dynamic_inputs":[],"reason":"compatible"},
              "provenance": {"source_url":"https://example.invalid/model","license":"Apache-2.0"}
            }""".trimIndent()
        )
    }
}
