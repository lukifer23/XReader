package com.xreader.app.ui

import com.xreader.app.data.AnnotationEntity
import com.xreader.app.data.AnnotationKind
import com.xreader.app.data.BookEntity
import com.xreader.app.data.BookFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesViewModelTest {
    @Test
    fun buildsTagOptionsFromCurrentSearchAndKindScope() {
        val state = buildNotesUiState(
            annotations = listOf(
                annotation(id = 1, kind = AnnotationKind.NOTE, quote = "Mars politics", tags = "Mars, theme"),
                annotation(id = 2, kind = AnnotationKind.NOTE, quote = "Mars craft", tags = "Mars, craft"),
                annotation(id = 3, kind = AnnotationKind.HIGHLIGHT, quote = "Mars highlight", tags = "Mars, craft")
            ),
            booksById = mapOf(1L to book()),
            currentQuery = "Mars",
            currentKind = AnnotationKind.NOTE,
            currentTag = null
        )

        assertEquals(listOf("Mars" to 2, "craft" to 1, "theme" to 1), state.tagOptions.map { it.label to it.count })
        assertEquals(listOf(1L, 2L), state.notes.map { it.annotation.id })
    }

    @Test
    fun selectedTagFiltersNotesAndInvalidSelectionClears() {
        val annotations = listOf(
            annotation(id = 1, tags = "Mars, theme"),
            annotation(id = 2, tags = "craft")
        )

        val filtered = buildNotesUiState(
            annotations = annotations,
            booksById = mapOf(1L to book()),
            currentQuery = "",
            currentKind = null,
            currentTag = "mars"
        )

        assertEquals("Mars", filtered.selectedTag)
        assertEquals(listOf(1L), filtered.notes.map { it.annotation.id })

        val invalid = buildNotesUiState(
            annotations = annotations,
            booksById = mapOf(1L to book()),
            currentQuery = "craft",
            currentKind = null,
            currentTag = "mars"
        )

        assertNull(invalid.selectedTag)
        assertEquals(listOf(2L), invalid.notes.map { it.annotation.id })
    }

    @Test
    fun notesVisibleBooksIgnoreUnrenderedMetadataAndOrder() {
        val first = book().copy(id = 1L, title = "Red Mars", author = "Kim Stanley Robinson")
        val second = book().copy(id = 2L, title = "Dune", author = "Frank Herbert")

        assertTrue(
            sameNotesVisibleBooks(
                previous = listOf(first, second),
                next = listOf(
                    second.copy(genre = "Science Fiction", favorite = true, updatedAt = 9_000L),
                    first.copy(description = "Changed", finished = true, fileSizeBytes = 99_000L)
                )
            )
        )
    }

    @Test
    fun notesVisibleBooksTrackSearchAndRenderedBookFields() {
        val book = book()

        assertFalse(sameNotesVisibleBooks(listOf(book), listOf(book.copy(title = "Blue Mars"))))
        assertFalse(sameNotesVisibleBooks(listOf(book), listOf(book.copy(author = "Different Author"))))
        assertFalse(sameNotesVisibleBooks(listOf(book), listOf(book.copy(id = 2L))))
    }

    private fun annotation(
        id: Long,
        kind: AnnotationKind = AnnotationKind.NOTE,
        quote: String = "Quote",
        tags: String,
    ): AnnotationEntity =
        AnnotationEntity(
            id = id,
            bookId = 1L,
            kind = kind,
            locator = "loc-$id",
            quote = quote,
            note = "Note $id",
            color = "#F2C94C",
            tags = tags,
            createdAt = 1L,
            updatedAt = 2L
        )

    private fun book(): BookEntity =
        BookEntity(
            id = 1L,
            title = "Red Mars",
            author = "Kim Stanley Robinson",
            sortTitle = "red mars",
            format = BookFormat.EPUB,
            sourceExtension = "epub",
            fileName = "red-mars.epub",
            filePath = "library/books/red-mars.epub",
            checksum = "checksum",
            fileSizeBytes = 1L,
            wordCount = 1,
            importedAt = 1L,
            updatedAt = 1L
        )
}
