package com.aeonreader.feed

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingState
import com.aeonreader.data.local.ArticleDao
import com.aeonreader.data.local.ArticleEntity
import com.aeonreader.data.local.ArticleSummaryEntity
import com.aeonreader.data.local.HighlightedWordEntity
import com.aeonreader.data.local.RemoteKeyDao
import com.aeonreader.data.local.RemoteKeyEntity
import com.aeonreader.data.local.UrlTimestamp
import com.aeonreader.data.local.WordDefinitionEntity
import com.aeonreader.data.network.AeonScraper
import com.aeonreader.data.repository.ArticleRemoteMediator
import com.aeonreader.data.repository.ArticleRepository
import com.aeonreader.data.repository.UserPreferencesRepository
import com.aeonreader.domain.Article
import com.aeonreader.domain.ArticleSummary
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * FeedIntegrationTest
 *
 * Integration tests that exercise the ViewModel and mediator together across
 * realistic event sequences. These tests verify end-to-end scenarios that
 * would be difficult to cover with isolated unit tests.
 *
 * **Validates: Requirements 2.1, 2.2, 2.3, 3.1, 3.3, 3.4**
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalPagingApi::class)
class FeedIntegrationTest : FunSpec({

    val testDispatcher = StandardTestDispatcher()

    beforeSpec {
        Dispatchers.setMain(testDispatcher)
    }

    afterSpec {
        Dispatchers.resetMain()
    }

    // ---------------------------------------------------------------------------
    // Shared fake helpers — same patterns as FeedViewModelPreservationTest
    // ---------------------------------------------------------------------------

    /**
     * Build a minimal FeedViewModel with injectable flows and a paging factory.
     *
     * [pagingFlowFactory] receives the `category` argument passed to [getFeedPager]
     * and returns a flow. Returning the SAME reference for the same category
     * (or always the same reference for any category) simulates a stable pager.
     */
    fun buildViewModel(
        networkStatusFlow: Flow<Boolean>,
        cachedUrlsFlow: Flow<Set<String>> = flowOf(emptySet()),
        pagingFlowFactory: (category: String?) -> Flow<PagingData<ArticleSummaryEntity>> = {
            flowOf(PagingData.empty())
        },
        categoryResults: List<String> = emptyList()
    ): FeedViewModel {
        val fakeRepository = object : ArticleRepository {
            override fun getFeedPager(
                category: String?
            ): Flow<PagingData<ArticleSummaryEntity>> = pagingFlowFactory(category)

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
    // Mediator fake helpers — same patterns as ArticleRemoteMediatorPreservationTest
    // ---------------------------------------------------------------------------

    fun fakeSummary(page: Int, index: Int) = ArticleSummary(
        url = "https://aeon.co/article/page${page}_item${index}",
        title = "Article $page/$index",
        description = null,
        author = null,
        category = null,
        heroImageUrl = null,
        estimatedReadingTimeMinutes = 5,
        cachedAt = null
    )

    fun buildFakeScraper(
        summariesByPage: Map<Int, List<ArticleSummary>>
    ): AeonScraper {
        return object : AeonScraper {
            override suspend fun fetchFeed(page: Int): Result<List<ArticleSummary>> =
                Result.success(summariesByPage[page] ?: emptyList())

            override suspend fun fetchCategoryFeed(
                category: String,
                page: Int
            ): Result<List<ArticleSummary>> = Result.success(emptyList())

            override suspend fun fetchArticle(url: String): Result<String> =
                Result.failure(UnsupportedOperationException())

            override suspend fun fetchCategories(): Result<List<String>> =
                Result.success(emptyList())

            override suspend fun search(
                query: String,
                page: Int
            ): Result<List<ArticleSummary>> = Result.success(emptyList())

            override suspend fun searchViaServer(query: String): Result<List<ArticleSummary>> =
                Result.success(emptyList())
        }
    }

    /**
     * In-memory ArticleDao that tracks upserted entities in insertion order.
     * Re-upsert with the same URL updates in place (preserving the existing record's
     * position for the URL list) — mirrors ArticleRemoteMediatorPreservationTest.
     */
    fun buildFakeArticleDao(
        writtenEntities: MutableList<ArticleSummaryEntity>
    ): ArticleDao {
        return object : ArticleDao {
            override suspend fun upsertSummaries(summaries: List<ArticleSummaryEntity>) {
                for (newEntity in summaries) {
                    val existingIndex =
                        writtenEntities.indexOfFirst { it.url == newEntity.url }
                    if (existingIndex >= 0) {
                        writtenEntities[existingIndex] = newEntity
                    } else {
                        writtenEntities.add(newEntity)
                    }
                }
            }

            override suspend fun getSummaryTimestamps(urls: List<String>): List<UrlTimestamp> =
                writtenEntities
                    .filter { it.url in urls }
                    .map { UrlTimestamp(it.url, it.cachedAt) }

            override fun getSummariesByCategory(category: String) =
                throw UnsupportedOperationException()

            override fun getAllSummaries() =
                throw UnsupportedOperationException()

            override suspend fun getArticle(url: String): ArticleEntity? = null
            override suspend fun upsertArticle(entity: ArticleEntity) {}
            override suspend fun deleteArticlesOlderThan(cutoffMillis: Long) {}
            override suspend fun deleteLeastRecentlyAccessed(count: Int) {}
            override suspend fun updateLastAccessed(url: String, time: Long) {}
            override suspend fun deleteSummariesOlderThan(cutoffMillis: Long) {}
            override suspend fun getTotalCacheSize(): Long = 0L
            override fun getCachedArticleUrls(): Flow<List<String>> = flowOf(emptyList())
            override suspend fun getWordDefinition(word: String): WordDefinitionEntity? = null
            override suspend fun upsertWordDefinition(entity: WordDefinitionEntity) {}
            override suspend fun deleteOldDefinitions(cutoffMillis: Long) {}
            override fun getHighlightedWords(articleUrl: String): Flow<List<String>> =
                flowOf(emptyList())
            override suspend fun upsertHighlightedWord(entity: HighlightedWordEntity) {}
            override suspend fun removeHighlightedWord(articleUrl: String, word: String) {}
        }
    }

    fun buildFakeRemoteKeyDao(): RemoteKeyDao {
        val keys = mutableMapOf<String, RemoteKeyEntity>()
        return object : RemoteKeyDao {
            override suspend fun upsert(entity: RemoteKeyEntity) {
                keys[entity.category] = entity
            }
            override suspend fun get(category: String): RemoteKeyEntity? = keys[category]
            override suspend fun deleteByCategory(category: String) {
                keys.remove(category)
            }
        }
    }

    // =========================================================================
    // Task 10.1 — Network offline status change does not create a new paging
    //             subscription (no new articles flow reference emitted)
    //
    // Validates: Requirements 2.1, 2.2, 3.1
    // =========================================================================
    test("10.1: network offline status change does not replace the articles flow reference") {
        /**
         * **Validates: Requirements 2.1, 2.2, 3.1**
         *
         * Integration scenario:
         *   1. Create FeedViewModel with a single stable paging flow (same reference
         *      always returned by getFeedPager regardless of category).
         *   2. Seed the network flow with `true` (online) and advance until the
         *      ViewModel reaches FeedUiState.Success.
         *   3. Record `val articlesRef = (uiState.value as Success).articles`.
         *   4. Emit an offline network status event (false).
         *   5. Advance until idle.
         *   6. Assert `(uiState.value as Success).articles === articlesRef` — same
         *      articles flow reference, meaning no new paging subscription was created.
         *
         * Fix 1 (tasks 3.1–3.2) ensures that network/cache status changes update
         * only `_isOffline` / `_cachedUrls` and never call `emitCurrentState()`,
         * so the `FeedUiState.Success` object and its `articles` field are unchanged.
         *
         * If this test fails it means a network-status emission caused `loadFeed()`
         * or `emitCurrentState()` to be called, replacing the articles flow and
         * triggering an unnecessary paging re-subscription in the UI.
         */
        runTest(testDispatcher) {
            // A single stable paging flow — same object reference for every getFeedPager call
            val stablePagingFlow: Flow<PagingData<ArticleSummaryEntity>> =
                flowOf(PagingData.empty())

            val networkStatusFlow = MutableSharedFlow<Boolean>(replay = 1)

            val viewModel = buildViewModel(
                networkStatusFlow = networkStatusFlow,
                pagingFlowFactory = { stablePagingFlow }
            )

            // Bring ViewModel to the initial Success state (online)
            networkStatusFlow.emit(true)
            advanceUntilIdle()

            val initialState = viewModel.uiState.value
            assert(initialState is FeedUiState.Success) {
                "Expected FeedUiState.Success after init, got $initialState"
            }

            // Record the articles flow reference from the initial Success state
            val articlesRef = (initialState as FeedUiState.Success).articles

            // Emit an offline network status event — this should NOT trigger a new Success
            networkStatusFlow.emit(false)
            advanceUntilIdle()

            val stateAfterOffline = viewModel.uiState.value
            assert(stateAfterOffline is FeedUiState.Success) {
                "Expected FeedUiState.Success to remain after going offline, got $stateAfterOffline"
            }

            // The articles flow reference must be identical — no new paging subscription
            val articlesAfterOffline = (stateAfterOffline as FeedUiState.Success).articles
            articlesAfterOffline shouldBeSameInstanceAs articlesRef
        }
    }

    // =========================================================================
    // Task 10.2 — Page 2 append does not reorder page 1 items
    //
    // Validates: Requirements 2.3, 3.4
    // =========================================================================
    test("10.2: page 2 append does not reorder page 1 items in the DAO store") {
        /**
         * **Validates: Requirements 2.3, 3.4**
         *
         * Integration scenario (mirrors ArticleRemoteMediatorPreservationTest Sub-test E
         * but framed as an integration test):
         *   1. Build a fake DAO (in-memory store) and a fake scraper with 20 articles
         *      on page 1 and 20 articles on page 2.
         *   2. Load page 1 via REFRESH — record the ordered URL list for page 1 items
         *      from the DAO store.
         *   3. Load page 2 via APPEND.
         *   4. Re-query the page 1 items from the DAO store and assert their URL order
         *      is identical to the pre-append recording.
         *
         * The globally-unique `pageOrder` formula (Fix 2, task 4.1) ensures that
         * page 2 writes rows with pageOrder 20–39 instead of colliding with page 1's
         * 0–19, but the URL insertion order of already-written page 1 rows must not
         * change when the DAO processes a page 2 upsert.
         *
         * This test verifies both:
         *   (a) the mediator writes are idempotent for existing page 1 URLs (none match
         *       page 2 URLs — no accidental overwrites), and
         *   (b) the relative ordering of page 1 entries in the DAO store is stable after
         *       a page 2 append, which is the write-layer prerequisite for scroll-position
         *       stability at the paging source layer.
         */
        val page1Summaries = (0 until 20).map { i -> fakeSummary(1, i) }
        val page2Summaries = (0 until 20).map { i -> fakeSummary(2, i) }

        val writtenEntities = mutableListOf<ArticleSummaryEntity>()
        val articleDao = buildFakeArticleDao(writtenEntities)
        val remoteKeyDao = buildFakeRemoteKeyDao()
        val scraper = buildFakeScraper(
            summariesByPage = mapOf(1 to page1Summaries, 2 to page2Summaries)
        )

        val mediator = ArticleRemoteMediator(
            category = null,
            scraper = scraper,
            articleDao = articleDao,
            remoteKeyDao = remoteKeyDao
        )

        val pagingConfig = PagingConfig(pageSize = 20, enablePlaceholders = false)
        val emptyPagingState = PagingState<Int, ArticleSummaryEntity>(
            pages = emptyList(),
            anchorPosition = null,
            config = pagingConfig,
            leadingPlaceholderCount = 0
        )

        // --- Load page 1 (REFRESH) ---
        mediator.load(LoadType.REFRESH, emptyPagingState)

        // Record the ordered URL list for page 1 items (in insertion order)
        val page1UrlsBeforeAppend: List<String> = writtenEntities
            .filter { it.page == 1 }
            .map { it.url }

        // Sanity: must have exactly 20 page-1 items
        page1UrlsBeforeAppend.size shouldBe 20

        // --- Load page 2 (APPEND) ---
        mediator.load(LoadType.APPEND, emptyPagingState)

        // Re-query page 1 items from the store after the append
        val page1UrlsAfterAppend: List<String> = writtenEntities
            .filter { it.page == 1 }
            .map { it.url }

        // Page 1 URL order must be identical before and after the page 2 append
        page1UrlsAfterAppend shouldBe page1UrlsBeforeAppend
    }

    // =========================================================================
    // Task 10.3 — Category change followed by network status change retains
    //             the category-scoped articles flow
    //
    // Validates: Requirements 3.3, 2.1
    // =========================================================================
    test("10.3: category change followed by network status change retains category-scoped articles flow") {
        /**
         * **Validates: Requirements 3.3, 2.1**
         *
         * Integration scenario:
         *   1. Create FeedViewModel with a controllable network status flow and a
         *      paging factory that produces a distinct flow per category string.
         *   2. Seed network flow with `true` (online) and advance to initial Success.
         *   3. Call `selectCategory("philosophy")` and advance until idle.
         *   4. Record `val philosophyArticles = (uiState.value as Success).articles`.
         *   5. Emit a network status change (online → offline).
         *   6. Advance until idle.
         *   7. Assert `(uiState.value as Success).articles === philosophyArticles` —
         *      still the same flow scoped to "philosophy", not replaced by a new one.
         *
         * This test verifies that Fix 1's decoupling of `_isOffline` from
         * FeedUiState.Success does not interfere with Fix 3.3's category-scoped
         * paging flow: after a category change the articles reference must remain
         * stable across subsequent network/cache ticks, just as it must for the
         * default "all" category.
         *
         * If this test fails it means either:
         *   (a) selectCategory() is not persisting the category-scoped flow, or
         *   (b) a network-status emission is replacing the category-scoped flow with
         *       a new one (regression from Fix 1 applied incorrectly).
         */
        runTest(testDispatcher) {
            val networkStatusFlow = MutableSharedFlow<Boolean>(replay = 1)

            // Each call to getFeedPager produces a stable flow for its category.
            // Use a map so the same category always returns the same flow reference —
            // this simulates what a real Pager backed by cachedIn() would do.
            val categoryFlows =
                mutableMapOf<String?, Flow<PagingData<ArticleSummaryEntity>>>()

            val viewModel = buildViewModel(
                networkStatusFlow = networkStatusFlow,
                pagingFlowFactory = { category ->
                    categoryFlows.getOrPut(category) { flowOf(PagingData.empty()) }
                }
            )

            // Bring ViewModel to the initial Success state (online, category = "all")
            networkStatusFlow.emit(true)
            advanceUntilIdle()

            val initialState = viewModel.uiState.value
            assert(initialState is FeedUiState.Success) {
                "Expected FeedUiState.Success after init, got $initialState"
            }

            // Select the "philosophy" category — this calls loadFeed("philosophy") and
            // emitCurrentState() synchronously, so uiState should update immediately.
            viewModel.selectCategory("philosophy")
            advanceUntilIdle()

            val stateAfterCategoryChange = viewModel.uiState.value
            assert(stateAfterCategoryChange is FeedUiState.Success) {
                "Expected FeedUiState.Success after selectCategory, got $stateAfterCategoryChange"
            }
            assert((stateAfterCategoryChange as FeedUiState.Success).selectedCategory == "philosophy") {
                "Expected selectedCategory == 'philosophy', got ${stateAfterCategoryChange.selectedCategory}"
            }

            // Record the philosophy-scoped articles flow reference
            val philosophyArticles = stateAfterCategoryChange.articles

            // Emit a network status change (online → offline)
            networkStatusFlow.emit(false)
            advanceUntilIdle()

            val stateAfterNetworkChange = viewModel.uiState.value
            assert(stateAfterNetworkChange is FeedUiState.Success) {
                "Expected FeedUiState.Success after network change, got $stateAfterNetworkChange"
            }

            // The articles flow must still be the philosophy-scoped reference —
            // the network-status emission must NOT have replaced it
            val articlesAfterNetworkChange =
                (stateAfterNetworkChange as FeedUiState.Success).articles
            articlesAfterNetworkChange shouldBeSameInstanceAs philosophyArticles
        }
    }
})
