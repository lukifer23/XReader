package com.xreader.app.analytics

import com.xreader.app.data.BookDao
import com.xreader.app.data.BookEntity
import com.xreader.app.data.ReadingSessionEntity
import com.xreader.app.repository.ReadingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.json.JSONArray
import org.json.JSONObject
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.temporal.ChronoUnit

enum class AnalyticsRange(val label: String) {
    WEEK("7 days"),
    MONTH("30 days"),
    QUARTER("13 weeks"),
    ALL_TIME("All time"),
}

enum class ActivityBucketGranularity {
    DAY,
    WEEK,
    MONTH,
    YEAR,
}

data class AnalyticsSummary(
    val range: AnalyticsRange,
    val totalBooks: Int,
    val finishedBooks: Int,
    val activeMillis: Long,
    val wordsRead: Int,
    val averageWpm: Int,
    val sessions: Int,
    val currentStreakDays: Int,
    val bestStreakDays: Int,
    val activityBuckets: List<ActivityBucketAnalytics>,
    val byBook: List<BookAnalytics>,
    val byAuthor: List<GroupAnalytics>,
    val byGenre: List<GroupAnalytics>,
)

data class BookAnalytics(
    val book: BookEntity,
    val activeMillis: Long,
    val wordsRead: Int,
    val averageWpm: Int,
    val sessions: Int,
)

data class GroupAnalytics(
    val label: String,
    val activeMillis: Long,
    val wordsRead: Int,
    val averageWpm: Int,
    val sessions: Int,
)

data class ActivityBucketAnalytics(
    val startMillis: Long,
    val granularity: ActivityBucketGranularity,
    val activeMillis: Long,
    val wordsRead: Int,
    val sessions: Int,
)

data class AnalyticsExportResult(
    val json: String,
    val ranges: Int,
    val readingSessions: Int,
)

data class AnalyticsCsvExportResult(
    val csv: String,
    val ranges: Int,
    val readingSessions: Int,
)

class AnalyticsRepository(
    private val bookDao: BookDao,
    private val readingRepository: ReadingRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    fun observeSummary(range: AnalyticsRange): Flow<AnalyticsSummary> =
        combine(bookDao.observeBooks(""), readingRepository.observeSessions()) { books, sessions ->
            AnalyticsCalculator.summarize(books, sessions, clock, range)
        }

    suspend fun exportSummariesJson(): AnalyticsExportResult {
        val books = bookDao.booksForBackup()
        val sessions = readingRepository.allSessions()
        val summaries = AnalyticsRange.entries.map { range ->
            AnalyticsCalculator.summarize(books, sessions, clock, range)
        }
        return AnalyticsExportResult(
            json = AnalyticsExportJson.build(
                exportedAt = clock.millis(),
                summaries = summaries
            ).toString(2),
            ranges = summaries.size,
            readingSessions = sessions.size
        )
    }

    suspend fun exportSummariesCsv(): AnalyticsCsvExportResult {
        val books = bookDao.booksForBackup()
        val sessions = readingRepository.allSessions()
        val summaries = AnalyticsRange.entries.map { range ->
            AnalyticsCalculator.summarize(books, sessions, clock, range)
        }
        return AnalyticsCsvExportResult(
            csv = AnalyticsExportCsv.build(
                exportedAt = clock.millis(),
                summaries = summaries
            ),
            ranges = summaries.size,
            readingSessions = sessions.size
        )
    }
}

internal object AnalyticsExportJson {
    fun build(
        exportedAt: Long,
        summaries: List<AnalyticsSummary>,
    ): JSONObject =
        JSONObject()
            .put("format", EXPORT_FORMAT)
            .put("version", 1)
            .put("exportedAt", exportedAt)
            .put(
                "ranges",
                JSONArray().also { array ->
                    summaries.forEach { array.put(it.toJson()) }
                }
            )

