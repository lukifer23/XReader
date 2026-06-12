@file:OptIn(ExperimentalMaterial3Api::class)

package com.xreader.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewCompact
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xreader.app.AppContainer
import com.xreader.app.data.BookAudioEntity
import com.xreader.app.data.BookAudioStatus
import com.xreader.app.data.BookEntity
import com.xreader.app.data.LibrarySearchRow
import com.xreader.app.data.NeuralTtsModelEntity
import com.xreader.app.data.NeuralTtsModelStatus
import com.xreader.app.data.ReaderTheme
import com.xreader.app.importer.SupportedBookTypes
import com.xreader.app.repository.bookExportFileName
import com.xreader.app.repository.bookExportMimeType
import com.xreader.app.settings.LibraryDensity
import com.xreader.app.settings.LibrarySort
import com.xreader.app.settings.NeuralTtsGender
import com.xreader.app.settings.NeuralTtsPace
import com.xreader.app.settings.NeuralTtsTone
import com.xreader.app.settings.ReaderSettings
import com.xreader.app.tts.AudiobookPlaybackUiState
import com.xreader.app.tts.NeuralTtsModelCatalog
import com.xreader.app.tts.NeuralTtsModelSpec
import com.xreader.app.tts.generationEtaLabel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
internal fun LibraryRoute(
    container: AppContainer,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container)),
    openReaderAt: (Long, String?) -> Unit,
    openLibrary: () -> Unit,
    openAnalytics: () -> Unit,
    openNotes: () -> Unit,
    openSettings: () -> Unit,
    currentTheme: ReaderTheme,
    onToggleTheme: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val readerSettings by viewModel.readerSettings.collectAsStateWithLifecycle()
    val neuralTtsModels by viewModel.neuralTtsModels.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var importMenuOpen by remember { mutableStateOf(false) }
    var importDialogOpen by remember { mutableStateOf(false) }
    var opdsDialogOpen by remember { mutableStateOf(false) }
    var exportTarget by remember { mutableStateOf<BookEntity?>(null) }
    var audiobookTarget by remember { mutableStateOf<BookEntity?>(null) }
    val supportedBookMimeTypes = remember { SupportedBookTypes.pickerMimeTypes }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.importFiles(uris)
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.importFolder(uri)
        }
    }
    val epubExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/epub+zip")) { uri ->
        val book = exportTarget
        exportTarget = null
        if (uri != null && book != null) viewModel.exportBook(book, uri)
    }
    val pdfExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val book = exportTarget
        exportTarget = null
        if (uri != null && book != null) viewModel.exportBook(book, uri)
    }
    val genericExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val book = exportTarget
        exportTarget = null
        if (uri != null && book != null) viewModel.exportBook(book, uri)
    }
    var audiobookExportTarget by remember { mutableStateOf<AudiobookExportTarget?>(null) }
    val audiobookExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val target = audiobookExportTarget
        audiobookExportTarget = null
        if (uri != null && target != null) {
            viewModel.exportAudiobookAudio(target.book, target.audio, uri)
        }
    }
    val openFileImportPicker = {
        if (!state.importing) fileLauncher.launch(supportedBookMimeTypes)
    }
    val openFolderImportPicker = {
        if (!state.importing) folderLauncher.launch(null)
    }
    fun runImportAction(action: LibraryImportAction) {
        importMenuOpen = false
        importDialogOpen = false
        when (action) {
            LibraryImportAction.FILES -> openFileImportPicker()
            LibraryImportAction.FOLDER -> openFolderImportPicker()
            LibraryImportAction.CATALOG -> opdsDialogOpen = true
        }
    }
    fun exportBook(book: BookEntity) {
        exportTarget = book
        val fileName = bookExportFileName(book)
        when (bookExportMimeType(book)) {
            "application/epub+zip" -> epubExportLauncher.launch(fileName)
            "application/pdf" -> pdfExportLauncher.launch(fileName)
            else -> genericExportLauncher.launch(fileName)
        }
    }
    fun exportAudiobookAudio(book: BookEntity, audio: BookAudioEntity) {
        audiobookExportTarget = AudiobookExportTarget(book = book, audio = audio)
        audiobookExportLauncher.launch("${book.title.fileSafeName()} ${audio.fileSafeProfileName()} audiobook.zip")
    }

    LaunchedEffect(state.message?.id) {
        val message = state.message ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message.text,
            actionLabel = message.actionLabel,
            withDismissAction = message.actionLabel != null,
            duration = SnackbarDuration.Long
        )
        viewModel.clearMessage()
        when {
            result == SnackbarResult.ActionPerformed && message.openBookId != null ->
                openReaderAt(message.openBookId, null)
            result == SnackbarResult.ActionPerformed && message.undoRemoveBookId != null ->
                viewModel.undoPendingBookRemoval(message.undoRemoveBookId)
            message.undoRemoveBookId != null ->
                viewModel.finalizePendingBookRemoval(message.undoRemoveBookId)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("XReader", fontWeight = FontWeight.SemiBold) },
                actions = {
                    Box {
                        TooltipIconButton(
                            label = if (state.importing) "Importing books" else "Import books",
                            onClick = { importMenuOpen = true },
                            enabled = !state.importing,
                            modifier = Modifier.size(44.dp)
                        ) {
                            if (state.importing) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Add, contentDescription = null)
                            }
                        }
                        DropdownMenu(expanded = importMenuOpen, onDismissRequest = { importMenuOpen = false }) {
                            LibraryImportActionMenuItems(
                                enabled = !state.importing,
                                onAction = ::runImportAction
                            )
                        }
                    }
                    ThemeToggleButton(theme = currentTheme, onClick = onToggleTheme)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            AppBottomBar(
                selectedTab = AppTab.LIBRARY,
                openLibrary = openLibrary,
                openAnalytics = openAnalytics,
                openNotes = openNotes,
                openSettings = openSettings
            )
        }
    ) { padding ->
        LibraryScreen(
            state = state,
            onQuery = viewModel::setQuery,
            onSearch = viewModel::searchLibrary,
            onGroup = viewModel::setGroup,
            onSort = viewModel::setSort,
            onToggleDensity = viewModel::toggleDensity,
            onImport = { importDialogOpen = true },
            onOpenBook = { openReaderAt(it, null) },
            onOpenSearchResult = openReaderAt,
            onToggleFavorite = viewModel::toggleFavorite,
            onSetFinished = viewModel::setFinished,
            onAddToCollection = viewModel::addToCollection,
            onRemoveFromCollection = viewModel::removeFromCollection,
            onShowAll = {
                viewModel.setQuery("")
                viewModel.setGroup(LibraryGroup.BOOKS)
            },
            onUpdateMetadata = viewModel::updateMetadata,
            onReplaceCover = viewModel::replaceCover,
            onExportBook = ::exportBook,
            onRefreshBookHealth = viewModel::refreshBookHealth,
            onRepairBook = viewModel::repairBook,
            onGenerateAudiobook = { audiobookTarget = it },
            onDeleteBook = viewModel::deleteBook,
            modifier = Modifier.padding(padding)
        )
    }
    if (importDialogOpen) {
        LibraryImportDialog(
            importing = state.importing,
            onDismiss = { importDialogOpen = false },
            onAction = ::runImportAction
        )
    }
    if (opdsDialogOpen) {
        OpdsCatalogDialog(
            state = state.opdsCatalog,
            busy = state.importing,
            onUrlChange = viewModel::setOpdsCatalogUrl,
            onLoad = viewModel::loadOpdsCatalog,
            onOpenLink = viewModel::openOpdsCatalogLink,
            onImportEntry = viewModel::importOpdsEntry,
            onDismiss = { opdsDialogOpen = false }
        )
    }
    audiobookTarget?.let { book ->
        val bookAudio by remember(book.id) {
            viewModel.observeBookAudio(book.id)
        }.collectAsStateWithLifecycle(emptyList())
        BookAudiobookDialog(
            book = book,
            settings = readerSettings,
            models = neuralTtsModels,
            bookAudio = bookAudio,
            scan = state.audiobookScans[book.id],
            playback = state.audiobookPlayback,
            onDismiss = { audiobookTarget = null },
            onScan = { viewModel.scanAudiobook(book) },
            onGenerate = {
                viewModel.generateAudiobook(book)
            },
            onCancelGeneration = { viewModel.cancelAudiobookGeneration(book) },
            onExportAudio = { audio -> exportAudiobookAudio(book, audio) },
            onDeleteAudio = { audio -> viewModel.deleteAudiobookAudio(book, audio) },
            onDeleteAllAudio = { viewModel.deleteAllAudiobookAudio(book) },
            onPlayAudio = { audio -> viewModel.playAudiobookAudio(book, audio) },
            onPauseAudio = { audio -> viewModel.pauseAudiobookPlayback(audio) },
            onStopAudio = { viewModel.stopAudiobookPlayback() },
            onSelected = viewModel::setNeuralTtsModelId,
            onSpeakerSelected = viewModel::setNeuralTtsSpeakerId,
            onGenderSelected = viewModel::setNeuralTtsGender,
            onToneSelected = viewModel::setNeuralTtsTone,
            onPaceSelected = viewModel::setNeuralTtsPace,
            onDownload = viewModel::downloadNeuralTtsModel,
            onDeleteModel = viewModel::deleteNeuralTtsModel,
            onPreview = viewModel::previewNeuralTtsModel
        )
    }
}

