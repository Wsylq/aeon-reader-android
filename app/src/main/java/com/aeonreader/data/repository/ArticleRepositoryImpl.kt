package com.aeonreader.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.aeonreader.data.cache.ImageCache
import com.aeonreader.data.local.ArticleDao
import com.aeonreader.data.local.ArticleEntity
import com.aeonreader.data.local.ArticleSummaryEntity
import com.aeonreader.data.local.ArticleSummaryProjection
import com.aeonreader.data.local.RemoteKeyDao
import com.aeonreader.data.local.RemoteKeyEntity
import com.aeonreader.data.network.AeonParser
import com.aeonreader.data.network.AeonScraper
import com.aeonreader.data.network.NetworkMonitor
import com.aeonreader.domain.Article
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalPagingApi::class)
@Singleton
class ArticleRepositoryImpl @Inject constructor(
    private val scraper: AeonScraper,
    private val parser: AeonParser,
    private val articleDao: ArticleDao,
    private val remoteKeyDao: RemoteKeyDao,
    private val networkMonitor: NetworkMonitor,
    private val imageCache: ImageCache
) : ArticleRepository {

    override fun getFeedPager(category: String?): Flow<PagingData<ArticleSummaryProjection>> {
        return Pager(
            config = PagingConfig(pageSize = 20, initialLoadSize = 20, prefetchDistance = 8, enablePlaceholders = false),
            remoteMediator = ArticleRemoteMediator(
                category = category,
                scraper = scraper,
                articleDao = articleDao,
                remoteKeyDao = remoteKeyDao
            ),
            pagingSourceFactory = {
                if (category != null && category != "all") {
                    articleDao.getSummariesByCategory(category)
                } else {
                    articleDao.getAllSummaries()
                }
            }
        ).flow.flowOn(Dispatchers.IO)
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
    private val remoteKeyDao: RemoteKeyDao
) : RemoteMediator<Int, ArticleSummaryProjection>() {

    override suspend fun initialize(): RemoteMediator.InitializeAction {
        val key = remoteKeyDao.get(category ?: "all")
        val isFresh = key != null && (System.currentTimeMillis() - key.lastUpdated) < 5 * 60 * 1000L
        return if (isFresh) RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH
        else RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH
    }

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, ArticleSummaryProjection>
    ): RemoteMediator.MediatorResult {
        return try {
            val page = when (loadType) {
                LoadType.REFRESH -> {
                    val key = remoteKeyDao.get(category ?: "all")
                    key?.nextPage ?: 1
                }
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
                        pageOrder = (page - 1) * state.config.pageSize + index
                    )
                }

            if (entities.isNotEmpty()) {
                articleDao.upsertSummaries(entities)
                remoteKeyDao.upsert(
                    RemoteKeyEntity(
                        category = category ?: "all",
                        nextPage = if (summaries.isEmpty()) null else page + 1,
                        lastUpdated = now
                    )
                )
            }

            RemoteMediator.MediatorResult.Success(
                endOfPaginationReached = summaries.isEmpty()
            )
        } catch (e: Exception) {
            RemoteMediator.MediatorResult.Error(e)
        }
    }
}
