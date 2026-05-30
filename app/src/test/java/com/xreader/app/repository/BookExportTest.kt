package com.xreader.app.repository

import com.xreader.app.data.BookEntity
import com.xreader.app.data.BookFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class BookExportTest {
    @Test
    fun epubExportUsesSafeTitleAndEpubMimeType() {
        val book = book(
            title = "Mars: The / Long * Night?",
            format = BookFormat.EPUB,
            filePath = "library/books/checksum.epub"
        )

        assertEquals("Mars The Long Night.epub", bookExportFileName(book))
        assertEquals("application/epub+zip", bookExportMimeType(book))
    }

    @Test
    fun pdfExportUsesPdfMimeType() {
        val book = book(
            title = "Station Manual",
            format = BookFormat.PDF,
            filePath = "library/books/checksum.pdf"
        )

        assertEquals("Station Manual.pdf", bookExportFileName(book))
        assertEquals("application/pdf", bookExportMimeType(book))
    }

    @Test
    fun convertedTextExportsStoredEpubCopy() {
        val book = book(
            title = "Plain Text Notes",
            format = BookFormat.EPUB,
            sourceExtension = "txt",
            fileName = "Plain Text Notes.txt",
            filePath = "library/books/checksum.epub"
        )

        assertEquals("Plain Text Notes.epub", bookExportFileName(book))
        assertEquals("application/epub+zip", bookExportMimeType(book))
    }

    @Test
    fun blankTitleFallsBackToOriginalFileName() {
        val book = book(
            title = " ",
            fileName = "Recovered Book.epub",
            filePath = "library/books/checksum.epub"
        )

        assertEquals("Recovered Book.epub", bookExportFileName(book))
    }

    private fun book(
        title: String,
        format: BookFormat = BookFormat.EPUB,
        sourceExtension: String = "epub",
        fileName: String = "$title.epub",
        filePath: String = "library/books/checksum.epub",
    ): BookEntity =
        BookEntity(
            id = 1L,
            title = title,
            author = "Author",
            sortTitle = title.trim().ifBlank { "recovered book" },
            format = format,
            sourceExtension = sourceExtension,
            fileName = fileName,
            filePath = filePath,
            checksum = "checksum",
            fileSizeBytes = 1024L,
            wordCount = 10_000,
            importedAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L
        )
}
