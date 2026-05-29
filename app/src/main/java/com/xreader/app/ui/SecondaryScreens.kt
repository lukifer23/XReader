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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xreader.app.AppContainer
import com.xreader.app.data.AnnotationEntity
import com.xreader.app.data.AnnotationKind
import com.xreader.app.data.ReaderTheme
import com.xreader.app.analytics.ActivityBucketAnalytics
import com.xreader.app.analytics.ActivityBucketGranularity
import com.xreader.app.analytics.AnalyticsRange
import com.xreader.app.analytics.BookAnalytics
import com.xreader.app.analytics.GroupAnalytics
import com.xreader.app.settings.LibraryDensity
import com.xreader.app.settings.LibrarySort
import com.xreader.app.settings.ReadAloudSleepTimer
import com.xreader.app.settings.ReaderFontFamily
import com.xreader.app.settings.ReaderPdfFit
import com.xreader.app.settings.ReaderSpacingPreset
import com.xreader.app.settings.ReaderTapZonePreset
import com.xreader.app.settings.ReaderTextAlign
import com.xreader.app.settings.spacingPresetOrNull
import com.xreader.app.tts.ReadAloudVoiceOption
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun AnalyticsRoute(container: AppContainer, onBack: () -> Unit) {
    val viewModel: AnalyticsViewModel = viewModel(factory = AnalyticsViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportAnalytics(uri)
    }
    val exportCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) viewModel.exportAnalyticsCsv(uri)
    }
    var exportMenuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Reading stats") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { exportMenuOpen = true },
                            enabled = !state.exporting
                        ) {
                            if (state.exporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Filled.FileDownload, contentDescription = "Export reading stats")
                            }
                        }
                        DropdownMenu(
                            expanded = exportMenuOpen,
                            onDismissRequest = { exportMenuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("CSV") },
                                onClick = {
                                    exportMenuOpen = false
                                    exportCsvLauncher.launch("xreader-reading-stats.csv")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("JSON") },
                                onClick = {
                                    exportMenuOpen = false
                                    exportLauncher.launch("xreader-reading-stats.json")
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        val summary = state.summary
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (summary != null) {
                item {
                    AnalyticsRangeSelector(
                        selectedRange = state.selectedRange,
                        onRangeSelected = viewModel::setRange
                    )
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatPill("Books", summary.totalBooks.toString())
                        StatPill("Finished", summary.finishedBooks.toString())
                        StatPill("Sessions", summary.sessions.toString())
                        StatPill("Streak", "${summary.currentStreakDays}d")
                        StatPill("WPM", summary.averageWpm.toString())
                        StatPill("Time", formatDuration(summary.activeMillis))
                        StatPill("Words", summary.wordsRead.toString())
                    }
                }
                item {
                    ReadingActivityChart(
                        buckets = summary.activityBuckets,
                        range = summary.range,
                        bestStreakDays = summary.bestStreakDays
                    )
                }
                if (summary.byAuthor.isNotEmpty()) {
                    item { AnalyticsSectionTitle("Authors") }
                    items(summary.byAuthor, key = { it.label }) { row ->
                        GroupAnalyticsRow(row)
                    }
                }
                if (summary.byGenre.isNotEmpty()) {
                    item { AnalyticsSectionTitle("Genres") }
                    items(summary.byGenre, key = { it.label }) { row ->
                        GroupAnalyticsRow(row)
                    }
                }
                if (summary.byBook.isNotEmpty()) {
                    item { AnalyticsSectionTitle("Books") }
                }
                items(summary.byBook, key = { it.book.id }) { row ->
                    BookAnalyticsRow(row)
                }
                if (summary.sessions == 0) {
                    item {
                        Text("No reading sessions in this range.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsRangeSelector(
    selectedRange: AnalyticsRange,
    onRangeSelected: (AnalyticsRange) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnalyticsRange.entries.forEach { range ->
            FilterChip(
                selected = selectedRange == range,
                onClick = { onRangeSelected(range) },
                label = { Text(range.label) }
            )
        }
    }
}

@Composable
private fun AnalyticsSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun ReadingActivityChart(
    buckets: List<ActivityBucketAnalytics>,
    range: AnalyticsRange,
    bestStreakDays: Int,
) {
    val maxActiveMillis = buckets.maxOfOrNull { it.activeMillis }?.coerceAtLeast(1L) ?: 1L
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(activityTitle(range, buckets), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Best streak ${bestStreakDays}d",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                buckets.forEachIndexed { index, bucket ->
                    ActivityBucketBar(
                        bucket = bucket,
                        maxActiveMillis = maxActiveMillis,
                        showLabel = shouldShowActivityLabel(index, buckets),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityBucketBar(
    bucket: ActivityBucketAnalytics,
    maxActiveMillis: Long,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
) {
    val activeFraction = (bucket.activeMillis.toFloat() / maxActiveMillis.toFloat()).coerceIn(0f, 1f)
    val visibleFraction = if (bucket.activeMillis > 0L) activeFraction.coerceAtLeast(0.08f) else 0.03f
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .fillMaxHeight(visibleFraction),
                shape = RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp),
                color = if (bucket.activeMillis > 0L) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {}
        }
        Text(
            if (showLabel) formatActivityBucket(bucket) else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun GroupAnalyticsRow(row: GroupAnalytics) {
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(row.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${row.sessions} sessions, ${formatDuration(row.activeMillis)}, ${row.wordsRead} words, ${row.averageWpm} WPM",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun BookAnalyticsRow(row: BookAnalytics) {
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(row.book.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${row.sessions} sessions, ${formatDuration(row.activeMillis)}, ${row.wordsRead} words, ${row.averageWpm} WPM",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
internal fun NotesRoute(
    container: AppContainer,
    onBack: () -> Unit,
    openReaderAt: (Long, String) -> Unit,
) {
    val viewModel: NotesViewModel = viewModel(factory = NotesViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val exportMarkdownLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) viewModel.exportMarkdown(uri)
    }
    var editing by remember { mutableStateOf<AnnotationEntity?>(null) }
    var deleteCandidate by remember { mutableStateOf<AnnotationEntity?>(null) }
    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Notes and highlights") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { exportMarkdownLauncher.launch("xreader-notes.md") },
                        enabled = !state.exporting
                    ) {
                        if (state.exporting) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.FileDownload, contentDescription = "Export notes as Markdown")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                singleLine = true,
                label = { Text("Search notes") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotBlank()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear notes search")
                        }
                    }
                }
            )
            FlowRow(
                modifier = Modifier.padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.kind == null,
                    onClick = { viewModel.setKind(null) },
                    label = { Text("All") }
                )
                AnnotationKind.entries.forEach { kind ->
                    FilterChip(
                        selected = state.kind == kind,
                        onClick = { viewModel.setKind(kind) },
                        label = { Text(kind.label()) }
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.notes.isEmpty()) {
                    item {
                        Text(
                            if (state.query.isBlank() && state.kind == null) "No notes yet." else "No matching notes.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(state.notes, key = { it.annotation.id }) { item ->
                    AnnotationRow(
                        item = item,
                        onOpen = { openReaderAt(item.annotation.bookId, item.annotation.locator) },
                        onEdit = { editing = item.annotation },
                        onDelete = { deleteCandidate = item.annotation }
                    )
                }
            }
        }
    }
    editing?.let { annotation ->
        EditAnnotationDialog(
            annotation = annotation,
            onDismiss = { editing = null },
            onSave = { note ->
                viewModel.updateNote(annotation, note)
                editing = null
            }
        )
    }
    deleteCandidate?.let { annotation ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete annotation") },
            text = { Text("Delete this ${annotation.kind.label().lowercase()}?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAnnotation(annotation.id)
                        deleteCandidate = null
                    }
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") } }
        )
    }
}

@Composable
internal fun AnnotationRow(
    item: NoteListItem,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val note = item.annotation
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    modifier = Modifier
                        .padding(top = 4.dp, end = 10.dp)
                        .size(10.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = note.color.toAnnotationColor()
                ) {}
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(note.kind.label(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text(item.book?.title ?: "Unknown book", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit annotation")
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete annotation")
                }
            }
            Text(note.quote, maxLines = 4, overflow = TextOverflow.Ellipsis)
            if (note.note.isNotBlank()) {
                Text(note.note, fontWeight = FontWeight.SemiBold)
            }
            Text(DateFormat.getDateTimeInstance().format(Date(note.updatedAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun EditAnnotationDialog(
    annotation: AnnotationEntity,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var note by remember(annotation.id) { mutableStateOf(annotation.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (annotation.kind == AnnotationKind.NOTE) "Edit note" else "Edit highlight note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(annotation.quote, maxLines = 4, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(note) }) {
                Icon(Icons.Filled.Done, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun SettingsRoute(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val librarySettings by viewModel.librarySettings.collectAsStateWithLifecycle()
    val readAloudVoices by viewModel.readAloudVoices.collectAsStateWithLifecycle()
    val maintenance by viewModel.maintenance.collectAsStateWithLifecycle()
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
    val maintenanceBusy = maintenance.repairingLibrary ||
        maintenance.exportingLibrary ||
        maintenance.importingLibrary ||
        maintenance.exportingAnnotations ||
        maintenance.importingAnnotations
    LaunchedEffect(maintenance.message) {
        maintenance.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMaintenanceMessage()
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            item {
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
            item {
                SettingsSection("Reading") {
                    SettingsChipGroup(
                        title = "PDF fit",
                        options = ReaderPdfFit.entries,
                        selected = settings.pdfFit,
                        label = { it.name.lowercase().replaceFirstChar(Char::titlecase) },
                        onSelected = viewModel::setPdfFit
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
                    SettingSlider("Read aloud speed", settings.readAloudRate, 0.7f..1.4f, viewModel::setReadAloudRate)
                    SettingsChipGroup(
                        title = "Sleep timer",
                        options = ReadAloudSleepTimer.entries,
                        selected = settings.readAloudSleepTimer,
                        label = { it.label },
                        onSelected = viewModel::setReadAloudSleepTimer
                    )
                    ReadAloudVoiceSettings(
                        voices = readAloudVoices,
                        selectedVoiceName = settings.readAloudVoiceName,
                        onSelected = viewModel::setReadAloudVoiceName
                    )
                }
            }
            item {
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
            item {
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
                        Text(if (maintenance.repairingLibrary) "Repairing library" else "Repair covers and search")
                    }
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
private fun ReadAloudVoiceSettings(
    voices: List<ReadAloudVoiceOption>,
    selectedVoiceName: String?,
    onSelected: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Read aloud voice", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedVoiceName == null,
                onClick = { onSelected(null) },
                label = { Text("Device default") }
            )
            voices.forEach { voice ->
                FilterChip(
                    selected = selectedVoiceName == voice.name,
                    onClick = { onSelected(voice.name) },
                    label = {
                        Text(
                            text = voice.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
    }
}

private fun activityTitle(
    range: AnalyticsRange,
    buckets: List<ActivityBucketAnalytics>,
): String =
    when (buckets.firstOrNull()?.granularity) {
        ActivityBucketGranularity.DAY -> when (range) {
            AnalyticsRange.WEEK -> "7-day activity"
            AnalyticsRange.MONTH -> "30-day activity"
            else -> "${range.label} activity"
        }
        ActivityBucketGranularity.WEEK -> "13-week activity"
        ActivityBucketGranularity.MONTH -> "Monthly activity"
        ActivityBucketGranularity.YEAR -> "Yearly activity"
        null -> "${range.label} activity"
    }

private fun shouldShowActivityLabel(
    index: Int,
    buckets: List<ActivityBucketAnalytics>,
): Boolean {
    val lastIndex = buckets.lastIndex
    if (lastIndex <= 13) return true
    return when (buckets.getOrNull(index)?.granularity) {
        ActivityBucketGranularity.DAY -> index == 0 || index == lastIndex || index % 5 == 0
        else -> true
    }
}

private fun formatActivityBucket(bucket: ActivityBucketAnalytics): String {
    val pattern = when (bucket.granularity) {
        ActivityBucketGranularity.DAY -> "E"
        ActivityBucketGranularity.WEEK -> "M/d"
        ActivityBucketGranularity.MONTH -> "MMM"
        ActivityBucketGranularity.YEAR -> "yyyy"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(bucket.startMillis))
}