    private fun AnalyticsSummary.toJson(): JSONObject =
        JSONObject()
            .put("range", range.name)
            .put("label", range.label)
            .put("totalBooks", totalBooks)
            .put("finishedBooks", finishedBooks)
            .put("activeMillis", activeMillis)
            .put("wordsRead", wordsRead)
            .put("averageWpm", averageWpm)
            .put("sessions", sessions)
            .put("currentStreakDays", currentStreakDays)
            .put("bestStreakDays", bestStreakDays)
            .put(
                "activity",
                JSONArray().also { array ->
                    activityBuckets.forEach { bucket ->
                        array.put(
                            JSONObject()
                                .put("startMillis", bucket.startMillis)
                                .put("granularity", bucket.granularity.name)
                                .put("activeMillis", bucket.activeMillis)
                                .put("wordsRead", bucket.wordsRead)
                                .put("sessions", bucket.sessions)
                        )
                    }
                }
            )
            .put("books", JSONArray().also { array -> byBook.forEach { array.put(it.toJson()) } })
            .put("authors", JSONArray().also { array -> byAuthor.forEach { array.put(it.toJson()) } })
            .put("genres", JSONArray().also { array -> byGenre.forEach { array.put(it.toJson()) } })

    private fun BookAnalytics.toJson(): JSONObject =
        JSONObject()
            .put("title", book.title)
            .put("author", book.author)
            .putNullable("series", book.series)
            .putNullable("genre", book.genre)
            .putNullable("year", book.year)
            .put("activeMillis", activeMillis)
            .put("wordsRead", wordsRead)
            .put("averageWpm", averageWpm)
            .put("sessions", sessions)

    private fun GroupAnalytics.toJson(): JSONObject =
        JSONObject()
            .put("label", label)
            .put("activeMillis", activeMillis)
            .put("wordsRead", wordsRead)
            .put("averageWpm", averageWpm)
            .put("sessions", sessions)

    private fun JSONObject.putNullable(name: String, value: Any?): JSONObject =
        put(name, value ?: JSONObject.NULL)

    private const val EXPORT_FORMAT = "com.xreader.analytics.v1"
}

internal object AnalyticsExportCsv {
    fun build(
        exportedAt: Long,
        summaries: List<AnalyticsSummary>,
    ): String {
        val rows = mutableListOf<List<Any?>>()
        rows += HEADER
        summaries.forEach { summary ->
            rows += listOf(
                "summary",
                exportedAt,
                summary.range.name,
                summary.range.label,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                summary.totalBooks,
                summary.finishedBooks,
                summary.activeMillis,
                summary.wordsRead,
                summary.averageWpm,
                summary.sessions,
                summary.currentStreakDays,
                summary.bestStreakDays
            )
            summary.activityBuckets.forEach { bucket ->
                rows += listOf(
                    "activity",
                    exportedAt,
                    summary.range.name,
                    summary.range.label,
                    bucket.startMillis,
                    bucket.granularity.name,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    bucket.activeMillis,
                    bucket.wordsRead,
                    null,
                    bucket.sessions,
                    null,
                    null
                )
            }
            summary.byBook.forEach { row ->
                rows += listOf(
                    "book",
                    exportedAt,
                    summary.range.name,
                    summary.range.label,
                    null,
                    null,
                    row.book.title,
                    row.book.author,
                    row.book.series,
                    row.book.genre,
                    row.book.year,
                    null,
                    null,
                    row.activeMillis,
                    row.wordsRead,
                    row.averageWpm,
                    row.sessions,
                    null,
                    null
                )
            }
            summary.byAuthor.forEach { row ->
                rows += groupRow("author", exportedAt, summary, row)
            }
            summary.byGenre.forEach { row ->
                rows += groupRow("genre", exportedAt, summary, row)
            }
        }
        return rows.joinToString("\n") { row -> row.joinToString(",") { it.csvCell() } } + "\n"
    }

