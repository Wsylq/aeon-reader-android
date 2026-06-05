package com.aeonreader.feed

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
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
import com.aeonreader.domain.ArticleSummary
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Unit tests for ArticleRemoteMediator pageOrder formula correctness.
 *
 * Validates: Requirements 2.3
 *
 * Formula under test: pageOrder = (page - 1) * state.config.pageSize + index
 *
 * Four test cases:
 *  1. Page 1, size 20 → pageOrder values exactly {0..19}
 *  2. Page 2, size 20 → pageOrder values exactly {20..39}
 *  3. Page 3, size 10 (partial) → pageOrder values exactly {40..49}
 *  4. Re-fetch page 1 → same {0..19} deterministically
 */
@OptIn(ExperimentalPagingApi::class)
class ArticleRemoteMediatorUnitTest : FunSpec({

    // ---------------------------------------------------------------------------
    // Helpers — reuse the fake DAO + scraper pattern from ArticleRemoteMediatorTest
    // ---------------------------------------------------------------------------

    fun fakeSummary(page: Int, index: Int) = ArticleSummary(
        url = "https://aeon.co/article/page${page}_item${index}",
        title = "Article $page/$index",
        description = null,
        author = null,
        category = null,
        heroImageUrl = null,
        estimatedReadingTimeMinutes = 5,
    )

    /** Build a scraper that returns exactly the provided summaries per page number. */
    fun buildFakeScraper(pageMap: Map<Int, List<ArticleSummary>>): AeonScraper {
        return object : AeonScraper {
            override suspend fun fetchFeed(page: Int): Result<List<ArticleSummary>> =
                Result.success(pageMap[page] ?: emptyList())

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
     * Fake ArticleDao whose [upsertSummaries] appends to [writtenEntities].
     * All other write methods are no-ops; all read/flow methods return empty results.
     */
    fun buildFakeArticleDao(writtenEntities: MutableList<ArticleSummaryEntity>): ArticleDao {
        return object : ArticleDao {
            override suspend fun upsertSummaries(summaries: List<ArticleSummaryEntity>) {
                writtenEntities.addAll(summaries)
            }

            override suspend fun getSummaryTimestamps(urls: List<String>): List<UrlTimestamp> =
                emptyList()

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

    /** Fake RemoteKeyDao backed by an in-memory map. */
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

    /**
     * Convenience: build the mediator with the given scraper and return the DAO list so
     * callers can inspect what was written.
     */
    fun buildMediatorAndDao(
        scraper: AeonScraper,
        remoteKeyDao: RemoteKeyDao = buildFakeRemoteKeyDao()
    ): Pair<ArticleRemoteMediator, MutableList<ArticleSummaryEntity>> {
        val written = mutableListOf<ArticleSummaryEntity>()
        val mediator = ArticleRemoteMediator(
            category = null,
            scraper = scraper,
            articleDao = buildFakeArticleDao(written),
            remoteKeyDao = remoteKeyDao
        )
        return mediator to written
    }

    /** Helper: build a PagingState with the given page size and no pages loaded. */
    fun emptyPagingState(pageSize: Int) = PagingState<Int, ArticleSummaryEntity>(
        pages = emptyList(),
        anchorPosition = null,
        config = PagingConfig(pageSize = pageSize, enablePlaceholders = false),
        leadingPlaceholderCount = 0
    )

    // ---------------------------------------------------------------------------
    // Test 1: Page 1, size 20 → pageOrder values exactly {0..19}
    // ---------------------------------------------------------------------------

    test("pageOrder for page 1 (size 20) is exactly {0..19}") {
        // **Validates: Requirements 2.3**

        val page1 = (0 until 20).map { i -> fakeSummary(1, i) }
        val scraper = buildFakeScraper(mapOf(1 to page1))
        val (mediator, written) = buildMediatorAndDao(scraper)
        val state = emptyPagingState(20)

        // REFRESH loads page 1 when no remote key exists
        mediator.load(LoadType.REFRESH, state)

        written shouldHaveSize 20
        val pageOrders = written.map { it.pageOrder }
        pageOrders shouldContainExactlyInAnyOrder (0..19).toList()
    }

    // ---------------------------------------------------------------------------
    // Test 2: Page 2, size 20 → pageOrder values exactly {20..39}
    // ---------------------------------------------------------------------------

    test("pageOrder for page 2 (size 20) is exactly {20..39}") {
        // **Validates: Requirements 2.3**

        val page1 = (0 until 20).map { i -> fakeSummary(1, i) }
        val page2 = (0 until 20).map { i -> fakeSummary(2, i) }
        val scraper = buildFakeScraper(mapOf(1 to page1, 2 to page2))
        val remoteKeyDao = buildFakeRemoteKeyDao()
        val (mediator, written) = buildMediatorAndDao(scraper, remoteKeyDao)
        val state = emptyPagingState(20)

        // Load page 1 first so the remote key advances nextPage to 2
        mediator.load(LoadType.REFRESH, state)
        written.clear()   // discard page 1 entries; only inspect page 2

        // APPEND reads nextPage = 2 from the remote key written after page 1
        mediator.load(LoadType.APPEND, state)

        written shouldHaveSize 20
        val pageOrders = written.map { it.pageOrder }
        pageOrders shouldContainExactlyInAnyOrder (20..39).toList()
    }

    // ---------------------------------------------------------------------------
    // Test 3: Page 3, size 10 (partial) → pageOrder values exactly {40..49}
    // ---------------------------------------------------------------------------

    test("pageOrder for page 3 (size 10, partial page) is exactly {40..49}") {
        // **Validates: Requirements 2.3**

        val page1 = (0 until 20).map { i -> fakeSummary(1, i) }
        val page2 = (0 until 20).map { i -> fakeSummary(2, i) }
        val page3 = (0 until 10).map { i -> fakeSummary(3, i) }  // partial page
        val scraper = buildFakeScraper(mapOf(1 to page1, 2 to page2, 3 to page3))
        val remoteKeyDao = buildFakeRemoteKeyDao()
        val (mediator, written) = buildMediatorAndDao(scraper, remoteKeyDao)
        val state = emptyPagingState(20)

        // Load pages 1 and 2 to advance remote key to page 3
        mediator.load(LoadType.REFRESH, state)
        mediator.load(LoadType.APPEND, state)
        written.clear()   // discard pages 1 & 2; only inspect page 3

        // APPEND reads nextPage = 3
        mediator.load(LoadType.APPEND, state)

        written shouldHaveSize 10
        val pageOrders = written.map { it.pageOrder }
        // Formula: (3 - 1) * 20 + index = 40 + index → {40..49}
        pageOrders shouldContainExactlyInAnyOrder (40..49).toList()
    }

    // ---------------------------------------------------------------------------
    // Test 4: Re-fetch page 1 → same {0..19} deterministically
    // ---------------------------------------------------------------------------

    test("Re-fetching page 1 produces the same pageOrder values {0..19} deterministically") {
        // **Validates: Requirements 2.3**

        val page1 = (0 until 20).map { i -> fakeSummary(1, i) }
        val scraper = buildFakeScraper(mapOf(1 to page1))

        // --- First fetch ---
        val (mediator1, written1) = buildMediatorAndDao(scraper)
        val state = emptyPagingState(20)
        mediator1.load(LoadType.REFRESH, state)
        val firstFetchOrders = written1.map { it.pageOrder }.toSet()

        // --- Second (independent) fetch of the same page 1 ---
        val (mediator2, written2) = buildMediatorAndDao(scraper)
        mediator2.load(LoadType.REFRESH, state)
        val secondFetchOrders = written2.map { it.pageOrder }.toSet()

        // Both fetches must produce {0..19}
        firstFetchOrders shouldBe (0..19).toSet()
        secondFetchOrders shouldBe (0..19).toSet()

        // The two sets must be identical (determinism)
        firstFetchOrders shouldBe secondFetchOrders
    }
})
