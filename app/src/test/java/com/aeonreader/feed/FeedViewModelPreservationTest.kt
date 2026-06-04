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
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
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
 * FeedViewModelPreservationTest
 *
 * **Property 2: Preservation** — tests that encode baseline behaviors of the
 * UNFIXED ViewModel that must survive all three performance fixes.
 *
 * These tests are scoped to non-bug-condition inputs (isBugCondition = false):
 *  • cachedUrls emissions (no paging flow change)
 *  • refresh() / selectCategory() calls
 *
 * EXPECTED OUTCOME ON UNFIXED CODE: ALL PASS
 *
 * **Validates: Requirements 3.1, 3.2, 3.3**
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelPreservationTest : FunSpec({

    val testDispatcher = StandardTestDispatcher()

    beforeSpec {
        Dispatchers.setMain(testDispatcher)
    }

    afterSpec {
        Dispatchers.resetMain()
    }

    // ---------------------------------------------------------------------------
    // Helper: build a fake ViewModel with injectable flows
    // ---------------------------------------------------------------------------
    fun buildViewModel(
        networkStatusFlow: Flow<Boolean> = flowOf(true),
        cachedUrlsFlow: Flow<Set<String>> = flowOf(emptySet()),
        pagingFlowFactory: (category: String?) -> Flow<PagingData<ArticleSummaryEntity>> = {
            flowOf(PagingData.empty())
        },
        categoryResults: List<String> = emptyList()
    ): FeedViewModel {
        val fakeRepository = object : ArticleRepository {
            override fun getFeedPager(category: String?): Flow<PagingData<ArticleSummaryEntity>> =
                pagingFlowFactory(category)

            override suspend fun getArticle(url: String): Result<Article> =
                Result.failure(UnsupportedOperationException())

            override suspend fun getCachedArticle(url: String): Article? = null

            override suspend fun getCategories(): Result<List<String>> =
                Result.success(categoryResults)

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
    // Sub-test A — Offline Badge Preservation
    //
    // Property: ∀ cachedUrlSet emitted by observeCachedArticleUrls(),
    //           ViewModel.cachedUrls.value == cachedUrlSet after emission.
    //
    // Validates: Requirement 3.1
    // ---------------------------------------------------------------------------
    test("Sub-test A: cachedUrls StateFlow reflects emitted cachedUrlSet (property-based, passes on unfixed code)") {
        /**
         * **Validates: Requirements 3.1**
         *
         * Observe UNFIXED ViewModel: emit arbitrary subsets of cached URL strings
         * from observeCachedArticleUrls() and assert that cachedUrls.value equals
         * the emitted set after each emission.
         *
         * This test is scoped to non-bug-condition inputs: changing the cached URL
         * set does not change the paging flow, so isBugCondition = false.
         *
         * EXPECTED OUTCOME: PASSES on unfixed code — _cachedUrls is unconditionally
         * updated in the combine collector regardless of whether emitCurrentState()
         * creates a new Success instance.
         */
        runTest(testDispatcher) {
            // Arbitrary generator: sets of URL strings (0–10 elements)
            val urlSetArb: Arb<Set<String>> = Arb.list(
                Arb.string(minSize = 5, maxSize = 60),
                range = 0..10
            ).map { it.toSet() }

            checkAll(iterations = 50, urlSetArb) { cachedUrlSet ->
                val cachedUrlsFlow = MutableStateFlow<Set<String>>(emptySet())

                val viewModel = buildViewModel(
                    networkStatusFlow = flowOf(true),
                    cachedUrlsFlow = cachedUrlsFlow
                )
                advanceUntilIdle()

                // Emit the arbitrary set of URLs
                cachedUrlsFlow.value = cachedUrlSet
                advanceUntilIdle()

                // Assert: cachedUrls StateFlow reflects the emitted set
                viewModel.cachedUrls.value shouldBe cachedUrlSet
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Sub-test B — Pull-to-Refresh Preservation
    //
    // Property: calling refresh() transitions state to Loading then back to
    //           Success with a non-null articles flow.
    //
    // Validates: Requirement 3.2
    // ---------------------------------------------------------------------------
    test("Sub-test B: refresh() transitions state to Loading then Success (passes on unfixed code)") {
        /**
         * **Validates: Requirements 3.2**
         *
         * Observe UNFIXED ViewModel: calling refresh() first sets uiState to Loading,
         * then (after coroutines settle) back to Success with a non-null articles flow.
         *
         * This test is scoped to non-bug-condition inputs: refresh() itself is not a
         * network/cache status emission — it is a user-initiated reload. isBugCondition
         * does not fire on refresh() calls.
         *
         * EXPECTED OUTCOME: PASSES on unfixed code — refresh() explicitly assigns
         * _uiState.value = FeedUiState.Loading, then loadFeed() drives the combine
         * collector back to a Success emission.
         */
        runTest(testDispatcher) {
            val networkStatusFlow = MutableSharedFlow<Boolean>(replay = 1)
            val viewModel = buildViewModel(networkStatusFlow = networkStatusFlow)

            // Seed the network flow so the combine collector emits
            networkStatusFlow.emit(true)
            advanceUntilIdle()

            // Confirm we are in Success before refresh
            val stateBeforeRefresh = viewModel.uiState.value
            stateBeforeRefresh shouldBe stateBeforeRefresh.also {
                assert(it is FeedUiState.Success) {
                    "Expected Success before refresh, got $it"
                }
            }

            // Call refresh — immediately check for Loading
            viewModel.refresh()

            // After refresh() sets Loading synchronously, uiState must be Loading
            val stateAfterRefreshCall = viewModel.uiState.value
            assert(stateAfterRefreshCall is FeedUiState.Loading) {
                "Expected FeedUiState.Loading immediately after refresh(), got $stateAfterRefreshCall"
            }

            // Emit a network status so the combine collector fires and we get Success
            networkStatusFlow.emit(true)
            advanceUntilIdle()

            val stateAfterSettle = viewModel.uiState.value
            assert(stateAfterSettle is FeedUiState.Success) {
                "Expected FeedUiState.Success after refresh settles, got $stateAfterSettle"
            }

            // The resulting Success must have a non-null articles flow
            val successState = stateAfterSettle as FeedUiState.Success
            successState.articles shouldNotBe null
        }
    }

    // ---------------------------------------------------------------------------
    // Sub-test C — Category Filter Preservation
    //
    // Property: ∀ non-empty category string, calling selectCategory(category)
    //           produces a new Success.articles flow reference ≠ previous.
    //
    // Validates: Requirement 3.3
    // ---------------------------------------------------------------------------
    test("Sub-test C: selectCategory() replaces Success.articles flow reference (property-based, passes on unfixed code)") {
        /**
         * **Validates: Requirements 3.3**
         *
         * Observe UNFIXED ViewModel: calling selectCategory(cat) for arbitrary
         * non-empty category strings causes loadFeed(cat) to be invoked, which
         * creates a brand-new pagingFlow via getFeedPager(cat). The emitted
         * Success.articles reference must differ from the pre-selection reference.
         *
         * This test is scoped to non-bug-condition inputs: selectCategory() is not a
         * network/cache status emission. It is a user-initiated flow replacement,
         * so isBugCondition = false for this path.
         *
         * EXPECTED OUTCOME: PASSES on unfixed code — selectCategory() calls
         * loadFeed() which unconditionally assigns a new pagingFlow from
         * getFeedPager(category).
         */
        runTest(testDispatcher) {
            // Arbitrary generator: non-empty category strings (avoid "all" to exercise
            // the category-scoped path; use printable ASCII to stay realistic)
            val categoryArb: Arb<String> = Arb.string(minSize = 1, maxSize = 30)
                .filter { it.isNotBlank() && it != "all" }

            checkAll(iterations = 30, categoryArb) { category ->
                val networkStatusFlow = MutableSharedFlow<Boolean>(replay = 1)

                // Each call to getFeedPager produces a DISTINCT flow object — use a
                // counter to guarantee distinct references even for the same category
                var callCount = 0
                val flows = mutableListOf<Flow<PagingData<ArticleSummaryEntity>>>()

                val viewModel = buildViewModel(
                    networkStatusFlow = networkStatusFlow,
                    pagingFlowFactory = {
                        val newFlow: Flow<PagingData<ArticleSummaryEntity>> =
                            flowOf(PagingData.empty())
                        flows.add(newFlow)
                        callCount++
                        newFlow
                    }
                )

                // Seed network so we reach Success
                networkStatusFlow.emit(true)
                advanceUntilIdle()

                val stateBeforeSelect = viewModel.uiState.value
                assert(stateBeforeSelect is FeedUiState.Success) {
                    "Expected Success before selectCategory, got $stateBeforeSelect"
                }
                val articlesBeforeSelect = (stateBeforeSelect as FeedUiState.Success).articles

                // Select the generated category
                viewModel.selectCategory(category)

                // Emit a network status tick so the combine collector fires
                networkStatusFlow.emit(true)
                advanceUntilIdle()

                val stateAfterSelect = viewModel.uiState.value
                assert(stateAfterSelect is FeedUiState.Success) {
                    "Expected Success after selectCategory, got $stateAfterSelect"
                }
                val articlesAfterSelect = (stateAfterSelect as FeedUiState.Success).articles

                // The articles flow reference must have changed: getFeedPager was called
                // again for the new category, yielding a fresh Flow object.
                articlesAfterSelect shouldNotBeSameInstanceAs articlesBeforeSelect
            }
        }
    }
})
