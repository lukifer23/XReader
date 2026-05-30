@file:OptIn(ExperimentalMaterial3Api::class)

package com.xreader.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.xreader.app.data.BookEntity
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun BookMetadataDialog(
    book: BookEntity,
    health: BookHealthUiState?,
    repairing: Boolean,
    authorOptions: List<String>,
    genreOptions: List<String>,
    seriesOptions: List<String>,
    onDismiss: () -> Unit,
    onRefreshHealth: () -> Unit,
    onRepairBook: () -> Unit,
    onReplaceCover: () -> Unit,
    onSave: (String, String, Int?, String?, String?, Double?, Boolean) -> Unit,
) {
    var title by remember(book.id) { mutableStateOf(book.title) }
    var author by remember(book.id) { mutableStateOf(book.author) }
    var year by remember(book.id) { mutableStateOf(book.year?.toString().orEmpty()) }
    var genre by remember(book.id) { mutableStateOf(book.genre.orEmpty()) }
    var series by remember(book.id) { mutableStateOf(book.series.orEmpty()) }
    var seriesIndex by remember(book.id) { mutableStateOf(book.seriesIndex?.toString().orEmpty()) }
    var applyToSeries by remember(book.id) { mutableStateOf(false) }
    val canApplyToSeries = series.isNotBlank() || !book.series.isNullOrBlank()
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
                OutlinedTextField(
                    title,
                    { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                MetadataSuggestionField(
                    value = author,
                    onValueChange = { author = it },
                    label = "Author",
                    options = authorOptions
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        year,
                        { year = it.filter(Char::isDigit).take(4) },
                        label = { Text("Year") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    MetadataSuggestionField(
                        value = genre,
                        onValueChange = { genre = it },
                        label = "Genre",
                        options = genreOptions,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetadataSuggestionField(
                        value = series,
                        onValueChange = { series = it },
                        label = "Series",
                        options = seriesOptions,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        seriesIndex,
                        { seriesIndex = it },
                        label = { Text("#") },
                        singleLine = true,
                        modifier = Modifier.width(96.dp)
                    )
                }
                if (canApplyToSeries) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Apply shared metadata to matching books",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = applyToSeries,
                            onCheckedChange = { applyToSeries = it }
                        )
                    }
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
                        seriesIndex.toDoubleOrNull(),
                        applyToSeries
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun MetadataSuggestionField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    options: List<String>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    var fieldWidthDp by remember { mutableStateOf(0.dp) }
    val suggestions = remember(value, options) { metadataSuggestions(value, options) }
    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { size ->
                    fieldWidthDp = with(density) { size.width.toDp() }
                }
        )
        DropdownMenu(
            expanded = expanded && suggestions.isNotEmpty(),
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = fieldWidthDp),
            properties = PopupProperties(focusable = false)
        ) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        onValueChange(suggestion)
                        expanded = false
                    }
                )
            }
        }
    }
}

internal fun metadataSuggestions(
    query: String,
    options: List<String>,
    limit: Int = MaxMetadataSuggestions,
): List<String> {
    if (limit <= 0) return emptyList()
    val exactValue = query.cleanSuggestionValue()
    val queryKey = exactValue.lowercase(Locale.ROOT)
    return options
        .asSequence()
        .map { it.cleanSuggestionValue() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase(Locale.ROOT) }
        .mapNotNull { option ->
            if (option == exactValue) return@mapNotNull null
            val optionKey = option.lowercase(Locale.ROOT)
            val rank = when {
                queryKey.isBlank() -> 2
                optionKey.startsWith(queryKey) -> 0
                optionKey.contains(queryKey) -> 1
                else -> null
            }
            rank?.let { MetadataSuggestion(rank = it, value = option) }
        }
        .sortedWith(
            compareBy<MetadataSuggestion> { it.rank }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.value }
        )
        .take(limit)
        .map { it.value }
        .toList()
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

private data class MetadataSuggestion(
    val rank: Int,
    val value: String,
)

private fun String.cleanSuggestionValue(): String =
    trim().replace(MetadataWhitespaceRegex, " ")

private const val MaxMetadataSuggestions = 6
private val MetadataWhitespaceRegex = Regex("\\s+")
