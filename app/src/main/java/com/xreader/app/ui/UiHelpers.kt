@file:OptIn(ExperimentalMaterial3Api::class)

package com.xreader.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.xreader.app.data.AnnotationKind
import com.xreader.app.data.BookEntity
import com.xreader.app.data.ReaderTheme
import com.xreader.app.settings.LibrarySort
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun LibraryBottomBar(
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
internal fun ThemeToggleButton(theme: ReaderTheme, onClick: () -> Unit) {
    val dark = theme == ReaderTheme.DARK || theme == ReaderTheme.OLED
    val label = if (dark) "Switch to light mode" else "Switch to dark mode"
    TooltipIconButton(label = label, onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(
            imageVector = if (dark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
            contentDescription = null
        )
    }
}

@Composable
internal fun FullScreenToggleButton(fullScreen: Boolean, onClick: () -> Unit) {
    val label = if (fullScreen) "Exit fullscreen" else "Enter fullscreen"
    TooltipIconButton(label = label, onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(
            imageVector = if (fullScreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
            contentDescription = null
        )
    }
}

@Composable
internal fun TooltipIconButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState()
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.semantics { contentDescription = label }
        ) {
            icon()
        }
    }
}

@Composable
internal fun SettingSlider(
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
internal fun StatPill(label: String, value: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun ErrorScreen(error: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(error)
            Button(onClick = onBack) { Text("Back") }
        }
    }
}

internal fun groupBooks(
    group: LibraryGroup,
    books: List<BookListItem>,
    sort: LibrarySort,
): Map<String, List<BookListItem>> =
    when (group) {
        LibraryGroup.AUTHORS -> books.groupBy { it.book.author }
            .mapValues { (_, items) -> items.sortedForLibrary(sort) }
            .sortedLibraryGroups(sort)
        LibraryGroup.SERIES -> books.groupBy { it.book.series ?: NO_SERIES_LABEL }
            .mapValues { (_, items) -> items.sortedForLibrary(sort) }
            .sortedLibraryGroups(sort, emptyLabel = NO_SERIES_LABEL)
        LibraryGroup.GENRES -> books.groupBy { it.book.genre ?: NO_GENRE_LABEL }
            .mapValues { (_, items) -> items.sortedForLibrary(sort) }
            .sortedLibraryGroups(sort, emptyLabel = NO_GENRE_LABEL)
        LibraryGroup.YEARS -> books.groupBy { it.book.year?.toString() ?: NO_YEAR_LABEL }
            .mapValues { (_, items) -> items.sortedForLibrary(sort) }
            .sortedYearGroups(sort)
        LibraryGroup.COLLECTIONS -> books
            .flatMap { item -> item.collections.map { collection -> collection.name to item } }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .mapValues { (_, items) -> items.distinctBy { it.book.id }.sortedForLibrary(sort) }
            .sortedLibraryGroups(sort)
        else -> mapOf("" to books.sortedForLibrary(sort))
    }

internal fun List<BookListItem>.sortedForLibrary(sort: LibrarySort): List<BookListItem> =
    when (sort) {
        LibrarySort.RECENT -> sortedWith(
            compareByDescending<BookListItem> { it.libraryRecentTimestamp() }
                .thenBy { it.book.sortTitle.lowercase() }
        )
        LibrarySort.TITLE -> sortedWith(
            compareBy<BookListItem> { it.book.sortTitle.lowercase() }
                .thenBy { it.book.author.lowercase() }
        )
        LibrarySort.AUTHOR -> sortedWith(
            compareBy<BookListItem> { it.book.author.lowercase() }
                .thenBy { it.book.sortTitle.lowercase() }
        )
        LibrarySort.PROGRESS -> sortedWith(
            compareByDescending<BookListItem> { it.displayLibraryProgress() }
                .thenBy { it.book.sortTitle.lowercase() }
        )
        LibrarySort.SERIES -> sortedWith(
            compareBy<BookListItem> { it.book.series?.lowercase() ?: it.book.sortTitle.lowercase() }
                .thenBy { it.book.seriesIndex ?: Double.MAX_VALUE }
                .thenBy { it.book.year ?: Int.MAX_VALUE }
                .thenBy { it.book.sortTitle.lowercase() }
        )
    }

