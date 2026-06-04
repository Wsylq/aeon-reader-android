package com.aeonreader.ui.screens.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.aeonreader.data.local.ArticleSummaryEntity
import com.aeonreader.data.repository.ArticleRepository
import com.aeonreader.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface FeedUiState {
    data object Loading : FeedUiState
    data class Success(
        val articles: Flow<PagingData<ArticleSummaryEntity>>,
        val categories: List<String>,
        val selectedCategory: String
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

    private val _cachedUrls = MutableStateFlow<Set<String>>(emptySet())
    val cachedUrls: StateFlow<Set<String>> = _cachedUrls.asStateFlow()

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private var categories: List<String> = emptyList()
    private var selectedCategory: String = "all"
    private var pagingFlow: Flow<PagingData<ArticleSummaryEntity>>? = null
    private var combineJob: Job? = null

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

    /**
     * Rebuilds the paging flow for [category] and restarts the network/cache
     * status observer. Does NOT touch [_uiState] — callers are responsible for
     * updating state before/after this call so that Loading / Success sequencing
     * is controlled at the call site.
     */
    private fun loadFeed(category: String) {
        pagingFlow = articleRepository.getFeedPager(
            if (category == "all") null else category
        ).cachedIn(viewModelScope)

        combineJob?.cancel()
        combineJob = viewModelScope.launch {
            launch {
                articleRepository.observeNetworkStatus()
                    .debounce(300)
                    .distinctUntilChanged()
                    .collect { isOnline -> _isOffline.value = !isOnline }
            }
            launch {
                articleRepository.observeCachedArticleUrls()
                    .distinctUntilChanged()
                    .collect { urls -> _cachedUrls.value = urls }
            }
        }
    }

    private fun emitCurrentState() {
        val flow = pagingFlow
        if (flow != null) {
            val current = _uiState.value
            // Guard against redundant emissions: only update state if articles, categories,
            // or selectedCategory has actually changed. Flow references are compared with
            // referential equality (===) because Flow does not implement structural equality.
            if (current is FeedUiState.Success &&
                current.articles === flow &&
                current.categories == categories &&
                current.selectedCategory == selectedCategory
            ) {
                return
            }
            _uiState.value = FeedUiState.Success(
                articles = flow,
                categories = categories,
                selectedCategory = selectedCategory
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
        // Emit the updated state immediately so the new articles flow reference is
        // reflected in uiState right away (without waiting for a network/cache tick).
        emitCurrentState()
    }
}
