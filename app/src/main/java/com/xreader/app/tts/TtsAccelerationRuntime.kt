package com.xreader.app.tts

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.system.Os
import android.util.Log
import com.xreader.app.BuildConfig
import java.io.File
import java.util.Collections
import java.util.Locale
import java.util.zip.ZipFile

internal object TtsAccelerationRuntime {
    private const val TAG = "TtsAcceleration"
    private val failedProviderKeys = Collections.synchronizedSet(mutableSetOf<String>())
    private val failedProviderReasons = Collections.synchronizedMap(mutableMapOf<String, String>())
    private val packagedNativeLibrariesCacheLock = Any()
    private var packagedNativeLibrariesCache: PackagedNativeLibrariesCache? = null
    private var qnnSignedProcessDomainOverride: Boolean? = null
    private var qnnSignedProcessDomainOptionOverride: Boolean? = null
    private var qnnSocModelOverride: Int? = null
    private var qnnHtpArchOverride: Int? = null

    private val requiredQnnCoreLibraries = setOf(
        "libQnnSystem.so",
    )

    private val requiredQnnHtpLibraries = setOf(
        "libQnnHtp.so",
        "libQnnHtpNetRunExtensions.so",
        "libQnnHtpPrepare.so",
    )

    fun providerOrder(
        context: Context,
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
            androidApiLevel = Build.VERSION.SDK_INT,
            hardware = hardware,
            boardPlatform = boardPlatform,
            socManufacturer = socManufacturer,
            socModel = socModel,
            qnnProviders = qnnProviders,
            logDecisions = true,
        )
    }

    internal fun providerOrder(
        installedLibraries: Set<String>,
        androidApiLevel: Int = Build.VERSION.SDK_INT,
        hardware: String,
        boardPlatform: String,
        socManufacturer: String,
        socModel: String,
        qnnProviders: List<String> = listOf("qnn"),
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
        val selectedQnnProvider = selectedQnnProvider(qnnProviders)
        if (qnn.ready && selectedQnnProvider != null) {
            providers += selectedQnnProvider
            if (logDecisions) {
                Log.i(
                    TAG,
                    "QNN hardware provider enabled: " +
                        "${providerDisplayKey(selectedQnnProvider)}. ${qnn.reason}"
                )
            }
        } else if (qnn.ready) {
            if (logDecisions) Log.i(TAG, "QNN provider staged but no provider config could be written.")
        } else {
            if (logDecisions) Log.i(TAG, "QNN provider unavailable: ${qnn.reason}")
        }
        if (logDecisions) Log.i(TAG, "Only QNN HTP/NPU neural TTS generation is enabled.")
        return providers.filter { provider ->
            val key = providerFailureKey(provider)
            val keep = key !in failedProviderKeys
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
        isStrictQnnHardwareProvider(provider)

    fun isStrictAudiobookHardwareProvider(provider: String): Boolean =
        isAudiobookGenerationAcceleratedProvider(provider)

    fun isAudiobookGenerationAcceleratedProvider(provider: String): Boolean {
        return isStrictQnnHardwareProvider(provider)
    }

    private fun isStrictQnnHardwareProvider(provider: String): Boolean {
        val displayKey = providerDisplayKey(provider)
        return displayKey == "qnn-htp"
    }

    fun providerDisplayKey(provider: String): String {
        val normalized = provider.trim().lowercase(Locale.US)
        if (normalized.startsWith("qnn:")) {
            val path = normalized.substringAfter(':')
            return when {
                "qnn-htp-strict-provider.config" in path -> "qnn-htp"
                "qnn-htp-provider.config" in path -> "qnn-htp"
                else -> "qnn"
            }
        }
        return providerKey(provider)
    }

    fun qnnBackend(provider: String): QnnBackend? =
        when (providerDisplayKey(provider)) {
            "qnn-htp" -> QnnBackend.HTP
            else -> null
        }

    internal fun selectedQnnProvider(providers: List<String>): String? =
        providers.firstOrNull { providerDisplayKey(it) == "qnn-htp" }

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
            isQnnHtpTransportFailure(provider, error) ->
                "QNN HTP/NPU transport failed before audio generation. " +
                    "The Qualcomm runtime could not create the DSP/HTP device for this app process."
            else -> error.message ?: error::class.java.simpleName
        }
    }

    fun audiobookHardwareProviderBlockReason(): String? =
        failedProviderReasons["qnn-htp"]?.trim()?.takeIf { it.isNotBlank() }

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
        val htp = qnnBackendReadiness(installedLibraries, QnnBackend.HTP)
        if (!htp.ready) {
            return QnnReadiness(
                false,
                "QNN HTP/NPU hardware pipeline is not usable. ${htp.reason}"
            )
        }
        return QnnReadiness(
            true,
            buildString {
                append("Qualcomm target with packaged QNN HTP/NPU hardware runtime.")
            }
        )
    }

    fun qnnProviderString(context: Context): String? =
        qnnProviderStrings(context).firstOrNull()

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
        val mode = qnnProviderMode(installedLibraries) ?: return emptyList()
        val provider = if (configureProviders) {
            val configFile = writeQnnProviderConfig(context, socModel, mode)
            "qnn:${configFile.absolutePath}"
        } else {
            "qnn-${mode.backend.configName}"
        }
        return listOf(provider)
    }

    internal fun qnnProviderModes(installedLibraries: Set<String>): List<QnnProviderMode> =
        qnnProviderMode(installedLibraries)?.let(::listOf).orEmpty()

    internal fun qnnProviderMode(installedLibraries: Set<String>): QnnProviderMode? =
        when {
            qnnBackendReadiness(installedLibraries, QnnBackend.HTP).ready ->
                QnnProviderMode(QnnBackend.HTP, QnnExecutionMode.STRICT)
            else -> null
        }

    internal fun qnnBackendReadiness(
        installedLibraries: Set<String>,
        backend: QnnBackend,
    ): QnnBackendReadiness {
        val missing = requiredQnnHtpLibraries.filterNot { it in installedLibraries }
        if (missing.isNotEmpty()) {
            return QnnBackendReadiness(false, "Missing ${backend.displayName} libraries: ${missing.joinToString()}.")
        }
        val packagedArchitectures = qnnHtpPackagedArchitectureVersions(installedLibraries)
        if (packagedArchitectures.isEmpty()) {
            return QnnBackendReadiness(false, "Missing matching QNN HTP Stub/Skel/DSP libraries.")
        }
        return QnnBackendReadiness(true, "${backend.displayName} runtime is packaged.")
    }

    internal fun qnnHtpPackagedArchitectureVersions(installedLibraries: Set<String>): Set<String> {
        val stubs = mutableSetOf<String>()
        val skels = mutableSetOf<String>()
        val dspBackends = mutableSetOf<String>()
        installedLibraries.forEach { library ->
            library.qnnHtpArchitectureVersion("Stub.so")?.let { version ->
                stubs += version
                return@forEach
            }
            library.qnnHtpArchitectureVersion("Skel.so")?.let { version ->
                skels += version
                return@forEach
            }
            library.qnnHtpArchitectureVersion(".so")?.let { version ->
                dspBackends += version
            }
        }
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
            "disable_cpu_ep_fallback" to "1",
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

    // Read-only debug/runtime hints have no public API; failures are contained and become an empty value.
    @SuppressLint("PrivateApi")
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

    private const val QNN_ADSP_LIBRARY_PATH = "ADSP_LIBRARY_PATH"
    private const val QNN_DSP_ASSET_ROOT = "qnn-dsp"
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

internal enum class QnnBackend(
    val configName: String,
    val displayName: String,
    val providerType: String,
    val providerLibrary: String,
) {
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
}
