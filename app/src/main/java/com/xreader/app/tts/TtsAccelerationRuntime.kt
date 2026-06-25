package com.xreader.app.tts

import android.content.Context
import android.os.Build
import android.system.Os
import android.system.OsConstants
import android.util.Log
import com.xreader.app.BuildConfig
import java.io.File
import java.io.FileDescriptor
import java.util.Collections
import java.util.Locale
import java.util.zip.ZipFile

internal object TtsAccelerationRuntime {
    private const val TAG = "TtsAcceleration"
    private val failedProviderKeys = Collections.synchronizedSet(mutableSetOf<String>())
    private val failedProviderReasons = Collections.synchronizedMap(mutableMapOf<String, String>())
    private val packagedNativeLibrariesCacheLock = Any()
    private var packagedNativeLibrariesCache: PackagedNativeLibrariesCache? = null
    private var qnnGpuOpenClDriverFd: FileDescriptor? = null
    private var qnnSignedProcessDomainOverride: Boolean? = null
    private var qnnSignedProcessDomainOptionOverride: Boolean? = null
    private var qnnSocModelOverride: Int? = null
    private var qnnHtpArchOverride: Int? = null

    private val requiredQnnCoreLibraries = setOf(
        "libQnnSystem.so",
    )

    private val requiredQnnGpuLibraries = setOf(
        "libQnnGpu.so",
        "libQnnGpuNetRunExtensions.so",
        "libOpenCL.so",
        "libOpenCL_adreno.so",
    )

    private val requiredQnnHtpLibraries = setOf(
        "libQnnHtp.so",
        "libQnnHtpNetRunExtensions.so",
        "libQnnHtpPrepare.so",
    )

    fun providerOrder(
        context: Context,
        includeExperimentalWebGpu: Boolean = false,
        includeCpuFallbacks: Boolean = true,
        configureQnnProviders: Boolean = true,
    ): List<String> {
        val installedLibraries = packagedNativeLibraries(context)
        val hardware = Build.HARDWARE.orEmpty()
        val boardPlatform = systemProperty("ro.board.platform")
        val socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER.orEmpty() else ""
        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.orEmpty() else ""
        val qnnProviders = qnnProviderStrings(
            context = context,
            installedLibraries = installedLibraries,
            hardware = hardware,
            boardPlatform = boardPlatform,
            socManufacturer = socManufacturer,
            socModel = socModel,
            configureProviders = configureQnnProviders,
        )
        return providerOrder(
            installedLibraries = installedLibraries,
            hasVulkan = context.packageManager.hasSystemFeature("android.hardware.vulkan.level"),
            includeExperimentalWebGpu = includeExperimentalWebGpu,
            androidApiLevel = Build.VERSION.SDK_INT,
            hardware = hardware,
            boardPlatform = boardPlatform,
            socManufacturer = socManufacturer,
            socModel = socModel,
            qnnProviders = qnnProviders,
            includeCpuFallbacks = includeCpuFallbacks,
            logDecisions = true,
        )
    }

