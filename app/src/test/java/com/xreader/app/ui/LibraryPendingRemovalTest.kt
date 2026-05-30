package com.xreader.app.ui

import com.xreader.app.data.BookEntity
import com.xreader.app.data.BookFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LibraryPendingRemovalTest {
    @Test
    fun withoutPendingRemovalIdsKeepsListWhenNothingIsPending() {
        val books = listOf(book(1L, "First"), book(2L, "Second"))

        val filtered = books.withoutPendingRemovalIds(emptySet())

        assertSame(books, filtered)
    }

    @Test
    fun withoutPendingRemovalIdsHidesPendingBooksFromLibraryState() {
        val filtered = listOf(
            book(1L, "First"),
            book(2L, "Second"),
            book(3L, "Third")
        ).withoutPendingRemovalIds(setOf(2L, 99L))

        assertEquals(listOf("First", "Third"), filtered.map { it.title })
    }

    private fun book(id: Long, title: String): BookEntity =
        BookEntity(
            id = id,
            title = title,
            author = "Author",
            sortTitle = title.lowercase(),
            format = BookFormat.EPUB,
            sourceExtension = "epub",
            fileName = "$title.epub",
            filePath = "library/books/$title.epub",
            checksum = "checksum-$id",
            fileSizeBytes = 1024L,
            wordCount = 10_000,
            importedAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L
        )
}
