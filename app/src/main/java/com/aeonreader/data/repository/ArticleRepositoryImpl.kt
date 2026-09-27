package com.aeonreader.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.aeonreader.data.cache.ImageCache
import com.aeonreader.data.local.ArchiveIndexDao
import com.aeonreader.data.local.ArchiveIndexEntity
import com.aeonreader.data.local.ArticleDao
import com.aeonreader.data.local.ArticleEntity
import com.aeonreader.data.local.ArticlePagingSource
import com.aeonreader.data.local.ArticleSummaryEntity
import com.aeonreader.data.local.ArticleSummaryProjection
import com.aeonreader.data.local.RemoteKeyDao
import com.aeonreader.data.local.RemoteKeyEntity
import com.aeonreader.data.network.AeonParser
import com.aeonreader.data.network.AeonScraper
import com.aeonreader.data.network.NetworkMonitor
import com.aeonreader.domain.Article
import com.aeonreader.domain.ArticleSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton

private val WHITESPACE = Regex("\\s+")

/** The DAO's search query has a fixed number of term slots. */
private const val MAX_QUERY_TERMS = 5

/** Rebuilding the index costs ~120 requests, so at most once a day. */
private const val ARCHIVE_TTL_MS = 24L * 60L * 60L * 1000L

/**
 * LIKE wildcards typed by the user have to be neutralised, otherwise `100%`
 * turns into a prefix match and `%` alone returns the entire table.
 */
private fun escapeLike(term: String): String =
    term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

