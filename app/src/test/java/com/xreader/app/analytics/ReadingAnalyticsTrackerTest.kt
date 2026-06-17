package com.xreader.app.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class ReadingAnalyticsTrackerTest {
    @Test
    fun recordsProgressAndSessionWpm() {
        val clock = MutableClock()
        val tracker = ReadingAnalyticsTracker(
            bookId = 7L,
            totalUnits = 10,
            wordsForUnit = { 100 },
            idleTimeoutMillis = 90_000L,
            clock = clock
        )

        tracker.record(0)
        clock.advance(60_000L)
        val state = tracker.record(2)
        clock.advance(10_000L)
        val session = requireNotNull(tracker.finish())

        assertEquals(0.3, state.progress, 0.001)
        assertEquals(200, state.estimatedWpm)
        assertEquals(200, session.wordsRead)
    }

    @Test
    fun firstVisiblePageAnchorsSessionWithoutCountingWords() {
        val clock = MutableClock()
        val tracker = ReadingAnalyticsTracker(
            bookId = 7L,
            totalUnits = 10,
            wordsForUnit = { 120 },
            idleTimeoutMillis = 90_000L,
            clock = clock
        )

        tracker.record(unit = 4, locator = "same-page", progressOverride = 0.5)
        clock.advance(45_000L)
        val flush = requireNotNull(tracker.flush())

        assertEquals("same-page", flush.state.locator)
        assertEquals(45_000L, flush.state.activeMillis)
        assertEquals(0, flush.state.estimatedWpm)
        assertNull(flush.session)
    }

    @Test
    fun snapshotPersistsTimeSpentWithoutPageTurn() {
        val clock = MutableClock()
        val tracker = ReadingAnalyticsTracker(
            bookId = 7L,
            totalUnits = 10,
            wordsForUnit = { 120 },
            idleTimeoutMillis = 90_000L,
            clock = clock
        )

        tracker.record(unit = 4, locator = "same-page", progressOverride = 0.5)
        clock.advance(30_000L)
        val state = requireNotNull(tracker.snapshot())

        assertEquals("same-page", state.locator)
        assertEquals(0.5, state.progress, 0.001)
        assertEquals(30_000L, state.activeMillis)
        assertEquals(4, state.currentUnit)
    }

    @Test
    fun flushEndsSessionAndReturnsFinalState() {
        val clock = MutableClock()
        val tracker = ReadingAnalyticsTracker(
            bookId = 7L,
            totalUnits = 10,
            wordsForUnit = { 100 },
            idleTimeoutMillis = 90_000L,
            clock = clock
        )

        tracker.record(1, locator = "start", progressOverride = 0.2)
        clock.advance(20_000L)
        tracker.record(3, locator = "end", progressOverride = 0.4)
        clock.advance(10_000L)
        val flush = requireNotNull(tracker.flush())

        assertEquals("end", flush.state.locator)
        assertEquals(30_000L, flush.state.activeMillis)
        assertEquals(200, flush.session?.wordsRead)
        assertEquals(400, flush.state.estimatedWpm)
    }

    @Test
    fun seededResumeRetainsSavedPaceWithoutCountingCurrentPageAgain() {
        val clock = MutableClock()
        val tracker = ReadingAnalyticsTracker(
            bookId = 7L,
            totalUnits = 10,
            wordsForUnit = { 100 },
            idleTimeoutMillis = 90_000L,
            clock = clock
        )

        tracker.seed(unit = 5, locator = "saved", progressOverride = 0.55, retainedEstimatedWpm = 240)
        clock.advance(20_000L)
        val flush = requireNotNull(tracker.flush())

        assertEquals("saved", flush.state.locator)
        assertEquals(5, flush.state.currentUnit)
        assertEquals(20_000L, flush.state.activeMillis)
        assertEquals(240, flush.state.estimatedWpm)
        assertNull(flush.session)
    }

    @Test
    fun shortNoisyMovementDoesNotCreateSessionOrPace() {
        val clock = MutableClock()
        val tracker = ReadingAnalyticsTracker(
            bookId = 7L,
            totalUnits = 10,
            wordsForUnit = { 100 },
            idleTimeoutMillis = 90_000L,
            clock = clock
        )

        tracker.record(0)
        clock.advance(5_000L)
        val state = tracker.record(1)
        val flush = requireNotNull(tracker.flush())

        assertEquals(0, state.estimatedWpm)
        assertNull(flush.session)
    }

    @Test
    fun shortValidSessionPersistsWithoutUsingItAsPaceSample() {
        val clock = MutableClock()
        val tracker = ReadingAnalyticsTracker(
            bookId = 7L,
            totalUnits = 10,
            wordsForUnit = { 100 },
            idleTimeoutMillis = 90_000L,
            clock = clock
        )

        tracker.record(0)
        clock.advance(20_000L)
        tracker.record(1)
        val session = requireNotNull(tracker.finish())

        assertEquals(100, session.wordsRead)
        assertEquals(0, session.wpm)
    }

    @Test
    fun idleTimeoutDoesNotCountAwayTime() {
        val clock = MutableClock()
        val tracker = ReadingAnalyticsTracker(
            bookId = 7L,
            totalUnits = 10,
            wordsForUnit = { 100 },
            idleTimeoutMillis = 90_000L,
            clock = clock
        )

        tracker.record(0)
        clock.advance(120_000L)
        val state = tracker.record(1)

        assertEquals(0L, state.activeMillis)
        assertEquals(0, state.estimatedWpm)
    }

    @Test
    fun largeNavigationJumpsDoNotInflateWordsRead() {
        val clock = MutableClock()
        val tracker = ReadingAnalyticsTracker(
            bookId = 7L,
            totalUnits = 20,
            wordsForUnit = { 100 },
            idleTimeoutMillis = 90_000L,
            clock = clock
        )

        tracker.record(0)
        clock.advance(10_000L)
        tracker.record(12)
        clock.advance(50_000L)
        val flush = requireNotNull(tracker.flush())

        assertNull(flush.session)
        assertEquals(0, flush.state.estimatedWpm)
        assertEquals(12, flush.state.currentUnit)
    }

    @Test
    fun smallBackwardMovementCountsAsRereadingWithoutCountingSkippedPages() {
        val clock = MutableClock()
        val tracker = ReadingAnalyticsTracker(
            bookId = 7L,
            totalUnits = 10,
            wordsForUnit = { 100 },
            idleTimeoutMillis = 90_000L,
            clock = clock
        )

        tracker.record(4)
        clock.advance(30_000L)
        tracker.record(3)
        clock.advance(30_000L)
        tracker.record(4)
        val flush = requireNotNull(tracker.flush())

        assertEquals(200, flush.session?.wordsRead)
        assertEquals(200, flush.state.estimatedWpm)
    }

    private class MutableClock : Clock() {
        private var now = Instant.parse("2026-05-27T12:00:00Z")

        fun advance(millis: Long) {
            now = now.plusMillis(millis)
        }

        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
    }
}
