package com.aeonreader.data.repository

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aeonreader.data.local.ArticleDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class CacheEvictionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val articleDao: ArticleDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val now = System.currentTimeMillis()
            val sevenDaysAgo = now - 7L * 24 * 60 * 60 * 1000
            articleDao.deleteArticlesOlderThan(sevenDaysAgo)
            articleDao.deleteSummariesOlderThan(sevenDaysAgo)

            var totalSize = articleDao.getTotalCacheSize()
            val maxSize = 100L * 1024 * 1024
            while (totalSize > maxSize) {
                articleDao.deleteLeastRecentlyAccessed(10)
                totalSize = articleDao.getTotalCacheSize()
            }

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