    internal fun providerOrder(
        installedLibraries: Set<String>,
        hasVulkan: Boolean,
        includeExperimentalWebGpu: Boolean,
        androidApiLevel: Int = Build.VERSION.SDK_INT,
        hardware: String,
        boardPlatform: String,
        socManufacturer: String,
        socModel: String,
        qnnProviders: List<String> = listOf("qnn"),
        includeCpuFallbacks: Boolean = true,
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
        val nnapi = nnapiReadiness(
            installedLibraries = installedLibraries,
            androidApiLevel = androidApiLevel,
        )
        if (qnn.ready && qnnProviders.isNotEmpty()) {
            providers += qnnProviders
            if (logDecisions) Log.i(TAG, "QNN providers enabled: ${qnn.reason}")
        } else if (qnn.ready) {
            if (logDecisions) Log.i(TAG, "QNN provider staged but no provider config could be written.")
        } else {
            if (logDecisions) Log.i(TAG, "QNN provider unavailable: ${qnn.reason}")
        }
        if (nnapi.ready) {
            providers += NNAPI_PROVIDER
            if (logDecisions) Log.i(TAG, "NNAPI provider enabled: ${nnapi.reason}")
        } else if (logDecisions) {
            Log.i(TAG, "NNAPI provider unavailable: ${nnapi.reason}")
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
        if (includeCpuFallbacks) {
            providers += "xnnpack"
            providers += "cpu"
        } else if (logDecisions) {
            Log.i(TAG, "CPU-backed providers skipped for strict audiobook generation.")
        }
        return providers.filter { provider ->
            val key = providerFailureKey(provider)
            val keep = key == "cpu" || key !in failedProviderKeys
            if (!keep && logDecisions) {
                val reason = failedProviderReasons[key]?.let { " Reason: $it" }.orEmpty()
                Log.i(TAG, "Skipping provider=$key after a previous initialization failure in this process.$reason")
            }
            keep
        }
    }

    fun providerKey(provider: String): String =
        provider.substringBefore(':').trim().lowercase(Locale.US)

    fun isHardwareAcceleratedProvider(provider: String): Boolean =
        providerKey(provider) in setOf("qnn", "nnapi", "webgpu")

    fun isStrictAudiobookHardwareProvider(provider: String): Boolean =
        providerKey(provider) == "qnn"

    fun isAudiobookGenerationAcceleratedProvider(provider: String): Boolean {
        val displayKey = providerDisplayKey(provider)
        return displayKey == "qnn-gpu" || displayKey == "qnn-htp"
    }

    fun providerDisplayKey(provider: String): String {
        val normalized = provider.trim().lowercase(Locale.US)
        if (normalized.startsWith("qnn:")) {
            val path = normalized.substringAfter(':')
            return when {
                "qnn-gpu-hybrid-provider.config" in path -> "qnn-gpu-hybrid"
                "qnn-htp-hybrid-provider.config" in path -> "qnn-htp-hybrid"
                "qnn-gpu-strict-provider.config" in path -> "qnn-gpu"
                "qnn-htp-strict-provider.config" in path -> "qnn-htp"
                "qnn-gpu-provider.config" in path -> "qnn-gpu"
                "qnn-htp-provider.config" in path -> "qnn-htp"
                else -> "qnn"
            }
        }
        return providerKey(provider)
    }

    fun qnnBackend(provider: String): QnnBackend? =
        when (providerDisplayKey(provider)) {
            "qnn-gpu",
            "qnn-gpu-hybrid" -> QnnBackend.GPU
            "qnn-htp",
            "qnn-htp-hybrid" -> QnnBackend.HTP
            else -> null
        }

    fun recordProviderInitialized(provider: String) {
        val key = providerFailureKey(provider)
        failedProviderKeys.remove(key)
        failedProviderReasons.remove(key)
    }

    fun recordProviderInitializationFailed(provider: String, error: Throwable? = null) {
        val key = providerFailureKey(provider)
        if (key != "cpu" && key != "qnn") {
            failedProviderKeys += key
            providerInitializationFailureSummary(provider, error)?.let { failedProviderReasons[key] = it }
        }
    }

    fun overrideQnnSignedProcessDomainForCurrentProcess(value: Boolean?) {
        qnnSignedProcessDomainOverride = value
    }

    fun overrideQnnHtpDeviceOptionsForCurrentProcess(
        includeSignedProcessDomainOption: Boolean? = null,
        socModel: Int? = null,
        htpArch: Int? = null,
    ) {
        qnnSignedProcessDomainOptionOverride = includeSignedProcessDomainOption
        qnnSocModelOverride = socModel
        qnnHtpArchOverride = htpArch
    }

    internal fun clearQnnHtpDeviceOptionOverridesForTests() {
        qnnSignedProcessDomainOverride = null
        qnnSignedProcessDomainOptionOverride = null
        qnnSocModelOverride = null
        qnnHtpArchOverride = null
    }

    fun providerInitializationFailureSummary(provider: String, error: Throwable?): String? {
        if (error == null) return null
        return when {
            isQnnGpuOpenClFailure(provider, error) ->
                "QNN GPU failed before audio generation. " +
                    "The Qualcomm runtime could not load a usable OpenCL driver from the app process."
            isQnnHtpTransportFailure(provider, error) ->
                "QNN HTP/NPU transport failed before audio generation. " +
                    "The Qualcomm runtime could not create the DSP/HTP device for this app process."
            else -> error.message ?: error::class.java.simpleName
        }
    }

    fun audiobookHardwareProviderBlockReason(): String? =
        listOfNotNull(
            failedProviderReasons["qnn-gpu"],
            failedProviderReasons["qnn-htp"],
        ).distinct().joinToString(separator = " ").ifBlank { null }

    internal fun isQnnGpuOpenClFailure(provider: String, error: Throwable): Boolean {
        if (providerDisplayKey(provider) != "qnn-gpu") return false
        val details = error.throwableChainText().lowercase(Locale.US)
        return listOf(
            "invalid opencl driver path",
            "unable to open opencl driver",
            "failed to retrieve opencl platform",
            "failed to retrieve opencl platforms",
            "failed to retrieve opencl devices",
            "gpu_error_failed_creation",
            "qnn_common_error_platform_not_supported",
            "libopencl.so",
            "opencl is not enabled"
        ).any { it in details }
    }

    internal fun isQnnHtpTransportFailure(provider: String, error: Throwable): Boolean {
        if (providerDisplayKey(provider) != "qnn-htp") return false
        val details = error.throwableChainText().lowercase(Locale.US)
        return listOf(
            "qnn_device_error_invalid_config",
            "failed to create transport",
            "failed to load skel",
            "transport layer setup failed",
            "failed to parse platform config",
            "failed to load default platform info",
            "failed to create device",
            "libcdsprpc",
            "libadsprpc",
            "fastrpc"
        ).any { it in details }
    }

    internal fun clearProviderFailuresForTests() {
        failedProviderKeys.clear()
        failedProviderReasons.clear()
    }

    internal fun clearPackagedNativeLibrariesCacheForTests() {
        synchronized(packagedNativeLibrariesCacheLock) {
            packagedNativeLibrariesCache = null
        }
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
        val missingCore = requiredQnnCoreLibraries.filterNot { it in installedLibraries }
        if (missingCore.isNotEmpty()) {
            return QnnReadiness(false, "Missing packaged QNN core libraries: ${missingCore.joinToString()}.")
        }
        val gpu = qnnBackendReadiness(installedLibraries, QnnBackend.GPU)
        val htp = qnnBackendReadiness(installedLibraries, QnnBackend.HTP)
        if (!gpu.ready && !htp.ready) {
            return QnnReadiness(
                false,
                "No packaged QNN hardware backend is usable. GPU: ${gpu.reason} HTP: ${htp.reason}"
            )
        }
        return QnnReadiness(
            true,
            buildString {
                append("Qualcomm target with packaged QNN hardware runtime.")
                if (gpu.ready) append(" GPU ready.")
                if (htp.ready) append(" HTP/NPU ready.")
                if (!gpu.ready) append(" GPU unavailable: ${gpu.reason}")
                if (!htp.ready) append(" HTP unavailable: ${htp.reason}")
            }
        )
    }

    internal fun nnapiReadiness(
        installedLibraries: Set<String>,
        androidApiLevel: Int,
    ): NnapiReadiness {
        if (androidApiLevel < 27) {
            return NnapiReadiness(false, "Android API $androidApiLevel is below NNAPI's supported API 27 floor.")
        }
        if ("libonnxruntime.so" !in installedLibraries || "libsherpa-onnx-jni.so" !in installedLibraries) {
            return NnapiReadiness(false, "Missing packaged Sherpa/ONNX Runtime libraries.")
        }
        return NnapiReadiness(true, "Android NNAPI runtime is available for strict hardware execution.")
    }

    fun qnnProviderString(context: Context): String? {
        return qnnProviderStrings(context).firstOrNull()
    }

    fun qnnProviderStrings(context: Context): List<String> {
        return qnnProviderStrings(
            context = context,
            installedLibraries = packagedNativeLibraries(context),
            hardware = Build.HARDWARE.orEmpty(),
            boardPlatform = systemProperty("ro.board.platform"),
            socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER.orEmpty() else "",
            socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.orEmpty() else "",
            configureProviders = true,
        )
    }

    private fun qnnProviderStrings(
        context: Context,
        installedLibraries: Set<String>,
        hardware: String,
        boardPlatform: String,
        socManufacturer: String,
        socModel: String,
        configureProviders: Boolean,
    ): List<String> {
        val readiness = qnnReadiness(
            installedLibraries = installedLibraries,
            hardware = hardware,
            boardPlatform = boardPlatform,
            socManufacturer = socManufacturer,
            socModel = socModel,
        )
        if (!readiness.ready) return emptyList()
        return qnnProviderModes(installedLibraries).map { mode ->
            if (configureProviders) {
                val configFile = writeQnnProviderConfig(context, socModel, mode)
                "qnn:${configFile.absolutePath}"
            } else {
                "qnn-${mode.backend.configName}"
            }
        }
    }

    internal fun qnnProviderModes(installedLibraries: Set<String>): List<QnnProviderMode> =
        buildList {
            if (qnnBackendReadiness(installedLibraries, QnnBackend.GPU).ready) {
                add(QnnProviderMode(QnnBackend.GPU, QnnExecutionMode.STRICT))
            }
            if (qnnBackendReadiness(installedLibraries, QnnBackend.HTP).ready) {
                add(QnnProviderMode(QnnBackend.HTP, QnnExecutionMode.STRICT))
            }
        }

    internal fun qnnBackendReadiness(
        installedLibraries: Set<String>,
        backend: QnnBackend,
    ): QnnBackendReadiness {
        val missing = when (backend) {
            QnnBackend.GPU -> requiredQnnGpuLibraries.filterNot { it in installedLibraries }
            QnnBackend.HTP -> requiredQnnHtpLibraries.filterNot { it in installedLibraries }
        }
        if (missing.isNotEmpty()) {
            return QnnBackendReadiness(false, "Missing ${backend.displayName} libraries: ${missing.joinToString()}.")
        }
        if (backend == QnnBackend.HTP) {
            val packagedArchitectures = qnnHtpPackagedArchitectureVersions(installedLibraries)
            if (packagedArchitectures.isEmpty()) {
                return QnnBackendReadiness(false, "Missing matching QNN HTP Stub/Skel/DSP libraries.")
            }
        }
        return QnnBackendReadiness(true, "${backend.displayName} runtime is packaged.")
    }

    internal fun qnnHtpPackagedArchitectureVersions(installedLibraries: Set<String>): Set<String> {
        val stubs = installedLibraries.mapNotNull { it.qnnHtpArchitectureVersion("Stub.so") }.toSet()
        val skels = installedLibraries.mapNotNull { it.qnnHtpArchitectureVersion("Skel.so") }.toSet()
        val dspBackends = installedLibraries.mapNotNull { it.qnnHtpArchitectureVersion(".so") }
            .filterNot { version -> "Stub" in version || "Skel" in version }
            .toSet()
        return stubs.intersect(skels).intersect(dspBackends)
    }

    internal fun writeQnnProviderConfig(
        context: Context,
        socModel: String,
        mode: QnnProviderMode = QnnProviderMode(QnnBackend.HTP, QnnExecutionMode.STRICT),
    ): File {
        configureQnnProcessEnvironment(context, mode)
        val configFile = File(
            context.cacheDir,
            "xreader-qnn-${mode.backend.configName}-${mode.execution.configName}-provider.config"
        )
        configFile.writeText(
            qnnProviderConfigText(
                socModel = socModel,
                mode = mode,
                backendPath = qnnBackendPath(context, mode.backend)
            )
        )
        return configFile
    }

    private fun configureQnnProcessEnvironment(
        context: Context,
        mode: QnnProviderMode,
    ) {
        val nativeLibraryDir = context.applicationInfo.nativeLibraryDir
            ?.takeIf { it.isNotBlank() }
            ?: return
        if (mode.backend == QnnBackend.GPU) {
            configureQnnGpuProcessEnvironment(context, nativeLibraryDir)
            return
        }
        if (mode.backend != QnnBackend.HTP) return
        val dspRuntimePath = qnnExtractedDspRuntimePath(context)
        val useSignedProcessDomain = qnnUseSignedProcessDomain()
        val adspLibraryPath = qnnHtpAdspLibraryPath(
            nativeLibraryDir = nativeLibraryDir,
            existingPath = Os.getenv(QNN_ADSP_LIBRARY_PATH),
            includeDebugDiagnosticPath = qnnIncludeDebugDspSearchPath(),
            extractedDspRuntimePath = dspRuntimePath,
            preferDeviceSignedDspRuntime = useSignedProcessDomain,
        )
        runCatching {
            Os.setenv(QNN_ADSP_LIBRARY_PATH, adspLibraryPath, true)
            Log.i(
                TAG,
                "Configured $QNN_ADSP_LIBRARY_PATH for QNN HTP: " +
                    "$adspLibraryPath signedProcessDomain=$useSignedProcessDomain " +
                    "extractedDspRuntimePath=${dspRuntimePath.orEmpty()}"
            )
        }.onFailure { error ->
            Log.w(TAG, "Could not configure $QNN_ADSP_LIBRARY_PATH for QNN HTP.", error)
        }
    }

    private fun configureQnnGpuProcessEnvironment(
        context: Context,
        nativeLibraryDir: String,
    ) {
        val packagedOpenClDirectory = qnnGpuOpenClDriverPath(nativeLibraryDir) ?: return
        val fixedOpenClDriver = bindQnnGpuOpenClDriverFd(nativeLibraryDir)
        val openClDriver = fixedOpenClDriver ?: packagedOpenClDirectory
        val libraryPath = qnnGpuLibrarySearchPath(
            nativeLibraryDir = nativeLibraryDir,
            existingPath = Os.getenv(QNN_GPU_LD_LIBRARY_PATH)
        )
        val icdVendorsPath = qnnGpuIcdVendorsPath(context, nativeLibraryDir)
        runCatching {
            Os.setenv(QNN_GPU_LD_LIBRARY_PATH, libraryPath, true)
            Os.setenv(QNN_GPU_CL_LIBRARY_PATH, openClDriver, true)
            icdVendorsPath?.let { Os.setenv(QNN_GPU_OCL_ICD_VENDORS, it, true) }
            Log.i(
                TAG,
                "Configured QNN GPU OpenCL environment: " +
                    "$QNN_GPU_LD_LIBRARY_PATH=$libraryPath " +
                    "$QNN_GPU_CL_LIBRARY_PATH=$openClDriver " +
                    "fixedOpenClDriver=${fixedOpenClDriver.orEmpty()} " +
                    "$QNN_GPU_OCL_ICD_VENDORS=${icdVendorsPath.orEmpty()}"
            )
        }.onFailure { error ->
            Log.w(TAG, "Could not configure QNN GPU OpenCL environment.", error)
        }
    }

    internal fun qnnGpuOpenClDriverPath(nativeLibraryDir: String): String? {
        val directory = File(nativeLibraryDir)
        return directory
            .takeIf { File(it, "libOpenCL.so").isFile && File(it, "libOpenCL_adreno.so").isFile }
            ?.absolutePath
    }

    internal fun qnnGpuLibrarySearchPath(
        nativeLibraryDir: String,
        existingPath: String? = null,
    ): String {
        val orderedPaths = buildList {
            add(nativeLibraryDir)
            existingPath
                ?.split(QNN_GPU_LIBRARY_PATH_SEPARATOR)
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.let(::addAll)
        }
        return orderedPaths
            .distinct()
            .joinToString(QNN_GPU_LIBRARY_PATH_SEPARATOR)
    }

    internal fun qnnGpuIcdVendorFileText(nativeLibraryDir: String): String =
        File(nativeLibraryDir, "libOpenCL_adreno.so").absolutePath + "\n"

    private fun bindQnnGpuOpenClDriverFd(nativeLibraryDir: String): String? {
        val source = File(nativeLibraryDir, "libOpenCL.so")
        if (!source.isFile) return null
        return runCatching {
            val opened = Os.open(source.absolutePath, OsConstants.O_RDONLY, 0)
            qnnGpuOpenClDriverFd = Os.dup2(opened, QNN_GPU_OPENCL_DRIVER_FD)
            QNN_GPU_OPENCL_DRIVER_FD_PATH
        }.onFailure { error ->
            Log.w(TAG, "Could not bind QNN GPU OpenCL loader to $QNN_GPU_OPENCL_DRIVER_FD_PATH.", error)
        }.getOrNull()
    }

    private fun qnnGpuIcdVendorsPath(
        context: Context,
        nativeLibraryDir: String,
    ): String? {
        val driver = File(nativeLibraryDir, "libOpenCL_adreno.so")
        if (!driver.isFile) return null
        return runCatching {
            val directory = File(context.cacheDir, "opencl-vendors").apply { mkdirs() }
            val vendorFile = File(directory, "xreader-adreno.icd")
            vendorFile.writeText(qnnGpuIcdVendorFileText(nativeLibraryDir))
            directory.absolutePath
        }.onFailure { error ->
            Log.w(TAG, "Could not write QNN GPU OpenCL ICD vendor file.", error)
        }.getOrNull()
    }

    internal fun qnnHtpAdspLibraryPath(
        nativeLibraryDir: String,
        existingPath: String? = null,
        includeDebugDiagnosticPath: Boolean = false,
        extractedDspRuntimePath: String? = null,
        preferDeviceSignedDspRuntime: Boolean = false,
    ): String {
        val orderedPaths = buildList {
            if (preferDeviceSignedDspRuntime) addAll(QNN_HTP_SIGNED_ADSP_SEARCH_PATHS)
            extractedDspRuntimePath
                ?.takeIf { it.isNotBlank() }
                ?.let(::add)
            if (includeDebugDiagnosticPath) add(QNN_HTP_DEBUG_DSP_SEARCH_PATH)
            add(nativeLibraryDir)
            addAll(QNN_HTP_ADSP_SEARCH_PATHS)
            existingPath
                ?.split(QNN_ADSP_PATH_SEPARATOR)
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.let(::addAll)
        }
        return orderedPaths
            .distinct()
            .joinToString(QNN_ADSP_PATH_SEPARATOR)
    }

    private fun qnnExtractedDspRuntimePath(context: Context): String? {
        val normalizedSoc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.orEmpty()
        } else {
            systemProperty("ro.soc.model")
        }.uppercase(Locale.US)
        val arch = qnnHtpArch(normalizedSoc) ?: return null
        val assetDir = "$QNN_DSP_ASSET_ROOT/hexagon-v$arch"
        val assetNames = context.assets.list(assetDir)
            ?.filter { it.isNotBlank() }
            ?.sorted()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val targetDir = File(context.noBackupFilesDir, assetDir)
        return runCatching {
            makeQnnDspPathSearchable(context.dataDir)
            makeQnnDspPathSearchable(context.noBackupFilesDir)
            targetDir.mkdirs()
            makeQnnDspDirectoryReadable(targetDir.parentFile)
            makeQnnDspDirectoryReadable(targetDir)
            assetNames.forEach { name ->
                val target = File(targetDir, name)
                context.assets.open("$assetDir/$name").use { input ->
                    val bytes = input.readBytes()
                    if (!target.isFile || target.length() != bytes.size.toLong()) {
                        target.outputStream().use { output -> output.write(bytes) }
                    }
                }
                makeQnnDspFileReadable(target)
            }
            targetDir.absolutePath
        }.onFailure { error ->
            Log.w(TAG, "Could not extract QNN HTP DSP runtime assets from $assetDir.", error)
        }.getOrNull()
    }

