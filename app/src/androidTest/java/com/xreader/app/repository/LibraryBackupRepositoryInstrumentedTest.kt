package com.xreader.app.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xreader.app.data.BookCollectionEntity
import com.xreader.app.data.BookEntity
import com.xreader.app.data.BookFormat
import com.xreader.app.data.CollectionEntity
import com.xreader.app.data.ReadingSessionEntity
import com.xreader.app.data.ReadingStateEntity
import com.xreader.app.data.ReaderTheme
import com.xreader.app.data.XReaderDatabase
import com.xreader.app.settings.LibraryDensity
import com.xreader.app.settings.LibrarySettings
import com.xreader.app.settings.LibrarySort
import com.xreader.app.settings.ReadAloudSleepTimer
import com.xreader.app.settings.ReaderFontFamily
import com.xreader.app.settings.ReaderHighlightColor
import com.xreader.app.settings.ReaderOrientation
import com.xreader.app.settings.ReaderPageDirection
import com.xreader.app.settings.ReaderPdfFit
import com.xreader.app.settings.ReaderPdfScrollAxis
import com.xreader.app.settings.ReaderSettings
import com.xreader.app.settings.ReaderTapZonePreset
import com.xreader.app.settings.ReaderTextAlign
import com.xreader.app.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class LibraryBackupRepositoryInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val root = File(context.cacheDir, "library-backup-test-${System.nanoTime()}")
    private val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sourceDb = Room.inMemoryDatabaseBuilder(context, XReaderDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val targetDb = Room.inMemoryDatabaseBuilder(context, XReaderDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val clock = Clock.fixed(Instant.parse("2026-05-28T12:00:00Z"), ZoneOffset.UTC)

    @After
    fun cleanUp() {
        dataStoreScope.cancel()
        root.deleteRecursively()
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
        val sourceSettings = testSettingsRepository("source")
        sourceSettings.setReaderSettings(
            ReaderSettings(
                theme = ReaderTheme.OLED,
                fontScale = 1.28f,
                lineHeight = 1.5f,
                marginScale = 0.7f,
                fontFamily = ReaderFontFamily.DUOSPACE,
                fontWeight = 1.15f,
                hyphenation = true,
                tapZonesEnabled = false,
                tapZonePreset = ReaderTapZonePreset.WIDE,
                pageTurnAnimations = false,
                keepScreenAwake = true,
                volumeKeysTurnPages = true,
                screenDim = 0.2f,
                readAloudRate = 1.2f,
                readAloudVoiceName = "local-test-voice",
                readAloudSleepTimer = ReadAloudSleepTimer.THIRTY_MINUTES,
                fullScreen = true,
                publisherStyles = true,
                textAlign = ReaderTextAlign.JUSTIFY,
                pdfFit = ReaderPdfFit.HEIGHT,
                pdfScrollAxis = ReaderPdfScrollAxis.VERTICAL,
                pageDirection = ReaderPageDirection.RIGHT_TO_LEFT,
                orientation = ReaderOrientation.LANDSCAPE,
                highlightColor = ReaderHighlightColor.BLUE.hex,
                idleTimeoutMillis = 120_000L
            )
        )
        sourceSettings.setLibrarySettings(
            LibrarySettings(
                sort = LibrarySort.SERIES,
                density = LibraryDensity.COMPACT
            )
        )
        sourceSettings.setBookAppearanceEnabled(
            bookId = sourceBookId,
            enabled = true,
            seed = ReaderSettings(
                fontScale = 1.35f,
                lineHeight = 1.6f,
                marginScale = 0.85f,
                fontFamily = ReaderFontFamily.ACCESSIBLE,
                fontWeight = 1.25f,
                hyphenation = true,
                publisherStyles = false,
                textAlign = ReaderTextAlign.JUSTIFY,
                pdfFit = ReaderPdfFit.CONTAIN,
                pdfScrollAxis = ReaderPdfScrollAxis.VERTICAL,
                pageDirection = ReaderPageDirection.RIGHT_TO_LEFT
            )
        )
        val exported = LibraryBackupRepository(
            bookDao = sourceDb.books(),
            collectionDao = sourceDb.collections(),
            readingDao = sourceDb.reading(),
            clock = clock,
            settingsRepository = sourceSettings
        ).exportBackupJson()
        val targetBookId = targetDb.books().insert(
            testBook(
                title = "Fresh Import",
                author = "Original Author",
                filePath = "library/books/target.epub",
                favorite = false,
                finished = false
            )
        )
        val targetSettings = testSettingsRepository("target")
        val targetRepository = LibraryBackupRepository(
            bookDao = targetDb.books(),
            collectionDao = targetDb.collections(),
            readingDao = targetDb.reading(),
            clock = clock,
            settingsRepository = targetSettings
        )

        val imported = targetRepository.importBackupJson(exported.json)

        assertEquals(1, exported.collections)
        assertEquals(2, exported.globalSettings)
        assertEquals(1, exported.readerAppearances)
        assertEquals("Sci-Fi", JSONObject(exported.json).getJSONArray("collections").getJSONObject(0).getString("name"))
        assertEquals(
            ReaderTheme.OLED.name,
            JSONObject(exported.json).getJSONObject("readerSettings").getString("theme")
        )
        assertEquals(
            LibrarySort.SERIES.name,
            JSONObject(exported.json).getJSONObject("librarySettings").getString("sort")
        )
        assertEquals(
            ReaderPageDirection.RIGHT_TO_LEFT.name,
            JSONObject(exported.json).getJSONArray("readerAppearances").getJSONObject(0).getString("pageDirection")
        )
        assertEquals(1, imported.booksUpdated)
        assertEquals(2, imported.globalSettingsImported)
        assertEquals(1, imported.collectionsImported)
        assertEquals(1, imported.collectionMembershipsImported)
        assertEquals(1, imported.readerAppearancesImported)
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
        val restoredSettings = targetSettings.settings.first()
        assertEquals(ReaderTheme.OLED, restoredSettings.theme)
        assertEquals(1.28f, restoredSettings.fontScale, 0.001f)
        assertEquals(ReaderFontFamily.DUOSPACE, restoredSettings.fontFamily)
        assertEquals(ReaderTapZonePreset.WIDE, restoredSettings.tapZonePreset)
        assertEquals(false, restoredSettings.pageTurnAnimations)
        assertEquals(true, restoredSettings.keepScreenAwake)
        assertEquals("local-test-voice", restoredSettings.readAloudVoiceName)
        assertEquals(ReadAloudSleepTimer.THIRTY_MINUTES, restoredSettings.readAloudSleepTimer)
        assertEquals(ReaderOrientation.LANDSCAPE, restoredSettings.orientation)
        assertEquals(ReaderHighlightColor.BLUE.hex, restoredSettings.highlightColor)
        assertEquals(120_000L, restoredSettings.idleTimeoutMillis)
        val restoredLibrarySettings = targetSettings.librarySettings.first()
        assertEquals(LibrarySort.SERIES, restoredLibrarySettings.sort)
        assertEquals(LibraryDensity.COMPACT, restoredLibrarySettings.density)
        val restoredAppearance = requireNotNull(targetSettings.bookAppearance(targetBookId).first())
        assertEquals(1.35f, restoredAppearance.fontScale, 0.001f)
        assertEquals(ReaderFontFamily.ACCESSIBLE, restoredAppearance.fontFamily)
        assertEquals(ReaderTextAlign.JUSTIFY, restoredAppearance.textAlign)
        assertEquals(ReaderPdfFit.CONTAIN, restoredAppearance.pdfFit)
        assertEquals(ReaderPdfScrollAxis.VERTICAL, restoredAppearance.pdfScrollAxis)
        assertEquals(ReaderPageDirection.RIGHT_TO_LEFT, restoredAppearance.pageDirection)

        val secondImport = targetRepository.importBackupJson(exported.json)

        assertEquals(0, secondImport.booksUpdated)
        assertEquals(0, secondImport.globalSettingsImported)
        assertEquals(2, secondImport.globalSettingsSkipped)
        assertEquals(0, secondImport.collectionsImported)
        assertEquals(0, secondImport.collectionMembershipsImported)
        assertEquals(1, secondImport.collectionMembershipsSkipped)
        assertEquals(0, secondImport.readerAppearancesImported)
        assertEquals(1, secondImport.readerAppearancesSkipped)
        assertEquals(0, secondImport.readingStatesImported)
        assertEquals(1, secondImport.readingStatesSkipped)
        assertEquals(0, secondImport.readingSessionsImported)
        assertEquals(1, secondImport.readingSessionsSkipped)
    }

    private fun testSettingsRepository(name: String): SettingsRepository {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = {
                root.mkdirs()
                File(root, "$name.preferences_pb")
            }
        )
        return SettingsRepository(context, dataStore)
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
