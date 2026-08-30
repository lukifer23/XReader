package com.xreader.app.repository

import com.xreader.app.data.AudiobookBookmarkEntity
import com.xreader.app.data.AudiobookChapterEntity
import com.xreader.app.data.AudiobookCollectionEntity
import com.xreader.app.data.AudiobookTrackEntity
import com.xreader.app.data.CollectionEntity
import com.xreader.app.data.ImportedAudiobookEntity
import com.xreader.app.data.NarrationOverrideEntity
import com.xreader.app.data.PronunciationRuleEntity
import com.xreader.app.data.XReaderDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.time.Clock

internal data class AudiobookBackupPlan(val records: List<AudiobookBackupRecord>)
internal data class AudiobookBackupRecord(
    val checksum: String,
    val title: String,
    val author: String,
    val sortTitle: String,
    val narrator: String?,
    val series: String?,
    val seriesIndex: Double?,
    val description: String?,
    val language: String?,
    val isbn: String?,
    val fileName: String,
    val sourceExtension: String,
    val mimeType: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val linkedBookChecksum: String?,
    val playbackTrackIndex: Int,
    val playbackPositionMs: Int,
    val playbackSpeed: Float,
    val importedAt: Long,
    val updatedAt: Long,
    val lastPlayedAt: Long?,
    val favorite: Boolean,
    val finished: Boolean,
    val tracks: List<BackupTrack>,
    val chapters: List<BackupChapter>,
    val bookmarks: List<BackupAudioBookmark>,
    val collections: List<String>,
)
internal data class BackupTrack(val index: Int, val title: String, val fileName: String, val checksum: String, val mimeType: String, val durationMs: Long, val fileSizeBytes: Long, val disc: Int?, val track: Int?)
internal data class BackupChapter(val index: Int, val title: String, val trackIndex: Int, val startMs: Long, val endMs: Long)
internal data class BackupAudioBookmark(val trackIndex: Int, val positionMs: Long, val label: String, val note: String, val createdAt: Long, val updatedAt: Long)

internal data class NarrationBackupPlan(
    val pronunciationRules: List<BackupPronunciationRule>,
    val overrides: List<BackupNarrationOverride>,
)
internal data class BackupPronunciationRule(val bookChecksum: String?, val languageTag: String, val phrase: String, val replacement: String, val enabled: Boolean, val createdAt: Long, val updatedAt: Long)
internal data class BackupNarrationOverride(val bookChecksum: String, val sourceKey: String, val include: Boolean, val replacementText: String?, val createdAt: Long, val updatedAt: Long)

data class ExtendedImportResult(
    val audiobooksImported: Int = 0,
    val audiobooksUpdated: Int = 0,
    val audiobooksSkipped: Int = 0,
    val audioBookmarksImported: Int = 0,
    val missingAudioFiles: Int = 0,
    val missingBooks: Int = 0,
    val invalidItems: Int = 0,
    val pronunciationRulesImported: Int = 0,
    val narrationOverridesImported: Int = 0,
)

internal suspend fun XReaderDatabase.exportAudiobookBackupJson(): String {
    val books = books().booksForBackup().associateBy { it.id }
    val audio = audiobooks().allAudiobooks()
    val tracks = audiobooks().allTracks().groupBy { it.audiobookId }
    val chapters = audiobooks().allChapters().groupBy { it.audiobookId }
    val bookmarks = audiobooks().allBookmarks().groupBy { it.audiobookId }
    val memberships = audiobooks().allCollectionMemberships().groupBy { it.audiobookId }
    val collections = collections().allCollections().associateBy { it.id }
    return JSONObject()
        .put("format", "com.xreader.audiobooks-backup.v1")
        .put("version", 1)
        .put("audiobooks", JSONArray().apply {
            audio.forEach { item ->
                put(JSONObject()
                    .put("checksum", item.checksum)
                    .put("title", item.title)
                    .put("author", item.author)
                    .put("sortTitle", item.sortTitle)
                    .putNullable("narrator", item.narrator)
                    .putNullable("series", item.series)
                    .putNullable("seriesIndex", item.seriesIndex)
                    .putNullable("description", item.description)
                    .putNullable("language", item.language)
                    .putNullable("isbn", item.isbn)
                    .put("fileName", item.fileName)
                    .put("sourceExtension", item.sourceExtension)
                    .put("mimeType", item.mimeType)
                    .put("durationMs", item.durationMs)
                    .put("fileSizeBytes", item.fileSizeBytes)
                    .putNullable("linkedBookChecksum", item.linkedBookId?.let { books[it]?.checksum })
                    .put("playbackTrackIndex", item.playbackTrackIndex)
                    .put("playbackPositionMs", item.playbackPositionMs)
                    .put("playbackSpeed", item.playbackSpeed.toDouble())
                    .put("importedAt", item.importedAt)
                    .put("updatedAt", item.updatedAt)
                    .putNullable("lastPlayedAt", item.lastPlayedAt)
                    .put("favorite", item.favorite)
                    .put("finished", item.finished)
                    .put("tracks", JSONArray().apply { tracks[item.id].orEmpty().forEach { put(it.toBackupJson()) } })
                    .put("chapters", JSONArray().apply { chapters[item.id].orEmpty().forEach { put(it.toBackupJson()) } })
                    .put("bookmarks", JSONArray().apply { bookmarks[item.id].orEmpty().forEach { put(it.toBackupJson()) } })
                    .put("collections", JSONArray().apply {
                        memberships[item.id].orEmpty().mapNotNull { collections[it.collectionId]?.name }.forEach(::put)
                    }))
            }
        }).toString()
}

