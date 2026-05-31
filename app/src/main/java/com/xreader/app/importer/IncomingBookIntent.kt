package com.xreader.app.importer

import android.content.ClipData
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri

data class IncomingBookImport(
    val uris: List<Uri>,
)

fun Intent.toIncomingBookImport(): IncomingBookImport? {
    val uris = linkedSetOf<Uri>()
    when (action) {
        Intent.ACTION_VIEW -> {
            data?.let(uris::add)
            clipData?.bookUris()?.let { uris += it }
        }
        Intent.ACTION_SEND -> {
            streamUri()?.let(uris::add)
            clipData?.bookUris()?.let { uris += it }
        }
        Intent.ACTION_SEND_MULTIPLE -> {
            uris += streamUris()
            clipData?.bookUris()?.let { uris += it }
        }
        else -> return null
    }
    val readableUris = uris
        .filter { it.isReadableDocumentUri() }
        .distinctBy { it.toString() }
    return readableUris.takeIf { it.isNotEmpty() }?.let(::IncomingBookImport)
}

private fun ClipData.bookUris(): List<Uri> =
    buildList {
        for (index in 0 until itemCount) {
            getItemAt(index).uri?.let(::add)
        }
    }

@Suppress("DEPRECATION")
private fun Intent.streamUri(): Uri? =
    getParcelableExtra(Intent.EXTRA_STREAM)

@Suppress("DEPRECATION")
private fun Intent.streamUris(): List<Uri> =
    getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()

private fun Uri.isReadableDocumentUri(): Boolean =
    scheme.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true) ||
        scheme.equals(ContentResolver.SCHEME_FILE, ignoreCase = true)
