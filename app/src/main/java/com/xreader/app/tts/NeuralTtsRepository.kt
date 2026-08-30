package com.xreader.app.tts

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Trace
import android.util.Log
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import com.xreader.app.data.BookAudioEntity
import com.xreader.app.data.BookAudioStatus
import com.xreader.app.data.BookAudioWithBook
import com.xreader.app.data.NeuralTtsDao
import com.xreader.app.data.NeuralTtsModelEntity
import com.xreader.app.data.NeuralTtsModelStatus
import com.xreader.app.settings.NeuralTtsPace
import com.xreader.app.settings.NeuralTtsTone
import java.io.BufferedInputStream
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Clock
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

class NeuralTtsRepository(
    context: Context,
    private val dao: NeuralTtsDao,
    private val clock: Clock = Clock.systemUTC(),
    private val generationDispatcher: CoroutineDispatcher = neuralTtsGenerationDispatcher(),
    private val previewDispatcher: CoroutineDispatcher = neuralTtsPreviewDispatcher(),
    private val audioSaveDispatcher: CoroutineDispatcher = neuralTtsAudioSaveDispatcher(),
) {
    private val tag = "NeuralTtsRepository"
    private val appContext = context.applicationContext
    private val modelRoot = File(appContext.filesDir, "neural-tts/models")
    private val audioRoot = File(appContext.filesDir, "neural-tts/book-audio")
    private val activeModelDownloads = ConcurrentHashMap<String, Job>()
    private val previewRoot = File(appContext.cacheDir, "neural-tts/previews")
    private val catalogSeedMutex = Mutex()
    @Volatile
    private var catalogSeededForProcess = false

    fun observeModels(): Flow<List<NeuralTtsModelEntity>> = flow {
        ensureCatalogSeeded()
        emitAll(dao.observeModels())
    }

    fun observeBookAudio(bookId: Long): Flow<List<BookAudioEntity>> = dao.observeBookAudio(bookId)

    fun observeVisibleAudiobookScreenRows(): Flow<List<BookAudioWithBook>> =
        dao.observeVisibleAudiobookScreenRows()

    suspend fun audiobookGenerationHardwareReadiness(
        modelId: String = NeuralTtsModelCatalog.DEFAULT_MODEL_ID,
    ): AudiobookGenerationHardwareReadiness = withContext(Dispatchers.IO) {
        val spec = NeuralTtsModelCatalog.requireModel(modelId)
        val model = dao.model(modelId)
        if (model?.status != NeuralTtsModelStatus.INSTALLED || model.localPath.isNullOrBlank()) {
            return@withContext AudiobookGenerationHardwareReadiness(
                ready = false,
                reason = "Download ${spec.displayName} before generating audiobook audio."
            )
        }
        val providers = TtsAccelerationRuntime.providerOrder(
            context = appContext,
            configureQnnProviders = false
        )
        val hardwareProviders = providers.filter(TtsAccelerationRuntime::isAudiobookGenerationAcceleratedProvider)
        if (hardwareProviders.isEmpty()) {
            val qnn = TtsAccelerationRuntime.qnnReadiness(appContext)
            val blockedReason = TtsAccelerationRuntime.audiobookHardwareProviderBlockReason()
            return@withContext AudiobookGenerationHardwareReadiness(
                ready = false,
                reason = if (blockedReason != null) {
                    "$blockedReason QNN status: ${qnn.reason}"
                } else {
                    "No strict hardware audiobook provider is available in this build. " +
                        "QNN status: ${qnn.reason}"
                }
            )
        }
        val modelDir = File(model.localPath)
        val missingArtifactProviders = hardwareProviders.filterNot { provider ->
            neuralTtsProviderHasRequiredModelArtifact(spec, modelDir, provider)
        }
        val usableProviders = hardwareProviders - missingArtifactProviders.toSet()
        if (usableProviders.isEmpty()) {
            return@withContext AudiobookGenerationHardwareReadiness(
                ready = false,
                reason = neuralTtsMissingHardwareArtifactReason(spec, missingArtifactProviders)
            )
        }
        AudiobookGenerationHardwareReadiness(
            ready = true,
            providerLabels = usableProviders.map(TtsAccelerationRuntime::providerDisplayKey)
        )
    }

    suspend fun requireAudiobookGenerationHardwareReady(
        modelId: String = NeuralTtsModelCatalog.DEFAULT_MODEL_ID,
    ): AudiobookGenerationHardwareReadiness {
        val readiness = audiobookGenerationHardwareReadiness(modelId)
        require(readiness.ready) {
            readiness.reason ?: "Full-book neural audiobook generation requires strict hardware acceleration."
        }
        return readiness
    }

    suspend fun generatedBookAudio(
        bookId: Long,
        modelId: String,
        speakerId: Int = 0,
        pace: NeuralTtsPace = NeuralTtsPace.STANDARD,
        tone: NeuralTtsTone = NeuralTtsTone.NATURAL,
        scope: AudiobookGenerationScope = AudiobookGenerationScope.FULL_BOOK,
    ): BookAudioEntity? = withContext(Dispatchers.IO) {
        dao.bookAudio(bookId, modelId, speakerId, pace.speed, tone.name, scope.key)
            ?.let { repairBookAudioFilesystemState(it) }
            ?.takeIf { it.hasVerifiedCompleteGeneratedAudiobook() }
    }

    suspend fun bestPlayableBookAudio(
        bookId: Long,
        modelId: String,
        speakerId: Int = 0,
        pace: NeuralTtsPace = NeuralTtsPace.STANDARD,
        tone: NeuralTtsTone = NeuralTtsTone.NATURAL,
    ): BookAudioEntity? = withContext(Dispatchers.IO) {
        dao.bookAudioForProfile(
            bookId = bookId,
            modelId = modelId,
            speakerId = speakerId,
            speed = pace.speed,
            tone = tone.name
        )
            .playableAudiobooksForProfile(
                modelId = modelId,
                speakerId = speakerId,
                speed = pace.speed,
                tone = tone.name,
                verifyFiles = false
            )
            .firstNotNullOfOrNull { candidate ->
                repairBookAudioFilesystemState(candidate)
                    .takeIf { it.playableSegmentCount() > 0 }
            }
    }

    suspend fun exportBookAudio(audioId: Long, uri: Uri): BookAudioEntity =
        withContext(Dispatchers.IO) {
            val audio = requireNotNull(dao.bookAudioById(audioId)) {
                "Generated audiobook audio is no longer available."
            }
            repairBookAudioFilesystemState(audio)
            val current = requireNotNull(dao.bookAudioById(audioId)) {
                "Generated audiobook audio is no longer available."
            }
            val segments = current.playableSegmentFiles()
            require(segments.isNotEmpty()) {
                "Generated audiobook files are missing. Regenerate this audio."
            }
            exportAudio(current, segments, uri)
            current
        }

    suspend fun deleteBookAudio(audioId: Long): BookAudioEntity =
        withContext(Dispatchers.IO) {
            val audio = requireNotNull(dao.bookAudioById(audioId)) {
                "Generated audiobook audio is no longer available."
            }
            require(audio.canDeleteGeneratedAudiobook()) {
                "Stop audiobook generation before deleting this audio."
            }
            dao.deleteBookAudio(audioId)
            deleteGeneratedAudiobookFiles(audio)
            audio
        }

    suspend fun deleteBookAudioForBook(bookId: Long): List<BookAudioEntity> =
        withContext(Dispatchers.IO) {
            val audio = dao.bookAudioForBook(bookId)
            require(audio.none { !it.canDeleteGeneratedAudiobook() }) {
                "Stop audiobook generation before deleting generated audio."
            }
            dao.deleteBookAudioForBook(bookId)
            audio.forEach { deleteGeneratedAudiobookFiles(it) }
            audio
        }

    suspend fun updateBookAudioPlayback(
        audioId: Long,
        segmentIndex: Int,
        positionMs: Int,
        playableSegmentCount: Int? = null,
    ) {
        withContext(Dispatchers.IO) {
            val audio = dao.bookAudioById(audioId) ?: return@withContext
            if (playableSegmentCount == null) {
                repairBookAudioFilesystemState(audio)
            }
            val current = if (playableSegmentCount == null) {
                dao.bookAudioById(audioId) ?: return@withContext
            } else {
                audio
            }
            val boundedSegmentCount = playableSegmentCount
                ?.coerceIn(0, current.segmentCount.coerceAtLeast(0))
                ?: current.verifiedPlayableSegmentCount()
            val position = generatedAudiobookPersistedPlaybackPosition(
                requestedSegmentIndex = segmentIndex,
                positionMs = positionMs,
                segmentCount = boundedSegmentCount
            )
            if (!shouldPersistGeneratedAudiobookPlaybackPosition(current, position)) {
                return@withContext
            }
            dao.updateBookAudioPlayback(
                id = audioId,
                segmentIndex = position.segmentIndex,
                positionMs = position.positionMs
            )
        }
    }

    private suspend fun repairCompletedGeneratingAudio(
        audio: BookAudioEntity,
        reconcileIncomplete: Boolean,
    ): BookAudioEntity {
        if (audio.status != BookAudioStatus.GENERATING) return audio
        if (audio.segmentCount <= 0) return audio
        val root = audio.filePath?.let(::File)
        if (root == null || !root.isDirectory) {
            if (!reconcileIncomplete) return audio
            val failed = audio.copy(
                status = BookAudioStatus.FAILED,
                completedSegments = 0,
                fileSizeBytes = 0L,
                updatedAt = clock.millis(),
                error = "Generated audio files are missing. Start generation again."
            )
            audio.filePath?.takeIf { it.isNotBlank() }?.let { path ->
                rewriteAudiobookRecoveryManifest(
                    target = File(path),
                    status = failed.status,
                    completedSegments = failed.completedSegments,
                    updatedAt = failed.updatedAt,
                    error = failed.error
                )
            }
            dao.upsertBookAudio(failed)
            return failed
        }
        val reusableSegments = reusableGeneratedAudiobookSegments(root, audio.segmentCount)
        if (reusableSegments < audio.segmentCount) {
            var current = audio
            val recoveredFileSize = root.generatedAudiobookKnownFilesSizeBytes(reusableSegments)
            if (
                shouldWriteRecoveredGeneratingProgress(
                    reusableSegments = reusableSegments,
                    completedSegments = audio.completedSegments,
                    reconcileIncomplete = reconcileIncomplete
                )
            ) {
                current = audio.copy(
                    completedSegments = reusableSegments,
                    fileSizeBytes = recoveredFileSize,
                    updatedAt = clock.millis()
                )
                dao.updateBookAudioProgress(
                    id = audio.id,
                    completedSegments = current.completedSegments,
                    fileSizeBytes = current.fileSizeBytes,
                    updatedAt = current.updatedAt
                )
            }
            if (reconcileIncomplete) {
                val canceled = current.copy(
                    status = BookAudioStatus.CANCELED,
                    completedSegments = reusableSegments.coerceIn(0, audio.segmentCount.coerceAtLeast(0)),
                    fileSizeBytes = recoveredFileSize,
                    updatedAt = clock.millis(),
                    error = null
                )
                rewriteAudiobookRecoveryManifest(
                    target = root,
                    status = canceled.status,
                    completedSegments = canceled.completedSegments,
                    updatedAt = canceled.updatedAt,
                    error = canceled.error
                )
                dao.upsertBookAudio(canceled)
                return canceled
            }
            return current
        }
        val now = clock.millis()
        val generated = audio.copy(
            status = BookAudioStatus.GENERATED,
            completedSegments = audio.segmentCount,
            fileSizeBytes = root.generatedAudiobookKnownFilesSizeBytes(audio.segmentCount),
            generatedAt = audio.generatedAt ?: now,
            updatedAt = now,
            error = null
        )
        dao.upsertBookAudio(generated)
        return generated
    }

    private suspend fun repairBookAudioFilesystemState(audio: BookAudioEntity): BookAudioEntity {
        val repairedGenerating = repairCompletedGeneratingAudio(
            audio = audio,
            reconcileIncomplete = audio.updatedAt < clock.millis() - STALE_GENERATING_AUDIO_REPAIR_AGE_MS
        )
        if (repairedGenerating.status == BookAudioStatus.GENERATING) return repairedGenerating
        val root = repairedGenerating.filePath?.let(::File)?.takeIf { it.isDirectory }
        val expectedPlayable = repairedGenerating.segmentCount.coerceAtLeast(0)
        if (expectedPlayable <= 0) return repairedGenerating
        val verifiedPlayable = root?.let { reusableGeneratedAudiobookSegments(it, expectedPlayable) } ?: 0
        if (verifiedPlayable == repairedGenerating.playableSegmentCount()) {
            return repairedGenerating
        }
        val now = clock.millis()
        val repaired = repairedGenerating.copy(
            status = if (verifiedPlayable > 0) BookAudioStatus.CANCELED else BookAudioStatus.FAILED,
            completedSegments = verifiedPlayable,
            fileSizeBytes = repairedGenerating.generatedAudiobookKnownFilesSizeBytes(verifiedPlayable),
            generatedAt = if (verifiedPlayable > 0) repairedGenerating.generatedAt else null,
            updatedAt = now,
            error = if (verifiedPlayable > 0) null else "Generated audio files are missing. Start generation again."
        )
        dao.upsertBookAudio(repaired)
        return repaired
    }

    private fun BookAudioEntity.hasVerifiedCompleteGeneratedAudiobook(): Boolean =
        status == BookAudioStatus.GENERATED &&
            segmentCount > 0 &&
            completedSegments == segmentCount

    suspend fun ensureCatalogSeeded() {
        if (catalogSeededForProcess) return
        withContext(Dispatchers.IO) {
            catalogSeedMutex.withLock {
                if (catalogSeededForProcess) return@withLock
                NeuralTtsModelCatalog.models.forEach { spec ->
                    val existing = dao.model(spec.modelId)
                    if (existing == null) {
                        dao.upsertModel(
                            NeuralTtsModelEntity(
                                modelId = spec.modelId,
                                displayName = spec.displayName,
                                engine = spec.engine,
                                status = NeuralTtsModelStatus.NOT_DOWNLOADED,
                                totalBytes = spec.archiveBytes,
                                updatedAt = clock.millis()
                            )
                        )
                    } else if (
                        existing.displayName != spec.displayName ||
                        existing.engine != spec.engine ||
                        existing.totalBytes != spec.archiveBytes
                    ) {
                        dao.upsertModel(
                            existing.copy(
                                displayName = spec.displayName,
                                engine = spec.engine,
                                totalBytes = spec.archiveBytes,
                                updatedAt = clock.millis()
                            )
                        )
                    }
                }
                catalogSeededForProcess = true
            }
        }
    }

    suspend fun pruneObsoleteCatalogStorage() {
        withContext(Dispatchers.IO) {
            val supportedModelIds = NeuralTtsModelCatalog.models.mapTo(mutableSetOf()) { it.modelId }
            dao.allModels()
                .filterNot { it.modelId in supportedModelIds }
                .forEach { obsolete ->
                    deleteModelFiles(obsolete)
                    dao.bookAudioForModel(obsolete.modelId).forEach { audio ->
                        deleteGeneratedAudiobookFiles(audio)
                    }
                    dao.deleteBookAudioForModel(obsolete.modelId)
                    dao.deleteModel(obsolete.modelId)
                }
            dao.allBookAudio()
                .filterNot { it.modelId in supportedModelIds }
                .groupBy { it.modelId }
                .forEach { (modelId, rows) ->
                    rows.forEach { audio -> deleteGeneratedAudiobookFiles(audio) }
                    dao.deleteBookAudioForModel(modelId)
                }
        }
    }

    suspend fun repairInterruptedModelInstalls() {
        withContext(Dispatchers.IO) {
            NeuralTtsModelCatalog.models.forEach { spec ->
                val model = dao.model(spec.modelId) ?: return@forEach
                if (model.status != NeuralTtsModelStatus.DOWNLOADING && model.status != NeuralTtsModelStatus.EXTRACTING) {
                    return@forEach
                }
                dao.upsertModel(
                    model.copy(
                        displayName = spec.displayName,
                        engine = spec.engine,
                        status = NeuralTtsModelStatus.FAILED,
                        localPath = null,
                        totalBytes = spec.archiveBytes,
                        updatedAt = clock.millis(),
                        error = "Voice install was interrupted. Retry download."
                    )
                )
            }
        }
    }

    suspend fun repairStaleGeneratingAudio(
        staleBeforeMillis: Long = clock.millis() - STALE_GENERATING_AUDIO_REPAIR_AGE_MS,
    ) {
        withContext(Dispatchers.IO) {
            dao.generatingBookAudio().forEach { audio ->
                repairCompletedGeneratingAudio(
                    audio = audio,
                    reconcileIncomplete = audio.updatedAt < staleBeforeMillis
                )
            }
        }
    }

    suspend fun downloadModel(modelId: String = NeuralTtsModelCatalog.DEFAULT_MODEL_ID): NeuralTtsModelEntity =
        withContext(Dispatchers.IO) {
            val spec = NeuralTtsModelCatalog.requireModel(modelId)
            val job = requireNotNull(currentCoroutineContext()[Job]) { "Download job unavailable." }
            while (true) {
                val existingJob = activeModelDownloads.putIfAbsent(modelId, job)
                when {
                    existingJob == null -> break
                    existingJob.isCompleted -> activeModelDownloads.remove(modelId, existingJob)
                    else -> error("${spec.displayName} is already downloading.")
                }
            }
            modelRoot.mkdirs()
            val archive = File(modelRoot, "${spec.modelId}.tar.bz2")
            val extractDir = File(modelRoot, spec.modelId)
            updateModel(spec, NeuralTtsModelStatus.DOWNLOADING, downloadedBytes = 0, localPath = null, error = null)

            try {
                runCatching {
                    downloadArchive(spec, archive)
                    updateModel(
                        spec = spec,
                        status = NeuralTtsModelStatus.EXTRACTING,
                        downloadedBytes = spec.archiveBytes,
                        error = null
                    )
                    currentCoroutineContext().ensureActive()
                    val sha256 = sha256(archive)
                    require(sha256 == spec.sha256) { "Downloaded model checksum did not match." }
                    if (extractDir.exists()) extractDir.deleteRecursively()
                    extractDir.mkdirs()
                    extractTarBz2(archive, extractDir)
                    currentCoroutineContext().ensureActive()
                    val modelDir = File(extractDir, spec.rootDirectory)
                    require(File(modelDir, spec.modelFile).isFile) { "Model file missing after extraction." }
                    require(File(modelDir, spec.tokensFile).isFile) { "Token file missing after extraction." }
                    require(File(modelDir, spec.dataDirectory).isDirectory) { "Phoneme data missing after extraction." }
                    if (spec.voicesFile.isNotBlank()) {
                        require(File(modelDir, spec.voicesFile).isFile) { "Voice embedding file missing after extraction." }
                    }
                    spec.lexiconFile.split(',')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .forEach { lexicon ->
                            require(File(modelDir, lexicon).isFile) { "Lexicon file $lexicon missing after extraction." }
                        }
                    archive.delete()
                    updateModel(
                        spec = spec,
                        status = NeuralTtsModelStatus.INSTALLED,
                        downloadedBytes = spec.archiveBytes,
                        localPath = modelDir.absolutePath,
                        checksum = sha256,
                        installedAt = clock.millis(),
                        error = null
                    )
                }.getOrElse { error ->
                    if (error is CancellationException) {
                        updateModel(spec, NeuralTtsModelStatus.FAILED, downloadedBytes = archive.length(), error = "Voice download canceled. Retry download.")
                        throw error
                    }
                    updateModel(spec, NeuralTtsModelStatus.FAILED, downloadedBytes = archive.length(), error = error.message)
                }
            } finally {
                activeModelDownloads.remove(modelId, job)
            }
        }

    suspend fun cancelModelInstall(modelId: String): NeuralTtsModelEntity =
        withContext(Dispatchers.IO) {
            val spec = NeuralTtsModelCatalog.requireModel(modelId)
            activeModelDownloads.remove(modelId)?.cancel(CancellationException("Voice download canceled."))
            val archive = File(modelRoot, "${spec.modelId}.tar.bz2")
            val existing = dao.model(modelId)
            archive.delete()
            val updated = (existing ?: NeuralTtsModelEntity(
                modelId = spec.modelId,
                displayName = spec.displayName,
                engine = spec.engine,
                status = NeuralTtsModelStatus.FAILED,
                totalBytes = spec.archiveBytes,
                updatedAt = clock.millis()
            )).copy(
                displayName = spec.displayName,
                engine = spec.engine,
                status = NeuralTtsModelStatus.FAILED,
                localPath = null,
                downloadedBytes = 0,
                totalBytes = spec.archiveBytes,
                updatedAt = clock.millis(),
                error = "Voice download canceled. Retry download."
            )
            dao.upsertModel(updated)
            updated
        }

    suspend fun deleteModel(modelId: String): NeuralTtsModelEntity =
        withContext(Dispatchers.IO) {
            val spec = NeuralTtsModelCatalog.requireModel(modelId)
            val existing = requireNotNull(dao.model(modelId)) {
                "This neural voice is not installed."
            }
            deleteModelFiles(existing)
            val updated = existing.copy(
                displayName = spec.displayName,
                engine = spec.engine,
                status = NeuralTtsModelStatus.NOT_DOWNLOADED,
                localPath = null,
                downloadedBytes = 0,
                totalBytes = spec.archiveBytes,
                checksumSha256 = null,
                installedAt = null,
                updatedAt = clock.millis(),
                error = null
            )
            dao.upsertModel(updated)
            updated
        }

    suspend fun markBookAudioPreparing(
        bookId: Long,
        modelId: String = NeuralTtsModelCatalog.DEFAULT_MODEL_ID,
        speakerId: Int = 0,
        pace: NeuralTtsPace = NeuralTtsPace.STANDARD,
        tone: NeuralTtsTone = NeuralTtsTone.NATURAL,
        scope: AudiobookGenerationScope = AudiobookGenerationScope.FULL_BOOK,
    ): BookAudioEntity = withContext(Dispatchers.IO) {
        val spec = NeuralTtsModelCatalog.requireModel(modelId)
        val model = dao.model(modelId)
        require(model?.status == NeuralTtsModelStatus.INSTALLED && !model.localPath.isNullOrBlank()) {
            "Download the neural voice before generating audiobook audio."
        }
        val speed = pace.speed
        val target = audioDirectory(bookId, modelId, speakerId, pace, tone, scope)
        val existing = dao.bookAudio(bookId, modelId, speakerId, speed, tone.name, scope.key)
        if (existing?.hasCompletePlayableAudiobook() == true) {
            return@withContext existing
        }
        if (existing?.isFreshActiveAudiobookGeneration(clock.millis()) == true) {
            return@withContext existing
        }
        val now = clock.millis()
        val preparing = (existing ?: BookAudioEntity(
            bookId = bookId,
            modelId = modelId,
            modelDisplayName = spec.displayName,
            speakerId = speakerId,
            speed = speed,
            tone = tone.name,
            scope = scope.key,
            scopeLabel = scope.label,
            status = BookAudioStatus.GENERATING,
            generationStartedAt = now,
            updatedAt = now
        )).copy(
            status = BookAudioStatus.GENERATING,
            modelDisplayName = spec.displayName,
            scope = scope.key,
            scopeLabel = scope.label,
            filePath = target.absolutePath,
            segmentCount = 0,
            completedSegments = 0,
            wordCount = 0,
            generationStartedAt = now,
            generationSessionStartCompletedSegments = 0,
            updatedAt = now,
            error = null
        ).withAudiobookGenerationStartState(
            canResumeExistingAudio = false,
            target = target,
            reusableSegments = 0
        )
        dao.upsertBookAudio(preparing)
        dao.bookAudio(bookId, modelId, speakerId, speed, tone.name, scope.key) ?: preparing
    }

    suspend fun cancelGeneratingBookAudio(
        bookId: Long,
        modelId: String = NeuralTtsModelCatalog.DEFAULT_MODEL_ID,
        speakerId: Int = 0,
        pace: NeuralTtsPace = NeuralTtsPace.STANDARD,
        tone: NeuralTtsTone = NeuralTtsTone.NATURAL,
        scope: AudiobookGenerationScope = AudiobookGenerationScope.FULL_BOOK,
    ): Int = withContext(Dispatchers.IO) {
        val speed = pace.speed
        dao.cancelGeneratingBookAudio(
            bookId = bookId,
            modelId = modelId,
            speakerId = speakerId,
            speed = speed,
            tone = tone.name,
            scope = scope.key,
            updatedAt = clock.millis()
        )
    }

    suspend fun failGeneratingBookAudio(
        bookId: Long,
        modelId: String = NeuralTtsModelCatalog.DEFAULT_MODEL_ID,
        speakerId: Int = 0,
        pace: NeuralTtsPace = NeuralTtsPace.STANDARD,
        tone: NeuralTtsTone = NeuralTtsTone.NATURAL,
        scope: AudiobookGenerationScope = AudiobookGenerationScope.FULL_BOOK,
        error: String,
    ): Int = withContext(Dispatchers.IO) {
        dao.failGeneratingBookAudio(
            bookId = bookId,
            modelId = modelId,
            speakerId = speakerId,
            speed = pace.speed,
            tone = tone.name,
            scope = scope.key,
            error = error.take(MAX_GENERATING_AUDIO_ERROR_LENGTH),
            updatedAt = clock.millis()
        )
    }

    suspend fun generateBookAudio(
        bookId: Long,
        bookTitle: String,
        chunks: List<ReadAloudChunk>,
        modelId: String = NeuralTtsModelCatalog.DEFAULT_MODEL_ID,
        speakerId: Int = 0,
        pace: NeuralTtsPace = NeuralTtsPace.STANDARD,
        tone: NeuralTtsTone = NeuralTtsTone.NATURAL,
        scope: AudiobookGenerationScope = AudiobookGenerationScope.FULL_BOOK,
    ): BookAudioEntity = withContext(Dispatchers.IO) {
        val prepared = withContext(generationDispatcher) {
            traced("XReader TTS prepare text") {
                NeuralTtsText.prepare(chunks.forAudiobookScope(scope)).forScope(scope)
            }
        }
        generatePreparedBookAudio(
            bookId = bookId,
            bookTitle = bookTitle,
            prepared = prepared,
            modelId = modelId,
            speakerId = speakerId,
            pace = pace,
            tone = tone,
            scope = scope
        )
    }

    internal suspend fun generatePreparedBookAudio(
        bookId: Long,
        bookTitle: String,
        prepared: NeuralTtsPreparedBook,
        modelId: String = NeuralTtsModelCatalog.DEFAULT_MODEL_ID,
        speakerId: Int = 0,
        pace: NeuralTtsPace = NeuralTtsPace.STANDARD,
        tone: NeuralTtsTone = NeuralTtsTone.NATURAL,
        scope: AudiobookGenerationScope = AudiobookGenerationScope.FULL_BOOK,
    ): BookAudioEntity = withContext(Dispatchers.IO) {
        val spec = NeuralTtsModelCatalog.requireModel(modelId)
        val model = dao.model(modelId)
        require(model?.status == NeuralTtsModelStatus.INSTALLED && !model.localPath.isNullOrBlank()) {
            "Download the neural voice before generating audiobook audio."
        }
        requireAudiobookGenerationHardwareReady(modelId)
        require(prepared.segments.isNotEmpty()) { "This book has no extractable text for audiobook generation." }

        val speed = pace.speed
        val ttsGenerationConfig = generationConfig(speakerId, pace, tone)
        val target = audioDirectory(bookId, modelId, speakerId, pace, tone, scope)
        val existing = dao.bookAudio(bookId, modelId, speakerId, speed, tone.name, scope.key)
        if (existing?.hasCompletePlayableAudiobook() == true) {
            return@withContext existing
        }
        if (existing?.isFreshActiveAudiobookGeneration(clock.millis()) == true) {
            return@withContext existing
        }
        val canResumeExistingAudio = existing.canResumeGeneration(
            target = target,
            segmentCount = prepared.segments.size,
            wordCount = prepared.wordCount
        )
        val reusableSegments = if (canResumeExistingAudio) {
            reusableGeneratedAudiobookSegments(target, prepared.segments.size)
        } else {
            0
        }
        val now = clock.millis()
        val reusableGenerationSaveMillis = if (canResumeExistingAudio) {
            target.generatedAudiobookManifestLong("generationSaveMillis")
        } else {
            0L
        }
        val generating = (existing ?: BookAudioEntity(
                bookId = bookId,
                modelId = modelId,
                modelDisplayName = spec.displayName,
                speakerId = speakerId,
                speed = speed,
                tone = tone.name,
                scope = scope.key,
                scopeLabel = scope.label,
                status = BookAudioStatus.GENERATING,
                generationStartedAt = now,
                updatedAt = now
            )).copy(
            status = BookAudioStatus.GENERATING,
            modelDisplayName = spec.displayName,
            scope = scope.key,
            scopeLabel = scope.label,
            filePath = target.absolutePath,
            segmentCount = prepared.segments.size,
            completedSegments = reusableSegments,
            wordCount = prepared.wordCount,
            generationProvider = existing?.generationProvider.takeIf { canResumeExistingAudio },
            generationAudioMillis = existing?.generationAudioMillis?.takeIf { canResumeExistingAudio } ?: 0L,
            generationComputeMillis = existing?.generationComputeMillis?.takeIf { canResumeExistingAudio } ?: 0L,
            generationStartedAt = now,
            generationSessionStartCompletedSegments = reusableSegments,
            updatedAt = now,
            error = null
        ).withAudiobookGenerationStartState(
            canResumeExistingAudio = canResumeExistingAudio,
            target = target,
            reusableSegments = reusableSegments
        )
        dao.upsertBookAudio(generating)
        val activeAudio = requireNotNull(dao.bookAudio(bookId, modelId, speakerId, speed, tone.name, scope.key)) {
            "Could not create audiobook generation record."
        }
        prepareAudiobookGenerationTarget(target = target, canResumeExistingAudio = canResumeExistingAudio)
        writeAudiobookManifest(
            target = target,
            title = bookTitle,
            spec = spec,
            provider = null,
            pace = pace,
            tone = tone,
            scope = scope,
            segmentCount = prepared.segments.size,
            completedSegments = reusableSegments,
            wordCount = prepared.wordCount,
            sampleRate = null,
            generationAudioMillis = generating.generationAudioMillis,
            generationComputeMillis = generating.generationComputeMillis,
            generationSaveMillis = reusableGenerationSaveMillis,
            status = BookAudioStatus.GENERATING,
            error = null
        )
        writeAudiobookStructure(
            target = target,
            prepared = prepared
        )

        var completedSegments = reusableSegments
        var activeProvider: String? = null
        var activeSampleRate: Int? = null
        var activeHostThreadCount: Int? = null
        var generationAudioMillis = generating.generationAudioMillis.coerceAtLeast(0L)
        var generationComputeMillis = generating.generationComputeMillis.coerceAtLeast(0L)
        var generationSaveMillis = reusableGenerationSaveMillis
        var runtimeInitializationCount = 0
        var runtimeInitializationMillis = 0L
        var lastProgressWrittenSegments = reusableSegments
        var lastProgressWrittenAtMillis = now
        var sessionAudioMillis = 0L
        var sessionComputeMillis = 0L
        var sessionGeneratedSegments = 0
        var generatedSegmentFileSizeBytes = if (canResumeExistingAudio) {
            target.generatedAudiobookSegmentFilesSizeBytes(reusableSegments)
        } else {
            0L
        }
        val heartbeatSnapshot = AtomicReference(
            GenerationHeartbeatSnapshot(
                completedSegments = completedSegments,
                provider = activeProvider,
                audioMillis = generationAudioMillis,
                computeMillis = generationComputeMillis,
                sampleRate = activeSampleRate ?: spec.sampleRate,
                fileSizeBytes = generatedSegmentFileSizeBytes
            )
        )
        val persistedGenerationSnapshot = AtomicReference<GenerationHeartbeatSnapshot?>(null)
        runCatching {
            val modelDir = requireNotNull(model.localPath)
            var runtime: TtsRuntime? = null
            val activeGenerationJob = requireNotNull(currentCoroutineContext()[Job]) {
                "Audiobook generation job unavailable."
            }
            val heartbeatJob = launch(Dispatchers.IO) {
                var lastHeartbeatWrittenAtMillis = clock.millis()
                var lastHeartbeatWrittenSnapshot: GenerationHeartbeatSnapshot? = null
                while (isActive) {
                    delay(GENERATION_CANCELLATION_POLL_INTERVAL_MS)
                    if (dao.generatingBookAudioCount(activeAudio.id) != 1) {
                        activeGenerationJob.cancel(
                            CancellationException("Audiobook generation was stopped.")
                        )
                        return@launch
                    }
                    val heartbeatAt = clock.millis()
                    val snapshot = heartbeatSnapshot.get()
                    if (
                        shouldWriteGenerationHeartbeat(
                            lastHeartbeatWrittenAtMillis = lastHeartbeatWrittenAtMillis,
                            nowMillis = heartbeatAt,
                            snapshotChanged = snapshot != lastHeartbeatWrittenSnapshot,
                            snapshotAlreadyPersisted = snapshot == persistedGenerationSnapshot.get()
                        )
                    ) {
                        val updatedRows = dao.updateBookAudioGenerationMetrics(
                            id = activeAudio.id,
                            completedSegments = snapshot.completedSegments,
                            generationProvider = snapshot.provider,
                            generationAudioMillis = snapshot.audioMillis,
                            generationComputeMillis = snapshot.computeMillis,
                            sampleRate = snapshot.sampleRate,
                            fileSizeBytes = snapshot.fileSizeBytes,
                            updatedAt = heartbeatAt
                        )
                        if (updatedRows != 1) {
                            activeGenerationJob.cancel(
                                CancellationException("Audiobook generation was stopped.")
                            )
                            return@launch
                        }
                        lastHeartbeatWrittenAtMillis = heartbeatAt
                        lastHeartbeatWrittenSnapshot = snapshot
                        persistedGenerationSnapshot.set(snapshot)
                    }
                }
            }
            try {
                var sampleRate = spec.sampleRate
                prepared.segments.forEachIndexed { index, segment ->
                    currentCoroutineContext().ensureActive()
                    if (index < reusableSegments) return@forEachIndexed
                    if (runtime == null) {
                        runtime?.releaseOnGenerationDispatcher()
                        runtime = withContext(generationDispatcher) {
                            traced("XReader TTS init runtime") {
                                createOfflineTts(
                                    spec = spec,
                                    modelDir = modelDir,
                                    tone = tone,
                                    workload = NeuralTtsRuntimeWorkload.AUDIOBOOK_GENERATION
                                )
                            }
                        }
                        val activeRuntime = requireNotNull(runtime)
                        activeProvider = activeRuntime.provider
                        activeHostThreadCount = activeRuntime.hostThreadCount
                        runtimeInitializationCount += 1
                        runtimeInitializationMillis += activeRuntime.initializationMillis
                        heartbeatSnapshot.set(
                            GenerationHeartbeatSnapshot(
                                completedSegments = completedSegments,
                                provider = activeProvider,
                                audioMillis = generationAudioMillis,
                                computeMillis = generationComputeMillis,
                                sampleRate = activeSampleRate ?: sampleRate,
                                fileSizeBytes = generatedSegmentFileSizeBytes
                            )
                        )
                        writeAudiobookManifest(
                            target = target,
                            title = bookTitle,
                            spec = spec,
                            provider = activeProvider,
                            hostThreadCount = activeHostThreadCount,
                            pace = pace,
                            tone = tone,
                            scope = scope,
                            segmentCount = prepared.segments.size,
                            completedSegments = completedSegments,
                            wordCount = prepared.wordCount,
                            sampleRate = activeSampleRate,
                            generationAudioMillis = generationAudioMillis,
                            generationComputeMillis = generationComputeMillis,
                            runtimeInitializationCount = runtimeInitializationCount,
                            runtimeInitializationMillis = runtimeInitializationMillis,
                            status = BookAudioStatus.GENERATING,
                            error = null
                        )
                    }
                    val tts = requireNotNull(runtime)
                    val generatedSegment = withContext(generationDispatcher) {
                        traced("XReader TTS generate segment") {
                            val segmentStartedAt = clock.millis()
                            GeneratedTtsSegment(
                                audio = tts.engine.generateWithConfig(
                                    text = segment,
                                    config = ttsGenerationConfig
                                ),
                                computeMillis = (clock.millis() - segmentStartedAt).coerceAtLeast(0L)
                            )
                        }
                    }
                    val generated = generatedSegment.audio
                    val segmentComputeMillis = generatedSegment.computeMillis
                    currentCoroutineContext().ensureActive()
                    require(generated.samples.isNotEmpty()) { "Neural TTS produced no audio for segment ${index + 1}." }
                    ensureAudiobookGenerationStillActive(
                        audioId = activeAudio.id,
                        segmentNumber = index + 1
                    )
                    sampleRate = generated.sampleRate
                    activeSampleRate = sampleRate
                    val file = File(target, generatedAudiobookSegmentFileName(index))
                    val saveStartedAt = clock.millis()
                    val savedBytes = withContext(audioSaveDispatcher) {
                        traced("XReader TTS save segment") {
                            generated.saveGeneratedAudiobookSegment(file)
                        }
                    }
                    val segmentSaveMillis = (clock.millis() - saveStartedAt).coerceAtLeast(0L)
                    require(savedBytes > 0L) { "Could not save generated segment ${index + 1}." }
                    val segmentAudioMillis = generated.audioDurationMillis()
                    generationAudioMillis = generationAudioMillis.plusNonNegativeDuration(segmentAudioMillis)
                    generationComputeMillis = generationComputeMillis.plusNonNegativeDuration(segmentComputeMillis)
                    generationSaveMillis = generationSaveMillis.plusNonNegativeDuration(segmentSaveMillis)
                    sessionAudioMillis = sessionAudioMillis.plusNonNegativeDuration(segmentAudioMillis)
                    sessionComputeMillis = sessionComputeMillis.plusNonNegativeDuration(segmentComputeMillis)
                    sessionGeneratedSegments += 1
                    require(
                        !isSustainedUnusableAudiobookHardwareGenerationSpeed(
                            audioMillis = sessionAudioMillis,
                            computeMillis = sessionComputeMillis,
                            generatedSegments = sessionGeneratedSegments
                        )
                    ) {
                        val audioTimeFactor = generationAudioTimeFactor(sessionAudioMillis, sessionComputeMillis)
                            ?.let { "%.2f".format(Locale.US, it) }
                            ?: "unknown"
                        "Hardware audiobook generation is too slow for full-book use: " +
                            "audioTimeFactor=$audioTimeFactor after $sessionGeneratedSegments segments. " +
                            "Use a faster strict QNN HTP/NPU build before generating this book."
                    }
                    completedSegments = index + 1
                    generatedSegmentFileSizeBytes += savedBytes
                    heartbeatSnapshot.set(
                        GenerationHeartbeatSnapshot(
                            completedSegments = completedSegments,
                            provider = tts.provider,
                            audioMillis = generationAudioMillis,
                            computeMillis = generationComputeMillis,
                            sampleRate = sampleRate,
                            fileSizeBytes = generatedSegmentFileSizeBytes
                        )
                    )
                    val progressWriteAt = clock.millis()
                    if (
                        shouldWriteGenerationProgress(
                            completedSegments = completedSegments,
                            totalSegments = prepared.segments.size,
                            lastProgressWrittenSegments = lastProgressWrittenSegments,
                            lastProgressWrittenAtMillis = lastProgressWrittenAtMillis,
                            nowMillis = progressWriteAt
                        )
                    ) {
                        val updatedRows = dao.updateBookAudioGenerationMetrics(
                            id = activeAudio.id,
                            completedSegments = completedSegments,
                            generationProvider = tts.provider,
                            generationAudioMillis = generationAudioMillis,
                            generationComputeMillis = generationComputeMillis,
                            sampleRate = sampleRate,
                            fileSizeBytes = generatedSegmentFileSizeBytes,
                            updatedAt = progressWriteAt
                        )
                        if (updatedRows != 1) {
                            throw CancellationException(
                                "Audiobook generation was stopped before segment ${completedSegments.coerceAtLeast(1)} could be recorded."
                            )
                        }
                        persistedGenerationSnapshot.set(heartbeatSnapshot.get())
                        lastProgressWrittenSegments = completedSegments
                        lastProgressWrittenAtMillis = progressWriteAt
                    }
                    if (shouldWriteGenerationCheckpoint(completedSegments, prepared.segments.size)) {
                        writeAudiobookManifest(
                            target = target,
                            title = bookTitle,
                            spec = spec,
                            provider = tts.provider,
                            hostThreadCount = tts.hostThreadCount,
                            pace = pace,
                            tone = tone,
                            scope = scope,
                            segmentCount = prepared.segments.size,
                            completedSegments = completedSegments,
                            wordCount = prepared.wordCount,
                            sampleRate = sampleRate,
                            generationAudioMillis = generationAudioMillis,
                            generationComputeMillis = generationComputeMillis,
                            generationSaveMillis = generationSaveMillis,
                            runtimeInitializationCount = runtimeInitializationCount,
                            runtimeInitializationMillis = runtimeInitializationMillis,
                            status = BookAudioStatus.GENERATING,
                            error = null
                        )
                    }
                }
                writeAudiobookManifest(
                    target = target,
                    title = bookTitle,
                    spec = spec,
                    provider = activeProvider,
                    hostThreadCount = activeHostThreadCount,
                    pace = pace,
                    tone = tone,
                    scope = scope,
                    segmentCount = prepared.segments.size,
                    completedSegments = prepared.segments.size,
                    wordCount = prepared.wordCount,
                    sampleRate = sampleRate,
                    generationAudioMillis = generationAudioMillis,
                    generationComputeMillis = generationComputeMillis,
                    generationSaveMillis = generationSaveMillis,
                    runtimeInitializationCount = runtimeInitializationCount,
                    runtimeInitializationMillis = runtimeInitializationMillis,
                    status = BookAudioStatus.GENERATED,
                    error = null
                )
                finalizedGeneratedAudiobookEntity(
                    audio = activeAudio,
                    target = target,
                    status = BookAudioStatus.GENERATED,
                    scope = scope,
                    segmentCount = prepared.segments.size,
                    completedSegments = prepared.segments.size,
                    wordCount = prepared.wordCount,
                    sampleRate = sampleRate,
                    generationProvider = activeProvider,
                    generationAudioMillis = generationAudioMillis,
                    generationComputeMillis = generationComputeMillis,
                    sessionStartCompletedSegments = reusableSegments,
                    fileSizeBytes = target.generatedAudiobookKnownFileSizeBytes(generatedSegmentFileSizeBytes),
                    error = null
                )
            } finally {
                withContext(NonCancellable) {
                    heartbeatJob.cancelAndJoin()
                }
                runtime?.releaseOnGenerationDispatcher()
            }
        }.fold(
            onSuccess = { result ->
                dao.upsertBookAudio(result)
                result
            },
            onFailure = { error ->
                if (error is CancellationException) {
                    withContext(NonCancellable) {
                        writeAudiobookManifest(
                            target = target,
                            title = bookTitle,
                            spec = spec,
                            provider = activeProvider,
                            hostThreadCount = activeHostThreadCount,
                            pace = pace,
                            tone = tone,
                            scope = scope,
                            segmentCount = prepared.segments.size,
                            completedSegments = completedSegments,
                            wordCount = prepared.wordCount,
                            sampleRate = activeSampleRate,
                            generationAudioMillis = generationAudioMillis,
                            generationComputeMillis = generationComputeMillis,
                            generationSaveMillis = generationSaveMillis,
                            runtimeInitializationCount = runtimeInitializationCount,
                            runtimeInitializationMillis = runtimeInitializationMillis,
                            status = BookAudioStatus.CANCELED,
                            error = null
                        )
                        val canceled = finalizedGeneratedAudiobookEntity(
                            audio = activeAudio,
                            target = target,
                            status = BookAudioStatus.CANCELED,
                            scope = scope,
                            segmentCount = prepared.segments.size,
                            completedSegments = completedSegments,
                            wordCount = prepared.wordCount,
                            sampleRate = activeSampleRate ?: 0,
                            generationProvider = activeProvider,
                            generationAudioMillis = generationAudioMillis,
                            generationComputeMillis = generationComputeMillis,
                            sessionStartCompletedSegments = reusableSegments,
                            fileSizeBytes = target.generatedAudiobookKnownFileSizeBytes(generatedSegmentFileSizeBytes),
                            error = null
                        )
                        dao.upsertBookAudio(canceled)
                    }
                    throw error
                }
                val failureMessage = error.message ?: "Neural audiobook generation failed for $bookTitle"
                writeAudiobookManifest(
                    target = target,
                    title = bookTitle,
                    spec = spec,
                    provider = activeProvider,
                    hostThreadCount = activeHostThreadCount,
                    pace = pace,
                    tone = tone,
                    scope = scope,
                    segmentCount = prepared.segments.size,
                    completedSegments = completedSegments,
                    wordCount = prepared.wordCount,
                    sampleRate = activeSampleRate,
                    generationAudioMillis = generationAudioMillis,
                    generationComputeMillis = generationComputeMillis,
                    generationSaveMillis = generationSaveMillis,
                    runtimeInitializationCount = runtimeInitializationCount,
                    runtimeInitializationMillis = runtimeInitializationMillis,
                    status = BookAudioStatus.FAILED,
                    error = failureMessage
                )
                val failed = finalizedGeneratedAudiobookEntity(
                    audio = activeAudio,
                    target = target,
                    status = BookAudioStatus.FAILED,
                    scope = scope,
                    segmentCount = prepared.segments.size,
                    completedSegments = completedSegments,
                    wordCount = prepared.wordCount,
                    sampleRate = activeSampleRate ?: 0,
                    generationProvider = activeProvider,
                    generationAudioMillis = generationAudioMillis,
                    generationComputeMillis = generationComputeMillis,
                    sessionStartCompletedSegments = reusableSegments,
                    fileSizeBytes = target.generatedAudiobookKnownFileSizeBytes(generatedSegmentFileSizeBytes),
                    error = failureMessage
                )
                dao.upsertBookAudio(failed)
                failed
            }
        )
    }

    suspend fun generatePreviewAudio(
        modelId: String,
        speakerId: Int = 0,
        pace: NeuralTtsPace = NeuralTtsPace.STANDARD,
        tone: NeuralTtsTone = NeuralTtsTone.NATURAL,
    ): File = withContext(Dispatchers.IO) {
        val spec = NeuralTtsModelCatalog.requireModel(modelId)
        val model = dao.model(modelId)
        require(model?.status == NeuralTtsModelStatus.INSTALLED && !model.localPath.isNullOrBlank()) {
            "Download this neural voice before previewing it."
        }
        previewRoot.mkdirs()
        previewRoot.deleteStaleNeuralPreviewTempAudio()
        val output = File(previewRoot, neuralPreviewAudioFileName(modelId, speakerId, pace, tone))
        if (output.hasUsableNeuralPreviewAudio()) {
            return@withContext output
        }
        val modelDir = requireNotNull(model.localPath)
        val ttsGenerationConfig = generationConfig(speakerId, pace, tone)
        val tts = withContext(previewDispatcher) {
            createOfflineTts(
                spec = spec,
                modelDir = modelDir,
                tone = tone,
                workload = NeuralTtsRuntimeWorkload.PREVIEW
            )
        }
        try {
            val generated = withContext(previewDispatcher) {
                tts.engine.generateWithConfig(
                    text = PREVIEW_TEXT,
                    config = ttsGenerationConfig
                )
            }
            require(generated.samples.isNotEmpty()) { "Neural TTS produced no preview audio." }
            val saved = withContext(audioSaveDispatcher) {
                generated.saveNeuralPreviewAudio(output)
            }
            require(saved) { "Could not save neural voice preview." }
            require(output.hasUsableNeuralPreviewAudio()) { "Neural voice preview file was incomplete." }
            output
        } finally {
            tts.releaseOnPreviewDispatcher()
        }
    }

    private suspend fun TtsRuntime.releaseOnGenerationDispatcher() {
        withContext(generationDispatcher + NonCancellable) {
            runCatching { engine.release() }
                .onFailure { error ->
                    Log.w(tag, "Could not release neural TTS runtime provider=$provider.", error)
                }
        }
    }

    private suspend fun TtsRuntime.releaseOnPreviewDispatcher() {
        withContext(previewDispatcher + NonCancellable) {
            runCatching { engine.release() }
                .onFailure { error ->
                    Log.w(tag, "Could not release neural TTS preview runtime provider=$provider.", error)
                }
        }
    }

    private suspend fun updateModel(
        spec: NeuralTtsModelSpec,
        status: NeuralTtsModelStatus,
        downloadedBytes: Long = 0,
        localPath: String? = null,
        checksum: String? = null,
        installedAt: Long? = null,
        error: String? = null,
    ): NeuralTtsModelEntity {
        val existing = dao.model(spec.modelId)
        val updated = (existing ?: NeuralTtsModelEntity(
            modelId = spec.modelId,
            displayName = spec.displayName,
            engine = spec.engine,
            status = status,
            updatedAt = clock.millis()
        )).copy(
            displayName = spec.displayName,
            engine = spec.engine,
            status = status,
            localPath = localPath ?: existing?.localPath,
            downloadedBytes = downloadedBytes,
            totalBytes = spec.archiveBytes,
            checksumSha256 = checksum ?: existing?.checksumSha256,
            installedAt = installedAt ?: existing?.installedAt,
            updatedAt = clock.millis(),
            error = error
        )
        dao.upsertModel(updated)
        return updated
    }

    private suspend fun downloadArchive(spec: NeuralTtsModelSpec, archive: File) {
        val connection = (URL(spec.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var downloaded = 0L
            var lastPersisted = 0L
            connection.inputStream.use { input ->
                archive.outputStream().use { output ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded - lastPersisted >= DOWNLOAD_PROGRESS_STEP_BYTES) {
                            updateModel(spec, NeuralTtsModelStatus.DOWNLOADING, downloadedBytes = downloaded)
                            lastPersisted = downloaded
                        }
                    }
                }
            }
            updateModel(spec, NeuralTtsModelStatus.DOWNLOADING, downloadedBytes = downloaded)
            require(downloaded == spec.archiveBytes && archive.length() == spec.archiveBytes) {
                "Downloaded ${archive.length()} bytes; expected ${spec.archiveBytes}."
            }
        } catch (error: IOException) {
            throw IOException("Model download failed: ${error.message}", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun extractTarBz2(archive: File, destination: File) {
        TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(FileInputStream(archive)))).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                val target = File(destination, entry.name).canonicalFile
                require(target.path.startsWith(destination.canonicalPath)) { "Unsafe model archive path." }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { output -> tar.copyTo(output) }
                }
            }
        }
    }

    private fun audioDirectory(
        bookId: Long,
        modelId: String,
        speakerId: Int,
        pace: NeuralTtsPace,
        tone: NeuralTtsTone,
        scope: AudiobookGenerationScope,
    ): File {
        val safeSpeed = "%.2f".format(Locale.US, pace.speed)
        return File(audioRoot, "book-$bookId/$modelId-s$speakerId-v$safeSpeed-${tone.name.lowercase()}-${scope.key.lowercase()}")
    }

    private fun exportAudio(audio: BookAudioEntity, segments: List<File>, uri: Uri) {
        val source = File(requireNotNull(audio.filePath) { "Generated audio files are missing." })
        require(source.isDirectory) { "Generated audio files are missing." }
        require(segments.isNotEmpty()) { "Generated audio segments are missing." }
        val output = requireNotNull(appContext.contentResolver.openOutputStream(uri)) {
            "Could not open output file."
        }
        output.use { stream ->
            ZipOutputStream(stream).use { zip ->
                source.generatedAudiobookExportManifestFile()
                    ?.let { manifest ->
                        zip.putNextEntry(ZipEntry(FINAL_MANIFEST))
                        manifest.inputStream().use { input -> input.copyTo(zip) }
                        zip.closeEntry()
                    }
                val chapters = audio.generatedAudiobookChapters(segments.size)
                val segmentMetadata = audio.generatedAudiobookSegmentMetadata(segments.size, chapters)
                zip.putFileOrTextEntry(
                    source = File(source, CHAPTERS_FILE),
                    fallbackName = CHAPTERS_FILE,
                    fallbackText = chapters.takeIf { it.isNotEmpty() }?.toGeneratedAudiobookChaptersTsv()
                )
                zip.putTextEntry(
                    name = SEGMENTS_FILE,
                    text = segmentMetadata.exportTsv
                )
                segments.forEach { file ->
                    zip.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    private fun ZipOutputStream.putFileOrTextEntry(
        source: File,
        fallbackName: String,
        fallbackText: String?,
    ) {
        if (source.isFile) {
            putNextEntry(ZipEntry(source.name))
            source.inputStream().use { input -> input.copyTo(this) }
            closeEntry()
            return
        }
        if (fallbackText.isNullOrBlank()) return
        putNextEntry(ZipEntry(fallbackName))
        write(fallbackText.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun ZipOutputStream.putTextEntry(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun writeAudiobookManifest(
        target: File,
        title: String,
        spec: NeuralTtsModelSpec,
        provider: String?,
        hostThreadCount: Int? = null,
        pace: NeuralTtsPace,
        tone: NeuralTtsTone,
        scope: AudiobookGenerationScope,
        segmentCount: Int,
        completedSegments: Int,
        wordCount: Int,
        sampleRate: Int?,
        generationAudioMillis: Long,
        generationComputeMillis: Long,
        generationSaveMillis: Long = 0L,
        runtimeInitializationCount: Int = 0,
        runtimeInitializationMillis: Long = 0L,
        status: BookAudioStatus,
        error: String?,
    ) {
        target.mkdirs()
        val manifest = buildString {
            appendLine("title=$title")
            appendLine("model=${spec.displayName}")
            provider?.let { appendLine("provider=$it") }
            hostThreadCount?.takeIf { it > 0 }?.let { appendLine("hostThreads=$it") }
            appendLine("gender=${spec.gender.label}")
            appendLine("tone=${tone.label}")
            appendLine("pace=${pace.label}")
            appendLine("scope=${scope.label}")
            appendLine("status=${status.name.lowercase()}")
            appendLine("segments=$segmentCount")
            appendLine("completed=$completedSegments")
            appendLine("words=$wordCount")
            sampleRate?.let { appendLine("sampleRate=$it") }
            if (generationAudioMillis > 0) appendLine("generationAudioMillis=$generationAudioMillis")
            if (generationComputeMillis > 0) appendLine("generationComputeMillis=$generationComputeMillis")
            if (generationSaveMillis > 0) appendLine("generationSaveMillis=$generationSaveMillis")
            if (runtimeInitializationCount > 0) appendLine("runtimeInitializations=$runtimeInitializationCount")
            if (runtimeInitializationMillis > 0) appendLine("runtimeInitializationMillis=$runtimeInitializationMillis")
            generationAudioTimeFactor(generationAudioMillis, generationComputeMillis)
                ?.let { appendLine("generationRealtimeFactor=${"%.2f".format(Locale.US, it)}") }
            appendLine("updatedAt=${clock.millis()}")
            error?.takeIf { it.isNotBlank() }?.let { appendLine("error=${it.lineSequence().first()}") }
        }
        File(target, IN_PROGRESS_MANIFEST).writeTextAtomically(manifest)
        if (status == BookAudioStatus.GENERATED) {
            File(target, FINAL_MANIFEST).writeTextAtomically(manifest)
            File(target, IN_PROGRESS_MANIFEST).delete()
        }
    }

    private fun finalizedGeneratedAudiobookEntity(
        audio: BookAudioEntity,
        target: File,
        status: BookAudioStatus,
        scope: AudiobookGenerationScope,
        segmentCount: Int,
        completedSegments: Int,
        wordCount: Int,
        sampleRate: Int,
        generationProvider: String?,
        generationAudioMillis: Long,
        generationComputeMillis: Long,
        sessionStartCompletedSegments: Int,
        fileSizeBytes: Long,
        error: String?,
    ): BookAudioEntity {
        val boundedTotal = segmentCount.coerceAtLeast(0)
        val boundedCompleted = when (status) {
            BookAudioStatus.GENERATED -> boundedTotal
            BookAudioStatus.GENERATING,
            BookAudioStatus.CANCELED,
            BookAudioStatus.FAILED -> completedSegments.coerceIn(0, boundedTotal)
        }
        val now = clock.millis()
        return audio.copy(
            status = status,
            filePath = target.absolutePath,
            scope = scope.key,
            scopeLabel = scope.label,
            segmentCount = boundedTotal,
            completedSegments = boundedCompleted,
            wordCount = wordCount.coerceAtLeast(0),
            sampleRate = sampleRate.coerceAtLeast(0),
            fileSizeBytes = fileSizeBytes.coerceAtLeast(0L),
            generationProvider = generationProvider,
            generationAudioMillis = generationAudioMillis.coerceAtLeast(0L),
            generationComputeMillis = generationComputeMillis.coerceAtLeast(0L),
            generationStartedAt = audio.generationStartedAt,
            generationSessionStartCompletedSegments = sessionStartCompletedSegments.coerceIn(0, boundedTotal),
            generatedAt = if (status == BookAudioStatus.GENERATED) now else audio.generatedAt,
            updatedAt = now,
            error = error
        ).withPlaybackBoundedToGeneratedAudio(boundedCompleted)
    }

    private fun writeAudiobookStructure(
        target: File,
        prepared: NeuralTtsPreparedBook,
    ) {
        target.mkdirs()
        File(target, CHAPTERS_FILE).writeAtomically { writer ->
            writer.appendLine("index\tfirstSegment\tsegmentCount\ttitle")
            prepared.chapters.forEach { chapter ->
                writer.appendDecimal(chapter.index)
                writer.append('\t')
                writer.appendDecimal(chapter.firstSegmentIndex)
                writer.append('\t')
                writer.appendDecimal(chapter.segmentCount)
                writer.append('\t')
                writer.appendLine(chapter.title.tsvEscaped())
            }
        }
        File(target, SEGMENTS_FILE).writeAtomically { writer ->
            writer.appendLine("index\tchapterIndex\tpauseAfterMs\ttext")
            prepared.segments.forEachIndexed { index, segment ->
                writer.appendDecimal(index)
                writer.append('\t')
                writer.appendDecimal(prepared.segmentChapterIndexes.getOrElse(index) { 0 })
                writer.append('\t')
                writer.appendDecimal(prepared.segmentPauseMillis.getOrElse(index) { DEFAULT_AUDIOBOOK_SEGMENT_PAUSE_MS })
                writer.append('\t')
                writer.appendLine(segment.tsvEscaped())
            }
        }
    }

    private fun createOfflineTts(
        spec: NeuralTtsModelSpec,
        modelDir: String,
        tone: NeuralTtsTone,
        workload: NeuralTtsRuntimeWorkload,
    ): TtsRuntime {
        val providers = TtsAccelerationRuntime.providerOrder(
            context = appContext,
        )
        val runtimeProviders = providers.enforceRequiredAccelerator(
            workload = workload,
            spec = spec,
            modelDir = File(modelDir)
        )
        Log.i(
            tag,
            "Starting ${spec.displayName} runtime initialization for workload=$workload " +
                "with providers=${runtimeProviders.joinToString { TtsAccelerationRuntime.providerDisplayKey(it) }}."
        )
        val provider = runtimeProviders.single()
        val hostThreadCount = neuralTtsHostThreadCount(provider, workload = workload)
        Log.i(
            tag,
            "Trying ${spec.displayName} provider=${TtsAccelerationRuntime.providerKey(provider)} " +
                "for workload=$workload with hostThreads=$hostThreadCount."
        )
        val startedAt = clock.millis()
        return runCatching {
            OfflineTts(config = ttsConfig(spec, modelDir, tone, provider, hostThreadCount, workload))
        }.map { engine ->
            val initializationMillis = (clock.millis() - startedAt).coerceAtLeast(0L)
            TtsAccelerationRuntime.recordProviderInitialized(provider)
            val providerKey = TtsAccelerationRuntime.providerDisplayKey(provider)
            Log.i(
                tag,
                "Initialized ${spec.displayName} with provider=$providerKey, " +
                    "hostThreads=$hostThreadCount, initMs=$initializationMillis."
            )
            TtsRuntime(
                engine = engine,
                provider = providerKey,
                hostThreadCount = hostThreadCount,
                initializationMillis = initializationMillis
            )
        }.getOrElse { error ->
            TtsAccelerationRuntime.recordProviderInitializationFailed(provider, error)
            Log.w(tag, "Could not initialize ${spec.displayName} with provider=$provider.", error)
            val summary = TtsAccelerationRuntime.providerInitializationFailureSummary(provider, error)
                ?: error.message
                ?: error::class.java.simpleName
            throw IllegalStateException(
                "Could not initialize local neural TTS runtime with " +
                    "${TtsAccelerationRuntime.providerDisplayKey(provider)}: $summary",
                error
            )
        }
    }

    private fun List<String>.enforceRequiredAccelerator(
        workload: NeuralTtsRuntimeWorkload,
        spec: NeuralTtsModelSpec,
        modelDir: File,
    ): List<String> {
        val selection = neuralTtsSelectStrictHardwareProviders(
            providers = this,
            spec = spec,
            modelDir = modelDir,
            workload = workload
        )
        check(selection.usableProviders.isNotEmpty()) {
            neuralTtsNoUsableHardwareProviderReason(selection, workload)
        }
        return listOf(selection.usableProviders.single())
    }

    private suspend fun ensureAudiobookGenerationStillActive(audioId: Long, segmentNumber: Int) {
        if (dao.generatingBookAudioCount(audioId) != 1) {
            throw CancellationException(
                "Audiobook generation was stopped before segment $segmentNumber could be recorded."
            )
        }
    }

    private fun ttsConfig(
        spec: NeuralTtsModelSpec,
        modelDir: String,
        tone: NeuralTtsTone,
        provider: String,
        hostThreadCount: Int,
        workload: NeuralTtsRuntimeWorkload,
    ): OfflineTtsConfig {
        val selectedModelFile = neuralTtsModelFileForProvider(
            spec = spec,
            modelDir = File(modelDir),
            provider = provider,
        )
        return getOfflineTtsConfig(
            modelDir = modelDir,
            modelName = selectedModelFile,
            acousticModelName = "",
            vocoder = "",
            voices = spec.voicesFile,
            lexicon = spec.lexiconFile,
            dataDir = File(modelDir, spec.dataDirectory).absolutePath,
            dictDir = "",
            ruleFsts = "",
            ruleFars = "",
            numThreads = hostThreadCount,
            provider = provider,
            isKitten = false
        ).apply {
            maxNumSentences = kokoroMaxNumSentences(workload)
            silenceScale = tone.silenceScale
        }
    }

    private fun deleteModelFiles(model: NeuralTtsModelEntity) {
        model.localPath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.parentFile
            ?.canonicalFile
            ?.takeIf { it.parentFile == modelRoot.canonicalFile }
            ?.deleteRecursively()
        File(modelRoot, "${model.modelId}.tar.bz2").delete()
    }

    private fun generationConfig(
        speakerId: Int,
        pace: NeuralTtsPace,
        tone: NeuralTtsTone,
    ): GenerationConfig = neuralTtsGenerationConfig(speakerId, pace, tone)

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val DOWNLOAD_PROGRESS_STEP_BYTES = 1_048_576L
        const val PREVIEW_TEXT = "This is XReader's local neural voice preview, generated privately on this device."
        const val FINAL_MANIFEST = "manifest.txt"
        const val IN_PROGRESS_MANIFEST = "manifest.in-progress.txt"
        const val CHAPTERS_FILE = "chapters.tsv"
        const val SEGMENTS_FILE = "segments.tsv"
    }
}

private val GENERATED_AUDIOBOOK_SIDECAR_FILES = arrayOf(
    "manifest.txt",
    "manifest.in-progress.txt",
    "chapters.tsv",
    "segments.tsv"
)

private data class TtsRuntime(
    val engine: OfflineTts,
    val provider: String,
    val hostThreadCount: Int,
    val initializationMillis: Long,
)

private data class GeneratedTtsSegment(
    val audio: GeneratedAudio,
    val computeMillis: Long,
)

private data class GenerationHeartbeatSnapshot(
    val completedSegments: Int,
    val provider: String?,
    val audioMillis: Long,
    val computeMillis: Long,
    val sampleRate: Int,
    val fileSizeBytes: Long,
)

internal data class AudiobookGenerationMetricTotals(
    val audioMillis: Long = 0L,
    val computeMillis: Long = 0L,
    val saveMillis: Long = 0L,
)

internal data class AudiobookGeneratedSegmentMetrics(
    val audioMillis: Long,
    val computeMillis: Long,
    val saveMillis: Long,
)

internal fun AudiobookGenerationMetricTotals.plusSegment(
    segment: AudiobookGeneratedSegmentMetrics,
): AudiobookGenerationMetricTotals =
    copy(
        audioMillis = audioMillis.plusNonNegativeDuration(segment.audioMillis),
        computeMillis = computeMillis.plusNonNegativeDuration(segment.computeMillis),
        saveMillis = saveMillis.plusNonNegativeDuration(segment.saveMillis),
    )

internal fun Long.plusNonNegativeDuration(durationMillis: Long): Long =
    this + durationMillis.coerceAtLeast(0L)

data class AudiobookGenerationHardwareReadiness(
    val ready: Boolean,
    val reason: String? = null,
    val providerLabels: List<String> = emptyList(),
)

private val sharedNeuralTtsGenerationDispatcher: CoroutineDispatcher by lazy {
    Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "XReader-NeuralTTS-Generation").apply {
            isDaemon = true
        }
    }.asCoroutineDispatcher()
}

private val sharedNeuralTtsPreviewDispatcher: CoroutineDispatcher by lazy {
    Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "XReader-NeuralTTS-Preview").apply {
            isDaemon = true
        }
    }.asCoroutineDispatcher()
}

private val sharedNeuralTtsAudioSaveDispatcher: CoroutineDispatcher by lazy {
    Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "XReader-NeuralTTS-AudioSave").apply {
            isDaemon = true
        }
    }.asCoroutineDispatcher()
}

