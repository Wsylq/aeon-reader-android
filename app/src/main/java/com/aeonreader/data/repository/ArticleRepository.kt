package com.aeonreader.data.repository

import androidx.paging.PagingData
import com.aeonreader.data.local.ArticleSummaryEntity
import com.aeonreader.domain.Article
import kotlinx.coroutines.flow.Flow

interface ArticleRepository {
    fun getFeedPager(category: String?): Flow<PagingData<ArticleSummaryEntity>>
    suspend fun getArticle(url: String): Result<Article>
    suspend fun getCachedArticle(url: String): Article?
    suspend fun getCategories(): Result<List<String>>
    fun observeNetworkStatus(): Flow<Boolean>
}
