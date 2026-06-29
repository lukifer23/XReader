package com.xreader.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsUiFormattersTest {
    @Test
    fun countLabelsUseReadableGrouping() {
        assertEquals("0", analyticsCountLabel(-1))
        assertEquals("12", analyticsCountLabel(12))
        assertEquals("12,345", analyticsCountLabel(12_345))
    }

    @Test
    fun paceLabelsAvoidMisleadingZeroWpm() {
        assertEquals("--", analyticsPaceValue(0))
        assertEquals("245", analyticsPaceValue(245))
        assertEquals("Reliable pace appears after enough active reading.", analyticsPaceDetail(0))
        assertEquals("Based on 1 reliable session.", analyticsPaceDetail(1))
        assertEquals("Based on 2 reliable sessions.", analyticsPaceDetail(2))
    }

    @Test
    fun rowDetailKeepsStatsCompact() {
        assertEquals(
            "1 session • 1h 0m • 2,400 words • 240 WPM",
            analyticsRowDetail(sessions = 1, activeMillis = 3_600_000L, wordsRead = 2_400, averageWpm = 240)
        )
        assertEquals(
            "2 sessions • 5m • 800 words • pace pending",
            analyticsRowDetail(sessions = 2, activeMillis = 300_000L, wordsRead = 800, averageWpm = 0)
        )
        assertEquals(
            "0 sessions • 0m • 0 words • pace pending",
            analyticsRowDetail(sessions = -2, activeMillis = -1L, wordsRead = -800, averageWpm = -10)
        )
    }
}
