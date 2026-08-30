package com.xreader.app.repository

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal data class FullBackupArchive(
    val operationId: String,
    val exportedAt: Long,
    val settingsCapturedAt: Long,
    val sections: Map<String, ByteArray>,
    val planSha256: String,
    val schemaVersion: Int,
    val appVersion: String,
)

internal object FullBackupArchiveCodec {
    const val FORMAT = "com.xreader.full-backup"
    const val VERSION = 2
    const val MIME_TYPE = "application/vnd.xreader.backup+zip"
    const val FILE_EXTENSION = ".xreader-backup"
    const val MAX_COMPRESSED_BYTES = 128L * 1024L * 1024L
    const val MAX_UNCOMPRESSED_BYTES = 256L * 1024L * 1024L
    const val MAX_SECTION_BYTES = 64L * 1024L * 1024L
    const val MAX_MANIFEST_BYTES = 1024L * 1024L
    const val MAX_ENTRIES = 16
    const val MAX_RECORDS_PER_SECTION = 100_000

    val requiredSections = setOf(
        "library.json",
        "annotations.json",
        "settings.json",
        "audiobooks.json",
        "narration.json",
    )

    fun create(
        operationId: String,
        exportedAt: Long,
        settingsCapturedAt: Long,
        schemaVersion: Int,
        appVersion: String,
        sections: Map<String, ByteArray>,
    ): ByteArray {
        require(sections.keys == requiredSections) { "Full backup sections are incomplete." }
        sections.forEach { (name, bytes) ->
            validateEntryName(name)
            require(bytes.size.toLong() <= MAX_SECTION_BYTES) { "$name exceeds the backup section limit." }
            validateJsonBounds(name, bytes)
        }
        val planSha = planSha256(sections)
        val entries = JSONArray()
        sections.toSortedMap().forEach { (name, bytes) ->
            entries.put(
                JSONObject()
                    .put("path", name)
                    .put("mediaType", "application/json")
                    .put("bytes", bytes.size)
                    .put("records", recordCount(bytes))
                    .put("sha256", sha256(bytes))
            )
        }
        val manifest = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("operationId", operationId)
            .put("exportedAt", exportedAt)
            .put("settingsCapturedAt", settingsCapturedAt)
            .put("schemaVersion", schemaVersion)
            .put("appVersion", appVersion)
            .put("planSha256", planSha)
            .put("entries", entries)
            .toString(2)
            .toByteArray(Charsets.UTF_8)
        require(manifest.size.toLong() <= MAX_MANIFEST_BYTES) { "Backup manifest is too large." }
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            writeEntry(zip, "manifest.json", manifest)
            sections.toSortedMap().forEach { (name, bytes) -> writeEntry(zip, name, bytes) }
        }
        return output.toByteArray().also {
            require(it.size.toLong() <= MAX_COMPRESSED_BYTES) { "Full backup exceeds the compressed size limit." }
        }
    }

    fun parse(bytes: ByteArray): FullBackupArchive {
        require(bytes.size.toLong() <= MAX_COMPRESSED_BYTES) { "Full backup exceeds the compressed size limit." }
        require(bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            "Full backup is not a ZIP archive."
        }
        val entries = linkedMapOf<String, ByteArray>()
        var totalBytes = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory) { "Backup directories are not allowed." }
                validateEntryName(entry.name)
                require(entries.size < MAX_ENTRIES) { "Backup contains too many entries." }
                require(!entries.containsKey(entry.name)) { "Backup contains duplicate entry ${entry.name}." }
                val limit = if (entry.name == "manifest.json") MAX_MANIFEST_BYTES else MAX_SECTION_BYTES
                val payload = readBounded(zip, limit)
                totalBytes += payload.size
                require(totalBytes <= MAX_UNCOMPRESSED_BYTES) { "Backup expands beyond the uncompressed size limit." }
                entries[entry.name] = payload
                zip.closeEntry()
            }
        }
        val manifestBytes = requireNotNull(entries.remove("manifest.json")) { "Backup manifest is missing." }
        val manifest = JSONObject(manifestBytes.toString(Charsets.UTF_8))
        require(manifest.optString("format") == FORMAT) { "This is not an XReader full backup." }
        require(manifest.optInt("version") == VERSION) { "Unsupported XReader backup version." }
        val operationId = manifest.optString("operationId").takeIf { it.length in 8..128 }
            ?: error("Backup operation id is invalid.")
        val declarations = manifest.optJSONArray("entries") ?: error("Backup entry manifest is missing.")
        require(declarations.length() <= MAX_ENTRIES) { "Backup entry manifest is too large." }
        data class Declaration(val bytes: Long, val records: Int, val sha256: String)
        val declared = linkedMapOf<String, Declaration>()
        for (index in 0 until declarations.length()) {
            val item = declarations.getJSONObject(index)
            val path = item.getString("path")
            validateEntryName(path)
            require(item.optString("mediaType") == "application/json") { "$path has an unsupported media type." }
            val size = item.getLong("bytes")
            val records = item.getInt("records")
            val checksum = item.getString("sha256")
            require(size in 0..MAX_SECTION_BYTES) { "$path declares an invalid size." }
            require(records in 0..MAX_RECORDS_PER_SECTION) { "$path declares an invalid record count." }
            require(checksum.matches(Regex("[0-9a-fA-F]{64}"))) { "$path declares an invalid checksum." }
            require(declared.put(path, Declaration(size, records, checksum)) == null) {
                "Backup manifest declares $path more than once."
            }
        }
        require(declared.keys == requiredSections) { "Backup manifest sections are incomplete or unexpected." }
        require(entries.keys == requiredSections) { "Backup archive sections are incomplete or unexpected." }
        entries.forEach { (name, payload) ->
            val declaration = requireNotNull(declared[name])
            require(declaration.bytes == payload.size.toLong()) { "$name size does not match its manifest." }
            require(declaration.records == recordCount(payload)) { "$name record count does not match its manifest." }
            require(declaration.sha256.equals(sha256(payload), ignoreCase = true)) { "$name checksum does not match its manifest." }
            validateJsonBounds(name, payload)
        }
        val planSha = planSha256(entries)
        require(manifest.optString("planSha256").equals(planSha, ignoreCase = true)) { "Backup plan checksum does not match." }
        val exportedAt = manifest.getLong("exportedAt")
        val settingsCapturedAt = manifest.getLong("settingsCapturedAt")
        val schemaVersion = manifest.getInt("schemaVersion")
        val appVersion = manifest.getString("appVersion").trim()
        require(exportedAt > 0L && settingsCapturedAt > 0L && settingsCapturedAt <= exportedAt) { "Backup timestamps are invalid." }
        require(schemaVersion > 0) { "Backup schema version is invalid." }
        require(appVersion.length in 1..128) { "Backup app version is invalid." }
        return FullBackupArchive(
            operationId = operationId,
            exportedAt = exportedAt,
            settingsCapturedAt = settingsCapturedAt,
            sections = entries,
            planSha256 = planSha,
            schemaVersion = schemaVersion,
            appVersion = appVersion,
        )
    }

    fun isZip(bytes: ByteArray): Boolean = bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    private fun validateJsonBounds(name: String, bytes: ByteArray) {
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        root.keys().forEach { key ->
            val array = root.optJSONArray(key) ?: return@forEach
            require(array.length() <= MAX_RECORDS_PER_SECTION) { "$name contains too many $key records." }
        }
    }

    private fun recordCount(bytes: ByteArray): Int {
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        var count = 0L
        root.keys().forEach { key -> count += root.optJSONArray(key)?.length()?.toLong() ?: 0L }
        require(count <= MAX_RECORDS_PER_SECTION) { "Backup section contains too many records." }
        return count.toInt()
    }

    private fun validateEntryName(name: String) {
        require(name.length in 1..128) { "Backup entry name is invalid." }
        require(!name.startsWith('/') && !name.contains('\\') && name.split('/').none { it == ".." || it.isBlank() }) {
            "Backup entry path is unsafe."
        }
        require('/' !in name) { "Nested backup entries are not allowed." }
    }

    private fun readBounded(zip: ZipInputStream, limit: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = zip.read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "Backup entry exceeds its size limit." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name).apply { time = 0L })
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun planSha256(sections: Map<String, ByteArray>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        sections.toSortedMap().forEach { (name, bytes) ->
            digest.update(name.toByteArray(Charsets.UTF_8))
            digest.update(0)
            digest.update(bytes)
            digest.update(0)
        }
        return digest.digest().hex()
    }

    internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).hex()
    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
}

internal data class SplitLibraryBackup(val libraryJson: String, val settingsJson: String)

internal fun splitLibraryBackup(json: String): SplitLibraryBackup {
    val root = JSONObject(json)
    val settings = JSONObject()
        .put("format", "com.xreader.settings-backup.v1")
        .put("version", 1)
    listOf("readerSettings", "librarySettings", "readerAppearances").forEach { key ->
        if (root.has(key)) settings.put(key, root.remove(key))
    }
    return SplitLibraryBackup(root.toString(), settings.toString())
}

internal fun settingsAsLibraryBackup(settingsJson: String): String {
    val settings = JSONObject(settingsJson)
    require(settings.optString("format") == "com.xreader.settings-backup.v1") { "Backup settings section is invalid." }
    val library = JSONObject()
        .put("format", "com.xreader.library-backup.v1")
        .put("version", 1)
        .put("books", JSONArray())
        .put("collections", JSONArray())
        .put("readingStates", JSONArray())
        .put("readingSessions", JSONArray())
    listOf("readerSettings", "librarySettings", "readerAppearances").forEach { key ->
        if (settings.has(key)) library.put(key, settings.get(key))
    }
    return library.toString()
}
