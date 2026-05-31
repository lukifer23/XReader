package com.xreader.app.importer

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncomingBookIntentInstrumentedTest {
    @Test
    fun viewIntentImportsSingleReadableUri() {
        val uri = Uri.parse("content://com.example.books/novel.epub")
        val import = Intent(Intent.ACTION_VIEW, uri).toIncomingBookImport()

        assertEquals(listOf(uri), import?.uris)
    }

    @Test
    fun sendIntentImportsStreamUri() {
        val uri = Uri.parse("content://com.example.books/report.pdf")
        val import = Intent(Intent.ACTION_SEND)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .toIncomingBookImport()

        assertEquals(listOf(uri), import?.uris)
    }

    @Test
    fun sendMultipleIntentImportsDistinctStreamUris() {
        val first = Uri.parse("content://com.example.books/one.epub")
        val second = Uri.parse("file:///sdcard/Download/two.txt")
        val import = Intent(Intent.ACTION_SEND_MULTIPLE)
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second, first))
            .toIncomingBookImport()

        assertEquals(listOf(first, second), import?.uris)
    }

    @Test
    fun ignoresLauncherAndWebUrlIntents() {
        val uri = Uri.parse("content://com.example.books/not-imported.epub")
        val launcher = Intent(Intent.ACTION_MAIN).apply {
            clipData = ClipData.newRawUri("book", uri)
        }

        assertNull(launcher.toIncomingBookImport())
        assertNull(Intent(Intent.ACTION_MAIN).toIncomingBookImport())
        assertNull(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.test/book.epub")).toIncomingBookImport())
    }
}
