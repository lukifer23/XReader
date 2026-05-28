package com.xreader.app.analytics

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xreader.app.data.BookEntity
import com.xreader.app.data.BookFormat
import com.xreader.app.data.ReadingSessionEntity
import com.xreader.app.data.XReaderDatabase
import com.xreader.app.repository.ReadingRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AnalyticsExportInstrumentedTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-05-28T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun analyticsExportJsonIncludesRangesAndOmitsPrivateLibraryPaths() {
        val books = listOf(
            book(id = 1, title = "Red Rising", author = "Pierce Brown", genre = "Science Fiction")
        )
        val sessions = listOf(
            session(bookId = 1, startedAt = "2026-05-28T20:00:00Z", activeMillis = 600_000, wordsRead = 3_000)
        )
        val summaries = AnalyticsRange.entries.map { range ->
            AnalyticsCalculator.summarize(books, sessions, clock, range)
        }

        val root = AnalyticsExportJson.build(exportedAt = 123_456L, summaries = summaries)
        val json = root.toString()
        val ranges = JSONObject(json).getJSONArray("ranges")
        val month = ranges.getJSONObject(AnalyticsRange.MONTH.ordinal)
        val firstBook = month.getJSONArray("books").getJSONObject(0)

        assertEquals("com.xreader.analytics.v1", root.getString("format"))
        assertEquals(1, root.getInt("version"))
        assertEquals(123_456L, root.getLong("exportedAt"))
        assertEquals(AnalyticsRange.entries.size, ranges.length())
        assertEquals("MONTH", month.getString("range"))
        assertEquals("30 days", month.getString("label"))
        assertEquals("Red Rising", firstBook.getString("title"))
        assertEquals("Pierce Brown", firstBook.getString("author"))
        assertEquals(3_000, month.getInt("wordsRead"))
        assertFalse(json.contains("library/books"))
        assertFalse(json.contains("checksum-"))
    }

    @Test
    fun analyticsExportServiceWritesJsonToUri() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val db = Room.inMemoryDatabaseBuilder(context, XReaderDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val outputFile = File(context.cacheDir, "xreader-analytics-export-test.json")
        outputFile.delete()
        try {
            val bookId = db.books().insert(
                book(id = 0, title = "Red Rising", author = "Pierce Brown", genre = "Science Fiction")
            )
            db.reading().insertSession(
                session(
                    bookId = bookId,
                    startedAt = "2026-05-28T20:00:00Z",
                    activeMillis = 600_000,
                    wordsRead = 3_000
                )
            )
            val repository = AnalyticsRepository(
                bookDao = db.books(),
                readingRepository = ReadingRepository(db.reading()),
                clock = clock
            )
            val service = AnalyticsExportService(context, repository)

            val result = service.exportTo(Uri.fromFile(outputFile))
            val root = JSONObject(outputFile.readText())

            assertEquals(AnalyticsRange.entries.size, result.ranges)
            assertEquals(1, result.readingSessions)
            assertEquals("com.xreader.analytics.v1", root.getString("format"))
            assertEquals(AnalyticsRange.entries.size, root.getJSONArray("ranges").length())
        } finally {
            db.close()
            outputFile.delete()
        }
    }

    @Test
    fun analyticsExportCsvIncludesFlatRowsAndOmitsPrivateLibraryPaths() {
        val books = listOf(
            book(id = 1, title = "Red, Rising", author = "Pierce Brown", genre = "Science Fiction")
        )
        val sessions = listOf(
            session(bookId = 1, startedAt = "2026-05-28T20:00:00Z", activeMillis = 600_000, wordsRead = 3_000)
        )
        val summaries = AnalyticsRange.entries.map { range ->
            AnalyticsCalculator.summarize(books, sessions, clock, range)
        }

        val csv = AnalyticsExportCsv.build(exportedAt = 123_456L, summaries = summaries)

        assertTrue(csv.startsWith("record_type,exported_at,range,range_label"))
        assertTrue(csv.contains("summary,123456,MONTH,30 days"))
        assertTrue(csv.contains("book,123456,MONTH,30 days,,,\"Red, Rising\",Pierce Brown,Red Rising,Science Fiction,2026"))
        assertTrue(csv.contains("author,123456,MONTH,30 days,,,Pierce Brown"))
        assertTrue(csv.contains("genre,123456,MONTH,30 days,,,Science Fiction"))
        assertFalse(csv.contains("library/books"))
        assertFalse(csv.contains("checksum-"))
    }

    @Test
    fun analyticsExportServiceWritesCsvToUri() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val db = Room.inMemoryDatabaseBuilder(context, XReaderDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val outputFile = File(context.cacheDir, "xreader-analytics-export-test.csv")
        outputFile.delete()
        try {
            val bookId = db.books().insert(
                book(id = 0, title = "Red Rising", author = "Pierce Brown", genre = "Science Fiction")
            )
            db.reading().insertSession(
                session(
                    bookId = bookId,
                    startedAt = "2026-05-28T20:00:00Z",
                    activeMillis = 600_000,
                    wordsRead = 3_000
                )
            )
            val repository = AnalyticsRepository(
                bookDao = db.books(),
                readingRepository = ReadingRepository(db.reading()),
                clock = clock
            )
            val service = AnalyticsExportService(context, repository)

            val result = service.exportCsvTo(Uri.fromFile(outputFile))
            val csv = outputFile.readText()

            assertEquals(AnalyticsRange.entries.size, result.ranges)
            assertEquals(1, result.readingSessions)
            assertTrue(csv.contains("summary,"))
            assertTrue(csv.contains("book,"))
            assertTrue(csv.contains("Red Rising"))
        } finally {
            db.close()
            outputFile.delete()
        }
    }

    private fun book(
        id: Long,
        title: String,
        author: String,
        genre: String?,
    ): BookEntity =
        BookEntity(
            id = id,
            title = title,
            author = author,
            sortTitle = title.lowercase(),
            series = "Red Rising",
            seriesIndex = 1.0,
            genre = genre,
            year = 2026,
            description = null,
            language = "en",
            format = BookFormat.EPUB,
            sourceExtension = "epub",
            fileName = "$id.epub",
            filePath = "library/books/$id.epub",
            coverImagePath = null,
            checksum = "checksum-$id",
            fileSizeBytes = 1024,
            wordCount = 100_000,
            pageCount = null,
            importedAt = 1_000,
            updatedAt = 1_000,
            lastOpenedAt = null,
            favorite = false,
            finished = false
        )

    private fun session(
        bookId: Long,
        startedAt: String,
        activeMillis: Long,
        wordsRead: Int,
    ): ReadingSessionEntity =
        ReadingSessionEntity(
            bookId = bookId,
            startedAt = Instant.parse(startedAt).toEpochMilli(),
            endedAt = Instant.parse(startedAt).plusMillis(activeMillis).toEpochMilli(),
            activeMillis = activeMillis,
            startUnit = 0,
            endUnit = 1,
            wordsRead = wordsRead,
            wpm = 300
        )
}
