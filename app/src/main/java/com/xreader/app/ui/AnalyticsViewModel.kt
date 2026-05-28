package com.xreader.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xreader.app.AppContainer
import com.xreader.app.analytics.AnalyticsRange
import com.xreader.app.analytics.AnalyticsSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AnalyticsUiState(
    val summary: AnalyticsSummary? = null,
    val selectedRange: AnalyticsRange = AnalyticsRange.MONTH,
    val exporting: Boolean = false,
    val message: String? = null,
)

private data class AnalyticsExportUiState(
    val exporting: Boolean = false,
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModel(private val container: AppContainer) : ViewModel() {
    private val selectedRange = MutableStateFlow(AnalyticsRange.MONTH)
    private val exportState = MutableStateFlow(AnalyticsExportUiState())

    val uiState: StateFlow<AnalyticsUiState> =
        selectedRange
            .flatMapLatest { range ->
                container.analyticsRepository.observeSummary(range)
                    .map { summary -> summary to range }
            }
            .combine(exportState) { (summary, range), export ->
                AnalyticsUiState(
                    summary = summary,
                    selectedRange = range,
                    exporting = export.exporting,
                    message = export.message
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState())

    fun setRange(range: AnalyticsRange) {
        selectedRange.value = range
    }

    fun exportAnalytics(uri: Uri) {
        if (exportState.value.exporting) return
        viewModelScope.launch {
            exportState.update { it.copy(exporting = true, message = null) }
            val message = runCatching { container.analyticsExportService.exportTo(uri) }
                .fold(
                    onSuccess = {
                        "Exported JSON for ${it.ranges} stat ranges from ${it.readingSessions} ${if (it.readingSessions == 1) "session" else "sessions"}"
                    },
                    onFailure = { it.message ?: "Stats export failed" }
                )
            exportState.update { it.copy(exporting = false, message = message) }
        }
    }

    fun exportAnalyticsCsv(uri: Uri) {
        if (exportState.value.exporting) return
        viewModelScope.launch {
            exportState.update { it.copy(exporting = true, message = null) }
            val message = runCatching { container.analyticsExportService.exportCsvTo(uri) }
                .fold(
                    onSuccess = {
                        "Exported CSV for ${it.ranges} stat ranges from ${it.readingSessions} ${if (it.readingSessions == 1) "session" else "sessions"}"
                    },
                    onFailure = { it.message ?: "Stats export failed" }
                )
            exportState.update { it.copy(exporting = false, message = message) }
        }
    }

    fun clearMessage() {
        exportState.update { it.copy(message = null) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AnalyticsViewModel(container) as T
            }
    }
}
