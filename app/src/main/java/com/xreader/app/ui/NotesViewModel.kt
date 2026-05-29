package com.xreader.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xreader.app.AppContainer
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
    val notes: List<NoteListItem> = emptyList(),
    val exporting: Boolean = false,
    val message: String? = null,
)

class NotesViewModel(container: AppContainer) : ViewModel() {
    private val annotationRepository = container.annotationRepository
    private val annotationBackupService = container.annotationBackupService
    private val query = MutableStateFlow("")
    private val kind = MutableStateFlow<AnnotationKind?>(null)
    private val exportState = MutableStateFlow(NotesExportState())

    private data class NotesExportState(
        val exporting: Boolean = false,
        val message: String? = null,
    )

    val uiState: StateFlow<NotesUiState> =
        container.annotationRepository.observeAllAnnotations()
            .combine(container.libraryRepository.observeBooks("")) { annotations, books ->
                annotations to books.associateBy { it.id }
            }
            .combine(query) { (annotations, booksById), currentQuery ->
                Triple(annotations, booksById, currentQuery.trim())
            }
            .combine(kind) { (annotations, booksById, currentQuery), currentKind ->
                val filtered = annotations
                    .asSequence()
                    .filter { currentKind == null || it.kind == currentKind }
                    .filter { annotation ->
                        if (currentQuery.isBlank()) {
                            true
                        } else {
                            val haystack = listOf(
                                annotation.quote,
                                annotation.note,
                                annotation.tags,
                                booksById[annotation.bookId]?.title.orEmpty(),
                                booksById[annotation.bookId]?.author.orEmpty()
                            ).joinToString(" ")
                            haystack.contains(currentQuery, ignoreCase = true)
                        }
                    }
                    .map { NoteListItem(it, booksById[it.bookId]) }
                    .toList()
                NotesUiState(query = currentQuery, kind = currentKind, notes = filtered)
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

    fun updateNote(annotation: AnnotationEntity, note: String, color: String) {
        viewModelScope.launch {
            annotationRepository.updateNote(
                annotation = annotation,
                note = note,
                color = if (annotation.kind == AnnotationKind.HIGHLIGHT) {
                    ReaderHighlightColor.normalized(color)
                } else {
                    annotation.color
                }
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
