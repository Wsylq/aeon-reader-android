package com.aeonreader.feed

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import com.aeonreader.data.local.ArticleSummaryEntity
import com.aeonreader.data.local.ArticleSummaryProjection
import com.aeonreader.data.network.AeonScraper
import com.aeonreader.data.repository.ArticleRemoteMediator
import com.aeonreader.domain.ArticleSummary
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Property-based tests for ArticleRemoteMediator pageOrder formula.
 *
 * **Validates: Requirements 2.3**
 *
 * Property: For any combination of page count and page size, the pageOrder values
 * written across all pages must be:
 *  1. All unique (no duplicates)
 *  2. Minimum value is 0
 *  3. Values form a contiguous range [0, pageCount * pageSize - 1]
 *
 * Formula under test: pageOrder = (page - 1) * state.config.pageSize + index
 */
@OptIn(ExperimentalPagingApi::class)
class ArticleRemoteMediatorPbtTest : FunSpec({

    // ---------------------------------------------------------------------------
    // Helpers — same fake DAO + scraper pattern as ArticleRemoteMediatorUnitTest
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

    fun buildFakeScraperForPbt(pageMap: Map<Int, List<ArticleSummary>>): AeonScraper {
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

    fun buildFakeArticleDaoForPbt(
        writtenEntities: MutableList<ArticleSummaryEntity>
    ): com.aeonreader.data.local.ArticleDao {
        return object : com.aeonreader.data.local.ArticleDao {
            override suspend fun upsertSummaries(summaries: List<ArticleSummaryEntity>) {
                writtenEntities.addAll(summaries)
            }

            override suspend fun getSummaryTimestamps(
                urls: List<String>
            ): List<com.aeonreader.data.local.UrlTimestamp> = emptyList()

            override fun getSummariesByCategory(category: String) =
                throw UnsupportedOperationException()

            override fun getAllSummaries() =
                throw UnsupportedOperationException()

            override suspend fun getArticle(url: String): com.aeonreader.data.local.ArticleEntity? = null
            override suspend fun upsertArticle(entity: com.aeonreader.data.local.ArticleEntity) {}
            override suspend fun deleteArticlesOlderThan(cutoffMillis: Long) {}
            override suspend fun deleteLeastRecentlyAccessed(count: Int) {}
            override suspend fun updateLastAccessed(url: String, time: Long) {}
            override suspend fun deleteSummariesOlderThan(cutoffMillis: Long) {}
            override suspend fun getTotalCacheSize(): Long = 0L
            override fun getCachedArticleUrls(): Flow<List<String>> = flowOf(emptyList())
            override suspend fun getWordDefinition(word: String): com.aeonreader.data.local.WordDefinitionEntity? = null
            override suspend fun upsertWordDefinition(entity: com.aeonreader.data.local.WordDefinitionEntity) {}
            override suspend fun deleteOldDefinitions(cutoffMillis: Long) {}
            override fun getHighlightedWords(articleUrl: String): Flow<List<String>> =
                flowOf(emptyList())
            override suspend fun upsertHighlightedWord(entity: com.aeonreader.data.local.HighlightedWordEntity) {}
            override suspend fun removeHighlightedWord(articleUrl: String, word: String) {}
        }
    }

    fun buildFakeRemoteKeyDaoForPbt(): com.aeonreader.data.local.RemoteKeyDao {
        val keys = mutableMapOf<String, com.aeonreader.data.local.RemoteKeyEntity>()
        return object : com.aeonreader.data.local.RemoteKeyDao {
            override suspend fun upsert(entity: com.aeonreader.data.local.RemoteKeyEntity) {
                keys[entity.category] = entity
            }
            override suspend fun get(category: String): com.aeonreader.data.local.RemoteKeyEntity? =
                keys[category]
            override suspend fun deleteByCategory(category: String) {
                keys.remove(category)
            }
        }
    }

    fun emptyPagingStateForPbt(pageSize: Int) = PagingState<Int, ArticleSummaryProjection>(
        pages = emptyList(),
        anchorPosition = null,
        config = PagingConfig(pageSize = pageSize, enablePlaceholders = false),
        leadingPlaceholderCount = 0
    )

    // ---------------------------------------------------------------------------
    // Property test: arbitrary page counts and sizes produce unique, gap-free pageOrder
    // ---------------------------------------------------------------------------

    test("arbitrary page counts and sizes produce unique, gap-free pageOrder values") {
        // **Validates: Requirements 2.3**

        checkAll(
            iterations = 30,
            genA = Arb.int(1..10),   // pageCount
            genB = Arb.int(5..50)    // pageSize
        ) { pageCount, pageSize ->

            // Build page map: each page has exactly pageSize articles
            val pageMap = (1..pageCount).associateWith { page ->
                (0 until pageSize).map { index -> fakeSummary(page, index) }
            }

            val scraper = buildFakeScraperForPbt(pageMap)
            val remoteKeyDao = buildFakeRemoteKeyDaoForPbt()
            val writtenEntities = mutableListOf<ArticleSummaryEntity>()
            val articleDao = buildFakeArticleDaoForPbt(writtenEntities)

            val mediator = ArticleRemoteMediator(
                category = null,
                scraper = scraper,
                articleDao = articleDao,
                remoteKeyDao = remoteKeyDao
            )

            val state = emptyPagingStateForPbt(pageSize)

            // Load page 1 via REFRESH, then remaining pages via APPEND
            mediator.load(LoadType.REFRESH, state)
            for (p in 2..pageCount) {
                mediator.load(LoadType.APPEND, state)
            }

            val totalExpected = pageCount * pageSize

            // Validate total count
            writtenEntities.size shouldBe totalExpected

            val pageOrders = writtenEntities.map { it.pageOrder }

            // 1. All values are unique (no duplicates)
            val distinctCount = pageOrders.distinct().size
            distinctCount shouldBe totalExpected

            // 2. Minimum value is 0
            pageOrders.min() shouldBe 0

            // 3. Maximum value is pageCount * pageSize - 1
            pageOrders.max() shouldBe totalExpected - 1

            // 4. Values form a contiguous range [0, totalExpected - 1]
            val pageOrderSet = pageOrders.toSet()
            val expectedRange = (0 until totalExpected).toSet()
            pageOrderSet shouldBe expectedRange
        }
    }
})