private fun Map<String, List<BookListItem>>.sortedLibraryGroups(
    sort: LibrarySort,
    emptyLabel: String? = null,
): Map<String, List<BookListItem>> =
    entries
        .sortedWith { left, right ->
            val leftEmpty = if (left.key == emptyLabel) 1 else 0
            val rightEmpty = if (right.key == emptyLabel) 1 else 0
            if (leftEmpty != rightEmpty) {
                leftEmpty.compareTo(rightEmpty)
            } else {
                sort.groupComparator().compare(left, right)
            }
        }
        .associateTo(LinkedHashMap()) { it.key to it.value }

private fun Map<String, List<BookListItem>>.sortedYearGroups(sort: LibrarySort): Map<String, List<BookListItem>> =
    entries
        .sortedWith { left, right ->
            val leftEmpty = if (left.key == NO_YEAR_LABEL) 1 else 0
            val rightEmpty = if (right.key == NO_YEAR_LABEL) 1 else 0
            if (leftEmpty != rightEmpty) {
                leftEmpty.compareTo(rightEmpty)
            } else {
                val sorted = sort.yearGroupComparator().compare(left, right)
                if (sorted != 0) sorted else left.key.compareTo(right.key)
            }
        }
        .associateTo(LinkedHashMap()) { it.key to it.value }

private fun LibrarySort.groupComparator(): Comparator<Map.Entry<String, List<BookListItem>>> =
    when (this) {
        LibrarySort.RECENT -> compareByDescending<Map.Entry<String, List<BookListItem>>> {
            it.value.maxOfOrNull { item -> item.libraryRecentTimestamp() } ?: Long.MIN_VALUE
        }.thenBy { it.key.lowercase() }
        LibrarySort.PROGRESS -> compareByDescending<Map.Entry<String, List<BookListItem>>> {
            it.value.map { item -> item.displayLibraryProgress() }.average().takeUnless(Double::isNaN) ?: 0.0
        }.thenBy { it.key.lowercase() }
        else -> compareBy { it.key.lowercase() }
    }

private fun LibrarySort.yearGroupComparator(): Comparator<Map.Entry<String, List<BookListItem>>> =
    when (this) {
        LibrarySort.RECENT -> compareByDescending<Map.Entry<String, List<BookListItem>>> {
            it.value.maxOfOrNull { item -> item.libraryRecentTimestamp() } ?: Long.MIN_VALUE
        }.thenByDescending { it.key.toIntOrNull() ?: Int.MIN_VALUE }
        LibrarySort.PROGRESS -> compareByDescending<Map.Entry<String, List<BookListItem>>> {
            it.value.map { item -> item.displayLibraryProgress() }.average().takeUnless(Double::isNaN) ?: 0.0
        }.thenByDescending { it.key.toIntOrNull() ?: Int.MIN_VALUE }
        else -> compareByDescending { it.key.toIntOrNull() ?: Int.MIN_VALUE }
    }

private const val NO_SERIES_LABEL = "No series"
private const val NO_GENRE_LABEL = "No genre"
private const val NO_YEAR_LABEL = "No year"

internal fun BookListItem.rawLibraryProgress(): Double =
    (state?.progress ?: 0.0).coerceIn(0.0, 1.0)

internal fun BookListItem.displayLibraryProgress(): Double =
    if (book.finished) 1.0 else rawLibraryProgress()

internal fun BookListItem.libraryRecentTimestamp(): Long =
    state?.lastReadAt ?: book.lastOpenedAt ?: book.importedAt

internal fun BookListItem.isLibraryFinished(): Boolean =
    book.finished || rawLibraryProgress() >= 0.995

internal fun BookListItem.isLibraryInProgress(): Boolean =
    !isLibraryFinished() && rawLibraryProgress() in 0.01..0.994

internal fun BookListItem.isLibraryUnread(): Boolean =
    !isLibraryFinished() && rawLibraryProgress() <= 0.01

internal data class SeriesNextRecommendation(
    val series: String,
    val previous: BookListItem,
    val next: BookListItem,
)

