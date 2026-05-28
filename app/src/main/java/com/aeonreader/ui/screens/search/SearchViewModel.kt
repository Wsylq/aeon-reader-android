package com.aeonreader.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeonreader.data.network.AeonScraper
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
    data class Success(val results: List<ArticleSummary>) : SearchUiState
    data object Empty : SearchUiState
    data class Error(val message: String) : SearchUiState
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val scraper: AeonScraper
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
            scraper.search(trimmed, 1).fold(
                onSuccess = { results ->
                    _uiState.value = if (results.isEmpty()) {
                        SearchUiState.Empty
                    } else {
                        SearchUiState.Success(results)
                    }
                },
                onFailure = { error ->
                    _uiState.value = SearchUiState.Error(
                        error.message ?: "Search failed"
                    )
                }
            )
        }
    }

    fun retry() {
        if (currentQuery.isNotBlank()) {
            search(currentQuery)
        }
    }
}