internal fun neuralTtsGenerationDispatcher(): CoroutineDispatcher =
    sharedNeuralTtsGenerationDispatcher

internal fun neuralTtsPreviewDispatcher(): CoroutineDispatcher =
    sharedNeuralTtsPreviewDispatcher

internal fun neuralTtsAudioSaveDispatcher(): CoroutineDispatcher =
    sharedNeuralTtsAudioSaveDispatcher

internal fun neuralTtsGenerationConfig(
    speakerId: Int,
    pace: NeuralTtsPace,
    tone: NeuralTtsTone,
): GenerationConfig =
    GenerationConfig(
        sid = speakerId,
        speed = pace.speed.coerceIn(0.75f, 1.35f),
        silenceScale = tone.silenceScale,
    )

internal fun neuralTtsModelFileForProvider(
    spec: NeuralTtsModelSpec,
    modelDir: File,
    provider: String,
): String {
    val hardwareModel = spec.hardwareModelFile.takeIf { it.isNotBlank() } ?: return spec.modelFile
    return if (
        neuralTtsProviderRequiresPreparedHardwareModel(provider) &&
        File(modelDir, hardwareModel).isFile
    ) {
        hardwareModel
    } else {
        spec.modelFile
    }
}

internal fun neuralTtsProviderRequiresPreparedHardwareModel(provider: String): Boolean =
    TtsAccelerationRuntime.qnnBackend(provider) != null