internal suspend fun XReaderDatabase.exportNarrationBackupJson(): String {
    val books = books().booksForBackup().associateBy { it.id }
    return JSONObject()
        .put("format", "com.xreader.narration-backup.v1")
        .put("version", 1)
        .put("pronunciationRules", JSONArray().apply {
            narration().allPronunciationRules().forEach { rule ->
                put(JSONObject()
                    .putNullable("bookChecksum", rule.bookId?.let { books[it]?.checksum })
                    .put("languageTag", rule.languageTag)
                    .put("phrase", rule.phrase)
                    .put("replacement", rule.replacement)
                    .put("enabled", rule.enabled)
                    .put("createdAt", rule.createdAt)
                    .put("updatedAt", rule.updatedAt))
            }
        })
        .put("overrides", JSONArray().apply {
            narration().allNarrationOverrides().forEach { override ->
                books[override.bookId]?.checksum?.let { checksum ->
                    put(JSONObject()
                        .put("bookChecksum", checksum)
                        .put("sourceKey", override.sourceKey)
                        .put("include", override.include)
                        .putNullable("replacementText", override.replacementText)
                        .put("createdAt", override.createdAt)
                        .put("updatedAt", override.updatedAt))
                }
            }
        }).toString()
}

internal fun parseAudiobookBackup(json: String): AudiobookBackupPlan {
    val root = JSONObject(json)
    require(root.optString("format") == "com.xreader.audiobooks-backup.v1") { "Audiobook backup section is invalid." }
    val array = root.optJSONArray("audiobooks") ?: JSONArray()
    return AudiobookBackupPlan((0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        val checksum = item.getString("checksum").normalizedBackupChecksum() ?: error("Audiobook checksum is invalid.")
        val tracks = item.optJSONArray("tracks") ?: JSONArray()
        val chapters = item.optJSONArray("chapters") ?: JSONArray()
        val bookmarks = item.optJSONArray("bookmarks") ?: JSONArray()
        val collections = item.optJSONArray("collections") ?: JSONArray()
        AudiobookBackupRecord(
            checksum, item.requiredText("title", 512), item.requiredText("author", 512), item.requiredText("sortTitle", 512),
            item.optionalText("narrator", 512), item.optionalText("series", 512), item.optionalDouble("seriesIndex"),
            item.optionalText("description", 20_000), item.optionalText("language", 64), item.optionalText("isbn", 64),
            item.requiredText("fileName", 512), item.requiredText("sourceExtension", 16), item.requiredText("mimeType", 128),
            item.optLong("durationMs").coerceAtLeast(0), item.optLong("fileSizeBytes").coerceAtLeast(0),
            item.optionalText("linkedBookChecksum", 64)?.normalizedBackupChecksum(), item.optInt("playbackTrackIndex").coerceAtLeast(0),
            item.optInt("playbackPositionMs").coerceAtLeast(0), item.optDouble("playbackSpeed", 1.0).toFloat().coerceIn(.5f, 3f),
            item.optLong("importedAt").coerceAtLeast(0), item.optLong("updatedAt").coerceAtLeast(0), item.optionalLong("lastPlayedAt"),
            item.optBoolean("favorite"), item.optBoolean("finished"),
            (0 until tracks.length()).map { trackIndex -> tracks.getJSONObject(trackIndex).toBackupTrack() },
            (0 until chapters.length()).map { chapterIndex -> chapters.getJSONObject(chapterIndex).toBackupChapter() },
            (0 until bookmarks.length()).map { bookmarkIndex -> bookmarks.getJSONObject(bookmarkIndex).toBackupBookmark() },
            (0 until collections.length()).map { collectionIndex -> collections.getString(collectionIndex).trim().take(200) }.filter { it.isNotBlank() },
        )
    })
}

