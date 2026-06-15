package com.xreader.app.tts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsAccelerationRuntimeTest {
    @Test
    fun stableProviderOrderExcludesExperimentalWebGpu() {
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
