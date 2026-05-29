package com.xreader.app.ui

import com.xreader.app.data.BookEntity
import com.xreader.app.data.BookFormat
import com.xreader.app.data.ReadingStateEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryProgressStateTest {
    @Test
    fun manualFinishedOverridesLowProgressForLibraryClassification() {
        val item = item(progress = 0.0, finished = true)

        assertTrue(item.isLibraryFinished())
        assertFalse(item.isLibraryUnread())
        assertFalse(item.isLibraryInProgress())
        assertEquals(1.0, item.displayLibraryProgress(), 0.0)
    }

    @Test
    fun nearlyCompleteProgressCountsAsFinishedWithoutManualFlag() {
        val item = item(progress = 0.996, finished = false)

        assertTrue(item.isLibraryFinished())
        assertFalse(item.isLibraryInProgress())
        assertEquals(0.996, item.displayLibraryProgress(), 0.0)
    }

    @Test
    fun activeProgressStaysInProgress() {
        val item = item(progress = 0.42, finished = false)

        assertFalse(item.isLibraryFinished())
        assertTrue(item.isLibraryInProgress())
        assertFalse(item.isLibraryUnread())
        assertEquals(0.42, item.displayLibraryProgress(), 0.0)
    }

    private fun item(progress: Double, finished: Boolean): BookListItem =
        BookListItem(
            book = book(finished = finished),
            state = ReadingStateEntity(
                bookId = 7L,
                locator = "locator",
                progress = progress,
                currentUnit = 4,
                totalUnits = 10,
                activeMillis = 0L,
                estimatedWpm = 0,
                lastReadAt = 1_700_000_000_000L
            )
        )

    private fun book(finished: Boolean): BookEntity =
        BookEntity(
            id = 7L,
            title = "Book",
            author = "Author",
            sortTitle = "Book",
            format = BookFormat.EPUB,
            sourceExtension = "epub",
            fileName = "book.epub",
            filePath = "books/book.epub",
            checksum = "checksum",
            fileSizeBytes = 1024L,
            wordCount = 80_000,
            importedAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L,
            finished = finished
        )
}