internal fun neuralTtsProviderHasRequiredModelArtifact(
    spec: NeuralTtsModelSpec,
    modelDir: File,
    provider: String,
): Boolean {
    if (!neuralTtsProviderRequiresPreparedHardwareModel(provider)) return false
    val hardwareModel = spec.hardwareModelFile.takeIf { it.isNotBlank() } ?: return false
    val modelFile = File(modelDir, hardwareModel)
    return modelFile.isFile && modelFile.hasStrictQnnCompatibilityManifest(spec)
}

internal data class NeuralTtsStrictHardwareProviderSelection(
    val candidateProviders: List<String>,
    val missingArtifactProviders: List<String>,
) {
    val usableProviders: List<String> =
        candidateProviders - missingArtifactProviders.toSet()
}

internal fun neuralTtsSelectStrictHardwareProviders(
    providers: List<String>,
    spec: NeuralTtsModelSpec,
    modelDir: File,
    workload: NeuralTtsRuntimeWorkload,
): NeuralTtsStrictHardwareProviderSelection {
    val candidates = when (workload) {
        NeuralTtsRuntimeWorkload.PREVIEW ->
            providers.filter(TtsAccelerationRuntime::isHardwareAcceleratedProvider)
        NeuralTtsRuntimeWorkload.AUDIOBOOK_GENERATION ->
            providers.filter(TtsAccelerationRuntime::isAudiobookGenerationAcceleratedProvider)
    }
    val selectedCandidates = TtsAccelerationRuntime.selectedQnnProvider(candidates)
        ?.let(::listOf)
        .orEmpty()
    val missingArtifacts = selectedCandidates.filterNot {
        neuralTtsProviderHasRequiredModelArtifact(spec, modelDir, it)
    }
    return NeuralTtsStrictHardwareProviderSelection(
        candidateProviders = selectedCandidates,
        missingArtifactProviders = missingArtifacts
    )
}

