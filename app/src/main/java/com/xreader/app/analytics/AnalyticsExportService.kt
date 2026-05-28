package com.xreader.app.analytics

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AnalyticsExportService(
    private val context: Context,
    private val repository: AnalyticsRepository,
) {
    suspend fun exportTo(uri: Uri): AnalyticsExportResult = withContext(Dispatchers.IO) {
        val result = repository.exportSummariesJson()
        context.contentResolver.openOutputStream(uri, "wt").use { output ->
            requireNotNull(output) { "Could not open analytics export destination." }
            output.bufferedWriter().use { it.write(result.json) }
        }
        result
    }

    suspend fun exportCsvTo(uri: Uri): AnalyticsCsvExportResult = withContext(Dispatchers.IO) {
        val result = repository.exportSummariesCsv()
        context.contentResolver.openOutputStream(uri, "wt").use { output ->
            requireNotNull(output) { "Could not open analytics export destination." }
            output.bufferedWriter().use { it.write(result.csv) }
        }
        result
    }
}
