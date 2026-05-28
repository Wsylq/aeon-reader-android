package com.aeonreader.ui.screens.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.aeonreader.data.local.ArticleSummaryEntity
import com.aeonreader.data.repository.ArticleRepository
import com.aeonreader.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface FeedUiState {
    data object Loading : FeedUiState
    data class Success(
        val articles: Flow<PagingData<ArticleSummaryEntity>>,
        val categories: List<String>,
        val selectedCategory: String,
        val isOffline: Boolean
    ) : FeedUiState
    data class Error(val message: String) : FeedUiState
}

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<FeedUiState>(FeedUiState.Loading)
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private var categories: List<String> = emptyList()
    private var selectedCategory: String = "all"
    private var isOffline: Boolean = false
    private var pagingFlow: Flow<PagingData<ArticleSummaryEntity>>? = null

    init {
        loadInitial()
    }

    private fun loadInitial() {
        viewModelScope.launch {
            selectedCategory = try {
                val cat = userPreferencesRepository.selectedCategory.first()
                if (cat.isBlank()) "all" else cat
            } catch (_: Exception) {
                "all"
            }

            loadCategories()
            loadFeed(selectedCategory)
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            val result = articleRepository.getCategories()
            if (result.isSuccess) {
                categories = result.getOrDefault(emptyList())
            }
            emitCurrentState()
        }
    }

    private fun loadFeed(category: String) {
        pagingFlow = articleRepository.getFeedPager(
            if (category == "all") null else category
        ).cachedIn(viewModelScope)

        viewModelScope.launch {
            articleRepository.observeNetworkStatus().collect { isOnline ->
                isOffline = !isOnline
            }
        }

        emitCurrentState()
    }

    private fun emitCurrentState() {
        val flow = pagingFlow
        if (flow != null) {
            _uiState.value = FeedUiState.Success(
                articles = flow,
                categories = categories,
                selectedCategory = selectedCategory,
                isOffline = isOffline
            )
        } else {
            _uiState.value = FeedUiState.Loading
        }
    }

    fun refresh() {
        _uiState.value = FeedUiState.Loading
        loadFeed(selectedCategory)
        loadCategories()
    }

    fun selectCategory(category: String) {
        selectedCategory = category
        viewModelScope.launch {
            userPreferencesRepository.setSelectedCategory(category)
        }
        loadFeed(category)
    }
}