internal fun neuralTtsNoUsableHardwareProviderReason(
    selection: NeuralTtsStrictHardwareProviderSelection,
    workload: NeuralTtsRuntimeWorkload,
): String = buildString {
    append(
        when (workload) {
            NeuralTtsRuntimeWorkload.PREVIEW ->
                "Neural voice preview requires packaged hardware acceleration. "
            NeuralTtsRuntimeWorkload.AUDIOBOOK_GENERATION ->
                "Full-book neural audiobook generation requires packaged hardware acceleration. "
        }
    )
    append("No usable hardware provider is available on this device/build.")
    if (selection.candidateProviders.isNotEmpty()) {
        append(" Candidates=")
        append(selection.candidateProviders.joinToString { TtsAccelerationRuntime.providerDisplayKey(it) })
        append(".")
    }
    if (selection.missingArtifactProviders.isNotEmpty()) {
        append(" Missing prepared model artifacts for ")
        append(selection.missingArtifactProviders.joinToString { TtsAccelerationRuntime.providerDisplayKey(it) })
        append(".")
    }
}

internal fun neuralTtsMissingHardwareArtifactReason(
    spec: NeuralTtsModelSpec,
    providers: List<String>,
): String {
    val labels = providers
        .map(TtsAccelerationRuntime::providerDisplayKey)
        .distinct()
        .joinToString()
        .ifBlank { "strict hardware providers" }
    val artifact = spec.hardwareModelFile.takeIf { it.isNotBlank() } ?: "prepared hardware model"
    return "$labels can run full-book generation, but ${spec.displayName} is missing $artifact. " +
        "Prepare and install a strict-compatible QNN/NPU model artifact before generating audiobook audio."
}