    private fun makeQnnDspPathSearchable(directory: File?) {
        directory ?: return
        runCatching {
            if (directory.isDirectory) Os.chmod(directory.absolutePath, QNN_DSP_PARENT_DIRECTORY_MODE)
        }.onFailure { error ->
            Log.w(TAG, "Could not make QNN DSP parent path searchable: ${directory.absolutePath}", error)
        }
    }

    private fun makeQnnDspDirectoryReadable(directory: File?) {
        directory ?: return
        runCatching {
            if (directory.isDirectory) Os.chmod(directory.absolutePath, QNN_DSP_DIRECTORY_MODE)
        }.onFailure { error ->
            Log.w(TAG, "Could not make QNN DSP runtime directory readable: ${directory.absolutePath}", error)
        }
    }

    private fun makeQnnDspFileReadable(file: File) {
        runCatching {
            if (file.isFile) Os.chmod(file.absolutePath, QNN_DSP_FILE_MODE)
        }.onFailure { error ->
            Log.w(TAG, "Could not make QNN DSP runtime file readable: ${file.absolutePath}", error)
        }
    }

    internal fun qnnProviderConfigText(
        socModel: String,
        mode: QnnProviderMode = QnnProviderMode(QnnBackend.HTP, QnnExecutionMode.STRICT),
        backendPath: String? = null,
    ): String {
        val normalizedSoc = socModel.uppercase(Locale.US)
        val options = linkedMapOf(
            "backend_path" to (backendPath ?: mode.backend.providerLibrary),
            "disable_cpu_ep_fallback" to if (mode.execution == QnnExecutionMode.STRICT) "1" else "0",
            "offload_graph_io_quantization" to "0",
            "log_severity_level" to "0",
            "skip_qnn_version_check" to "0",
            "ep.context_enable" to "1",
            "ep.context_embed_mode" to "1",
        )
        if (mode.backend == QnnBackend.HTP) {
            options["htp_performance_mode"] = "burst"
            options["htp_graph_finalization_optimization_mode"] = "2"
            options["qnn_context_priority"] = "high"
            options["enable_htp_fp16_precision"] = "1"
            if (qnnIncludeSignedProcessDomainOption()) {
                options["htp_use_signed_process_domain"] = qnnUseSignedProcessDomain().asQnnBool()
            }
            options["vtcm_mb"] = qnnHtpVtcmMb(normalizedSoc).toString()
            options["rpc_control_latency"] = QNN_HTP_RPC_CONTROL_LATENCY_MS.toString()
            options["device_id"] = QNN_HTP_DEVICE_ID.toString()
            qnnSocModelOverride
                ?.takeIf { it >= 0 }
                ?.let { options["soc_model"] = it.toString() }
            qnnHtpArchOverride
                ?.takeIf { it >= 0 }
                ?.let { options["htp_arch"] = it.toString() }
        }
        return buildString {
            appendLine(
                "# Generated by XReader for ONNX Runtime QNNExecutionProvider " +
                    "${mode.backend.displayName} ${mode.execution.displayName}."
            )
            options.forEach { (key, value) -> appendLine("$key=$value") }
        }
    }

