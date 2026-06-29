package com.xreader.app.repository

import com.xreader.app.data.AnnotationEntity
import com.xreader.app.data.AnnotationKind
import com.xreader.app.data.BookEntity
import com.xreader.app.data.BookFormat
import com.xreader.app.data.BookmarkEntity
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationMarkdownExportTest {
    @Test
    fun exportIncludesReadableProgressForAnnotationsAndBookmarks() {
        val markdown = AnnotationMarkdownExport.build(
            exportedAt = 1_700_000_000_000L,
            booksById = mapOf(7L to book(title = "Mars Notes")),
            annotations = listOf(
                annotation(
                    kind = AnnotationKind.HIGHLIGHT,
                    locator = """{"href":"chapter.xhtml","locations":{"totalProgression":0.426}}""",
                    quote = "The quote remains."
                )
            ),
            bookmarks = listOf(
                BookmarkEntity(
                    id = 2L,
                    bookId = 7L,
                    locator = "bookmark",
                    label = "Landing",
                    progress = 0.12,
                    createdAt = 1L
                )
            )
        )

        assertTrue(markdown.contains("#### Highlight - 43% - 2023-11-14T22:13:20Z"))
        assertTrue(markdown.contains("- 12% - Landing"))
    }

    @Test
    fun exportEscapesMarkdownInlineBookAndTagText() {
        val markdown = AnnotationMarkdownExport.build(
            exportedAt = 1_700_000_000_000L,
            booksById = mapOf(7L to book(title = "The [Book] *Name*", author = "A_B `Writer`")),
            annotations = listOf(
                annotation(
                    tags = "hard_sci-fi, `quote`",
                    note = "Keep this note readable."
                )
            ),
            bookmarks = listOf(
                BookmarkEntity(
                    id = 2L,
                    bookId = 7L,
                    locator = "bookmark",
                    label = "Part [One]",
                    progress = 0.5,
                    createdAt = 1L
                )
            )
        )

        assertTrue(markdown.contains("## The \\[Book\\] \\*Name\\*"))
        assertTrue(markdown.contains("A\\_B \\`Writer\\`"))
        assertTrue(markdown.contains("Tags: hard\\_sci-fi, \\`quote\\`"))
        assertTrue(markdown.contains("- 50% - Part \\[One\\]"))
    }

    @Test
    fun exportAnnotationHeadingSkipsMissingProgressWithoutDanglingSeparator() {
        val markdown = AnnotationMarkdownExport.build(
            exportedAt = 1_700_000_000_000L,
            booksById = mapOf(7L to book(title = "Mars Notes")),
            annotations = listOf(
                annotation(
                    kind = AnnotationKind.NOTE,
                    locator = """{"href":"chapter.xhtml","locations":{}}""",
                    note = "Progress is unavailable."
                )
            ),
            bookmarks = emptyList()
        )

        assertTrue(markdown.contains("#### Note - 2023-11-14T22:13:20Z"))
        assertTrue(!markdown.contains("#### Note - - 2023-11-14T22:13:20Z"))
    }

    private fun book(
        title: String,
        author: String = "Author",
    ): BookEntity =
        BookEntity(
            id = 7L,
            title = title,
            author = author,
            sortTitle = title.lowercase(),
            format = BookFormat.EPUB,
            sourceExtension = "epub",
            fileName = "book.epub",
            filePath = "books/book.epub",
            checksum = "checksum",
            fileSizeBytes = 1_024L,
            wordCount = 12_000,
            importedAt = 1L,
            updatedAt = 1L
        )

    private fun annotation(
        kind: AnnotationKind = AnnotationKind.NOTE,
        locator: String = """{"href":"chapter.xhtml","locations":{"progression":0.25}}""",
        quote: String = "A quoted sentence.",
        note: String = "",
        tags: String = "",
    ): AnnotationEntity =
        AnnotationEntity(
            id = 1L,
            bookId = 7L,
            kind = kind,
            locator = locator,
            quote = quote,
            note = note,
            color = "#F7D154",
            tags = tags,
            createdAt = 1L,
            updatedAt = 1_700_000_000_000L
        )
}
