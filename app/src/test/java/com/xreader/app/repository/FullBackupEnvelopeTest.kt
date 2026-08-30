package com.xreader.app.repository

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FullBackupEnvelopeTest {
    private val library = JSONObject()
        .put("format", "com.xreader.library-backup.v1")
        .put("version", 1)
        .put("books", org.json.JSONArray())
        .toString()
    private val annotations = JSONObject()
        .put("format", "com.xreader.annotations.v1")
        .put("version", 1)
        .put("annotations", org.json.JSONArray())
        .put("bookmarks", org.json.JSONArray())
        .toString()

    @Test
    fun roundTripPreservesBothRealPayloads() {
        val encoded = FullBackupEnvelope.create(1234L, library, annotations)
        val decoded = FullBackupEnvelope.parse(encoded)

        assertEquals("com.xreader.library-backup.v1", JSONObject(decoded.libraryJson).getString("format"))
        assertEquals("com.xreader.annotations.v1", JSONObject(decoded.annotationJson).getString("format"))
        assertEquals(1234L, JSONObject(encoded).getLong("exportedAt"))
    }

    @Test
    fun rejectsUnsupportedAndPartialBackupsBeforeRestore() {
        val unsupported = JSONObject(FullBackupEnvelope.create(1L, library, annotations)).put("version", 99).toString()
        val partial = JSONObject()
            .put("format", FullBackupRepository.FORMAT)
            .put("version", 1)
            .put("library", JSONObject(library))
            .toString()

        assertTrue(runCatching { FullBackupEnvelope.parse(unsupported) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { FullBackupEnvelope.parse(partial) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun rejectsWrongNestedBackupKinds() {
        val wrong = FullBackupEnvelope.create(
            exportedAt = 1L,
            libraryJson = JSONObject(library).put("format", "wrong").toString(),
            annotationJson = annotations,
        )
        assertTrue(runCatching { FullBackupEnvelope.parse(wrong) }.exceptionOrNull() is IllegalArgumentException)
    }
}
