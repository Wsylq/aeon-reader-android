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
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.set
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
 * FeedViewModelPbtTest
 *
 * Property-based tests for FeedViewModel correctness after the three performance fixes.
 *
 * **Task 9.1** — arbitrary network/cache emission sequences do not produce new `articles`
 *   references (confirms Fix 1, tasks 3.1–3.2).
 *
 * **Task 9.4** — arbitrary category strings preserve category-scoped paging flow
 *   (confirms Fix 1 + selectCategory path, task 3.1).
 *
 * **Validates: Requirements 2.1, 2.2, 3.3**
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelPbtTest : FunSpec({

    val testDispatcher = StandardTestDispatcher()

    beforeSpec {
        Dispatchers.setMain(testDispatcher)
    }

    afterSpec {
        Dispatchers.resetMain()
    }

    // ---------------------------------------------------------------------------
    // Helper: build a FeedViewModel with injectable flows
    // ---------------------------------------------------------------------------
    fun buildViewModel(
        networkStatusFlow: Flow<Boolean> = flowOf(true),
        cachedUrlsFlow: Flow<Set<String>> = flowOf(emptySet()),
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
    // Task 9.1 — PBT: arbitrary network/cache emission sequences do not produce
    //            new `articles` references
    //
    // **Validates: Requirements 2.1, 2.2**
    //
    // Confirms Fix 1 (tasks 3.1–3.2): after the fix, emitting any sequence of
    // Boolean (network status) and Set<String> (cached URLs) values must never
    // cause a new FeedUiState.Success whose `articles` reference differs from
    // the initial one.
    // ---------------------------------------------------------------------------
    test("9.1: arbitrary network/cache emission sequences do not produce a new articles reference") {
        /**
         * **Validates: Requirements 2.1, 2.2**
         *
         * Property: for ANY sequence of network-status and cached-URL emissions,
         * the `articles` flow reference inside FeedUiState.Success never changes.
         *
         * Steps per generated sequence:
         *  1. Create a FeedViewModel backed by a stable single pagingFlow (same
         *     object reference always returned by getFeedPager).
         *  2. Seed the network status flow with `true` and advance until the
         *     ViewModel reaches the initial FeedUiState.Success.
         *  3. Record `initialArticles = (uiState.value as Success).articles`.
         *  4. Interleave all network-status booleans and cached-URL sets from the
         *     generated sequences, advancing after each emission.
         *  5. Assert that `uiState.value` is still a FeedUiState.Success AND its
         *     `articles` field is the SAME reference as `initialArticles`.
         *
         * Run with at least 30 iterations.
         */
        runTest(testDispatcher) {
            // Generators as specified in the task
            val networkSeqArb: Arb<List<Boolean>> = Arb.list(
                Arb.boolean(),
                range = 1..20
            )
            val cachedUrlSeqArb: Arb<List<Set<String>>> = Arb.list(
                Arb.set(Arb.string(maxSize = 30)),
                range = 1..20
            )

            checkAll(iterations = 30, networkSeqArb, cachedUrlSeqArb) { networkSeq, cachedSeq ->
                // Stable pagingFlow — same reference regardless of category argument
                val stablePagingFlow: Flow<PagingData<ArticleSummaryEntity>> =
                    flowOf(PagingData.empty())

                val networkStatusFlow = MutableSharedFlow<Boolean>(replay = 1)
                val cachedUrlsFlow = MutableStateFlow<Set<String>>(emptySet())

                val viewModel = buildViewModel(
                    networkStatusFlow = networkStatusFlow,
                    cachedUrlsFlow = cachedUrlsFlow,
                    pagingFlowFactory = { stablePagingFlow }
                )

                // Reach initial Success state
                networkStatusFlow.emit(true)
                advanceUntilIdle()

                val initialState = viewModel.uiState.value
                assert(initialState is FeedUiState.Success) {
                    "Expected FeedUiState.Success after init, got $initialState"
                }
                val initialArticles = (initialState as FeedUiState.Success).articles

                // Emit all events in the generated sequences (interleaved)
                val maxLen = maxOf(networkSeq.size, cachedSeq.size)
                for (i in 0 until maxLen) {
                    if (i < networkSeq.size) {
                        networkStatusFlow.emit(networkSeq[i])
                        advanceUntilIdle()
                    }
                    if (i < cachedSeq.size) {
                        cachedUrlsFlow.value = cachedSeq[i]
                        advanceUntilIdle()
                    }
                }

                // Assert: still a Success with the SAME articles reference
                val finalState = viewModel.uiState.value
                assert(finalState is FeedUiState.Success) {
                    "Expected FeedUiState.Success after emission sequence, got $finalState"
                }
                val finalArticles = (finalState as FeedUiState.Success).articles

                finalArticles shouldBeSameInstanceAs initialArticles
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Task 9.4 — PBT: arbitrary category strings preserve category-scoped paging flow
    //
    // **Validates: Requirements 3.3**
    //
    // For any non-empty category string, calling selectCategory(category) must
    // produce a NEW `articles` flow reference — i.e., getFeedPager is called again
    // with the new category, yielding a fresh Flow object scoped to that category.
    // This is essentially Sub-test C from task 2 run on FIXED code.
    // ---------------------------------------------------------------------------
    test("9.4: arbitrary category strings cause selectCategory() to install a new articles flow reference") {
        /**
         * **Validates: Requirements 3.3**
         *
         * Property: for ANY non-empty, non-"all" category string, after calling
         * selectCategory(category), the `articles` reference in FeedUiState.Success
         * must differ from the reference that was present before the call.
         *
         * Steps per generated category:
         *  1. Create a FeedViewModel where getFeedPager always returns a new,
         *     distinct Flow instance (counter-based factory).
         *  2. Seed the network status flow and advance to the initial Success state.
         *  3. Record `articlesBeforeSelect = (uiState.value as Success).articles`.
         *  4. Call `selectCategory(category)`.
         *  5. Advance until idle.
         *  6. Assert that `(uiState.value as Success).articles !== articlesBeforeSelect`.
         *
         * Run with at least 30 iterations.
         */
        runTest(testDispatcher) {
            // Arbitrary generator: non-empty, non-"all" category strings
            val categoryArb: Arb<String> = Arb.string(minSize = 1, maxSize = 30)
                .filter { it.isNotBlank() && it != "all" }

            checkAll(iterations = 30, categoryArb) { category ->
                val networkStatusFlow = MutableSharedFlow<Boolean>(replay = 1)

                // Each getFeedPager call returns a brand-new Flow object so we can
                // detect reference changes even for repeated category strings.
                val viewModel = buildViewModel(
                    networkStatusFlow = networkStatusFlow,
                    pagingFlowFactory = {
                        // New flow instance on every call — guaranteed distinct reference
                        flowOf(PagingData.empty())
                    }
                )

                // Reach initial Success state
                networkStatusFlow.emit(true)
                advanceUntilIdle()

                val stateBeforeSelect = viewModel.uiState.value
                assert(stateBeforeSelect is FeedUiState.Success) {
                    "Expected FeedUiState.Success before selectCategory, got $stateBeforeSelect"
                }
                val articlesBeforeSelect = (stateBeforeSelect as FeedUiState.Success).articles

                // Select the generated category
                viewModel.selectCategory(category)
                advanceUntilIdle()

                // Assert: new Flow reference installed for the category
                val stateAfterSelect = viewModel.uiState.value
                assert(stateAfterSelect is FeedUiState.Success) {
                    "Expected FeedUiState.Success after selectCategory, got $stateAfterSelect"
                }
                val articlesAfterSelect = (stateAfterSelect as FeedUiState.Success).articles

                // The articles reference MUST have changed — getFeedPager was called
                // again for the new category, returning a fresh Flow object.
                assert(articlesAfterSelect !== articlesBeforeSelect) {
                    "Expected a NEW articles flow reference after selectCategory(\"$category\"), " +
                        "but got the same instance. Fix 1 / selectCategory path is broken."
                }
            }
        }
    }
})
