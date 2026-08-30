package com.xreader.app.repository

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

class FullBackupService(
    context: Context,
    private val repository: FullBackupRepository,
) {
    private val appContext = context.applicationContext
    private val journalFile = File(appContext.filesDir, "maintenance/restore-journal.json")

    suspend fun exportTo(uri: Uri): FullBackupRepository.ArchiveExportResult = withContext(Dispatchers.IO) {
        val result = repository.exportBackupArchive()
        appContext.contentResolver.openOutputStream(uri, "wt").use { output ->
            requireNotNull(output) { "Could not open full backup destination." }
            output.write(result.bytes)
            output.flush()
        }
        result
    }

    suspend fun importFrom(uri: Uri): FullBackupRepository.ImportResult = withContext(Dispatchers.IO) {
        recoverInterruptedRestore()
        val bytes = appContext.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open full backup file." }
            readBounded(input, FullBackupArchiveCodec.MAX_COMPRESSED_BYTES)
        }
        if (!FullBackupArchiveCodec.isZip(bytes)) {
            require(bytes.size.toLong() <= FullBackupArchiveCodec.MAX_SECTION_BYTES) { "Legacy backup exceeds the size limit." }
            return@withContext repository.importBackupJson(bytes.toString(Charsets.UTF_8))
        }
        val plan = repository.parseArchive(bytes)
        repository.restoreOperation(plan.operationId)?.let { committed ->
            require(committed.planSha256.equals(plan.planSha256, ignoreCase = true)) {
                "Backup operation id conflicts with a previously restored archive."
            }
        }
        val previousSettings = repository.currentSettingsJson()
        writeJournal(plan.operationId, plan.planSha256, previousSettings)
        try {
            repository.applySettingsJson(plan.settingsJson)
            val result = repository.applyRestorePlan(plan)
            journalFile.delete()
            result
        } catch (error: Throwable) {
            if (!repository.restoreCommitted(plan.operationId)) {
                runCatching { repository.applySettingsJson(previousSettings) }
                    .onFailure { rollback -> error.addSuppressed(rollback) }
            }
            journalFile.delete()
            throw error
        }
    }

    suspend fun recoverInterruptedRestore() = withContext(Dispatchers.IO) {
        if (!journalFile.isFile) return@withContext
        val journal = runCatching { JSONObject(AtomicFile(journalFile).readFully().toString(Charsets.UTF_8)) }.getOrNull()
        val operationId = journal?.optString("operationId").orEmpty()
        val previousSettings = journal?.optJSONObject("previousSettings")?.toString()
        if (operationId.isNotBlank() && !repository.restoreCommitted(operationId) && previousSettings != null) {
            repository.applySettingsJson(previousSettings)
        }
        journalFile.delete()
    }

    private fun writeJournal(operationId: String, planSha256: String, previousSettings: String) {
        val body = JSONObject()
            .put("operationId", operationId)
            .put("planSha256", planSha256)
            .put("previousSettings", JSONObject(previousSettings))
            .toString()
        val parent = requireNotNull(journalFile.parentFile)
        require(parent.exists() || parent.mkdirs()) { "Could not prepare restore recovery journal." }
        val atomicFile = AtomicFile(journalFile)
        val output = atomicFile.startWrite()
        try {
            output.write(body.toByteArray(Charsets.UTF_8))
            output.flush()
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    private fun readBounded(input: java.io.InputStream, maxBytes: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= maxBytes) { "Backup exceeds the input size limit." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
