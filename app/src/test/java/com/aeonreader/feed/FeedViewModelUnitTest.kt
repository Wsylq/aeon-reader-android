package com.aeonreader.feed

import androidx.paging.PagingData
import com.aeonreader.data.local.ArticleSummaryEntity
import com.aeonreader.data.repository.ArticleRepository
import com.aeonreader.data.repository.UserPreferencesRepository
import com.aeonreader.domain.Article
import com.aeonreader.domain.ReadingPreferences
import com.aeonreader.domain.ThemeOverride
import com.aeonreader.ui.screens.feed.FeedUiState
import com.aeonreader.ui.screens.feed.FeedViewModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * FeedViewModelUnitTest
 *
 * Unit tests for FeedViewModel correctness after the three performance fixes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelUnitTest : FunSpec({

    val testDispatcher = StandardTestDispatcher()

    beforeSpec {
        Dispatchers.setMain(testDispatcher)
    }

    afterSpec {
        Dispatchers.resetMain()
    }

    // ---------------------------------------------------------------------------
    // Helper: build a FeedViewModel with injectable flows and optional paging factory
    // ---------------------------------------------------------------------------
    fun buildViewModel(
        cachedUrlsFlow: Flow<Set<String>> = flowOf(emptySet()),
        networkStatusFlow: Flow<Boolean> = flowOf(true),
        pagingFlowFactory: (String?) -> Flow<PagingData<ArticleSummaryEntity>> = {
            flowOf(PagingData.empty())
        }
    ): FeedViewModel {
        val fakeRepository = object : ArticleRepository {
            override fun getFeedPager(category: String?): Flow<PagingData<ArticleSummaryEntity>> =
                pagingFlowFactory(category)

            override suspend fun getArticle(url: String): Result<Article> =
                Result.failure(UnsupportedOperationException())

            override suspend fun getCachedArticle(url: String): Article? = null

            override suspend fun getCategories(): Result<List<String>> =
                Result.success(emptyList())

            override fun observeNetworkStatus(): Flow<Boolean> = networkStatusFlow

            override fun observeCachedArticleUrls(): Flow<Set<String>> = cachedUrlsFlow
        }

        val fakePrefsRepository = object : UserPreferencesRepository {
            override val selectedCategory: Flow<String> = flowOf("all")
            override val themeOverride: Flow<ThemeOverride> = flowOf(ThemeOverride.NONE)
            override val readingPreferences: Flow<ReadingPreferences> =
                flowOf(ReadingPreferences())

            override suspend fun setSelectedCategory(category: String) {}
            override suspend fun setThemeOverride(override: ThemeOverride) {}
            override suspend fun setReadingPreferences(prefs: ReadingPreferences) {}
        }

        return FeedViewModel(fakeRepository, fakePrefsRepository)
    }

    // ---------------------------------------------------------------------------
    // Task 8.1 — emitCurrentState() does not emit on offline/cache-only changes
    //
    // After Fix 1, network-status changes update _isOffline only; they must NOT
    // cause a new FeedUiState.Success to be emitted (same object reference).
    //
    // Validates: Requirements 2.1, 2.2
    // ---------------------------------------------------------------------------
    test("8.1: uiState reference stays the same after a network-status change (no new Success emitted)") {
        /**
         * **Validates: Requirements 2.1, 2.2**
         *
         * Set up FeedViewModel with:
         *   - a stable, fixed paging flow (same object reference throughout)
         *   - fixed categories (empty list)
         *   - fixed selectedCategory ("all")
         *   - a MutableSharedFlow for network status so we can emit on demand
         *
         * Steps:
         * 1. Seed the network flow with `true` (online) and advance until the ViewModel
         *    reaches an initial FeedUiState.Success.
         * 2. Record `val before = viewModel.uiState.value`.
         * 3. Emit `false` (offline) from the network monitor — no paging state changes.
         * 4. Advance until idle.
         * 5. Assert `viewModel.uiState.value === before` (same object reference).
         *
         * On unfixed code this fails because emitCurrentState() creates a new Success
         * on every network tick. On fixed code it passes because network emissions only
         * update _isOffline and never call emitCurrentState().
         */
        runTest(testDispatcher) {
            // Use a stable, single paging flow shared across all getFeedPager calls
            val stablePagingFlow: Flow<PagingData<ArticleSummaryEntity>> = flowOf(PagingData.empty())

            val networkStatusFlow = MutableSharedFlow<Boolean>(replay = 1)

            val viewModel = buildViewModel(
                networkStatusFlow = networkStatusFlow,
                pagingFlowFactory = { stablePagingFlow }
            )

            // Bring ViewModel to initial Success state
            networkStatusFlow.emit(true)
            advanceUntilIdle()

            val initialState = viewModel.uiState.value
            assert(initialState is FeedUiState.Success) {
                "Expected FeedUiState.Success after init, got $initialState"
            }

            // Record the exact Success object reference before any network change
            val before = viewModel.uiState.value

            // Emit a network status change (online → offline) — no paging change
            networkStatusFlow.emit(false)
            advanceUntilIdle()

            // The uiState value must be the same object reference — no new Success emitted
            viewModel.uiState.value shouldBeSameInstanceAs before
        }
    }

    // ---------------------------------------------------------------------------
    // Task 8.4 — cachedUrls StateFlow updates when observeCachedArticleUrls() emits
    //
    // Regression test for Requirement 3.1: the cachedUrls StateFlow update path must
    // remain intact after Fix 1 (isOffline decoupled from FeedUiState.Success).
    //
    // Validates: Requirement 3.1
    // ---------------------------------------------------------------------------
    test("8.4: cachedUrls StateFlow updates to reflect each set emitted by observeCachedArticleUrls()") {
        /**
         * **Validates: Requirements 3.1**
         *
         * Regression test ensuring the cachedUrls StateFlow update path was NOT broken
         * by Fix 1 (extracting isOffline from FeedUiState.Success).
         *
         * Steps:
         * 1. Create FeedViewModel with a controllable MutableStateFlow for cachedUrls.
         * 2. Emit a specific Set<String> of URLs.
         * 3. Advance until idle.
         * 4. Assert viewModel.cachedUrls.value == first emitted set.
         * 5. Emit a second, different Set<String>.
         * 6. Advance until idle.
         * 7. Assert viewModel.cachedUrls.value == second emitted set.
         */
        runTest(testDispatcher) {
            val cachedUrlsFlow = MutableStateFlow<Set<String>>(emptySet())
            val viewModel = buildViewModel(cachedUrlsFlow = cachedUrlsFlow)

            // Let the ViewModel initialise fully
            advanceUntilIdle()

            // -- Emission 1 --
            val firstSet = setOf(
                "https://aeon.co/articles/article-one",
                "https://aeon.co/articles/article-two",
                "https://aeon.co/articles/article-three"
            )
            cachedUrlsFlow.value = firstSet
            advanceUntilIdle()

            viewModel.cachedUrls.value shouldBe firstSet

            // -- Emission 2 --
            val secondSet = setOf(
                "https://aeon.co/articles/article-four",
                "https://aeon.co/articles/article-five"
            )
            cachedUrlsFlow.value = secondSet
            advanceUntilIdle()

            viewModel.cachedUrls.value shouldBe secondSet
        }
    }
})
