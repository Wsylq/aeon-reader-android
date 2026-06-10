package com.aeonreader.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class ArticleSummaryProjection(
    val url: String,
    val title: String,
    val category: String?,
    val heroImageUrl: String?,
    val estimatedReadingTimeMinutes: Int,
    val cachedAt: Long,
    val lastAccessedAt: Long,
    val page: Int,
    val pageOrder: Int
)

@Dao
interface ArticleDao {

    @Upsert
    suspend fun upsertSummaries(summaries: List<ArticleSummaryEntity>)

    @Query("SELECT url, title, category, heroImageUrl, estimatedReadingTimeMinutes, cachedAt, lastAccessedAt, page, pageOrder FROM article_summaries WHERE category = :category AND pageOrder > :afterPageOrder ORDER BY readCount ASC, pageOrder ASC LIMIT :limit")
    suspend fun getSummariesByCategoryAfter(category: String, afterPageOrder: Int, limit: Int): List<ArticleSummaryProjection>

    @Query("SELECT url, cachedAt FROM article_summaries WHERE url IN (:urls)")
    suspend fun getSummaryTimestamps(urls: List<String>): List<UrlTimestamp>

    @Query("SELECT url, title, category, heroImageUrl, estimatedReadingTimeMinutes, cachedAt, lastAccessedAt, page, pageOrder FROM article_summaries WHERE pageOrder > :afterPageOrder ORDER BY readCount ASC, pageOrder ASC LIMIT :limit")
    suspend fun getAllSummariesAfter(afterPageOrder: Int, limit: Int): List<ArticleSummaryProjection>

    @Query("UPDATE article_summaries SET readCount = readCount + 1 WHERE url = :url")
    suspend fun incrementReadCount(url: String)

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
    fun getCachedArticleUrls(): Flow<List<String>>

    @Query("SELECT url FROM articles")
    suspend fun getAllArticleUrls(): List<String>

    @Query("SELECT * FROM articles")
    suspend fun getAllArticles(): List<ArticleEntity>

    @Query("SELECT * FROM word_definitions WHERE word = :word")
    suspend fun getWordDefinition(word: String): WordDefinitionEntity?

    @Upsert
    suspend fun upsertWordDefinition(entity: WordDefinitionEntity)

    @Query("DELETE FROM word_definitions WHERE cachedAt < :cutoffMillis")
    suspend fun deleteOldDefinitions(cutoffMillis: Long)

    @Query("SELECT word FROM highlighted_words WHERE articleUrl = :articleUrl")
    fun getHighlightedWords(articleUrl: String): Flow<List<String>>

    @Upsert
    suspend fun upsertHighlightedWord(entity: HighlightedWordEntity)

    @Query("DELETE FROM highlighted_words WHERE articleUrl = :articleUrl AND word = :word")
    suspend fun removeHighlightedWord(articleUrl: String, word: String)
}
