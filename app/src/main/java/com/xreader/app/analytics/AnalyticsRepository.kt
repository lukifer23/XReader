package com.xreader.app.analytics

import com.xreader.app.data.BookDao
import com.xreader.app.data.BookEntity
import com.xreader.app.data.ReadingSessionEntity
import com.xreader.app.repository.ReadingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class AnalyticsSummary(
    val totalBooks: Int,
    val finishedBooks: Int,
    val activeMillis: Long,
    val wordsRead: Int,
    val averageWpm: Int,
    val sessions: Int,
    val byBook: List<BookAnalytics>,
)

data class BookAnalytics(
    val book: BookEntity,
    val activeMillis: Long,
    val wordsRead: Int,
    val averageWpm: Int,
    val sessions: Int,
)

class AnalyticsRepository(
    private val bookDao: BookDao,
    private val readingRepository: ReadingRepository,
) {
    fun observeSummary(): Flow<AnalyticsSummary> =
        combine(bookDao.observeBooks(""), readingRepository.observeSessions()) { books, sessions ->
            val byBook = books.map { book ->
                val bookSessions = sessions.filter { it.bookId == book.id }
                BookAnalytics(
                    book = book,
                    activeMillis = bookSessions.sumOf { it.activeMillis },
                    wordsRead = bookSessions.sumOf { it.wordsRead },
                    averageWpm = weightedWpm(bookSessions),
                    sessions = bookSessions.size
                )
            }.sortedByDescending { it.activeMillis }
            AnalyticsSummary(
                totalBooks = books.size,
                finishedBooks = books.count { it.finished },
                activeMillis = sessions.sumOf { it.activeMillis },
                wordsRead = sessions.sumOf { it.wordsRead },
                averageWpm = weightedWpm(sessions),
                sessions = sessions.size,
                byBook = byBook
            )
        }

    private fun weightedWpm(sessions: List<ReadingSessionEntity>): Int {
        val active = sessions.sumOf { it.activeMillis }
        val words = sessions.sumOf { it.wordsRead }
        if (active <= 0L || words <= 0) return 0
        return (words / (active / 60_000.0)).toInt().coerceIn(0, 1200)
    }
}
