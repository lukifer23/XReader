package com.xreader.app.analytics

import com.xreader.app.data.ReadingSessionEntity
import com.xreader.app.data.ReadingStateEntity
import java.time.Clock
import kotlin.math.abs
import kotlin.math.roundToInt

data class ReadingTrackerFlush(
    val state: ReadingStateEntity,
    val session: ReadingSessionEntity?,
)

class ReadingAnalyticsTracker(
    private val bookId: Long,
    private val totalUnits: Int,
    private val wordsForUnit: (Int) -> Int,
    private val idleTimeoutMillis: Long,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val startedAt = clock.millis()
    private var lastInteractionAt = startedAt
    private var activeMillis = 0L
    private var startUnit = 0
    private var currentUnit = 0
    private var currentLocator = "0"
    private var currentProgressOverride: Double? = null
    private var countedWords = 0
    private var lastCountedUnit: Int? = null
    private var initialized = false

    fun record(
        unit: Int,
        locator: String = unit.toString(),
        progressOverride: Double? = null,
    ): ReadingStateEntity {
        val now = clock.millis()
        applyElapsed(now)
        val boundedUnit = unit.coerceIn(0, (totalUnits - 1).coerceAtLeast(0))
        if (!initialized) {
            startUnit = boundedUnit
            countedWords = wordsForUnit(boundedUnit).coerceAtLeast(0)
            lastCountedUnit = boundedUnit
            initialized = true
        } else {
            countTraversalTo(boundedUnit)
        }
        currentUnit = boundedUnit
        currentLocator = locator
        currentProgressOverride = progressOverride
        lastInteractionAt = now
        return stateAt(now)
    }

    fun snapshot(): ReadingStateEntity? {
        if (!initialized) return null
        val now = clock.millis()
        applyElapsed(now)
        lastInteractionAt = now
        return stateAt(now)
    }

    fun flush(): ReadingTrackerFlush? {
        val state = snapshot() ?: return null
        val wordsRead = countedWords
        val session = if (activeMillis >= 5_000L && wordsRead > 0) {
            ReadingSessionEntity(
                bookId = bookId,
                startedAt = startedAt,
                endedAt = state.lastReadAt,
                activeMillis = activeMillis,
                startUnit = startUnit,
                endUnit = currentUnit,
                wordsRead = wordsRead,
                wpm = estimateWpm(wordsRead, activeMillis)
            )
        } else {
            null
        }
        return ReadingTrackerFlush(state = state, session = session)
    }

    fun finish(): ReadingSessionEntity? = flush()?.session

    private fun applyElapsed(now: Long) {
        if (!initialized) return
        val delta = now - lastInteractionAt
        if (delta in 1..idleTimeoutMillis) activeMillis += delta
    }

    private fun stateAt(now: Long): ReadingStateEntity {
        val progress = currentProgressOverride
            ?: if (totalUnits <= 0) 0.0 else (currentUnit + 1).toDouble() / totalUnits.toDouble()
        val wordsRead = countedWords
        val wpm = estimateWpm(wordsRead, activeMillis)
        return ReadingStateEntity(
            bookId = bookId,
            locator = currentLocator,
            progress = progress.coerceIn(0.0, 1.0),
            currentUnit = currentUnit,
            totalUnits = totalUnits,
            activeMillis = activeMillis,
            estimatedWpm = wpm,
            lastReadAt = now,
            finishedAt = if (progress >= 0.995) now else null
        )
    }

    private fun countTraversalTo(unit: Int) {
        val previous = lastCountedUnit ?: run {
            countedWords += wordsForUnit(unit).coerceAtLeast(0)
            lastCountedUnit = unit
            return
        }
        val delta = unit - previous
        if (delta == 0) return
        if (abs(delta) > MAX_CONTIGUOUS_READING_STEP) {
            lastCountedUnit = unit
            return
        }
        val range = if (delta > 0) (previous + 1)..unit else unit until previous
        countedWords += range.sumOf { wordsForUnit(it).coerceAtLeast(0) }
        lastCountedUnit = unit
    }

    private fun estimateWpm(wordsRead: Int, activeMillis: Long): Int {
        if (activeMillis <= 0L) return 0
        val minutes = activeMillis / 60_000.0
        return abs(wordsRead / minutes).roundToInt().coerceIn(0, 1200)
    }

    private companion object {
        const val MAX_CONTIGUOUS_READING_STEP = 3
    }
}