@OptIn(ExperimentalPagingApi::class)
@Singleton
class ArticleRepositoryImpl @Inject constructor(
    private val scraper: AeonScraper,
    private val parser: AeonParser,
    private val articleDao: ArticleDao,
    private val remoteKeyDao: RemoteKeyDao,
    private val archiveIndexDao: ArchiveIndexDao,
    private val networkMonitor: NetworkMonitor,
    private val imageCache: ImageCache,
    private val userInterestRepository: UserInterestRepository
) : ArticleRepository {

    private val indexMutex = Mutex()

    override fun getFeedPager(category: String?): Flow<PagingData<ArticleSummaryProjection>> {
        return Pager(
            config = PagingConfig(pageSize = 20, initialLoadSize = 40, prefetchDistance = 15, enablePlaceholders = false),
            remoteMediator = ArticleRemoteMediator(
                category = category,
                scraper = scraper,
                articleDao = articleDao,
                remoteKeyDao = remoteKeyDao,
                userInterestRepository = userInterestRepository
            ),
            pagingSourceFactory = {
                ArticlePagingSource(
                    articleDao = articleDao,
                    category = if (category != null && category != "all") category else null
                )
            }
        ).flow
    }

    override suspend fun getArticle(url: String): Result<Article> {
        val cached = articleDao.getArticle(url)
        if (cached != null) {
            val domain = cached.toDomainArticle(parser)
            if (domain != null) {
                articleDao.updateLastAccessed(url, System.currentTimeMillis())
                return Result.success(domain)
            }
        }

        return try {
            val html = scraper.fetchArticle(url).getOrElse { error ->
                return Result.failure(error)
            }
            val article = parser.parseArticle(html).getOrElse { error ->
                return Result.failure(error)
            }
            val articleWithUrl = article.copy(url = url)
            val bodyJson = parser.serialize(articleWithUrl)
            val now = System.currentTimeMillis()
            val entity = ArticleEntity(
                url = url,
                title = articleWithUrl.title,
                author = articleWithUrl.author,
                authorBio = articleWithUrl.authorBio,
                publicationDate = articleWithUrl.publicationDate?.toString(),
                category = articleWithUrl.category,
                heroImageUrl = articleWithUrl.heroImageUrl,
                bodyJson = bodyJson,
                wordCount = articleWithUrl.wordCount,
                cachedAt = now,
                lastAccessedAt = now,
                sizeBytes = bodyJson.length.toLong()
            )
            articleDao.upsertArticle(entity)
            cacheArticleImages(articleWithUrl)
            Result.success(articleWithUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCachedArticle(url: String): Article? {
        val entity = articleDao.getArticle(url) ?: return null
        articleDao.updateLastAccessed(url, System.currentTimeMillis())
        return entity.toDomainArticle(parser)
    }

    override suspend fun incrementReadCount(url: String) {
        articleDao.incrementReadCount(url)
    }

    override suspend fun getCategories(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val categories = scraper.fetchCategories().getOrElse { error ->
                return@withContext Result.failure(error)
            }
            Result.success(categories)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeNetworkStatus(): Flow<Boolean> = networkMonitor.networkStatus

    override fun observeCachedArticleUrls(): Flow<Set<String>> =
        articleDao.getCachedArticleUrls().map { it.toSet() }.distinctUntilChanged()

    /**
     * Local half of search. The live feeds only index the newest essays per
     * section, so this is what makes search reach older articles: everything the
     * feed has listed (title, dek, author) plus the full body of anything already
     * opened. It also keeps search working with no connection.
     */
    override suspend fun searchCached(query: String, limit: Int): List<ArticleSummary> =
        withContext(Dispatchers.IO) {
            val terms = query.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
            if (terms.isEmpty()) return@withContext emptyList()

            // Every term has to match, so intersect the per-term result sets.
            fun intersect(sets: List<List<ArticleSummaryProjection>>): List<ArticleSummaryProjection> =
                sets.reduceOrNull { acc, list ->
                    val allowed = list.map { it.url }.toHashSet()
                    acc.filter { it.url in allowed }
                } ?: emptyList()

            val byMetadata = intersect(terms.map { articleDao.searchSummaries(it, limit * terms.size) })
            val byBody = intersect(terms.map { articleDao.searchCachedArticleBodies(it, limit * terms.size) })

            val merged = LinkedHashMap<String, ArticleSummaryProjection>()
            (byMetadata + byBody).forEach { merged.putIfAbsent(it.url, it) }

            merged.values.take(limit).map { row ->
                ArticleSummary(
                    url = row.url,
                    title = row.title,
                    description = null,
                    author = null,
                    category = row.category,
                    heroImageUrl = row.heroImageUrl,
                    estimatedReadingTimeMinutes = row.estimatedReadingTimeMinutes,
                    cachedAt = row.cachedAt
                )
            }
        }

    /**
     * Queries the local archive index. This is the search path that actually
     * reaches into Aeon's back catalogue: the index holds every essay published
     * to the ~120 section and subtopic feeds, so a query for a topic that hasn't
     * been in the front page for months still matches.
     */
    override suspend fun searchArchive(query: String, limit: Int): List<ArticleSummary> =
        withContext(Dispatchers.IO) {
            val terms = query.trim().lowercase().split(WHITESPACE).filter { it.isNotBlank() }
            if (terms.isEmpty()) return@withContext emptyList()

            // The DAO takes a fixed number of term slots; anything past the fifth
            // word is folded into the last one so a long query can't silently
            // return the unfiltered table.
            val slots = (terms.take(MAX_QUERY_TERMS) + List(MAX_QUERY_TERMS) { "" })
                .map { escapeLike(it) }
                .toTypedArray()

            archiveIndexDao.search(
                termCount = terms.size,
                t0 = slots[0], t1 = slots[1], t2 = slots[2], t3 = slots[3], t4 = slots[4],
                limit = limit
            ).map { row ->
                ArticleSummary(
                    url = row.url,
                    title = row.title,
                    description = row.description,
                    author = row.author,
                    category = row.category,
                    heroImageUrl = row.heroImageUrl,
                    estimatedReadingTimeMinutes = row.estimatedReadingTimeMinutes,
                    cachedAt = row.indexedAt
                )
            }
        }

    override suspend fun archiveIndexSize(): Int = withContext(Dispatchers.IO) {
        archiveIndexDao.count()
    }

    /**
     * Fetches every section and subtopic feed and stores the result locally.
     *
     * This is ~120 requests, so it is deliberately rate-limited to once a day
     * and guarded against concurrent callers. The alternative — querying the
     * feeds per search — would make every search a 120-request burst.
     */
    override suspend fun refreshArchiveIndex(
        force: Boolean,
        onProgress: ((done: Int, total: Int) -> Unit)?
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (!force) {
            val lastBuilt = archiveIndexDao.lastIndexedAt() ?: 0L
            if (lastBuilt > 0L && System.currentTimeMillis() - lastBuilt < ARCHIVE_TTL_MS) {
                return@withContext Result.success(archiveIndexDao.count())
            }
        }

        // One build at a time; a second caller would double the traffic for nothing.
        if (!indexMutex.tryLock()) {
            return@withContext Result.success(archiveIndexDao.count())
        }
        try {
            // Re-check: another caller may have finished while we waited.
            if (!force) {
                val lastBuilt = archiveIndexDao.lastIndexedAt() ?: 0L
                if (lastBuilt > 0L && System.currentTimeMillis() - lastBuilt < ARCHIVE_TTL_MS) {
                    return@withContext Result.success(archiveIndexDao.count())
                }
            }

            val built = scraper.fetchArchiveIndex(onProgress).getOrElse { error ->
                // Keep whatever index we already have rather than wiping it on a
                // flaky connection — an old index beats no index.
                return@withContext if (archiveIndexDao.count() > 0) {
                    Result.success(archiveIndexDao.count())
                } else {
                    Result.failure(error)
                }
            }

            val now = System.currentTimeMillis()
            archiveIndexDao.replaceAll(built.map { summary ->
                ArchiveIndexEntity(
                    url = summary.url,
                    title = summary.title,
                    description = summary.description,
                    author = summary.author,
                    category = summary.category,
                    heroImageUrl = summary.heroImageUrl,
                    estimatedReadingTimeMinutes = summary.estimatedReadingTimeMinutes,
                    indexedAt = now
                )
            })
            Result.success(built.size)
        } finally {
            indexMutex.unlock()
        }
    }

    private suspend fun cacheArticleImages(article: Article) {
        val urls = mutableListOf<String>()
        article.heroImageUrl?.let { urls.add(it) }
        for (block in article.bodyBlocks) {
            if (block is com.aeonreader.domain.ContentBlock.InlineImage) {
                urls.add(block.url)
            }
        }
        for (url in urls) {
            try { imageCache.cacheImage(url) } catch (_: Exception) {}
        }
    }
}

private fun ArticleEntity.toDomainArticle(parser: AeonParser): Article? {
    val deserialized = parser.deserialize(bodyJson)
    val result = deserialized.getOrNull() ?: return null
    return Article(
        url = url,
        title = title,
        description = result.description,
        author = author,
        authorBio = authorBio,
        publicationDate = publicationDate?.let {
            try { LocalDate.parse(it) } catch (_: Exception) { null }
        },
        category = category,
        heroImageUrl = heroImageUrl,
        bodyBlocks = result.bodyBlocks,
        wordCount = wordCount,
        relatedArticles = result.relatedArticles
    )
}

@OptIn(ExperimentalPagingApi::class)
class ArticleRemoteMediator(
    private val category: String?,
    private val scraper: AeonScraper,
    private val articleDao: ArticleDao,
    private val remoteKeyDao: RemoteKeyDao,
    private val userInterestRepository: UserInterestRepository
) : RemoteMediator<Int, ArticleSummaryProjection>() {

    override suspend fun initialize(): RemoteMediator.InitializeAction {
        val key = remoteKeyDao.get(category ?: "all")
        val age = key?.let { System.currentTimeMillis() - it.lastUpdated } ?: Long.MAX_VALUE
        // The old comparison was `age < 0`, which is never true for a timestamp
        // in the past, so every launch refetched from scratch.
        return if (age < FRESH_WINDOW_MS) RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH
        else RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, ArticleSummaryProjection>
    ): RemoteMediator.MediatorResult {
        return try {
            val page = when (loadType) {
                // A refresh must re-read page 1. Continuing from nextPage meant
                // pull-to-refresh walked further into the archive instead of
                // picking up newly published essays.
                LoadType.REFRESH -> 1
                LoadType.PREPEND -> return RemoteMediator.MediatorResult.Success(
                    endOfPaginationReached = true
                )
                LoadType.APPEND -> {
                    val key = remoteKeyDao.get(category ?: "all")
                    key?.nextPage ?: return RemoteMediator.MediatorResult.Success(
                        endOfPaginationReached = true
                    )
                }
            }

            val summaries = if (category != null && category != "all") {
                scraper.fetchCategoryFeed(category, page).getOrElse { error ->
                    return RemoteMediator.MediatorResult.Error(error)
                }
            } else {
                scraper.fetchFeed(page).getOrElse { error ->
                    return RemoteMediator.MediatorResult.Error(error)
                }
            }

            val now = System.currentTimeMillis()
            val existingUrls = articleDao.getSummaryTimestamps(
                summaries.map { it.url }
            ).map { it.url }.toSet()
            val entities = summaries
                .filter { it.url !in existingUrls }
                .mapIndexed { index, summary ->
                    val score = userInterestRepository.getScore(summary.category, summary.title)
                    ArticleSummaryEntity(
                        url = summary.url,
                        title = summary.title,
                        description = summary.description,
                        author = summary.author,
                        category = summary.category,
                        heroImageUrl = summary.heroImageUrl,
                        estimatedReadingTimeMinutes = summary.estimatedReadingTimeMinutes,
                        cachedAt = now,
                        lastAccessedAt = now,
                        page = page,
                        pageOrder = (page - 1) * state.config.pageSize + index,
                        relevanceScore = score
                    )
                }

            if (entities.isNotEmpty()) {
                articleDao.upsertSummaries(entities)
            }

            // A section feed can carry a topic for an article the combined feed
            // already stored without one; backfill so the category tab and the
            // category index both see it.
            for (summary in summaries) {
                if (summary.category != null && summary.url in existingUrls) {
                    articleDao.updateSummaryCategory(summary.url, summary.category)
                }
            }

            // The page key has to advance even when every URL was already known,
            // otherwise a page of pure duplicates stalls pagination for good.
            remoteKeyDao.upsert(
                RemoteKeyEntity(
                    category = category ?: "all",
                    nextPage = if (summaries.isEmpty()) null else page + 1,
                    lastUpdated = now
                )
            )

            if (loadType == LoadType.REFRESH) {
                val titles = articleDao.getAllSummaryTitles()
                for (entry in titles) {
                    val score = userInterestRepository.getScore(entry.category, entry.title)
                    articleDao.updateRelevanceScore(entry.url, score)
                }
            }

            RemoteMediator.MediatorResult.Success(
                endOfPaginationReached = summaries.isEmpty()
            )
        } catch (e: Exception) {
            RemoteMediator.MediatorResult.Error(e)
        }
    }

    private companion object {
        /** How long a cached feed is considered fresh enough to skip the initial refresh. */
        const val FRESH_WINDOW_MS = 30L * 60L * 1000L
    }
}