private data class AudiobookExportTarget(
    val book: BookEntity,
    val audio: BookAudioEntity,
)

internal enum class LibraryImportAction {
    FILES,
    FOLDER,
    CATALOG,
}

@Composable
private fun LibraryImportActionMenuItems(
    enabled: Boolean,
    onAction: (LibraryImportAction) -> Unit,
) {
    DropdownMenuItem(
        text = { Text("Import files") },
        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
        enabled = enabled,
        onClick = { onAction(LibraryImportAction.FILES) }
    )
    DropdownMenuItem(
        text = { Text("Import folder") },
        leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
        enabled = enabled,
        onClick = { onAction(LibraryImportAction.FOLDER) }
    )
    DropdownMenuItem(
        text = { Text("Catalog URL") },
        leadingIcon = { Icon(Icons.Filled.Public, contentDescription = null) },
        enabled = enabled,
        onClick = { onAction(LibraryImportAction.CATALOG) }
    )
}

@Composable
internal fun LibraryImportDialog(
    importing: Boolean,
    onDismiss: () -> Unit,
    onAction: (LibraryImportAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import books") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (importing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Importing books")
                    }
                } else {
                    LibraryImportActionButton(
                        label = "Import files",
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        onClick = { onAction(LibraryImportAction.FILES) }
                    )
                    LibraryImportActionButton(
                        label = "Import folder",
                        icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                        onClick = { onAction(LibraryImportAction.FOLDER) }
                    )
                    LibraryImportActionButton(
                        label = "Catalog URL",
                        icon = { Icon(Icons.Filled.Public, contentDescription = null) },
                        onClick = { onAction(LibraryImportAction.CATALOG) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun LibraryImportActionButton(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            icon()
            Text(label)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LibraryScreen(
    state: LibraryUiState,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onGroup: (LibraryGroup) -> Unit,
    onSort: (LibrarySort) -> Unit,
    onToggleDensity: () -> Unit,
    onImport: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onOpenSearchResult: (Long, String?) -> Unit,
    onToggleFavorite: (BookListItem) -> Unit,
    onSetFinished: (BookListItem, Boolean) -> Unit,
    onAddToCollection: (BookListItem, String) -> Unit,
    onRemoveFromCollection: (BookListItem, CollectionUiItem) -> Unit,
    onShowAll: () -> Unit,
    onUpdateMetadata: (BookEntity, String, String, Int?, String?, String?, Double?, Boolean) -> Unit,
    onReplaceCover: (BookEntity, Uri) -> Unit,
    onExportBook: (BookEntity) -> Unit,
    onRefreshBookHealth: (Long) -> Unit,
    onRepairBook: (BookEntity) -> Unit,
    onGenerateAudiobook: (BookEntity) -> Unit,
    onDeleteBook: (BookEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<BookEntity?>(null) }
    var deleteCandidate by remember { mutableStateOf<BookEntity?>(null) }
    var coverTarget by remember { mutableStateOf<BookEntity?>(null) }
    var collectionsTarget by remember { mutableStateOf<BookListItem?>(null) }
    var searchExpanded by remember { mutableStateOf(false) }
    val supportedCoverMimeTypes = remember {
        arrayOf("image/jpeg", "image/png", "image/webp", "image/*")
    }
    val coverLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val book = coverTarget
        coverTarget = null
        if (uri != null && book != null) {
            onReplaceCover(book, uri)
            editing = null
        }
    }
    val continueItem = remember(state.books, state.group) {
        if (state.group == LibraryGroup.BOOKS) {
            state.books
                .filter { it.isLibraryInProgress() }
                .maxByOrNull { it.state?.lastReadAt ?: it.book.lastOpenedAt ?: it.book.importedAt }
        } else {
            null
        }
    }
    val nextSeriesItem = remember(state.allBooks, state.group, state.query, continueItem) {
        if (state.group == LibraryGroup.BOOKS && state.query.isBlank()) {
            recommendNextSeriesBook(state.allBooks)
                ?.takeUnless { it.next.book.id == continueItem?.book?.id }
        } else {
            null
        }
    }
    val displayBooks = remember(state.books, state.group, continueItem) {
        if (state.group == LibraryGroup.BOOKS && continueItem != null) {
            state.books.filterNot { it.book.id == continueItem.book.id }
        } else {
            state.books
        }
    }

    LaunchedEffect(state.query, state.librarySearchResults) {
        if (state.query.isNotBlank() || state.librarySearchResults.isNotEmpty()) {
            searchExpanded = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        if (searchExpanded) {
            LibrarySearchField(
                query = state.query,
                onQuery = onQuery,
                onSearch = onSearch,
                onCollapse = {
                    if (state.query.isNotBlank()) onQuery("")
                    searchExpanded = false
                }
            )
        } else {
            LibraryActionRow(
                state = state,
                onToggleSearch = { searchExpanded = true },
                onGroup = onGroup,
                onSort = onSort,
                onToggleDensity = onToggleDensity
            )
        }
        if (state.librarySearchResults.isNotEmpty()) {
            SearchResultsStrip(
                results = state.librarySearchResults,
                query = state.query,
                onOpenResult = onOpenSearchResult
            )
        }
        if (state.books.isEmpty()) {
            LibraryEmptyState(
                copy = state.emptyStateCopy(),
                onImport = onImport,
                onShowAll = {
                    searchExpanded = false
                    onShowAll()
                }
            )
        } else {
            val grouped = groupBooks(state.group, displayBooks, state.sort)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(if (state.density == LibraryDensity.COMPACT) 8.dp else 10.dp)
            ) {
                continueItem?.let { current ->
                    item {
                        ContinueReadingCard(
                            item = current,
                            onOpen = { onOpenBook(current.book.id) }
                        )
                    }
                }
                nextSeriesItem?.let { recommendation ->
                    item {
                        SeriesNextCard(
                            recommendation = recommendation,
                            onOpen = { onOpenBook(recommendation.next.book.id) }
                        )
                    }
                }
                grouped.forEach { (header, items) ->
                    if (header.isNotBlank()) {
                        stickyHeader {
                            Text(
                                text = header,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(vertical = 8.dp),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    items(
                        items = items,
                        key = { it.book.id }
                    ) { item ->
                        BookRow(
                            item = item,
                            density = state.density,
                            onOpen = { onOpenBook(item.book.id) },
                            onFavorite = { onToggleFavorite(item) },
                            onSetFinished = { finished -> onSetFinished(item, finished) },
                            onCollections = { collectionsTarget = item },
                            onEdit = { editing = item.book },
                            onExport = { onExportBook(item.book) },
                            onGenerateAudiobook = { onGenerateAudiobook(item.book) },
                            onDelete = { deleteCandidate = item.book }
                        )
                    }
                }
            }
        }
    }
    editing?.let { book ->
        LaunchedEffect(book.id) {
            onRefreshBookHealth(book.id)
        }
        BookMetadataDialog(
            book = book,
            health = state.bookHealth[book.id],
            repairing = book.id in state.repairingBookIds,
            authorOptions = state.authorOptions,
            genreOptions = state.genreOptions,
            seriesOptions = state.seriesOptions,
            onDismiss = { editing = null },
            onRefreshHealth = { onRefreshBookHealth(book.id) },
            onRepairBook = { onRepairBook(book) },
            onReplaceCover = {
                coverTarget = book
                coverLauncher.launch(supportedCoverMimeTypes)
            },
            onSave = { title, author, year, genre, series, index, applyToSeries ->
                onUpdateMetadata(book, title, author, year, genre, series, index, applyToSeries)
                editing = null
            }
        )
    }
    collectionsTarget?.let { target ->
        val item = state.allBooks.firstOrNull { it.book.id == target.book.id } ?: target
        BookCollectionsDialog(
            item = item,
            allCollections = state.collections,
            onDismiss = { collectionsTarget = null },
            onAdd = { name -> onAddToCollection(item, name) },
            onRemove = { collection -> onRemoveFromCollection(item, collection) }
        )
    }
    deleteCandidate?.let { book ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Remove book") },
            text = { Text("Remove \"${book.title}\" from this device?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteBook(book)
                        deleteCandidate = null
                    }
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Remove")
                }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") } }
        )
    }
}

@Composable
internal fun LibraryActionRow(
    state: LibraryUiState,
    onToggleSearch: () -> Unit,
    onGroup: (LibraryGroup) -> Unit,
    onSort: (LibrarySort) -> Unit,
    onToggleDensity: () -> Unit,
) {
    var groupMenuOpen by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    val bookCount = state.books.size
    val inProgress = state.books.count { it.isLibraryInProgress() }
    val finished = state.books.count { it.isLibraryFinished() }
    val countText = when (bookCount) {
        0 -> "No books"
        1 -> "1 book"
        else -> "$bookCount books"
    }
    val statusText = listOfNotNull(
        countText,
        if (inProgress > 0) "$inProgress reading" else null,
        if (finished > 0) "$finished finished" else null,
        state.sort.label()
    ).joinToString(" • ")
    val densityLabel =
        if (state.density == LibraryDensity.COMPACT) "Use comfortable layout" else "Use compact layout"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box {
                TextButton(
                    onClick = { groupMenuOpen = true },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                ) {
                    Text(
                        state.group.label(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = groupMenuOpen, onDismissRequest = { groupMenuOpen = false }) {
                    LibraryGroup.entries.forEach { group ->
                        DropdownMenuItem(
                            text = { Text(group.label()) },
                            leadingIcon = {
                                if (group == state.group) {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                }
                            },
                            onClick = {
                                groupMenuOpen = false
                                onGroup(group)
                            }
                        )
                    }
                }
            }
            Text(
                statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TooltipIconButton(
            label = "Search library",
            onClick = onToggleSearch,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(Icons.Filled.Search, contentDescription = null)
        }
        Box {
            TooltipIconButton(
                label = "Sort library",
                onClick = { sortMenuOpen = true },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null)
            }
            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                LibrarySort.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(sort.label()) },
                        leadingIcon = {
                            if (sort == state.sort) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                            }
                        },
                        onClick = {
                            sortMenuOpen = false
                            onSort(sort)
                        }
                    )
                }
            }
        }
        TooltipIconButton(
            label = densityLabel,
            onClick = onToggleDensity,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                if (state.density == LibraryDensity.COMPACT) Icons.Filled.ViewAgenda else Icons.Filled.ViewCompact,
                contentDescription = null
            )
        }
    }
}

