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
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Sub-test B — Non-Unique Sort Keys
 *
 * Validates: Bug Condition 2 (Requirements 1.3, 2.3)
 *
 * EXPECTED OUTCOME ON UNFIXED CODE: FAILS
 *
 * The test loads two pages of 20 articles each via ArticleRemoteMediator.
 * It collects all pageOrder values written to the fake DAO and asserts that
 * the 40 values form a set of exactly 40 unique integers.
 *
 * On UNFIXED code this FAILS because pageOrder = index resets to 0 for every
 * page. Both page 1 and page 2 produce pageOrder values {0,1,…,19}, yielding
 * only 20 unique values in the set.
 *
 * Counterexample documented:
 *   "pageOrder set size = 20 instead of 40; duplicate keys: {0,1,…,19}
 *    — page 1 and page 2 both assign pageOrder 0–19, colliding in Room's index."
 */
@OptIn(ExperimentalPagingApi::class)
class ArticleRemoteMediatorTest : FunSpec({

    // Helper: build a fake ArticleSummary for a given page and position
    fun fakeSummary(page: Int, index: Int) = ArticleSummary(
        url = "https://aeon.co/article/page${page}_item${index}",
        title = "Article $page/$index",
        description = null,
        author = null,
        category = null,
        heroImageUrl = null,
        estimatedReadingTimeMinutes = 5,
    )

    fun buildFakeScraper(
        page1Summaries: List<ArticleSummary>,
        page2Summaries: List<ArticleSummary>
    ): AeonScraper {
        return object : AeonScraper {
            override suspend fun fetchFeed(page: Int): Result<List<ArticleSummary>> {
                return when (page) {
                    1 -> Result.success(page1Summaries)
                    2 -> Result.success(page2Summaries)
                    else -> Result.success(emptyList())
                }
            }

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

    test("Sub-test B: pageOrder values across 2 pages form 40 unique integers (BUG: fails on unfixed code)") {
        val page1Summaries = (0 until 20).map { i -> fakeSummary(1, i) }
        val page2Summaries = (0 until 20).map { i -> fakeSummary(2, i) }

        val writtenEntities = mutableListOf<ArticleSummaryEntity>()
        val articleDao = buildFakeArticleDao(writtenEntities)
        val remoteKeyDao = buildFakeRemoteKeyDao()
        val scraper = buildFakeScraper(page1Summaries, page2Summaries)

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

        // Load page 1 (REFRESH loads from page 1 when no remote key exists)
        mediator.load(LoadType.REFRESH, emptyPagingState)

        // Load page 2 (APPEND reads nextPage = 2 from remote key set after page 1)
        mediator.load(LoadType.APPEND, emptyPagingState)

        // Collect all pageOrder values written across both page loads
        val allPageOrders = writtenEntities.map { it.pageOrder }

        // Expect 40 entities total (20 per page)
        allPageOrders shouldHaveSize 40

        // Assert: 40 unique pageOrder values — no duplicates across pages.
        // On UNFIXED code this FAILS: both pages produce {0,1,…,19},
        // so distinctValues.size = 20, not 40.
        val distinctPageOrders = allPageOrders.toSet()
        distinctPageOrders.size shouldBe 40
    }
})
