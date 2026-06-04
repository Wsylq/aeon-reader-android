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
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * ArticleRemoteMediatorPreservationTest
 *
 * **Sub-test E — Scroll-Position Stability on Page Append**
 *
 * Validates: Requirement 3.4
 *
 * EXPECTED OUTCOME ON UNFIXED CODE: PASSES
 *
 * The test loads page 1 (20 articles), records the ordered list of their URLs
 * as written to the DAO, then loads page 2 (20 more articles). It re-queries
 * the page 1 items from the in-memory store and asserts their URL order is
 * identical to the pre-append recording.
 *
 * Rationale for passing on unfixed code:
 *   The non-unique pageOrder bug (task 1B) makes the *paging source sort order*
 *   ambiguous when multiple pages are loaded simultaneously, but the mediator
 *   itself writes each page's rows independently. The rows written for page 1
 *   on the first load are not deleted or reordered by the page 2 write; Room's
 *   UPSERT only updates the row if the URL (PrimaryKey) already exists.
 *   Therefore, the URL ordering within page 1's written entities is stable
 *   before and after a page 2 append — the preservation property holds on
 *   unfixed code.
 *
 * **Validates: Requirements 3.4**
 */
@OptIn(ExperimentalPagingApi::class)
class ArticleRemoteMediatorPreservationTest : FunSpec({

    // ---------------------------------------------------------------------------
    // Helpers
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
     * Re-upsert with same URL updates in place (preserving the existing record's
     * position for the URL list).
     */
    fun buildFakeArticleDao(
        writtenEntities: MutableList<ArticleSummaryEntity>
    ): ArticleDao {
        return object : ArticleDao {
            override suspend fun upsertSummaries(summaries: List<ArticleSummaryEntity>) {
                for (newEntity in summaries) {
                    val existingIndex = writtenEntities.indexOfFirst { it.url == newEntity.url }
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

    // ---------------------------------------------------------------------------
    // Sub-test E — Scroll-Position Stability on Page Append
    // ---------------------------------------------------------------------------
    test("Sub-test E: page 1 URL order is unchanged after page 2 is appended (passes on unfixed code)") {
        /**
         * **Validates: Requirements 3.4**
         *
         * Load page 1 (20 articles), record ordered URL list.
         * Load page 2 (20 articles), re-query page 1 items from store.
         * Assert: the page 1 URL list is identical after the append.
         *
         * This is scoped to the non-bug-condition path: appending a page via the
         * mediator does NOT reorder already-written rows in the DAO store. The
         * non-unique pageOrder bug manifests at the paging source *query* layer
         * (ambiguous ORDER BY), not at the mediator write layer tested here.
         *
         * EXPECTED OUTCOME: PASSES on unfixed code.
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

        // --- Load page 1 ---
        mediator.load(LoadType.REFRESH, emptyPagingState)

        // Record the ordered URL list for page 1 items (in insertion order)
        val page1UrlsBeforeAppend: List<String> = writtenEntities
            .filter { it.page == 1 }
            .map { it.url }

        // Sanity check: page 1 must have 20 items
        page1UrlsBeforeAppend.size shouldBe 20

        // --- Load page 2 (append) ---
        mediator.load(LoadType.APPEND, emptyPagingState)

        // Re-query page 1 items from the store after the append
        val page1UrlsAfterAppend: List<String> = writtenEntities
            .filter { it.page == 1 }
            .map { it.url }

        // Assert: page 1 URL list is identical before and after page 2 append
        page1UrlsAfterAppend shouldBe page1UrlsBeforeAppend
    }

    test("Sub-test E: total entity count after page 2 append equals page1 + page2 size (passes on unfixed code)") {
        /**
         * **Validates: Requirements 3.4**
         *
         * Additional sanity check: appending page 2 should add 20 NEW entities
         * (none of the URLs collide), bringing the total to 40. This confirms
         * the mediator does not accidentally overwrite or drop page 1 rows.
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

        mediator.load(LoadType.REFRESH, emptyPagingState)
        mediator.load(LoadType.APPEND, emptyPagingState)

        // No URL collisions between page 1 and page 2 → 40 distinct entries
        writtenEntities.size shouldBe 40
        writtenEntities.map { it.url }.toSet().size shouldBe 40
    }
})
