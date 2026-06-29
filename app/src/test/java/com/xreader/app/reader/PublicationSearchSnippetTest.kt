package com.xreader.app.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class PublicationSearchSnippetTest {
    @Test
    fun searchSnippetCombinesVisibleTextPartsAndCollapsesWhitespace() {
        assertEquals(
            "Before text highlighted term after text",
            readerSearchSnippet(
                before = " Before\ntext ",
                highlight = " highlighted\tterm ",
                after = " after   text ",
                title = "Chapter 1",
                query = "term"
            )
        )
    }

    @Test
    fun searchSnippetSkipsBlankParts() {
        assertEquals(
            "highlighted term",
            readerSearchSnippet(
                before = " ",
                highlight = "highlighted term",
                after = "",
                title = "Chapter 1",
                query = "term"
            )
        )
    }

    @Test
    fun searchSnippetFallsBackToNonblankTitleThenQuery() {
        assertEquals(
            "Chapter 1",
            readerSearchSnippet(
                before = null,
                highlight = " ",
                after = null,
                title = "Chapter 1",
                query = "term"
            )
        )
        assertEquals(
            "term",
            readerSearchSnippet(
                before = null,
                highlight = "",
                after = null,
                title = " ",
                query = "term"
            )
        )
    }
}
