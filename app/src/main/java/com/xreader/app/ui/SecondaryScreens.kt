@file:OptIn(ExperimentalMaterial3Api::class)

package com.xreader.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xreader.app.AppContainer
import com.xreader.app.data.AnnotationEntity
import com.xreader.app.data.AnnotationKind
import com.xreader.app.data.ReaderTheme
import com.xreader.app.settings.ReaderFontFamily
import com.xreader.app.settings.ReaderPdfFit
import com.xreader.app.settings.ReaderTextAlign
import java.text.DateFormat
import java.util.Date

@Composable
internal fun AnalyticsRoute(container: AppContainer, onBack: () -> Unit) {
    val viewModel: AnalyticsViewModel = viewModel(factory = AnalyticsViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reading stats") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatPill("Books", summary.totalBooks.toString())
                        StatPill("Finished", summary.finishedBooks.toString())
                        StatPill("Sessions", summary.sessions.toString())
                        StatPill("WPM", summary.averageWpm.toString())
                        StatPill("Time", formatDuration(summary.activeMillis))
                        StatPill("Words", summary.wordsRead.toString())
                    }
                }
                items(summary.byBook, key = { it.book.id }) { row ->
                    Card(shape = RoundedCornerShape(8.dp)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(row.book.title, style = MaterialTheme.typography.titleMedium)
                            Text("${row.sessions} sessions, ${formatDuration(row.activeMillis)}, ${row.wordsRead} words, ${row.averageWpm} WPM")
                        }
                    }
                }
            }
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
    var editing by remember { mutableStateOf<AnnotationEntity?>(null) }
    var deleteCandidate by remember { mutableStateOf<AnnotationEntity?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notes and highlights") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
    val maintenance by viewModel.maintenance.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
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
                title = { Text("Reader settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderTheme.entries.forEach { theme ->
                    FilterChip(
                        selected = settings.theme == theme,
                        onClick = { viewModel.setTheme(theme) },
                        label = { Text(theme.label()) }
                    )
                }
            }
            SettingSlider("Font size", settings.fontScale, 0.75f..1.65f, viewModel::setFontScale)
            SettingSlider("Line height", settings.lineHeight, 1.1f..2.0f, viewModel::setLineHeight)
            SettingSlider("Margins", settings.marginScale, 0.35f..1.8f, viewModel::setMarginScale)
            Text("Font", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderFontFamily.entries.forEach { family ->
                    FilterChip(
                        selected = settings.fontFamily == family,
                        onClick = { viewModel.setFontFamily(family) },
                        label = { Text(family.label) }
                    )
                }
            }
            Text("Alignment", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderTextAlign.entries.forEach { alignment ->
                    FilterChip(
                        selected = settings.textAlign == alignment,
                        onClick = { viewModel.setTextAlign(alignment) },
                        label = { Text(alignment.name.lowercase().replaceFirstChar(Char::titlecase)) }
                    )
                }
            }
            Text("PDF fit", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderPdfFit.entries.forEach { fit ->
                    FilterChip(
                        selected = settings.pdfFit == fit,
                        onClick = { viewModel.setPdfFit(fit) },
                        label = { Text(fit.name.lowercase().replaceFirstChar(Char::titlecase)) }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Fullscreen reading", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = settings.fullScreen, onCheckedChange = viewModel::setFullScreen)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Publisher styles", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = settings.publisherStyles, onCheckedChange = viewModel::setPublisherStyles)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tap zones", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = settings.tapZonesEnabled, onCheckedChange = viewModel::setTapZonesEnabled)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Page animations", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = settings.pageTurnAnimations, onCheckedChange = viewModel::setPageTurnAnimations)
            }
            Text("Library maintenance", style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = viewModel::repairLibrary,
                enabled = !maintenance.repairingLibrary,
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
        }
    }
}
