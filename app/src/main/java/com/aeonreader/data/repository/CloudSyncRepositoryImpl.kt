package com.aeonreader.data.repository

import com.aeonreader.data.cloudflare.BookmarkPayload
import com.aeonreader.data.cloudflare.CloudflareApiService
import com.aeonreader.data.cloudflare.HistoryPayload
import com.aeonreader.data.cloudflare.ProgressPayload
import com.aeonreader.data.local.BookmarkDao
import com.aeonreader.data.local.ReadingProgressDao
import com.aeonreader.data.local.ArticleDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudSyncRepositoryImpl @Inject constructor(
    private val api: CloudflareApiService,
    private val userPreferences: UserPreferencesRepository,
    private val bookmarkDao: BookmarkDao,
    private val progressDao: ReadingProgressDao,
    private val articleDao: ArticleDao
) : CloudSyncRepository {

    override suspend fun syncBookmarks(): Result<Int> {
        val token = userPreferences.getAuthToken() ?: return Result.failure(Exception("Not logged in"))
        val localBookmarks = bookmarkDao.getAll()
        if (localBookmarks.isEmpty()) return Result.success(0)
        val payloads = localBookmarks.map { entity ->
            BookmarkPayload(
                articleUrl = entity.articleUrl,
                title = entity.title,
                author = entity.author,
                heroImageUrl = entity.heroImageUrl,
                bookmarkedAt = entity.bookmarkedAt.toString()
            )
        }
        return api.syncBookmarks(token, payloads).map { it.synced }
    }

    override suspend fun syncProgress(): Result<Int> {
        val token = userPreferences.getAuthToken() ?: return Result.failure(Exception("Not logged in"))
        val articleDao2 = articleDao
        val urls = articleDao2.getAllArticleUrls()
        if (urls.isEmpty()) return Result.success(0)

        val payloads = mutableListOf<ProgressPayload>()
        for (url in urls) {
            val progress = progressDao.get(url) ?: continue
            payloads.add(
                ProgressPayload(
                    articleUrl = progress.articleUrl,
                    lastBlockIndex = progress.lastBlockIndex,
                    totalBlocks = progress.totalBlocks,
                    savedAt = progress.savedAt.toString()
                )
            )
        }
        if (payloads.isEmpty()) return Result.success(0)
        return api.syncProgress(token, payloads).map { it.synced }
    }

    override suspend fun syncHistory(): Result<Int> {
        val token = userPreferences.getAuthToken() ?: return Result.failure(Exception("Not logged in"))
        val articles = articleDao.getAllArticles()
        if (articles.isEmpty()) return Result.success(0)
        val payloads = articles.map { entity ->
            HistoryPayload(
                articleUrl = entity.url,
                title = entity.title,
                author = entity.author,
                heroImageUrl = entity.heroImageUrl,
                category = entity.category,
                readAt = entity.lastAccessedAt.toString()
            )
        }
        return api.syncHistory(token, payloads).map { it.synced }
    }

    override suspend fun syncAll(): Result<SyncAllResult> {
        val bookmarksResult = syncBookmarks()
        val progressResult = syncProgress()
        val historyResult = syncHistory()
        val b = bookmarksResult.getOrElse { 0 }
        val p = progressResult.getOrElse { 0 }
        val h = historyResult.getOrElse { 0 }
        return Result.success(SyncAllResult(b, p, h))
    }
}
