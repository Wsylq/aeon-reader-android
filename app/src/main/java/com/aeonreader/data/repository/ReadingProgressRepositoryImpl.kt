package com.aeonreader.data.repository

import com.aeonreader.data.local.ReadingProgressDao
import com.aeonreader.data.local.ReadingProgressEntity
import com.aeonreader.domain.ReadingProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
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

    override suspend fun getAllProgress(): List<ReadingProgress> {
        return readingProgressDao.getAll().map { it.toDomainProgress() }
    }

    override fun observeAllProgress(): Flow<List<ReadingProgress>> {
        return readingProgressDao.observeAll().map { entities ->
            entities.map { it.toDomainProgress() }
        }
    }

    private fun ReadingProgressEntity.toDomainProgress(): ReadingProgress {
        val percent = if (totalBlocks > 0) {
            (lastBlockIndex.toFloat() / totalBlocks * 100).coerceAtMost(100f)
        } else 0f
        return ReadingProgress(
            articleUrl = articleUrl,
            progressPercent = percent,
            savedAt = Instant.ofEpochMilli(savedAt)
        )
    }
}
