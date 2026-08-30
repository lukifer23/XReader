package com.xreader.app.repository

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class FullBackupArchiveCodecTest {
    private fun section(format: String, arrayName: String): ByteArray = JSONObject()
        .put("format", format)
        .put("version", 1)
        .put(arrayName, JSONArray())
        .toString()
        .toByteArray()

    private fun sections(): Map<String, ByteArray> = mapOf(
        "library.json" to section("com.xreader.library-backup.v1", "books"),
        "annotations.json" to section("com.xreader.annotations.v1", "annotations"),
        "settings.json" to section("com.xreader.settings-backup.v1", "readerAppearances"),
        "audiobooks.json" to section("com.xreader.audiobooks-backup.v1", "audiobooks"),
        "narration.json" to section("com.xreader.narration-backup.v1", "pronunciationRules"),
    )

    @Test
    fun roundTripPreservesEveryDeclaredSectionAndManifestIdentity() {
        val expected = sections()
        val bytes = FullBackupArchiveCodec.create("operation-123", 2_000L, 1_900L, 16, "1.2.3", expected)

        val actual = FullBackupArchiveCodec.parse(bytes)

        assertEquals("operation-123", actual.operationId)
        assertEquals(16, actual.schemaVersion)
        assertEquals("1.2.3", actual.appVersion)
        assertEquals(expected.keys, actual.sections.keys)
        expected.forEach { (name, payload) -> assertArrayEquals(payload, actual.sections.getValue(name)) }
    }

    @Test
    fun createRejectsMissingAndOversizedRecordSections() {
        val missing = sections() - "narration.json"
        assertTrue(runCatching { FullBackupArchiveCodec.create("operation-123", 2L, 1L, 16, "1", missing) }.isFailure)

        val tooMany = JSONObject()
            .put("format", "com.xreader.library-backup.v1")
            .put("books", JSONArray().apply { repeat(FullBackupArchiveCodec.MAX_RECORDS_PER_SECTION + 1) { put(JSONObject()) } })
            .toString().toByteArray()
        assertTrue(
            runCatching {
                FullBackupArchiveCodec.create("operation-123", 2L, 1L, 16, "1", sections() + ("library.json" to tooMany))
            }.isFailure
        )
    }

    @Test
    fun parseRejectsTraversalUnexpectedEntriesAndChangedPayloads() {
        val valid = FullBackupArchiveCodec.create("operation-123", 2L, 1L, 16, "1", sections())
        val entries = unzip(valid)

        assertTrue(runCatching { FullBackupArchiveCodec.parse(zip(entries + ("../escape.json" to byteArrayOf(1)))) }.isFailure)
        assertTrue(runCatching { FullBackupArchiveCodec.parse(zip(entries + ("extra.json" to byteArrayOf(1)))) }.isFailure)
        assertTrue(
            runCatching {
                FullBackupArchiveCodec.parse(zip(entries + ("library.json" to "{}".toByteArray())))
            }.exceptionOrNull()?.message.orEmpty().contains("size does not match")
        )
    }

    @Test
    fun parseRejectsManifestCountAndTimestampTampering() {
        val entries = unzip(FullBackupArchiveCodec.create("operation-123", 2L, 1L, 16, "1", sections())).toMutableMap()
        val manifest = JSONObject(entries.getValue("manifest.json").toString(Charsets.UTF_8))
        manifest.getJSONArray("entries").getJSONObject(0).put("records", 99)
        entries["manifest.json"] = manifest.toString().toByteArray()
        assertTrue(runCatching { FullBackupArchiveCodec.parse(zip(entries)) }.isFailure)

        manifest.getJSONArray("entries").getJSONObject(0).put("records", 1)
        manifest.put("settingsCapturedAt", 3L)
        entries["manifest.json"] = manifest.toString().toByteArray()
        assertTrue(runCatching { FullBackupArchiveCodec.parse(zip(entries)) }.isFailure)
    }

    @Test
    fun librarySettingsSplitRoundTripsWithoutBookData() {
        val original = JSONObject()
            .put("format", "com.xreader.library-backup.v1")
            .put("version", 1)
            .put("books", JSONArray().put(JSONObject().put("checksum", "a")))
            .put("readerSettings", JSONObject().put("fontSizeSp", 20))
            .put("librarySettings", JSONObject().put("density", "COMPACT"))
            .put("readerAppearances", JSONArray())
            .toString()

        val split = splitLibraryBackup(original)
        assertTrue(!JSONObject(split.libraryJson).has("readerSettings"))
        val settingsOnly = JSONObject(settingsAsLibraryBackup(split.settingsJson))
        assertEquals(0, settingsOnly.getJSONArray("books").length())
        assertEquals(20, settingsOnly.getJSONObject("readerSettings").getInt("fontSizeSp"))
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                result[entry.name] = zip.readBytes()
            }
        }
        return result
    }

    private fun zip(entries: Map<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
