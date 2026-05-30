package com.xreader.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPagingControllerTest {
    @Test
    fun startsFromPersistedVisibleUnit() {
        val controller = ReaderPagingController(
            initialUnit = 42,
            initialLocatorJson = """{"href":"chapter.xhtml"}"""
        )

        assertEquals(42, controller.currentPage)
        assertEquals(42, controller.currentUnit)
        assertEquals("""{"href":"chapter.xhtml"}""", controller.currentLocatorJson)
    }

    @Test
    fun clampsInvalidInitialUnitToStart() {
        val controller = ReaderPagingController(initialUnit = -4)

        assertEquals(0, controller.currentPage)
        assertEquals(0, controller.currentUnit)
    }

    @Test
    fun updateVisiblePositionClampsToAvailablePages() {
        val controller = ReaderPagingController(initialUnit = 2)

        controller.updateVisiblePosition(
            page = 12,
            locatorJson = """{"href":"end.xhtml"}""",
            pageCount = 5
        )

        assertEquals(4, controller.currentPage)
        assertEquals(4, controller.currentUnit)
        assertEquals("""{"href":"end.xhtml"}""", controller.currentLocatorJson)
        assertEquals(5, controller.pageCount)
    }
}