private fun File.hasStrictQnnCompatibilityManifest(spec: NeuralTtsModelSpec): Boolean {
    val manifestName = spec.hardwareModelManifestFile.takeIf { it.isNotBlank() } ?: return false
    val manifest = File(parentFile ?: return false, manifestName)
    if (!manifest.isFile) return false
    val text = runCatching { manifest.readText() }.getOrNull() ?: return false
    return STRICT_QNN_COMPATIBLE_JSON.containsMatchIn(text) &&
        Regex(
            pattern = """"output_model"\s*:\s*"${Regex.escape(name)}"""",
            option = RegexOption.IGNORE_CASE
        ).containsMatchIn(text)
}

internal enum class NeuralTtsRuntimeWorkload {
    PREVIEW,
    AUDIOBOOK_GENERATION,
}

internal fun kokoroMaxNumSentences(workload: NeuralTtsRuntimeWorkload): Int =
    when (workload) {
        NeuralTtsRuntimeWorkload.PREVIEW -> KOKORO_PREVIEW_MAX_NUM_SENTENCES
        NeuralTtsRuntimeWorkload.AUDIOBOOK_GENERATION -> KOKORO_AUDIOBOOK_GENERATION_MAX_NUM_SENTENCES
    }

internal fun neuralTtsHostThreadCount(
    provider: String,
    workload: NeuralTtsRuntimeWorkload = NeuralTtsRuntimeWorkload.PREVIEW,
    availableProcessors: Int = Runtime.getRuntime().availableProcessors(),
): Int {
    return when (TtsAccelerationRuntime.providerKey(provider)) {
        "qnn" -> 1
        else -> error("Neural TTS requires strict QNN hardware acceleration. Provider '$provider' is disabled.")
    }
}