    private fun groupRow(
        type: String,
        exportedAt: Long,
        summary: AnalyticsSummary,
        row: GroupAnalytics,
    ): List<Any?> =
        listOf(
            type,
            exportedAt,
            summary.range.name,
            summary.range.label,
            null,
            null,
            row.label,
            null,
            null,
            null,
            null,
            null,
            null,
            row.activeMillis,
            row.wordsRead,
            row.averageWpm,
            row.sessions,
            null,
            null
        )

    private fun Any?.csvCell(): String {
        val value = this?.toString().orEmpty()
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    private val HEADER = listOf(
        "record_type",
        "exported_at",
        "range",
        "range_label",
        "bucket_start_millis",
        "bucket_granularity",
        "label_or_title",
        "author",
        "series",
        "genre",
        "year",
        "total_books",
        "finished_books",
        "active_millis",
        "words_read",
        "average_wpm",
        "sessions",
        "current_streak_days",
        "best_streak_days"
    )
}

internal object AnalyticsCalculator {
    fun summarize(
        books: List<BookEntity>,
        sessions: List<ReadingSessionEntity>,
        clock: Clock,
        range: AnalyticsRange = AnalyticsRange.MONTH,
    ): AnalyticsSummary {
        val periodSessions = sessions.filterForRange(range, clock)
        val sessionsByBook = periodSessions.groupBy { it.bookId }
        val byBook = books.mapNotNull { book ->
            val bookSessions = sessionsByBook[book.id].orEmpty()
            if (bookSessions.isEmpty()) return@mapNotNull null
            BookAnalytics(
                book = book,
                activeMillis = bookSessions.sumOf { it.activeMillis },
                wordsRead = bookSessions.sumOf { it.wordsRead },
                averageWpm = weightedWpm(bookSessions),
                sessions = bookSessions.size
            )
        }.sortedWith(
            compareByDescending<BookAnalytics> { it.activeMillis }
                .thenBy { it.book.sortTitle.lowercase() }
        )

        return AnalyticsSummary(
            range = range,
            totalBooks = books.size,
            finishedBooks = books.count { it.finished },
            activeMillis = periodSessions.sumOf { it.activeMillis },
            wordsRead = periodSessions.sumOf { it.wordsRead },
            averageWpm = weightedWpm(periodSessions),
            sessions = periodSessions.size,
            currentStreakDays = currentStreakDays(sessions, clock),
            bestStreakDays = bestStreakDays(sessions, clock),
            activityBuckets = activityBuckets(sessions, clock, range),
            byBook = byBook,
            byAuthor = grouped(byBook) { it.book.author.ifBlank { "Unknown Author" } },
            byGenre = grouped(byBook) { it.book.genre?.takeIf(String::isNotBlank) ?: "No genre" }
        )
    }

    private fun grouped(
        rows: List<BookAnalytics>,
        label: (BookAnalytics) -> String,
    ): List<GroupAnalytics> =
        rows.groupBy(label)
            .map { (name, groupRows) ->
                GroupAnalytics(
                    label = name,
                    activeMillis = groupRows.sumOf { it.activeMillis },
                    wordsRead = groupRows.sumOf { it.wordsRead },
                    averageWpm = weightedWpm(
                        activeMillis = groupRows.sumOf { it.activeMillis },
                        wordsRead = groupRows.sumOf { it.wordsRead }
                    ),
                    sessions = groupRows.sumOf { it.sessions }
                )
            }
            .sortedWith(compareByDescending<GroupAnalytics> { it.activeMillis }.thenBy { it.label.lowercase() })

    private fun activityBuckets(
        sessions: List<ReadingSessionEntity>,
        clock: Clock,
        range: AnalyticsRange,
    ): List<ActivityBucketAnalytics> =
        when (range) {
            AnalyticsRange.WEEK -> dailyActivity(sessions, clock, dayCount = 7)
            AnalyticsRange.MONTH -> dailyActivity(sessions, clock, dayCount = 30)
            AnalyticsRange.QUARTER -> rollingWeekActivity(sessions, clock)
            AnalyticsRange.ALL_TIME -> allTimeActivity(sessions, clock)
        }

