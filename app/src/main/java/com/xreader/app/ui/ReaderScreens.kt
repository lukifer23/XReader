@file:OptIn(ExperimentalMaterial3Api::class)

package com.xreader.app.ui

import android.app.Activity
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.xreader.app.data.ReaderTheme
import com.xreader.app.reader.OpenPublication
import com.xreader.app.settings.ReaderFontFamily
import com.xreader.app.settings.ReaderHighlightColor
import com.xreader.app.settings.ReaderPdfFit
import com.xreader.app.settings.ReaderSettings
import com.xreader.app.settings.ReaderSpacingPreset
import com.xreader.app.settings.ReaderTapZonePreset
import com.xreader.app.settings.ReaderTextAlign
import com.xreader.app.settings.ReadAloudSleepTimer
import com.xreader.app.settings.spacingPresetOrNull
import com.xreader.app.tts.ReadAloudState
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
        onActiveSearchResult = viewModel::setActiveSearchResult,
        onLookup = viewModel::lookupWord,
        onSelectedNote = viewModel::openSelectedNote,
        onSelectedHighlight = { locator, quote -> viewModel.addSelectedHighlight(locator, quote) },
        onCloseDictionary = viewModel::closeDictionary,
        onOpenNote = viewModel::openNoteDraft,
        onCloseNote = viewModel::closeNoteDraft,
        onAddNote = viewModel::addNote,
        onUpdateAnnotationNote = viewModel::updateAnnotationNote,
        onBookmark = viewModel::toggleBookmark,
        onDeleteBookmark = viewModel::deleteBookmark,
        onDeleteAnnotation = viewModel::deleteAnnotation,
        onToggleTheme = viewModel::toggleLightDark,
        onToggleFullScreen = viewModel::toggleFullScreen,
        onFontScale = viewModel::setFontScale,
        onLineHeight = viewModel::setLineHeight,
        onMarginScale = viewModel::setMarginScale,
        onSpacingPreset = viewModel::setSpacingPreset,
        onFontFamily = viewModel::setFontFamily,
        onPublisherStyles = viewModel::setPublisherStyles,
        onTapZonesEnabled = viewModel::setTapZonesEnabled,
        onTapZonePreset = viewModel::setTapZonePreset,
        onPageTurnAnimations = viewModel::setPageTurnAnimations,
        onReadAloudRate = viewModel::setReadAloudRate,
        onReadAloudSleepTimer = viewModel::setReadAloudSleepTimer,
        onHighlightColor = viewModel::setHighlightColor,
        onTextAlign = viewModel::setTextAlign,
        onPdfFit = viewModel::setPdfFit,
        onBookAppearanceEnabled = viewModel::setBookAppearanceEnabled,
        onToggleReadAloud = { visibleUnit, visibleLocator ->
            viewModel.toggleReadAloud(visibleUnit, visibleLocator)
        },
        onReadAloudPrevious = viewModel::skipReadAloudPrevious,
        onReadAloudNext = viewModel::skipReadAloudNext,
        onClearReadAloudMessage = viewModel::clearReadAloudMessage
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
    onActiveSearchResult: (Int?) -> Unit,
    onLookup: (String) -> Unit,
    onSelectedNote: (org.readium.r2.shared.publication.Locator, String) -> Unit,
    onSelectedHighlight: (org.readium.r2.shared.publication.Locator, String) -> Unit,
    onCloseDictionary: () -> Unit,
    onOpenNote: (Int, String?) -> Unit,
    onCloseNote: () -> Unit,
    onAddNote: (String) -> Unit,
    onUpdateAnnotationNote: (AnnotationEntity, String, String) -> Unit,
    onBookmark: (Int, String?) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onDeleteAnnotation: (Long) -> Unit,
    onToggleTheme: () -> Unit,
    onToggleFullScreen: () -> Unit,
    onFontScale: (Float) -> Unit,
    onLineHeight: (Float) -> Unit,
    onMarginScale: (Float) -> Unit,
    onSpacingPreset: (ReaderSpacingPreset) -> Unit,
    onFontFamily: (ReaderFontFamily) -> Unit,
    onPublisherStyles: (Boolean) -> Unit,
    onTapZonesEnabled: (Boolean) -> Unit,
    onTapZonePreset: (ReaderTapZonePreset) -> Unit,
    onPageTurnAnimations: (Boolean) -> Unit,
    onReadAloudRate: (Float) -> Unit,
    onReadAloudSleepTimer: (ReadAloudSleepTimer) -> Unit,
    onHighlightColor: (String) -> Unit,
    onTextAlign: (ReaderTextAlign) -> Unit,
    onPdfFit: (ReaderPdfFit) -> Unit,
    onBookAppearanceEnabled: (Boolean) -> Unit,
    onToggleReadAloud: (Int, String?) -> Unit,
    onReadAloudPrevious: () -> Unit,
    onReadAloudNext: () -> Unit,
    onClearReadAloudMessage: () -> Unit,
) {
    val publication = state.publication as? OpenPublication.Readium ?: return
    val units = publication.units
    val pagingController = remember(publication.book.id) {
        ReaderPagingController(
            initialUnit = state.currentUnit,
            initialLocatorJson = state.initialLocatorJson
        )
    }
    var searchOpen by remember(publication.book.id) { mutableStateOf(false) }
    var navigationOpen by remember(publication.book.id) { mutableStateOf(false) }
    var readerSettingsOpen by remember(publication.book.id) { mutableStateOf(false) }
    var editingAnnotation by remember(publication.book.id) { mutableStateOf<AnnotationEntity?>(null) }
    val returnHistory = remember(publication.book.id) { mutableStateListOf<String>() }
    val activity = LocalContext.current.findActivity()
    fun jumpWithReturn(locator: String) {
        pushReaderReturnLocator(
            history = returnHistory,
            visibleLocatorJson = pagingController.currentLocatorJson,
            fallbackUnitLocator = units.getOrNull(pagingController.currentUnit)?.locator,
            targetLocatorJson = locator
        )
        pagingController.goToLocator(locator)
    }
    fun seekWithReturn(progress: Float) {
        val index = ((publication.positions.size - 1).coerceAtLeast(0) * progress).roundToInt()
        val target = publication.positions.getOrNull(index)?.toJSON()?.toString()
        if (target != null) {
            pushReaderReturnLocator(
                history = returnHistory,
                visibleLocatorJson = pagingController.currentLocatorJson,
                fallbackUnitLocator = units.getOrNull(pagingController.currentUnit)?.locator,
                targetLocatorJson = target
            )
        }
        pagingController.goToProgress(progress)
    }
    fun goBackWithinBook(): Boolean {
        val locator = popReaderReturnLocator(returnHistory) ?: return false
        pagingController.goToLocator(locator)
        return true
    }
    fun jumpToSearchResult(index: Int) {
        val result = state.searchResults.getOrNull(index) ?: return
        onActiveSearchResult(index)
        jumpWithReturn(result.locatorJson)
    }

    ReaderSystemBars(
        activity = activity,
        theme = state.settings.theme,
        immersive = state.settings.fullScreen && !state.chromeVisible
    )

    BackHandler {
        when {
            searchOpen -> searchOpen = false
            editingAnnotation != null -> editingAnnotation = null
            navigationOpen -> navigationOpen = false
            readerSettingsOpen -> readerSettingsOpen = false
            state.dictionaryWord != null -> onCloseDictionary()
            state.noteDraftOpen -> onCloseNote()
            state.chromeVisible -> onToggleChrome()
            returnHistory.isNotEmpty() -> goBackWithinBook()
            state.searchResults.isNotEmpty() && state.searchQuery.isNotBlank() -> onClearSearch()
            else -> onBack()
        }
    }

    LaunchedEffect(state.readAloud.currentLocator) {
        if (state.readAloud.playing) {
            state.readAloud.currentLocator?.let { pagingController.goToLocator(it) }
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
                bookmarked = state.bookmarks.bookmarkAtReaderLocation(
                    visibleLocatorJson = pagingController.currentLocatorJson,
                    fallbackUnitLocator = units.getOrNull(pagingController.currentUnit)?.locator
                ) != null,
                onBack = onBack,
                canReturn = returnHistory.isNotEmpty(),
                onReturn = { goBackWithinBook() },
                onContents = { navigationOpen = true },
                onSearch = { searchOpen = true },
                onBookmark = {
                    onBookmark(
                        pagingController.currentUnit,
                        pagingController.currentLocatorJson
                    )
                },
                onNote = {
                    onOpenNote(
                        pagingController.currentUnit,
                        pagingController.currentLocatorJson
                    )
                },
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
                onSeek = ::seekWithReturn,
                theme = state.settings.theme,
                onToggleTheme = onToggleTheme,
                fullScreen = state.settings.fullScreen,
                onToggleFullScreen = onToggleFullScreen,
                readAloud = state.readAloud,
                onToggleReadAloud = {
                    onToggleReadAloud(
                        pagingController.currentUnit,
                        pagingController.currentLocatorJson
                    )
                },
                onReadAloudPrevious = onReadAloudPrevious,
                onReadAloudNext = onReadAloudNext,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(2f)
            )
        }

        val findNavigation = readerSearchNavigationState(
            currentUnit = pagingController.currentUnit,
            results = state.searchResults,
            activeIndex = state.activeSearchResultIndex
        )
        if (!searchOpen && findNavigation != null && state.searchQuery.isNotBlank()) {
            ReaderFindBar(
                query = state.searchQuery.trim(),
                navigation = findNavigation,
                onPrevious = { jumpToSearchResult(findNavigation.previousIndex) },
                onNext = { jumpToSearchResult(findNavigation.nextIndex) },
                onOpenSearch = { searchOpen = true },
                onClose = onClearSearch,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (state.chromeVisible) 86.dp else 12.dp)
                    .zIndex(3f)
            )
        }
    }

    if (searchOpen) {
        ReaderSearchDialog(
            state = state,
            onQuery = onSearchQuery,
            onRun = onRunSearch,
            onDismiss = { searchOpen = false },
            onJump = { index, locator ->
                onActiveSearchResult(index)
                jumpWithReturn(locator)
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
    state.readAloud.message?.let { message ->
        ReadAloudMessageDialog(message = message, onDismiss = onClearReadAloudMessage)
    }
    if (navigationOpen) {
        ReaderNavigationDialog(
            tableOfContents = state.tableOfContents,
            tableOfContentsLoading = state.tableOfContentsLoading,
            bookmarks = state.bookmarks,
            annotations = state.annotations,
            onDismiss = { navigationOpen = false },
            onJump = { locator ->
                jumpWithReturn(locator)
                navigationOpen = false
            },
            onDeleteBookmark = onDeleteBookmark,
            onDeleteAnnotation = onDeleteAnnotation,
            onEditAnnotation = { annotation ->
                editingAnnotation = annotation
                navigationOpen = false
            }
        )
    }
    editingAnnotation?.let { annotation ->
        EditAnnotationDialog(
            annotation = annotation,
            onDismiss = { editingAnnotation = null },
            onSave = { note, color ->
                onUpdateAnnotationNote(annotation, note, color)
                editingAnnotation = null
            }
        )
    }
    if (readerSettingsOpen) {
        ReaderQuickSettingsDialog(
            settings = state.settings,
            bookAppearanceEnabled = state.bookAppearanceEnabled,
            onDismiss = { readerSettingsOpen = false },
            onFontScale = onFontScale,
            onLineHeight = onLineHeight,
            onMarginScale = onMarginScale,
            onSpacingPreset = onSpacingPreset,
            onFontFamily = onFontFamily,
            onPublisherStyles = onPublisherStyles,
            onTapZonesEnabled = onTapZonesEnabled,
            onTapZonePreset = onTapZonePreset,
            onPageTurnAnimations = onPageTurnAnimations,
            onReadAloudRate = onReadAloudRate,
            onReadAloudSleepTimer = onReadAloudSleepTimer,
            onHighlightColor = onHighlightColor,
            onTextAlign = onTextAlign,
            onPdfFit = onPdfFit,
            onBookAppearanceEnabled = onBookAppearanceEnabled
        )
    }
}

internal class ReaderPagingController(
    initialUnit: Int = 0,
    initialLocatorJson: String? = null,
) {
    var currentPage by mutableIntStateOf(initialUnit.coerceAtLeast(0))
    var currentUnit by mutableIntStateOf(initialUnit.coerceAtLeast(0))
    var currentLocatorJson by mutableStateOf(initialLocatorJson)
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
    canReturn: Boolean,
    onReturn: () -> Unit,
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
            if (canReturn) {
                IconButton(onClick = onReturn, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Return to previous reading location")
                }
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
    readAloud: ReadAloudState,
    onToggleReadAloud: () -> Unit,
    onReadAloudPrevious: () -> Unit,
    onReadAloudNext: () -> Unit,
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
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (readAloud.playing) {
                Text(
                    text = readAloudStatusText(readAloud),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeToggleButton(theme = theme, onClick = onToggleTheme)
                FullScreenToggleButton(fullScreen = fullScreen, onClick = onToggleFullScreen)
                if (readAloud.playing) {
                    TooltipIconButton(
                        label = "Previous read-aloud passage",
                        onClick = onReadAloudPrevious,
                        enabled = readAloudCanSkipPrevious(readAloud),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                    }
                }
                ReadAloudButton(readAloud = readAloud, onClick = onToggleReadAloud)
                if (readAloud.playing) {
                    TooltipIconButton(
                        label = "Next read-aloud passage",
                        onClick = onReadAloudNext,
                        enabled = readAloudCanSkipNext(readAloud),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
                if (!readAloud.playing) {
                    IconButton(onClick = onPrevious, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous page")
                    }
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
                if (!readAloud.playing) {
                    IconButton(onClick = onNext, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next page")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadAloudButton(readAloud: ReadAloudState, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        when {
            readAloud.initializing -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            readAloud.playing -> Icon(Icons.Filled.Stop, contentDescription = "Stop read aloud")
            else -> Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Read aloud")
        }
    }
}

@Composable
private fun ReadAloudMessageDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Read aloud") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
internal fun ReaderQuickSettingsDialog(
    settings: ReaderSettings,
    bookAppearanceEnabled: Boolean,
    onDismiss: () -> Unit,
    onFontScale: (Float) -> Unit,
    onLineHeight: (Float) -> Unit,
    onMarginScale: (Float) -> Unit,
    onSpacingPreset: (ReaderSpacingPreset) -> Unit,
    onFontFamily: (ReaderFontFamily) -> Unit,
    onPublisherStyles: (Boolean) -> Unit,
    onTapZonesEnabled: (Boolean) -> Unit,
    onTapZonePreset: (ReaderTapZonePreset) -> Unit,
    onPageTurnAnimations: (Boolean) -> Unit,
    onReadAloudRate: (Float) -> Unit,
    onReadAloudSleepTimer: (ReadAloudSleepTimer) -> Unit,
    onHighlightColor: (String) -> Unit,
    onTextAlign: (ReaderTextAlign) -> Unit,
    onPdfFit: (ReaderPdfFit) -> Unit,
    onBookAppearanceEnabled: (Boolean) -> Unit,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Book-specific appearance", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Switch(checked = bookAppearanceEnabled, onCheckedChange = onBookAppearanceEnabled)
                }
                Text("Highlight color", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReaderHighlightColor.entries.forEach { color ->
                        FilterChip(
                            selected = ReaderHighlightColor.optionFor(settings.highlightColor) == color,
                            onClick = { onHighlightColor(color.hex) },
                            label = { Text(color.label) },
                            leadingIcon = { AnnotationColorSwatch(color.hex) }
                        )
                    }
                }
                Text("Spacing preset", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val selectedPreset = settings.spacingPresetOrNull()
                    ReaderSpacingPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = selectedPreset == preset,
                            onClick = { onSpacingPreset(preset) },
                            label = { Text(preset.label) }
                        )
                    }
                }
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
                    Text("Tap zones", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Switch(checked = settings.tapZonesEnabled, onCheckedChange = onTapZonesEnabled)
                }
                if (settings.tapZonesEnabled) {
                    Text("Tap zone size", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReaderTapZonePreset.entries.forEach { preset ->
                            FilterChip(
                                selected = settings.tapZonePreset == preset,
                                onClick = { onTapZonePreset(preset) },
                                label = { Text(preset.label) }
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Page animations", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Switch(checked = settings.pageTurnAnimations, onCheckedChange = onPageTurnAnimations)
                }
                SettingSlider("Read aloud speed", settings.readAloudRate, 0.7f..1.4f, onReadAloudRate)
                Text("Sleep timer", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReadAloudSleepTimer.entries.forEach { timer ->
                        FilterChip(
                            selected = settings.readAloudSleepTimer == timer,
                            onClick = { onReadAloudSleepTimer(timer) },
                            label = { Text(timer.label) }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
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
