package com.aeonreader.data.repository

import androidx.paging.PagingData
import com.aeonreader.data.local.ArticleSummaryProjection
import com.aeonreader.domain.Article
import com.aeonreader.domain.ArticleSummary
import kotlinx.coroutines.flow.Flow

interface ArticleRepository {
    fun getFeedPager(category: String?): Flow<PagingData<ArticleSummaryProjection>>
    suspend fun getArticle(url: String): Result<Article>
    suspend fun getCachedArticle(url: String): Article?
    suspend fun getCategories(): Result<List<String>>
    fun observeNetworkStatus(): Flow<Boolean>
    fun observeCachedArticleUrls(): Flow<Set<String>>
    suspend fun incrementReadCount(url: String)
    suspend fun searchCached(query: String, limit: Int = 100): List<ArticleSummary>

    /** Hits in the local archive index. Empty until [refreshArchiveIndex] has run. */
    suspend fun searchArchive(query: String, limit: Int = 100): List<ArticleSummary>

    suspend fun archiveIndexSize(): Int

    /**
     * Rebuilds the local search index from every section and subtopic feed.
     * No-ops when the index is already fresher than a day unless [force].
     */
    suspend fun refreshArchiveIndex(
        force: Boolean = false,
        onProgress: ((done: Int, total: Int) -> Unit)? = null
    ): Result<Int>
}