    internal fun qnnBackendPath(
        context: Context,
        backend: QnnBackend,
    ): String = qnnBackendPath(
        nativeLibraryDir = context.applicationInfo.nativeLibraryDir,
        backend = backend
    )

    internal fun qnnBackendPath(
        nativeLibraryDir: String?,
        backend: QnnBackend,
    ): String {
        val directory = nativeLibraryDir?.takeIf { it.isNotBlank() } ?: return backend.providerLibrary
        val extracted = File(directory, backend.providerLibrary)
        return if (extracted.isFile) extracted.absolutePath else backend.providerLibrary
    }

    internal fun qnnSocModelId(normalizedSocModel: String): Int? =
        when {
            "SM8750" in normalizedSocModel -> 69
            "SM8650" in normalizedSocModel -> 57
            else -> null
        }

    internal fun qnnHtpArch(normalizedSocModel: String): Int? =
        when {
            "SM8750" in normalizedSocModel -> 79
            "SM8650" in normalizedSocModel -> 75
            else -> null
        }

    internal fun qnnHtpVtcmMb(normalizedSocModel: String): Int =
        when {
            "SM8350" in normalizedSocModel -> 4
            else -> 8
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

    private fun qnnIncludeDebugDspSearchPath(): Boolean =
        BuildConfig.DEBUG && systemProperty(QNN_DEBUG_DSP_SEARCH_PROPERTY) == "1"

    private fun qnnUseSignedProcessDomain(): Boolean =
        qnnSignedProcessDomainOverride
            ?: (systemProperty(QNN_SIGNED_PROCESS_DOMAIN_PROPERTY) == "1")

    private fun qnnIncludeSignedProcessDomainOption(): Boolean =
        qnnSignedProcessDomainOptionOverride ?: false

    private fun Boolean.asQnnBool(): String = if (this) "1" else "0"

    private fun packagedNativeLibraries(context: Context): Set<String> {
        val key = PackagedNativeLibrariesKey(
            nativeLibraryDir = context.applicationInfo.nativeLibraryDir,
            sourcePaths = buildList {
                context.applicationInfo.sourceDir?.let(::add)
                context.applicationInfo.splitSourceDirs?.forEach(::add)
            }
        )
        return cachedPackagedNativeLibraries(key) {
            discoverPackagedNativeLibraries(
                nativeLibraryDir = key.nativeLibraryDir,
                sourcePaths = key.sourcePaths
            )
        }
    }

    internal fun cachedPackagedNativeLibraries(
        key: PackagedNativeLibrariesKey,
        discover: () -> Set<String>,
    ): Set<String> {
        synchronized(packagedNativeLibrariesCacheLock) {
            packagedNativeLibrariesCache
                ?.takeIf { it.key == key }
                ?.let { return it.libraries }
        }
        val libraries = discover()
        synchronized(packagedNativeLibrariesCacheLock) {
            val current = packagedNativeLibrariesCache
            if (current?.key == key) return current.libraries
            packagedNativeLibrariesCache = PackagedNativeLibrariesCache(key, libraries)
        }
        return libraries
    }

    private fun discoverPackagedNativeLibraries(
        nativeLibraryDir: String?,
        sourcePaths: List<String>,
    ): Set<String> {
        val libraries = mutableSetOf<String>()
        nativeLibraryDir
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.listFiles()
            ?.mapTo(libraries) { it.name }

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

    private fun providerFailureKey(provider: String): String =
        providerDisplayKey(provider)

    private fun Throwable.throwableChainText(): String {
        val seen = mutableSetOf<Throwable>()
        val parts = mutableListOf<String>()
        var current: Throwable? = this
        while (current != null && seen.add(current)) {
            parts += current::class.java.name
            current.message?.let(parts::add)
            current = current.cause
        }
        return parts.joinToString(separator = "\n")
    }

    private fun String.qnnHtpArchitectureVersion(suffix: String): String? {
        if (!startsWith("libQnnHtpV") || !endsWith(suffix)) return null
        return removePrefix("libQnnHtpV")
            .removeSuffix(suffix)
            .takeIf { version -> version.isNotBlank() && version.all(Char::isDigit) }
    }

    private const val NNAPI_PROVIDER = "nnapi"
    private const val QNN_ADSP_LIBRARY_PATH = "ADSP_LIBRARY_PATH"
    private const val QNN_DSP_ASSET_ROOT = "qnn-dsp"
    private const val QNN_GPU_LD_LIBRARY_PATH = "LD_LIBRARY_PATH"
    private const val QNN_GPU_CL_LIBRARY_PATH = "CL_LIBRARY_PATH"
    private const val QNN_GPU_OCL_ICD_VENDORS = "OCL_ICD_VENDORS"
    private const val QNN_GPU_LIBRARY_PATH_SEPARATOR = ":"
    private const val QNN_GPU_OPENCL_DRIVER_FD = 198
    private const val QNN_GPU_OPENCL_DRIVER_FD_PATH = "/dev/fd/198"
    private const val QNN_ADSP_PATH_SEPARATOR = ";"
    private const val QNN_HTP_DEVICE_ID = 0
    private const val QNN_HTP_RPC_CONTROL_LATENCY_MS = 200
    private const val QNN_DSP_PARENT_DIRECTORY_MODE = 0x1C9 // 0711: app-private data stays unlistable, but FastRPC can traverse.
    private const val QNN_DSP_DIRECTORY_MODE = 0x1ED // 0755
    private const val QNN_DSP_FILE_MODE = 0x1A4 // 0644
    private const val QNN_DEBUG_DSP_SEARCH_PROPERTY = "debug.xreader.qnn.local_tmp_dsp"
    private const val QNN_SIGNED_PROCESS_DOMAIN_PROPERTY = "debug.xreader.qnn.signed_pd"
    private const val QNN_HTP_DEBUG_DSP_SEARCH_PATH = "/data/local/tmp/xreader-qnn-dsp"
    private val QNN_HTP_SIGNED_ADSP_SEARCH_PATHS = listOf(
        "/vendor/lib64/hw/audio",
        "/vendor/lib64/rfs/dsp",
    )
    private val QNN_HTP_ADSP_SEARCH_PATHS = listOf(
        "/odm/lib/rfsa/adsp",
        "/vendor/lib/rfsa/adsp/",
        "/system/lib/rfsa/adsp",
        "/system/vendor/lib/rfsa/adsp",
        "/dsp",
    )
}

internal data class PackagedNativeLibrariesKey(
    val nativeLibraryDir: String?,
    val sourcePaths: List<String>,
)

private data class PackagedNativeLibrariesCache(
    val key: PackagedNativeLibrariesKey,
    val libraries: Set<String>,
)

internal data class QnnReadiness(
    val ready: Boolean,
    val reason: String,
)

internal data class QnnBackendReadiness(
    val ready: Boolean,
    val reason: String,
)

internal data class WebGpuReadiness(
    val ready: Boolean,
    val reason: String,
)

internal data class NnapiReadiness(
    val ready: Boolean,
    val reason: String,
)

internal enum class QnnBackend(
    val configName: String,
    val displayName: String,
    val providerType: String,
    val providerLibrary: String,
) {
    GPU("gpu", "GPU", "gpu", "libQnnGpu.so"),
    HTP("htp", "HTP/NPU", "htp", "libQnnHtp.so"),
}

internal data class QnnProviderMode(
    val backend: QnnBackend,
    val execution: QnnExecutionMode,
)

internal enum class QnnExecutionMode(
    val configName: String,
    val displayName: String,
) {
    STRICT("strict", "strict"),
    HYBRID("hybrid", "hybrid"),
}
