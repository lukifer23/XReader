@file:OptIn(ExperimentalMaterial3Api::class)

package com.xreader.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.xreader.app.AppContainer
import com.xreader.app.data.AnnotationEntity
import com.xreader.app.data.AnnotationKind
import com.xreader.app.data.BookEntity
import com.xreader.app.data.BookmarkEntity
import com.xreader.app.data.ReaderTheme
import com.xreader.app.reader.OpenPublication
import com.xreader.app.reader.ReaderNavigationItem
import com.xreader.app.settings.ReaderFontFamily
import com.xreader.app.settings.ReaderPdfFit
import com.xreader.app.settings.ReaderSettings
import com.xreader.app.settings.ReaderTextAlign
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun XReaderApp(container: AppContainer) {
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val activity = LocalContext.current.findActivity()
    LaunchedEffect(container) {
        delay(READER_PATH_WARMUP_DELAY_MS)
        container.warmReaderPath()
    }
    XReaderTheme(readerTheme = settings.theme) {
        AppSystemBars(activity = activity, theme = settings.theme)
        NavHost(
            navController = navController,
            startDestination = "library",
            modifier = Modifier.fillMaxSize()
        ) {
            composable("library") {
                LibraryRoute(
                    container = container,
                    openReaderAt = { bookId, locator ->
                        if (locator == null) {
                            navController.navigate("reader/$bookId")
                        } else {
                            navController.navigate("reader/$bookId?locator=${Uri.encode(locator)}")
                        }
                    },
                    openAnalytics = { navController.navigate("analytics") },
                    openNotes = { navController.navigate("notes") },
                    openSettings = { navController.navigate("settings") },
                    currentTheme = settings.theme,
                    onToggleTheme = settingsViewModel::toggleLightDark
                )
            }
            composable(
                route = "reader/{bookId}?locator={locator}",
                arguments = listOf(
                    navArgument("bookId") { type = NavType.LongType },
                    navArgument("locator") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { entry ->
                ReaderRoute(
                    bookId = entry.arguments?.getLong("bookId") ?: 0L,
                    initialLocatorJson = entry.arguments?.getString("locator")?.takeIf { it.isNotBlank() },
                    container = container,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("analytics") {
                AnalyticsRoute(container = container, onBack = { navController.popBackStack() })
            }
            composable("notes") {
                NotesRoute(
                    container = container,
                    onBack = { navController.popBackStack() },
                    openReaderAt = { bookId, locator ->
                        navController.navigate("reader/$bookId?locator=${Uri.encode(locator)}")
                    }
                )
            }
            composable("settings") {
                SettingsRoute(
                    viewModel = settingsViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun LibraryRoute(
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
                    IconButton(
                        onClick = openImportPicker,
                        enabled = !state.importing,
                        modifier = Modifier.semantics {
                            contentDescription = if (state.importing) "Importing books" else "Import books"
                        }
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
            onImport = openImportPicker,
            onOpenBook = { openReaderAt(it, null) },
            onOpenSearchResult = openReaderAt,
            onToggleFavorite = viewModel::toggleFavorite,
            onUpdateMetadata = viewModel::updateMetadata,
            onDeleteBook = viewModel::deleteBook,
            modifier = Modifier.padding(padding)
        )
    }
}

@Composable
private fun LibraryBottomBar(
    openAnalytics: () -> Unit,
    openNotes: () -> Unit,
    openSettings: () -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            selected = true,
            onClick = {},
            icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
            label = { Text("Library") }
        )
        NavigationBarItem(
            selected = false,
            onClick = openAnalytics,
            icon = { Icon(Icons.Filled.QueryStats, contentDescription = null) },
            label = { Text("Stats") }
        )
        NavigationBarItem(
            selected = false,
            onClick = openNotes,
            icon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null) },
            label = { Text("Notes") }
        )
        NavigationBarItem(
            selected = false,
            onClick = openSettings,
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { Text("Settings") }
        )
    }
}

@Composable
private fun ThemeToggleButton(theme: ReaderTheme, onClick: () -> Unit) {
    val dark = theme == ReaderTheme.DARK || theme == ReaderTheme.OLED
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(
            imageVector = if (dark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
            contentDescription = if (dark) "Switch to light mode" else "Switch to dark mode"
        )
    }
}

@Composable
private fun FullScreenToggleButton(fullScreen: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(
            imageVector = if (fullScreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
            contentDescription = if (fullScreen) "Exit fullscreen" else "Enter fullscreen"
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryScreen(
    state: LibraryUiState,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onGroup: (LibraryGroup) -> Unit,
    onImport: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onOpenSearchResult: (Long, String?) -> Unit,
    onToggleFavorite: (BookListItem) -> Unit,
    onUpdateMetadata: (BookEntity, String, String, Int?, String?, String?, Double?) -> Unit,
    onDeleteBook: (BookEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<BookEntity?>(null) }
    var deleteCandidate by remember { mutableStateOf<BookEntity?>(null) }
    val continueItem = remember(state.books, state.group) {
        if (state.group == LibraryGroup.BOOKS) {
            state.books
                .filter { (it.state?.progress ?: 0.0) in 0.01..0.994 }
                .maxByOrNull { it.state?.lastReadAt ?: it.book.lastOpenedAt ?: it.book.importedAt }
        } else {
            null
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Search library") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onQuery("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                            IconButton(onClick = onSearch) {
                                Icon(Icons.Filled.Search, contentDescription = "Search inside books")
                            }
                        }
                    }
                }
            )
        }
        LibraryFilterRow(selected = state.group, onGroup = onGroup)
        if (state.librarySearchResults.isNotEmpty()) {
            SearchResultsStrip(state.librarySearchResults, onOpenSearchResult)
        }
        if (state.books.isEmpty()) {
            EmptyLibrary(onImport = onImport)
        } else {
            val grouped = groupBooks(state.group, state.books)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
        BookMetadataDialog(
            book = book,
            onDismiss = { editing = null },
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
private fun LibraryFilterRow(
    selected: LibraryGroup,
    onGroup: (LibraryGroup) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LibraryGroup.entries.forEach { group ->
            FilterChip(
                selected = selected == group,
                onClick = { onGroup(group) },
                label = { Text(group.label()) }
            )
        }
    }
}

@Composable
private fun ContinueReadingCard(
    item: BookListItem,
    onOpen: () -> Unit,
) {
    val progress = item.state?.progress ?: 0.0
    var menuOpen by remember(item.book.id) { mutableStateOf(false) }
    Card(
        onClick = onOpen,
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
                Text("Continue", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(item.book.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                LinearProgressIndicator(
                    progress = { progress.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("${(progress * 100).roundToInt()}% read", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyLibrary(onImport: () -> Unit) {
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
private fun BookRow(
    item: BookListItem,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val progress = item.state?.progress ?: 0.0
    var menuOpen by remember(item.book.id) { mutableStateOf(false) }
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BookCoverTile(item.book)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(item.book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                    Text(wordCountLabel(item.book.wordCount), style = MaterialTheme.typography.bodySmall)
                    item.book.genre?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Book actions")
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
private fun SearchResultsStrip(
    results: List<com.xreader.app.data.SearchIndexEntity>,
    onOpenResult: (Long, String?) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Text matches", style = MaterialTheme.typography.labelLarge)
        results.take(4).forEach {
            val snippet = it.body.replace(Regex("\\s+"), " ").trim().take(120)
            Text(
                text = "${it.heading}: $snippet",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenResult(it.bookId, "$SEARCH_UNIT_LOCATOR_PREFIX${it.unitIndex}") },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BookMetadataDialog(
    book: BookEntity,
    onDismiss: () -> Unit,
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
private fun ReaderRoute(
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
private fun ReaderScreen(
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
            tableOfContents = publication.tableOfContents,
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
private fun ReaderTopChrome(
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
private fun ReaderBottomBar(
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
private fun ReaderQuickSettingsDialog(
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
private fun ReaderSearchDialog(
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
private fun DictionaryDialog(
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
private fun NoteDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
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
private fun ReaderNavigationDialog(
    tableOfContents: List<ReaderNavigationItem>,
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
                if (tableOfContents.isEmpty()) {
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

@Composable
private fun AnalyticsRoute(container: AppContainer, onBack: () -> Unit) {
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
private fun NotesRoute(
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
private fun AnnotationRow(
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
private fun EditAnnotationDialog(
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
private fun SettingsRoute(viewModel: SettingsViewModel, onBack: () -> Unit) {
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

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValue: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text("%.2f".format(value))
        }
        Slider(value = value, onValueChange = onValue, valueRange = range)
    }
}

@Composable
private fun StatPill(label: String, value: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ErrorScreen(error: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(error)
            Button(onClick = onBack) { Text("Back") }
        }
    }
}

private fun groupBooks(group: LibraryGroup, books: List<BookListItem>): Map<String, List<BookListItem>> =
    when (group) {
        LibraryGroup.AUTHORS -> books.groupBy { it.book.author }
        LibraryGroup.SERIES -> books.groupBy { it.book.series ?: "No series" }
            .mapValues { (_, items) ->
                items.sortedWith(
                    compareBy<BookListItem> { it.book.seriesIndex ?: Double.MAX_VALUE }
                        .thenBy { it.book.year ?: Int.MAX_VALUE }
                        .thenBy { it.book.sortTitle }
                )
            }
        LibraryGroup.GENRES -> books.groupBy { it.book.genre ?: "No genre" }
        LibraryGroup.YEARS -> books.groupBy { it.book.year?.toString() ?: "No year" }
        else -> mapOf("" to books)
    }.toSortedMap()

private fun LibraryGroup.label(): String =
    name.lowercase().split('_').joinToString(" ") { it.replaceFirstChar(Char::titlecase) }

private fun AnnotationKind.label(): String =
    name.lowercase().replaceFirstChar(Char::titlecase)

private fun String.toAnnotationColor(): Color =
    runCatching { Color(toColorInt()) }
        .getOrDefault(Color(0xFFF2C94C))

private fun ReaderTheme.label(): String =
    if (this == ReaderTheme.OLED) "OLED" else name.lowercase().replaceFirstChar(Char::titlecase)

private const val READER_PATH_WARMUP_DELAY_MS = 3_500L

private fun wordCountLabel(words: Int): String =
    if (words >= 1_000) "${(words / 1_000.0).roundToInt()}k words" else "$words words"

private fun formatDuration(millis: Long): String {
    val minutes = (millis / 60_000).coerceAtLeast(0)
    val hours = minutes / 60
    val remaining = minutes % 60
    return if (hours > 0) "${hours}h ${remaining}m" else "${remaining}m"
}

@Composable
private fun AppSystemBars(activity: Activity?, theme: ReaderTheme) {
    val darkTheme = theme == ReaderTheme.DARK || theme == ReaderTheme.OLED
    SideEffect {
        val window = activity?.window ?: return@SideEffect
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}

@Composable
private fun ReaderSystemBars(
    activity: Activity?,
    theme: ReaderTheme,
    immersive: Boolean,
) {
    val window = activity?.window
    val darkTheme = theme == ReaderTheme.DARK || theme == ReaderTheme.OLED
    SideEffect {
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, !immersive)
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (immersive) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    DisposableEffect(activity) {
        onDispose {
            val activeWindow = activity?.window ?: return@onDispose
            WindowCompat.setDecorFitsSystemWindows(activeWindow, false)
            WindowCompat.getInsetsController(activeWindow, activeWindow.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private fun Context.openDictionarySearch(word: String) {
    val query = Uri.encode("$word definition")
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, "https://www.google.com/search?q=$query".toUri()))
    }
}

private fun Context.shareDictionaryWord(word: String) {
    val sendIntent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, word)
    runCatching {
        startActivity(Intent.createChooser(sendIntent, "Share word"))
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
