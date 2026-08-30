package com.xreader.app.repository

import androidx.room.withTransaction
import com.xreader.app.BuildConfig
import com.xreader.app.data.RestoreOperationEntity
import com.xreader.app.data.XREADER_DATABASE_VERSION
import com.xreader.app.data.XReaderDatabase
import org.json.JSONObject
import java.time.Clock
import java.util.UUID

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

    data class ArchiveExportResult(
        val bytes: ByteArray,
        val library: LibraryBackupRepository.ExportResult,
        val annotations: AnnotationRepository.BackupExportResult,
        val audiobookCount: Int,
        val pronunciationRuleCount: Int,
    )

    data class ImportResult(
        val library: LibraryBackupRepository.ImportResult,
        val annotations: AnnotationRepository.BackupImportResult,
        val extended: ExtendedImportResult = ExtendedImportResult(),
    )

    internal data class RestorePlan(
        val operationId: String,
        val planSha256: String,
        val formatVersion: Int,
        val libraryJson: String,
        val annotationsJson: String,
        val settingsJson: String,
        val audiobookPlan: AudiobookBackupPlan,
        val narrationPlan: NarrationBackupPlan,
    )

    suspend fun exportBackupJson(): ExportResult {
        val libraryResult = library.exportBackupJson()
        val annotationResult = annotations.exportBackupJson()
        val json = FullBackupEnvelope.create(clock.millis(), libraryResult.json, annotationResult.json)
        return ExportResult(json, libraryResult, annotationResult)
    }

    suspend fun exportBackupArchive(): ArchiveExportResult {
        val settingsCapturedAt = clock.millis()
        lateinit var libraryResult: LibraryBackupRepository.ExportResult
        lateinit var annotationResult: AnnotationRepository.BackupExportResult
        lateinit var split: SplitLibraryBackup
        lateinit var audiobookJson: String
        lateinit var narrationJson: String
        database.withTransaction {
            libraryResult = library.exportBackupJson()
            annotationResult = annotations.exportBackupJson()
            split = splitLibraryBackup(libraryResult.json)
            audiobookJson = database.exportAudiobookBackupJson()
            narrationJson = database.exportNarrationBackupJson()
        }
        val sections = mapOf(
            "library.json" to split.libraryJson.toByteArray(Charsets.UTF_8),
            "annotations.json" to annotationResult.json.toByteArray(Charsets.UTF_8),
            "settings.json" to split.settingsJson.toByteArray(Charsets.UTF_8),
            "audiobooks.json" to audiobookJson.toByteArray(Charsets.UTF_8),
            "narration.json" to narrationJson.toByteArray(Charsets.UTF_8),
        )
        val bytes = FullBackupArchiveCodec.create(
            operationId = UUID.randomUUID().toString(),
            exportedAt = clock.millis(),
            settingsCapturedAt = settingsCapturedAt,
            schemaVersion = XREADER_DATABASE_VERSION,
            appVersion = BuildConfig.VERSION_NAME,
            sections = sections,
        )
        return ArchiveExportResult(
            bytes = bytes,
            library = libraryResult,
            annotations = annotationResult,
            audiobookCount = JSONObject(audiobookJson).optJSONArray("audiobooks")?.length() ?: 0,
            pronunciationRuleCount = JSONObject(narrationJson).optJSONArray("pronunciationRules")?.length() ?: 0,
        )
    }

    internal fun parseArchive(bytes: ByteArray): RestorePlan {
        val archive = FullBackupArchiveCodec.parse(bytes)
        val libraryJson = archive.sections.getValue("library.json").toString(Charsets.UTF_8)
        val annotationsJson = archive.sections.getValue("annotations.json").toString(Charsets.UTF_8)
        val settingsJson = archive.sections.getValue("settings.json").toString(Charsets.UTF_8)
        require(JSONObject(libraryJson).optString("format") == "com.xreader.library-backup.v1") { "Library backup section is invalid." }
        require(JSONObject(annotationsJson).optString("format") == "com.xreader.annotations.v1") { "Annotation backup section is invalid." }
        settingsAsLibraryBackup(settingsJson)
        return RestorePlan(
            operationId = archive.operationId,
            planSha256 = archive.planSha256,
            formatVersion = FullBackupArchiveCodec.VERSION,
            libraryJson = libraryJson,
            annotationsJson = annotationsJson,
            settingsJson = settingsJson,
            audiobookPlan = parseAudiobookBackup(archive.sections.getValue("audiobooks.json").toString(Charsets.UTF_8)),
            narrationPlan = parseNarrationBackup(archive.sections.getValue("narration.json").toString(Charsets.UTF_8)),
        )
    }

    suspend fun currentSettingsJson(): String = splitLibraryBackup(library.exportBackupJson().json).settingsJson

    suspend fun applySettingsJson(json: String) {
        library.importBackupJson(settingsAsLibraryBackup(json))
    }

    internal suspend fun applyRestorePlan(plan: RestorePlan): ImportResult = database.withTransaction {
        val libraryResult = library.importBackupJson(plan.libraryJson)
        val annotationResult = annotations.importBackupJson(plan.annotationsJson)
        val audioResult = database.applyAudiobookBackup(plan.audiobookPlan, clock)
        val narrationResult = database.applyNarrationBackup(plan.narrationPlan)
        database.restoreOperations().insert(
            RestoreOperationEntity(plan.operationId, plan.planSha256, plan.formatVersion, clock.millis())
        )
        ImportResult(libraryResult, annotationResult, audioResult + narrationResult)
    }

    suspend fun restoreCommitted(operationId: String): Boolean = database.restoreOperations().operation(operationId) != null

    suspend fun restoreOperation(operationId: String): RestoreOperationEntity? =
        database.restoreOperations().operation(operationId)

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

private operator fun ExtendedImportResult.plus(other: ExtendedImportResult): ExtendedImportResult = ExtendedImportResult(
    audiobooksImported + other.audiobooksImported,
    audiobooksUpdated + other.audiobooksUpdated,
    audiobooksSkipped + other.audiobooksSkipped,
    audioBookmarksImported + other.audioBookmarksImported,
    missingAudioFiles + other.missingAudioFiles,
    missingBooks + other.missingBooks,
    invalidItems + other.invalidItems,
    pronunciationRulesImported + other.pronunciationRulesImported,
    narrationOverridesImported + other.narrationOverridesImported,
)

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
        require(root.optInt("version", 0) == FullBackupRepository.VERSION) { "Unsupported XReader full backup version." }
        val library = requireNotNull(root.optJSONObject("library")) { "Full backup is missing library data." }
        val annotations = requireNotNull(root.optJSONObject("annotations")) { "Full backup is missing notes and bookmarks." }
        require(library.optString("format") == "com.xreader.library-backup.v1") { "Full backup contains invalid library data." }
        require(annotations.optString("format") == "com.xreader.annotations.v1") { "Full backup contains invalid notes data." }
        return Payload(library.toString(), annotations.toString())
    }
}
