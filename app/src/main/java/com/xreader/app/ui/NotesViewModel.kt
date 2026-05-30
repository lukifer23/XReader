package com.xreader.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xreader.app.AppContainer
import com.xreader.app.annotations.AnnotationTagSummary
import com.xreader.app.annotations.summarizeAnnotationTags
import com.xreader.app.annotations.tagMatches
import com.xreader.app.data.AnnotationEntity
import com.xreader.app.data.AnnotationKind
import com.xreader.app.data.BookEntity
import com.xreader.app.settings.ReaderHighlightColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoteListItem(
    val annotation: AnnotationEntity,
    val book: BookEntity?,
)

data class NotesUiState(
    val query: String = "",
    val kind: AnnotationKind? = null,
    val selectedTag: String? = null,
    val tagOptions: List<AnnotationTagSummary> = emptyList(),
    val notes: List<NoteListItem> = emptyList(),
    val exporting: Boolean = false,
    val message: String? = null,
)

class NotesViewModel(container: AppContainer) : ViewModel() {
    private val annotationRepository = container.annotationRepository
    private val annotationBackupService = container.annotationBackupService
    private val query = MutableStateFlow("")
    private val kind = MutableStateFlow<AnnotationKind?>(null)
    private val selectedTag = MutableStateFlow<String?>(null)
    private val exportState = MutableStateFlow(NotesExportState())

    private data class NotesExportState(
        val exporting: Boolean = false,
        val message: String? = null,
    )

    val uiState: StateFlow<NotesUiState> =
        combine(
            container.annotationRepository.observeAllAnnotations(),
            container.libraryRepository.observeBooks(""),
            query,
            kind,
            selectedTag
        ) { annotations, books, currentQuery, currentKind, currentTag ->
            val booksById = books.associateBy { it.id }
            buildNotesUiState(
                annotations = annotations,
                booksById = booksById,
                currentQuery = currentQuery,
                currentKind = currentKind,
                currentTag = currentTag
            )
        }
            .combine(exportState) { notes, export ->
                notes.copy(exporting = export.exporting, message = export.message)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun setKind(value: AnnotationKind?) {
        kind.value = value
    }

    fun setSelectedTag(value: String?) {
        selectedTag.value = value?.trim()?.ifBlank { null }
    }

    fun updateNote(annotation: AnnotationEntity, note: String, color: String, tags: String) {
        viewModelScope.launch {
            annotationRepository.updateNote(
                annotation = annotation,
                note = note,
                color = if (annotation.kind == AnnotationKind.HIGHLIGHT) {
                    ReaderHighlightColor.normalized(color)
                } else {
                    annotation.color
                },
                tags = tags
            )
        }
    }

    fun deleteAnnotation(id: Long) {
        viewModelScope.launch {
            annotationRepository.deleteAnnotation(id)
        }
    }

    fun exportMarkdown(uri: Uri) {
        if (exportState.value.exporting) return
        viewModelScope.launch {
            exportState.update { it.copy(exporting = true, message = null) }
            val message = runCatching { annotationBackupService.exportMarkdownTo(uri) }
                .fold(
                    onSuccess = {
                        "Exported ${it.annotations} ${if (it.annotations == 1) "annotation" else "annotations"} and ${it.bookmarks} ${if (it.bookmarks == 1) "bookmark" else "bookmarks"} to Markdown"
                    },
                    onFailure = { it.message ?: "Markdown export failed" }
                )
            exportState.update { it.copy(exporting = false, message = message) }
        }
    }

    fun clearMessage() {
        exportState.update { it.copy(message = null) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    NotesViewModel(container) as T
            }
    }
}

internal fun buildNotesUiState(
    annotations: List<AnnotationEntity>,
    booksById: Map<Long, BookEntity>,
    currentQuery: String,
    currentKind: AnnotationKind?,
    currentTag: String?,
): NotesUiState {
    val trimmedQuery = currentQuery.trim()
    val base = annotations
        .asSequence()
        .filter { currentKind == null || it.kind == currentKind }
        .filter { annotation ->
            if (trimmedQuery.isBlank()) {
                true
            } else {
                val haystack = listOf(
                    annotation.quote,
                    annotation.note,
                    annotation.tags,
                    booksById[annotation.bookId]?.title.orEmpty(),
                    booksById[annotation.bookId]?.author.orEmpty()
                ).joinToString(" ")
                haystack.contains(trimmedQuery, ignoreCase = true)
            }
        }
        .toList()
    val tags = summarizeAnnotationTags(base.map { it.tags })
    val resolvedTag = currentTag?.let { selected ->
        tags.firstOrNull { it.label.equals(selected, ignoreCase = true) }?.label
    }
    val filtered = base
        .asSequence()
        .filter { annotation -> tagMatches(annotation.tags, resolvedTag) }
        .map { NoteListItem(it, booksById[it.bookId]) }
        .toList()
    return NotesUiState(
        query = trimmedQuery,
        kind = currentKind,
        selectedTag = resolvedTag,
        tagOptions = tags,
        notes = filtered
    )
}
