package com.aeonreader.data.repository

interface ReadingProgressRepository {
    suspend fun saveProgress(articleUrl: String, progressPercent: Float)
    suspend fun getProgress(articleUrl: String): Float?
    suspend fun clearProgress(articleUrl: String)
}
