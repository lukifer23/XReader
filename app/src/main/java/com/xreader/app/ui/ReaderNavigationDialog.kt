@file:OptIn(ExperimentalMaterial3Api::class)

package com.xreader.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xreader.app.annotations.annotationTagsLabel
import com.xreader.app.data.AnnotationEntity
import com.xreader.app.data.BookmarkEntity
import com.xreader.app.reader.ReaderNavigationItem
import java.util.Locale
import kotlin.math.roundToInt

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
    onEditAnnotation: (AnnotationEntity) -> Unit,
) {
    var selectedPane by remember {
        mutableStateOf(
            defaultReaderNavigationPane(
                hasContents = tableOfContentsLoading || tableOfContents.isNotEmpty(),
                hasBookmarks = bookmarks.isNotEmpty(),
                hasAnnotations = annotations.isNotEmpty()
            )
        )
    }
    var query by remember { mutableStateOf("") }
    val filteredContents = remember(tableOfContents, query) { filterReaderNavigationItems(tableOfContents, query) }
    val filteredBookmarks = remember(bookmarks, query) { filterReaderBookmarks(bookmarks, query) }
    val filteredAnnotations = remember(annotations, query) { filterReaderAnnotations(annotations, query) }
    val showFilter = tableOfContents.isNotEmpty() || bookmarks.isNotEmpty() || annotations.isNotEmpty() || query.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Navigate") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 260.dp, max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (showFilter) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Filter navigation") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotBlank()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear navigation filter")
                                }
                            }
                        }
                    )
                }
                PrimaryTabRow(selectedTabIndex = selectedPane.ordinal) {
                    ReaderNavigationPane.entries.forEach { pane ->
                        Tab(
                            selected = selectedPane == pane,
                            onClick = { selectedPane = pane },
                            text = {
                                Text(
                                    pane.displayLabel(
                                        query = query,
                                        contentsCount = filteredContents.size,
                                        bookmarksCount = filteredBookmarks.size,
                                        annotationsCount = filteredAnnotations.size
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    when (selectedPane) {
                        ReaderNavigationPane.CONTENTS -> contentsPaneItems(
                            loading = tableOfContentsLoading,
                            items = filteredContents,
                            filtering = query.isNotBlank(),
                            onJump = onJump
                        )

                        ReaderNavigationPane.BOOKMARKS -> bookmarksPaneItems(
                            bookmarks = filteredBookmarks,
                            filtering = query.isNotBlank(),
                            onJump = onJump,
                            onDeleteBookmark = onDeleteBookmark
                        )

                        ReaderNavigationPane.NOTES -> annotationsPaneItems(
                            annotations = filteredAnnotations,
                            filtering = query.isNotBlank(),
                            onJump = onJump,
                            onDeleteAnnotation = onDeleteAnnotation,
                            onEditAnnotation = onEditAnnotation
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

private fun LazyListScope.contentsPaneItems(
    loading: Boolean,
    items: List<ReaderNavigationItem>,
    filtering: Boolean,
    onJump: (String) -> Unit,
) {
    when {
        loading -> {
            item("contents-loading") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        items.isEmpty() -> {
            item("contents-empty") {
                EmptyNavigationText(
                    if (filtering) "No matching contents." else "No table of contents found."
                )
            }
        }

        else -> {
            items(items = items) { item ->
                ReaderContentRow(item = item, onJump = onJump)
            }
        }
    }
}

private fun LazyListScope.bookmarksPaneItems(
    bookmarks: List<BookmarkEntity>,
    filtering: Boolean,
    onJump: (String) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
) {
    if (bookmarks.isEmpty()) {
        item("bookmarks-empty") {
            EmptyNavigationText(
                if (filtering) "No matching bookmarks." else "No bookmarks yet."
            )
        }
        return
    }
    items(
        items = bookmarks,
        key = { "bookmark-${it.id}" }
    ) { bookmark ->
        ReaderBookmarkRow(bookmark = bookmark, onJump = onJump, onDeleteBookmark = onDeleteBookmark)
    }
}

private fun LazyListScope.annotationsPaneItems(
    annotations: List<AnnotationEntity>,
    filtering: Boolean,
    onJump: (String) -> Unit,
    onDeleteAnnotation: (Long) -> Unit,
    onEditAnnotation: (AnnotationEntity) -> Unit,
) {
    if (annotations.isEmpty()) {
        item("annotations-empty") {
            EmptyNavigationText(
                if (filtering) "No matching notes or highlights." else "No notes or highlights yet."
            )
        }
        return
    }
    items(
        items = annotations,
        key = { "annotation-${it.id}" }
    ) { annotation ->
        ReaderAnnotationRow(
            annotation = annotation,
            onJump = onJump,
            onDeleteAnnotation = onDeleteAnnotation,
            onEditAnnotation = onEditAnnotation
        )
    }
}

@Composable
private fun ReaderContentRow(
    item: ReaderNavigationItem,
    onJump: (String) -> Unit,
) {
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

@Composable
private fun ReaderBookmarkRow(
    bookmark: BookmarkEntity,
    onJump: (String) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
) {
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
                bookmark.progressLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = { onDeleteBookmark(bookmark.id) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete bookmark")
        }
    }
}

@Composable
private fun ReaderAnnotationRow(
    annotation: AnnotationEntity,
    onJump: (String) -> Unit,
    onDeleteAnnotation: (Long) -> Unit,
    onEditAnnotation: (AnnotationEntity) -> Unit,
) {
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
            annotationTagsLabel(annotation.tags).takeIf { it.isNotBlank() }?.let { tags ->
                Text(tags, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = { onEditAnnotation(annotation) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit annotation")
        }
        IconButton(onClick = { onDeleteAnnotation(annotation.id) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete annotation")
        }
    }
}

@Composable
private fun EmptyNavigationText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

internal enum class ReaderNavigationPane(val label: String) {
    CONTENTS("Contents"),
    BOOKMARKS("Bookmarks"),
    NOTES("Notes"),
}

private fun ReaderNavigationPane.displayLabel(
    query: String,
    contentsCount: Int,
    bookmarksCount: Int,
    annotationsCount: Int,
): String {
    if (query.isBlank()) return label
    val count = when (this) {
        ReaderNavigationPane.CONTENTS -> contentsCount
        ReaderNavigationPane.BOOKMARKS -> bookmarksCount
        ReaderNavigationPane.NOTES -> annotationsCount
    }
    return "$label ($count)"
}

internal fun defaultReaderNavigationPane(
    hasContents: Boolean,
    hasBookmarks: Boolean,
    hasAnnotations: Boolean,
): ReaderNavigationPane =
    when {
        hasContents -> ReaderNavigationPane.CONTENTS
        hasBookmarks -> ReaderNavigationPane.BOOKMARKS
        hasAnnotations -> ReaderNavigationPane.NOTES
        else -> ReaderNavigationPane.CONTENTS
    }

internal fun filterReaderNavigationItems(
    items: List<ReaderNavigationItem>,
    query: String,
): List<ReaderNavigationItem> =
    filterByNavigationQuery(items, query) { item ->
        listOf(item.title)
    }

internal fun filterReaderBookmarks(
    bookmarks: List<BookmarkEntity>,
    query: String,
): List<BookmarkEntity> =
    filterByNavigationQuery(bookmarks, query) { bookmark ->
        listOf(bookmark.label, bookmark.progressLabel())
    }

internal fun filterReaderAnnotations(
    annotations: List<AnnotationEntity>,
    query: String,
): List<AnnotationEntity> =
    filterByNavigationQuery(annotations, query) { annotation ->
        listOf(
            annotation.kind.label(),
            annotation.quote,
            annotation.note,
            annotation.tags,
            annotationTagsLabel(annotation.tags)
        )
    }

private fun <T> filterByNavigationQuery(
    items: List<T>,
    query: String,
    searchableText: (T) -> List<String>,
): List<T> {
    val terms = query.navigationQueryTerms()
    if (terms.isEmpty()) return items
    return items.filter { item ->
        val haystack = searchableText(item)
            .joinToString(separator = " ")
            .lowercase(Locale.ROOT)
        terms.all { it in haystack }
    }
}

private fun String.navigationQueryTerms(): List<String> =
    trim()
        .lowercase(Locale.ROOT)
        .split(Regex("\\s+"))
        .map { it.trimStart('#') }
        .filter { it.isNotBlank() }

private fun BookmarkEntity.progressLabel(): String =
    "${(progress * 100).roundToInt()}% read"
