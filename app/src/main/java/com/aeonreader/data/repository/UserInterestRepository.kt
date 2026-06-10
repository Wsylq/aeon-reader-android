package com.aeonreader.data.repository

interface UserInterestRepository {
    suspend fun updateOnRead(category: String?, title: String)
    suspend fun getScore(category: String?, title: String): Float
    suspend fun rescoreAll(existingScores: Map<String, Float>): Map<String, Float>
}
