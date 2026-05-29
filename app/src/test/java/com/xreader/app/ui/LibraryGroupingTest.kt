package com.xreader.app.ui

import com.xreader.app.data.BookEntity
import com.xreader.app.data.BookFormat
import com.xreader.app.data.ReadingStateEntity
import com.xreader.app.settings.LibrarySort
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
            ),
            LibrarySort.SERIES
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
            ),
            LibrarySort.SERIES
        )
        val genreGroups = groupBooks(
            LibraryGroup.GENRES,
            listOf(
                item(title = "No Genre", genre = null),
                item(title = "Science", genre = "Science Fiction"),
                item(title = "Fantasy", genre = "Fantasy")
            ),
            LibrarySort.TITLE
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
            ),
            LibrarySort.SERIES
        )

        assertEquals(listOf("First", "Second", "Third"), grouped.getValue("Saga").map { it.book.title })
    }

    @Test
    fun authorGroupsCanSortByRecentActivity() {
        val grouped = groupBooks(
            LibraryGroup.AUTHORS,
            listOf(
                item(title = "Older", author = "Alpha", lastReadAt = 10L),
                item(title = "Newest", author = "Beta", lastReadAt = 30L),
                item(title = "Newer", author = "Alpha", lastReadAt = 20L)
            ),
            LibrarySort.RECENT
        )

        assertEquals(listOf("Beta", "Alpha"), grouped.keys.toList())
        assertEquals(listOf("Newer", "Older"), grouped.getValue("Alpha").map { it.book.title })
    }

    @Test
    fun genreGroupsCanSortByAverageProgress() {
        val grouped = groupBooks(
            LibraryGroup.GENRES,
            listOf(
                item(title = "Early", genre = "Science Fiction", progress = 0.25),
                item(title = "Done", genre = "Fantasy", progress = 0.9),
                item(title = "Later", genre = "Science Fiction", progress = 0.75)
            ),
            LibrarySort.PROGRESS
        )

        assertEquals(listOf("Fantasy", "Science Fiction"), grouped.keys.toList())
        assertEquals(listOf("Later", "Early"), grouped.getValue("Science Fiction").map { it.book.title })
    }

    @Test
    fun seriesGroupItemsHonorSelectedSort() {
        val items = listOf(
            item(title = "Third", series = "Saga", seriesIndex = 3.0, lastReadAt = 30L),
            item(title = "First", series = "Saga", seriesIndex = 1.0, lastReadAt = 10L),
            item(title = "Second", series = "Saga", seriesIndex = 2.0, lastReadAt = 20L)
        )

        assertEquals(
            listOf("Third", "Second", "First"),
            groupBooks(LibraryGroup.SERIES, items, LibrarySort.RECENT).getValue("Saga").map { it.book.title }
        )
        assertEquals(
            listOf("First", "Second", "Third"),
            groupBooks(LibraryGroup.SERIES, items, LibrarySort.SERIES).getValue("Saga").map { it.book.title }
        )
    }

    @Test
    fun collectionGroupsCanPlaceOneBookInMultipleCollections() {
        val grouped = groupBooks(
            LibraryGroup.COLLECTIONS,
            listOf(
                item(
                    title = "Red Rising",
                    collections = listOf(CollectionUiItem(1L, "Favorites"), CollectionUiItem(2L, "Sci-Fi"))
                ),
                item(
                    title = "Dune",
                    collections = listOf(CollectionUiItem(2L, "Sci-Fi"))
                )
            ),
            LibrarySort.TITLE
        )

        assertEquals(listOf("Favorites", "Sci-Fi"), grouped.keys.toList())
        assertEquals(listOf("Red Rising"), grouped.getValue("Favorites").map { it.book.title })
        assertEquals(listOf("Dune", "Red Rising"), grouped.getValue("Sci-Fi").map { it.book.title })
    }

    private fun item(
        title: String,
        author: String = "Author",
        series: String? = null,
        seriesIndex: Double? = null,
        genre: String? = null,
        year: Int? = null,
        progress: Double? = null,
        lastReadAt: Long? = null,
        collections: List<CollectionUiItem> = emptyList(),
    ): BookListItem =
        BookListItem(
            book = BookEntity(
                id = title.hashCode().toLong(),
                title = title,
                author = author,
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
            state = if (progress == null && lastReadAt == null) {
                null
            } else {
                ReadingStateEntity(
                    bookId = title.hashCode().toLong(),
                    locator = "locator-$title",
                    progress = progress ?: 0.0,
                    currentUnit = 0,
                    totalUnits = 100,
                    activeMillis = 0L,
                    estimatedWpm = 0,
                    lastReadAt = lastReadAt ?: 1_700_000_000_000L
                )
            },
            collections = collections
        )
}
