package com.xreader.app.ui

import com.xreader.app.data.BookEntity
import com.xreader.app.data.BookFormat
import com.xreader.app.data.ReadingStateEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchSnippetTest {
    @Test
    fun libraryQueryMatchesAcrossMetadataCollectionsAndStatus() {
        val books = listOf(
            item(title = "Red Rising", author = "Pierce Brown", genre = "Science Fiction", progress = 0.45),
            item(title = "Dune", author = "Frank Herbert", collections = listOf(CollectionUiItem(1L, "Favorites"))),
            item(title = "Station Manual", sourceExtension = "pdf", format = BookFormat.PDF, finished = true)
        )

        assertEquals(
            listOf("Red Rising"),
            books.filteredForLibraryQuery("pierce progress").map { it.book.title }
        )
        assertEquals(
            listOf("Dune"),
            books.filteredForLibraryQuery("favorites unread").map { it.book.title }
        )
        assertEquals(
            listOf("Station Manual"),
            books.filteredForLibraryQuery("pdf finished").map { it.book.title }
        )
    }

    @Test
    fun libraryQueryMatchesTermsAcrossDifferentFields() {
        val books = listOf(
            item(title = "Golden Son", author = "Pierce Brown", series = "Red Rising", year = 2015),
            item(title = "Leviathan Wakes", author = "James Corey", series = "The Expanse", year = 2011)
        )

        assertEquals(
            listOf("Golden Son"),
            books.filteredForLibraryQuery("pierce 2015 rising").map { it.book.title }
        )
    }

    @Test
    fun libraryQueryMatchesAcrossPunctuationAndAccents() {
        val books = listOf(
            item(title = "Red Rising", author = "Pierce Brown", series = "Sci Fi Saga"),
            item(title = "Leviathan Wakes", author = "James S. A. Corey", series = "The Expanse"),
            item(title = "Cafe Society", author = "Ada Lovelace")
        )

        assertEquals(
            listOf("Red Rising"),
            books.filteredForLibraryQuery("red-rising sci_fi").map { it.book.title }
        )
        assertEquals(
            listOf("Leviathan Wakes"),
            books.filteredForLibraryQuery("james.s.a. expanse").map { it.book.title }
        )
        assertEquals(
            listOf("Cafe Society"),
            books.filteredForLibraryQuery("café").map { it.book.title }
        )
    }

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

    @Test
    fun snippetHandlesMultiTermReaderQueries() {
        val body = "Opening status text that should not dominate the snippet. ".repeat(4) +
            "The terraforming council gathered under the dome after the alarms. " +
            "Closing material that should remain outside the short result. ".repeat(4)

        val snippet = searchResultSnippet(body, "council terraforming", maxLength = 80)

        assertTrue(snippet.contains("terraforming council"))
        assertFalse(snippet.startsWith("Opening status"))
    }

    private fun item(
        title: String,
        author: String = "Author",
        series: String? = null,
        genre: String? = null,
        year: Int? = null,
        sourceExtension: String = "epub",
        format: BookFormat = BookFormat.EPUB,
        collections: List<CollectionUiItem> = emptyList(),
        progress: Double? = null,
        finished: Boolean = false,
    ): BookListItem =
        BookListItem(
            book = BookEntity(
                id = title.hashCode().toLong(),
                title = title,
                author = author,
                sortTitle = title,
                series = series,
                genre = genre,
                year = year,
                format = format,
                sourceExtension = sourceExtension,
                fileName = "$title.$sourceExtension",
                filePath = "books/$title.$sourceExtension",
                checksum = "checksum-$title",
                fileSizeBytes = 1024L,
                wordCount = 10_000,
                finished = finished,
                favorite = collections.any { it.name.equals("Favorites", ignoreCase = true) },
                importedAt = 1_700_000_000_000L,
                updatedAt = 1_700_000_000_000L
            ),
            state = progress?.let {
                ReadingStateEntity(
                    bookId = title.hashCode().toLong(),
                    locator = "locator-$title",
                    progress = progress,
                    currentUnit = 0,
                    totalUnits = 100,
                    activeMillis = 0L,
                    estimatedWpm = 0,
                    lastReadAt = 1_700_000_000_000L
                )
            },
            collections = collections
        )
}
