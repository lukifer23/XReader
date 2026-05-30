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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Sort
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewCompact
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xreader.app.AppContainer
import com.xreader.app.data.BookEntity
import com.xreader.app.data.ReaderTheme
import com.xreader.app.repository.bookExportFileName
import com.xreader.app.repository.bookExportMimeType
import com.xreader.app.settings.LibraryDensity
import com.xreader.app.settings.LibrarySort
import kotlin.math.roundToInt

@Composable
internal fun LibraryRoute(
    container: AppContainer,
    openReaderAt: (Long, String?) -> Unit,
    openAnalytics: () -> Unit,
    openNotes: () -> Unit,
    openSettings: () -> Unit,
    currentTheme: ReaderTheme,
    onToggleTheme: () -> Unit,
) {
    val viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var importMenuOpen by remember { mutableStateOf(false) }
    var importDialogOpen by remember { mutableStateOf(false) }
    var exportTarget by remember { mutableStateOf<BookEntity?>(null) }
    val supportedBookMimeTypes = remember {
        arrayOf(
            "application/epub+zip",
            "application/pdf",
            "text/plain",
            "application/rtf",
            "text/rtf",
            "application/x-rtf",
            "application/vnd.oasis.opendocument.text",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/zip",
            "application/x-cbz",
            "application/vnd.comicbook+zip",
            "application/x-fictionbook+xml",
            "application/fb2+xml",
            "text/fb2+xml",
            "application/octet-stream"
        )
    }
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

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearMessage()
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
        bottomBar = { LibraryBottomBar(openAnalytics, openNotes, openSettings) }
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
}

internal enum class LibraryImportAction {
    FILES,
    FOLDER,
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
            SearchResultsStrip(state.librarySearchResults, onOpenSearchResult)
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
                    listOfNotNull("${(progress * 100).roundToInt()}% read", wpm?.let { "$it WPM" }).joinToString(" • "),
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
            body = "Import EPUB, PDF, TXT, CBZ, FB2, RTF, or ODT files from this device.",
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
    results: List<com.xreader.app.data.SearchIndexEntity>,
    onOpenResult: (Long, String?) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Text matches", style = MaterialTheme.typography.labelLarge)
            results.take(5).forEach {
                val snippet = it.body.replace(Regex("\\s+"), " ").trim().take(140)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenResult(it.bookId, "$SEARCH_UNIT_LOCATOR_PREFIX${it.unitIndex}") },
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = it.heading,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
