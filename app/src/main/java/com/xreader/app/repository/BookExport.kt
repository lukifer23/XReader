package com.xreader.app.repository

import com.xreader.app.core.TextTools
import com.xreader.app.data.BookEntity
import com.xreader.app.data.BookFormat
import java.util.Locale

internal fun bookExportFileName(book: BookEntity): String {
    val extension = storedBookExtension(book)
    val base = book.title
        .ifBlank { book.fileName.substringBeforeLast('.') }
        .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { "XReader book" }
        .take(MAX_EXPORT_BASENAME_LENGTH)
        .trimEnd('.', ' ')
    return "$base.$extension"
}

internal fun bookExportMimeType(book: BookEntity): String =
    when (storedBookExtension(book)) {
        "epub" -> "application/epub+zip"
        "pdf" -> "application/pdf"
        else -> "application/octet-stream"
    }

internal fun storedBookExtension(book: BookEntity): String =
    TextTools.extension(book.filePath).ifBlank {
        when (book.format) {
            BookFormat.EPUB -> "epub"
            BookFormat.PDF -> "pdf"
        }
    }.lowercase(Locale.US)

private const val MAX_EXPORT_BASENAME_LENGTH = 96