internal fun recommendNextSeriesBook(books: List<BookListItem>): SeriesNextRecommendation? =
    books
        .asSequence()
        .filter { !it.book.series.isNullOrBlank() }
        .groupBy { it.book.series.orEmpty().normalizedSeriesKey() }
        .values
        .asSequence()
        .filter { it.size > 1 }
        .flatMap { items ->
            val sortedItems = items.sortedWith(seriesReadingOrderComparator)
            sortedItems.asSequence().mapIndexedNotNull { index, item ->
                if (!item.isLibraryFinished()) {
                    null
                } else {
                    sortedItems
                        .drop(index + 1)
                        .firstOrNull { !it.isLibraryFinished() }
                        ?.let { next ->
                            SeriesNextRecommendation(
                                series = item.book.series.orEmpty().trim(),
                                previous = item,
                                next = next
                            )
                        }
                }
            }
        }
        .sortedWith(
            compareByDescending<SeriesNextRecommendation> { it.previous.libraryRecentTimestamp() }
                .thenBy { it.series.lowercase(Locale.US) }
                .thenBy { it.next.book.seriesIndex ?: Double.MAX_VALUE }
                .thenBy { it.next.book.year ?: Int.MAX_VALUE }
                .thenBy { it.next.book.sortTitle.lowercase(Locale.US) }
                .thenBy { it.next.book.id }
        )
        .firstOrNull()

private val seriesReadingOrderComparator: Comparator<BookListItem> =
    compareBy<BookListItem> { it.book.seriesIndex ?: Double.MAX_VALUE }
        .thenBy { it.book.year ?: Int.MAX_VALUE }
        .thenBy { it.book.sortTitle.lowercase(Locale.US) }
        .thenBy { it.book.id }

private fun String.normalizedSeriesKey(): String =
    trim()
        .replace(Regex("\\s+"), " ")
        .lowercase(Locale.US)

internal fun LibraryGroup.label(): String =
    name.lowercase().split('_').joinToString(" ") { it.replaceFirstChar(Char::titlecase) }

internal fun LibrarySort.label(): String =
    when (this) {
        LibrarySort.RECENT -> "Recent first"
        LibrarySort.TITLE -> "Title"
        LibrarySort.AUTHOR -> "Author"
        LibrarySort.PROGRESS -> "Progress"
        LibrarySort.SERIES -> "Series"
    }

internal fun com.xreader.app.settings.LibraryDensity.label(): String =
    name.lowercase().replaceFirstChar(Char::titlecase)

internal fun AnnotationKind.label(): String =
    name.lowercase().replaceFirstChar(Char::titlecase)

internal fun String.toAnnotationColor(): Color =
    runCatching { Color(toColorInt()) }
        .getOrDefault(Color(0xFFF2C94C))

@Composable
internal fun AnnotationColorSwatch(
    hex: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(14.dp),
        shape = RoundedCornerShape(8.dp),
        color = hex.toAnnotationColor()
    ) {}
}

internal fun ReaderTheme.label(): String =
    if (this == ReaderTheme.OLED) "OLED" else name.lowercase().replaceFirstChar(Char::titlecase)

internal const val READER_SERVICE_WARMUP_DELAY_MS = 900L
internal const val READER_WEBVIEW_WARMUP_DELAY_MS = 3_500L

internal fun wordCountLabel(words: Int): String =
    if (words >= 1_000) "${(words / 1_000.0).roundToInt()}k words" else "$words words"

internal fun bookFormatLabel(book: BookEntity): String =
    when (book.sourceExtension.lowercase(Locale.US)) {
        "txt" -> "TXT"
        "cbz" -> "CBZ"
        "fb2", "fb2.zip" -> "FB2"
        "rtf" -> "RTF"
        "odt" -> "ODT"
        else -> book.format.name
    }

internal fun bookLengthLabel(book: BookEntity): String =
    if (book.wordCount <= 0 && book.pageCount != null) {
        "${book.pageCount} ${if (book.pageCount == 1) "page" else "pages"}"
    } else {
        wordCountLabel(book.wordCount)
    }

internal fun formatDuration(millis: Long): String {
    val minutes = (millis / 60_000).coerceAtLeast(0)
    val hours = minutes / 60
    val remaining = minutes % 60
    return if (hours > 0) "${hours}h ${remaining}m" else "${remaining}m"
}

@Composable
internal fun AppSystemBars(activity: Activity?, theme: ReaderTheme) {
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
internal fun ReaderSystemBars(
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

internal fun Context.openDictionarySearch(word: String) {
    val query = Uri.encode("$word definition")
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, "https://www.google.com/search?q=$query".toUri()))
    }
}

internal fun Context.shareDictionaryWord(word: String) {
    val sendIntent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, word)
    runCatching {
        startActivity(Intent.createChooser(sendIntent, "Share word"))
    }
}

internal tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
