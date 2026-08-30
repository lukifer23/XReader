@file:OptIn(ExperimentalMaterial3Api::class)

package com.xreader.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xreader.app.AppContainer
import com.xreader.app.annotations.annotationTagsLabel
import com.xreader.app.annotations.normalizeAnnotationNote
import com.xreader.app.data.AnnotationEntity
import com.xreader.app.data.AnnotationKind
import com.xreader.app.data.BookAudioEntity
import com.xreader.app.data.BookAudioStatus
import com.xreader.app.data.BookAudioWithBook
import com.xreader.app.data.BookEntity
import com.xreader.app.data.ReaderTheme
import com.xreader.app.analytics.ActivityBucketAnalytics
import com.xreader.app.analytics.ActivityBucketGranularity
import com.xreader.app.analytics.AnalyticsRange
import com.xreader.app.analytics.AnalyticsSummary
import com.xreader.app.analytics.BookAnalytics
import com.xreader.app.analytics.GroupAnalytics
import com.xreader.app.data.NeuralTtsModelEntity
import com.xreader.app.data.NeuralTtsModelStatus
import com.xreader.app.settings.LibraryDensity
import com.xreader.app.settings.LibrarySort
import com.xreader.app.settings.MAX_READER_DIM_AMOUNT
import com.xreader.app.settings.MAX_READER_FONT_WEIGHT
import com.xreader.app.settings.MIN_READER_FONT_WEIGHT
import com.xreader.app.settings.NeuralTtsGender
import com.xreader.app.settings.NeuralTtsPace
import com.xreader.app.settings.NeuralTtsTone
import com.xreader.app.settings.ReadAloudSleepTimer
import com.xreader.app.settings.ReaderFontFamily
import com.xreader.app.settings.ReaderHighlightColor
import com.xreader.app.settings.ReaderOrientation
import com.xreader.app.settings.ReaderPageDirection
import com.xreader.app.settings.ReaderPdfFit
import com.xreader.app.settings.ReaderPdfScrollAxis
import com.xreader.app.settings.ReaderSpacingPreset
import com.xreader.app.settings.ReaderTapZonePreset
import com.xreader.app.settings.ReaderTextAlign
import com.xreader.app.settings.spacingPresetOrNull
import com.xreader.app.tts.ReadAloudEngineOption
import com.xreader.app.tts.ReadAloudVoiceOption
import com.xreader.app.tts.NeuralTtsModelCatalog
import com.xreader.app.tts.AudiobookPlaybackUiState
import com.xreader.app.tts.EMPTY_AUDIOBOOK_PLAYBACK_UI_STATE
import com.xreader.app.tts.TtsAccelerationRuntime
import com.xreader.app.tts.canDeleteGeneratedAudiobook
import com.xreader.app.tts.GeneratedAudiobookChapter
import com.xreader.app.tts.playableSegmentCount
import com.xreader.app.tts.playableSegmentFiles
import com.xreader.app.tts.nextChapterStart
import com.xreader.app.tts.previousChapterStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date

