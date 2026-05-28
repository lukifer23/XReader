package com.xreader.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextToolsTest {
    @Test
    fun normalizesSearchText() {
        assertEquals("cafe deja vu", TextTools.normalizeForSearch("Cafe\u0301   Deja\u0300 Vu"))
    }

    @Test
    fun sortTitleDropsLeadingArticles() {
        assertEquals("martian", TextTools.sortTitle("The Martian"))
        assertEquals("reader", TextTools.sortTitle("A Reader"))
    }

    @Test
    fun countsWordsWithApostrophesAndHyphens() {
        assertEquals(4, TextTools.wordCount("Reader's fast, clean-built app."))
        assertTrue(TextTools.words("one two").contains("one"))
    }
}
