package com.xreader.app.repository

import com.xreader.app.core.TextTools
import com.xreader.app.data.AuthorEntity
import com.xreader.app.data.BookCollectionEntity
import com.xreader.app.data.BookDao
import com.xreader.app.data.CollectionDao
import com.xreader.app.data.CollectionEntity
import com.xreader.app.data.GenreEntity
import com.xreader.app.data.ReadingDao
import com.xreader.app.data.ReadingSessionEntity
import com.xreader.app.data.ReadingStateEntity
import com.xreader.app.data.SeriesEntity
import com.xreader.app.settings.BookReaderAppearance
import com.xreader.app.settings.ReaderFontFamily
import com.xreader.app.settings.ReaderPageDirection
import com.xreader.app.settings.ReaderPdfFit
import com.xreader.app.settings.ReaderPdfScrollAxis
import com.xreader.app.settings.ReaderTextAlign
import com.xreader.app.settings.SettingsRepository
import com.xreader.app.settings.normalizedReaderFontWeight
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.Clock
import kotlin.math.max

class LibraryBackupRepository(
    private val bookDao: BookDao,
    private val collectionDao: CollectionDao,
    private val readingDao: ReadingDao,
    private val clock: Clock = Clock.systemUTC(),
    private val settingsRepository: SettingsRepository? = null,
) {
    data class ExportResult(
        val json: String,
        val books: Int,
        val collections: Int,
        val readerAppearances: Int,
        val readingStates: Int,
        val readingSessions: Int,
    )

    data class ImportResult(
        val booksUpdated: Int,
        val collectionsImported: Int,
        val collectionMembershipsImported: Int,
        val collectionMembershipsSkipped: Int,
        val readerAppearancesImported: Int,
        val readerAppearancesSkipped: Int,
        val readingStatesImported: Int,
        val readingStatesSkipped: Int,
        val readingSessionsImported: Int,
        val readingSessionsSkipped: Int,
        val missingBooks: Int,
        val invalidItems: Int,
    )

    suspend fun exportBackupJson(): ExportResult {
        val books = bookDao.booksForBackup()
        val booksById = books.associateBy { it.id }
        val collections = collectionDao.allCollections()
        val bookCollections = collectionDao.allBookCollections()
        val membershipsByCollection = bookCollections.groupBy { it.collectionId }
        val states = readingDao.allStates()
        val sessions = readingDao.allSessions()
        val readerAppearances = settingsRepository?.let { repository ->
            books.mapNotNull { book ->
                repository.bookAppearance(book.id).first()?.let { appearance -> book to appearance }
            }
        }.orEmpty()
        val root = JSONObject()
            .put("format", BACKUP_FORMAT)
            .put("version", 1)
            .put("exportedAt", clock.millis())
            .put(
                "books",
                JSONArray().also { array ->
                    books.forEach { book ->
                        array.put(
                            JSONObject()
                                .put("checksum", book.checksum)
                                .put("title", book.title)
                                .put("author", book.author)
                                .putNullable("series", book.series)
                                .putNullable("seriesIndex", book.seriesIndex)
                                .putNullable("genre", book.genre)
                                .putNullable("year", book.year)
                                .putNullable("description", book.description)
                                .putNullable("language", book.language)
                                .put("favorite", book.favorite)
                                .put("finished", book.finished)
                                .putNullable("lastOpenedAt", book.lastOpenedAt)
                                .put("updatedAt", book.updatedAt)
                        )
                    }
                }
            )
            .put(
                "collections",
                JSONArray().also { array ->
                    collections.forEach { collection ->
                        val checksums = membershipsByCollection[collection.id]
                            .orEmpty()
                            .mapNotNull { membership -> booksById[membership.bookId]?.checksum }
                            .distinct()
                        if (checksums.isNotEmpty()) {
                            array.put(
                                JSONObject()
                                    .put("name", collection.name)
                                    .put("updatedAt", collection.updatedAt)
                                    .put(
                                        "bookChecksums",
                                        JSONArray().also { checksumsArray ->
                                            checksums.forEach(checksumsArray::put)
                                        }
                                    )
                            )
                        }
                    }
                }
            )
            .put(
                "readerAppearances",
                JSONArray().also { array ->
                    readerAppearances.forEach { (book, appearance) ->
                        array.put(appearance.toJson(book.checksum))
                    }
                }
            )
            .put(
                "readingStates",
                JSONArray().also { array ->
                    states.forEach { state ->
                        val book = booksById[state.bookId] ?: return@forEach
                        array.put(
                            JSONObject()
                                .put("bookChecksum", book.checksum)
                                .put("locator", state.locator)
                                .put("progress", state.progress)
                                .put("currentUnit", state.currentUnit)
                                .put("totalUnits", state.totalUnits)
                                .put("activeMillis", state.activeMillis)
                                .put("estimatedWpm", state.estimatedWpm)
                                .put("lastReadAt", state.lastReadAt)
                                .putNullable("finishedAt", state.finishedAt)
                        )
                    }
                }
            )
            .put(
                "readingSessions",
                JSONArray().also { array ->
                    sessions.forEach { session ->
                        val book = booksById[session.bookId] ?: return@forEach
                        array.put(
                            JSONObject()
                                .put("bookChecksum", book.checksum)
                                .put("startedAt", session.startedAt)
                                .put("endedAt", session.endedAt)
                                .put("activeMillis", session.activeMillis)
                                .put("startUnit", session.startUnit)
                                .put("endUnit", session.endUnit)
                                .put("wordsRead", session.wordsRead)
                                .put("wpm", session.wpm)
                        )
                    }
                }
            )
        return ExportResult(
            json = root.toString(2),
            books = books.size,
            collections = collections.count { collection -> membershipsByCollection[collection.id].orEmpty().isNotEmpty() },
            readerAppearances = readerAppearances.size,
            readingStates = states.size,
            readingSessions = sessions.size
        )
    }

    suspend fun importBackupJson(json: String): ImportResult {
        val root = JSONObject(json)
        require(root.optString("format") == BACKUP_FORMAT) { "This is not an XReader library backup." }
        val booksByChecksum = bookDao.booksForBackup().associateBy { it.checksum }
        val missingChecksums = mutableSetOf<String>()
        var booksUpdated = 0
        var collectionsImported = 0
        var collectionMembershipsImported = 0
        var collectionMembershipsSkipped = 0
        var readerAppearancesImported = 0
        var readerAppearancesSkipped = 0
        var readingStatesImported = 0
        var readingStatesSkipped = 0
        var readingSessionsImported = 0
        var readingSessionsSkipped = 0
        var invalidItems = 0

        val books = root.optJSONArray("books") ?: JSONArray()
        for (index in 0 until books.length()) {
            val item = books.optJSONObject(index) ?: run {
                invalidItems += 1
                continue
            }
            val checksum = item.optString("checksum").takeIf { it.isNotBlank() } ?: run {
                invalidItems += 1
                continue
            }
            val existing = booksByChecksum[checksum] ?: run {
                missingChecksums += checksum
                continue
            }
            val title = item.optString("title", existing.title).trim().ifBlank { existing.title }
            val author = item.optString("author", existing.author).trim().ifBlank { existing.author }
            val metadata = existing.copy(
                title = title,
                author = author,
                sortTitle = TextTools.sortTitle(title),
                series = item.optNullableString("series")?.trim()?.ifBlank { null },
                seriesIndex = item.optNullableDouble("seriesIndex"),
                genre = item.optNullableString("genre")?.trim()?.ifBlank { null },
                year = item.optNullableInt("year"),
                description = item.optNullableString("description")?.trim()?.ifBlank { null },
                language = item.optNullableString("language")?.trim()?.ifBlank { null },
                favorite = item.optBoolean("favorite", existing.favorite),
                finished = item.optBoolean("finished", existing.finished),
                lastOpenedAt = item.optNullableLong("lastOpenedAt") ?: existing.lastOpenedAt,
                updatedAt = existing.updatedAt
            )
            if (metadata != existing) {
                val updated = metadata.copy(updatedAt = max(clock.millis(), item.optLong("updatedAt", existing.updatedAt)))
                bookDao.update(updated)
                bookDao.insertAuthor(AuthorEntity(name = updated.author))
                updated.genre?.let { bookDao.insertGenre(GenreEntity(name = it)) }
                updated.series?.let { bookDao.insertSeries(SeriesEntity(name = it)) }
                booksUpdated += 1
            }
        }

        val collections = root.optJSONArray("collections") ?: JSONArray()
        for (index in 0 until collections.length()) {
            val item = collections.optJSONObject(index) ?: run {
                invalidItems += 1
                continue
            }
            val name = runCatching { item.optString("name").cleanCollectionName() }.getOrElse {
                invalidItems += 1
                continue
            }
            val checksumArray = item.optJSONArray("bookChecksums") ?: run {
                invalidItems += 1
                continue
            }
            val matchedBooks = buildList {
                for (checksumIndex in 0 until checksumArray.length()) {
                    val checksum = checksumArray.optString(checksumIndex).takeIf { it.isNotBlank() } ?: run {
                        invalidItems += 1
                        continue
                    }
                    val book = booksByChecksum[checksum] ?: run {
                        missingChecksums += checksum
                        continue
                    }
                    add(book)
                }
            }.distinctBy { it.id }
            if (matchedBooks.isEmpty()) continue

            val now = clock.millis()
            val existing = collectionDao.collectionByName(name)
            val collectionId = existing?.id
                ?: collectionDao.insertCollection(
                    CollectionEntity(
                        name = name,
                        createdAt = now,
                        updatedAt = max(now, item.optLong("updatedAt", now))
                    )
                ).takeIf { it > 0 }
                ?: requireNotNull(collectionDao.collectionByName(name)?.id) { "Could not create collection" }
            if (existing == null) collectionsImported += 1
            matchedBooks.forEach { book ->
                val inserted = collectionDao.insertBookCollection(
                    BookCollectionEntity(
                        bookId = book.id,
                        collectionId = collectionId,
                        addedAt = now
                    )
                ) > 0
                if (inserted) {
                    collectionMembershipsImported += 1
                } else {
                    collectionMembershipsSkipped += 1
                }
            }
            collectionDao.touchCollection(collectionId, max(now, item.optLong("updatedAt", now)))
        }

        val readerAppearances = root.optJSONArray("readerAppearances") ?: JSONArray()
        for (index in 0 until readerAppearances.length()) {
            val item = readerAppearances.optJSONObject(index) ?: run {
                invalidItems += 1
                continue
            }
            val checksum = item.optString("bookChecksum").takeIf { it.isNotBlank() } ?: run {
                invalidItems += 1
                continue
            }
            val book = booksByChecksum[checksum] ?: run {
                missingChecksums += checksum
                continue
            }
            val appearance = item.toBookReaderAppearanceOrNull() ?: run {
                invalidItems += 1
                continue
            }
            val repository = settingsRepository ?: run {
                readerAppearancesSkipped += 1
                continue
            }
            if (repository.bookAppearance(book.id).first() == appearance) {
                readerAppearancesSkipped += 1
                continue
            }
            repository.setBookAppearance(book.id, appearance)
            readerAppearancesImported += 1
        }

        val states = root.optJSONArray("readingStates") ?: JSONArray()
        for (index in 0 until states.length()) {
            val item = states.optJSONObject(index) ?: run {
                invalidItems += 1
                continue
            }
            val checksum = item.optString("bookChecksum").takeIf { it.isNotBlank() } ?: run {
                invalidItems += 1
                continue
            }
            val book = booksByChecksum[checksum] ?: run {
                missingChecksums += checksum
                continue
            }
            val locator = item.optString("locator").takeIf { it.isNotBlank() } ?: run {
                invalidItems += 1
                continue
            }
            val imported = ReadingStateEntity(
                bookId = book.id,
                locator = locator,
                progress = item.optDouble("progress", 0.0).coerceIn(0.0, 1.0),
                currentUnit = item.optInt("currentUnit", 0).coerceAtLeast(0),
                totalUnits = item.optInt("totalUnits", 0).coerceAtLeast(0),
                activeMillis = item.optLong("activeMillis", 0L).coerceAtLeast(0L),
                estimatedWpm = item.optInt("estimatedWpm", 0).coerceAtLeast(0),
                lastReadAt = item.optLong("lastReadAt", clock.millis()),
                finishedAt = item.optNullableLong("finishedAt")
            )
            val existing = readingDao.getState(book.id)
            if (existing != null && existing.lastReadAt >= imported.lastReadAt) {
                readingStatesSkipped += 1
                continue
            }
            readingDao.upsertState(imported)
            readingStatesImported += 1
        }

        val sessions = root.optJSONArray("readingSessions") ?: JSONArray()
        for (index in 0 until sessions.length()) {
            val item = sessions.optJSONObject(index) ?: run {
                invalidItems += 1
                continue
            }
            val checksum = item.optString("bookChecksum").takeIf { it.isNotBlank() } ?: run {
                invalidItems += 1
                continue
            }
            val book = booksByChecksum[checksum] ?: run {
                missingChecksums += checksum
                continue
            }
            val startedAt = item.optLong("startedAt", 0L)
            val endedAt = item.optLong("endedAt", 0L)
            val startUnit = item.optInt("startUnit", 0)
            val endUnit = item.optInt("endUnit", startUnit)
            if (startedAt <= 0L || endedAt < startedAt) {
                invalidItems += 1
                continue
            }
            if (readingDao.getSessionForImport(book.id, startedAt, endedAt, startUnit, endUnit) != null) {
                readingSessionsSkipped += 1
                continue
            }
            readingDao.insertSession(
                ReadingSessionEntity(
                    bookId = book.id,
                    startedAt = startedAt,
                    endedAt = endedAt,
                    activeMillis = item.optLong("activeMillis", 0L).coerceAtLeast(0L),
                    startUnit = startUnit.coerceAtLeast(0),
                    endUnit = endUnit.coerceAtLeast(0),
                    wordsRead = item.optInt("wordsRead", 0).coerceAtLeast(0),
                    wpm = item.optInt("wpm", 0).coerceAtLeast(0)
                )
            )
            readingSessionsImported += 1
        }

        return ImportResult(
            booksUpdated = booksUpdated,
            collectionsImported = collectionsImported,
            collectionMembershipsImported = collectionMembershipsImported,
            collectionMembershipsSkipped = collectionMembershipsSkipped,
            readerAppearancesImported = readerAppearancesImported,
            readerAppearancesSkipped = readerAppearancesSkipped,
            readingStatesImported = readingStatesImported,
            readingStatesSkipped = readingStatesSkipped,
            readingSessionsImported = readingSessionsImported,
            readingSessionsSkipped = readingSessionsSkipped,
            missingBooks = missingChecksums.size,
            invalidItems = invalidItems
        )
    }

    private fun JSONObject.putNullable(name: String, value: Any?): JSONObject =
        put(name, value ?: JSONObject.NULL)

    private fun JSONObject.optNullableString(name: String): String? =
        if (!has(name) || isNull(name)) null else optString(name)

    private fun JSONObject.optNullableLong(name: String): Long? =
        if (!has(name) || isNull(name)) null else optLong(name)

    private fun JSONObject.optNullableInt(name: String): Int? =
        if (!has(name) || isNull(name)) null else optInt(name)

    private fun JSONObject.optNullableDouble(name: String): Double? =
        if (!has(name) || isNull(name)) null else optDouble(name)

    private fun BookReaderAppearance.toJson(bookChecksum: String): JSONObject =
        JSONObject()
            .put("bookChecksum", bookChecksum)
            .put("fontScale", fontScale)
            .put("lineHeight", lineHeight)
            .put("marginScale", marginScale)
            .put("fontFamily", fontFamily.name)
            .put("fontWeight", fontWeight)
            .put("hyphenation", hyphenation)
            .put("publisherStyles", publisherStyles)
            .put("textAlign", textAlign.name)
            .put("pdfFit", pdfFit.name)
            .put("pdfScrollAxis", pdfScrollAxis.name)
            .put("pageDirection", pageDirection.name)

    private fun JSONObject.toBookReaderAppearanceOrNull(): BookReaderAppearance? =
        runCatching {
            BookReaderAppearance(
                fontScale = optDouble("fontScale", 1.18).toFloat().coerceIn(0.75f, 1.65f),
                lineHeight = optDouble("lineHeight", 1.42).toFloat().coerceIn(1.1f, 2.0f),
                marginScale = optDouble("marginScale", 0.52).toFloat().coerceIn(0.35f, 1.8f),
                fontFamily = enumValueOrNull<ReaderFontFamily>("fontFamily") ?: ReaderFontFamily.DEFAULT,
                fontWeight = normalizedReaderFontWeight(optDouble("fontWeight", 1.0).toFloat()),
                hyphenation = optBoolean("hyphenation", false),
                publisherStyles = optBoolean("publisherStyles", false),
                textAlign = enumValueOrNull<ReaderTextAlign>("textAlign") ?: ReaderTextAlign.START,
                pdfFit = enumValueOrNull<ReaderPdfFit>("pdfFit") ?: ReaderPdfFit.WIDTH,
                pdfScrollAxis = enumValueOrNull<ReaderPdfScrollAxis>("pdfScrollAxis") ?: ReaderPdfScrollAxis.HORIZONTAL,
                pageDirection = enumValueOrNull<ReaderPageDirection>("pageDirection") ?: ReaderPageDirection.AUTO
            )
        }.getOrNull()

    private inline fun <reified T : Enum<T>> JSONObject.enumValueOrNull(name: String): T? =
        optString(name).takeIf { it.isNotBlank() }?.let { value ->
            runCatching { enumValueOf<T>(value) }.getOrNull()
        }

    private fun String.cleanCollectionName(): String {
        val cleaned = trim().replace(Regex("\\s+"), " ")
        require(cleaned.isNotBlank()) { "Collection name required" }
        require(cleaned.length <= MAX_COLLECTION_NAME_LENGTH) { "Collection names can be up to $MAX_COLLECTION_NAME_LENGTH characters" }
        return cleaned
    }

    private companion object {
        const val BACKUP_FORMAT = "com.xreader.library-metadata.v1"
        const val MAX_COLLECTION_NAME_LENGTH = 80
    }
}
