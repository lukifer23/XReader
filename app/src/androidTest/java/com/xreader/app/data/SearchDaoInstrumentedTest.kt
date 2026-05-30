package com.xreader.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchDaoInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, XReaderDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun ftsSearchFindsIndexedChunks() = runBlocking {
        val bookId = db.books().insert(
            BookEntity(
                title = "Searchable",
                author = "Test",
                sortTitle = "searchable",
                format = BookFormat.EPUB,
                sourceExtension = "epub",
                fileName = "book.epub",
                filePath = "library/books/book.epub",
                checksum = "abc",
                fileSizeBytes = 1,
                wordCount = 6,
                importedAt = 1,
                updatedAt = 1
            )
        )
        db.search().replaceForBook(
            bookId,
            listOf(
                SearchIndexEntity(
                    bookId = bookId,
                    locator = "epub:one:0",
                    heading = "One",
                    body = "A clean reader should be fast.",
                    normalizedBody = "a clean reader should be fast",
                    unitIndex = 0
                ),
                SearchIndexEntity(
                    bookId = bookId,
                    locator = "epub:two:0",
                    heading = "Two",
                    body = "This paragraph is unrelated.",
                    normalizedBody = "this paragraph is unrelated",
                    unitIndex = 1
                )
            )
        )

        val results = db.search().searchBook(bookId, "normalizedBody:reader*")

        assertEquals(1, results.size)
        assertTrue(results.single().body.contains("reader"))
    }

    @Test
    fun librarySearchIncludesSourceBookDetails() = runBlocking {
        val firstBook = db.books().insert(
            BookEntity(
                title = "Mars Reader",
                author = "Ada Lovelace",
                sortTitle = "mars reader",
                format = BookFormat.EPUB,
                sourceExtension = "epub",
                fileName = "mars.epub",
                filePath = "library/books/mars.epub",
                checksum = "mars",
                fileSizeBytes = 1,
                wordCount = 6,
                importedAt = 1,
                updatedAt = 1,
                lastOpenedAt = 30
            )
        )
        val secondBook = db.books().insert(
            BookEntity(
                title = "Ocean Manual",
                author = "Grace Hopper",
                sortTitle = "ocean manual",
                format = BookFormat.EPUB,
                sourceExtension = "epub",
                fileName = "ocean.epub",
                filePath = "library/books/ocean.epub",
                checksum = "ocean",
                fileSizeBytes = 1,
                wordCount = 6,
                importedAt = 1,
                updatedAt = 1,
                lastOpenedAt = 10
            )
        )
        db.search().replaceForBook(
            firstBook,
            listOf(
                SearchIndexEntity(
                    bookId = firstBook,
                    locator = "epub:mars:0",
                    heading = "Dust",
                    body = "The courier crossed the landing field.",
                    normalizedBody = "the courier crossed the landing field",
                    unitIndex = 0
                )
            )
        )
        db.search().replaceForBook(
            secondBook,
            listOf(
                SearchIndexEntity(
                    bookId = secondBook,
                    locator = "epub:ocean:0",
                    heading = "Harbor",
                    body = "A courier brought a waterproof note.",
                    normalizedBody = "a courier brought a waterproof note",
                    unitIndex = 0
                )
            )
        )

        val results = db.search().searchLibraryWithBooks("normalizedBody:courier*")

        assertEquals(listOf("Mars Reader", "Ocean Manual"), results.map { it.bookTitle })
        assertEquals(listOf("Ada Lovelace", "Grace Hopper"), results.map { it.bookAuthor })
        assertEquals(listOf(firstBook, secondBook), results.map { it.row.bookId })
    }
}
