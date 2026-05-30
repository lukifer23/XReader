package com.xreader.app.ui

import com.xreader.app.data.AnnotationEntity
import com.xreader.app.data.AnnotationKind
import com.xreader.app.data.BookmarkEntity
import com.xreader.app.reader.ReaderNavigationItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderNavigationDialogTest {
    @Test
    fun filtersContentsByMultiWordTitle() {
        val items = listOf(
            navItem("Chapter 1: Mars"),
            navItem("Chapter 2: Lunar Transfer"),
            navItem("Appendix")
        )

        val filtered = filterReaderNavigationItems(items, "chapter mars")

        assertEquals(listOf("Chapter 1: Mars"), filtered.map { it.title })
    }

    @Test
    fun blankNavigationQueryKeepsOriginalOrder() {
        val items = listOf(navItem("One"), navItem("Two"))

        assertEquals(items, filterReaderNavigationItems(items, "  "))
    }

    @Test
    fun filtersBookmarksByLabelAndProgress() {
        val bookmarks = listOf(
            bookmark(id = 1, label = "Before the storm", progress = 0.12),
            bookmark(id = 2, label = "Landing sequence", progress = 0.44)
        )

        assertEquals(
            listOf(2L),
            filterReaderBookmarks(bookmarks, "landing 44").map { it.id }
        )
    }

    @Test
    fun filtersAnnotationsByQuoteNoteKindAndTags() {
        val annotations = listOf(
            annotation(id = 1, kind = AnnotationKind.HIGHLIGHT, quote = "Terraforming council", tags = "science, politics"),
            annotation(id = 2, kind = AnnotationKind.NOTE, note = "Check this character later", tags = "character"),
            annotation(id = 3, kind = AnnotationKind.HIGHLIGHT, quote = "Unrelated", tags = "misc")
        )

        assertEquals(
            listOf(1L),
            filterReaderAnnotations(annotations, "highlight science").map { it.id }
        )
        assertEquals(
            listOf(2L),
            filterReaderAnnotations(annotations, "#character later").map { it.id }
        )
    }

    private fun navItem(title: String): ReaderNavigationItem =
        ReaderNavigationItem(
            title = title,
            locatorJson = "loc-$title",
            level = 0
        )

    private fun bookmark(
        id: Long,
        label: String,
        progress: Double,
    ): BookmarkEntity =
        BookmarkEntity(
            id = id,
            bookId = 1L,
            locator = "bookmark-$id",
            label = label,
            progress = progress,
            createdAt = 1L
        )

    private fun annotation(
        id: Long,
        kind: AnnotationKind,
        quote: String = "",
        note: String = "",
        tags: String = "",
    ): AnnotationEntity =
        AnnotationEntity(
            id = id,
            bookId = 1L,
            kind = kind,
            locator = "annotation-$id",
            quote = quote,
            note = note,
            color = "#F2C94C",
            tags = tags,
            createdAt = 1L,
            updatedAt = 2L
        )
}