internal fun parseNarrationBackup(json: String): NarrationBackupPlan {
    val root = JSONObject(json)
    require(root.optString("format") == "com.xreader.narration-backup.v1") { "Narration backup section is invalid." }
    val rules = root.optJSONArray("pronunciationRules") ?: JSONArray()
    val overrides = root.optJSONArray("overrides") ?: JSONArray()
    return NarrationBackupPlan(
        pronunciationRules = (0 until rules.length()).map { index ->
            val item = rules.getJSONObject(index)
            BackupPronunciationRule(
                item.optionalText("bookChecksum", 64)?.normalizedBackupChecksum(), item.requiredText("languageTag", 64),
                item.requiredText("phrase", 500), item.requiredText("replacement", 500), item.optBoolean("enabled", true),
                item.optLong("createdAt").coerceAtLeast(0), item.optLong("updatedAt").coerceAtLeast(0),
            )
        },
        overrides = (0 until overrides.length()).map { index ->
            val item = overrides.getJSONObject(index)
            BackupNarrationOverride(
                item.requiredText("bookChecksum", 64).normalizedBackupChecksum() ?: error("Narration book checksum is invalid."),
                item.requiredText("sourceKey", 128), item.getBoolean("include"), item.optionalText("replacementText", 20_000),
                item.optLong("createdAt").coerceAtLeast(0), item.optLong("updatedAt").coerceAtLeast(0),
            )
        },
    )
}

internal suspend fun XReaderDatabase.applyAudiobookBackup(plan: AudiobookBackupPlan, clock: Clock): ExtendedImportResult {
    val booksByChecksum = books().booksForBackup().associateBy { it.checksum.lowercase() }
    var imported = 0
    var updated = 0
    var skipped = 0
    var bookmarkCount = 0
    var missingFiles = 0
    var missingBooks = 0
    for (record in plan.records) {
        val existing = audiobooks().audiobookByChecksum(record.checksum)
        val existingPackage = existing?.let { audiobooks().audiobook(it.id) }
        val linkedBookId = record.linkedBookChecksum?.let { booksByChecksum[it]?.id }.also {
            if (record.linkedBookChecksum != null && it == null) missingBooks++
        }
        val now = clock.millis()
        val entity = ImportedAudiobookEntity(
            id = existing?.id ?: 0, title = record.title, author = record.author, sortTitle = record.sortTitle,
            narrator = record.narrator, series = record.series, seriesIndex = record.seriesIndex, description = record.description,
            language = record.language, isbn = record.isbn, checksum = record.checksum, fileName = record.fileName,
            filePath = existing?.filePath.orEmpty(), sourceExtension = record.sourceExtension, mimeType = record.mimeType,
            coverImagePath = existing?.coverImagePath, durationMs = record.durationMs, fileSizeBytes = record.fileSizeBytes,
            trackCount = record.tracks.size, linkedBookId = linkedBookId ?: existing?.linkedBookId,
            playbackTrackIndex = record.playbackTrackIndex, playbackPositionMs = record.playbackPositionMs,
            playbackSpeed = record.playbackSpeed, importedAt = existing?.importedAt ?: record.importedAt,
            updatedAt = maxOf(record.updatedAt, now), lastPlayedAt = record.lastPlayedAt,
            favorite = record.favorite, finished = record.finished,
        )
        val id = if (existing == null) {
            missingFiles++
            imported++
            audiobooks().insertAudiobook(entity)
        } else if (existing.copy(id = 0, filePath = "", coverImagePath = null, updatedAt = entity.updatedAt) == entity.copy(id = 0, filePath = "", coverImagePath = null)) {
            skipped++
            existing.id
        } else {
            updated++
            audiobooks().updateAudiobook(entity)
            existing.id
        }
        val trackRows = record.tracks.map { AudiobookTrackEntity(id, it.index, it.title, it.fileName, it.fileName, it.checksum, it.mimeType, it.durationMs, it.fileSizeBytes, it.disc, it.track) }
        val chapterRows = record.chapters.map { AudiobookChapterEntity(id, it.index, it.title, it.trackIndex, it.startMs, it.endMs) }
        if (existingPackage == null || existingPackage.tracks.sortedBy { it.trackIndex } != trackRows || existingPackage.chapters.sortedBy { it.chapterIndex } != chapterRows) {
            audiobooks().deleteTracks(id)
            audiobooks().deleteChapters(id)
            audiobooks().insertTracks(trackRows)
            audiobooks().insertChapters(chapterRows)
        }
        record.bookmarks.forEach { bookmark ->
            if (audiobooks().insertBookmark(AudiobookBookmarkEntity(0, id, bookmark.trackIndex, bookmark.positionMs, bookmark.label, bookmark.note, bookmark.createdAt, bookmark.updatedAt)) > 0) bookmarkCount++
        }
        record.collections.forEach { name ->
            val collectionId = collections().collectionByName(name)?.id
                ?: collections().insertCollection(CollectionEntity(name = name, createdAt = now, updatedAt = now))
            audiobooks().insertCollectionMembership(AudiobookCollectionEntity(id, collectionId, now))
        }
    }
    return ExtendedImportResult(imported, updated, skipped, bookmarkCount, missingFiles, missingBooks)
}

