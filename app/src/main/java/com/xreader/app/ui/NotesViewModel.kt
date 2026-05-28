package com.xreader.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xreader.app.AppContainer
import com.xreader.app.data.AnnotationEntity
import com.xreader.app.data.AnnotationKind
import com.xreader.app.data.BookEntity
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
)

class NotesViewModel(container: AppContainer) : ViewModel() {
    private val annotationRepository = container.annotationRepository
    private val query = MutableStateFlow("")
    private val kind = MutableStateFlow<AnnotationKind?>(null)

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
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun setKind(value: AnnotationKind?) {
        kind.value = value
    }

    fun updateNote(annotation: AnnotationEntity, note: String) {
        viewModelScope.launch {
            annotationRepository.updateNote(annotation, note)
        }
    }

    fun deleteAnnotation(id: Long) {
        viewModelScope.launch {
            annotationRepository.deleteAnnotation(id)
        }
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
