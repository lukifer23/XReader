package com.xreader.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BookMetadataSuggestionsTest {
    @Test
    fun deduplicatesOptionsAndRanksPrefixMatchesFirst() {
        val suggestions = metadataSuggestions(
            query = "fiction",
            options = listOf(
                "Science Fiction",
                "Fiction Anthology",
                " science fiction ",
                "Historical Fiction"
            )
        )

        assertEquals(
            listOf("Fiction Anthology", "Historical Fiction", "Science Fiction"),
            suggestions
        )
    }

    @Test
    fun suggestsCanonicalCasingWithoutRepeatingExactValue() {
        assertEquals(
            listOf("Science Fiction"),
            metadataSuggestions("science fiction", listOf("Science Fiction"))
        )
        assertEquals(
            emptyList<String>(),
            metadataSuggestions("Science Fiction", listOf("Science Fiction"))
        )
    }

    @Test
    fun returnsLimitedCleanSuggestionsForBlankQuery() {
        val suggestions = metadataSuggestions(
            query = " ",
            options = listOf("  Beta  ", "Alpha", "Gamma", "Delta"),
            limit = 2
        )

        assertEquals(listOf("Alpha", "Beta"), suggestions)
    }
}
