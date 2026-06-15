package com.xreader.app.tts

import android.content.Context
import android.net.Uri
import android.util.Log
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import com.xreader.app.data.BookAudioEntity
import com.xreader.app.data.BookAudioStatus
import com.xreader.app.data.NeuralTtsDao
import com.xreader.app.data.NeuralTtsModelEntity
import com.xreader.app.data.NeuralTtsModelStatus
import com.xreader.app.settings.NeuralTtsPace
import com.xreader.app.settings.NeuralTtsTone
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Clock
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

class NeuralTtsRepository(
    context: Context,
    private val dao: NeuralTtsDao,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val tag = "NeuralTtsRepository"
    private val appContext = context.applicationContext
    private val modelRoot = File(appContext.filesDir, "neural-tts/models")
    private val audioRoot = File(appContext.filesDir, "neural-tts/book-audio")
    private val previewRoot = File(appContext.cacheDir, "neural-tts/previews")

    fun observeModels(): Flow<List<NeuralTtsModelEntity>> = dao.observeModels()

    fun observeBookAudio(bookId: Long): Flow<List<BookAudioEntity>> =
        dao.observeBookAudio(bookId).onEach { rows ->
            rows.forEach { audio ->
                repairBookAudioFilesystemState(audio)
            }
        }

    fun observeAllBookAudio(): Flow<List<BookAudioEntity>> =
        dao.observeAllBookAudio().onEach { rows ->
            rows.forEach { audio ->
                repairBookAudioFilesystemState(audio)
            }
        }

    suspend fun generatedBookAudio(
        bookId: Long,
        modelId: String,
        speakerId: Int = 0,
        pace: NeuralTtsPace = NeuralTtsPace.STANDARD,
        tone: NeuralTtsTone = NeuralTtsTone.NATURAL,
    ): BookAudioEntity? = withContext(Dispatchers.IO) {
        dao.bookAudio(bookId, modelId, speakerId, pace.speed, tone.name, AudiobookGenerationScope.FULL_BOOK.key)?.let {
            repairBookAudioFilesystemState(it)
        }
        dao.generatedBookAudio(bookId, modelId, speakerId, pace.speed, tone.name, AudiobookGenerationScope.FULL_BOOK.key)
            ?.takeIf { it.hasCompletePlayableAudiobook() }
    }

    suspend fun bestPlayableBookAudio(
        bookId: Long,
        modelId: String,
        speakerId: Int = 0,
        pace: NeuralTtsPace = NeuralTtsPace.STANDARD,
        tone: NeuralTtsTone = NeuralTtsTone.NATURAL,
    ): BookAudioEntity? = withContext(Dispatchers.IO) {
        dao.bookAudioForBook(bookId)
            .filter { audio ->
                audio.modelId == modelId &&
                    audio.speakerId == speakerId &&
                    kotlin.math.abs(audio.speed - pace.speed) < 0.001f &&
                    audio.tone == tone.name
            }
            .onEach { repairBookAudioFilesystemState(it) }
            .mapNotNull { dao.bookAudioById(it.id) }
            .bestPlayableAudiobookForProfile(
                modelId = modelId,
                speakerId = speakerId,
                speed = pace.speed,
                tone = tone.name
            )
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
            require(current.playableSegmentFiles().isNotEmpty()) {
                "Generated audiobook files are missing. Regenerate this audio."
            }
            exportAudio(current, uri)
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

    suspend fun updateBookAudioPlayback(audioId: Long, segmentIndex: Int, positionMs: Int) {
        withContext(Dispatchers.IO) {
            dao.updateBookAudioPlayback(
                id = audioId,
                segmentIndex = segmentIndex.coerceAtLeast(0),
                positionMs = positionMs.coerceAtLeast(0)
            )
        }
    }

    private suspend fun repairCompletedGeneratingAudio(
        audio: BookAudioEntity,
        reconcileIncomplete: Boolean,
    ) = withContext(Dispatchers.IO) {
        if (audio.status != BookAudioStatus.GENERATING) return@withContext
        if (audio.segmentCount <= 0) return@withContext
        val root = audio.filePath?.let(::File)
        if (root == null || !root.isDirectory) {
            if (!reconcileIncomplete) return@withContext
            markStaleGeneratingAudio(
                audio = audio,
                completedSegments = 0,
                status = BookAudioStatus.FAILED,
                error = "Generated audio files are missing. Start generation again."
            )
            return@withContext
        }
        val reusableSegments = reusableGeneratedAudiobookSegments(root, audio.segmentCount)
        if (reusableSegments < audio.segmentCount) {
            if (reconcileIncomplete) {
                markStaleGeneratingAudio(
                    audio = audio,
                    completedSegments = reusableSegments,
                    status = BookAudioStatus.CANCELED,
                    error = null
                )
            }
            return@withContext
        }
        val now = clock.millis()
        dao.upsertBookAudio(
            audio.copy(
                status = BookAudioStatus.GENERATED,
                completedSegments = audio.segmentCount,
                fileSizeBytes = root.walkTopDown().filter { it.isFile }.sumOf { it.length() },
                generatedAt = audio.generatedAt ?: now,
                updatedAt = now,
                error = null
            )
        )
    }

    private suspend fun repairBookAudioFilesystemState(audio: BookAudioEntity) = withContext(Dispatchers.IO) {
        repairCompletedGeneratingAudio(
            audio = audio,
            reconcileIncomplete = audio.updatedAt < clock.millis() - STALE_GENERATING_AUDIO_REPAIR_AGE_MS
        )
        if (audio.status == BookAudioStatus.GENERATING) return@withContext
        val expectedPlayable = audio.playableSegmentCount()
        if (expectedPlayable <= 0) return@withContext
        val verifiedPlayable = audio.playableSegmentFiles().size
        if (verifiedPlayable == expectedPlayable) return@withContext
        val now = clock.millis()
        dao.upsertBookAudio(
            audio.copy(
                status = if (verifiedPlayable > 0) BookAudioStatus.CANCELED else BookAudioStatus.FAILED,
                completedSegments = verifiedPlayable,
                fileSizeBytes = audio.filePath?.let(::File)
                    ?.takeIf { it.isDirectory }
                    ?.walkTopDown()
                    ?.filter { it.isFile }
                    ?.sumOf { it.length() }
                    ?: 0L,
                generatedAt = if (verifiedPlayable > 0) audio.generatedAt else null,
                updatedAt = now,
                error = if (verifiedPlayable > 0) null else "Generated audio files are missing. Start generation again."
            )
        )
    }

    private suspend fun markStaleGeneratingAudio(
        audio: BookAudioEntity,
        completedSegments: Int,
        status: BookAudioStatus,
        error: String?,
    ) {
        dao.upsertBookAudio(
            audio.copy(
                status = status,
                completedSegments = completedSegments.coerceIn(0, audio.segmentCount.coerceAtLeast(0)),
                fileSizeBytes = audio.filePath?.let(::File)
                    ?.takeIf { it.isDirectory }
                    ?.walkTopDown()
                    ?.filter { it.isFile }
                    ?.sumOf { it.length() }
                    ?: 0L,
                updatedAt = clock.millis(),
                error = error
            )
        )
    }

    suspend fun ensureCatalogSeeded() {
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
            modelRoot.mkdirs()
            val archive = File(modelRoot, "${spec.modelId}.tar.bz2")
            val extractDir = File(modelRoot, spec.modelId)
            updateModel(spec, NeuralTtsModelStatus.DOWNLOADING, downloadedBytes = 0, localPath = null, error = null)

            runCatching {
                downloadArchive(spec, archive)
                updateModel(
                    spec = spec,
                    status = NeuralTtsModelStatus.EXTRACTING,
                    downloadedBytes = spec.archiveBytes,
                    error = null
                )
                val sha256 = sha256(archive)
                require(sha256 == spec.sha256) { "Downloaded model checksum did not match." }
                if (extractDir.exists()) extractDir.deleteRecursively()
                extractDir.mkdirs()
                extractTarBz2(archive, extractDir)
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
                updateModel(spec, NeuralTtsModelStatus.FAILED, downloadedBytes = archive.length(), error = error.message)
            }
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
        val spec = NeuralTtsModelCatalog.requireModel(modelId)
        val model = dao.model(modelId)
        require(model?.status == NeuralTtsModelStatus.INSTALLED && !model.localPath.isNullOrBlank()) {
            "Download the neural voice before generating audiobook audio."
        }
        val prepared = NeuralTtsText.prepare(chunks.forAudiobookScope(scope)).forScope(scope)
        require(prepared.segments.isNotEmpty()) { "This book has no extractable text for audiobook generation." }

        val speed = pace.speed
        val target = audioDirectory(bookId, modelId, speakerId, pace, tone, scope)
        val existing = dao.bookAudio(bookId, modelId, speakerId, speed, tone.name, scope.key)
        if (existing?.hasCompletePlayableAudiobook() == true) {
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
            generationStartedAt = now,
            generationSessionStartCompletedSegments = reusableSegments,
            updatedAt = now,
            error = null
        )
        dao.upsertBookAudio(generating)
        val activeAudio = requireNotNull(dao.bookAudio(bookId, modelId, speakerId, speed, tone.name, scope.key)) {
            "Could not create audiobook generation record."
        }
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
        runCatching {
            if (!canResumeExistingAudio && target.exists()) target.deleteRecursively()
            target.mkdirs()
            val modelDir = requireNotNull(model.localPath)
            var runtime: TtsRuntime? = null
            var segmentsOnRuntime = 0
            try {
                var sampleRate = spec.sampleRate
                prepared.segments.forEachIndexed { index, segment ->
                    currentCoroutineContext().ensureActive()
                    if (index < reusableSegments) return@forEachIndexed
                    if (runtime == null || runtime.shouldRotateAfter(segmentsOnRuntime)) {
                        runtime?.engine?.release()
                        runtime = createOfflineTts(
                            spec = spec,
                            modelDir = modelDir,
                            tone = tone,
                            includeExperimentalWebGpu = true
                        )
                        segmentsOnRuntime = 0
                        activeProvider = runtime.provider
                        writeAudiobookManifest(
                            target = target,
                            title = bookTitle,
                            spec = spec,
                            provider = activeProvider,
                            pace = pace,
                            tone = tone,
                            scope = scope,
                            segmentCount = prepared.segments.size,
                            completedSegments = completedSegments,
                            wordCount = prepared.wordCount,
                            sampleRate = activeSampleRate,
                            status = BookAudioStatus.GENERATING,
                            error = null
                        )
                    }
                    val tts = requireNotNull(runtime)
                    dao.updateBookAudioProgress(
                        bookId = bookId,
                        modelId = modelId,
                        speakerId = speakerId,
                        speed = speed,
                        tone = tone.name,
                        scope = scope.key,
                        completedSegments = completedSegments,
                        updatedAt = clock.millis()
                    )
                    val generated = tts.engine.generateWithConfig(
                        text = segment,
                        config = generationConfig(speakerId, pace, tone)
                    )
                    require(generated.samples.isNotEmpty()) { "Neural TTS produced no audio for segment ${index + 1}." }
                    sampleRate = generated.sampleRate
                    activeSampleRate = sampleRate
                    val file = File(target, generatedAudiobookSegmentFileName(index))
                    require(generated.save(file.absolutePath)) { "Could not save generated segment ${index + 1}." }
                    completedSegments = index + 1
                    segmentsOnRuntime += 1
                    dao.updateBookAudioProgress(
                        bookId = bookId,
                        modelId = modelId,
                        speakerId = speakerId,
                        speed = speed,
                        tone = tone.name,
                        scope = scope.key,
                        completedSegments = completedSegments,
                        updatedAt = clock.millis()
                    )
                    writeAudiobookManifest(
                        target = target,
                        title = bookTitle,
                        spec = spec,
                        provider = tts.provider,
                        pace = pace,
                        tone = tone,
                        scope = scope,
                        segmentCount = prepared.segments.size,
                        completedSegments = completedSegments,
                        wordCount = prepared.wordCount,
                        sampleRate = sampleRate,
                        status = BookAudioStatus.GENERATING,
                        error = null
                    )
                }
                writeAudiobookManifest(
                    target = target,
                    title = bookTitle,
                    spec = spec,
                    provider = activeProvider,
                    pace = pace,
                    tone = tone,
                    scope = scope,
                    segmentCount = prepared.segments.size,
                    completedSegments = prepared.segments.size,
                    wordCount = prepared.wordCount,
                    sampleRate = sampleRate,
                    status = BookAudioStatus.GENERATED,
                    error = null
                )
                activeAudio.copy(
                    status = BookAudioStatus.GENERATED,
                    filePath = target.absolutePath,
                    scope = scope.key,
                    scopeLabel = scope.label,
                    segmentCount = prepared.segments.size,
                    completedSegments = prepared.segments.size,
                    wordCount = prepared.wordCount,
                    sampleRate = sampleRate,
                    fileSizeBytes = target.walkTopDown().filter { it.isFile }.sumOf { it.length() },
                    generatedAt = clock.millis(),
                    playbackSegmentIndex = activeAudio.playbackSegmentIndex,
                    playbackPositionMs = activeAudio.playbackPositionMs,
                    updatedAt = clock.millis(),
                    error = null
                )
            } finally {
                runtime?.engine?.release()
            }
        }.fold(
            onSuccess = { result ->
                dao.upsertBookAudio(result)
                result
            },
            onFailure = { error ->
                if (error is CancellationException) {
                    val canceled = activeAudio.copy(
                        status = BookAudioStatus.CANCELED,
                        filePath = target.absolutePath,
                        scope = scope.key,
                        scopeLabel = scope.label,
                        segmentCount = prepared.segments.size,
                        completedSegments = completedSegments,
                        wordCount = prepared.wordCount,
                        generationSessionStartCompletedSegments = reusableSegments,
                        fileSizeBytes = target.takeIf { it.isDirectory }?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L,
                        updatedAt = clock.millis(),
                        error = null
                    )
                    withContext(NonCancellable) {
                        writeAudiobookManifest(
                            target = target,
                            title = bookTitle,
                            spec = spec,
                            provider = activeProvider,
                            pace = pace,
                            tone = tone,
                            scope = scope,
                            segmentCount = prepared.segments.size,
                            completedSegments = completedSegments,
                            wordCount = prepared.wordCount,
                            sampleRate = activeSampleRate,
                            status = BookAudioStatus.CANCELED,
                            error = null
                        )
                        dao.upsertBookAudio(canceled)
                    }
                    throw error
                }
                val failed = activeAudio.copy(
                    status = BookAudioStatus.FAILED,
                    filePath = target.absolutePath,
                    scope = scope.key,
                    scopeLabel = scope.label,
                    segmentCount = prepared.segments.size,
                    completedSegments = completedSegments,
                    wordCount = prepared.wordCount,
                    generationSessionStartCompletedSegments = reusableSegments,
                    fileSizeBytes = target.takeIf { it.isDirectory }?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L,
                    updatedAt = clock.millis(),
                    error = error.message ?: "Neural audiobook generation failed for $bookTitle"
                )
                writeAudiobookManifest(
                    target = target,
                    title = bookTitle,
                    spec = spec,
                    provider = activeProvider,
                    pace = pace,
                    tone = tone,
                    scope = scope,
                    segmentCount = prepared.segments.size,
                    completedSegments = completedSegments,
                    wordCount = prepared.wordCount,
                    sampleRate = activeSampleRate,
                    status = BookAudioStatus.FAILED,
                    error = failed.error
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
        val output = File(previewRoot, "${modelId}-s$speakerId-${pace.name.lowercase()}-${tone.name.lowercase()}.wav")
        val modelDir = requireNotNull(model.localPath)
        val tts = createOfflineTts(
            spec = spec,
            modelDir = modelDir,
            tone = tone,
            includeExperimentalWebGpu = false
        )
        try {
            val generated = tts.engine.generateWithConfig(
                text = PREVIEW_TEXT,
                config = generationConfig(speakerId, pace, tone)
            )
            require(generated.samples.isNotEmpty()) { "Neural TTS produced no preview audio." }
            require(generated.save(output.absolutePath)) { "Could not save neural voice preview." }
            output
        } finally {
            tts.engine.release()
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
        val safeSpeed = "%.2f".format(java.util.Locale.US, pace.speed)
        return File(audioRoot, "book-$bookId/$modelId-s$speakerId-v$safeSpeed-${tone.name.lowercase()}-${scope.key.lowercase()}")
    }

    private fun exportAudio(audio: BookAudioEntity, uri: Uri) {
        val source = File(requireNotNull(audio.filePath) { "Generated audio files are missing." })
        require(source.isDirectory) { "Generated audio files are missing." }
        val segments = audio.playableSegmentFiles()
        require(segments.isNotEmpty()) { "Generated audio segments are missing." }
        val output = requireNotNull(appContext.contentResolver.openOutputStream(uri)) {
            "Could not open output file."
        }
        output.use { stream ->
            ZipOutputStream(stream).use { zip ->
                File(source, "manifest.txt")
                    .takeIf { it.isFile }
                    ?.let { manifest ->
                        zip.putNextEntry(ZipEntry(manifest.name))
                        manifest.inputStream().use { input -> input.copyTo(zip) }
                        zip.closeEntry()
                    }
                listOf(CHAPTERS_FILE, SEGMENTS_FILE)
                    .map { File(source, it) }
                    .filter { it.isFile }
                    .forEach { metadata ->
                        zip.putNextEntry(ZipEntry(metadata.name))
                        metadata.inputStream().use { input -> input.copyTo(zip) }
                        zip.closeEntry()
                    }
                segments.forEach { file ->
                    zip.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    private fun writeAudiobookManifest(
        target: File,
        title: String,
        spec: NeuralTtsModelSpec,
        provider: String?,
        pace: NeuralTtsPace,
        tone: NeuralTtsTone,
        scope: AudiobookGenerationScope,
        segmentCount: Int,
        completedSegments: Int,
        wordCount: Int,
        sampleRate: Int?,
        status: BookAudioStatus,
        error: String?,
    ) {
        target.mkdirs()
        val manifest = buildString {
            appendLine("title=$title")
            appendLine("model=${spec.displayName}")
            provider?.let { appendLine("provider=$it") }
            appendLine("gender=${spec.gender.label}")
            appendLine("tone=${tone.label}")
            appendLine("pace=${pace.label}")
            appendLine("scope=${scope.label}")
            appendLine("status=${status.name.lowercase()}")
            appendLine("segments=$segmentCount")
            appendLine("completed=$completedSegments")
            appendLine("words=$wordCount")
            sampleRate?.let { appendLine("sampleRate=$it") }
            appendLine("updatedAt=${clock.millis()}")
            error?.takeIf { it.isNotBlank() }?.let { appendLine("error=${it.lineSequence().first()}") }
        }
        File(target, IN_PROGRESS_MANIFEST).writeText(manifest)
        if (status == BookAudioStatus.GENERATED) {
            File(target, FINAL_MANIFEST).writeText(manifest)
            File(target, IN_PROGRESS_MANIFEST).delete()
        }
    }

    private fun writeAudiobookStructure(
        target: File,
        prepared: NeuralTtsPreparedBook,
    ) {
        target.mkdirs()
        File(target, CHAPTERS_FILE).writeText(
            buildString {
                appendLine("index\tfirstSegment\tsegmentCount\ttitle")
                prepared.chapters.forEach { chapter ->
                    appendLine(
                        listOf(
                            chapter.index.toString(),
                            chapter.firstSegmentIndex.toString(),
                            chapter.segmentCount.toString(),
                            chapter.title.tsvEscaped()
                        ).joinToString("\t")
                    )
                }
            }
        )
        File(target, SEGMENTS_FILE).writeText(
            buildString {
                appendLine("index\tchapterIndex\tpauseAfterMs\ttext")
                prepared.segments.forEachIndexed { index, segment ->
                    appendLine(
                        listOf(
                            index.toString(),
                            prepared.segmentChapterIndexes.getOrElse(index) { 0 }.toString(),
                            prepared.segmentPauseMillis.getOrElse(index) { DEFAULT_AUDIOBOOK_SEGMENT_PAUSE_MS }.toString(),
                            segment.tsvEscaped()
                        ).joinToString("\t")
                    )
                }
            }
        )
    }

    private fun createOfflineTts(
        spec: NeuralTtsModelSpec,
        modelDir: String,
        tone: NeuralTtsTone,
        includeExperimentalWebGpu: Boolean,
    ): TtsRuntime {
        val providers = TtsAccelerationRuntime.providerOrder(
            context = appContext,
            includeExperimentalWebGpu = includeExperimentalWebGpu
        )
        var lastError: Throwable? = null
        providers.forEach { provider ->
            runCatching {
                OfflineTts(config = ttsConfig(spec, modelDir, tone, provider))
            }.onSuccess { engine ->
                Log.i(tag, "Initialized ${spec.displayName} with provider=$provider.")
                return TtsRuntime(engine = engine, provider = provider)
            }.onFailure { error ->
                Log.w(tag, "Could not initialize ${spec.displayName} with provider=$provider.", error)
                lastError = error
            }
        }
        throw IllegalStateException("Could not initialize local neural TTS runtime.", lastError)
    }

    private fun ttsConfig(spec: NeuralTtsModelSpec, modelDir: String, tone: NeuralTtsTone, provider: String) =
        getOfflineTtsConfig(
            modelDir = modelDir,
            modelName = spec.modelFile,
            acousticModelName = "",
            vocoder = "",
            voices = spec.voicesFile,
            lexicon = spec.lexiconFile,
            dataDir = File(modelDir, spec.dataDirectory).absolutePath,
            dictDir = "",
            ruleFsts = "",
            ruleFars = "",
            numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
            provider = provider,
            isKitten = false
        ).apply {
            silenceScale = tone.silenceScale
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
        const val STALE_GENERATING_AUDIO_REPAIR_AGE_MS = 60 * 1000L
        const val PREVIEW_TEXT = "This is XReader's local neural voice preview, generated privately on this device."
        const val FINAL_MANIFEST = "manifest.txt"
        const val IN_PROGRESS_MANIFEST = "manifest.in-progress.txt"
        const val CHAPTERS_FILE = "chapters.tsv"
        const val SEGMENTS_FILE = "segments.tsv"
    }
}

private data class TtsRuntime(
    val engine: OfflineTts,
    val provider: String,
)

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

private fun TtsRuntime.shouldRotateAfter(generatedSegments: Int): Boolean =
    provider == "webgpu" && generatedSegments >= WEBGPU_SEGMENTS_PER_RUNTIME

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

internal fun String.tsvEscaped(): String =
    replace("\\", "\\\\")
        .replace("\t", "\\t")
        .replace("\r", "\\r")
        .replace("\n", "\\n")

internal fun reusableGeneratedAudiobookSegments(directory: File, expectedSegments: Int): Int {
    if (expectedSegments <= 0 || !directory.isDirectory) return 0
    var reusable = 0
    repeat(expectedSegments) { index ->
        val file = File(directory, generatedAudiobookSegmentFileName(index))
        if (!file.isFile || file.length() <= WAV_HEADER_BYTES) return reusable
        reusable = index + 1
    }
    return reusable
}

internal fun generatedAudiobookSegmentFileName(index: Int): String =
    "segment-${(index + 1).toString().padStart(5, '0')}.wav"

internal fun deleteGeneratedAudiobookFiles(audio: BookAudioEntity): Boolean {
    val path = audio.filePath?.takeIf { it.isNotBlank() } ?: return false
    val target = File(path)
    return when {
        target.isDirectory -> target.deleteRecursively()
        target.isFile -> target.delete()
        else -> false
    }
}

private const val WEBGPU_SEGMENTS_PER_RUNTIME = 32