@Composable
internal fun SettingsRoute(
    viewModel: SettingsViewModel,
    onBack: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val librarySettings by viewModel.librarySettings.collectAsStateWithLifecycle()
    val readAloudEngines by viewModel.readAloudEngines.collectAsStateWithLifecycle()
    val readAloudVoices by viewModel.readAloudVoices.collectAsStateWithLifecycle()
    val neuralTtsModels by viewModel.neuralTtsModels.collectAsStateWithLifecycle()
    val maintenance by viewModel.maintenance.collectAsStateWithLifecycle()
    var settingsQuery by rememberSaveable { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val backupMimeTypes = remember {
        arrayOf("application/json", "text/json", "text/plain", "application/octet-stream")
    }
    val exportNotesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportAnnotations(uri)
    }
    val importNotesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importAnnotations(uri)
    }
    val exportLibraryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportLibrary(uri)
    }
    val importLibraryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importLibrary(uri)
    }
    val exportFullBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.xreader.backup+zip")
    ) { uri ->
        if (uri != null) viewModel.exportFullBackup(uri)
    }
    val importFullBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFullBackup(uri)
    }
    val maintenanceBusy = maintenance.repairingLibrary ||
        maintenance.exportingLibrary ||
        maintenance.importingLibrary ||
        maintenance.exportingAnnotations ||
        maintenance.importingAnnotations ||
        maintenance.exportingFullBackup ||
        maintenance.importingFullBackup
    LaunchedEffect(maintenance.message) {
        maintenance.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMaintenanceMessage()
        }
    }
    LaunchedEffect(settings.readAloudEngineName) {
        viewModel.refreshReadAloudOptions(settings.readAloudEngineName)
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            item {
                OutlinedTextField(
                    value = settingsQuery,
                    onValueChange = { settingsQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search settings") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = if (settingsQuery.isBlank()) null else ({
                        IconButton(onClick = { settingsQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear settings search")
                        }
                    }),
                )
            }
            if (SettingsSectionDefinition.APPEARANCE.matches(settingsQuery)) item {
                SettingsSection("Appearance") {
                    SettingsChipGroup(
                        title = "Theme",
                        options = ReaderTheme.entries,
                        selected = settings.theme,
                        label = { it.label() },
                        onSelected = viewModel::setTheme
                    )
                }
            }
            if (SettingsSectionDefinition.TYPOGRAPHY.matches(settingsQuery)) item {
                SettingsSection("Typography") {
                    SettingsChipGroup(
                        title = "Spacing preset",
                        options = ReaderSpacingPreset.entries,
                        selected = settings.spacingPresetOrNull(),
                        label = { it.label },
                        onSelected = viewModel::setSpacingPreset
                    )
                    SettingSlider("Font size", settings.fontScale, 0.75f..1.65f, viewModel::setFontScale)
                    SettingSlider("Line height", settings.lineHeight, 1.1f..2.0f, viewModel::setLineHeight)
                    SettingSlider("Margins", settings.marginScale, 0.35f..1.8f, viewModel::setMarginScale)
                    SettingsChipGroup(
                        title = "Font",
                        options = ReaderFontFamily.entries,
                        selected = settings.fontFamily,
                        label = { it.label },
                        onSelected = viewModel::setFontFamily
                    )
                    SettingSlider(
                        "Text weight",
                        settings.fontWeight,
                        MIN_READER_FONT_WEIGHT..MAX_READER_FONT_WEIGHT,
                        viewModel::setFontWeight
                    )
                    SettingsToggleRow(
                        label = "Hyphenation",
                        checked = settings.hyphenation,
                        onCheckedChange = viewModel::setHyphenation
                    )
                    SettingsChipGroup(
                        title = "Alignment",
                        options = ReaderTextAlign.entries,
                        selected = settings.textAlign,
                        label = { it.name.lowercase().replaceFirstChar(Char::titlecase) },
                        onSelected = viewModel::setTextAlign
                    )
                    SettingsToggleRow(
                        label = "Publisher styles",
                        checked = settings.publisherStyles,
                        onCheckedChange = viewModel::setPublisherStyles
                    )
                }
            }
            if (SettingsSectionDefinition.READING.matches(settingsQuery)) item {
                SettingsSection("Reading") {
                    SettingsChipGroup(
                        title = "PDF fit",
                        options = ReaderPdfFit.entries,
                        selected = settings.pdfFit,
                        label = { it.label },
                        onSelected = viewModel::setPdfFit
                    )
                    SettingsChipGroup(
                        title = "PDF layout",
                        options = ReaderPdfScrollAxis.entries,
                        selected = settings.pdfScrollAxis,
                        label = { it.label },
                        onSelected = viewModel::setPdfScrollAxis
                    )
                    SettingsChipGroup(
                        title = "Page direction",
                        options = ReaderPageDirection.entries,
                        selected = settings.pageDirection,
                        label = { it.label },
                        onSelected = viewModel::setPageDirection
                    )
                    SettingsChipGroup(
                        title = "Orientation",
                        options = ReaderOrientation.entries,
                        selected = settings.orientation,
                        label = { it.label },
                        onSelected = viewModel::setOrientation
                    )
                    SettingsToggleRow(
                        label = "Fullscreen reading",
                        checked = settings.fullScreen,
                        onCheckedChange = viewModel::setFullScreen
                    )
                    SettingsToggleRow(
                        label = "Tap zones",
                        checked = settings.tapZonesEnabled,
                        onCheckedChange = viewModel::setTapZonesEnabled
                    )
                    if (settings.tapZonesEnabled) {
                        SettingsChipGroup(
                            title = "Tap zone size",
                            options = ReaderTapZonePreset.entries,
                            selected = settings.tapZonePreset,
                            label = { it.label },
                            onSelected = viewModel::setTapZonePreset
                        )
                    }
                    SettingsToggleRow(
                        label = "Page animations",
                        checked = settings.pageTurnAnimations,
                        onCheckedChange = viewModel::setPageTurnAnimations
                    )
                    SettingsToggleRow(
                        label = "Keep screen awake",
                        checked = settings.keepScreenAwake,
                        onCheckedChange = viewModel::setKeepScreenAwake
                    )
                    SettingsToggleRow(
                        label = "Volume buttons turn pages",
                        checked = settings.volumeKeysTurnPages,
                        onCheckedChange = viewModel::setVolumeKeysTurnPages
                    )
                    SettingSlider("Reader dim", settings.screenDim, 0f..MAX_READER_DIM_AMOUNT, viewModel::setScreenDim)
                    SettingSlider("Read aloud speed", settings.readAloudRate, 0.7f..1.4f, viewModel::setReadAloudRate)
                    SettingsChipGroup(
                        title = "Sleep timer",
                        options = ReadAloudSleepTimer.entries,
                        selected = settings.readAloudSleepTimer,
                        label = { it.label },
                        onSelected = viewModel::setReadAloudSleepTimer
                    )
                    ReadAloudEngineSettings(
                        engines = readAloudEngines,
                        selectedEngineName = settings.readAloudEngineName,
                        onSelected = viewModel::setReadAloudEngineName
                    )
                    ReadAloudVoiceSettings(
                        voices = readAloudVoices,
                        selectedVoiceName = settings.readAloudVoiceName,
                        onSelected = viewModel::setReadAloudVoiceName
                    )
                    NeuralTtsModelSettings(
                        models = neuralTtsModels,
                        selectedModelId = settings.neuralTtsModelId,
                        selectedSpeakerId = settings.neuralTtsSpeakerId,
                        gender = settings.neuralTtsGender,
                        tone = settings.neuralTtsTone,
                        pace = settings.neuralTtsPace,
                        onSelected = viewModel::setNeuralTtsModelId,
                        onSpeakerSelected = viewModel::setNeuralTtsSpeakerId,
                        onGenderSelected = viewModel::setNeuralTtsGender,
                        onToneSelected = viewModel::setNeuralTtsTone,
                        onPaceSelected = viewModel::setNeuralTtsPace,
                        onDownload = viewModel::downloadNeuralTtsModel,
                        onCancelInstall = viewModel::cancelNeuralTtsModelInstall,
                        onDelete = viewModel::deleteNeuralTtsModel,
                        onPreview = viewModel::previewNeuralTtsModel
                    )
                }
            }
            if (SettingsSectionDefinition.LIBRARY.matches(settingsQuery)) item {
                SettingsSection("Library") {
                    SettingsChipGroup(
                        title = "Sort",
                        options = LibrarySort.entries,
                        selected = librarySettings.sort,
                        label = { it.label() },
                        onSelected = viewModel::setLibrarySort
                    )
                    SettingsChipGroup(
                        title = "Density",
                        options = LibraryDensity.entries,
                        selected = librarySettings.density,
                        label = { it.label() },
                        onSelected = viewModel::setLibraryDensity
                    )
                }
            }
            if (SettingsSectionDefinition.MAINTENANCE.matches(settingsQuery)) item {
                SettingsSection("Maintenance") {
                    Button(
                        onClick = viewModel::repairLibrary,
                        enabled = !maintenanceBusy,
                    ) {
                        if (maintenance.repairingLibrary) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Filled.Search, contentDescription = null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (maintenance.repairingLibrary) "Repairing library" else "Repair library")
                    }
                    Text(
                        "Rebuilds search, covers, metadata, series order, and readability for imported books.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("Full backup", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Saves metadata, reading progress, settings, collections, notes, highlights, and bookmarks. Book files and generated audio remain on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { exportFullBackupLauncher.launch("xreader-full-backup.xreader-backup") },
                            enabled = !maintenanceBusy
                        ) {
                            if (maintenance.exportingFullBackup) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Filled.FileDownload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (maintenance.exportingFullBackup) "Exporting backup" else "Export full backup")
                        }
                        Button(
                            onClick = { importFullBackupLauncher.launch(backupMimeTypes) },
                            enabled = !maintenanceBusy
                        ) {
                            if (maintenance.importingFullBackup) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Filled.FileUpload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (maintenance.importingFullBackup) "Importing backup" else "Import full backup")
                        }
                    }
                    Text("Legacy separate backups", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { exportLibraryLauncher.launch("xreader-library-metadata.json") },
                            enabled = !maintenanceBusy
                        ) {
                            if (maintenance.exportingLibrary) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Filled.FileDownload, contentDescription = null)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (maintenance.exportingLibrary) "Exporting library" else "Export library")
                        }
                        Button(
                            onClick = { importLibraryLauncher.launch(backupMimeTypes) },
                            enabled = !maintenanceBusy
                        ) {
                            if (maintenance.importingLibrary) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Filled.FileUpload, contentDescription = null)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (maintenance.importingLibrary) "Importing library" else "Import library")
                        }
                        Button(
                            onClick = { exportNotesLauncher.launch("xreader-notes-bookmarks.json") },
                            enabled = !maintenanceBusy
                        ) {
                            if (maintenance.exportingAnnotations) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Filled.FileDownload, contentDescription = null)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (maintenance.exportingAnnotations) "Exporting notes" else "Export notes")
                        }
                        Button(
                            onClick = { importNotesLauncher.launch(backupMimeTypes) },
                            enabled = !maintenanceBusy
                        ) {
                            if (maintenance.importingAnnotations) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Filled.FileUpload, contentDescription = null)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (maintenance.importingAnnotations) "Importing notes" else "Import notes")
                        }
                    }
                }
            }
            if (settingsQuery.isNotBlank() && !settingsHasMatches(settingsQuery)) {
                item {
                    Text(
                        "No settings match “${settingsQuery.trim()}”.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    }
}

@Composable
private fun <T> SettingsChipGroup(
    title: String,
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelected(option) },
                    label = { Text(label(option)) }
                )
            }
        }
    }
}

