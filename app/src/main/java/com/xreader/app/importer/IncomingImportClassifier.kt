package com.xreader.app.importer

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.xreader.app.audiobook.SupportedAudiobookTypes

enum class IncomingImportKind { BOOK, AUDIOBOOK, ACSM }

fun ContentResolver.classifyIncomingImport(uri: Uri): IncomingImportKind {
    val mimeType = getType(uri).orEmpty().substringBefore(';').trim().lowercase()
    val displayName = when (uri.scheme?.lowercase()) {
        ContentResolver.SCHEME_FILE -> uri.lastPathSegment.orEmpty()
        else -> runCatching {
            query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
            }.orEmpty()
        }.getOrDefault("")
    }
    val extension = displayName.substringAfterLast('.', "").lowercase()
    return when {
        extension == "acsm" || mimeType == ACSM_MIME_TYPE -> IncomingImportKind.ACSM
        SupportedAudiobookTypes.isSupported(displayName, mimeType) -> IncomingImportKind.AUDIOBOOK
        else -> IncomingImportKind.BOOK
    }
}

const val ACSM_MIME_TYPE = "application/vnd.adobe.adept+xml"