internal fun GeneratedAudio.audioDurationMillis(): Long =
    if (sampleRate > 0) {
        samples.size.toLong() * 1000L / sampleRate.toLong()
    } else {
        0L
    }

internal fun generationAudioTimeFactor(audioMillis: Long, computeMillis: Long): Float? {
    if (audioMillis <= 0L || computeMillis <= 0L) return null
    return computeMillis.toFloat() / audioMillis.toFloat()
}

internal fun isUsableAudiobookHardwareAudioTimeFactor(audioTimeFactor: Float): Boolean =
    audioTimeFactor.isFinite() && audioTimeFactor > 0f && audioTimeFactor <= MAX_AUDIOBOOK_HARDWARE_AUDIO_TIME_FACTOR

internal fun isSustainedUnusableAudiobookHardwareGenerationSpeed(
    audioMillis: Long,
    computeMillis: Long,
    generatedSegments: Int,
): Boolean {
    if (generatedSegments < MIN_HARDWARE_SPEED_GATE_SEGMENTS) return false
    if (audioMillis < MIN_HARDWARE_SPEED_GATE_AUDIO_MS) return false
    val audioTimeFactor = generationAudioTimeFactor(audioMillis, computeMillis) ?: return false
    return !isUsableAudiobookHardwareAudioTimeFactor(audioTimeFactor)
}

