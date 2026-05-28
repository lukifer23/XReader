@file:OptIn(ExperimentalMaterial3Api::class)

package com.xreader.app.ui

import android.app.Activity
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xreader.app.AppContainer
import com.xreader.app.data.AnnotationEntity
import com.xreader.app.data.BookmarkEntity
import com.xreader.app.data.ReaderTheme
import com.xreader.app.reader.OpenPublication
import com.xreader.app.reader.ReaderNavigationItem
import com.xreader.app.settings.ReaderFontFamily
import com.xreader.app.settings.ReaderPdfFit
import com.xreader.app.settings.ReaderSettings
import com.xreader.app.settings.ReaderTextAlign
import kotlin.math.roundToInt

@Composable
internal fun ReaderRoute(
    bookId: Long,
    initialLocatorJson: String?,
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel: ReaderViewModel = viewModel(factory = ReaderViewModel.factory(bookId, initialLocatorJson, container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(Unit) {
        onDispose { viewModel.flushSession() }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.resumeReadingSession()
                Lifecycle.Event.ON_PAUSE -> viewModel.persistReadingState()
                Lifecycle.Event.ON_STOP -> viewModel.pauseReadingSession()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val error = state.error
    if (error != null) {
        ErrorScreen(error, onBack)
        return
    }
    ReaderScreen(
        state = state,
        onBack = onBack,
        onToggleChrome = viewModel::toggleChrome,
        onLocator = viewModel::recordLocator,
        onSearchQuery = viewModel::setSearchQuery,
        onRunSearch = viewModel::runSearch,
        onClearSearch = viewModel::clearSearch,
        onLookup = viewModel::lookupWord,
        onSelectedNote = viewModel::openSelectedNote,
        onSelectedHighlight = { locator, quote -> viewModel.addSelectedHighlight(locator, quote) },
        onCloseDictionary = viewModel::closeDictionary,
        onOpenNote = viewModel::openNoteDraft,
        onCloseNote = viewModel::closeNoteDraft,
        onAddNote = viewModel::addNote,
        onBookmark = viewModel::toggleBookmark,
        onDeleteBookmark = viewModel::deleteBookmark,
        onDeleteAnnotation = viewModel::deleteAnnotation,
        onToggleTheme = viewModel::toggleLightDark,
        onToggleFullScreen = viewModel::toggleFullScreen,
        onFontScale = viewModel::setFontScale,
        onLineHeight = viewModel::setLineHeight,
        onMarginScale = viewModel::setMarginScale,
        onFontFamily = viewModel::setFontFamily,
        onPublisherStyles = viewModel::setPublisherStyles,
        onPageTurnAnimations = viewModel::setPageTurnAnimations,
        onTextAlign = viewModel::setTextAlign,
        onPdfFit = viewModel::setPdfFit
    )
}

@Composable
internal fun ReaderScreen(
    state: ReaderUiState,
    onBack: () -> Unit,
    onToggleChrome: () -> Unit,
    onLocator: (org.readium.r2.shared.publication.Locator) -> Unit,
    onSearchQuery: (String) -> Unit,
    onRunSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onLookup: (String) -> Unit,
    onSelectedNote: (org.readium.r2.shared.publication.Locator, String) -> Unit,
    onSelectedHighlight: (org.readium.r2.shared.publication.Locator, String) -> Unit,
    onCloseDictionary: () -> Unit,
    onOpenNote: () -> Unit,
    onCloseNote: () -> Unit,
    onAddNote: (String) -> Unit,
    onBookmark: () -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onDeleteAnnotation: (Long) -> Unit,
    onToggleTheme: () -> Unit,
    onToggleFullScreen: () -> Unit,
    onFontScale: (Float) -> Unit,
    onLineHeight: (Float) -> Unit,
    onMarginScale: (Float) -> Unit,
    onFontFamily: (ReaderFontFamily) -> Unit,
    onPublisherStyles: (Boolean) -> Unit,
    onPageTurnAnimations: (Boolean) -> Unit,
    onTextAlign: (ReaderTextAlign) -> Unit,
    onPdfFit: (ReaderPdfFit) -> Unit,
) {
    val publication = state.publication as? OpenPublication.Readium ?: return
    val units = publication.units
    val pagingController = remember(publication.book.id) { ReaderPagingController() }
    var searchOpen by remember(publication.book.id) { mutableStateOf(false) }
    var navigationOpen by remember(publication.book.id) { mutableStateOf(false) }
    var readerSettingsOpen by remember(publication.book.id) { mutableStateOf(false) }
    val activity = LocalContext.current.findActivity()

    ReaderSystemBars(
        activity = activity,
        theme = state.settings.theme,
        immersive = state.settings.fullScreen && !state.chromeVisible
    )

    BackHandler {
        when {
            searchOpen -> {
                onClearSearch()
                searchOpen = false
            }
            navigationOpen -> navigationOpen = false
            readerSettingsOpen -> readerSettingsOpen = false
            state.dictionaryWord != null -> onCloseDictionary()
            state.noteDraftOpen -> onCloseNote()
            state.chromeVisible -> onToggleChrome()
            else -> onBack()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ReadiumPublicationView(
            publication = publication,
            initialLocatorJson = state.initialLocatorJson,
            settings = state.settings,
            controller = pagingController,
            onLocator = onLocator,
            onLookup = onLookup,
            onSelectedNote = onSelectedNote,
            onSelectedHighlight = onSelectedHighlight,
            onToggleChrome = onToggleChrome,
            modifier = Modifier.zIndex(0f)
        )

        if (state.chromeVisible) {
            ReaderTopChrome(
                title = publication.book.title,
                progress = state.state?.progress ?: 0.0,
                bookmarked = state.bookmarks.any { it.locator == units.getOrNull(state.currentUnit)?.locator },
                onBack = onBack,
                onContents = { navigationOpen = true },
                onSearch = { searchOpen = true },
                onBookmark = onBookmark,
                onNote = onOpenNote,
                onSettings = { readerSettingsOpen = true },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(2f)
            )
            ReaderBottomBar(
                progress = state.state?.progress ?: 0.0,
                page = pagingController.currentPage,
                pageCount = pagingController.pageCount,
                onPrevious = { pagingController.goToPage(pagingController.currentPage - 1) },
                onNext = { pagingController.goToPage(pagingController.currentPage + 1) },
                onSeek = pagingController.goToProgress,
                theme = state.settings.theme,
                onToggleTheme = onToggleTheme,
                fullScreen = state.settings.fullScreen,
                onToggleFullScreen = onToggleFullScreen,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(2f)
            )
        }
    }

    if (searchOpen) {
        ReaderSearchDialog(
            state = state,
            onQuery = onSearchQuery,
            onRun = onRunSearch,
            onDismiss = {
                onClearSearch()
                searchOpen = false
            },
            onJump = { locator ->
                pagingController.goToLocator(locator)
                searchOpen = false
            }
        )
    }
    if (state.dictionaryWord != null) {
        DictionaryDialog(
            word = state.dictionaryWord,
            entries = state.dictionaryEntries,
            onDismiss = onCloseDictionary
        )
    }
    if (state.noteDraftOpen) {
        NoteDialog(onDismiss = onCloseNote, onSave = onAddNote)
    }
    if (navigationOpen) {
        ReaderNavigationDialog(
            tableOfContents = state.tableOfContents,
            tableOfContentsLoading = state.tableOfContentsLoading,
            bookmarks = state.bookmarks,
            annotations = state.annotations,
            onDismiss = { navigationOpen = false },
            onJump = { locator ->
                pagingController.goToLocator(locator)
                navigationOpen = false
            },
            onDeleteBookmark = onDeleteBookmark,
            onDeleteAnnotation = onDeleteAnnotation
        )
    }
    if (readerSettingsOpen) {
        ReaderQuickSettingsDialog(
            settings = state.settings,
            onDismiss = { readerSettingsOpen = false },
            onFontScale = onFontScale,
            onLineHeight = onLineHeight,
            onMarginScale = onMarginScale,
            onFontFamily = onFontFamily,
            onPublisherStyles = onPublisherStyles,
            onPageTurnAnimations = onPageTurnAnimations,
            onTextAlign = onTextAlign,
            onPdfFit = onPdfFit
        )
    }
}

internal class ReaderPagingController {
    var currentPage by mutableIntStateOf(0)
    var pageCount by mutableIntStateOf(1)
    var goToPage: (Int) -> Unit = {}
    var goToUnit: (Int) -> Unit = {}
    var goToLocator: (String) -> Unit = {}
    var goToProgress: (Float) -> Unit = {}
}

@Composable
internal fun ReaderTopChrome(
    title: String,
    progress: Double,
    bookmarked: Boolean,
    onBack: () -> Unit,
    onContents: () -> Unit,
    onSearch: () -> Unit,
    onBookmark: () -> Unit,
    onNote: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${(progress * 100).roundToInt()}% read",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onContents, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Contents")
            }
            IconButton(onClick = onSearch, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Search, contentDescription = "Search this book")
            }
            IconButton(onClick = onBookmark, modifier = Modifier.size(40.dp)) {
                Icon(if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, contentDescription = "Bookmark")
            }
            IconButton(onClick = onNote, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = "Add note")
            }
            IconButton(onClick = onSettings, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Settings, contentDescription = "Reader settings")
            }
        }
    }
}

