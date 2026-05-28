package com.aeonreader.ui.screens.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeonreader.data.repository.ArticleRepository
import com.aeonreader.data.repository.BookmarkRepository
import com.aeonreader.data.repository.ReadingProgressRepository
import com.aeonreader.data.repository.UserPreferencesRepository
import com.aeonreader.domain.Article
import com.aeonreader.domain.ReadingPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ArticleUiState {
    data object Loading : ArticleUiState
    data class Success(
        val article: Article,
        val isBookmarked: Boolean,
        val readingProgress: Float?
    ) : ArticleUiState
    data class Error(val message: String) : ArticleUiState
}

@HiltViewModel
class ArticleViewModel @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ArticleUiState>(ArticleUiState.Loading)
    val uiState: StateFlow<ArticleUiState> = _uiState.asStateFlow()

    val readingPrefs: StateFlow<ReadingPreferences> = userPreferencesRepository.readingPreferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReadingPreferences())

    private var progressSaveJob: Job? = null

    fun setReadingPrefs(prefs: ReadingPreferences) {
        viewModelScope.launch {
            userPreferencesRepository.setReadingPreferences(prefs)
        }
    }

    fun loadArticle(url: String) {
        viewModelScope.launch {
            _uiState.value = ArticleUiState.Loading

            articleRepository.getArticle(url).fold(
                onSuccess = { article ->
                    val progress = readingProgressRepository.getProgress(url)
                    val isBookmarked = bookmarkRepository.observeBookmarkState(url).first()
                    _uiState.value = ArticleUiState.Success(
                        article = article,
                        isBookmarked = isBookmarked,
                        readingProgress = progress
                    )
                },
                onFailure = { error ->
                    val cached = articleRepository.getCachedArticle(url)
                    if (cached != null) {
                        val progress = readingProgressRepository.getProgress(url)
                        _uiState.value = ArticleUiState.Success(
                            article = cached,
                            isBookmarked = false,
                            readingProgress = progress
                        )
                    } else {
                        _uiState.value = ArticleUiState.Error(
                            error.message ?: "Failed to load article"
                        )
                    }
                }
            )
        }
    }

    fun toggleBookmark() {
        val state = _uiState.value
        if (state is ArticleUiState.Success) {
            viewModelScope.launch {
                val summary = com.aeonreader.domain.ArticleSummary(
                    url = state.article.url,
                    title = state.article.title,
                    author = state.article.author,
                    category = state.article.category,
                    heroImageUrl = state.article.heroImageUrl,
                    estimatedReadingTimeMinutes = state.article.wordCount,
                    cachedAt = null
                )
                if (state.isBookmarked) {
                    bookmarkRepository.removeBookmark(state.article.url)
                } else {
                    bookmarkRepository.addBookmark(summary)
                }
            }
        }
    }

    fun updateProgress(percent: Float) {
        val state = _uiState.value
        if (state !is ArticleUiState.Success) return

        if (percent >= 95f) {
            viewModelScope.launch {
                readingProgressRepository.clearProgress(state.article.url)
            }
            return
        }

        progressSaveJob?.cancel()
        progressSaveJob = viewModelScope.launch {
            delay(2000)
            readingProgressRepository.saveProgress(state.article.url, percent)
        }
    }
}