@Composable
internal fun LibrarySearchField(
    query: String,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onCollapse: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 4.dp),
        singleLine = true,
        label = { Text("Search library") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) onSearch() }),
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotBlank()) {
                    TooltipIconButton(
                        label = "Search inside books",
                        onClick = onSearch,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    }
                }
                TooltipIconButton(
                    label = if (query.isBlank()) "Hide search" else "Clear search",
                    onClick = onCollapse,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                }
            }
        }
    )
}

@Composable
internal fun ContinueReadingCard(
    item: BookListItem,
    onOpen: () -> Unit,
) {
    val progress = item.displayLibraryProgress()
    val wpm = item.state?.estimatedWpm?.takeIf { it > 0 }
    val eta = readingEtaLabel(item.book, item.state)
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BookCoverTile(item.book, width = 48.dp, height = 68.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Continue reading", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(item.book.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    item.book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                LinearProgressIndicator(
                    progress = { progress.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    listOfNotNull("${(progress * 100).roundToInt()}% read", eta, wpm?.let { "$it WPM" }).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SeriesNextCard(
    recommendation: SeriesNextRecommendation,
    onOpen: () -> Unit,
) {
    val item = recommendation.next
    val progress = item.displayLibraryProgress()
    val position = item.book.seriesIndex?.let { index ->
        val wholeNumber = index % 1.0 == 0.0
        if (wholeNumber) "Book ${index.toInt()}" else "Book $index"
    }
    val readingState = when {
        item.isLibraryInProgress() -> "${(progress * 100).roundToInt()}% read"
        item.isLibraryFinished() -> "Finished"
        else -> "Unread"
    }
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BookCoverTile(item.book, width = 48.dp, height = 68.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Up next in ${recommendation.series}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    item.book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    item.book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    position?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(readingState, style = MaterialTheme.typography.bodySmall)
                    Text(bookFormatLabel(item.book), style = MaterialTheme.typography.bodySmall)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal data class LibraryEmptyStateCopy(
    val title: String,
    val body: String,
    val primaryAction: String,
    val importsBooks: Boolean,
)

internal fun LibraryUiState.emptyStateCopy(): LibraryEmptyStateCopy =
    when {
        totalBookCount == 0 -> LibraryEmptyStateCopy(
            title = "Build your library",
            body = "Import books and documents from this device.",
            primaryAction = "Import books",
            importsBooks = true
        )
        query.isNotBlank() && matchedBookCount == 0 -> LibraryEmptyStateCopy(
            title = "No matching books",
            body = "No titles, authors, series, or genres match \"${query.trim().take(48)}\".",
            primaryAction = "Show all",
            importsBooks = false
        )
        query.isNotBlank() -> LibraryEmptyStateCopy(
            title = "No matches in ${group.label()}",
            body = "Clear search and filters to see the rest of your library.",
            primaryAction = "Show all",
            importsBooks = false
        )
        group == LibraryGroup.FAVORITES -> LibraryEmptyStateCopy(
            title = "No favorites yet",
            body = "Favorites will appear here.",
            primaryAction = "Show all",
            importsBooks = false
        )
        group == LibraryGroup.IN_PROGRESS -> LibraryEmptyStateCopy(
            title = "Nothing in progress",
            body = "Books you start reading will appear here.",
            primaryAction = "Show all",
            importsBooks = false
        )
        group == LibraryGroup.FINISHED -> LibraryEmptyStateCopy(
            title = "No finished books",
            body = "Finished books will appear here.",
            primaryAction = "Show all",
            importsBooks = false
        )
        group == LibraryGroup.UNREAD -> LibraryEmptyStateCopy(
            title = "No unread books",
            body = "The rest of your library is still available.",
            primaryAction = "Show all",
            importsBooks = false
        )
        group == LibraryGroup.COLLECTIONS -> LibraryEmptyStateCopy(
            title = "No collections yet",
            body = "Add a book to a collection from its book actions.",
            primaryAction = "Show all",
            importsBooks = false
        )
        else -> LibraryEmptyStateCopy(
            title = "Nothing here",
            body = "Switch filters to see the rest of your library.",
            primaryAction = "Show all",
            importsBooks = false
        )
    }

@Composable
internal fun LibraryEmptyState(
    copy: LibraryEmptyStateCopy,
    onImport: () -> Unit,
    onShowAll: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(56.dp))
            Text(copy.title, style = MaterialTheme.typography.titleMedium)
            Text(
                copy.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Button(onClick = if (copy.importsBooks) onImport else onShowAll) {
                Icon(
                    if (copy.importsBooks) Icons.Filled.Add else Icons.Filled.Search,
                    contentDescription = null
                )
                Spacer(Modifier.width(6.dp))
                Text(copy.primaryAction)
            }
        }
    }
}

@Composable
internal fun BookAudiobookDialog(
    book: BookEntity,
    settings: ReaderSettings,
    models: List<NeuralTtsModelEntity>,
    bookAudio: List<BookAudioEntity>,
    scan: AudiobookScanUiState?,
    playback: AudiobookPlaybackUiState,
    onDismiss: () -> Unit,
    onScan: () -> Unit,
    onGenerate: () -> Unit,
    onCancelGeneration: () -> Unit,
    onExportAudio: (BookAudioEntity) -> Unit,
    onDeleteAudio: (BookAudioEntity) -> Unit,
    onDeleteAllAudio: () -> Unit,
    onPlayAudio: (BookAudioEntity) -> Unit,
    onPauseAudio: (BookAudioEntity) -> Unit,
    onStopAudio: () -> Unit,
    onSelected: (String) -> Unit,
    onSpeakerSelected: (Int) -> Unit,
    onGenderSelected: (NeuralTtsGender) -> Unit,
    onToneSelected: (NeuralTtsTone) -> Unit,
    onPaceSelected: (NeuralTtsPace) -> Unit,
    onDownload: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
    onPreview: (String) -> Unit,
) {
    val modelsById = remember(models) { models.associateBy { it.modelId } }
    val selectedSpec = NeuralTtsModelCatalog.models.firstOrNull { it.modelId == settings.neuralTtsModelId }
        ?: NeuralTtsModelCatalog.models.first()
    val selectedModel = modelsById[selectedSpec.modelId]
    val selectedInstalled = selectedModel?.status == NeuralTtsModelStatus.INSTALLED
    val selectedSpeaker = selectedSpec.speaker(settings.neuralTtsSpeakerId)
    val narratorOptions = remember(selectedSpec, settings.neuralTtsGender, selectedSpeaker) {
        selectedSpec.speakers
            .filter { settings.neuralTtsGender == NeuralTtsGender.ANY || it.gender == settings.neuralTtsGender }
            .let { filtered ->
                if (selectedSpeaker in filtered) filtered else listOf(selectedSpeaker) + filtered
            }
    }
    val selectedAudio = remember(bookAudio, settings.neuralTtsModelId, settings.neuralTtsSpeakerId, settings.neuralTtsPace, settings.neuralTtsTone) {
        bookAudio.firstOrNull { audio ->
            audio.modelId == settings.neuralTtsModelId &&
                audio.speakerId == selectedSpec.normalizedSpeakerId(settings.neuralTtsSpeakerId) &&
                abs(audio.speed - settings.neuralTtsPace.speed) < 0.001f &&
                audio.tone == settings.neuralTtsTone.name
        }
    }
    val generatingSelectedAudio = selectedAudio?.status == BookAudioStatus.GENERATING
    val visibleVoices = NeuralTtsModelCatalog.models
    val generatedAudio = remember(bookAudio) {
        bookAudio
            .filter { it.status == BookAudioStatus.GENERATED }
            .sortedByDescending { it.generatedAt ?: it.updatedAt }
    }
    var deleteCandidate by remember(book.id) { mutableStateOf<BookAudioEntity?>(null) }
    var deleteAllOpen by remember(book.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate audiobook") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = listOf(
                                "Full book",
                                bookLengthLabel(book),
                                scan?.takeIf { it.hasText }?.let { audiobookDurationLabel(it.estimatedAudioMillis) },
                                selectedSpec.displayName,
                                selectedSpeaker.label,
                                settings.neuralTtsTone.label,
                                settings.neuralTtsPace.label
                            ).joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                selectedAudio?.let { audio ->
                    item {
                        AudiobookStatusCard(
                            audio = audio,
                            onCancelGeneration = onCancelGeneration,
                            onExportAudio = onExportAudio,
                            playback = playback,
                            onPlayAudio = onPlayAudio,
                            onPauseAudio = onPauseAudio,
                            onStopAudio = onStopAudio,
                            onDeleteAudio = { deleteCandidate = it },
                        )
                    }
                }
                if (bookAudio.isNotEmpty()) {
                    item {
                        OutlinedButton(
                            onClick = { deleteAllOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Delete all generated audio for this book")
                        }
                    }
                }
                item {
                    AudiobookScanCard(
                        scan = scan,
                        onScan = onScan
                    )
                }
                item {
                    AudiobookRuntimeNote()
                }
                item {
                    AudiobookChipGroup(
                        title = "Voice",
                        options = NeuralTtsGender.entries,
                        selected = settings.neuralTtsGender,
                        label = { it.label },
                        onSelected = onGenderSelected
                    )
                }
                if (selectedSpec.speakers.size > 1) {
                    item {
                        AudiobookChipGroup(
                            title = "Narrator",
                            options = narratorOptions,
                            selected = selectedSpeaker,
                            label = { "${it.label} (${it.gender.label})" },
                            onSelected = { onSpeakerSelected(it.id) }
                        )
                    }
                }
                item {
                    AudiobookChipGroup(
                        title = "Tone",
                        options = NeuralTtsTone.entries,
                        selected = settings.neuralTtsTone,
                        label = { it.label },
                        onSelected = onToneSelected
                    )
                }
                item {
                    AudiobookChipGroup(
                        title = "Pacing",
                        options = NeuralTtsPace.entries,
                        selected = settings.neuralTtsPace,
                        label = { it.label },
                        onSelected = onPaceSelected
                    )
                }
                item {
                    Text("Voice model", style = MaterialTheme.typography.labelLarge)
                }
                items(
                    items = visibleVoices,
                    key = { it.modelId }
                ) { spec ->
                    val model = modelsById[spec.modelId]
                    val status = model?.status ?: NeuralTtsModelStatus.NOT_DOWNLOADED
                    AudiobookVoiceRow(
                        spec = spec,
                        status = status,
                        statusText = audiobookVoiceStatusText(
                            status = status,
                            downloaded = model?.downloadedBytes ?: 0,
                            total = model?.totalBytes ?: spec.archiveBytes,
                            archiveBytes = spec.archiveBytes,
                            error = model?.error
                        ),
                        selected = spec.modelId == settings.neuralTtsModelId,
                        onSelected = { onSelected(spec.modelId) },
                        onDownload = { onDownload(spec.modelId) },
                        onDelete = { onDeleteModel(spec.modelId) },
                        onPreview = { onPreview(spec.modelId) }
                    )
                }
                if (generatedAudio.isNotEmpty()) {
                    item {
                        GeneratedAudiobookList(
                            audio = generatedAudio,
                            selectedAudioId = selectedAudio?.id,
                            playback = playback,
                            onExportAudio = onExportAudio,
                            onPlayAudio = onPlayAudio,
                            onPauseAudio = onPauseAudio,
                            onStopAudio = onStopAudio,
                            onDeleteAudio = { deleteCandidate = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onGenerate,
                enabled = selectedInstalled && !generatingSelectedAudio
            ) {
                Icon(Icons.Filled.GraphicEq, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (generatingSelectedAudio) "Generating" else "Generate full book")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
    deleteCandidate?.let { audio ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete generated audio?") },
            text = {
                Text(
                    listOf(
                        audio.displayProfileLabel(),
                        audio.estimatedDurationLabel(),
                        audio.fileSizeBytes.takeIf { it > 0 }?.compactBytes()
                    ).filterNotNull().joinToString(" • ")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteCandidate = null
                        onDeleteAudio(audio)
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("Cancel")
                }
            }
        )
    }
    if (deleteAllOpen) {
        AlertDialog(
            onDismissRequest = { deleteAllOpen = false },
            title = { Text("Delete all audiobook audio?") },
            text = { Text("This removes every generated, partial, and failed audiobook version for ${book.title}.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteAllOpen = false
                        onDeleteAllAudio()
                    }
                ) {
                    Text("Delete all")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteAllOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AudiobookScanCard(
    scan: AudiobookScanUiState?,
    onScan: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Full-book audio scan", style = MaterialTheme.typography.labelLarge)
                    Text(
                        audiobookScanSummary(scan),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (scan?.scanning == true) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    OutlinedButton(onClick = onScan) {
                        Text(if (scan?.hasText == true) "Rescan" else "Scan")
                    }
                }
            }
            scan?.error?.let { error ->
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (scan?.hasText == true) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudiobookScanPill("${scan.wordCount} words")
                    AudiobookScanPill("${scan.segmentCount} segments")
                    AudiobookScanPill(audiobookDurationLabel(scan.estimatedAudioMillis))
                    AudiobookScanPill("~${scan.estimatedStorageBytes.compactBytes()}")
                }
            }
        }
    }
}

@Composable
private fun AudiobookScanPill(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun audiobookScanSummary(scan: AudiobookScanUiState?): String =
    when {
        scan?.scanning == true -> "Scanning indexed book text for extractable narration."
        scan?.hasText == true -> "${scan.sourceSectionCount} source sections prepared for full-book generation."
        scan?.error != null -> "Scan could not prepare narration for this book."
        else -> "Scan the selected ebook before a long generation job."
    }

@Composable
private fun AudiobookRuntimeNote() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = "Generated locally on device with Kokoro v1.0. XReader uses the optimized XNNPACK runtime when available and falls back to CPU, so full-book jobs can take time and should be started with enough battery.",
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AudiobookStatusCard(
    audio: BookAudioEntity,
    onCancelGeneration: () -> Unit,
    onExportAudio: (BookAudioEntity) -> Unit,
    playback: AudiobookPlaybackUiState,
    onPlayAudio: (BookAudioEntity) -> Unit,
    onPauseAudio: (BookAudioEntity) -> Unit,
    onStopAudio: () -> Unit,
    onDeleteAudio: (BookAudioEntity) -> Unit,
) {
    val progress = when {
        audio.segmentCount <= 0 -> 0f
        else -> audio.completedSegments.toFloat() / audio.segmentCount.toFloat()
    }.coerceIn(0f, 1f)
    val title = when (audio.status) {
        BookAudioStatus.GENERATED -> "Audio ready"
        BookAudioStatus.GENERATING -> "Generating audio"
        BookAudioStatus.CANCELED -> "Audio generation stopped"
        BookAudioStatus.FAILED -> "Audio generation failed"
    }
    val detail = when (audio.status) {
        BookAudioStatus.GENERATED -> listOf(
            "${audio.segmentCount} segments",
            audio.estimatedDurationLabel(),
            audio.playbackResumeLabel(),
            audio.fileSizeBytes.takeIf { it > 0 }?.compactBytes()
        ).filterNotNull().joinToString(" • ")
        BookAudioStatus.GENERATING -> {
            if (audio.segmentCount > 0) {
                listOf(
                    "${audio.completedSegments.coerceAtMost(audio.segmentCount)} of ${audio.segmentCount} segments",
                    "${(progress * 100).roundToInt()}%",
                    audio.generationEtaLabel(),
                    audio.estimatedDurationLabel()
                ).filterNotNull().joinToString(" • ")
            } else {
                "Preparing segments"
            }
        }
        BookAudioStatus.CANCELED -> {
            if (audio.segmentCount > 0) {
                listOf(
                    "${audio.completedSegments.coerceAtMost(audio.segmentCount)} of ${audio.segmentCount} segments completed",
                    audio.estimatedDurationLabel()
                ).filterNotNull().joinToString(" • ")
            } else {
                "No segments were generated."
            }
        }
        BookAudioStatus.FAILED -> audio.error ?: "Generation stopped before audio was ready."
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = when (audio.status) {
            BookAudioStatus.GENERATED -> MaterialTheme.colorScheme.primaryContainer
            BookAudioStatus.GENERATING -> MaterialTheme.colorScheme.secondaryContainer
            BookAudioStatus.CANCELED -> MaterialTheme.colorScheme.surfaceVariant
            BookAudioStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
        }
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = when (audio.status) {
                    BookAudioStatus.GENERATED -> MaterialTheme.colorScheme.onPrimaryContainer
                    BookAudioStatus.GENERATING -> MaterialTheme.colorScheme.onSecondaryContainer
                    BookAudioStatus.CANCELED -> MaterialTheme.colorScheme.onSurfaceVariant
                    BookAudioStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
                }
            )
            if (audio.status == BookAudioStatus.GENERATING && audio.segmentCount > 0) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = onCancelGeneration,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Stop")
                }
            }
            if (audio.status == BookAudioStatus.CANCELED || audio.status == BookAudioStatus.FAILED) {
                TextButton(
                    onClick = { onDeleteAudio(audio) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete")
                }
            }
            if (audio.status == BookAudioStatus.GENERATED) {
                AudiobookPlaybackActions(
                    audio = audio,
                    playback = playback,
                    onPlayAudio = onPlayAudio,
                    onPauseAudio = onPauseAudio,
                    onStopAudio = onStopAudio,
                    onExportAudio = onExportAudio,
                    onDeleteAudio = onDeleteAudio,
                    modifier = Modifier.align(Alignment.End)
                )
                playback.error?.takeIf { playback.audioId == audio.id }?.let { error ->
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun AudiobookPlaybackActions(
    audio: BookAudioEntity,
    playback: AudiobookPlaybackUiState,
    onPlayAudio: (BookAudioEntity) -> Unit,
    onPauseAudio: (BookAudioEntity) -> Unit,
    onStopAudio: () -> Unit,
    onExportAudio: (BookAudioEntity) -> Unit,
    onDeleteAudio: (BookAudioEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = playback.audioId == audio.id && playback.active
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (active) {
            Text(
                playbackProgressLabel(playback),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TooltipIconButton(
            label = if (active && playback.playing) "Pause generated audio" else "Play generated audio",
            onClick = {
                if (active && playback.playing) onPauseAudio(audio) else onPlayAudio(audio)
            },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(if (active && playback.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null)
        }
        if (active) {
            TooltipIconButton(
                label = "Stop generated audio",
                onClick = onStopAudio,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Filled.Stop, contentDescription = null)
            }
        }
        TooltipIconButton(
            label = "Save generated audio",
            onClick = { onExportAudio(audio) },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(Icons.Filled.FileDownload, contentDescription = null)
        }
        TooltipIconButton(
            label = "Delete generated audio",
            onClick = { onDeleteAudio(audio) },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null)
        }
    }
}

private fun playbackProgressLabel(playback: AudiobookPlaybackUiState): String =
    if (playback.segmentCount > 0) {
        listOfNotNull(
            "${(playback.segmentIndex + 1).coerceAtMost(playback.segmentCount)} / ${playback.segmentCount}",
            playback.segmentTimeLabel()
        ).joinToString(" • ")
    } else {
        "Ready"
    }

private fun BookAudioEntity.playbackResumeLabel(): String? {
    if (segmentCount <= 0) return null
    val segment = playbackSegmentIndex.coerceIn(0, segmentCount)
    if (segment <= 0 && playbackPositionMs <= 0) return null
    if (segment >= segmentCount) return null
    return "resume ${segment + 1} / $segmentCount"
}

@Composable
private fun GeneratedAudiobookList(
    audio: List<BookAudioEntity>,
    selectedAudioId: Long?,
    playback: AudiobookPlaybackUiState,
    onExportAudio: (BookAudioEntity) -> Unit,
    onPlayAudio: (BookAudioEntity) -> Unit,
    onPauseAudio: (BookAudioEntity) -> Unit,
    onStopAudio: () -> Unit,
    onDeleteAudio: (BookAudioEntity) -> Unit,
) {
    var expanded by remember(audio.map { it.id } to selectedAudioId) { mutableStateOf(false) }
    val visibleAudio = remember(audio, selectedAudioId, expanded) {
        visibleGeneratedAudiobooks(audio, selectedAudioId, expanded)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Saved audio", style = MaterialTheme.typography.labelLarge)
        visibleAudio.forEach { item ->
            GeneratedAudiobookRow(
                audio = item,
                selected = item.id == selectedAudioId,
                playback = playback,
                onExportAudio = onExportAudio,
                onPlayAudio = onPlayAudio,
                onPauseAudio = onPauseAudio,
                onStopAudio = onStopAudio,
                onDeleteAudio = onDeleteAudio
            )
        }
        if (audio.size > COLLAPSED_GENERATED_AUDIO_COUNT) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Show fewer versions" else "Show all ${audio.size} versions")
            }
        }
    }
}

@Composable
private fun GeneratedAudiobookRow(
    audio: BookAudioEntity,
    selected: Boolean,
    playback: AudiobookPlaybackUiState,
    onExportAudio: (BookAudioEntity) -> Unit,
    onPlayAudio: (BookAudioEntity) -> Unit,
    onPauseAudio: (BookAudioEntity) -> Unit,
    onStopAudio: () -> Unit,
    onDeleteAudio: (BookAudioEntity) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Filled.GraphicEq,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    audio.displayProfileLabel(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOf(
                        "${audio.segmentCount} segments",
                        audio.estimatedDurationLabel(),
                        audio.playbackResumeLabel(),
                        audio.fileSizeBytes.takeIf { it > 0 }?.compactBytes(),
                        audio.generatedAt?.let { "generated ${relativeAgeLabel(it)}" }
                    ).filterNotNull().joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            AudiobookPlaybackActions(
                audio = audio,
                playback = playback,
                onPlayAudio = onPlayAudio,
                onPauseAudio = onPauseAudio,
                onStopAudio = onStopAudio,
                onExportAudio = onExportAudio,
                onDeleteAudio = onDeleteAudio
            )
        }
    }
}

@Composable
private fun <T> AudiobookChipGroup(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelected(option) },
                    label = { Text(label(option)) },
                    leadingIcon = if (option == selected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

@Composable
private fun AudiobookVoiceRow(
    spec: NeuralTtsModelSpec,
    status: NeuralTtsModelStatus,
    statusText: String,
    selected: Boolean,
    onSelected: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onPreview: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        spec.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${spec.gender.label} • ${spec.voiceDescription}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            status == NeuralTtsModelStatus.FAILED -> MaterialTheme.colorScheme.error
                            selected -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (status == NeuralTtsModelStatus.DOWNLOADING || status == NeuralTtsModelStatus.EXTRACTING) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when (status) {
                    NeuralTtsModelStatus.DOWNLOADING,
                    NeuralTtsModelStatus.EXTRACTING -> Unit
                    NeuralTtsModelStatus.INSTALLED -> {
                        TextButton(onClick = onPreview) { Text("Preview") }
                        TextButton(onClick = onSelected, enabled = !selected) {
                            Text(if (selected) "Selected" else "Use")
                        }
                        TextButton(onClick = onDownload) { Text("Reinstall") }
                        TextButton(onClick = onDelete) { Text("Delete") }
                    }
                    NeuralTtsModelStatus.NOT_DOWNLOADED,
                    NeuralTtsModelStatus.FAILED -> TextButton(onClick = onDownload) {
                        Text(if (status == NeuralTtsModelStatus.FAILED) "Retry" else "Download")
                    }
                }
            }
        }
    }
}

private fun audiobookVoiceStatusText(
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

@Composable
internal fun BookCollectionsDialog(
    item: BookListItem,
    allCollections: List<CollectionUiItem>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (CollectionUiItem) -> Unit,
) {
    var collectionName by remember(item.book.id) { mutableStateOf("") }
    val currentIds = item.collections.mapTo(mutableSetOf()) { it.id }
    val availableCollections = allCollections.filterNot { it.id in currentIds }
    fun addCollection(name: String = collectionName) {
        val cleaned = name.trim()
        if (cleaned.isNotBlank()) {
            onAdd(cleaned)
            collectionName = ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Collections") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    item.book.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("On this book", style = MaterialTheme.typography.labelLarge)
                    if (item.collections.isEmpty()) {
                        Text(
                            "No collections",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            item.collections.forEach { collection ->
                                FilterChip(
                                    selected = true,
                                    onClick = { onRemove(collection) },
                                    label = {
                                        Text(
                                            collection.name,
                                            modifier = Modifier.widthIn(max = 220.dp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                )
                            }
                        }
                    }
                }
                if (availableCollections.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Add existing", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            availableCollections.forEach { collection ->
                                FilterChip(
                                    selected = false,
                                    onClick = { addCollection(collection.name) },
                                    label = {
                                        Text(
                                            collection.name,
                                            modifier = Modifier.widthIn(max = 220.dp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = collectionName,
                        onValueChange = { collectionName = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("New collection") }
                    )
                    Button(
                        onClick = { addCollection() },
                        enabled = collectionName.isNotBlank()
                    ) {
                        Text("Add")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
internal fun BookRow(
    item: BookListItem,
    density: LibraryDensity,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onSetFinished: (Boolean) -> Unit,
    onCollections: () -> Unit,
    onEdit: () -> Unit,
    onExport: () -> Unit,
    onGenerateAudiobook: () -> Unit,
    onDelete: () -> Unit,
) {
    val progress = item.displayLibraryProgress()
    var menuOpen by remember(item.book.id) { mutableStateOf(false) }
    val compact = density == LibraryDensity.COMPACT
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(if (compact) 10.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
        ) {
            BookCoverTile(
                book = item.book,
                width = if (compact) 44.dp else 58.dp,
                height = if (compact) 62.dp else 82.dp
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp)) {
                Text(
                    item.book.title,
                    style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(item.book.author, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                LinearProgressIndicator(
                    progress = { progress.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(bookFormatLabel(item.book), modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${(progress * 100).roundToInt()}% read", style = MaterialTheme.typography.bodySmall)
                    if (!compact) {
                        Text(bookLengthLabel(item.book), style = MaterialTheme.typography.bodySmall)
                    }
                    item.book.genre?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Box {
                TooltipIconButton(
                    label = "Book actions",
                    onClick = { menuOpen = true },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (item.book.favorite) "Unfavorite" else "Favorite") },
                        leadingIcon = {
                            Icon(
                                if (item.book.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onFavorite()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (item.book.finished) "Mark not finished" else "Mark finished") },
                        leadingIcon = {
                            Icon(
                                if (item.book.finished) Icons.Filled.Refresh else Icons.Filled.Check,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onSetFinished(!item.book.finished)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Collections") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onCollections()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit metadata") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Save copy") },
                        leadingIcon = { Icon(Icons.Filled.FileDownload, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onExport()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Audiobook") },
                        leadingIcon = { Icon(Icons.Filled.GraphicEq, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onGenerateAudiobook()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun SearchResultsStrip(
    results: List<LibrarySearchRow>,
    query: String,
    onOpenResult: (Long, String?) -> Unit,
) {
    var expanded by remember(query, results) { mutableStateOf(false) }
    val visibleResults = remember(results, expanded) {
        visibleLibrarySearchResults(results, expanded)
    }
    val canExpand = results.size > COLLAPSED_LIBRARY_SEARCH_RESULT_COUNT
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = librarySearchResultsHeader(visibleResults.size, results.size),
                    style = MaterialTheme.typography.labelLarge
                )
                if (canExpand) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "Show fewer" else "Show more")
                    }
                }
            }
            if (expanded) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = visibleResults,
                        key = { "${it.row.bookId}:${it.row.unitIndex}:${it.row.id}" }
                    ) { result ->
                        LibrarySearchResultItem(
                            result = result,
                            query = query,
                            onOpenResult = onOpenResult
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    visibleResults.forEach { result ->
                        LibrarySearchResultItem(
                            result = result,
                            query = query,
                            onOpenResult = onOpenResult
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySearchResultItem(
    result: LibrarySearchRow,
    query: String,
    onOpenResult: (Long, String?) -> Unit,
) {
    val row = result.row
    val snippet = searchResultSnippet(row.body, query)
    val subtitle = listOf(row.heading, result.bookAuthor)
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.equals(result.bookTitle, ignoreCase = true) }
        .distinct()
        .joinToString(" • ")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenResult(row.bookId, "$SEARCH_UNIT_LOCATOR_PREFIX${row.unitIndex}") },
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = result.bookTitle,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = snippet,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal const val COLLAPSED_LIBRARY_SEARCH_RESULT_COUNT = 5

internal fun <T> visibleLibrarySearchResults(results: List<T>, expanded: Boolean): List<T> =
    if (expanded) results else results.take(COLLAPSED_LIBRARY_SEARCH_RESULT_COUNT)

internal fun librarySearchResultsHeader(visibleCount: Int, totalCount: Int): String =
    when {
        totalCount <= 0 -> "Text matches"
        visibleCount < totalCount -> "Text matches $visibleCount of $totalCount"
        else -> "Text matches $totalCount"
    }

internal fun String.fileSafeName(): String =
    replace(Regex("[\\\\/:*?\"<>|]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { "xreader" }
        .take(80)

private fun BookAudioEntity.fileSafeProfileName(): String =
    displayProfileLabel()
        .fileSafeName()
        .take(48)

private fun BookAudioEntity.displayProfileLabel(): String =
    listOf(
        modelDisplayName,
        "Speaker ${speakerId + 1}".takeIf { speakerId > 0 },
        tone.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) },
        "%.2fx".format(Locale.US, speed)
    ).filterNotNull().joinToString(" ")

internal fun visibleGeneratedAudiobooks(
    audio: List<BookAudioEntity>,
    selectedAudioId: Long?,
    expanded: Boolean,
): List<BookAudioEntity> {
    if (expanded || audio.size <= COLLAPSED_GENERATED_AUDIO_COUNT) return audio
    val collapsed = audio.take(COLLAPSED_GENERATED_AUDIO_COUNT)
    val selected = selectedAudioId?.let { id -> audio.firstOrNull { it.id == id } }
    return if (selected == null || collapsed.any { it.id == selected.id }) {
        collapsed
    } else {
        collapsed.dropLast(1) + selected
    }
}

internal fun BookAudioEntity.estimatedDurationLabel(): String? =
    audiobookEstimatedDurationMillis(wordCount = wordCount, speed = speed)
        .takeIf { it > 0L }
        ?.let(::audiobookDurationLabel)

internal fun audiobookEstimatedDurationMillis(wordCount: Int, speed: Float): Long {
    if (wordCount <= 0) return 0L
    val effectiveWpm = (AUDIOBOOK_ESTIMATED_WORDS_PER_MINUTE * speed.coerceIn(0.75f, 1.35f)).coerceAtLeast(1f)
    return ((wordCount / effectiveWpm.toDouble()) * 60_000.0).toLong().coerceAtLeast(0L)
}

internal fun audiobookDurationLabel(millis: Long): String {
    val minutes = ceil(millis.coerceAtLeast(0L) / 60_000.0).toLong()
    if (minutes <= 0L) return "under 1m audio"
    if (minutes < 60L) return "~${minutes}m audio"
    val hours = minutes / 60L
    val remaining = minutes % 60L
    return if (remaining == 0L) "~${hours}h audio" else "~${hours}h ${remaining}m audio"
}

internal fun AudiobookPlaybackUiState.segmentTimeLabel(): String? {
    if (segmentDurationMs <= 0 && segmentPositionMs <= 0) return null
    val position = segmentPositionMs.coerceAtLeast(0)
    val duration = segmentDurationMs.coerceAtLeast(0)
    return if (duration > 0) {
        "${formatPlaybackTimestamp(position)} / ${formatPlaybackTimestamp(duration)}"
    } else {
        formatPlaybackTimestamp(position)
    }
}

private fun formatPlaybackTimestamp(millis: Int): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(Locale.US, hours, minutes, seconds)
    } else {
        "%d:%02d".format(Locale.US, minutes, seconds)
    }
}

private fun relativeAgeLabel(timeMillis: Long): String {
    val elapsedMillis = (System.currentTimeMillis() - timeMillis).coerceAtLeast(0L)
    val minutes = elapsedMillis / 60_000L
    if (minutes < 1L) return "just now"
    if (minutes < 60L) return "${minutes}m ago"
    val hours = minutes / 60L
    if (hours < 24L) return "${hours}h ago"
    val days = hours / 24L
    return "${days}d ago"
}

internal fun searchResultSnippet(
    body: String,
    query: String,
    maxLength: Int = 150,
): String {
    val cleanBody = body.replace(SEARCH_SNIPPET_WHITESPACE, " ").trim()
    if (cleanBody.length <= maxLength) return cleanBody
    val terms = SEARCH_SNIPPET_TERM_PATTERN
        .findAll(query)
        .map { it.value }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .sortedByDescending { it.length }
    val matchIndex = terms
        .asSequence()
        .map { cleanBody.indexOf(it, ignoreCase = true) }
        .filter { it >= 0 }
        .minOrNull()
    val center = matchIndex ?: 0
    val halfWindow = maxLength / 2
    var start = (center - halfWindow).coerceAtLeast(0)
    var end = (start + maxLength).coerceAtMost(cleanBody.length)
    if (end - start < maxLength) {
        start = (end - maxLength).coerceAtLeast(0)
    }
    if (start > 0) {
        val nextSpace = cleanBody.indexOf(' ', start)
        if (nextSpace in (start + 1)..(end - 20)) start = nextSpace + 1
    }
    if (end < cleanBody.length) {
        val previousSpace = cleanBody.lastIndexOf(' ', end)
        if (previousSpace in (start + 20)..(end - 1)) end = previousSpace
    }
    return buildString {
        if (start > 0) append("...")
        append(cleanBody.substring(start, end).trim())
        if (end < cleanBody.length) append("...")
    }
}

private fun Long.compactBytes(): String =
    when {
        this >= 1_048_576L -> "${(this / 1_048_576.0).roundToInt()} MB"
        this >= 1024L -> "${(this / 1024.0).roundToInt()} KB"
        else -> "$this B"
    }

private const val AUDIOBOOK_ESTIMATED_WORDS_PER_MINUTE = 150f
private const val COLLAPSED_GENERATED_AUDIO_COUNT = 4

private val SEARCH_SNIPPET_WHITESPACE = Regex("\\s+")
private val SEARCH_SNIPPET_TERM_PATTERN = Regex("[\\p{L}\\p{N}]+")
