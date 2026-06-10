package com.aeonreader.data.repository

import com.aeonreader.domain.ReadingProgress
import kotlinx.coroutines.flow.Flow

data class ReadingProgressInfo(
    val lastBlockIndex: Int,
    val totalBlocks: Int
)

interface ReadingProgressRepository {
    suspend fun saveProgress(articleUrl: String, blockIndex: Int, totalBlocks: Int)
    suspend fun getProgress(articleUrl: String): ReadingProgressInfo?
    suspend fun clearProgress(articleUrl: String)
    suspend fun getAllProgress(): List<ReadingProgress>
    fun observeAllProgress(): Flow<List<ReadingProgress>>
}

data class KeepReadingItem(
    val url: String,
    val title: String,
    val author: String?,
    val heroImageUrl: String?,
    val progressPercent: Float
)