internal fun neuralPreviewAudioFileName(
    modelId: String,
    speakerId: Int,
    pace: NeuralTtsPace,
    tone: NeuralTtsTone,
): String =
    "${modelId}-s${speakerId.coerceAtLeast(0)}-${pace.name.lowercase()}-${tone.name.lowercase()}.wav"

internal fun File.hasUsableNeuralPreviewAudio(): Boolean =
    isFile && length() > WAV_HEADER_BYTES

internal fun neuralPreviewTempAudioFileName(fileName: String): String =
    "$fileName.tmp"

internal fun File.deleteStaleNeuralPreviewTempAudio(): Int {
    if (!isDirectory) return 0
    var deleted = 0
    listFiles()?.forEach { file ->
        if (file.isFile && file.name.endsWith(".wav.tmp") && file.delete()) {
            deleted += 1
        }
    }
    return deleted
}

internal fun shouldWriteGenerationCheckpoint(completedSegments: Int, totalSegments: Int): Boolean =
    completedSegments in 1..totalSegments.coerceAtLeast(1) &&
        (
            completedSegments <= 1 ||
                completedSegments >= totalSegments ||
                completedSegments % generationManifestCheckpointSegmentStep(totalSegments) == 0
            )

internal fun generationManifestCheckpointSegmentStep(totalSegments: Int): Int =
    if (totalSegments <= SMALL_GENERATION_PROGRESS_SEGMENTS) {
        1
    } else {
        generationProgressWriteSegmentStep(totalSegments)
            .coerceAtLeast(GENERATION_MANIFEST_CHECKPOINT_SEGMENTS)
    }

internal fun shouldWriteGenerationProgress(
    completedSegments: Int,
    totalSegments: Int,
    lastProgressWrittenSegments: Int,
    lastProgressWrittenAtMillis: Long = 0L,
    nowMillis: Long = 0L,
): Boolean =
    completedSegments in 1..totalSegments.coerceAtLeast(1) &&
        completedSegments > lastProgressWrittenSegments &&
        (
            completedSegments <= 1 ||
                completedSegments <= INITIAL_GENERATION_PROGRESS_SEGMENTS ||
                completedSegments >= totalSegments ||
                generationProgressWriteTimeElapsed(lastProgressWrittenAtMillis, nowMillis) ||
                completedSegments - lastProgressWrittenSegments >= generationProgressWriteSegmentStep(totalSegments)
            )

internal fun generationProgressWriteTimeElapsed(lastProgressWrittenAtMillis: Long, nowMillis: Long): Boolean =
    lastProgressWrittenAtMillis > 0L &&
        nowMillis > 0L &&
        nowMillis - lastProgressWrittenAtMillis >= GENERATION_PROGRESS_WRITE_INTERVAL_MS

internal fun shouldWriteGenerationHeartbeat(
    lastHeartbeatWrittenAtMillis: Long,
    nowMillis: Long,
    snapshotChanged: Boolean = true,
    snapshotAlreadyPersisted: Boolean = false,
): Boolean =
    lastHeartbeatWrittenAtMillis > 0L &&
        nowMillis > 0L &&
        snapshotChanged &&
        !snapshotAlreadyPersisted &&
        nowMillis - lastHeartbeatWrittenAtMillis >= GENERATION_HEARTBEAT_WRITE_INTERVAL_MS

internal fun generationProgressWriteSegmentStep(totalSegments: Int): Int {
    val boundedTotal = totalSegments.coerceAtLeast(1)
    if (boundedTotal <= SMALL_GENERATION_PROGRESS_SEGMENTS) return 1
    return ((boundedTotal + TARGET_LONG_GENERATION_PROGRESS_UPDATES - 1) / TARGET_LONG_GENERATION_PROGRESS_UPDATES)
        .coerceAtLeast(MIN_LONG_GENERATION_PROGRESS_SEGMENT_STEP)
}

internal fun prepareAudiobookGenerationTarget(target: File, canResumeExistingAudio: Boolean) {
    if (!canResumeExistingAudio && target.exists()) {
        target.deleteRecursively()
    }
    target.mkdirs()
    target.deleteStaleGeneratedAudiobookTempSegments()
}

internal fun File.generatedAudiobookExportManifestFile(): File? =
    File(this, "manifest.txt").takeIf { it.isFile }
        ?: File(this, "manifest.in-progress.txt").takeIf { it.isFile }

internal fun File.generatedAudiobookManifestLong(key: String): Long =
    generatedAudiobookManifestValue(key)
        ?.toLongOrNull()
        ?.coerceAtLeast(0L)
        ?: 0L

private fun File.generatedAudiobookManifestValue(key: String): String? {
    if (key.isBlank()) return null
    val prefix = "$key="
    val manifest = File(this, "manifest.in-progress.txt").takeIf { it.isFile }
        ?: File(this, "manifest.txt").takeIf { it.isFile }
        ?: return null
    return runCatching {
        manifest.useLines { lines ->
            lines.firstNotNullOfOrNull { line ->
                line.takeIf { it.startsWith(prefix) }?.substringAfter('=')
            }
        }
    }.getOrNull()
}

