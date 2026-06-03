package com.aeonreader.ui.screens.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeonreader.data.cache.ImageCache
import com.aeonreader.data.local.ArticleDao
import com.aeonreader.data.local.HighlightedWordEntity
import com.aeonreader.data.repository.ArticleRepository
import com.aeonreader.data.repository.BookmarkRepository
import com.aeonreader.data.repository.ReadingProgressRepository
import com.aeonreader.data.repository.UserPreferencesRepository
import com.aeonreader.data.word.WordService
import com.aeonreader.domain.Article
import com.aeonreader.domain.ReadingPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.UnknownHostException
import javax.inject.Inject
import kotlin.math.ceil

    sealed interface ArticleUiState {
        data object Loading : ArticleUiState
        data class Success(
            val article: Article,
            val isBookmarked: Boolean,
            val readingProgress: Int?
        ) : ArticleUiState
        data class Error(val message: String) : ArticleUiState
    }

    sealed interface DefinitionState {
        data class Loading(val word: String) : DefinitionState
        data class Result(val word: String, val definition: String) : DefinitionState
    }

@HiltViewModel
class ArticleViewModel @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val wordService: WordService,
    private val articleDao: ArticleDao,
    val imageCache: ImageCache
) : ViewModel() {

    private val _uiState = MutableStateFlow<ArticleUiState>(ArticleUiState.Loading)
    val uiState: StateFlow<ArticleUiState> = _uiState.asStateFlow()

    private val _definition = MutableStateFlow<DefinitionState?>(null)
    val definition: StateFlow<DefinitionState?> = _definition.asStateFlow()

    private val _highlightedWords = MutableStateFlow<Set<String>>(emptySet())
    val highlightedWords: StateFlow<Set<String>> = _highlightedWords.asStateFlow()

    val readingPrefs: StateFlow<ReadingPreferences> = userPreferencesRepository.readingPreferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReadingPreferences())



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
                        readingProgress = progress?.lastBlockIndex
                    )
                    loadHighlightedWords(url)
                },
                onFailure = { error ->
                    val cached = articleRepository.getCachedArticle(url)
                    if (cached != null) {
                        val progress = readingProgressRepository.getProgress(url)
                        _uiState.value = ArticleUiState.Success(
                            article = cached,
                            isBookmarked = false,
                            readingProgress = progress?.lastBlockIndex
                        )
                        loadHighlightedWords(url)
                    } else {
                        val friendlyMessage = when {
                            error is UnknownHostException -> "No internet connection.\nDownload articles while online to read them offline."
                            else -> "Something went wrong.\nPlease try again."
                        }
                        _uiState.value = ArticleUiState.Error(friendlyMessage)
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
                    description = null,
                    author = state.article.author,
                    category = state.article.category,
                    heroImageUrl = state.article.heroImageUrl,
                    estimatedReadingTimeMinutes = ceil(state.article.wordCount / 200.0).toInt().coerceAtLeast(1),
                    cachedAt = null
                )
                if (state.isBookmarked) {
                    bookmarkRepository.removeBookmark(state.article.url)
                } else {
                    bookmarkRepository.addBookmark(summary)
                }
                _uiState.value = state.copy(isBookmarked = !state.isBookmarked)
            }
        }
    }

    fun updateProgress(blockIndex: Int) {
        val state = _uiState.value
        if (state !is ArticleUiState.Success) return

        val totalBlocks = state.article.bodyBlocks.size
        val lastBlock = totalBlocks - 1
        if (blockIndex >= lastBlock) {
            viewModelScope.launch {
                readingProgressRepository.saveProgress(state.article.url, totalBlocks, totalBlocks)
            }
            return
        }

        viewModelScope.launch {
            readingProgressRepository.saveProgress(state.article.url, blockIndex, state.article.bodyBlocks.size)
        }
    }

    fun lookupWord(word: String) {
        val clean = word.trim().lowercase().trimEnd('.', ',', '!', '?', ';', ':')
        viewModelScope.launch {
            _definition.value = DefinitionState.Loading(clean)
            val definition = wordService.getDefinition(clean)
            _definition.value = DefinitionState.Result(clean, definition)
        }
    }

    fun dismissDefinition() {
        _definition.value = null
    }

    private fun loadHighlightedWords(url: String) {
        viewModelScope.launch {
            articleDao.getHighlightedWords(url).collect { words ->
                _highlightedWords.value = words.toSet()
            }
        }
    }

    fun toggleHighlightWord(word: String) {
        val state = _uiState.value
        if (state !is ArticleUiState.Success) return
        val clean = word.trim().lowercase().trimEnd('.', ',', '!', '?', ';', ':')
        viewModelScope.launch {
            if (clean in _highlightedWords.value) {
                articleDao.removeHighlightedWord(state.article.url, clean)
            } else {
                articleDao.upsertHighlightedWord(
                    HighlightedWordEntity(articleUrl = state.article.url, word = clean)
                )
            }
        }
    }
}
