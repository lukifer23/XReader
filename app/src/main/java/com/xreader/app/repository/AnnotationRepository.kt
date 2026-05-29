package com.xreader.app.repository

import com.xreader.app.data.AnnotationDao
import com.xreader.app.data.AnnotationEntity
import com.xreader.app.data.AnnotationKind
import com.xreader.app.data.BookDao
import com.xreader.app.data.BookmarkEntity
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Clock
import kotlin.math.min

class AnnotationRepository(
    private val dao: AnnotationDao,
    private val bookDao: BookDao,
    private val clock: Clock = Clock.systemUTC(),
) {
    data class BackupExportResult(
        val json: String,
        val annotations: Int,
        val bookmarks: Int,
    )

    data class MarkdownExportResult(
        val markdown: String,
        val books: Int,
        val annotations: Int,
        val bookmarks: Int,
    )

    data class BackupImportResult(
        val annotationsImported: Int,
        val annotationsUpdated: Int,
        val annotationsSkipped: Int,
        val bookmarksImported: Int,
        val bookmarksSkipped: Int,
        val missingBooks: Int,
    )

    fun observeAllAnnotations(): Flow<List<AnnotationEntity>> = dao.observeAllAnnotations()
    fun observeAnnotations(bookId: Long): Flow<List<AnnotationEntity>> = dao.observeAnnotations(bookId)
    fun observeBookmarks(bookId: Long): Flow<List<BookmarkEntity>> = dao.observeBookmarks(bookId)

    suspend fun addNote(
        bookId: Long,
        locator: String,
        quote: String,
        note: String,
        tags: String = "",
    ): Long {
        val now = clock.millis()
        return dao.insertAnnotation(
            AnnotationEntity(
                bookId = bookId,
                kind = AnnotationKind.NOTE,
                locator = locator,
                quote = quote,
                note = note,
                color = "#2F6F6B",
                tags = tags,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun addHighlight(
        bookId: Long,
        locator: String,
        quote: String,
        color: String,
        note: String = "",
    ): Long {
        val now = clock.millis()
        return dao.insertAnnotation(
            AnnotationEntity(
                bookId = bookId,
                kind = AnnotationKind.HIGHLIGHT,
                locator = locator,
                quote = quote,
                note = note,
                color = color,
                tags = "",
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun deleteAnnotation(id: Long) = dao.deleteAnnotation(id)

    suspend fun updateNote(
        annotation: AnnotationEntity,
        note: String,
        color: String = annotation.color,
    ) {
        dao.updateAnnotation(
            annotation.copy(
                note = note.trim(),
                color = color,
                updatedAt = clock.millis()
            )
        )
    }

    suspend fun toggleBookmark(
        bookId: Long,
        locator: String,
        label: String,
        progress: Double,
    ): Boolean {
        val existing = dao.getBookmarkAt(bookId, locator)
        return if (existing != null) {
            dao.deleteBookmark(existing.id)
            false
        } else {
            dao.insertBookmark(
                BookmarkEntity(
                    bookId = bookId,
                    locator = locator,
                    label = label,
                    progress = progress,
                    createdAt = clock.millis()
                )
            )
            true
        }
    }

    suspend fun deleteBookmark(id: Long) = dao.deleteBookmark(id)

    suspend fun exportBackupJson(): BackupExportResult {
        val books = bookDao.booksForBackup().associateBy { it.id }
        val annotations = dao.allAnnotations()
        val bookmarks = dao.allBookmarks()
        val root = JSONObject()
            .put("format", BACKUP_FORMAT)
            .put("version", 1)
            .put("exportedAt", clock.millis())
            .put(
                "annotations",
                JSONArray().also { array ->
                    annotations.forEach { annotation ->
                        val book = books[annotation.bookId] ?: return@forEach
                        array.put(
                            JSONObject()
                                .put("bookChecksum", book.checksum)
                                .put("bookTitle", book.title)
                                .put("bookAuthor", book.author)
                                .put("kind", annotation.kind.name)
                                .put("locator", annotation.locator)
                                .put("quote", annotation.quote)
                                .put("note", annotation.note)
                                .put("color", annotation.color)
                                .put("tags", annotation.tags)
                                .put("createdAt", annotation.createdAt)
                                .put("updatedAt", annotation.updatedAt)
                        )
                    }
                }
            )
            .put(
                "bookmarks",
                JSONArray().also { array ->
                    bookmarks.forEach { bookmark ->
                        val book = books[bookmark.bookId] ?: return@forEach
                        array.put(
                            JSONObject()
                                .put("bookChecksum", book.checksum)
                                .put("bookTitle", book.title)
                                .put("bookAuthor", book.author)
                                .put("locator", bookmark.locator)
                                .put("label", bookmark.label)
                                .put("progress", bookmark.progress)
                                .put("createdAt", bookmark.createdAt)
                        )
                    }
                }
            )
        return BackupExportResult(
            json = root.toString(2),
            annotations = annotations.size,
            bookmarks = bookmarks.size
        )
    }

    suspend fun exportMarkdown(): MarkdownExportResult {
        val books = bookDao.booksForBackup().associateBy { it.id }
        val annotations = dao.allAnnotations().filter { books.containsKey(it.bookId) }
        val bookmarks = dao.allBookmarks().filter { books.containsKey(it.bookId) }
        val bookCount = (annotations.map { it.bookId } + bookmarks.map { it.bookId })
            .distinct()
            .size
        return MarkdownExportResult(
            markdown = AnnotationMarkdownExport.build(
                exportedAt = clock.millis(),
                booksById = books,
                annotations = annotations,
                bookmarks = bookmarks
            ),
            books = bookCount,
            annotations = annotations.size,
            bookmarks = bookmarks.size
        )
    }

    suspend fun importBackupJson(json: String): BackupImportResult {
        val root = JSONObject(json)
        require(root.optString("format") == BACKUP_FORMAT) { "This is not an XReader notes backup." }
        var annotationsImported = 0
        var annotationsUpdated = 0
        var annotationsSkipped = 0
        var bookmarksImported = 0
        var bookmarksSkipped = 0
        var missingBooks = 0

        val annotations = root.optJSONArray("annotations") ?: JSONArray()
        for (index in 0 until annotations.length()) {
            val item = annotations.optJSONObject(index) ?: continue
            val book = bookDao.getByChecksum(item.optString("bookChecksum"))
            if (book == null) {
                missingBooks += 1
                continue
            }
            val kind = runCatching { AnnotationKind.valueOf(item.optString("kind")) }.getOrNull()
            if (kind == null) {
                annotationsSkipped += 1
                continue
            }
            val locator = item.optString("locator")
            val quote = item.optString("quote")
            if (locator.isBlank() || quote.isBlank()) {
                annotationsSkipped += 1
                continue
            }
            val imported = AnnotationEntity(
                bookId = book.id,
                kind = kind,
                locator = locator,
                quote = quote,
                note = item.optString("note"),
                color = item.optString("color", "#2F6F6B"),
                tags = item.optString("tags"),
                createdAt = item.optLong("createdAt", clock.millis()),
                updatedAt = item.optLong("updatedAt", clock.millis())
            )
            val existing = dao.getAnnotationForImport(book.id, kind, locator, quote)
            if (existing == null) {
                dao.insertAnnotation(imported)
                annotationsImported += 1
            } else if (imported.updatedAt > existing.updatedAt) {
                dao.updateAnnotation(
                    existing.copy(
                        note = imported.note,
                        color = imported.color,
                        tags = imported.tags,
                        createdAt = min(existing.createdAt, imported.createdAt),
                        updatedAt = imported.updatedAt
                    )
                )
                annotationsUpdated += 1
            } else {
                annotationsSkipped += 1
            }
        }

        val bookmarks = root.optJSONArray("bookmarks") ?: JSONArray()
        for (index in 0 until bookmarks.length()) {
            val item = bookmarks.optJSONObject(index) ?: continue
            val book = bookDao.getByChecksum(item.optString("bookChecksum"))
            if (book == null) {
                missingBooks += 1
                continue
            }
            val locator = item.optString("locator")
            if (locator.isBlank()) {
                bookmarksSkipped += 1
                continue
            }
            if (dao.getBookmarkAt(book.id, locator) != null) {
                bookmarksSkipped += 1
                continue
            }
            dao.insertBookmark(
                BookmarkEntity(
                    bookId = book.id,
                    locator = locator,
                    label = item.optString("label", book.title),
                    progress = item.optDouble("progress", 0.0).coerceIn(0.0, 1.0),
                    createdAt = item.optLong("createdAt", clock.millis())
                )
            )
            bookmarksImported += 1
        }

        return BackupImportResult(
            annotationsImported = annotationsImported,
            annotationsUpdated = annotationsUpdated,
            annotationsSkipped = annotationsSkipped,
            bookmarksImported = bookmarksImported,
            bookmarksSkipped = bookmarksSkipped,
            missingBooks = missingBooks
        )
    }

    private companion object {
        const val BACKUP_FORMAT = "com.xreader.annotations.v1"
    }
}
