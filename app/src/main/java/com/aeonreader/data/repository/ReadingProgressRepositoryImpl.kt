package com.aeonreader.data.repository

import com.aeonreader.data.local.ReadingProgressDao
import com.aeonreader.data.local.ReadingProgressEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingProgressRepositoryImpl @Inject constructor(
    private val readingProgressDao: ReadingProgressDao
) : ReadingProgressRepository {

    override suspend fun saveProgress(articleUrl: String, blockIndex: Int, totalBlocks: Int) {
        val entity = ReadingProgressEntity(
            articleUrl = articleUrl,
            lastBlockIndex = blockIndex,
            totalBlocks = totalBlocks,
            savedAt = System.currentTimeMillis()
        )
        readingProgressDao.upsert(entity)
    }

    override suspend fun getProgress(articleUrl: String): ReadingProgressInfo? {
        val entity = readingProgressDao.get(articleUrl) ?: return null
        return ReadingProgressInfo(
            lastBlockIndex = entity.lastBlockIndex,
            totalBlocks = entity.totalBlocks
        )
    }

    override suspend fun clearProgress(articleUrl: String) {
        readingProgressDao.delete(articleUrl)
    }
}
