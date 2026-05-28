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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.xreader.app.data.AnnotationKind
import com.xreader.app.data.ReaderTheme
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
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(
            imageVector = if (dark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
            contentDescription = if (dark) "Switch to light mode" else "Switch to dark mode"
        )
    }
}

@Composable
internal fun FullScreenToggleButton(fullScreen: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(
            imageVector = if (fullScreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
            contentDescription = if (fullScreen) "Exit fullscreen" else "Enter fullscreen"
        )
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

internal fun groupBooks(group: LibraryGroup, books: List<BookListItem>): Map<String, List<BookListItem>> =
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

internal fun LibraryGroup.label(): String =
    name.lowercase().split('_').joinToString(" ") { it.replaceFirstChar(Char::titlecase) }

internal fun AnnotationKind.label(): String =
    name.lowercase().replaceFirstChar(Char::titlecase)

internal fun String.toAnnotationColor(): Color =
    runCatching { Color(toColorInt()) }
        .getOrDefault(Color(0xFFF2C94C))

internal fun ReaderTheme.label(): String =
    if (this == ReaderTheme.OLED) "OLED" else name.lowercase().replaceFirstChar(Char::titlecase)

internal const val READER_PATH_WARMUP_DELAY_MS = 3_500L

internal fun wordCountLabel(words: Int): String =
    if (words >= 1_000) "${(words / 1_000.0).roundToInt()}k words" else "$words words"

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
