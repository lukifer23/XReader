package com.xreader.app.repository

import com.xreader.app.data.AnnotationEntity
import com.xreader.app.data.AnnotationKind
import com.xreader.app.data.BookEntity
import com.xreader.app.data.BookmarkEntity
import java.time.Instant
import kotlin.math.roundToInt

internal object AnnotationMarkdownExport {
    fun build(
        exportedAt: Long,
        booksById: Map<Long, BookEntity>,
        annotations: List<AnnotationEntity>,
        bookmarks: List<BookmarkEntity>,
    ): String {
        val annotationsByBook = annotations
            .filter { booksById.containsKey(it.bookId) }
            .groupBy { it.bookId }
        val bookmarksByBook = bookmarks
            .filter { booksById.containsKey(it.bookId) }
            .groupBy { it.bookId }
        val bookIds = (annotationsByBook.keys + bookmarksByBook.keys)
            .distinct()
            .sortedWith(
                compareBy<Long> { booksById[it]?.sortTitle?.lowercase().orEmpty() }
                    .thenBy { booksById[it]?.author?.lowercase().orEmpty() }
            )

        return buildString {
            appendLine("# XReader Notes")
            appendLine()
            appendLine("Exported ${Instant.ofEpochMilli(exportedAt)}")
            appendLine()
            if (bookIds.isEmpty()) {
                appendLine("No notes, highlights, or bookmarks.")
                return@buildString
            }
            bookIds.forEachIndexed { index, bookId ->
                val book = booksById.getValue(bookId)
                if (index > 0) appendLine()
                appendLine("## ${book.title.markdownInline()}")
                appendLine()
                appendLine(book.author.markdownInline())
                val bookAnnotations = annotationsByBook[bookId].orEmpty()
                    .sortedBy { it.createdAt }
                if (bookAnnotations.isNotEmpty()) {
                    appendLine()
                    appendLine("### Notes and highlights")
                    bookAnnotations.forEach { annotation ->
                        appendLine()
                        appendLine("#### ${annotation.kind.label()} - ${Instant.ofEpochMilli(annotation.updatedAt)}")
                        appendLine()
                        appendBlockQuote(annotation.quote)
                        if (annotation.note.isNotBlank()) {
                            appendLine()
                            appendLine(annotation.note.trim())
                        }
                        if (annotation.tags.isNotBlank()) {
                            appendLine()
                            appendLine("Tags: ${annotation.tags.markdownInline()}")
                        }
                    }
                }
                val bookBookmarks = bookmarksByBook[bookId].orEmpty()
                    .sortedBy { it.progress }
                if (bookBookmarks.isNotEmpty()) {
                    appendLine()
                    appendLine("### Bookmarks")
                    bookBookmarks.forEach { bookmark ->
                        val percent = (bookmark.progress.coerceIn(0.0, 1.0) * 100).roundToInt()
                        appendLine("- $percent% - ${bookmark.label.markdownInline()}")
                    }
                }
            }
        }
    }

    private fun StringBuilder.appendBlockQuote(text: String) {
        text.trim()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { line ->
                append("> ")
                appendLine(line)
            }
    }

    private fun AnnotationKind.label(): String =
        when (this) {
            AnnotationKind.NOTE -> "Note"
            AnnotationKind.HIGHLIGHT -> "Highlight"
        }

    private fun String.markdownInline(): String =
        replace("\n", " ").replace(Regex("\\s+"), " ").trim()
}
