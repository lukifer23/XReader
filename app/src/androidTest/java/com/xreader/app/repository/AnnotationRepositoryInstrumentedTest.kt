package com.xreader.app.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xreader.app.data.BookEntity
import com.xreader.app.data.BookFormat
import com.xreader.app.data.XReaderDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class AnnotationRepositoryInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sourceDb = Room.inMemoryDatabaseBuilder(context, XReaderDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val targetDb = Room.inMemoryDatabaseBuilder(context, XReaderDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val clock = Clock.fixed(Instant.parse("2026-05-28T12:00:00Z"), ZoneOffset.UTC)

    @After
    fun cleanUp() {
        sourceDb.close()
        targetDb.close()
    }

    @Test
    fun exportsAndImportsNotesHighlightsAndBookmarksByBookChecksum() = runBlocking {
        val sourceBookId = sourceDb.books().insert(testBook(id = 0, title = "Source title"))
        val sourceRepository = AnnotationRepository(sourceDb.annotations(), sourceDb.books(), clock)
        sourceRepository.addNote(
            bookId = sourceBookId,
            locator = "loc-1",
            quote = "Important quote",
            note = "Remember this",
            tags = "tagged"
        )
        sourceRepository.addHighlight(
            bookId = sourceBookId,
            locator = "loc-2",
            quote = "Highlighted quote",
            color = "#FFCC00",
            note = "Useful"
        )
        assertTrue(sourceRepository.toggleBookmark(sourceBookId, "loc-3", "Chapter 3", 0.42))

        val exported = sourceRepository.exportBackupJson()
        val targetBookId = targetDb.books().insert(testBook(id = 0, title = "Target title"))
        val targetRepository = AnnotationRepository(targetDb.annotations(), targetDb.books(), clock)

        val imported = targetRepository.importBackupJson(exported.json)

        assertEquals(2, imported.annotationsImported)
        assertEquals(1, imported.bookmarksImported)
        assertEquals(0, imported.missingBooks)
        assertTrue(targetDb.annotations().allAnnotations().all { it.bookId == targetBookId })
        assertTrue(targetDb.annotations().allBookmarks().all { it.bookId == targetBookId })

        val secondImport = targetRepository.importBackupJson(exported.json)

        assertEquals(0, secondImport.annotationsImported)
        assertEquals(0, secondImport.bookmarksImported)
        assertEquals(2, secondImport.annotationsSkipped)
        assertEquals(1, secondImport.bookmarksSkipped)
    }

    private fun testBook(id: Long, title: String): BookEntity =
        BookEntity(
            id = id,
            title = title,
            author = "Author",
            sortTitle = title.lowercase(),
            format = BookFormat.EPUB,
            sourceExtension = "epub",
            fileName = "book.epub",
            filePath = "library/books/book.epub",
            checksum = "shared-checksum",
            fileSizeBytes = 1234,
            wordCount = 500,
            importedAt = clock.millis(),
            updatedAt = clock.millis()
        )
}
