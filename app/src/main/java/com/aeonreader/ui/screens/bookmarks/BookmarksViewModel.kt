package com.aeonreader.ui.screens.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeonreader.data.repository.BookmarkRepository
import com.aeonreader.data.repository.ReadingProgressRepository
import com.aeonreader.domain.Bookmark
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface BookmarksUiState {
    data object Loading : BookmarksUiState
    data class Success(val bookmarks: List<Bookmark>) : BookmarksUiState
    data class Error(val message: String) : BookmarksUiState
}

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val readingProgressRepository: ReadingProgressRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<BookmarksUiState>(BookmarksUiState.Loading)
    val uiState: StateFlow<BookmarksUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            bookmarkRepository.observeBookmarks().collect { bookmarks ->
                val enriched = bookmarks.map { bookmark ->
                    val progress = readingProgressRepository.getProgress(bookmark.articleUrl)
                    if (progress != null && progress.totalBlocks > 0) {
                        val percent = progress.lastBlockIndex.toFloat() / progress.totalBlocks.toFloat()
                        bookmark.copy(progressPercent = percent)
                    } else bookmark
                }
                _uiState.value = BookmarksUiState.Success(enriched)
            }
        }
    }

    fun refreshProgress() {
        val current = _uiState.value
        if (current is BookmarksUiState.Success) {
            viewModelScope.launch {
                val enriched = current.bookmarks.map { bookmark ->
                    val progress = readingProgressRepository.getProgress(bookmark.articleUrl)
                    if (progress != null && progress.totalBlocks > 0) {
                        val percent = progress.lastBlockIndex.toFloat() / progress.totalBlocks.toFloat()
                        bookmark.copy(progressPercent = percent)
                    } else bookmark
                }
                _uiState.value = BookmarksUiState.Success(enriched)
            }
        }
    }

    fun deleteBookmark(articleUrl: String) {
        viewModelScope.launch {
            val result = bookmarkRepository.removeBookmark(articleUrl)
            if (result.isFailure) {
                _uiState.value = BookmarksUiState.Error(
                    result.exceptionOrNull()?.message ?: "Failed to delete bookmark"
                )
            }
        }
    }
}
