package com.xreader.app.repository

import com.xreader.app.data.BookEntity
import com.xreader.app.data.BookFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupChecksumToolsTest {
    @Test
    fun normalizedBackupChecksumTrimsAndLowercases() {
        assertEquals("abc123", "  ABC123 \n".normalizedBackupChecksum())
        assertNull("   ".normalizedBackupChecksum())
    }

    @Test
    fun booksCanBeMatchedByNormalizedChecksum() {
        val book = book(checksum = "ABCDEF123")

        val byChecksum = listOf(book).byNormalizedChecksum()

        assertEquals(book.id, byChecksum.getValue("abcdef123").id)
    }

    private fun book(checksum: String): BookEntity =
        BookEntity(
            id = 42,
            title = "Book",
            author = "Author",
            sortTitle = "book",
            format = BookFormat.EPUB,
            sourceExtension = "epub",
            fileName = "book.epub",
            filePath = "library/books/book.epub",
            checksum = checksum,
            fileSizeBytes = 100,
            wordCount = 10,
            importedAt = 1,
            updatedAt = 1
        )
}