internal fun rewriteAudiobookRecoveryManifest(
    target: File,
    status: BookAudioStatus,
    completedSegments: Int,
    updatedAt: Long,
    error: String?,
): Boolean {
    val manifest = File(target, "manifest.in-progress.txt").takeIf { it.isFile }
        ?: File(target, "manifest.txt").takeIf { it.isFile }
        ?: return false
    val rewritten = rewriteAudiobookManifestText(
        text = manifest.readText(),
        status = status,
        completedSegments = completedSegments,
        updatedAt = updatedAt,
        error = error
    )
    manifest.writeTextAtomically(rewritten)
    return true
}

internal fun rewriteAudiobookManifestText(
    text: String,
    status: BookAudioStatus,
    completedSegments: Int,
    updatedAt: Long,
    error: String?,
): String {
    val replacements = linkedMapOf(
        "status" to status.name.lowercase(),
        "completed" to completedSegments.coerceAtLeast(0).toString(),
        "updatedAt" to updatedAt.toString()
    )
    val seen = mutableSetOf<String>()
    val lines = text.lineSequence()
        .filter { it.isNotBlank() }
        .map { line ->
            val key = line.substringBefore('=', missingDelimiterValue = "")
            val replacement = replacements[key]
            if (replacement != null) {
                seen += key
                "$key=$replacement"
            } else if (key == "error") {
                seen += key
                error?.takeIf { it.isNotBlank() }?.let { "error=${it.lineSequence().first()}" }.orEmpty()
            } else {
                line
            }
        }
        .filter { it.isNotBlank() }
        .toMutableList()
    replacements.forEach { (key, value) ->
        if (key !in seen) lines += "$key=$value"
    }
    if (!error.isNullOrBlank() && "error" !in seen) {
        lines += "error=${error.lineSequence().first()}"
    }
    return lines.joinToString(separator = "\n", postfix = "\n")
}

private fun BookAudioEntity?.canResumeGeneration(
    target: File,
    segmentCount: Int,
    wordCount: Int,
): Boolean {
    if (this == null) return false
    if (status == BookAudioStatus.GENERATED) return false
    if (filePath != target.absolutePath) return false
    if (this.segmentCount != segmentCount) return false
    if (this.wordCount != wordCount) return false
    return target.isDirectory
}

internal fun BookAudioEntity.isFreshActiveAudiobookGeneration(
    nowMillis: Long,
    staleAgeMillis: Long = STALE_GENERATING_AUDIO_REPAIR_AGE_MS,
): Boolean =
    status == BookAudioStatus.GENERATING &&
        segmentCount > 0 &&
        updatedAt >= nowMillis - staleAgeMillis

internal fun shouldWriteRecoveredGeneratingProgress(
    reusableSegments: Int,
    completedSegments: Int,
    reconcileIncomplete: Boolean,
): Boolean =
    !reconcileIncomplete && reusableSegments > completedSegments

internal fun BookAudioEntity.withAudiobookGenerationStartState(
    canResumeExistingAudio: Boolean,
    target: File,
    reusableSegments: Int,
): BookAudioEntity =
    copy(
        sampleRate = sampleRate.takeIf { canResumeExistingAudio }?.coerceAtLeast(0) ?: 0,
        fileSizeBytes = if (canResumeExistingAudio) {
            target.generatedAudiobookKnownFilesSizeBytes(reusableSegments)
        } else {
            0L
        },
        generatedAt = null,
        playbackSegmentIndex = playbackSegmentIndex.takeIf { canResumeExistingAudio }?.coerceAtLeast(0) ?: 0,
        playbackPositionMs = playbackPositionMs.takeIf { canResumeExistingAudio }?.coerceAtLeast(0) ?: 0,
    )

internal fun BookAudioEntity.generatedAudiobookKnownFilesSizeBytes(completedSegments: Int): Long =
    filePath?.let(::File)?.generatedAudiobookKnownFilesSizeBytes(completedSegments) ?: 0L

internal fun shouldPersistGeneratedAudiobookPlaybackPosition(
    audio: BookAudioEntity,
    position: GeneratedAudiobookPersistedPlaybackPosition,
): Boolean =
    audio.playbackSegmentIndex != position.segmentIndex ||
        audio.playbackPositionMs != position.positionMs

internal fun File.generatedAudiobookKnownFilesSizeBytes(completedSegments: Int): Long {
    if (!isDirectory) return 0L
    return generatedAudiobookSegmentFilesSizeBytes(completedSegments) + generatedAudiobookSidecarSizeBytes()
}

internal fun File.generatedAudiobookKnownFileSizeBytes(segmentFileSizeBytes: Long): Long =
    segmentFileSizeBytes.coerceAtLeast(0L) + generatedAudiobookSidecarSizeBytes()

internal fun File.generatedAudiobookSegmentFilesSizeBytes(completedSegments: Int): Long {
    if (!isDirectory) return 0L
    var total = 0L
    repeat(completedSegments.coerceAtLeast(0)) { index ->
        val segment = File(this, generatedAudiobookSegmentFileName(index))
        if (segment.isFile) total += segment.length()
    }
    return total
}

internal fun File.generatedAudiobookSidecarSizeBytes(): Long {
    if (!isDirectory) return 0L
    var total = 0L
    GENERATED_AUDIOBOOK_SIDECAR_FILES.forEach { name ->
        val sidecar = File(this, name)
        if (sidecar.isFile) total += sidecar.length()
    }
    return total
}

internal fun String.tsvEscaped(): String =
    replace("\\", "\\\\")
        .replace("\t", "\\t")
        .replace("\r", "\\r")
        .replace("\n", "\\n")

internal fun reusableGeneratedAudiobookSegments(directory: File, expectedSegments: Int): Int {
    return directory.countContiguousGeneratedAudiobookSegments(expectedSegments)
}

internal fun generatedAudiobookSegmentFileName(index: Int): String =
    "segment-${(index + 1).toString().padStart(5, '0')}.wav"

internal fun generatedAudiobookTempSegmentFileName(index: Int): String =
    "${generatedAudiobookSegmentFileName(index)}.tmp"

internal fun File.deleteStaleGeneratedAudiobookTempSegments(): Int {
    if (!isDirectory) return 0
    var deleted = 0
    listFiles()?.forEach { file ->
        if (file.isFile && file.name.startsWith("segment-") && file.name.endsWith(".wav.tmp") && file.delete()) {
            deleted += 1
        }
    }
    return deleted
}

internal fun File.writeTextAtomically(text: String) {
    parentFile?.mkdirs()
    val temp = File(parentFile ?: return writeText(text), "$name.tmp")
    runCatching {
        temp.writeText(text)
        replaceWithTempFile(temp)
    }.getOrElse { error ->
        temp.delete()
        throw error
    }
}

internal fun File.writeAtomically(block: (java.io.BufferedWriter) -> Unit) {
    parentFile?.mkdirs()
    val temp = File(parentFile ?: return bufferedWriter().use(block), "$name.tmp")
    runCatching {
        temp.bufferedWriter().use(block)
        replaceWithTempFile(temp)
    }.getOrElse { error ->
        temp.delete()
        throw error
    }
}

private fun BufferedWriter.appendDecimal(value: Int): BufferedWriter =
    appendDecimal(value.toLong())

private fun BufferedWriter.appendDecimal(value: Long): BufferedWriter {
    if (value == 0L) {
        append('0')
        return this
    }
    if (value == Long.MIN_VALUE) {
        append("-9223372036854775808")
        return this
    }
    var remaining = value
    if (remaining < 0L) {
        append('-')
        remaining = -remaining
    }
    var divisor = 1L
    while (remaining / divisor >= 10L) {
        divisor *= 10L
    }
    while (divisor > 0L) {
        val digit = (remaining / divisor).toInt()
        append(('0'.code + digit).toChar())
        remaining %= divisor
        divisor /= 10L
    }
    return this
}

private fun File.replaceWithTempFile(temp: File) {
    runCatching {
        Files.move(
            temp.toPath(),
            toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE
        )
    }.recoverCatching { error ->
        if (error !is AtomicMoveNotSupportedException) throw error
        Files.move(temp.toPath(), toPath(), StandardCopyOption.REPLACE_EXISTING)
    }.getOrElse { error ->
        temp.delete()
        throw IOException("Could not finalize $name.", error)
    }
}

internal fun GeneratedAudio.saveGeneratedAudiobookSegment(file: File): Long {
    file.parentFile?.mkdirs()
    val index = file.name
        .removePrefix("segment-")
        .removeSuffix(".wav")
        .toIntOrNull()
        ?.minus(1)
        ?: return 0L
    val temp = File(file.parentFile ?: return 0L, generatedAudiobookTempSegmentFileName(index))
    temp.delete()
    if (!save(temp.absolutePath)) {
        temp.delete()
        return 0L
    }
    return file.replaceWithUsableWavTemp(temp)
}

internal fun GeneratedAudio.saveNeuralPreviewAudio(file: File): Boolean {
    file.parentFile?.mkdirs()
    val temp = File(file.parentFile ?: return save(file.absolutePath), neuralPreviewTempAudioFileName(file.name))
    temp.delete()
    if (!save(temp.absolutePath)) {
        temp.delete()
        return false
    }
    return file.replaceWithUsableWavTemp(temp) > 0L
}

internal fun File.replaceWithUsableWavTemp(temp: File): Long {
    val savedBytes = temp.length()
    if (savedBytes <= WAV_HEADER_BYTES) {
        temp.delete()
        return 0L
    }
    return runCatching {
        replaceWithTempFile(temp)
        savedBytes
    }.getOrElse {
        temp.delete()
        0L
    }
}

internal fun deleteGeneratedAudiobookFiles(audio: BookAudioEntity): Boolean {
    val path = audio.filePath?.takeIf { it.isNotBlank() } ?: return false
    val target = File(path)
    return when {
        target.isDirectory -> target.deleteRecursively()
        target.isFile -> target.delete()
        else -> false
    }
}

@SuppressLint("UnclosedTrace")
private inline fun <T> traced(name: String, block: () -> T): T {
    Trace.beginSection(name.take(127))
    return try {
        block()
    } finally {
        Trace.endSection()
    }
}

internal const val KOKORO_PREVIEW_MAX_NUM_SENTENCES = 1
internal const val KOKORO_AUDIOBOOK_GENERATION_MAX_NUM_SENTENCES = 3
private const val GENERATION_MANIFEST_CHECKPOINT_SEGMENTS = 4
private const val SMALL_GENERATION_PROGRESS_SEGMENTS = 24
private const val INITIAL_GENERATION_PROGRESS_SEGMENTS = 8
private const val MIN_LONG_GENERATION_PROGRESS_SEGMENT_STEP = 4
private const val TARGET_LONG_GENERATION_PROGRESS_UPDATES = 120
private const val GENERATION_PROGRESS_WRITE_INTERVAL_MS = 8_000L
private const val GENERATION_CANCELLATION_POLL_INTERVAL_MS = 10_000L
private const val GENERATION_HEARTBEAT_WRITE_INTERVAL_MS = 30_000L
private const val MAX_GENERATING_AUDIO_ERROR_LENGTH = 240
internal const val STALE_GENERATING_AUDIO_REPAIR_AGE_MS = 60 * 1000L
internal const val MAX_AUDIOBOOK_HARDWARE_AUDIO_TIME_FACTOR = 0.55f
internal const val MIN_HARDWARE_SPEED_GATE_SEGMENTS = 3
internal const val MIN_HARDWARE_SPEED_GATE_AUDIO_MS = 45_000L
private val STRICT_QNN_COMPATIBLE_JSON = Regex(
    pattern = """"strict_qnn_compatible"\s*:\s*true""",
    option = RegexOption.IGNORE_CASE
)
