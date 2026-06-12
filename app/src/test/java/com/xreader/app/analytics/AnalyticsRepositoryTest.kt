package com.xreader.app.analytics

import com.xreader.app.data.BookEntity
import com.xreader.app.data.BookFormat
import com.xreader.app.data.ReadingSessionEntity
import com.xreader.app.data.ReadingStateEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
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
        assertEquals(4, summary.paceSampleSessions)
        assertEquals(2, summary.currentStreakDays)
        assertEquals(2, summary.bestStreakDays)
        assertEquals(AnalyticsRange.MONTH, summary.range)
        assertEquals(30, summary.activityBuckets.size)
        assertEquals(ActivityBucketGranularity.DAY, summary.activityBuckets.last().granularity)
        assertEquals(1_200_000L, summary.activityBuckets.last().activeMillis)
        assertEquals(2, summary.byBook.size)
        assertEquals("Red Rising", summary.byBook.first().book.title)
        assertEquals(1, summary.byAuthor.size)
        assertEquals("Pierce Brown", summary.byAuthor.single().label)
        assertEquals(4, summary.byAuthor.single().sessions)
        assertEquals(4, summary.byAuthor.single().paceSampleSessions)
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

    @Test
    fun rangeFiltersSessionTotalsAndGroupedStats() {
        val books = listOf(
            book(id = 1, title = "Recent Book", author = "Recent Author", genre = "Science Fiction"),
            book(id = 2, title = "Old Book", author = "Old Author", genre = "Fantasy")
        )
        val sessions = listOf(
            session(bookId = 1, startedAt = "2026-05-28T20:00:00Z", activeMillis = 600_000, wordsRead = 3_000),
            session(bookId = 2, startedAt = "2026-04-01T20:00:00Z", activeMillis = 900_000, wordsRead = 4_500)
        )

        val weekSummary = AnalyticsCalculator.summarize(books, sessions, clock, AnalyticsRange.WEEK)
        val allTimeSummary = AnalyticsCalculator.summarize(books, sessions, clock, AnalyticsRange.ALL_TIME)

        assertEquals(1, weekSummary.sessions)
        assertEquals(3_000, weekSummary.wordsRead)
        assertEquals("Recent Book", weekSummary.byBook.single().book.title)
        assertEquals("Recent Author", weekSummary.byAuthor.single().label)
        assertEquals(7, weekSummary.activityBuckets.size)
        assertEquals(ActivityBucketGranularity.DAY, weekSummary.activityBuckets.first().granularity)

        assertEquals(2, allTimeSummary.sessions)
        assertEquals(7_500, allTimeSummary.wordsRead)
        assertEquals(2, allTimeSummary.byBook.size)
        assertEquals(ActivityBucketGranularity.MONTH, allTimeSummary.activityBuckets.first().granularity)
    }

    @Test
    fun quarterRangeUsesWeeklyActivityBuckets() {
        val books = listOf(book(id = 1, title = "Range Book", author = "Author", genre = "Genre"))
        val sessions = listOf(
            session(bookId = 1, startedAt = "2026-03-05T20:00:00Z", activeMillis = 300_000, wordsRead = 1_000),
            session(bookId = 1, startedAt = "2026-05-28T20:00:00Z", activeMillis = 600_000, wordsRead = 2_000)
        )

        val summary = AnalyticsCalculator.summarize(books, sessions, clock, AnalyticsRange.QUARTER)

        assertEquals(AnalyticsRange.QUARTER, summary.range)
        assertEquals(13, summary.activityBuckets.size)
        assertEquals(ActivityBucketGranularity.WEEK, summary.activityBuckets.first().granularity)
        assertEquals(2, summary.sessions)
        assertEquals(3_000, summary.wordsRead)
        assertEquals(2, summary.activityBuckets.count { it.sessions > 0 })
    }

    @Test
    fun unreliablePaceSamplesDoNotInflateAverageWpm() {
        val books = listOf(book(id = 1, title = "Pace Book", author = "Author", genre = "Science Fiction"))
        val sessions = listOf(
            session(bookId = 1, startedAt = "2026-05-27T20:00:00Z", activeMillis = 10_000, wordsRead = 500),
            session(bookId = 1, startedAt = "2026-05-28T20:00:00Z", activeMillis = 600_000, wordsRead = 3_000)
        )

        val summary = AnalyticsCalculator.summarize(books, sessions, clock, AnalyticsRange.WEEK)

        assertEquals(2, summary.sessions)
        assertEquals(1, summary.paceSampleSessions)
        assertEquals(3_500, summary.wordsRead)
        assertEquals(300, summary.averageWpm)
        assertEquals(1, summary.byBook.single().paceSampleSessions)
        assertEquals(300, summary.byBook.single().averageWpm)
    }

    @Test
    fun readabilityGroupsSummarizeExistingStatsWithoutShowingWhenUnmeasured() {
        val unmeasuredBooks = listOf(book(id = 1, title = "Unmeasured", author = "Author", genre = "Science Fiction"))
        val unmeasuredSessions = listOf(
            session(bookId = 1, startedAt = "2026-05-28T20:00:00Z", activeMillis = 600_000, wordsRead = 3_000)
        )

        val unmeasuredSummary = AnalyticsCalculator.summarize(unmeasuredBooks, unmeasuredSessions, clock, AnalyticsRange.WEEK)

        assertTrue(unmeasuredSummary.byReadability.isEmpty())

        val books = listOf(
            book(id = 1, title = "Clear Book", author = "Author", genre = "Science Fiction")
                .copy(readabilityScore = 68.0, readabilityGradeLevel = 7.5),
            book(id = 2, title = "Dense Book", author = "Author", genre = "Science Fiction")
                .copy(readabilityScore = 34.0, readabilityGradeLevel = 13.2),
            book(id = 3, title = "Old Import", author = "Author", genre = "Science Fiction")
        )
        val sessions = listOf(
            session(bookId = 1, startedAt = "2026-05-26T20:00:00Z", activeMillis = 600_000, wordsRead = 3_000),
            session(bookId = 2, startedAt = "2026-05-27T20:00:00Z", activeMillis = 300_000, wordsRead = 1_500),
            session(bookId = 3, startedAt = "2026-05-28T20:00:00Z", activeMillis = 120_000, wordsRead = 600)
        )

        val summary = AnalyticsCalculator.summarize(books, sessions, clock, AnalyticsRange.WEEK)
        val groups = summary.byReadability.associateBy { it.label }

        assertEquals(setOf("Clear", "Very dense", "Not measured"), groups.keys)
        assertEquals(600_000L, groups.getValue("Clear").activeMillis)
        assertEquals(300_000L, groups.getValue("Very dense").activeMillis)
        assertEquals(120_000L, groups.getValue("Not measured").activeMillis)
    }

    @Test
    fun finishedBookCountUsesPersistedReadingStateCompletion() {
        val books = listOf(
            book(id = 1, title = "Almost Done", author = "Author", genre = "Science Fiction"),
            book(id = 2, title = "Manually Done", author = "Author", genre = "Science Fiction", finished = true),
            book(id = 3, title = "Finished Timestamp", author = "Author", genre = "Science Fiction"),
            book(id = 4, title = "In Progress", author = "Author", genre = "Science Fiction")
        )
        val states = listOf(
            state(bookId = 1, progress = 0.996, finishedAt = null),
            state(bookId = 3, progress = 0.42, finishedAt = 1_234L),
            state(bookId = 4, progress = 0.994, finishedAt = null)
        )

        val summary = AnalyticsCalculator.summarize(books, emptyList(), clock, states = states)

        assertEquals(4, summary.totalBooks)
        assertEquals(3, summary.finishedBooks)
    }

    @Test
    fun csvExportBuildsSpreadsheetFriendlyRowsWithoutPrivateBookData() {
        val books = listOf(
            book(id = 1, title = "Red, Rising", author = "Pierce Brown", genre = "Science Fiction")
                .copy(readabilityScore = 68.0, readabilityGradeLevel = 7.5)
        )
        val sessions = listOf(
            session(bookId = 1, startedAt = "2026-05-28T20:00:00Z", activeMillis = 600_000, wordsRead = 3_000)
        )
        val summaries = AnalyticsRange.entries.map { range ->
            AnalyticsCalculator.summarize(books, sessions, clock, range)
        }

        val csv = AnalyticsExportCsv.build(exportedAt = 123_456L, summaries = summaries)

        assertTrue(csv.startsWith("record_type,exported_at,range,range_label"))
        assertTrue(csv.contains("average_wpm,pace_sample_sessions,sessions"))
        assertTrue(csv.contains("summary,123456,MONTH,30 days"))
        assertTrue(csv.contains("book,123456,MONTH,30 days,,,\"Red, Rising\",Pierce Brown"))
        assertTrue(csv.contains("readability,123456,MONTH,30 days,,,Clear"))
        assertTrue(csv.contains("activity,123456,MONTH,30 days"))
        assertFalse(csv.contains("library/books"))
        assertFalse(csv.contains("checksum-"))
    }

    @Test
    fun jsonExportIncludesBookReadabilityWithoutPrivateBookData() {
        val books = listOf(
            book(id = 1, title = "Readable Book", author = "Author", genre = "Science Fiction")
                .copy(readabilityScore = 72.5, readabilityGradeLevel = 6.8)
        )
        val sessions = listOf(
            session(bookId = 1, startedAt = "2026-05-28T20:00:00Z", activeMillis = 600_000, wordsRead = 3_000)
        )
        val summaries = listOf(AnalyticsCalculator.summarize(books, sessions, clock, AnalyticsRange.WEEK))

        val json = AnalyticsExportJson.build(exportedAt = 123_456L, summaries = summaries).toString()
        val root = JSONObject(json)
        val book = root
            .getJSONArray("ranges")
            .getJSONObject(0)
            .getJSONArray("books")
            .getJSONObject(0)
        val readability = root
            .getJSONArray("ranges")
            .getJSONObject(0)
            .getJSONArray("readabilityLevels")
            .getJSONObject(0)

        assertEquals(4, root.getInt("version"))
        assertEquals(72.5, book.getDouble("readabilityScore"), 0.001)
        assertEquals(6.8, book.getDouble("readabilityGradeLevel"), 0.001)
        assertEquals("Clear", readability.getString("label"))
        assertFalse(json.contains("library/books"))
        assertFalse(json.contains("checksum-"))
    }

    private fun book(
        id: Long,
        title: String,
        author: String,
        genre: String?,
        finished: Boolean = false,
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
            finished = finished
        )

    private fun state(
        bookId: Long,
        progress: Double,
        finishedAt: Long?,
    ): ReadingStateEntity =
        ReadingStateEntity(
            bookId = bookId,
            locator = "{}",
            progress = progress,
            currentUnit = 1,
            totalUnits = 100,
            activeMillis = 0,
            estimatedWpm = 0,
            lastReadAt = 1_000,
            finishedAt = finishedAt
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
