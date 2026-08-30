package com.xreader.app.repository

import androidx.room.withTransaction
import com.xreader.app.data.XReaderDatabase
import org.json.JSONObject
import java.time.Clock

class FullBackupRepository(
    private val database: XReaderDatabase,
    private val library: LibraryBackupRepository,
    private val annotations: AnnotationRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    data class ExportResult(
        val json: String,
        val library: LibraryBackupRepository.ExportResult,
        val annotations: AnnotationRepository.BackupExportResult,
    )

    data class ImportResult(
        val library: LibraryBackupRepository.ImportResult,
        val annotations: AnnotationRepository.BackupImportResult,
    )

    suspend fun exportBackupJson(): ExportResult {
        val libraryResult = library.exportBackupJson()
        val annotationResult = annotations.exportBackupJson()
        val json = FullBackupEnvelope.create(
            exportedAt = clock.millis(),
            libraryJson = libraryResult.json,
            annotationJson = annotationResult.json,
        )
        return ExportResult(json, libraryResult, annotationResult)
    }

    suspend fun importBackupJson(json: String): ImportResult {
        val payload = FullBackupEnvelope.parse(json)

        return database.withTransaction {
            ImportResult(
                library = library.importBackupJson(payload.libraryJson),
                annotations = annotations.importBackupJson(payload.annotationJson),
            )
        }
    }

    companion object {
        const val FORMAT = "com.xreader.full-backup"
        const val VERSION = 1
    }
}

internal object FullBackupEnvelope {
    data class Payload(val libraryJson: String, val annotationJson: String)

    fun create(exportedAt: Long, libraryJson: String, annotationJson: String): String =
        JSONObject()
            .put("format", FullBackupRepository.FORMAT)
            .put("version", FullBackupRepository.VERSION)
            .put("exportedAt", exportedAt)
            .put("library", JSONObject(libraryJson))
            .put("annotations", JSONObject(annotationJson))
            .toString(2)

    fun parse(json: String): Payload {
        val root = JSONObject(json)
        require(root.optString("format") == FullBackupRepository.FORMAT) { "This is not an XReader full backup." }
        require(root.optInt("version", 0) in 1..FullBackupRepository.VERSION) { "Unsupported XReader full backup version." }
        val library = requireNotNull(root.optJSONObject("library")) { "Full backup is missing library data." }
        val annotations = requireNotNull(root.optJSONObject("annotations")) { "Full backup is missing notes and bookmarks." }
        require(library.optString("format") == LIBRARY_FORMAT) { "Full backup contains invalid library data." }
        require(annotations.optString("format") == ANNOTATION_FORMAT) { "Full backup contains invalid notes data." }
        return Payload(library.toString(), annotations.toString())
    }

    private const val LIBRARY_FORMAT = "com.xreader.library-backup.v1"
    private const val ANNOTATION_FORMAT = "com.xreader.annotations.v1"
}
