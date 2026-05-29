package com.xreader.app.repository

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AnnotationBackupService(
    private val context: Context,
    private val annotationRepository: AnnotationRepository,
) {
    suspend fun exportTo(uri: Uri): AnnotationRepository.BackupExportResult = withContext(Dispatchers.IO) {
        val result = annotationRepository.exportBackupJson()
        context.contentResolver.openOutputStream(uri, "wt").use { output ->
            requireNotNull(output) { "Could not open backup destination." }
            output.bufferedWriter().use { it.write(result.json) }
        }
        result
    }

    suspend fun exportMarkdownTo(uri: Uri): AnnotationRepository.MarkdownExportResult = withContext(Dispatchers.IO) {
        val result = annotationRepository.exportMarkdown()
        context.contentResolver.openOutputStream(uri, "wt").use { output ->
            requireNotNull(output) { "Could not open export destination." }
            output.bufferedWriter().use { it.write(result.markdown) }
        }
        result
    }

    suspend fun importFrom(uri: Uri): AnnotationRepository.BackupImportResult = withContext(Dispatchers.IO) {
        val json = context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open backup file." }
            input.bufferedReader().use { it.readText() }
        }
        annotationRepository.importBackupJson(json)
    }
}
