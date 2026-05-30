package com.xreader.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xreader.app.AppContainer
import com.xreader.app.data.ReaderTheme
import com.xreader.app.settings.LibraryDensity
import com.xreader.app.settings.LibrarySettings
import com.xreader.app.settings.LibrarySort
import com.xreader.app.settings.ReadAloudSleepTimer
import com.xreader.app.settings.ReaderFontFamily
import com.xreader.app.settings.ReaderPdfFit
import com.xreader.app.settings.ReaderSettings
import com.xreader.app.settings.ReaderSpacingPreset
import com.xreader.app.settings.ReaderTapZonePreset
import com.xreader.app.settings.ReaderTextAlign
import com.xreader.app.tts.ReadAloudVoiceOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsMaintenanceUiState(
    val repairingLibrary: Boolean = false,
    val exportingLibrary: Boolean = false,
    val importingLibrary: Boolean = false,
    val exportingAnnotations: Boolean = false,
    val importingAnnotations: Boolean = false,
    val message: String? = null,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<ReaderSettings> =
        container.settingsRepository.settings
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderSettings())

    val librarySettings: StateFlow<LibrarySettings> =
        container.settingsRepository.librarySettings
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibrarySettings())

    val readAloudVoices: StateFlow<List<ReadAloudVoiceOption>> =
        container.readAloudEngine.voices
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _maintenance = MutableStateFlow(SettingsMaintenanceUiState())
    val maintenance: StateFlow<SettingsMaintenanceUiState> = _maintenance

    init {
        viewModelScope.launch {
            container.readAloudEngine.refreshVoices()
        }
    }

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

    fun setSpacingPreset(value: ReaderSpacingPreset) {
        viewModelScope.launch { container.settingsRepository.setSpacingPreset(value) }
    }

    fun setFontFamily(value: ReaderFontFamily) {
        viewModelScope.launch { container.settingsRepository.setFontFamily(value) }
    }

    fun setTapZonesEnabled(value: Boolean) {
        viewModelScope.launch { container.settingsRepository.setTapZonesEnabled(value) }
    }

    fun setTapZonePreset(value: ReaderTapZonePreset) {
        viewModelScope.launch { container.settingsRepository.setTapZonePreset(value) }
    }

    fun setPageTurnAnimations(value: Boolean) {
        viewModelScope.launch { container.settingsRepository.setPageTurnAnimations(value) }
    }

    fun setKeepScreenAwake(value: Boolean) {
        viewModelScope.launch { container.settingsRepository.setKeepScreenAwake(value) }
    }

    fun setVolumeKeysTurnPages(value: Boolean) {
        viewModelScope.launch { container.settingsRepository.setVolumeKeysTurnPages(value) }
    }

    fun setScreenDim(value: Float) {
        viewModelScope.launch { container.settingsRepository.setScreenDim(value) }
    }

    fun setReadAloudRate(value: Float) {
        viewModelScope.launch { container.settingsRepository.setReadAloudRate(value) }
        container.readAloudEngine.setSpeechRate(value)
    }

    fun setReadAloudVoiceName(value: String?) {
        viewModelScope.launch { container.settingsRepository.setReadAloudVoiceName(value) }
        container.readAloudEngine.setVoice(value)
    }

    fun setReadAloudSleepTimer(value: ReadAloudSleepTimer) {
        viewModelScope.launch { container.settingsRepository.setReadAloudSleepTimer(value) }
        container.readAloudEngine.setSleepTimer(value.durationMillis)
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

    fun setLibrarySort(value: LibrarySort) {
        viewModelScope.launch { container.settingsRepository.setLibrarySort(value) }
    }

    fun setLibraryDensity(value: LibraryDensity) {
        viewModelScope.launch { container.settingsRepository.setLibraryDensity(value) }
    }

    fun toggleLightDark() {
        val next = when (settings.value.theme) {
            ReaderTheme.LIGHT, ReaderTheme.SEPIA -> ReaderTheme.DARK
            ReaderTheme.DARK, ReaderTheme.OLED -> ReaderTheme.LIGHT
        }
        viewModelScope.launch { container.settingsRepository.setTheme(next) }
    }

    fun repairLibrary() {
        if (_maintenance.value.isBusy()) return
        viewModelScope.launch {
            _maintenance.update { it.copy(repairingLibrary = true, message = null) }
            val message = runCatching { container.libraryRepository.repairLibrary() }
                .fold(
                    onSuccess = { it.summaryMessage() },
                    onFailure = { it.message ?: "Library repair failed" }
                )
            _maintenance.update { it.copy(repairingLibrary = false, message = message) }
        }
    }

    fun exportAnnotations(uri: Uri) {
        if (_maintenance.value.isBusy()) return
        viewModelScope.launch {
            _maintenance.update { it.copy(exportingAnnotations = true, message = null) }
            val message = runCatching { container.annotationBackupService.exportTo(uri) }
                .fold(
                    onSuccess = {
                        "Exported ${it.annotations} ${if (it.annotations == 1) "annotation" else "annotations"} and ${it.bookmarks} ${if (it.bookmarks == 1) "bookmark" else "bookmarks"}"
                    },
                    onFailure = { it.message ?: "Notes export failed" }
                )
            _maintenance.update { it.copy(exportingAnnotations = false, message = message) }
        }
    }

    fun exportLibrary(uri: Uri) {
        if (_maintenance.value.isBusy()) return
        viewModelScope.launch {
            _maintenance.update { it.copy(exportingLibrary = true, message = null) }
            val message = runCatching { container.libraryBackupService.exportTo(uri) }
                .fold(
                    onSuccess = {
                        "Exported ${it.books} ${if (it.books == 1) "book" else "books"}, ${it.collections} ${if (it.collections == 1) "collection" else "collections"}, ${it.readingStates} progress states, and ${it.readingSessions} sessions"
                    },
                    onFailure = { it.message ?: "Library export failed" }
                )
            _maintenance.update { it.copy(exportingLibrary = false, message = message) }
        }
    }

    fun importLibrary(uri: Uri) {
        if (_maintenance.value.isBusy()) return
        viewModelScope.launch {
            _maintenance.update { it.copy(importingLibrary = true, message = null) }
            val message = runCatching { container.libraryBackupService.importFrom(uri) }
                .fold(
                    onSuccess = { it.summaryMessage() },
                    onFailure = { it.message ?: "Library import failed" }
                )
            _maintenance.update { it.copy(importingLibrary = false, message = message) }
        }
    }

    fun importAnnotations(uri: Uri) {
        if (_maintenance.value.isBusy()) return
        viewModelScope.launch {
            _maintenance.update { it.copy(importingAnnotations = true, message = null) }
            val message = runCatching { container.annotationBackupService.importFrom(uri) }
                .fold(
                    onSuccess = { it.summaryMessage() },
                    onFailure = { it.message ?: "Notes import failed" }
                )
            _maintenance.update { it.copy(importingAnnotations = false, message = message) }
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

    private fun com.xreader.app.repository.AnnotationRepository.BackupImportResult.summaryMessage(): String {
        val changed = annotationsImported + annotationsUpdated + bookmarksImported
        val skipped = annotationsSkipped + bookmarksSkipped
        val base = "Imported $changed ${if (changed == 1) "item" else "items"}"
        val details = buildList {
            if (annotationsUpdated > 0) add("$annotationsUpdated updated")
            if (skipped > 0) add("$skipped skipped")
            if (missingBooks > 0) add("$missingBooks missing books")
        }
        return if (details.isEmpty()) base else "$base; ${details.joinToString(", ")}"
    }

    private fun com.xreader.app.repository.LibraryBackupRepository.ImportResult.summaryMessage(): String {
        val changed = booksUpdated + collectionsImported + collectionMembershipsImported + readingStatesImported + readingSessionsImported
        val skipped = collectionMembershipsSkipped + readingStatesSkipped + readingSessionsSkipped
        val base = "Imported $changed library ${if (changed == 1) "item" else "items"}"
        val details = buildList {
            if (booksUpdated > 0) add("$booksUpdated metadata updates")
            if (collectionsImported > 0) add("$collectionsImported collections")
            if (collectionMembershipsImported > 0) add("$collectionMembershipsImported collection links")
            if (skipped > 0) add("$skipped skipped")
            if (missingBooks > 0) add("$missingBooks missing books")
            if (invalidItems > 0) add("$invalidItems invalid")
        }
        return if (details.isEmpty()) base else "$base; ${details.joinToString(", ")}"
    }

    private fun SettingsMaintenanceUiState.isBusy(): Boolean =
        repairingLibrary || exportingLibrary || importingLibrary || exportingAnnotations || importingAnnotations

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SettingsViewModel(container) as T
            }
    }
}
