package com.xreader.app.repository

import com.xreader.app.data.BookEntity
import java.util.Locale

internal fun String.normalizedBackupChecksum(): String? =
    trim()
        .lowercase(Locale.US)
        .takeIf { it.isNotBlank() }

internal fun List<BookEntity>.byNormalizedChecksum(): Map<String, BookEntity> =
    mapNotNull { book ->
        book.checksum.normalizedBackupChecksum()?.let { checksum -> checksum to book }
    }.toMap()