@Composable
internal fun ReaderBottomBar(
    progress: Double,
    page: Int,
    pageCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
    theme: ReaderTheme,
    onToggleTheme: () -> Unit,
    fullScreen: Boolean,
    onToggleFullScreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sliderValue by remember(progress) { mutableFloatStateOf(progress.toFloat().coerceIn(0f, 1f)) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        tonalElevation = 3.dp,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeToggleButton(theme = theme, onClick = onToggleTheme)
            FullScreenToggleButton(fullScreen = fullScreen, onClick = onToggleFullScreen)
            IconButton(onClick = onPrevious, modifier = Modifier.size(44.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous page")
            }
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onSeek(sliderValue) },
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${page + 1}/$pageCount",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = onNext, modifier = Modifier.size(44.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next page")
            }
        }
    }
}

@Composable
internal fun ReaderQuickSettingsDialog(
    settings: ReaderSettings,
    onDismiss: () -> Unit,
    onFontScale: (Float) -> Unit,
    onLineHeight: (Float) -> Unit,
    onMarginScale: (Float) -> Unit,
    onFontFamily: (ReaderFontFamily) -> Unit,
    onPublisherStyles: (Boolean) -> Unit,
    onPageTurnAnimations: (Boolean) -> Unit,
    onTextAlign: (ReaderTextAlign) -> Unit,
    onPdfFit: (ReaderPdfFit) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reader settings") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SettingSlider("Font size", settings.fontScale, 0.75f..1.65f, onFontScale)
                SettingSlider("Line height", settings.lineHeight, 1.1f..2.0f, onLineHeight)
                SettingSlider("Margins", settings.marginScale, 0.35f..1.8f, onMarginScale)
                Text("Font", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReaderFontFamily.entries.forEach { family ->
                        FilterChip(
                            selected = settings.fontFamily == family,
                            onClick = { onFontFamily(family) },
                            label = { Text(family.label) }
                        )
                    }
                }
                Text("Alignment", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReaderTextAlign.entries.forEach { alignment ->
                        FilterChip(
                            selected = settings.textAlign == alignment,
                            onClick = { onTextAlign(alignment) },
                            label = { Text(alignment.name.lowercase().replaceFirstChar(Char::titlecase)) }
                        )
                    }
                }
                Text("PDF fit", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReaderPdfFit.entries.forEach { fit ->
                        FilterChip(
                            selected = settings.pdfFit == fit,
                            onClick = { onPdfFit(fit) },
                            label = { Text(fit.name.lowercase().replaceFirstChar(Char::titlecase)) }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Publisher styles", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Switch(checked = settings.publisherStyles, onCheckedChange = onPublisherStyles)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Page animations", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Switch(checked = settings.pageTurnAnimations, onCheckedChange = onPageTurnAnimations)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
internal fun ReaderSearchDialog(
    state: ReaderUiState,
    onQuery: (String) -> Unit,
    onRun: () -> Unit,
    onDismiss: () -> Unit,
    onJump: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = onQuery,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        label = { Text("Search this book") }
                    )
                    IconButton(onClick = onRun) {
                        Icon(Icons.Filled.Search, contentDescription = "Run search")
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    when {
                        state.searchRunning -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        state.searchPerformed && state.searchResults.isEmpty() -> {
                            Text(
                                text = "No matches found.",
                                modifier = Modifier.padding(8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        !state.searchPerformed -> {
                            Text(
                                text = "Enter a word or phrase.",
                                modifier = Modifier.padding(8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> {
                            state.searchResults.forEach { result ->
                                Text(
                                    text = "${result.title}: ${result.snippet}",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onJump(result.locatorJson) }
                                        .padding(8.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
internal fun DictionaryDialog(
    word: String,
    entries: List<com.xreader.app.data.DictionaryEntryEntity>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(word) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entries.isEmpty()) {
                    Text("No offline definition found.")
                } else {
                    entries.forEach { entry ->
                        Column {
                            Text(entry.partOfSpeech, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            Text(entry.definition)
                            if (entry.synonyms.isNotBlank()) {
                                Text(entry.synonyms, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = {
            if (entries.isEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { context.openDictionarySearch(word) }) {
                        Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Web")
                    }
                    TextButton(onClick = { context.shareDictionaryWord(word) }) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Share")
                    }
                }
            }
        }
    )
}

@Composable
internal fun NoteDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add note") },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onSave(note) }, enabled = note.isNotBlank()) {
                Icon(Icons.Filled.Done, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun ReaderNavigationDialog(
    tableOfContents: List<ReaderNavigationItem>,
    tableOfContentsLoading: Boolean,
    bookmarks: List<BookmarkEntity>,
    annotations: List<AnnotationEntity>,
    onDismiss: () -> Unit,
    onJump: (String) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onDeleteAnnotation: (Long) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Navigate") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (tableOfContentsLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (tableOfContents.isEmpty()) {
                    Text("No table of contents found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    tableOfContents.forEach { item ->
                        Text(
                            text = item.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onJump(item.locatorJson) }
                                .padding(start = (item.level * 14).dp, top = 8.dp, bottom = 8.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (bookmarks.isNotEmpty()) {
                    Text(
                        "Bookmarks",
                        modifier = Modifier.padding(top = 14.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    bookmarks.forEach { bookmark ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onJump(bookmark.locator) }
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(bookmark.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${(bookmark.progress * 100).roundToInt()}% read",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onDeleteBookmark(bookmark.id) }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete bookmark")
                            }
                        }
                    }
                }
                if (annotations.isNotEmpty()) {
                    Text(
                        "Notes and highlights",
                        modifier = Modifier.padding(top = 14.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    annotations.forEach { annotation ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(10.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = annotation.color.toAnnotationColor()
                            ) {}
                            Spacer(Modifier.width(10.dp))
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onJump(annotation.locator) }
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(annotation.kind.label(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                Text(annotation.quote, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (annotation.note.isNotBlank()) {
                                    Text(annotation.note, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(onClick = { onDeleteAnnotation(annotation.id) }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete annotation")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
