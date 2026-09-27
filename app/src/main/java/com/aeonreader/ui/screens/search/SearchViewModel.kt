package com.aeonreader.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeonreader.data.network.AeonScraper
import com.aeonreader.data.repository.ArticleRepository
import com.aeonreader.domain.ArticleSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState

    /**
     * First search on a fresh install: the archive index is being built from
     * Aeon's ~120 feeds, which is what makes older essays findable at all.
     */
    data class Indexing(val done: Int, val total: Int) : SearchUiState
    data class Success(val results: List<ArticleSummary>) : SearchUiState
    data object Empty : SearchUiState
    data class Error(val message: String) : SearchUiState
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val scraper: AeonScraper,
    private val articleRepository: ArticleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var currentQuery: String = ""

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _uiState.value = SearchUiState.Idle
            return
        }
        if (trimmed.length > 200) return

        currentQuery = trimmed
        _uiState.value = SearchUiState.Loading

        viewModelScope.launch {
            // 1. Anything the user has already read, matched on full body text.
            //    Instant, and the only thing that finds a word that appears only
            //    in an essay's body rather than its title or dek.
            val cached = runCatching { articleRepository.searchCached(trimmed) }.getOrDefault(emptyList())

            // 2. The local archive index. On a fresh install this is empty, so
            //    build it once (and at most daily after that) before searching
            //    it -- the feeds hold the whole back catalogue, not just the
            //    newest 20 per section.
            val indexEmpty = runCatching { articleRepository.archiveIndexSize() }.getOrDefault(0) == 0
            if (indexEmpty) {
                _uiState.value = SearchUiState.Indexing(0, 0)
                articleRepository.refreshArchiveIndex(force = false) { done, total ->
                    _uiState.value = SearchUiState.Indexing(done, total)
                }
            }
            val archived = runCatching { articleRepository.searchArchive(trimmed) }.getOrDefault(emptyList())

            val local = (archived + cached).distinctBy { it.url }
            if (local.isNotEmpty()) {
                _uiState.value = SearchUiState.Success(local)
            }

            // 3. The live section feeds, so anything published in the last few
            //    minutes shows up without waiting for a daily index refresh.
            scraper.search(trimmed, 1).fold(
                onSuccess = { remote ->
                    val merged = (local + remote).distinctBy { it.url }
                    _uiState.value = if (merged.isEmpty()) {
                        if (local.isEmpty()) SearchUiState.Empty else SearchUiState.Success(local)
                    } else {
                        SearchUiState.Success(merged)
                    }
                },
                onFailure = { error ->
                    if (local.isNotEmpty()) return@fold
                    _uiState.value = SearchUiState.Error(
                        error.message ?: "Search failed"
                    )
                }
            )
        }
    }

    /**
     * Rebuilds the index on demand. Exposed so the UI can offer a "refresh
     * index" action after a failed or stale build rather than leaving the user
     * stuck with a one-week-old index.
     */
    fun refreshIndex() {
        val trimmed = currentQuery
        _uiState.value = SearchUiState.Indexing(0, 0)
        viewModelScope.launch {
            articleRepository.refreshArchiveIndex(force = true) { done, total ->
                _uiState.value = SearchUiState.Indexing(done, total)
            }
            if (trimmed.isNotBlank()) search(trimmed) else _uiState.value = SearchUiState.Idle
        }
    }

    fun retry() {
        if (currentQuery.isNotBlank()) {
            search(currentQuery)
        }
    }
}
