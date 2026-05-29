@file:OptIn(ExperimentalMaterial3Api::class)

package com.xreader.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xreader.app.data.BookEntity
import kotlin.math.roundToInt

@Composable
internal fun BookMetadataDialog(
    book: BookEntity,
    health: BookHealthUiState?,
    repairing: Boolean,
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
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(author, { author = it }, label = { Text("Author") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        year,
                        { year = it.filter(Char::isDigit).take(4) },
                        label = { Text("Year") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        genre,
                        { genre = it },
                        label = { Text("Genre") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        series,
                        { series = it },
                        label = { Text("Series") },
                        singleLine = true,
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
                            "Apply genre and series to matching books",
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
