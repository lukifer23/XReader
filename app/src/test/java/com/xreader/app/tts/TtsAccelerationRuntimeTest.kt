package com.xreader.app.tts

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
            hardware = "qcom",
            boardPlatform = "sun",
            socManufacturer = "QTI",
            socModel = "SM8750",
        )

        assertFalse("webgpu" in providers)
        assertTrue(providers.first() == "xnnpack")
    }

    @Test
    fun experimentalProviderOrderCanIncludeWebGpu() {
        TtsAccelerationRuntime.clearProviderFailuresForTests()
        val providers = TtsAccelerationRuntime.providerOrder(
            installedLibraries = setOf("libonnxruntime.so", "libsherpa-onnx-jni.so"),
            hasVulkan = true,
            includeExperimentalWebGpu = true,
            hardware = "qcom",
            boardPlatform = "sun",
            socManufacturer = "QTI",
            socModel = "SM8750",
        )

        assertTrue(providers.first() == "webgpu")
    }

    @Test
    fun stagedQnnProviderOrderIncludesQnnFirst() {
        TtsAccelerationRuntime.clearProviderFailuresForTests()
        val providers = TtsAccelerationRuntime.providerOrder(
            installedLibraries = qnnLibraries(),
            hasVulkan = true,
            includeExperimentalWebGpu = true,
            hardware = "qcom",
            boardPlatform = "sun",
            socManufacturer = "QTI",
            socModel = "SM8750",
        )

        assertTrue(providers.first() == "qnn")
        assertTrue(providers.take(3) == listOf("qnn", "webgpu", "xnnpack"))
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
        assertTrue(readiness.reason.contains("Missing packaged QNN libraries"))
    }

    @Test
    fun qnnReadinessAcceptsQualcommDeviceWithPackagedHtpRuntime() {
        val readiness = TtsAccelerationRuntime.qnnReadiness(
            installedLibraries = qnnLibraries(),
            hardware = "qcom",
            boardPlatform = "sun",
            socManufacturer = "QTI",
            socModel = "SM8750",
        )

        assertTrue(readiness.ready)
    }

    @Test
    fun qnnConfigTargetsKnownSm8750HtpRuntime() {
        assertEquals(69, TtsAccelerationRuntime.qnnSocModelId("SM8750"))
        assertEquals(81, TtsAccelerationRuntime.qnnHtpArch("SM8750"))
    }

    @Test
    fun providerKeyRemovesConfigSuffix() {
        assertEquals("qnn", TtsAccelerationRuntime.providerKey("qnn:/data/user/0/com.xreader.app/cache/qnn.config"))
        assertEquals("webgpu", TtsAccelerationRuntime.providerKey("WebGPU"))
    }

    @Test
    fun failedAcceleratorIsSkippedAfterInitializationFailure() {
        TtsAccelerationRuntime.clearProviderFailuresForTests()
        TtsAccelerationRuntime.recordProviderInitializationFailed("qnn:/data/user/0/com.xreader.app/cache/qnn.config")

        val providers = TtsAccelerationRuntime.providerOrder(
            installedLibraries = qnnLibraries(),
            hasVulkan = true,
            includeExperimentalWebGpu = true,
            hardware = "qcom",
            boardPlatform = "sun",
            socManufacturer = "QTI",
            socModel = "SM8750",
        )

        assertFalse(providers.any { TtsAccelerationRuntime.providerKey(it) == "qnn" })
        assertTrue(providers.first() == "webgpu")
        TtsAccelerationRuntime.clearProviderFailuresForTests()
    }

    private fun qnnLibraries(): Set<String> = setOf(
        "libonnxruntime.so",
        "libsherpa-onnx-jni.so",
        "libQnnGpu.so",
        "libQnnHtp.so",
        "libQnnHtpPrepare.so",
        "libQnnSystem.so",
        "libQnnHtpV79Stub.so",
        "libQnnHtpV79Skel.so",
    )
}
