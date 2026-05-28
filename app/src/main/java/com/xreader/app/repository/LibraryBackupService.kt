package com.xreader.app.repository

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LibraryBackupService(
    private val context: Context,
    private val repository: LibraryBackupRepository,
) {
    suspend fun exportTo(uri: Uri): LibraryBackupRepository.ExportResult = withContext(Dispatchers.IO) {
        val result = repository.exportBackupJson()
        context.contentResolver.openOutputStream(uri, "wt").use { output ->
            requireNotNull(output) { "Could not open library backup destination." }
            output.bufferedWriter().use { it.write(result.json) }
        }
        result
    }

    suspend fun importFrom(uri: Uri): LibraryBackupRepository.ImportResult = withContext(Dispatchers.IO) {
        val json = context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open library backup file." }
            input.bufferedReader().use { it.readText() }
        }
        repository.importBackupJson(json)
    }
}
