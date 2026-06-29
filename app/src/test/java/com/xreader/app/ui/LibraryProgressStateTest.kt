package com.xreader.app.ui

import com.xreader.app.data.BookEntity
import com.xreader.app.data.BookFormat
import com.xreader.app.data.ReadingStateEntity
import com.xreader.app.settings.LibrarySort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryProgressStateTest {
    @Test
    fun manualFinishedOverridesLowProgressForLibraryClassification() {
        val item = item(progress = 0.0, finished = true)

        assertTrue(item.isLibraryFinished())
        assertFalse(item.isLibraryUnread())
        assertFalse(item.isLibraryInProgress())
        assertEquals(1.0, item.displayLibraryProgress(), 0.0)
    }

    @Test
    fun nearlyCompleteProgressCountsAsFinishedWithoutManualFlag() {
        val item = item(progress = 0.996, finished = false)

        assertTrue(item.isLibraryFinished())
        assertFalse(item.isLibraryInProgress())
        assertEquals(0.996, item.displayLibraryProgress(), 0.0)
    }

    @Test
    fun persistedFinishedAtCountsAsFinishedForLibraryClassification() {
        val item = item(progress = 0.42, finished = false, finishedAt = 1_700_000_000_000L)

        assertTrue(item.isLibraryFinished())
        assertFalse(item.isLibraryInProgress())
        assertFalse(item.isLibraryUnread())
        assertEquals(1.0, item.displayLibraryProgress(), 0.0)
    }

    @Test
    fun activeProgressStaysInProgress() {
        val item = item(progress = 0.42, finished = false)

        assertFalse(item.isLibraryFinished())
        assertTrue(item.isLibraryInProgress())
        assertFalse(item.isLibraryUnread())
        assertEquals(0.42, item.displayLibraryProgress(), 0.0)
    }

    @Test
    fun onePercentProgressStaysUnreadNotInProgress() {
        val item = item(progress = 0.01, finished = false)

        assertFalse(item.isLibraryFinished())
        assertFalse(item.isLibraryInProgress())
        assertTrue(item.isLibraryUnread())
    }

    @Test
    fun progressBetweenOnePercentAndCompletionThresholdStaysInProgress() {
        val item = item(progress = 0.9945, finished = false)

        assertFalse(item.isLibraryFinished())
        assertTrue(item.isLibraryInProgress())
        assertFalse(item.isLibraryUnread())
    }

    @Test
    fun etaUsesProgressWordCountAndWpm() {
        val item = item(progress = 0.5, finished = false, estimatedWpm = 250)

        assertEquals("2h 40m left", readingEtaLabel(item.book, item.state))
    }

    @Test
    fun etaRoundsUpShortRemainingTime() {
        val item = item(progress = 0.994, finished = false, estimatedWpm = 250)

        assertEquals("2m left", readingEtaLabel(item.book, item.state))
    }

    @Test
    fun etaStaysHiddenForNearlyFinishedBooks() {
        val item = item(progress = 0.999, finished = false, estimatedWpm = 250)

        assertEquals(null, readingEtaLabel(item.book, item.state))
    }

    @Test
    fun etaStaysHiddenUntilWpmIsKnown() {
        val item = item(progress = 0.5, finished = false, estimatedWpm = 0)

        assertEquals(null, readingEtaLabel(item.book, item.state))
    }

    @Test
    fun continueReadingProgressSummaryBoundsProgressAndSkipsBlankOptionalParts() {
        assertEquals("42% read", continueReadingProgressSummary(progress = 0.421, eta = null, wpm = null))
        assertEquals("42% read", continueReadingProgressSummary(progress = 0.421, eta = "  ", wpm = 0))
        assertEquals(
            "42% read • 8m left • 250 WPM",
            continueReadingProgressSummary(progress = 0.421, eta = " 8m left ", wpm = 250)
        )
        assertEquals("0% read", continueReadingProgressSummary(progress = -0.2, eta = null, wpm = null))
        assertEquals("100% read", continueReadingProgressSummary(progress = 1.8, eta = null, wpm = null))
    }

    @Test
    fun libraryActionStatusTextSkipsZeroStatusCounts() {
        assertEquals(
            "No books • Recent first",
            libraryActionStatusText(bookCount = 0, inProgress = 0, finished = 0, sort = LibrarySort.RECENT)
        )
        assertEquals(
            "1 book • 1 reading • Progress",
            libraryActionStatusText(bookCount = 1, inProgress = 1, finished = 0, sort = LibrarySort.PROGRESS)
        )
        assertEquals(
            "5 books • 2 reading • 1 finished • Title",
            libraryActionStatusText(bookCount = 5, inProgress = 2, finished = 1, sort = LibrarySort.TITLE)
        )
    }

    @Test
    fun readabilityLabelsStayCompact() {
        val clear = book(finished = false).copy(readabilityScore = 68.0, readabilityGradeLevel = 7.4)
        val dense = book(finished = false).copy(readabilityScore = 34.0, readabilityGradeLevel = 13.1)

        assertEquals("Clear • G7", readabilityCompactLabel(clear))
        assertEquals("Clear • grade 7.4 • ease 68", readabilityDetailLabel(clear))
        assertEquals("Very dense • G13", readabilityCompactLabel(dense))
        assertEquals("Very dense • grade 18.0", readabilityDetailLabel(clear.copy(readabilityScore = null, readabilityGradeLevel = 28.2)))
    }

    private fun item(
        progress: Double,
        finished: Boolean,
        estimatedWpm: Int = 0,
        finishedAt: Long? = null,
    ): BookListItem =
        BookListItem(
            book = book(finished = finished),
            state = ReadingStateEntity(
                bookId = 7L,
                locator = "locator",
                progress = progress,
                currentUnit = 4,
                totalUnits = 10,
                activeMillis = 0L,
                estimatedWpm = estimatedWpm,
                lastReadAt = 1_700_000_000_000L,
                finishedAt = finishedAt
            )
        )

    private fun book(finished: Boolean): BookEntity =
        BookEntity(
            id = 7L,
            title = "Book",
            author = "Author",
            sortTitle = "Book",
            format = BookFormat.EPUB,
            sourceExtension = "epub",
            fileName = "book.epub",
            filePath = "books/book.epub",
            checksum = "checksum",
            fileSizeBytes = 1024L,
            wordCount = 80_000,
            importedAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L,
            finished = finished
        )
}
