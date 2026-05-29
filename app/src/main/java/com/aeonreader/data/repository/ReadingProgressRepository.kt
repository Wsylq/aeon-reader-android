package com.aeonreader.data.repository

interface ReadingProgressRepository {
    suspend fun saveProgress(articleUrl: String, blockIndex: Int)
    suspend fun getProgress(articleUrl: String): Int?
    suspend fun clearProgress(articleUrl: String)
}
