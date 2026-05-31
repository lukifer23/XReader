package com.xreader.app.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchFtsQueryTest {
    @Test
    fun buildsPrefixTermsForSimpleQueries() {
        assertEquals(
            "normalizedBody:reader* normalizedBody:history*",
            SearchFtsQuery.build("reader history")
        )
    }

    @Test
    fun splitsHyphenatedAndPunctuatedQueriesIntoSearchableTerms() {
        assertEquals(
            "normalizedBody:sci* normalizedBody:fi*",
            SearchFtsQuery.build("sci-fi")
        )
        assertEquals(
            "normalizedBody:well* normalizedBody:being*",
            SearchFtsQuery.build("\"well-being\"")
        )
    }

    @Test
    fun ignoresPossessiveNoiseAndSingleLetterFragmentsWhenUsefulTermsExist() {
        assertEquals(
            "normalizedBody:darrow* normalizedBody:ship*",
            SearchFtsQuery.build("Darrow's ship")
        )
        assertEquals(
            "normalizedBody:don* normalizedBody:stop*",
            SearchFtsQuery.build("don't stop")
        )
    }

    @Test
    fun keepsSingleLetterQueriesWhenTheyAreAllTheUserEntered() {
        assertEquals("normalizedBody:x*", SearchFtsQuery.build("x"))
        assertEquals("normalizedBody:a* normalizedBody:b*", SearchFtsQuery.build("A B"))
    }

    @Test
    fun normalizesDiacriticsAndLimitsTermCount() {
        assertEquals(
            "normalizedBody:cafe* normalizedBody:deja* normalizedBody:vu*",
            SearchFtsQuery.build("Cafe\u0301 deja vu")
        )
        assertEquals(
            "normalizedBody:one* normalizedBody:two* normalizedBody:three* normalizedBody:four* normalizedBody:five* normalizedBody:six* normalizedBody:seven* normalizedBody:eight*",
            SearchFtsQuery.build("one two three four five six seven eight nine ten")
        )
    }

    @Test
    fun returnsNullForBlankOrSymbolOnlyQueries() {
        assertNull(SearchFtsQuery.build(""))
        assertNull(SearchFtsQuery.build("   "))
        assertNull(SearchFtsQuery.build("?! --"))
    }
}