    private fun dailyActivity(
        sessions: List<ReadingSessionEntity>,
        clock: Clock,
        dayCount: Int,
    ): List<ActivityBucketAnalytics> {
        val today = LocalDate.now(clock)
        val firstDay = today.minusDays(dayCount - 1L)
        val sessionsByDay = sessions.groupBy { it.startedDate(clock) }
        return (0 until dayCount).map { offset ->
            val day = firstDay.plusDays(offset.toLong())
            val daySessions = sessionsByDay[day].orEmpty()
            ActivityBucketAnalytics(
                startMillis = day.atStartOfDay(clock.zone).toInstant().toEpochMilli(),
                granularity = ActivityBucketGranularity.DAY,
                activeMillis = daySessions.sumOf { it.activeMillis },
                wordsRead = daySessions.sumOf { it.wordsRead },
                sessions = daySessions.size
            )
        }
    }

    private fun rollingWeekActivity(
        sessions: List<ReadingSessionEntity>,
        clock: Clock,
    ): List<ActivityBucketAnalytics> {
        val today = LocalDate.now(clock)
        val firstDay = today.minusDays(WEEK_CHART_COUNT * DAYS_PER_WEEK - 1L)
        return (0 until WEEK_CHART_COUNT).map { offset ->
            val start = firstDay.plusDays(offset * DAYS_PER_WEEK)
            val endExclusive = start.plusDays(DAYS_PER_WEEK)
            val bucketSessions = sessions.filter { session ->
                val date = session.startedDate(clock)
                !date.isBefore(start) && date.isBefore(endExclusive)
            }
            ActivityBucketAnalytics(
                startMillis = start.atStartOfDay(clock.zone).toInstant().toEpochMilli(),
                granularity = ActivityBucketGranularity.WEEK,
                activeMillis = bucketSessions.sumOf { it.activeMillis },
                wordsRead = bucketSessions.sumOf { it.wordsRead },
                sessions = bucketSessions.size
            )
        }
    }

    private fun allTimeActivity(
        sessions: List<ReadingSessionEntity>,
        clock: Clock,
    ): List<ActivityBucketAnalytics> {
        val dates = sessions.map { it.startedDate(clock) }
        if (dates.isEmpty()) return monthlyActivity(
            firstMonth = YearMonth.now(clock).minusMonths(DEFAULT_EMPTY_MONTHS - 1L),
            lastMonth = YearMonth.now(clock),
            sessions = sessions,
            clock = clock
        )
        val firstDate = dates.minOrNull() ?: LocalDate.now(clock)
        val lastDate = dates.maxOrNull() ?: LocalDate.now(clock)
        val firstMonth = YearMonth.from(firstDate)
        val lastMonth = YearMonth.from(lastDate)
        val monthSpan = ChronoUnit.MONTHS.between(firstMonth, lastMonth) + 1L
        return if (monthSpan <= MAX_MONTH_BUCKETS) {
            monthlyActivity(firstMonth, lastMonth, sessions, clock)
        } else {
            yearlyActivity(Year.from(firstDate), Year.from(lastDate), sessions, clock)
        }
    }

    private fun monthlyActivity(
        firstMonth: YearMonth,
        lastMonth: YearMonth,
        sessions: List<ReadingSessionEntity>,
        clock: Clock,
    ): List<ActivityBucketAnalytics> {
        val monthCount = ChronoUnit.MONTHS.between(firstMonth, lastMonth).toInt().coerceAtLeast(0) + 1
        return (0 until monthCount).map { offset ->
            val month = firstMonth.plusMonths(offset.toLong())
            val bucketSessions = sessions.filter { YearMonth.from(it.startedDate(clock)) == month }
            ActivityBucketAnalytics(
                startMillis = month.atDay(1).atStartOfDay(clock.zone).toInstant().toEpochMilli(),
                granularity = ActivityBucketGranularity.MONTH,
                activeMillis = bucketSessions.sumOf { it.activeMillis },
                wordsRead = bucketSessions.sumOf { it.wordsRead },
                sessions = bucketSessions.size
            )
        }
    }

