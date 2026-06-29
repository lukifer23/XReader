package com.xreader.app.tts

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsAccelerationRuntimeTest {
    @Test
    fun stableProviderOrderExcludesExperimentalWebGpu() {
        TtsAccelerationRuntime.clearProviderFailuresForTests()
        val providers = TtsAccelerationRuntime.providerOrder(
            installedLibraries = setOf("libonnxruntime.so", "libsherpa-onnx-jni.so"),
            hasVulkan = true,
            includeExperimentalWebGpu = false,
            androidApiLevel = 36,
            hardware = "qcom",
            boardPlatform = "sun",
            socManufacturer = "QTI",
            socModel = "SM8750",
            qnnProviders = qnnProviderConfigs(),
        )

        assertFalse("webgpu" in providers)
        assertTrue(providers.first() == "nnapi")
    }

    @Test
    fun experimentalProviderOrderCanIncludeWebGpu() {
        TtsAccelerationRuntime.clearProviderFailuresForTests()
        val providers = TtsAccelerationRuntime.providerOrder(
            installedLibraries = setOf("libonnxruntime.so", "libsherpa-onnx-jni.so"),
            hasVulkan = true,
            includeExperimentalWebGpu = true,
            androidApiLevel = 36,
            hardware = "qcom",
            boardPlatform = "sun",
            socManufacturer = "QTI",
            socModel = "SM8750",
            qnnProviders = qnnProviderConfigs(),
        )

        assertTrue(providers.indexOf("nnapi") < providers.indexOf("webgpu"))
    }

    @Test
    fun stagedQnnProviderOrderPrefersVendorQnnBeforeNnapi() {
        TtsAccelerationRuntime.clearProviderFailuresForTests()
        val providers = TtsAccelerationRuntime.providerOrder(
            installedLibraries = qnnLibraries(),
            hasVulkan = true,
            includeExperimentalWebGpu = true,
            androidApiLevel = 36,
            hardware = "qcom",
            boardPlatform = "sun",
            socManufacturer = "QTI",
            socModel = "SM8750",
            qnnProviders = qnnProviderConfigs(),
        )

        assertEquals("qnn-gpu", TtsAccelerationRuntime.providerDisplayKey(providers[0]))
        assertEquals("qnn-htp", TtsAccelerationRuntime.providerDisplayKey(providers[1]))
        assertEquals("nnapi", providers[2])
        assertTrue(providers.take(5).map { TtsAccelerationRuntime.providerKey(it) } == listOf("qnn", "qnn", "nnapi", "webgpu", "xnnpack"))
    }

    @Test
    fun strictAudiobookProviderOrderExcludesCpuBackedFallbacks() {
        TtsAccelerationRuntime.clearProviderFailuresForTests()
        val providers = TtsAccelerationRuntime.providerOrder(
            installedLibraries = qnnLibraries(),
            hasVulkan = true,
            includeExperimentalWebGpu = false,
            includeCpuFallbacks = false,
            androidApiLevel = 36,
            hardware = "qcom",
            boardPlatform = "sun",
            socManufacturer = "QTI",
            socModel = "SM8750",
            qnnProviders = qnnProviderConfigs(),
        )

        assertEquals(listOf("qnn-gpu", "qnn-htp", "nnapi"), providers.map(TtsAccelerationRuntime::providerDisplayKey))
    }

    @Test
    fun qnnReadinessRejectsNonQualcommDevice() {
        val readiness = TtsAccelerationRuntime.qnnReadiness(
            installedLibraries = qnnLibraries(),
            hardware = "exynos",
            boardPlatform = "s5e",
            socManufacturer = "Samsung",
            socModel = "Exynos",
        )

        assertFalse(readiness.ready)
    }

    @Test
    fun qnnReadinessRejectsQualcommDeviceWithoutRuntimeLibraries() {
        val readiness = TtsAccelerationRuntime.qnnReadiness(
            installedLibraries = setOf("libonnxruntime.so", "libsherpa-onnx-jni.so"),
            hardware = "qcom",
            boardPlatform = "sun",
            socManufacturer = "QTI",
            socModel = "SM8750",
        )

        assertFalse(readiness.ready)
        assertTrue(readiness.reason.contains("Missing packaged QNN core libraries"))
        assertTrue(readiness.reason.contains("libQnnSystem.so"))
    }

    @Test
    fun qnnReadinessAcceptsQualcommDeviceWithPackagedHtpRuntime() {
        val readiness = TtsAccelerationRuntime.qnnReadiness(
            installedLibraries = qnnHtpLibraries(),
            hardware = "qcom",
            boardPlatform = "sun",
            socManufacturer = "QTI",
            socModel = "SM8750",
        )

        assertTrue(readiness.ready)
    }

    @Test
    fun qnnProviderModesUseStrictQnnHardwareForAudiobookAcceleration() {
        val htpOnly = TtsAccelerationRuntime.qnnProviderModes(qnnHtpLibraries())
        assertEquals(listOf(QnnBackend.HTP), htpOnly.map { it.backend })

        val gpuAndHtp = TtsAccelerationRuntime.qnnProviderModes(qnnLibraries())
        assertEquals(
            listOf(
                QnnProviderMode(QnnBackend.GPU, QnnExecutionMode.STRICT),
                QnnProviderMode(QnnBackend.HTP, QnnExecutionMode.STRICT),
            ),
            gpuAndHtp
        )

        val readiness = TtsAccelerationRuntime.qnnBackendReadiness(qnnHtpLibraries(), QnnBackend.GPU)
        assertFalse(readiness.ready)
        assertTrue(readiness.reason.contains("libOpenCL.so"))
        assertTrue(readiness.reason.contains("libOpenCL_adreno.so"))
    }

    @Test
    fun qnnHtpReadinessRequiresMatchingStubSkelAndDspBackend() {
        val incomplete = qnnHtpLibraries() - "libQnnHtpV79.so"
        val readiness = TtsAccelerationRuntime.qnnBackendReadiness(incomplete, QnnBackend.HTP)

        assertFalse(readiness.ready)
        assertTrue(readiness.reason.contains("Stub/Skel/DSP"))
        assertEquals(setOf("79"), TtsAccelerationRuntime.qnnHtpPackagedArchitectureVersions(qnnHtpLibraries()))
    }

    @Test
    fun qnnHtpArchitectureDetectionIgnoresMismatchedLibraryTriples() {
        val mixed = setOf(
            "libQnnHtpV79Stub.so",
            "libQnnHtpV79Skel.so",
            "libQnnHtpV75.so",
            "libQnnHtpV75Stub.so",
            "libQnnHtpV75Skel.so",
            "libQnnHtpV79.so",
            "libQnnHtpVabc.so"
        )

        assertEquals(setOf("75", "79"), TtsAccelerationRuntime.qnnHtpPackagedArchitectureVersions(mixed))
        assertEquals(emptySet<String>(), TtsAccelerationRuntime.qnnHtpPackagedArchitectureVersions(mixed - "libQnnHtpV75.so" - "libQnnHtpV79.so"))
    }

    @Test
    fun nnapiReadinessRequiresApi27AndPackagedRuntime() {
        assertFalse(
            TtsAccelerationRuntime.nnapiReadiness(
                installedLibraries = setOf("libonnxruntime.so", "libsherpa-onnx-jni.so"),
                androidApiLevel = 26
            ).ready
        )
        assertFalse(
            TtsAccelerationRuntime.nnapiReadiness(
                installedLibraries = emptySet(),
                androidApiLevel = 36
            ).ready
        )
        assertTrue(
            TtsAccelerationRuntime.nnapiReadiness(
                installedLibraries = setOf("libonnxruntime.so", "libsherpa-onnx-jni.so"),
                androidApiLevel = 36
            ).ready
        )
    }

    @Test
    fun qnnConfigTargetsKnownSm8750HtpRuntime() {
        assertEquals(69, TtsAccelerationRuntime.qnnSocModelId("SM8750"))
        assertEquals(79, TtsAccelerationRuntime.qnnHtpArch("SM8750"))
        assertEquals(8, TtsAccelerationRuntime.qnnHtpVtcmMb("SM8750"))
    }

    @Test
    fun qnnProviderConfigDefaultsToHtpNpuForPreparedKokoro() {
        TtsAccelerationRuntime.clearQnnHtpDeviceOptionOverridesForTests()
        val config = TtsAccelerationRuntime.qnnProviderConfigText(socModel = "SM8750")

        assertFalse(config.contains("DEBUG="))
        assertTrue(config.contains("backend_path=libQnnHtp.so"))
        assertTrue(config.contains("disable_cpu_ep_fallback=1"))
        assertTrue(config.contains("offload_graph_io_quantization=0"))
        assertTrue(config.contains("skip_qnn_version_check=0"))
        assertTrue(config.contains("ep.context_enable=1"))
        assertTrue(config.contains("ep.context_embed_mode=1"))
        assertTrue(config.contains("htp_performance_mode=burst"))
        assertFalse(config.contains("htp_use_signed_process_domain="))
        assertTrue(config.contains("vtcm_mb=8"))
        assertTrue(config.contains("rpc_control_latency=200"))
        assertTrue(config.contains("device_id=0"))
        assertFalse(config.contains("soc_model="))
        assertFalse(config.contains("htp_arch="))
    }

    @Test
    fun qnnHtpProviderConfigKeepsNpuTuningForQuantizedModels() {
        TtsAccelerationRuntime.clearQnnHtpDeviceOptionOverridesForTests()
        val config = TtsAccelerationRuntime.qnnProviderConfigText(
            socModel = "SM8750",
            mode = QnnProviderMode(QnnBackend.HTP, QnnExecutionMode.STRICT)
        )

        assertFalse(config.contains("DEBUG="))
        assertTrue(config.contains("backend_path=libQnnHtp.so"))
        assertTrue(config.contains("disable_cpu_ep_fallback=1"))
        assertTrue(config.contains("offload_graph_io_quantization=0"))
        assertTrue(config.contains("skip_qnn_version_check=0"))
        assertTrue(config.contains("htp_performance_mode=burst"))
        assertTrue(config.contains("htp_graph_finalization_optimization_mode=2"))
        assertTrue(config.contains("qnn_context_priority=high"))
        assertTrue(config.contains("enable_htp_fp16_precision=1"))
        assertFalse(config.contains("htp_use_signed_process_domain="))
        assertTrue(config.contains("vtcm_mb=8"))
        assertTrue(config.contains("rpc_control_latency=200"))
        assertTrue(config.contains("device_id=0"))
        assertFalse(config.contains("soc_model="))
        assertFalse(config.contains("htp_arch="))
    }

    @Test
    fun qnnHtpProviderConfigCanPinDeviceOptionsForDiagnostics() {
        try {
            TtsAccelerationRuntime.overrideQnnSignedProcessDomainForCurrentProcess(false)
            TtsAccelerationRuntime.overrideQnnHtpDeviceOptionsForCurrentProcess(
                includeSignedProcessDomainOption = true,
                socModel = 69,
                htpArch = 79
            )

            val config = TtsAccelerationRuntime.qnnProviderConfigText(
                socModel = "SM8750",
                mode = QnnProviderMode(QnnBackend.HTP, QnnExecutionMode.STRICT)
            )

            assertTrue(config.contains("htp_use_signed_process_domain=0"))
            assertTrue(config.contains("soc_model=69"))
            assertTrue(config.contains("htp_arch=79"))
        } finally {
            TtsAccelerationRuntime.clearQnnHtpDeviceOptionOverridesForTests()
        }
    }

    @Test
    fun qnnBackendPathFallsBackToLibraryNameWhenNativeLibraryIsNotExtracted() {
        assertEquals(
            "libQnnGpu.so",
            TtsAccelerationRuntime.qnnBackendPath(
                nativeLibraryDir = null,
                backend = QnnBackend.GPU
            )
        )
        assertEquals(
            "libQnnGpu.so",
            TtsAccelerationRuntime.qnnBackendPath(
                nativeLibraryDir = "/path/that/does/not/exist",
                backend = QnnBackend.GPU
            )
        )
    }

    @Test
    fun qnnBackendPathUsesExtractedNativeLibraryFileWhenAvailable() {
        val dirPath = createTempDirectory(prefix = "xreader-qnn-libs")
        val dir = dirPath.toFile()
        try {
            val gpu = File(dir, "libQnnGpu.so").apply { writeBytes(byteArrayOf(1)) }

            assertEquals(
                gpu.absolutePath,
                TtsAccelerationRuntime.qnnBackendPath(
                    nativeLibraryDir = dir.absolutePath,
                    backend = QnnBackend.GPU
                )
            )
            assertTrue(
                TtsAccelerationRuntime.qnnProviderConfigText(
                    socModel = "SM8750",
                    backendPath = gpu.absolutePath
                ).contains("backend_path=${gpu.absolutePath}")
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun qnnGpuOpenClPathUsesDirectoryContainingBothPackagedOpenClLibraries() {
        val dirPath = createTempDirectory(prefix = "xreader-opencl-libs")
        val dir = dirPath.toFile()
        try {
            assertEquals(null, TtsAccelerationRuntime.qnnGpuOpenClDriverPath(dir.absolutePath))

            File(dir, "libOpenCL.so").writeBytes(byteArrayOf(1))
            assertEquals(null, TtsAccelerationRuntime.qnnGpuOpenClDriverPath(dir.absolutePath))

            File(dir, "libOpenCL_adreno.so").writeBytes(byteArrayOf(1))
            assertEquals(dir.absolutePath, TtsAccelerationRuntime.qnnGpuOpenClDriverPath(dir.absolutePath))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun qnnGpuLibrarySearchPathPrependsExtractedNativeDirectory() {
        val path = TtsAccelerationRuntime.qnnGpuLibrarySearchPath(
            nativeLibraryDir = "/data/app/example/lib/arm64",
            existingPath = "/vendor/lib64:/custom/lib:/data/app/example/lib/arm64"
        )

        val parts = path.split(":")
        assertEquals("/data/app/example/lib/arm64", parts.first())
        assertTrue("/vendor/lib64" in parts)
        assertTrue("/custom/lib" in parts)
        assertEquals(parts.distinct(), parts)
    }

    @Test
    fun qnnGpuIcdVendorFilePointsToPackagedAdrenoDriver() {
        val text = TtsAccelerationRuntime.qnnGpuIcdVendorFileText("/data/app/example/lib/arm64")

        assertEquals("/data/app/example/lib/arm64/libOpenCL_adreno.so\n", text)
    }

    @Test
    fun qnnHtpAdspLibraryPathPrependsExtractedLibsAndAndroidRfsaPaths() {
        val path = TtsAccelerationRuntime.qnnHtpAdspLibraryPath(
            nativeLibraryDir = "/data/app/example/lib/arm64",
            existingPath = "/vendor/lib/rfsa/adsp/;/custom/dsp;/data/app/example/lib/arm64"
        )

        val parts = path.split(";")
        assertEquals("/data/app/example/lib/arm64", parts.first())
        assertTrue("/odm/lib/rfsa/adsp" in parts)
        assertTrue("/vendor/lib/rfsa/adsp/" in parts)
        assertTrue("/system/lib/rfsa/adsp" in parts)
        assertTrue("/system/vendor/lib/rfsa/adsp" in parts)
        assertTrue("/dsp" in parts)
        assertTrue("/custom/dsp" in parts)
        assertEquals(parts.distinct(), parts)
    }

    @Test
    fun qnnHtpAdspLibraryPathCanPrependDebugDiagnosticDspPath() {
        val path = TtsAccelerationRuntime.qnnHtpAdspLibraryPath(
            nativeLibraryDir = "/data/app/example/lib/arm64",
            includeDebugDiagnosticPath = true
        )

        val parts = path.split(";")
        assertEquals("/data/local/tmp/xreader-qnn-dsp", parts.first())
        assertEquals("/data/app/example/lib/arm64", parts[1])
    }

    @Test
    fun providerKeyRemovesConfigSuffix() {
        assertEquals("qnn", TtsAccelerationRuntime.providerKey("qnn:/data/user/0/com.xreader.app/cache/qnn.config"))
        assertEquals("webgpu", TtsAccelerationRuntime.providerKey("WebGPU"))
    }

    @Test
    fun hardwareProviderClassifiersRecognizeStrictAudiobookBackends() {
        assertTrue(TtsAccelerationRuntime.isHardwareAcceleratedProvider("qnn:/tmp/qnn.config"))
        assertTrue(TtsAccelerationRuntime.isHardwareAcceleratedProvider("nnapi"))
        assertTrue(TtsAccelerationRuntime.isHardwareAcceleratedProvider("webgpu"))
        assertFalse(TtsAccelerationRuntime.isHardwareAcceleratedProvider("xnnpack"))
        assertFalse(TtsAccelerationRuntime.isHardwareAcceleratedProvider("cpu"))

        assertTrue(TtsAccelerationRuntime.isStrictAudiobookHardwareProvider("qnn:/tmp/qnn.config"))
        assertFalse(TtsAccelerationRuntime.isStrictAudiobookHardwareProvider("nnapi"))
        assertFalse(TtsAccelerationRuntime.isStrictAudiobookHardwareProvider("webgpu"))
        assertFalse(TtsAccelerationRuntime.isStrictAudiobookHardwareProvider("cpu"))

        assertTrue(TtsAccelerationRuntime.isAudiobookGenerationAcceleratedProvider("qnn:/tmp/xreader-qnn-gpu-strict-provider.config"))
        assertFalse(TtsAccelerationRuntime.isAudiobookGenerationAcceleratedProvider("qnn:/tmp/xreader-qnn-gpu-hybrid-provider.config"))
        assertTrue(TtsAccelerationRuntime.isAudiobookGenerationAcceleratedProvider("qnn:/tmp/xreader-qnn-htp-strict-provider.config"))
        assertFalse(TtsAccelerationRuntime.isAudiobookGenerationAcceleratedProvider("nnapi"))
        assertFalse(TtsAccelerationRuntime.isAudiobookGenerationAcceleratedProvider("webgpu"))
        assertFalse(TtsAccelerationRuntime.isAudiobookGenerationAcceleratedProvider("xnnpack"))
    }

    @Test
    fun providerDisplayKeyIdentifiesQnnBackendConfig() {
        assertEquals(
            "qnn-gpu",
            TtsAccelerationRuntime.providerDisplayKey("qnn:/data/user/0/com.xreader.app/cache/xreader-qnn-gpu-strict-provider.config")
        )
        assertEquals(
            "qnn-htp",
            TtsAccelerationRuntime.providerDisplayKey("qnn:/data/user/0/com.xreader.app/cache/xreader-qnn-htp-strict-provider.config")
        )
        assertEquals(
            "qnn-gpu-hybrid",
            TtsAccelerationRuntime.providerDisplayKey("qnn:/data/user/0/com.xreader.app/cache/xreader-qnn-gpu-hybrid-provider.config")
        )
    }

    @Test
    fun qnnBackendIsDerivedFromProviderConfigName() {
        assertEquals(
            QnnBackend.GPU,
            TtsAccelerationRuntime.qnnBackend("qnn:/data/user/0/com.xreader.app/cache/xreader-qnn-gpu-strict-provider.config")
        )
        assertEquals(
            QnnBackend.HTP,
            TtsAccelerationRuntime.qnnBackend("qnn:/data/user/0/com.xreader.app/cache/xreader-qnn-htp-strict-provider.config")
        )
        assertEquals(null, TtsAccelerationRuntime.qnnBackend("nnapi"))
    }

    @Test
    fun qnnBackendIsDerivedFromReadinessProviderLabels() {
        assertEquals(QnnBackend.GPU, TtsAccelerationRuntime.qnnBackend("qnn-gpu"))
        assertEquals(QnnBackend.HTP, TtsAccelerationRuntime.qnnBackend("qnn-htp"))
        assertTrue(TtsAccelerationRuntime.isAudiobookGenerationAcceleratedProvider("qnn-gpu"))
        assertTrue(TtsAccelerationRuntime.isAudiobookGenerationAcceleratedProvider("qnn-htp"))
    }

    @Test
    fun failedAcceleratorIsSkippedAfterInitializationFailure() {
        TtsAccelerationRuntime.clearProviderFailuresForTests()
        TtsAccelerationRuntime.recordProviderInitializationFailed("webgpu")

        val providers = TtsAccelerationRuntime.providerOrder(
            installedLibraries = qnnLibraries(),
            hasVulkan = true,
            includeExperimentalWebGpu = true,
            androidApiLevel = 36,
            hardware = "qcom",
            boardPlatform = "sun",
            socManufacturer = "QTI",
            socModel = "SM8750",
            qnnProviders = qnnProviderConfigs(),
        )

        assertFalse(providers.any { TtsAccelerationRuntime.providerKey(it) == "webgpu" })
        assertEquals("qnn-gpu", TtsAccelerationRuntime.providerDisplayKey(providers.first()))
        TtsAccelerationRuntime.clearProviderFailuresForTests()
    }

    @Test
    fun failedGenericQnnDoesNotSuppressExplicitQnnBackends() {
        TtsAccelerationRuntime.clearProviderFailuresForTests()
        TtsAccelerationRuntime.recordProviderInitializationFailed("qnn:/data/user/0/com.xreader.app/cache/qnn.config")

        val providers = TtsAccelerationRuntime.providerOrder(
            installedLibraries = qnnLibraries(),
            hasVulkan = true,
            includeExperimentalWebGpu = false,
            androidApiLevel = 36,
            hardware = "qcom",
            boardPlatform = "sun",
            socManufacturer = "QTI",
            socModel = "SM8750",
            qnnProviders = qnnProviderConfigs(),
        )

        assertEquals("qnn-gpu", TtsAccelerationRuntime.providerDisplayKey(providers.first()))
        assertTrue(providers.any { TtsAccelerationRuntime.providerKey(it) == "nnapi" })
        TtsAccelerationRuntime.clearProviderFailuresForTests()
    }

    @Test
    fun failedQnnBackendDoesNotBlockOtherQnnBackends() {
        TtsAccelerationRuntime.clearProviderFailuresForTests()
        TtsAccelerationRuntime.recordProviderInitializationFailed("qnn:/data/user/0/com.xreader.app/cache/xreader-qnn-gpu-strict-provider.config")

        val providers = TtsAccelerationRuntime.providerOrder(
            installedLibraries = qnnLibraries(),
            hasVulkan = true,
            includeExperimentalWebGpu = false,
            androidApiLevel = 36,
            hardware = "qcom",
            boardPlatform = "sun",
            socManufacturer = "QTI",
            socModel = "SM8750",
            qnnProviders = listOf(
                "qnn:/data/user/0/com.xreader.app/cache/xreader-qnn-gpu-strict-provider.config",
                "qnn:/data/user/0/com.xreader.app/cache/xreader-qnn-htp-strict-provider.config",
                "qnn:/data/user/0/com.xreader.app/cache/xreader-qnn-gpu-hybrid-provider.config"
            ),
        )

        assertEquals("qnn-htp", TtsAccelerationRuntime.providerDisplayKey(providers.first()))
        assertTrue(providers.any { TtsAccelerationRuntime.providerKey(it) == "nnapi" })
        TtsAccelerationRuntime.clearProviderFailuresForTests()
    }

    @Test
    fun qnnHtpTransportFailuresAreClassifiedForUserFacingDiagnostics() {
        val provider = "qnn:/data/user/0/com.xreader.app/cache/xreader-qnn-htp-strict-provider.config"
        val error = IllegalStateException(
            "QNN SetupBackend failed Failed to create device. " +
                "Error: QNN_DEVICE_ERROR_INVALID_CONFIG: Invalid config values",
            RuntimeException("QnnDsp Failed to create transport for device, error: 4000")
        )

        assertTrue(TtsAccelerationRuntime.isQnnHtpTransportFailure(provider, error))
        assertEquals(
            "QNN HTP/NPU transport failed before audio generation. " +
                "The Qualcomm runtime could not create the DSP/HTP device for this app process.",
            TtsAccelerationRuntime.providerInitializationFailureSummary(provider, error)
        )
    }

    @Test
    fun qnnGpuOpenClFailuresAreClassifiedForUserFacingDiagnostics() {
        val provider = "qnn:/data/user/0/com.xreader.app/cache/xreader-qnn-gpu-strict-provider.config"
        val error = IllegalStateException(
            "QNN SetupBackend failed qnn_backend_manager.cc:581 InitializeBackend Failed to initialize backend.",
            RuntimeException(
                "GPU ERROR: GPU_ERROR_FAILED_CREATION(10006) - Invalid OpenCL driver path. " +
                    "QNN_COMMON_ERROR_PLATFORM_NOT_SUPPORTED"
            )
        )

        assertTrue(TtsAccelerationRuntime.isQnnGpuOpenClFailure(provider, error))
        assertEquals(
            "QNN GPU failed before audio generation. " +
                "The Qualcomm runtime could not load a usable OpenCL driver from the app process.",
            TtsAccelerationRuntime.providerInitializationFailureSummary(provider, error)
        )
    }

    @Test
    fun failedQnnHtpProviderIsBlockedWithReasonAfterTransportFailure() {
        TtsAccelerationRuntime.clearProviderFailuresForTests()
        val provider = "qnn:/data/user/0/com.xreader.app/cache/xreader-qnn-htp-strict-provider.config"
        TtsAccelerationRuntime.recordProviderInitializationFailed(
            provider,
            IllegalStateException("QNN_DEVICE_ERROR_INVALID_CONFIG")
        )

        val providers = TtsAccelerationRuntime.providerOrder(
            installedLibraries = qnnLibraries(),
            hasVulkan = true,
            includeExperimentalWebGpu = false,
            androidApiLevel = 36,
            hardware = "qcom",
            boardPlatform = "sun",
            socManufacturer = "QTI",
            socModel = "SM8750",
            qnnProviders = listOf(provider),
            includeCpuFallbacks = false,
        )

        assertFalse(providers.any { TtsAccelerationRuntime.providerDisplayKey(it) == "qnn-htp" })
        assertEquals(
            "QNN HTP/NPU transport failed before audio generation. " +
                "The Qualcomm runtime could not create the DSP/HTP device for this app process.",
            TtsAccelerationRuntime.audiobookHardwareProviderBlockReason()
        )
        TtsAccelerationRuntime.clearProviderFailuresForTests()
    }

    @Test
    fun failedQnnHardwareBackendsReportCombinedBlockReason() {
        TtsAccelerationRuntime.clearProviderFailuresForTests()
        TtsAccelerationRuntime.recordProviderInitializationFailed(
            "qnn:/data/user/0/com.xreader.app/cache/xreader-qnn-gpu-strict-provider.config",
            IllegalStateException("Invalid OpenCL driver path")
        )
        TtsAccelerationRuntime.recordProviderInitializationFailed(
            "qnn:/data/user/0/com.xreader.app/cache/xreader-qnn-htp-strict-provider.config",
            IllegalStateException("QNN_DEVICE_ERROR_INVALID_CONFIG")
        )

        val reason = TtsAccelerationRuntime.audiobookHardwareProviderBlockReason().orEmpty()
        assertTrue(reason.contains("QNN GPU failed"))
        assertTrue(reason.contains("QNN HTP/NPU transport failed"))
        TtsAccelerationRuntime.clearProviderFailuresForTests()
    }

    @Test
    fun orderedProviderFailureSummaryTrimsAndSuppressesDuplicates() {
        assertEquals(
            "GPU failed HTP failed",
            TtsAccelerationRuntime.orderedProviderFailureSummary(" GPU failed ", "HTP failed")
        )
        assertEquals(
            "QNN failed",
            TtsAccelerationRuntime.orderedProviderFailureSummary("QNN failed", " QNN failed ")
        )
        assertEquals(
            "HTP failed",
            TtsAccelerationRuntime.orderedProviderFailureSummary(null, " HTP failed ")
        )
        assertEquals(
            null,
            TtsAccelerationRuntime.orderedProviderFailureSummary(" ", null)
        )
    }

    @Test
    fun packagedNativeLibraryDiscoveryIsCachedPerInstallKey() {
        TtsAccelerationRuntime.clearPackagedNativeLibrariesCacheForTests()
        var discoveries = 0
        val key = PackagedNativeLibrariesKey(
            nativeLibraryDir = "/data/app/lib",
            sourcePaths = listOf("/data/app/base.apk")
        )

        val first = TtsAccelerationRuntime.cachedPackagedNativeLibraries(key) {
            discoveries += 1
            setOf("libonnxruntime.so")
        }
        val second = TtsAccelerationRuntime.cachedPackagedNativeLibraries(key) {
            discoveries += 1
            setOf("should-not-run.so")
        }

        assertEquals(setOf("libonnxruntime.so"), first)
        assertEquals(first, second)
        assertEquals(1, discoveries)
        TtsAccelerationRuntime.clearPackagedNativeLibrariesCacheForTests()
    }

    @Test
    fun packagedNativeLibraryCacheInvalidatesWhenInstallKeyChanges() {
        TtsAccelerationRuntime.clearPackagedNativeLibrariesCacheForTests()
        var discoveries = 0
        val firstKey = PackagedNativeLibrariesKey(
            nativeLibraryDir = "/data/app/lib",
            sourcePaths = listOf("/data/app/base.apk")
        )
        val nextKey = firstKey.copy(sourcePaths = listOf("/data/app/base.apk", "/data/app/split.apk"))

        TtsAccelerationRuntime.cachedPackagedNativeLibraries(firstKey) {
            discoveries += 1
            setOf("base.so")
        }
        val next = TtsAccelerationRuntime.cachedPackagedNativeLibraries(nextKey) {
            discoveries += 1
            setOf("base.so", "split.so")
        }

        assertEquals(setOf("base.so", "split.so"), next)
        assertEquals(2, discoveries)
        TtsAccelerationRuntime.clearPackagedNativeLibrariesCacheForTests()
    }

    private fun qnnHtpLibraries(): Set<String> = setOf(
        "libonnxruntime.so",
        "libsherpa-onnx-jni.so",
        "libQnnHtp.so",
        "libQnnHtpNetRunExtensions.so",
        "libQnnHtpPrepare.so",
        "libQnnSystem.so",
        "libQnnHtpV79Stub.so",
        "libQnnHtpV79Skel.so",
        "libQnnHtpV79.so",
    )

    private fun qnnLibraries(): Set<String> = qnnHtpLibraries() + setOf(
        "libQnnGpu.so",
        "libQnnGpuNetRunExtensions.so",
        "libOpenCL.so",
        "libOpenCL_adreno.so",
    )

    private fun qnnProviderConfigs(): List<String> = listOf(
        "qnn:/data/user/0/com.xreader.app/cache/xreader-qnn-gpu-strict-provider.config",
        "qnn:/data/user/0/com.xreader.app/cache/xreader-qnn-htp-strict-provider.config",
    )
}
