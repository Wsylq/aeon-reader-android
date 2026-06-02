package com.aeonreader.data.repository

data class ReadingProgressInfo(
    val lastBlockIndex: Int,
    val totalBlocks: Int
)

interface ReadingProgressRepository {
    suspend fun saveProgress(articleUrl: String, blockIndex: Int, totalBlocks: Int)
    suspend fun getProgress(articleUrl: String): ReadingProgressInfo?
    suspend fun clearProgress(articleUrl: String)
}
