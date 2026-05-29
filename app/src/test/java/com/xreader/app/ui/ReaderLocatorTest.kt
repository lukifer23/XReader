package com.xreader.app.ui

import com.xreader.app.data.BookmarkEntity
import com.xreader.app.data.ReadingStateEntity
import com.xreader.app.reader.ReadingUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun resolveVisibleReaderPositionKeepsVisibleLocator() {
        val units = (0..4).map { unit(it, "locator-$it") }

        val resolved = resolveVisibleReaderPosition(
            visibleUnit = 3,
            visibleLocatorJson = "visible-locator",
            fallbackUnit = 0,
            positions = emptyList(),
            units = units
        )

        assertEquals(3, resolved?.unitIndex)
        assertEquals("visible-locator", resolved?.locatorJson)
    }

    @Test
    fun resolveVisibleReaderPositionFallsBackToCurrentUnit() {
        val units = (0..4).map { unit(it, "locator-$it") }

        val resolved = resolveVisibleReaderPosition(
            visibleUnit = null,
            visibleLocatorJson = null,
            fallbackUnit = 2,
            positions = emptyList(),
            units = units
        )

        assertEquals(2, resolved?.unitIndex)
        assertEquals("locator-2", resolved?.locatorJson)
    }

    @Test
    fun bookmarkAtReaderLocationMatchesExactVisibleLocator() {
        val locator = locator(position = 7, progression = 0.42)
        val bookmarks = listOf(bookmark(id = 10, locator = locator))

        assertEquals(10L, bookmarks.bookmarkAtReaderLocation(locator, fallbackUnitLocator = null)?.id)
    }

    @Test
    fun bookmarkAtReaderLocationMatchesEquivalentReadiumPosition() {
        val bookmarks = listOf(
            bookmark(id = 11, locator = """{"href":"chapter.xhtml","type":"application/xhtml+xml","locations":{"position":4,"totalProgression":0.24}}""")
        )
        val visible = """{"href":"other.xhtml","type":"application/xhtml+xml","locations":{"position":4,"totalProgression":0.24001}}"""

        assertEquals(11L, bookmarks.bookmarkAtReaderLocation(visible, fallbackUnitLocator = null)?.id)
    }

    @Test
    fun bookmarkAtReaderLocationKeepsSeparateVisibleProgressionsDistinct() {
        val bookmarks = listOf(
            bookmark(id = 12, locator = """{"href":"chapter.xhtml","type":"application/xhtml+xml","locations":{"totalProgression":0.20}}""")
        )
        val visible = """{"href":"chapter.xhtml","type":"application/xhtml+xml","locations":{"totalProgression":0.45}}"""

        assertNull(bookmarks.bookmarkAtReaderLocation(visible, fallbackUnitLocator = null))
    }

    @Test
    fun bookmarkAtReaderLocationFallsBackToLegacyUnitLocator() {
        val legacyUnitLocator = "epub:2:14"
        val bookmarks = listOf(bookmark(id = 13, locator = legacyUnitLocator))

        assertEquals(
            13L,
            bookmarks.bookmarkAtReaderLocation(
                visibleLocatorJson = """{"href":"chapter.xhtml","type":"application/xhtml+xml","locations":{"position":2}}""",
                fallbackUnitLocator = legacyUnitLocator
            )?.id
        )
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

    private fun bookmark(
        id: Long,
        locator: String,
    ): BookmarkEntity =
        BookmarkEntity(
            id = id,
            bookId = 42,
            locator = locator,
            label = "Bookmark $id",
            progress = 0.5,
            createdAt = 1_700_000_000_000
        )

    private fun locator(
        position: Int,
        progression: Double,
    ): String =
        """{"href":"chapter.xhtml","type":"application/xhtml+xml","locations":{"position":$position,"totalProgression":$progression}}"""
}
