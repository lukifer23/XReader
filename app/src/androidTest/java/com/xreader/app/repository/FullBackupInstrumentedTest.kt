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
import com.xreader.app.settings.LibraryDensity
import com.xreader.app.settings.LibraryGroup
import com.xreader.app.settings.LibrarySettings
import com.xreader.app.settings.LibrarySort
import com.xreader.app.settings.SettingsRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FullBackupInstrumentedTest {
    @Test
    fun fullBackupRoundTripIsChecksumMatchedAndIdempotent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = database(context)
        val target = database(context)
        val settings = SettingsRepository(context)
        val clock = Clock.fixed(Instant.ofEpochMilli(10_000L), ZoneOffset.UTC)
        val originalLibrarySettings = settings.librarySettings.first()
        try {
            settings.setLibrarySettings(LibrarySettings(LibrarySort.AUTHOR, LibraryDensity.COMPACT, LibraryGroup.SERIES))
            val retainedId = source.books().insert(book("retained", "Source title"))
            source.books().insert(book("missing", "Missing title"))
            source.reading().upsertState(
                ReadingStateEntity(retainedId, "locator-2", 0.5, 2, 4, 900L, 240, 9_000L),
            )
            source.reading().insertSession(
                ReadingSessionEntity(bookId = retainedId, startedAt = 1_000L, endedAt = 2_000L, activeMillis = 800L, startUnit = 1, endUnit = 2, wordsRead = 200, wpm = 250),
            )
            val collectionId = source.collections().insertCollection(CollectionEntity(name = "Favorites", createdAt = 1L, updatedAt = 2L))
            source.collections().insertBookCollection(BookCollectionEntity(retainedId, collectionId, 3L))
            val sourceAnnotations = AnnotationRepository(source.annotations(), source.books(), clock)
            sourceAnnotations.addNote(retainedId, "locator-2", "Quoted text", "Remember this", "orbit, favorite")
            sourceAnnotations.toggleBookmark(retainedId, "locator-2", "Chapter 2", 0.5)

            val exported = fullRepository(source, settings, clock).exportBackupJson()

            settings.setLibrarySettings(LibrarySettings())
            val targetId = target.books().insert(book("retained", "Target title"))
            val first = fullRepository(target, settings, clock).importBackupJson(exported.json)

            assertEquals(1, first.library.booksUpdated)
            assertEquals(1, first.library.missingBooks)
            assertEquals(1, first.library.readingStatesImported)
            assertEquals(1, first.library.readingSessionsImported)
            assertEquals(1, first.annotations.annotationsImported)
            assertEquals(1, first.annotations.bookmarksImported)
            assertEquals(LibraryGroup.SERIES, settings.librarySettings.first().group)
            assertEquals("Source title", target.books().getBook(targetId)?.title)
            assertEquals("locator-2", target.reading().getState(targetId)?.locator)

            val repeated = fullRepository(target, settings, clock).importBackupJson(exported.json)
            assertEquals(0, repeated.library.booksUpdated)
            assertEquals(1, repeated.library.readingStatesSkipped)
            assertEquals(1, repeated.library.readingSessionsSkipped)
            assertEquals(1, repeated.annotations.annotationsSkipped)
            assertEquals(1, repeated.annotations.bookmarksSkipped)
            assertEquals(1, target.annotations().allAnnotations().size)
            assertEquals(1, target.annotations().allBookmarks().size)
        } finally {
            settings.setLibrarySettings(originalLibrarySettings)
            source.close()
            target.close()
        }
    }

    private fun database(context: Context): XReaderDatabase =
        Room.inMemoryDatabaseBuilder(context, XReaderDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    private fun fullRepository(
        database: XReaderDatabase,
        settings: SettingsRepository,
        clock: Clock,
    ): FullBackupRepository {
        val library = LibraryBackupRepository(
            bookDao = database.books(),
            collectionDao = database.collections(),
            readingDao = database.reading(),
            clock = clock,
            settingsRepository = settings,
        )
        return FullBackupRepository(
            database = database,
            library = library,
            annotations = AnnotationRepository(database.annotations(), database.books(), clock),
            clock = clock,
        )
    }

    private fun book(checksum: String, title: String): BookEntity =
        BookEntity(
            title = title,
            author = "XReader",
            sortTitle = title.lowercase(),
            format = BookFormat.EPUB,
            sourceExtension = "epub",
            fileName = "$checksum.epub",
            filePath = "/private/$checksum.epub",
            checksum = checksum,
            fileSizeBytes = 100,
            wordCount = 500,
            importedAt = 1_000L,
            updatedAt = 2_000L,
        )
}
