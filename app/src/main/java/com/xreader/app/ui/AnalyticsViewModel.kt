package com.xreader.app.ui

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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AnalyticsUiState(
    val summary: AnalyticsSummary? = null,
    val selectedRange: AnalyticsRange = AnalyticsRange.MONTH,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModel(container: AppContainer) : ViewModel() {
    private val selectedRange = MutableStateFlow(AnalyticsRange.MONTH)

    val uiState: StateFlow<AnalyticsUiState> =
        selectedRange
            .flatMapLatest { range ->
                container.analyticsRepository.observeSummary(range)
                    .map { summary -> AnalyticsUiState(summary = summary, selectedRange = range) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState())

    fun setRange(range: AnalyticsRange) {
        selectedRange.value = range
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