@Composable
private fun ReadAloudEngineSettings(
    engines: List<ReadAloudEngineOption>,
    selectedEngineName: String?,
    onSelected: (String?) -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    val selectedEngineAvailable = selectedEngineName == null || engines.any { it.name == selectedEngineName }
    val selectedLabel = selectedEngineLabel(engines, selectedEngineName)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Read aloud engine", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!selectedEngineAvailable) {
                    Text(
                        text = "Unavailable on this device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            TextButton(onClick = { pickerOpen = true }) {
                Text("Choose")
            }
        }
    }

    if (pickerOpen) {
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            title = { Text("Read aloud engine") },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item(key = "default") {
                        ReadAloudVoiceChoiceRow(
                            label = "Device default",
                            selected = selectedEngineName == null,
                            onClick = {
                                onSelected(null)
                                pickerOpen = false
                            }
                        )
                    }
                    items(engines.filter { it.name != null }, key = { it.name ?: it.label }) { engine ->
                        ReadAloudVoiceChoiceRow(
                            label = if (engine.isDefault) "${engine.label} (default)" else engine.label,
                            selected = selectedEngineName == engine.name,
                            onClick = {
                                onSelected(engine.name)
                                pickerOpen = false
                            }
                        )
                    }
                    if (engines.none { it.name != null }) {
                        item {
                            PickerEmptyRow("No additional TTS engines found.")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickerOpen = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun NeuralTtsModelSettings(
    models: List<NeuralTtsModelEntity>,
    selectedModelId: String,
    selectedSpeakerId: Int,
    gender: NeuralTtsGender,
    tone: NeuralTtsTone,
    pace: NeuralTtsPace,
    onSelected: (String) -> Unit,
    onSpeakerSelected: (Int) -> Unit,
    onGenderSelected: (NeuralTtsGender) -> Unit,
    onToneSelected: (NeuralTtsTone) -> Unit,
    onPaceSelected: (NeuralTtsPace) -> Unit,
    onDownload: (String) -> Unit,
    onCancelInstall: (String) -> Unit,
    onDelete: (String) -> Unit,
    onPreview: (String) -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    val spec = NeuralTtsModelCatalog.models.firstOrNull { it.modelId == selectedModelId }
        ?: NeuralTtsModelCatalog.models.first()
    val model = models.firstOrNull { it.modelId == spec.modelId }
    val status = model?.status ?: NeuralTtsModelStatus.NOT_DOWNLOADED
    val downloaded = model?.downloadedBytes ?: 0L
    val total = model?.totalBytes?.takeIf { it > 0L } ?: spec.archiveBytes
    val statusText = neuralTtsStatusText(status, downloaded, total, spec.archiveBytes, model?.error)
    val selectedSpeaker = spec.speaker(selectedSpeakerId)
    val narratorOptions = remember(spec, gender, selectedSpeaker) {
        spec.speakers
            .filter { gender == NeuralTtsGender.ANY || it.gender == gender }
            .let { filtered ->
                if (selectedSpeaker in filtered) filtered else listOf(selectedSpeaker) + filtered
            }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Local neural voice", style = MaterialTheme.typography.titleSmall)
        SettingsChipGroup(
            title = "Voice",
            options = NeuralTtsGender.entries,
            selected = gender,
            label = { it.label },
            onSelected = onGenderSelected
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "${spec.displayName} · ${selectedSpeaker.label}",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = neuralTtsModelDetailText(statusText, spec.voiceDescription),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status == NeuralTtsModelStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            when (status) {
                NeuralTtsModelStatus.DOWNLOADING,
                NeuralTtsModelStatus.EXTRACTING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    TextButton(onClick = { onCancelInstall(spec.modelId) }) { Text("Stop") }
                }
                else -> TextButton(onClick = { pickerOpen = true }) {
                    Text("Manage")
                }
            }
        }
        if (spec.speakers.size > 1) {
            SettingsChipGroup(
                title = "Narrator",
                options = narratorOptions,
                selected = selectedSpeaker,
                label = { "${it.label} (${it.gender.label})" },
                onSelected = { onSpeakerSelected(it.id) }
            )
        }
        SettingsChipGroup(
            title = "Narration style",
            options = NeuralTtsTone.entries,
            selected = tone,
            label = { it.label },
            onSelected = onToneSelected
        )
        SettingsChipGroup(
            title = "Pacing",
            options = NeuralTtsPace.entries,
            selected = pace,
            label = { it.label },
            onSelected = onPaceSelected
        )
    }
    if (pickerOpen) {
        val visibleVoices = NeuralTtsModelCatalog.models
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            title = { Text("Local neural voices") },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visibleVoices, key = { it.modelId }) { voice ->
                        val rowModel = models.firstOrNull { it.modelId == voice.modelId }
                        val rowStatus = rowModel?.status ?: NeuralTtsModelStatus.NOT_DOWNLOADED
                        val rowDownloaded = rowModel?.downloadedBytes ?: 0L
                        val rowTotal = rowModel?.totalBytes?.takeIf { it > 0L } ?: voice.archiveBytes
                        NeuralTtsVoiceRow(
                            spec = voice,
                            status = rowStatus,
                            selected = voice.modelId == selectedModelId,
                            statusText = neuralTtsStatusText(rowStatus, rowDownloaded, rowTotal, voice.archiveBytes, rowModel?.error),
                            onSelected = {
                                onSelected(voice.modelId)
                                pickerOpen = false
                            },
                            onDownload = { onDownload(voice.modelId) },
                            onCancelInstall = { onCancelInstall(voice.modelId) },
                            onDelete = { onDelete(voice.modelId) },
                            onPreview = { onPreview(voice.modelId) }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickerOpen = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun NeuralTtsVoiceRow(
    spec: com.xreader.app.tts.NeuralTtsModelSpec,
    status: NeuralTtsModelStatus,
    selected: Boolean,
    statusText: String,
    onSelected: () -> Unit,
    onDownload: () -> Unit,
    onCancelInstall: () -> Unit,
    onDelete: () -> Unit,
    onPreview: () -> Unit,
) {
    Surface(
        tonalElevation = if (selected) 2.dp else 0.dp,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(spec.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(spec.voiceDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(statusText, style = MaterialTheme.typography.bodySmall, color = if (status == NeuralTtsModelStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (selected) {
                    Text("Selected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when (status) {
                    NeuralTtsModelStatus.DOWNLOADING,
                    NeuralTtsModelStatus.EXTRACTING -> {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        TextButton(onClick = onCancelInstall) { Text("Stop") }
                    }
                    NeuralTtsModelStatus.INSTALLED -> {
                        TextButton(onClick = onPreview) { Text("Preview") }
                        TextButton(onClick = onSelected, enabled = !selected) { Text("Use") }
                        TextButton(onClick = onDownload) { Text("Reinstall") }
                        TextButton(onClick = onDelete) { Text("Delete") }
                    }
                    else -> TextButton(onClick = onDownload) { Text("Download") }
                }
            }
        }
    }
}

internal fun neuralTtsStatusText(
    status: NeuralTtsModelStatus,
    downloaded: Long,
    total: Long,
    archiveBytes: Long,
    error: String?,
): String =
    when (status) {
        NeuralTtsModelStatus.INSTALLED -> "Installed"
        NeuralTtsModelStatus.DOWNLOADING -> "Downloading ${downloaded.compactBytes()} of ${total.compactBytes()}"
        NeuralTtsModelStatus.EXTRACTING -> "Installing ${archiveBytes.compactBytes()} voice"
        NeuralTtsModelStatus.FAILED -> error ?: "Download failed"
        NeuralTtsModelStatus.NOT_DOWNLOADED -> "Not installed • ${archiveBytes.compactBytes()}"
    }

internal fun neuralTtsModelDetailText(statusText: String, voiceDescription: String): String {
    val cleanStatus = statusText.trim()
    val cleanDescription = voiceDescription.trim()
    return when {
        cleanStatus.isBlank() -> cleanDescription
        cleanDescription.isBlank() -> cleanStatus
        else -> "$cleanStatus - $cleanDescription"
    }
}

@Composable
private fun ReadAloudVoiceSettings(
    voices: List<ReadAloudVoiceOption>,
    selectedVoiceName: String?,
    onSelected: (String?) -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    val selectedLabel = selectedVoiceLabel(voices, selectedVoiceName)
    val selectedVoiceAvailable = selectedVoiceName == null || voices.any { it.name == selectedVoiceName }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Read aloud voice", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!selectedVoiceAvailable) {
                    Text(
                        text = "Unavailable on this device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            TextButton(onClick = { pickerOpen = true }) {
                Text("Choose")
            }
        }
    }

    if (pickerOpen) {
        ReadAloudVoicePickerDialog(
            voices = voices,
            selectedVoiceName = selectedVoiceName,
            onDismiss = { pickerOpen = false },
            onSelected = { voiceName ->
                onSelected(voiceName)
                pickerOpen = false
            }
        )
    }
}

private fun selectedEngineLabel(
    engines: List<ReadAloudEngineOption>,
    selected: String?,
): String =
    if (selected == null) {
        "Device default"
    } else {
        engines.firstOrNull { it.name == selected }?.label ?: "Selected engine unavailable"
    }

@Composable
private fun ReadAloudVoicePickerDialog(
    voices: List<ReadAloudVoiceOption>,
    selectedVoiceName: String?,
    onDismiss: () -> Unit,
    onSelected: (String?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedGroupKey by remember(voices, selectedVoiceName) { mutableStateOf<String?>(null) }
    val voiceGroups = remember(voices, selectedVoiceName) {
        buildReadAloudVoiceGroups(voices, selectedVoiceName)
    }
    val selectedGroup = voiceGroups.firstOrNull { it.key == selectedGroupKey }
    val matchingVoices = remember(voices, query) { filterReadAloudVoices(voices, query) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Read aloud voice") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        selectedGroupKey = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search voices") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear voice search")
                            }
                        }
                    }
                )
                if (selectedGroup != null && query.isBlank()) {
                    TextButton(onClick = { selectedGroupKey = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Languages")
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    when {
                        query.isNotBlank() -> {
                            if (matchingVoices.isEmpty()) {
                                item {
                                    PickerEmptyRow("No matching voices.")
                                }
                            } else {
                                items(matchingVoices, key = { "search-${it.name}" }) { voice ->
                                    ReadAloudVoiceChoiceRow(
                                        label = voice.label,
                                        selected = selectedVoiceName == voice.name,
                                        onClick = { onSelected(voice.name) }
                                    )
                                }
                            }
                        }
                        selectedGroup != null -> {
                            items(selectedGroup.voices, key = { "voice-${it.name}" }) { voice ->
                                ReadAloudVoiceChoiceRow(
                                    label = voice.label,
                                    selected = selectedVoiceName == voice.name,
                                    onClick = { onSelected(voice.name) }
                                )
                            }
                        }
                        else -> {
                            item(key = "default") {
                                ReadAloudVoiceChoiceRow(
                                    label = "Device default",
                                    selected = selectedVoiceName == null,
                                    onClick = { onSelected(null) }
                                )
                            }
                            if (voices.isEmpty()) {
                                item {
                                    PickerEmptyRow("No installed voices found.")
                                }
                            } else {
                                items(voiceGroups, key = { "group-${it.key}" }) { group ->
                                    ReadAloudVoiceGroupRow(
                                        group = group,
                                        onClick = { selectedGroupKey = group.key }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun ReadAloudVoiceGroupRow(
    group: ReadAloudVoiceGroup,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = group.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${group.voices.size} ${if (group.voices.size == 1) "voice" else "voices"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        },
        onClick = onClick,
        leadingIcon = {
            if (group.selected) {
                Icon(
                    imageVector = Icons.Filled.Done,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Spacer(Modifier.size(20.dp))
            }
        }
    )
}

@Composable
private fun PickerEmptyRow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun ReadAloudVoiceChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        onClick = onClick,
        leadingIcon = {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Done,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Spacer(Modifier.size(20.dp))
            }
        }
    )
}
