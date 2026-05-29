package com.xreader.app.ui

import com.xreader.app.data.BookEntity
import com.xreader.app.data.BookFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryGroupingTest {
    @Test
    fun yearGroupsSortNewestFirstWithMissingYearLast() {
        val grouped = groupBooks(
            LibraryGroup.YEARS,
            listOf(
                item(title = "Old", year = 1999),
                item(title = "New", year = 2026),
                item(title = "No Year", year = null),
                item(title = "Middle", year = 2012)
            )
        )

        assertEquals(listOf("2026", "2012", "1999", "No year"), grouped.keys.toList())
    }

    @Test
    fun missingSeriesAndGenreGroupsSortLast() {
        val seriesGroups = groupBooks(
            LibraryGroup.SERIES,
            listOf(
                item(title = "No Series", series = null),
                item(title = "Alpha", series = "Alpha"),
                item(title = "Zeta", series = "Zeta")
            )
        )
        val genreGroups = groupBooks(
            LibraryGroup.GENRES,
            listOf(
                item(title = "No Genre", genre = null),
                item(title = "Science", genre = "Science Fiction"),
                item(title = "Fantasy", genre = "Fantasy")
            )
        )

        assertEquals(listOf("Alpha", "Zeta", "No series"), seriesGroups.keys.toList())
        assertEquals(listOf("Fantasy", "Science Fiction", "No genre"), genreGroups.keys.toList())
    }

    @Test
    fun seriesItemsUseSeriesIndexBeforeTitle() {
        val grouped = groupBooks(
            LibraryGroup.SERIES,
            listOf(
                item(title = "Third", series = "Saga", seriesIndex = 3.0),
                item(title = "First", series = "Saga", seriesIndex = 1.0),
                item(title = "Second", series = "Saga", seriesIndex = 2.0)
            )
        )

        assertEquals(listOf("First", "Second", "Third"), grouped.getValue("Saga").map { it.book.title })
    }

    private fun item(
        title: String,
        series: String? = null,
        seriesIndex: Double? = null,
        genre: String? = null,
        year: Int? = null,
    ): BookListItem =
        BookListItem(
            book = BookEntity(
                id = title.hashCode().toLong(),
                title = title,
                author = "Author",
                sortTitle = title,
                series = series,
                seriesIndex = seriesIndex,
                genre = genre,
                year = year,
                format = BookFormat.EPUB,
                sourceExtension = "epub",
                fileName = "$title.epub",
                filePath = "books/$title.epub",
                checksum = "checksum-$title",
                fileSizeBytes = 1024L,
                wordCount = 10_000,
                importedAt = 1_700_000_000_000L,
                updatedAt = 1_700_000_000_000L
            ),
            state = null
        )
}