    private fun yearlyActivity(
        firstYear: Year,
        lastYear: Year,
        sessions: List<ReadingSessionEntity>,
        clock: Clock,
    ): List<ActivityBucketAnalytics> {
        val yearCount = (lastYear.value - firstYear.value).coerceAtLeast(0) + 1
        return (0 until yearCount).map { offset ->
            val year = Year.of(firstYear.value + offset)
            val bucketSessions = sessions.filter { Year.from(it.startedDate(clock)) == year }
            ActivityBucketAnalytics(
                startMillis = year.atDay(1).atStartOfDay(clock.zone).toInstant().toEpochMilli(),
                granularity = ActivityBucketGranularity.YEAR,
                activeMillis = bucketSessions.sumOf { it.activeMillis },
                wordsRead = bucketSessions.sumOf { it.wordsRead },
                sessions = bucketSessions.size
            )
        }
    }

    private fun List<ReadingSessionEntity>.filterForRange(
        range: AnalyticsRange,
        clock: Clock,
    ): List<ReadingSessionEntity> {
        val firstDay = range.firstDay(clock) ?: return this
        return filter { !it.startedDate(clock).isBefore(firstDay) }
    }

    private fun AnalyticsRange.firstDay(clock: Clock): LocalDate? {
        val today = LocalDate.now(clock)
        return when (this) {
            AnalyticsRange.WEEK -> today.minusDays(6)
            AnalyticsRange.MONTH -> today.minusDays(29)
            AnalyticsRange.QUARTER -> today.minusDays(WEEK_CHART_COUNT * DAYS_PER_WEEK - 1L)
            AnalyticsRange.ALL_TIME -> null
        }
    }

    private fun currentStreakDays(sessions: List<ReadingSessionEntity>, clock: Clock): Int {
        val dates = activeDates(sessions, clock)
        if (dates.isEmpty()) return 0
        val today = LocalDate.now(clock)
        var day = when {
            today in dates -> today
            today.minusDays(1) in dates -> today.minusDays(1)
            else -> return 0
        }
        var streak = 0
        while (day in dates) {
            streak += 1
            day = day.minusDays(1)
        }
        return streak
    }

    private fun bestStreakDays(sessions: List<ReadingSessionEntity>, clock: Clock): Int {
        val dates = activeDates(sessions, clock).sorted()
        if (dates.isEmpty()) return 0
        var best = 1
        var current = 1
        dates.zipWithNext { previous, next ->
            current = if (ChronoUnit.DAYS.between(previous, next) == 1L) current + 1 else 1
            best = maxOf(best, current)
        }
        return best
    }

    private fun activeDates(sessions: List<ReadingSessionEntity>, clock: Clock): Set<LocalDate> =
        sessions
            .asSequence()
            .filter { it.activeMillis > 0L || it.wordsRead > 0 }
            .map { it.startedDate(clock) }
            .toSet()

    private fun ReadingSessionEntity.startedDate(clock: Clock): LocalDate =
        Instant.ofEpochMilli(startedAt).atZone(clock.zone).toLocalDate()

    private fun weightedWpm(sessions: List<ReadingSessionEntity>): Int =
        weightedWpm(
            activeMillis = sessions.sumOf { it.activeMillis },
            wordsRead = sessions.sumOf { it.wordsRead }
        )

    private fun weightedWpm(activeMillis: Long, wordsRead: Int): Int {
        val active = activeMillis
        val words = wordsRead
        if (active <= 0L || words <= 0) return 0
        return (words / (active / 60_000.0)).toInt().coerceIn(0, 1200)
    }

    private const val DAYS_PER_WEEK = 7L
    private const val WEEK_CHART_COUNT = 13L
    private const val DEFAULT_EMPTY_MONTHS = 12L
    private const val MAX_MONTH_BUCKETS = 18L
}
