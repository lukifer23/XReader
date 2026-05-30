package com.xreader.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchSnippetTest {
    @Test
    fun visibleResultsUseCompactPreviewByDefault() {
        val results = (1..8).toList()

        assertEquals(listOf(1, 2, 3, 4, 5), visibleLibrarySearchResults(results, expanded = false))
    }

    @Test
    fun visibleResultsReturnAllWhenExpanded() {
        val results = (1..8).toList()

        assertEquals(results, visibleLibrarySearchResults(results, expanded = true))
    }

    @Test
    fun searchResultsHeaderReportsVisibleAndTotalCounts() {
        assertEquals("Text matches 5 of 8", librarySearchResultsHeader(visibleCount = 5, totalCount = 8))
        assertEquals("Text matches 4", librarySearchResultsHeader(visibleCount = 4, totalCount = 4))
        assertEquals("Text matches", librarySearchResultsHeader(visibleCount = 0, totalCount = 0))
    }

    @Test
    fun snippetCentersOnMatchedTerm() {
        val body = "Opening material that is not relevant. ".repeat(8) +
            "The courier crossed the landing field with a sealed dispatch. " +
            "Trailing material that should stay mostly out of the preview. ".repeat(8)

        val snippet = searchResultSnippet(body, "courier", maxLength = 90)

        assertTrue(snippet.contains("courier crossed"))
        assertTrue(snippet.startsWith("..."))
        assertTrue(snippet.endsWith("..."))
        assertFalse(snippet.contains("Opening material that is not relevant. Opening material"))
    }

    @Test
    fun snippetCollapsesWhitespace() {
        val snippet = searchResultSnippet(
            body = "First line\n\nSecond\tline    with  space",
            query = "second",
            maxLength = 80
        )

        assertEquals("First line Second line with space", snippet)
    }

    @Test
    fun snippetFallsBackToBeginningWhenQueryTermIsNotVisible() {
        val body = "Alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu"

        val snippet = searchResultSnippet(body, "missing", maxLength = 24)

        assertEquals("Alpha beta gamma delta...", snippet)
    }
}
