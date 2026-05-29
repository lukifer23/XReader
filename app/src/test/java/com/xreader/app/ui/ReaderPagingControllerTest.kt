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
}
