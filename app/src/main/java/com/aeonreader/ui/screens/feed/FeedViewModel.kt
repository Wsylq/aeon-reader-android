package com.aeonreader.ui.screens.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.aeonreader.data.local.ArticleSummaryEntity
import com.aeonreader.data.repository.ArticleRepository
import com.aeonreader.data.repository.BookmarkRepository
import com.aeonreader.data.repository.UserPreferencesRepository
import com.aeonreader.domain.ArticleSummary
import com.aeonreader.domain.FeedLayout
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface FeedUiState {
    data object Loading : FeedUiState
    data class Success(
        val articles: Flow<PagingData<ArticleSummary>>,
        val categories: List<String>,
        val selectedCategory: String
    ) : FeedUiState
    data class Error(val message: String) : FeedUiState
}

@OptIn(kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class FeedViewModel @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val bookmarkRepository: BookmarkRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<FeedUiState>(FeedUiState.Loading)
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private val _cachedUrls = MutableStateFlow<Set<String>>(emptySet())
    val cachedUrls: StateFlow<Set<String>> = _cachedUrls.asStateFlow()

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val _feedLayout = MutableStateFlow(FeedLayout.LIST)
    val feedLayout: StateFlow<FeedLayout> = _feedLayout.asStateFlow()

    private val _bookmarkedUrls = MutableStateFlow<Set<String>>(emptySet())
    val bookmarkedUrls: StateFlow<Set<String>> = _bookmarkedUrls.asStateFlow()

    private var categories: List<String> = emptyList()
    private var selectedCategory: String = "all"
    private var pagingFlow: Flow<PagingData<ArticleSummary>>? = null
    private var combineJob: Job? = null

    init {
        loadInitial()
        observeBookmarks()
    }

    private fun observeBookmarks() {
        viewModelScope.launch {
            bookmarkRepository.observeBookmarks()
                .map { bookmarks -> bookmarks.map { it.articleUrl }.toSet() }
                .collect { urls -> _bookmarkedUrls.value = urls }
        }
    }

    fun toggleBookmark(summary: ArticleSummary) {
        viewModelScope.launch {
            if (_bookmarkedUrls.value.contains(summary.url)) {
                bookmarkRepository.removeBookmark(summary.url)
            } else {
                bookmarkRepository.addBookmark(summary)
            }
        }
    }

    private fun loadInitial() {
        viewModelScope.launch {
            selectedCategory = try {
                val cat = userPreferencesRepository.selectedCategory.first()
                if (cat.isBlank()) "all" else cat
            } catch (_: Exception) {
                "all"
            }

            _feedLayout.value = try {
                userPreferencesRepository.feedLayout.first()
            } catch (_: Exception) { FeedLayout.LIST }

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
        ).map { pagingData ->
            pagingData.map { it.toArticleSummary() }
        }.cachedIn(viewModelScope)

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

    fun toggleLayout() {
        val next = if (_feedLayout.value == FeedLayout.LIST) FeedLayout.GRID else FeedLayout.LIST
        _feedLayout.value = next
        viewModelScope.launch { userPreferencesRepository.setFeedLayout(next) }
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
        emitCurrentState()
    }
}

private fun ArticleSummaryEntity.toArticleSummary(): ArticleSummary {
    return ArticleSummary(
        url = url,
        title = title,
        description = description,
        author = author,
        category = category,
        heroImageUrl = heroImageUrl,
        estimatedReadingTimeMinutes = estimatedReadingTimeMinutes,
        cachedAt = cachedAt
    )
}
