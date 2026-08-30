package com.xreader.app.audiobook

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.xreader.app.data.AudiobookChapterEntity
import com.xreader.app.data.AudiobookTrackEntity
import com.xreader.app.data.ImportedAudiobookEntity
import com.xreader.app.data.XReaderDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.time.Clock
import java.util.Locale
import java.util.UUID

class AudiobookImportService(
    context: Context,
    private val database: XReaderDatabase,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val appContext = context.applicationContext

    data class ImportResult(
        val audiobookId: Long,
        val title: String,
        val imported: Boolean,
        val restoredMissingFiles: Boolean,
        val trackCount: Int,
    )

    suspend fun importUris(uris: List<Uri>): ImportResult = withContext(Dispatchers.IO) {
        require(uris.isNotEmpty()) { "Select at least one audiobook file." }
        require(uris.size <= MAX_TRACKS) { "An audiobook can contain at most $MAX_TRACKS tracks." }
        val staging = File(appContext.cacheDir, "audiobook-import/${UUID.randomUUID()}")
        require(staging.mkdirs()) { "Could not prepare audiobook import." }
        try {
            val files = uris.mapIndexed { index, uri -> copyUriToStaging(uri, staging, index) }
            importStagedFiles(files)
        } finally {
            staging.deleteRecursively()
        }
    }

    suspend fun importFiles(files: List<File>): ImportResult = withContext(Dispatchers.IO) {
        require(files.isNotEmpty()) { "Select at least one audiobook file." }
        require(files.size <= MAX_TRACKS) { "An audiobook can contain at most $MAX_TRACKS tracks." }
        files.forEach { require(it.isFile) { "Audiobook track is missing: ${it.name}" } }
        importStagedFiles(files)
    }

    private suspend fun importStagedFiles(sourceFiles: List<File>): ImportResult {
        val inspected = sourceFiles.map(::inspectTrack).sortedWith(trackComparator)
        val totalBytes = inspected.sumOf { it.file.length() }
        require(totalBytes in 1..MAX_AUDIOBOOK_BYTES) { "Audiobook exceeds the ${MAX_AUDIOBOOK_BYTES / GIB} GiB import limit." }
        val checksum = publicationChecksum(inspected)
        val existing = database.audiobooks().audiobookByChecksum(checksum)
        if (existing != null && existingFilesAreComplete(existing.id, existing.filePath)) {
            return ImportResult(existing.id, existing.title, imported = false, restoredMissingFiles = false, existing.trackCount)
        }

        val target = File(appContext.filesDir, "audiobooks/$checksum")
        val temporaryTarget = File(target.parentFile, ".$checksum-${UUID.randomUUID()}.importing")
        val previousTarget = File(target.parentFile, ".$checksum-${UUID.randomUUID()}.previous")
        require(temporaryTarget.mkdirs()) { "Could not create private audiobook storage." }
        var targetFinalized = false
        try {
            val copied = inspected.mapIndexed { index, track ->
                val extension = track.file.extension.lowercase().ifBlank { "audio" }
                val targetFile = File(temporaryTarget, "%04d.%s".format(Locale.US, index + 1, extension))
                track.file.inputStream().buffered().use { input ->
                    targetFile.outputStream().buffered().use { output -> input.copyTo(output) }
                }
                require(targetFile.length() == track.file.length()) { "Audiobook track copy was incomplete." }
                track.copy(file = targetFile)
            }
            val coverPath = copied.firstNotNullOfOrNull { it.picture }?.let { bytes ->
                File(temporaryTarget, "cover.jpg").also { it.writeBytes(bytes) }.absolutePath
            }
            if (target.exists()) require(target.renameTo(previousTarget)) { "Could not preserve the existing audiobook during repair." }
            require(temporaryTarget.renameTo(target)) { "Could not finalize private audiobook storage." }
            targetFinalized = true
            val finalizedTracks = copied.map { track -> track.copy(file = File(target, track.file.name)) }
            val first = finalizedTracks.first()
            val now = clock.millis()
            val title = commonMetadata(finalizedTracks.map { it.album }).orEmpty()
                .ifBlank { commonMetadata(finalizedTracks.map { it.title }).orEmpty() }
                .ifBlank { sourceFiles.commonParentTitle() }
            val author = commonMetadata(finalizedTracks.map { it.artist }).orEmpty().ifBlank { "Unknown author" }
            val narrator = commonMetadata(finalizedTracks.map { it.writer })
            val entity = ImportedAudiobookEntity(
                id = existing?.id ?: 0,
                title = title.cleanMetadata(),
                author = author.cleanMetadata(),
                sortTitle = title.sortTitle(),
                narrator = narrator?.cleanMetadata(),
                series = commonMetadata(finalizedTracks.map { it.album })?.takeIf { it != title },
                checksum = checksum,
                fileName = if (finalizedTracks.size == 1) sourceFiles.first().name else "$title (${finalizedTracks.size} tracks)",
                filePath = target.absolutePath,
                sourceExtension = first.file.extension.lowercase(),
                mimeType = first.mimeType,
                coverImagePath = coverPath?.replace(temporaryTarget.absolutePath, target.absolutePath),
                durationMs = finalizedTracks.sumOf { it.durationMs },
                fileSizeBytes = finalizedTracks.sumOf { it.file.length() },
                trackCount = finalizedTracks.size,
                linkedBookId = existing?.linkedBookId,
                playbackTrackIndex = existing?.playbackTrackIndex ?: 0,
                playbackPositionMs = existing?.playbackPositionMs ?: 0,
                playbackSpeed = existing?.playbackSpeed ?: 1f,
                importedAt = existing?.importedAt ?: now,
                updatedAt = now,
                lastPlayedAt = existing?.lastPlayedAt,
                favorite = existing?.favorite ?: false,
                finished = existing?.finished ?: false,
            )
            val id = database.withTransaction {
                val audiobookId = if (existing == null) database.audiobooks().insertAudiobook(entity) else {
                    database.audiobooks().updateAudiobook(entity)
                    existing.id
                }
                val trackRows = finalizedTracks.mapIndexed { index, track ->
                    AudiobookTrackEntity(
                        audiobookId = audiobookId,
                        trackIndex = index,
                        title = track.title.cleanMetadata().ifBlank { track.file.nameWithoutExtension.cleanMetadata() },
                        fileName = track.file.name,
                        relativePath = track.file.name,
                        checksum = track.checksum,
                        mimeType = track.mimeType,
                        durationMs = track.durationMs,
                        fileSizeBytes = track.file.length(),
                        discNumber = track.discNumber,
                        trackNumber = track.trackNumber,
                    )
                }
                database.audiobooks().deleteTracks(audiobookId)
                database.audiobooks().deleteChapters(audiobookId)
                database.audiobooks().insertTracks(trackRows)
                val chapterRows = finalizedTracks.flatMapIndexed { trackIndex, track ->
                    val parsed = AudiobookChapterParser.parse(track.file, track.durationMs)
                    (parsed.ifEmpty {
                        listOf(ParsedAudiobookChapter(track.title.ifBlank { track.file.nameWithoutExtension }, 0, track.durationMs))
                    }).map { trackIndex to it }
                }.mapIndexed { chapterIndex, (trackIndex, chapter) ->
                    AudiobookChapterEntity(
                        audiobookId = audiobookId,
                        chapterIndex = chapterIndex,
                        title = chapter.title.cleanMetadata().ifBlank { "Track ${trackIndex + 1}" },
                        trackIndex = trackIndex,
                        startMs = chapter.startMs.coerceAtLeast(0),
                        endMs = chapter.endMs.coerceAtLeast(chapter.startMs),
                    )
                }
                database.audiobooks().insertChapters(chapterRows)
                audiobookId
            }
            previousTarget.deleteRecursively()
            return ImportResult(id, entity.title, imported = existing == null, restoredMissingFiles = existing != null, entity.trackCount)
        } catch (error: Throwable) {
            temporaryTarget.deleteRecursively()
            if (targetFinalized) target.deleteRecursively()
            if (previousTarget.exists() && !target.exists()) previousTarget.renameTo(target)
            throw error
        }
    }

    private fun copyUriToStaging(uri: Uri, staging: File, index: Int): File {
        val displayName = appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: "track-${index + 1}"
        val mimeType = appContext.contentResolver.getType(uri).orEmpty()
        require(SupportedAudiobookTypes.isSupported(displayName, mimeType)) { "Unsupported audiobook file: $displayName" }
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(180)
        val target = File(staging, "%04d-%s".format(Locale.US, index, safeName))
        appContext.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open $displayName." }
            target.outputStream().buffered().use { output ->
                val copied = input.copyTo(output)
                require(copied in 1..MAX_TRACK_BYTES) { "$displayName exceeds the per-track import limit." }
            }
        }
        return target
    }

    private fun inspectTrack(file: File): InspectedTrack {
        val extension = file.extension.lowercase()
        require(extension in SupportedAudiobookTypes.extensions) { "Unsupported audiobook file: ${file.name}" }
        require(file.length() in 1..MAX_TRACK_BYTES) { "Audiobook track is empty or too large: ${file.name}" }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            require(hasAudio == null || hasAudio.equals("yes", true) || hasAudio == "1") { "File contains no playable audio: ${file.name}" }
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            require(duration > 0L) { "Could not determine audio duration: ${file.name}" }
            return InspectedTrack(
                file = file,
                checksum = sha256(file),
                mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                    ?: SupportedAudiobookTypes.mimeTypes.firstOrNull { SupportedAudiobookTypes.extensionForMimeType(it) == extension }
                    ?: "audio/$extension",
                durationMs = duration,
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty(),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                writer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER),
                discNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)?.leadingNumber(),
                trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.leadingNumber(),
                picture = runCatching { retriever.embeddedPicture }.getOrNull()?.takeIf { it.size <= MAX_COVER_BYTES },
            )
        } catch (error: RuntimeException) {
            throw IllegalArgumentException("Could not read audiobook track ${file.name}: ${error.message}", error)
        } finally {
            retriever.release()
        }
    }

    private fun publicationChecksum(tracks: List<InspectedTrack>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        tracks.forEach { track ->
            digest.update(track.checksum.toByteArray(Charsets.US_ASCII))
            digest.update(0)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class InspectedTrack(
        val file: File,
        val checksum: String,
        val mimeType: String,
        val durationMs: Long,
        val title: String,
        val album: String?,
        val artist: String?,
        val writer: String?,
        val discNumber: Int?,
        val trackNumber: Int?,
        val picture: ByteArray?,
    )

    companion object {
        private const val GIB = 1024L * 1024L * 1024L
        private const val MAX_TRACKS = 2_000
        private const val MAX_TRACK_BYTES = 4L * GIB
        private const val MAX_AUDIOBOOK_BYTES = 32L * GIB
        private const val MAX_COVER_BYTES = 20 * 1024 * 1024
        private val trackComparator = compareBy<InspectedTrack>({ it.discNumber ?: Int.MAX_VALUE }, { it.trackNumber ?: Int.MAX_VALUE }, { naturalSortKey(it.file.name) })

        private fun naturalSortKey(value: String): String = value.lowercase()
            .replace(Regex("^\\d{4}-"), "")
            .replace(Regex("\\d+")) { match -> match.value.padStart(12, '0') }
        private fun String.leadingNumber(): Int? = substringBefore('/').trim().toIntOrNull()
        private fun String.cleanMetadata(): String = replace(Regex("[\\p{Cntrl}&&[^\\n\\t]]"), " ").replace(Regex("\\s+"), " ").trim().take(512)
        private fun String.sortTitle(): String = cleanMetadata().lowercase().removePrefix("the ").removePrefix("a ").removePrefix("an ")
        private fun List<File>.commonParentTitle(): String {
            val parent = first().parentFile?.name?.cleanMetadata().orEmpty()
            if (parent.isNotBlank() && !UUID_REGEX.matches(parent)) return parent
            val names = map { it.nameWithoutExtension.replace(Regex("^\\d{4}-"), "").cleanMetadata() }
            val commonPrefix = names.reduceOrNull { left, right -> left.commonPrefixWith(right) }
                ?.trim(' ', '-', '_', '.', '(', '[', '{')
                ?.takeIf { it.length >= 3 }
            return commonPrefix ?: names.first()
        }
        private fun commonMetadata(values: List<String?>): String? = values.filterNotNull().map { it.cleanMetadata() }.filter { it.isNotBlank() }.distinct().singleOrNull()
        private val UUID_REGEX = Regex("[0-9a-fA-F-]{36}")
    }

    private suspend fun existingFilesAreComplete(audiobookId: Long, filePath: String): Boolean {
        val directory = File(filePath)
        if (!directory.isDirectory) return false
        val stored = database.audiobooks().audiobook(audiobookId) ?: return false
        if (stored.tracks.isEmpty() || stored.tracks.size != stored.audiobook.trackCount) return false
        return stored.tracks.all { track ->
            val file = File(directory, track.relativePath)
            file.isFile && file.length() == track.fileSizeBytes && sha256(file).equals(track.checksum, ignoreCase = true)
        }
    }
}
