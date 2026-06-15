package com.xreader.app.tts

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile

internal object TtsAccelerationRuntime {
    private const val TAG = "TtsAcceleration"
    private const val ENABLE_QNN_BY_DEFAULT = false

    private val requiredQnnLibraries = setOf(
        "libQnnGpu.so",
        "libQnnHtp.so",
        "libQnnHtpPrepare.so",
        "libQnnSystem.so",
    )

    fun providerOrder(
        context: Context,
        includeExperimentalWebGpu: Boolean = false,
    ): List<String> {
        return providerOrder(
            installedLibraries = packagedNativeLibraries(context),
            hasVulkan = context.packageManager.hasSystemFeature("android.hardware.vulkan.level"),
            includeExperimentalWebGpu = includeExperimentalWebGpu,
            hardware = Build.HARDWARE.orEmpty(),
            boardPlatform = systemProperty("ro.board.platform"),
            socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER.orEmpty() else "",
            socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.orEmpty() else "",
            logDecisions = true,
        )
    }

    internal fun providerOrder(
        installedLibraries: Set<String>,
        hasVulkan: Boolean,
        includeExperimentalWebGpu: Boolean,
        hardware: String,
        boardPlatform: String,
        socManufacturer: String,
        socModel: String,
        logDecisions: Boolean = false,
    ): List<String> {
        val providers = mutableListOf<String>()
        val qnn = qnnReadiness(
            installedLibraries = installedLibraries,
            hardware = hardware,
            boardPlatform = boardPlatform,
            socManufacturer = socManufacturer,
            socModel = socModel,
        )
        if (qnn.ready && ENABLE_QNN_BY_DEFAULT) {
            providers += "qnn"
            if (logDecisions) Log.i(TAG, "QNN provider enabled: ${qnn.reason}")
        } else if (qnn.ready) {
            if (logDecisions) Log.i(TAG, "QNN provider staged but not enabled by default: no no-fallback hardware smoke has passed on this device.")
        } else {
            if (logDecisions) Log.i(TAG, "QNN provider unavailable: ${qnn.reason}")
        }
        if (includeExperimentalWebGpu) {
            val webGpu = webGpuReadiness(
                installedLibraries = installedLibraries,
                hasVulkan = hasVulkan,
            )
            if (webGpu.ready) {
                providers += "webgpu"
                if (logDecisions) Log.i(TAG, "WebGPU provider enabled for experimental Android Vulkan acceleration.")
            } else {
                if (logDecisions) Log.i(TAG, "WebGPU provider unavailable: ${webGpu.reason}")
            }
        } else {
            if (logDecisions) Log.i(TAG, "WebGPU provider skipped for stable audiobook generation.")
        }
        providers += "xnnpack"
        providers += "cpu"
        return providers
    }

    fun webGpuReadiness(context: Context): WebGpuReadiness {
        return webGpuReadiness(
            installedLibraries = packagedNativeLibraries(context),
            hasVulkan = context.packageManager.hasSystemFeature("android.hardware.vulkan.level"),
        )
    }

    internal fun webGpuReadiness(
        installedLibraries: Set<String>,
        hasVulkan: Boolean,
    ): WebGpuReadiness {
        if (!hasVulkan) {
            return WebGpuReadiness(false, "Device does not advertise Vulkan support.")
        }
        if ("libonnxruntime.so" !in installedLibraries || "libsherpa-onnx-jni.so" !in installedLibraries) {
            return WebGpuReadiness(false, "Missing packaged Sherpa/ONNX Runtime libraries.")
        }
        return WebGpuReadiness(true, "Device advertises Vulkan and packaged ONNX Runtime includes WebGPU on supported builds.")
    }

    fun qnnReadiness(context: Context): QnnReadiness {
        val installedLibraries = packagedNativeLibraries(context)
        return qnnReadiness(
            installedLibraries = installedLibraries,
            hardware = Build.HARDWARE.orEmpty(),
            boardPlatform = systemProperty("ro.board.platform"),
            socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER.orEmpty() else "",
            socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.orEmpty() else "",
        )
    }

    internal fun qnnReadiness(
        installedLibraries: Set<String>,
        hardware: String,
        boardPlatform: String,
        socManufacturer: String,
        socModel: String,
    ): QnnReadiness {
        if (!isQualcommDevice(hardware, boardPlatform, socManufacturer, socModel)) {
            return QnnReadiness(false, "Device is not a Qualcomm Snapdragon/QTI target.")
        }
        val missing = requiredQnnLibraries.filterNot { it in installedLibraries }
        if (missing.isNotEmpty()) {
            return QnnReadiness(false, "Missing packaged QNN libraries: ${missing.joinToString()}.")
        }
        val hasHtpStub = installedLibraries.any { it.startsWith("libQnnHtpV") && it.endsWith("Stub.so") }
        val hasHtpSkel = installedLibraries.any { it.startsWith("libQnnHtpV") && it.endsWith("Skel.so") }
        if (!hasHtpStub || !hasHtpSkel) {
            return QnnReadiness(false, "Missing matching QNN HTP Stub/Skel libraries.")
        }
        return QnnReadiness(true, "Qualcomm target with packaged QNN GPU/HTP runtime.")
    }

    private fun isQualcommDevice(
        hardware: String,
        boardPlatform: String,
        socManufacturer: String,
        socModel: String,
    ): Boolean {
        val haystack = listOf(hardware, boardPlatform, socManufacturer, socModel)
            .joinToString(separator = " ")
            .lowercase(Locale.US)
        return listOf("qcom", "qti", "qualcomm", "snapdragon", "sm8").any { it in haystack }
    }

    private fun systemProperty(name: String): String =
        runCatching {
            Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java)
                .invoke(null, name) as? String
        }.getOrNull().orEmpty()

    private fun packagedNativeLibraries(context: Context): Set<String> {
        val libraries = mutableSetOf<String>()
        context.applicationInfo.nativeLibraryDir
            ?.let(::File)
            ?.listFiles()
            ?.mapTo(libraries) { it.name }

        val sourcePaths = buildList {
            context.applicationInfo.sourceDir?.let(::add)
            context.applicationInfo.splitSourceDirs?.forEach(::add)
        }
        sourcePaths.forEach { sourcePath ->
            runCatching {
                ZipFile(sourcePath).use { zip ->
                    zip.entries().asSequence()
                        .map { it.name }
                        .filter { it.startsWith("lib/arm64-v8a/") && it.endsWith(".so") }
                        .mapTo(libraries) { it.substringAfterLast('/') }
                }
            }.onFailure { error ->
                Log.w(TAG, "Could not inspect APK native libraries in $sourcePath.", error)
            }
        }
        return libraries
    }
}

internal data class QnnReadiness(
    val ready: Boolean,
    val reason: String,
)

internal data class WebGpuReadiness(
    val ready: Boolean,
    val reason: String,
)
