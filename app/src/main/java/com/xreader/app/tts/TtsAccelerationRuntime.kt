package com.xreader.app.tts

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.util.Collections
import java.util.Locale
import java.util.zip.ZipFile

internal object TtsAccelerationRuntime {
    private const val TAG = "TtsAcceleration"
    private val failedProviderKeys = Collections.synchronizedSet(mutableSetOf<String>())

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
        val installedLibraries = packagedNativeLibraries(context)
        val hardware = Build.HARDWARE.orEmpty()
        val boardPlatform = systemProperty("ro.board.platform")
        val socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER.orEmpty() else ""
        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.orEmpty() else ""
        val qnnProvider = qnnProviderString(
            context = context,
            installedLibraries = installedLibraries,
            hardware = hardware,
            boardPlatform = boardPlatform,
            socManufacturer = socManufacturer,
            socModel = socModel,
        )
        return providerOrder(
            installedLibraries = installedLibraries,
            hasVulkan = context.packageManager.hasSystemFeature("android.hardware.vulkan.level"),
            includeExperimentalWebGpu = includeExperimentalWebGpu,
            hardware = hardware,
            boardPlatform = boardPlatform,
            socManufacturer = socManufacturer,
            socModel = socModel,
            qnnProvider = qnnProvider,
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
        qnnProvider: String? = "qnn",
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
        if (qnn.ready && qnnProvider != null) {
            providers += qnnProvider
            if (logDecisions) Log.i(TAG, "QNN provider enabled: ${qnn.reason}")
        } else if (qnn.ready) {
            if (logDecisions) Log.i(TAG, "QNN provider staged but no provider config could be written.")
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
        return providers.filter { provider ->
            val key = providerKey(provider)
            val keep = key == "cpu" || key !in failedProviderKeys
            if (!keep && logDecisions) Log.i(TAG, "Skipping provider=$key after a previous initialization failure in this process.")
            keep
        }
    }

    fun providerKey(provider: String): String =
        provider.substringBefore(':').trim().lowercase(Locale.US)

    fun recordProviderInitialized(provider: String) {
        failedProviderKeys.remove(providerKey(provider))
    }

    fun recordProviderInitializationFailed(provider: String) {
        val key = providerKey(provider)
        if (key != "cpu") failedProviderKeys += key
    }

    internal fun clearProviderFailuresForTests() {
        failedProviderKeys.clear()
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
        return QnnReadiness(true, "Qualcomm target with packaged QNN HTP runtime.")
    }

    fun qnnProviderString(context: Context): String? {
        return qnnProviderString(
            context = context,
            installedLibraries = packagedNativeLibraries(context),
            hardware = Build.HARDWARE.orEmpty(),
            boardPlatform = systemProperty("ro.board.platform"),
            socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER.orEmpty() else "",
            socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.orEmpty() else "",
        )
    }

    private fun qnnProviderString(
        context: Context,
        installedLibraries: Set<String>,
        hardware: String,
        boardPlatform: String,
        socManufacturer: String,
        socModel: String,
    ): String? {
        val readiness = qnnReadiness(
            installedLibraries = installedLibraries,
            hardware = hardware,
            boardPlatform = boardPlatform,
            socManufacturer = socManufacturer,
            socModel = socModel,
        )
        if (!readiness.ready) return null
        val configFile = writeQnnProviderConfig(context, socModel)
        return "qnn:${configFile.absolutePath}"
    }

    internal fun writeQnnProviderConfig(context: Context, socModel: String): File {
        val configFile = File(context.cacheDir, "xreader-qnn-provider.config")
        val normalizedSoc = socModel.uppercase(Locale.US)
        val options = linkedMapOf(
            "backend_path" to "libQnnHtp.so",
            "log_severity_level" to "0",
            "htp_performance_mode" to "burst",
        )
        qnnSocModelId(normalizedSoc)?.let { options["soc_model"] = it.toString() }
        qnnHtpArch(normalizedSoc)?.let { options["htp_arch"] = it.toString() }
        configFile.writeText(
            buildString {
                appendLine("# Generated by XReader for ONNX Runtime QNNExecutionProvider.")
                options.forEach { (key, value) -> appendLine("$key=$value") }
            }
        )
        return configFile
    }

    internal fun qnnSocModelId(normalizedSocModel: String): Int? =
        when {
            "SM8750" in normalizedSocModel -> 69
            "SM8650" in normalizedSocModel -> 57
            else -> null
        }

    internal fun qnnHtpArch(normalizedSocModel: String): Int? =
        when {
            "SM8750" in normalizedSocModel -> 81
            "SM8650" in normalizedSocModel -> 75
            else -> null
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
