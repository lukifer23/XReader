package com.xreader.app.analytics

import org.junit.Assert.assertEquals
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
        assertEquals(300, state.estimatedWpm)
        assertEquals(300, session.wordsRead)
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
        assertEquals(300, flush.session?.wordsRead)
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
