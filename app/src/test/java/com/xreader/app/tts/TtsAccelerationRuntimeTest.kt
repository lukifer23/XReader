package com.xreader.app.tts

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsAccelerationRuntimeTest {
    @Test
    fun providerOrderRequiresPackagedQnnHtpRuntime() {
        TtsAccelerationRuntime.clearProviderFailuresForTests()
        val providers = TtsAccelerationRuntime.providerOrder(
            installedLibraries = setOf("libonnxruntime.so", "libsherpa-onnx-jni.so"),
            androidApiLevel = 36,
            hardware = "qcom",
            boardPlatform = "sun",
            socManufacturer = "QTI",
            socModel = "SM8750",
            qnnProviders = qnnProviderConfigs(),
        )

        assertTrue(providers.isEmpty())
    }

    @Test
    fun stagedQnnProviderOrderSelectsSingleStrictHtpProvider() {
        TtsAccelerationRuntime.clearProviderFailuresForTests()
        val providers = TtsAccelerationRuntime.providerOrder(
            installedLibraries = qnnHtpLibraries(),
            androidApiLevel = 36,
            hardware = "qcom",
            boardPlatform = "sun",
            socManufacturer = "QTI",
            socModel = "SM8750",
            qnnProviders = qnnProviderConfigs(),
        )

        assertEquals(listOf("qnn-htp"), providers.map(TtsAccelerationRuntime::providerDisplayKey))
        assertEquals(listOf("qnn"), providers.map { TtsAccelerationRuntime.providerKey(it) })
    }

    @Test
    fun qnnReadinessRejectsNonQualcommDevice() {
        val readiness = TtsAccelerationRuntime.qnnReadiness(
            installedLibraries = qnnHtpLibraries(),
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
    fun qnnProviderModesUseOnlyStrictHtpForAudiobookAcceleration() {
        val modes = TtsAccelerationRuntime.qnnProviderModes(qnnHtpLibraries())

        assertEquals(
            listOf(QnnProviderMode(QnnBackend.HTP, QnnExecutionMode.STRICT)),
            modes
        )
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
        assertEquals(
            emptySet<String>(),
            TtsAccelerationRuntime.qnnHtpPackagedArchitectureVersions(mixed - "libQnnHtpV75.so" - "libQnnHtpV79.so")
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
    fun qnnHtpBackendPathFallsBackToLibraryNameWhenNativeLibraryIsNotExtracted() {
        assertEquals(
            "libQnnHtp.so",
            TtsAccelerationRuntime.qnnBackendPath(
                nativeLibraryDir = null,
                backend = QnnBackend.HTP
            )
        )
        assertEquals(
            "libQnnHtp.so",
            TtsAccelerationRuntime.qnnBackendPath(
                nativeLibraryDir = "/path/that/does/not/exist",
                backend = QnnBackend.HTP
            )
        )
    }

    @Test
    fun qnnHtpBackendPathUsesExtractedNativeLibraryFileWhenAvailable() {
        val dirPath = createTempDirectory(prefix = "xreader-qnn-libs")
        val dir = dirPath.toFile()
        try {
            val htp = File(dir, "libQnnHtp.so").apply { writeBytes(byteArrayOf(1)) }

            assertEquals(
                htp.absolutePath,
                TtsAccelerationRuntime.qnnBackendPath(
                    nativeLibraryDir = dir.absolutePath,
                    backend = QnnBackend.HTP
                )
            )
            assertTrue(
                TtsAccelerationRuntime.qnnProviderConfigText(
                    socModel = "SM8750",
                    backendPath = htp.absolutePath
                ).contains("backend_path=${htp.absolutePath}")
            )
        } finally {
            dir.deleteRecursively()
        }
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
    fun hardwareProviderClassifiersRecognizeOnlyStrictHtpBackend() {
        assertFalse(TtsAccelerationRuntime.isHardwareAcceleratedProvider("qnn:/tmp/qnn.config"))
        assertFalse(TtsAccelerationRuntime.isHardwareAcceleratedProvider("qnn:/tmp/xreader-qnn-gpu-strict-provider.config"))
        assertFalse(TtsAccelerationRuntime.isHardwareAcceleratedProvider("qnn-gpu"))
        assertFalse(TtsAccelerationRuntime.isHardwareAcceleratedProvider("nnapi"))
        assertFalse(TtsAccelerationRuntime.isHardwareAcceleratedProvider("webgpu"))
        assertFalse(TtsAccelerationRuntime.isHardwareAcceleratedProvider("xnnpack"))
        assertFalse(TtsAccelerationRuntime.isHardwareAcceleratedProvider("cpu"))

        assertTrue(
            TtsAccelerationRuntime.isStrictAudiobookHardwareProvider(
                "qnn:/tmp/xreader-qnn-htp-strict-provider.config"
            )
        )
        assertFalse(TtsAccelerationRuntime.isStrictAudiobookHardwareProvider("qnn:/tmp/qnn.config"))
        assertFalse(TtsAccelerationRuntime.isStrictAudiobookHardwareProvider("qnn:/tmp/xreader-qnn-gpu-strict-provider.config"))
        assertFalse(TtsAccelerationRuntime.isStrictAudiobookHardwareProvider("qnn-gpu"))
        assertFalse(TtsAccelerationRuntime.isStrictAudiobookHardwareProvider("nnapi"))
        assertFalse(TtsAccelerationRuntime.isStrictAudiobookHardwareProvider("webgpu"))
        assertFalse(TtsAccelerationRuntime.isStrictAudiobookHardwareProvider("cpu"))

        assertTrue(TtsAccelerationRuntime.isAudiobookGenerationAcceleratedProvider("qnn:/tmp/xreader-qnn-htp-strict-provider.config"))
        assertFalse(TtsAccelerationRuntime.isAudiobookGenerationAcceleratedProvider("qnn:/tmp/xreader-qnn-gpu-strict-provider.config"))
        assertFalse(TtsAccelerationRuntime.isAudiobookGenerationAcceleratedProvider("qnn-gpu"))
        assertFalse(TtsAccelerationRuntime.isAudiobookGenerationAcceleratedProvider("nnapi"))
        assertFalse(TtsAccelerationRuntime.isAudiobookGenerationAcceleratedProvider("webgpu"))
        assertFalse(TtsAccelerationRuntime.isAudiobookGenerationAcceleratedProvider("xnnpack"))
    }

    @Test
    fun providerDisplayKeyIdentifiesOnlyHtpBackendConfig() {
        assertEquals(
            "qnn",
            TtsAccelerationRuntime.providerDisplayKey("qnn:/data/user/0/com.xreader.app/cache/xreader-qnn-gpu-strict-provider.config")
        )
        assertEquals(
            "qnn-htp",
            TtsAccelerationRuntime.providerDisplayKey("qnn:/data/user/0/com.xreader.app/cache/xreader-qnn-htp-strict-provider.config")
        )
        assertEquals(
            "qnn",
            TtsAccelerationRuntime.providerDisplayKey("qnn:/data/user/0/com.xreader.app/cache/custom-qnn-provider.config")
        )
    }

    @Test
    fun qnnBackendIsDerivedFromHtpProviderConfigName() {
        assertEquals(
            null,
            TtsAccelerationRuntime.qnnBackend("qnn:/data/user/0/com.xreader.app/cache/xreader-qnn-gpu-strict-provider.config")
        )
        assertEquals(
            QnnBackend.HTP,
            TtsAccelerationRuntime.qnnBackend("qnn:/data/user/0/com.xreader.app/cache/xreader-qnn-htp-strict-provider.config")
        )
        assertEquals(null, TtsAccelerationRuntime.qnnBackend("qnn-gpu"))
        assertEquals(null, TtsAccelerationRuntime.qnnBackend("nnapi"))
    }

    @Test
    fun failedNonSelectedAcceleratorDoesNotEnableFallbacks() {
        TtsAccelerationRuntime.clearProviderFailuresForTests()
        TtsAccelerationRuntime.recordProviderInitializationFailed("webgpu")

        val providers = TtsAccelerationRuntime.providerOrder(
            installedLibraries = qnnHtpLibraries(),
            androidApiLevel = 36,
            hardware = "qcom",
            boardPlatform = "sun",
            socManufacturer = "QTI",
            socModel = "SM8750",
            qnnProviders = qnnProviderConfigs(),
        )

        assertFalse(providers.any { TtsAccelerationRuntime.providerKey(it) == "webgpu" })
        assertEquals(listOf("qnn-htp"), providers.map(TtsAccelerationRuntime::providerDisplayKey))
        TtsAccelerationRuntime.clearProviderFailuresForTests()
    }

    @Test
    fun failedGenericQnnDoesNotEnableCpuBackedFallbacks() {
        TtsAccelerationRuntime.clearProviderFailuresForTests()
        TtsAccelerationRuntime.recordProviderInitializationFailed("qnn:/data/user/0/com.xreader.app/cache/qnn.config")

        val providers = TtsAccelerationRuntime.providerOrder(
            installedLibraries = qnnHtpLibraries(),
            androidApiLevel = 36,
            hardware = "qcom",
            boardPlatform = "sun",
            socManufacturer = "QTI",
            socModel = "SM8750",
            qnnProviders = qnnProviderConfigs(),
        )

        assertEquals(listOf("qnn-htp"), providers.map(TtsAccelerationRuntime::providerDisplayKey))
        assertFalse(providers.any { TtsAccelerationRuntime.providerKey(it) == "nnapi" })
        TtsAccelerationRuntime.clearProviderFailuresForTests()
    }

    @Test
    fun failedSelectedQnnHtpBackendBlocksGeneration() {
        TtsAccelerationRuntime.clearProviderFailuresForTests()
        TtsAccelerationRuntime.recordProviderInitializationFailed(
            "qnn:/data/user/0/com.xreader.app/cache/xreader-qnn-htp-strict-provider.config"
        )

        val providers = TtsAccelerationRuntime.providerOrder(
            installedLibraries = qnnHtpLibraries(),
            androidApiLevel = 36,
            hardware = "qcom",
            boardPlatform = "sun",
            socManufacturer = "QTI",
            socModel = "SM8750",
            qnnProviders = qnnProviderConfigs(),
        )

        assertTrue(providers.isEmpty())
        assertFalse(providers.any { TtsAccelerationRuntime.providerKey(it) == "nnapi" })
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
    fun failedQnnHtpProviderIsBlockedWithReasonAfterTransportFailure() {
        TtsAccelerationRuntime.clearProviderFailuresForTests()
        val provider = "qnn:/data/user/0/com.xreader.app/cache/xreader-qnn-htp-strict-provider.config"
        TtsAccelerationRuntime.recordProviderInitializationFailed(
            provider,
            IllegalStateException("QNN_DEVICE_ERROR_INVALID_CONFIG")
        )

        val providers = TtsAccelerationRuntime.providerOrder(
            installedLibraries = qnnHtpLibraries(),
            androidApiLevel = 36,
            hardware = "qcom",
            boardPlatform = "sun",
            socManufacturer = "QTI",
            socModel = "SM8750",
            qnnProviders = listOf(provider),
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

        val next = TtsAccelerationRuntime.cachedPackagedNativeLibraries(
            key.copy(sourcePaths = listOf("/data/app/base.apk", "/data/app/split.apk"))
        ) {
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

    private fun qnnProviderConfigs(): List<String> = listOf(
        "qnn:/data/user/0/com.xreader.app/cache/xreader-qnn-htp-strict-provider.config",
    )
}
