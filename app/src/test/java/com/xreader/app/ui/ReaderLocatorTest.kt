package com.xreader.app.ui

import com.xreader.app.data.ReadingStateEntity
import com.xreader.app.reader.ReadingUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderLocatorTest {
    @Test
    fun locatorToUnitResolvesExactStoredLocator() {
        val units = listOf(
            unit(0, """{"href":"chapter-1.xhtml"}"""),
            unit(1, """{"href":"chapter-2.xhtml"}""")
        )

        assertEquals(1, locatorToUnit("""{"href":"chapter-2.xhtml"}""", units))
    }

    @Test
    fun locatorToUnitResolvesLegacyIndexedLocator() {
        val units = listOf(unit(0), unit(1), unit(2))

        assertEquals(2, locatorToUnit("epub:2:14", units))
    }

    @Test
    fun locatorToUnitFallsBackToStartForUnknownLocator() {
        val units = listOf(unit(0), unit(1), unit(2))

        assertEquals(0, locatorToUnit("not-a-known-locator", units))
    }

    @Test
    fun searchUnitIndexParsesLibrarySearchAnchor() {
        assertEquals(30512, "${SEARCH_UNIT_LOCATOR_PREFIX}30512".searchUnitIndexOrNull())
    }

    @Test
    fun resolveInitialReaderPositionMapsLibrarySearchAnchorOntoAvailableUnits() {
        val units = (0..4).map { unit(it, "locator-$it") }

        val resolved = resolveInitialReaderPosition(
            initialLocatorOverride = "${SEARCH_UNIT_LOCATOR_PREFIX}75",
            saved = null,
            positions = emptyList(),
            units = units,
            maxIndexedUnit = 100
        )

        assertEquals(3, resolved.unitIndex)
        assertEquals("locator-3", resolved.locatorJson)
        assertTrue(resolved.fromInitialOverride)
    }

    @Test
    fun resolveInitialReaderPositionUsesSavedCurrentUnitWhenSavedLocatorIsUnknown() {
        val units = (0..4).map { unit(it, "locator-$it") }
        val saved = readingState(locator = "unknown-future-locator-format", currentUnit = 2)

        val resolved = resolveInitialReaderPosition(
            initialLocatorOverride = null,
            saved = saved,
            positions = emptyList(),
            units = units,
            maxIndexedUnit = 1
        )

        assertEquals(2, resolved.unitIndex)
        assertFalse(resolved.fromInitialOverride)
    }

    private fun unit(
        index: Int,
        locator: String = "locator-$index",
    ): ReadingUnit =
        ReadingUnit(
            index = index,
            locator = locator,
            heading = "Position ${index + 1}",
            body = "",
            wordCount = 100
        )

    private fun readingState(
        locator: String,
        currentUnit: Int,
    ): ReadingStateEntity =
        ReadingStateEntity(
            bookId = 42,
            locator = locator,
            progress = currentUnit / 10.0,
            currentUnit = currentUnit,
            totalUnits = 10,
            activeMillis = 0,
            estimatedWpm = 0,
            lastReadAt = 1_700_000_000_000
        )
}
