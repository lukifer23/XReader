package com.xreader.app.ui

import com.xreader.app.reader.ReaderSearchResult
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSearchNavigationTest {
    @Test
    fun navigationStartsAroundCurrentVisibleUnit() {
        val results = listOf(
            result(unit = 2),
            result(unit = 8),
            result(unit = 14)
        )

        val navigation = requireNotNull(
            readerSearchNavigationState(
                currentUnit = 7,
                results = results,
                activeIndex = null
            )
        )

        assertEquals("3 matches", navigation.label)
        assertEquals(0, navigation.previousIndex)
        assertEquals(1, navigation.nextIndex)
    }

    @Test
    fun navigationWrapsWhenCurrentUnitIsPastLastMatch() {
        val results = listOf(
            result(unit = 2),
            result(unit = 8),
            result(unit = 14)
        )

        val navigation = requireNotNull(
            readerSearchNavigationState(
                currentUnit = 30,
                results = results,
                activeIndex = null
            )
        )

        assertEquals(2, navigation.previousIndex)
        assertEquals(0, navigation.nextIndex)
    }

    @Test
    fun activeResultDrivesPreviousAndNext() {
        val results = listOf(
            result(unit = 2),
            result(unit = 8),
            result(unit = 14)
        )

        val navigation = requireNotNull(
            readerSearchNavigationState(
                currentUnit = 7,
                results = results,
                activeIndex = 1
            )
        )

        assertEquals("2 of 3", navigation.label)
        assertEquals(0, navigation.previousIndex)
        assertEquals(2, navigation.nextIndex)
    }

    @Test
    fun singleResultCanStillBeOpenedFromTheFindBar() {
        val navigation = requireNotNull(
            readerSearchNavigationState(
                currentUnit = 7,
                results = listOf(result(unit = 2)),
                activeIndex = null
            )
        )

        assertEquals("1 match", navigation.label)
        assertEquals(0, navigation.previousIndex)
        assertEquals(0, navigation.nextIndex)
    }

    private fun result(unit: Int): ReaderSearchResult =
        ReaderSearchResult(
            title = "Chapter",
            snippet = "Snippet",
            locatorJson = "loc-$unit",
            unitIndex = unit
        )
}
