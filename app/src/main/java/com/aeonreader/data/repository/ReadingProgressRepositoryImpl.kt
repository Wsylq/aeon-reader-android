package com.aeonreader.data.repository

import com.aeonreader.data.local.ReadingProgressDao
import com.aeonreader.data.local.ReadingProgressEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingProgressRepositoryImpl @Inject constructor(
    private val readingProgressDao: ReadingProgressDao
) : ReadingProgressRepository {

    override suspend fun saveProgress(articleUrl: String, progressPercent: Float) {
        val entity = ReadingProgressEntity(
            articleUrl = articleUrl,
            progressPercent = progressPercent,
            savedAt = System.currentTimeMillis()
        )
        readingProgressDao.upsert(entity)
    }

    override suspend fun getProgress(articleUrl: String): Float? {
        return readingProgressDao.get(articleUrl)?.progressPercent
    }

    override suspend fun clearProgress(articleUrl: String) {
        readingProgressDao.delete(articleUrl)
    }
}
