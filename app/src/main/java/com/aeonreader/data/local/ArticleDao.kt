package com.aeonreader.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ArticleDao {

    @Upsert
    suspend fun upsertSummaries(summaries: List<ArticleSummaryEntity>)

    @Query("SELECT * FROM article_summaries WHERE category = :category ORDER BY pageOrder ASC")
    fun getSummariesByCategory(category: String): PagingSource<Int, ArticleSummaryEntity>

    @Query("SELECT * FROM article_summaries ORDER BY pageOrder ASC")
    fun getAllSummaries(): PagingSource<Int, ArticleSummaryEntity>

    @Query("SELECT * FROM articles WHERE url = :url")
    suspend fun getArticle(url: String): ArticleEntity?

    @Upsert
    suspend fun upsertArticle(entity: ArticleEntity)

    @Query("DELETE FROM articles WHERE cachedAt < :cutoffMillis")
    suspend fun deleteArticlesOlderThan(cutoffMillis: Long)

    @Query("DELETE FROM articles WHERE url IN (SELECT url FROM articles ORDER BY lastAccessedAt ASC LIMIT :count)")
    suspend fun deleteLeastRecentlyAccessed(count: Int)

    @Query("UPDATE articles SET lastAccessedAt = :time WHERE url = :url")
    suspend fun updateLastAccessed(url: String, time: Long)

    @Query("DELETE FROM article_summaries WHERE cachedAt < :cutoffMillis")
    suspend fun deleteSummariesOlderThan(cutoffMillis: Long)

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM articles")
    suspend fun getTotalCacheSize(): Long

    @Query("SELECT url FROM articles")
    fun getCachedArticleUrls(): kotlinx.coroutines.flow.Flow<List<String>>
}
