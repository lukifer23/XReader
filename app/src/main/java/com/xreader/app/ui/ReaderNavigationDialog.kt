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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Navigate") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 260.dp, max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PrimaryTabRow(selectedTabIndex = selectedPane.ordinal) {
                    ReaderNavigationPane.entries.forEach { pane ->
                        Tab(
                            selected = selectedPane == pane,
                            onClick = { selectedPane = pane },
                            text = { Text(pane.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    when (selectedPane) {
                        ReaderNavigationPane.CONTENTS -> ReaderContentsPane(
                            loading = tableOfContentsLoading,
                            items = tableOfContents,
                            onJump = onJump
                        )

                        ReaderNavigationPane.BOOKMARKS -> ReaderBookmarksPane(
                            bookmarks = bookmarks,
                            onJump = onJump,
                            onDeleteBookmark = onDeleteBookmark
                        )

                        ReaderNavigationPane.NOTES -> ReaderAnnotationsPane(
                            annotations = annotations,
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

@Composable
private fun ReaderContentsPane(
    loading: Boolean,
    items: List<ReaderNavigationItem>,
    onJump: (String) -> Unit,
) {
    when {
        loading -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        }

        items.isEmpty() -> {
            Text("No table of contents found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        else -> {
            items.forEach { item ->
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
    }
}

@Composable
private fun ReaderBookmarksPane(
    bookmarks: List<BookmarkEntity>,
    onJump: (String) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
) {
    if (bookmarks.isEmpty()) {
        Text("No bookmarks yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
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

@Composable
private fun ReaderAnnotationsPane(
    annotations: List<AnnotationEntity>,
    onJump: (String) -> Unit,
    onDeleteAnnotation: (Long) -> Unit,
    onEditAnnotation: (AnnotationEntity) -> Unit,
) {
    if (annotations.isEmpty()) {
        Text("No notes or highlights yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
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
}

internal enum class ReaderNavigationPane(val label: String) {
    CONTENTS("Contents"),
    BOOKMARKS("Bookmarks"),
    NOTES("Notes"),
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
