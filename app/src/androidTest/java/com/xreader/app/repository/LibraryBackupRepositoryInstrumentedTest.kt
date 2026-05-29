package com.xreader.app.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xreader.app.data.BookCollectionEntity
import com.xreader.app.data.BookEntity
import com.xreader.app.data.BookFormat
import com.xreader.app.data.CollectionEntity
import com.xreader.app.data.ReadingSessionEntity
import com.xreader.app.data.ReadingStateEntity
import com.xreader.app.data.XReaderDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class LibraryBackupRepositoryInstrumentedTest {
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
    fun exportsAndImportsLibraryMetadataByChecksumWithoutReplacingBookFiles() = runBlocking {
        val sourceBookId = sourceDb.books().insert(
            testBook(
                title = "Edited Title",
                author = "Edited Author",
                filePath = "library/books/source.epub",
                favorite = true,
                finished = true
            )
        )
        sourceDb.reading().upsertState(
            ReadingStateEntity(
                bookId = sourceBookId,
                locator = """{"href":"chapter.xhtml"}""",
                progress = 0.64,
                currentUnit = 12,
                totalUnits = 20,
                activeMillis = 1_200_000,
                estimatedWpm = 260,
                lastReadAt = 2_000,
                finishedAt = null
            )
        )
        sourceDb.reading().insertSession(
            ReadingSessionEntity(
                bookId = sourceBookId,
                startedAt = 1_000,
                endedAt = 1_600,
                activeMillis = 600,
                startUnit = 10,
                endUnit = 12,
                wordsRead = 420,
                wpm = 252
            )
        )
        val collectionId = sourceDb.collections().insertCollection(
            CollectionEntity(
                name = "Sci-Fi",
                createdAt = 1_000,
                updatedAt = 2_000
            )
        )
        sourceDb.collections().insertBookCollection(
            BookCollectionEntity(
                bookId = sourceBookId,
                collectionId = collectionId,
                addedAt = 2_000
            )
        )
        val exported = LibraryBackupRepository(sourceDb.books(), sourceDb.collections(), sourceDb.reading(), clock).exportBackupJson()
        val targetBookId = targetDb.books().insert(
            testBook(
                title = "Fresh Import",
                author = "Original Author",
                filePath = "library/books/target.epub",
                favorite = false,
                finished = false
            )
        )
        val targetRepository = LibraryBackupRepository(targetDb.books(), targetDb.collections(), targetDb.reading(), clock)

        val imported = targetRepository.importBackupJson(exported.json)

        assertEquals(1, exported.collections)
        assertEquals("Sci-Fi", JSONObject(exported.json).getJSONArray("collections").getJSONObject(0).getString("name"))
        assertEquals(1, imported.booksUpdated)
        assertEquals(1, imported.collectionsImported)
        assertEquals(1, imported.collectionMembershipsImported)
        assertEquals(1, imported.readingStatesImported)
        assertEquals(1, imported.readingSessionsImported)
        assertEquals(0, imported.missingBooks)

        val restoredBook = requireNotNull(targetDb.books().getBook(targetBookId))
        assertEquals("Edited Title", restoredBook.title)
        assertEquals("Edited Author", restoredBook.author)
        assertEquals("library/books/target.epub", restoredBook.filePath)
        assertTrue(restoredBook.favorite)
        assertTrue(restoredBook.finished)

        val restoredState = requireNotNull(targetDb.reading().getState(targetBookId))
        assertEquals(0.64, restoredState.progress, 0.001)
        assertEquals(260, restoredState.estimatedWpm)
        assertEquals(targetBookId, restoredState.bookId)
        assertEquals(1, targetDb.reading().allSessions().size)
        assertEquals(listOf("Sci-Fi"), targetDb.collections().observeCollections().first().map { it.name })
        assertEquals(listOf(targetBookId), targetDb.collections().allBookCollections().map { it.bookId })

        val secondImport = targetRepository.importBackupJson(exported.json)

        assertEquals(0, secondImport.booksUpdated)
        assertEquals(0, secondImport.collectionsImported)
        assertEquals(0, secondImport.collectionMembershipsImported)
        assertEquals(1, secondImport.collectionMembershipsSkipped)
        assertEquals(0, secondImport.readingStatesImported)
        assertEquals(1, secondImport.readingStatesSkipped)
        assertEquals(0, secondImport.readingSessionsImported)
        assertEquals(1, secondImport.readingSessionsSkipped)
    }

    private fun testBook(
        title: String,
        author: String,
        filePath: String,
        favorite: Boolean,
        finished: Boolean,
    ): BookEntity =
        BookEntity(
            title = title,
            author = author,
            sortTitle = title.lowercase(),
            series = "Series",
            seriesIndex = 1.0,
            genre = "Science Fiction",
            year = 2026,
            description = "Description",
            language = "en",
            format = BookFormat.EPUB,
            sourceExtension = "epub",
            fileName = filePath.substringAfterLast('/'),
            filePath = filePath,
            coverImagePath = "library/covers/cover.jpg",
            checksum = "shared-checksum",
            fileSizeBytes = 1234,
            wordCount = 500,
            pageCount = null,
            importedAt = 100,
            updatedAt = 100,
            lastOpenedAt = 1_500,
            favorite = favorite,
            finished = finished
        )
}
