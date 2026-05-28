package com.xreader.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xreader.app.AppContainer
import com.xreader.app.data.ReaderTheme
import com.xreader.app.settings.ReaderFontFamily
import com.xreader.app.settings.ReaderPdfFit
import com.xreader.app.settings.ReaderSettings
import com.xreader.app.settings.ReaderTextAlign
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsMaintenanceUiState(
    val repairingLibrary: Boolean = false,
    val message: String? = null,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<ReaderSettings> =
        container.settingsRepository.settings
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderSettings())

    private val _maintenance = MutableStateFlow(SettingsMaintenanceUiState())
    val maintenance: StateFlow<SettingsMaintenanceUiState> = _maintenance

    fun setTheme(theme: com.xreader.app.data.ReaderTheme) {
        viewModelScope.launch { container.settingsRepository.setTheme(theme) }
    }

    fun setFontScale(value: Float) {
        viewModelScope.launch { container.settingsRepository.setFontScale(value) }
    }

    fun setLineHeight(value: Float) {
        viewModelScope.launch { container.settingsRepository.setLineHeight(value) }
    }

    fun setMarginScale(value: Float) {
        viewModelScope.launch { container.settingsRepository.setMarginScale(value) }
    }

    fun setFontFamily(value: ReaderFontFamily) {
        viewModelScope.launch { container.settingsRepository.setFontFamily(value) }
    }

    fun setTapZonesEnabled(value: Boolean) {
        viewModelScope.launch { container.settingsRepository.setTapZonesEnabled(value) }
    }

    fun setPageTurnAnimations(value: Boolean) {
        viewModelScope.launch { container.settingsRepository.setPageTurnAnimations(value) }
    }

    fun setFullScreen(value: Boolean) {
        viewModelScope.launch { container.settingsRepository.setFullScreen(value) }
    }

    fun setPublisherStyles(value: Boolean) {
        viewModelScope.launch { container.settingsRepository.setPublisherStyles(value) }
    }

    fun setTextAlign(value: ReaderTextAlign) {
        viewModelScope.launch { container.settingsRepository.setTextAlign(value) }
    }

    fun setPdfFit(value: ReaderPdfFit) {
        viewModelScope.launch { container.settingsRepository.setPdfFit(value) }
    }

    fun toggleLightDark() {
        val next = when (settings.value.theme) {
            ReaderTheme.LIGHT, ReaderTheme.SEPIA -> ReaderTheme.DARK
            ReaderTheme.DARK, ReaderTheme.OLED -> ReaderTheme.LIGHT
        }
        viewModelScope.launch { container.settingsRepository.setTheme(next) }
    }

    fun repairLibrary() {
        if (_maintenance.value.repairingLibrary) return
        viewModelScope.launch {
            _maintenance.value = SettingsMaintenanceUiState(repairingLibrary = true)
            val message = runCatching { container.libraryRepository.repairLibrary() }
                .fold(
                    onSuccess = { it.summaryMessage() },
                    onFailure = { it.message ?: "Library repair failed" }
                )
            _maintenance.value = SettingsMaintenanceUiState(message = message)
        }
    }

    fun clearMaintenanceMessage() {
        _maintenance.update { it.copy(message = null) }
    }

    private fun com.xreader.app.importer.ImportService.LibraryRepairResult.summaryMessage(): String {
        val details = buildList {
            if (coversUpdated > 0) add("$coversUpdated covers")
            if (metadataUpdated > 0) add("$metadataUpdated metadata updates")
            if (failed > 0) add("$failed failed")
        }
        val base = "Repaired $scanned ${if (scanned == 1) "book" else "books"}; rebuilt $searchRows search rows"
        return if (details.isEmpty()) base else "$base; ${details.joinToString(", ")}"
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SettingsViewModel(container) as T
            }
    }
}
