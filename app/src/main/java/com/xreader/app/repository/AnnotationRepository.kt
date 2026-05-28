package com.xreader.app.repository

import com.xreader.app.data.AnnotationDao
import com.xreader.app.data.AnnotationEntity
import com.xreader.app.data.AnnotationKind
import com.xreader.app.data.BookmarkEntity
import kotlinx.coroutines.flow.Flow
import java.time.Clock

class AnnotationRepository(
    private val dao: AnnotationDao,
    private val clock: Clock = Clock.systemUTC(),
) {
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

    suspend fun updateNote(annotation: AnnotationEntity, note: String) {
        dao.updateAnnotation(
            annotation.copy(
                note = note.trim(),
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
}
