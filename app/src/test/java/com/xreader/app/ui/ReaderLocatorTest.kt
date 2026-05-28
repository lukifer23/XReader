package com.xreader.app.ui

import com.xreader.app.reader.ReadingUnit
import org.junit.Assert.assertEquals
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
}
