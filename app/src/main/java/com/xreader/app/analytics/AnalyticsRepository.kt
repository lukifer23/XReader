package com.xreader.app.analytics

import com.xreader.app.data.BookDao
import com.xreader.app.data.BookEntity
import com.xreader.app.data.ReadingSessionEntity
import com.xreader.app.repository.ReadingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class AnalyticsSummary(
    val totalBooks: Int,
    val finishedBooks: Int,
    val activeMillis: Long,
    val wordsRead: Int,
    val averageWpm: Int,
    val sessions: Int,
    val currentStreakDays: Int,
    val bestStreakDays: Int,
    val dailyActivity: List<DailyReadingAnalytics>,
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

data class DailyReadingAnalytics(
    val dayStartMillis: Long,
    val activeMillis: Long,
    val wordsRead: Int,
    val sessions: Int,
)

class AnalyticsRepository(
    private val bookDao: BookDao,
    private val readingRepository: ReadingRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    fun observeSummary(): Flow<AnalyticsSummary> =
        combine(bookDao.observeBooks(""), readingRepository.observeSessions()) { books, sessions ->
            AnalyticsCalculator.summarize(books, sessions, clock)
        }
}

internal object AnalyticsCalculator {
    fun summarize(
        books: List<BookEntity>,
        sessions: List<ReadingSessionEntity>,
        clock: Clock,
    ): AnalyticsSummary {
        val sessionsByBook = sessions.groupBy { it.bookId }
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
            totalBooks = books.size,
            finishedBooks = books.count { it.finished },
            activeMillis = sessions.sumOf { it.activeMillis },
            wordsRead = sessions.sumOf { it.wordsRead },
            averageWpm = weightedWpm(sessions),
            sessions = sessions.size,
            currentStreakDays = currentStreakDays(sessions, clock),
            bestStreakDays = bestStreakDays(sessions, clock),
            dailyActivity = dailyActivity(sessions, clock),
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

    private fun dailyActivity(sessions: List<ReadingSessionEntity>, clock: Clock): List<DailyReadingAnalytics> {
        val today = LocalDate.now(clock)
        val firstDay = today.minusDays(DAY_CHART_COUNT - 1L)
        val sessionsByDay = sessions.groupBy { it.startedDate(clock) }
        return (0 until DAY_CHART_COUNT).map { offset ->
            val day = firstDay.plusDays(offset.toLong())
            val daySessions = sessionsByDay[day].orEmpty()
            DailyReadingAnalytics(
                dayStartMillis = day.atStartOfDay(clock.zone).toInstant().toEpochMilli(),
                activeMillis = daySessions.sumOf { it.activeMillis },
                wordsRead = daySessions.sumOf { it.wordsRead },
                sessions = daySessions.size
            )
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

    private const val DAY_CHART_COUNT = 14
}
