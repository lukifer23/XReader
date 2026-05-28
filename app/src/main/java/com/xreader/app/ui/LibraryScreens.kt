@file:OptIn(ExperimentalMaterial3Api::class)

package com.xreader.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xreader.app.AppContainer
import com.xreader.app.data.BookEntity
import com.xreader.app.data.ReaderTheme
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
    val supportedBookMimeTypes = remember {
        arrayOf(
            "application/epub+zip",
            "application/pdf",
            "text/plain",
            "application/octet-stream"
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.import(uri)
    }
    val openImportPicker = {
        if (!state.importing) launcher.launch(supportedBookMimeTypes)
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
                    TooltipIconButton(
                        label = if (state.importing) "Importing books" else "Import books",
                        onClick = openImportPicker,
                        enabled = !state.importing,
                        modifier = Modifier.size(44.dp)
                    ) {
                        if (state.importing) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Add, contentDescription = null)
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
            onImport = openImportPicker,
            onOpenBook = { openReaderAt(it, null) },
            onOpenSearchResult = openReaderAt,
            onToggleFavorite = viewModel::toggleFavorite,
            onUpdateMetadata = viewModel::updateMetadata,
            onReplaceCover = viewModel::replaceCover,
            onRefreshBookHealth = viewModel::refreshBookHealth,
            onRepairBook = viewModel::repairBook,
            onDeleteBook = viewModel::deleteBook,
            modifier = Modifier.padding(padding)
        )
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
    onUpdateMetadata: (BookEntity, String, String, Int?, String?, String?, Double?) -> Unit,
    onReplaceCover: (BookEntity, Uri) -> Unit,
    onRefreshBookHealth: (Long) -> Unit,
    onRepairBook: (BookEntity) -> Unit,
    onDeleteBook: (BookEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<BookEntity?>(null) }
    var deleteCandidate by remember { mutableStateOf<BookEntity?>(null) }
    var coverTarget by remember { mutableStateOf<BookEntity?>(null) }
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
                .filter { (it.state?.progress ?: 0.0) in 0.01..0.994 }
                .maxByOrNull { it.state?.lastReadAt ?: it.book.lastOpenedAt ?: it.book.importedAt }
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
        AnimatedVisibility(visible = searchExpanded) {
            LibrarySearchField(
                query = state.query,
                onQuery = onQuery,
                onSearch = onSearch,
                onCollapse = {
                    if (state.query.isBlank()) {
                        searchExpanded = false
                    } else {
                        onQuery("")
                    }
                }
            )
        }
        AnimatedVisibility(visible = !searchExpanded) {
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
            EmptyLibrary(onImport = onImport)
        } else {
            val grouped = groupBooks(state.group, displayBooks)
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
                            onEdit = { editing = item.book },
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
            onDismiss = { editing = null },
            onRefreshHealth = { onRefreshBookHealth(book.id) },
            onRepairBook = { onRepairBook(book) },
            onReplaceCover = {
                coverTarget = book
                coverLauncher.launch(supportedCoverMimeTypes)
            },
            onSave = { title, author, year, genre, series, index ->
                onUpdateMetadata(book, title, author, year, genre, series, index)
                editing = null
            }
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
    val inProgress = state.books.count { (it.state?.progress ?: 0.0) in 0.01..0.994 }
    val finished = state.books.count { it.book.finished || (it.state?.progress ?: 0.0) >= 0.995 }
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
    val progress = item.state?.progress ?: 0.0
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
internal fun EmptyLibrary(onImport: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(56.dp))
            Text("Import EPUB, PDF, or TXT books", style = MaterialTheme.typography.titleMedium)
            Text("Files are copied into private app storage.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onImport) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Import books")
            }
        }
    }
}

@Composable
internal fun BookRow(
    item: BookListItem,
    density: LibraryDensity,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val progress = item.state?.progress ?: 0.0
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
                        Text(item.book.format.name, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${(progress * 100).roundToInt()}% read", style = MaterialTheme.typography.bodySmall)
                    if (!compact) {
                        Text(wordCountLabel(item.book.wordCount), style = MaterialTheme.typography.bodySmall)
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
                        text = { Text("Edit metadata") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onEdit()
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

@Composable
internal fun BookMetadataDialog(
    book: BookEntity,
    health: BookHealthUiState?,
    repairing: Boolean,
    onDismiss: () -> Unit,
    onRefreshHealth: () -> Unit,
    onRepairBook: () -> Unit,
    onReplaceCover: () -> Unit,
    onSave: (String, String, Int?, String?, String?, Double?) -> Unit,
) {
    var title by remember(book.id) { mutableStateOf(book.title) }
    var author by remember(book.id) { mutableStateOf(book.author) }
    var year by remember(book.id) { mutableStateOf(book.year?.toString().orEmpty()) }
    var genre by remember(book.id) { mutableStateOf(book.genre.orEmpty()) }
    var series by remember(book.id) { mutableStateOf(book.series.orEmpty()) }
    var seriesIndex by remember(book.id) { mutableStateOf(book.seriesIndex?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit metadata") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BookCoverTile(book = book, width = 52.dp, height = 74.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Cover", style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = onReplaceCover) {
                            Icon(Icons.Filled.Edit, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (book.coverImagePath.isNullOrBlank()) "Choose cover" else "Replace cover")
                        }
                    }
                }
                BookHealthPanel(
                    book = book,
                    health = health,
                    repairing = repairing,
                    onRefresh = onRefreshHealth,
                    onRepair = onRepairBook
                )
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(author, { author = it }, label = { Text("Author") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("Year") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(genre, { genre = it }, label = { Text("Genre") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(series, { series = it }, label = { Text("Series") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(seriesIndex, { seriesIndex = it }, label = { Text("#") }, singleLine = true, modifier = Modifier.width(96.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        title,
                        author,
                        year.toIntOrNull(),
                        genre,
                        series,
                        seriesIndex.toDoubleOrNull()
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun BookHealthPanel(
    book: BookEntity,
    health: BookHealthUiState?,
    repairing: Boolean,
    onRefresh: () -> Unit,
    onRepair: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Library health", style = MaterialTheme.typography.titleSmall)
                TooltipIconButton(
                    label = "Refresh book health",
                    onClick = onRefresh,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HealthPill("File", health?.let { if (it.fileAvailable) "Ready" else "Missing" } ?: "Checking")
                HealthPill("Cover", health?.let { if (it.coverAvailable) "Ready" else "Missing" } ?: "Checking")
                HealthPill("Search", health?.let { "${it.searchRows} chunks" } ?: "Checking")
                HealthPill("Words", wordCountLabel(book.wordCount))
                HealthPill("Size", fileSizeLabel(book.fileSizeBytes))
                book.pageCount?.let { HealthPill("Pages", it.toString()) }
            }
            TextButton(onClick = onRepair, enabled = !repairing) {
                if (repairing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                }
                Spacer(Modifier.width(6.dp))
                Text(if (repairing) "Repairing book" else "Repair this book")
            }
        }
    }
}

@Composable
private fun HealthPill(label: String, value: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun fileSizeLabel(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        "%.1f MB".format(mb)
    } else {
        "${kb.roundToInt().coerceAtLeast(1)} KB"
    }
}
