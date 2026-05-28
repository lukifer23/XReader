package com.xreader.app.analytics

import com.xreader.app.data.BookEntity
import com.xreader.app.data.BookFormat
import com.xreader.app.data.ReadingSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AnalyticsRepositoryTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-05-28T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun summaryIncludesActivityTrendsStreaksAndGroupedStats() {
        val books = listOf(
            book(id = 1, title = "Red Rising", author = "Pierce Brown", genre = "Science Fiction"),
            book(id = 2, title = "Golden Son", author = "Pierce Brown", genre = "Science Fiction"),
            book(id = 3, title = "Unread", author = "Nobody", genre = "Reference")
        )
        val sessions = listOf(
            session(bookId = 1, startedAt = "2026-05-24T20:00:00Z", activeMillis = 600_000, wordsRead = 2_500),
            session(bookId = 1, startedAt = "2026-05-25T20:00:00Z", activeMillis = 900_000, wordsRead = 4_000),
            session(bookId = 2, startedAt = "2026-05-27T20:00:00Z", activeMillis = 300_000, wordsRead = 1_000),
            session(bookId = 1, startedAt = "2026-05-28T20:00:00Z", activeMillis = 1_200_000, wordsRead = 5_000)
        )

        val summary = AnalyticsCalculator.summarize(books, sessions, clock)

        assertEquals(3, summary.totalBooks)
        assertEquals(4, summary.sessions)
        assertEquals(2, summary.currentStreakDays)
        assertEquals(2, summary.bestStreakDays)
        assertEquals(14, summary.dailyActivity.size)
        assertEquals(1_200_000L, summary.dailyActivity.last().activeMillis)
        assertEquals(2, summary.byBook.size)
        assertEquals("Red Rising", summary.byBook.first().book.title)
        assertEquals(1, summary.byAuthor.size)
        assertEquals("Pierce Brown", summary.byAuthor.single().label)
        assertEquals(4, summary.byAuthor.single().sessions)
        assertEquals("Science Fiction", summary.byGenre.single().label)
    }

    @Test
    fun currentStreakExpiresWhenLatestReadingIsOlderThanYesterday() {
        val books = listOf(book(id = 1, title = "Old Book", author = "Author", genre = null))
        val sessions = listOf(
            session(bookId = 1, startedAt = "2026-05-24T20:00:00Z", activeMillis = 600_000, wordsRead = 2_500)
        )

        val summary = AnalyticsCalculator.summarize(books, sessions, clock)

        assertEquals(0, summary.currentStreakDays)
        assertEquals(1, summary.bestStreakDays)
        assertEquals("No genre", summary.byGenre.single().label)
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
            series = null,
            seriesIndex = null,
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
            wpm = 250
        )
}