internal suspend fun XReaderDatabase.applyNarrationBackup(plan: NarrationBackupPlan): ExtendedImportResult {
    val booksByChecksum = books().booksForBackup().associateBy { it.checksum.lowercase() }
    var rules = 0
    var overrides = 0
    var missingBooks = 0
    plan.pronunciationRules.forEach { rule ->
        val bookId = rule.bookChecksum?.let { booksByChecksum[it]?.id }
        if (rule.bookChecksum != null && bookId == null) {
            missingBooks++
        } else {
            val existing = narration().pronunciationRule(bookId, rule.languageTag, rule.phrase)
            narration().upsertPronunciationRule(
                PronunciationRuleEntity(existing?.id ?: 0, bookId, rule.languageTag, rule.phrase, rule.replacement, rule.enabled, existing?.createdAt ?: rule.createdAt, maxOf(existing?.updatedAt ?: 0, rule.updatedAt))
            )
            if (existing == null || existing.replacement != rule.replacement || existing.enabled != rule.enabled) rules++
        }
    }
    plan.overrides.forEach { override ->
        val bookId = booksByChecksum[override.bookChecksum]?.id
        if (bookId == null) {
            missingBooks++
        } else {
            val existing = narration().narrationOverride(bookId, override.sourceKey)
            narration().upsertNarrationOverride(
                NarrationOverrideEntity(existing?.id ?: 0, bookId, override.sourceKey, override.include, override.replacementText, existing?.createdAt ?: override.createdAt, maxOf(existing?.updatedAt ?: 0, override.updatedAt))
            )
            if (existing == null || existing.include != override.include || existing.replacementText != override.replacementText) overrides++
        }
    }
    return ExtendedImportResult(missingBooks = missingBooks, pronunciationRulesImported = rules, narrationOverridesImported = overrides)
}

private fun AudiobookTrackEntity.toBackupJson(): JSONObject = JSONObject().put("index", trackIndex).put("title", title).put("fileName", fileName).put("checksum", checksum).put("mimeType", mimeType).put("durationMs", durationMs).put("fileSizeBytes", fileSizeBytes).putNullable("discNumber", discNumber).putNullable("trackNumber", trackNumber)
private fun AudiobookChapterEntity.toBackupJson(): JSONObject = JSONObject().put("index", chapterIndex).put("title", title).put("trackIndex", trackIndex).put("startMs", startMs).put("endMs", endMs)
private fun AudiobookBookmarkEntity.toBackupJson(): JSONObject = JSONObject().put("trackIndex", trackIndex).put("positionMs", positionMs).put("label", label).put("note", note).put("createdAt", createdAt).put("updatedAt", updatedAt)
private fun JSONObject.toBackupTrack() = BackupTrack(optInt("index").coerceAtLeast(0), requiredText("title", 512), requiredText("fileName", 512), requiredText("checksum", 64).normalizedBackupChecksum() ?: error("Track checksum is invalid."), requiredText("mimeType", 128), optLong("durationMs").coerceAtLeast(0), optLong("fileSizeBytes").coerceAtLeast(0), optionalInt("discNumber"), optionalInt("trackNumber"))
private fun JSONObject.toBackupChapter() = BackupChapter(optInt("index").coerceAtLeast(0), requiredText("title", 512), optInt("trackIndex").coerceAtLeast(0), optLong("startMs").coerceAtLeast(0), optLong("endMs").coerceAtLeast(0))
private fun JSONObject.toBackupBookmark() = BackupAudioBookmark(optInt("trackIndex").coerceAtLeast(0), optLong("positionMs").coerceAtLeast(0), optString("label").take(200), optString("note").take(10_000), optLong("createdAt").coerceAtLeast(0), optLong("updatedAt").coerceAtLeast(0))
private fun JSONObject.requiredText(name: String, max: Int): String = getString(name).trim().take(max).also { require(it.isNotBlank()) { "$name is required." } }
private fun JSONObject.optionalText(name: String, max: Int): String? = if (!has(name) || isNull(name)) null else optString(name).trim().take(max).takeIf { it.isNotBlank() }
private fun JSONObject.optionalLong(name: String): Long? = if (!has(name) || isNull(name)) null else getLong(name)
private fun JSONObject.optionalInt(name: String): Int? = if (!has(name) || isNull(name)) null else getInt(name)
private fun JSONObject.optionalDouble(name: String): Double? = if (!has(name) || isNull(name)) null else getDouble(name)
private fun JSONObject.putNullable(name: String, value: Any?): JSONObject = put(name, value ?: JSONObject.NULL)
