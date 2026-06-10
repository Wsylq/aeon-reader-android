package com.aeonreader.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingProgressDao {

    @Upsert
    suspend fun upsert(entity: ReadingProgressEntity)

    @Query("SELECT * FROM reading_progress WHERE articleUrl = :url")
    suspend fun get(url: String): ReadingProgressEntity?

    @Query("DELETE FROM reading_progress WHERE articleUrl = :url")
    suspend fun delete(url: String)

    @Query("SELECT * FROM reading_progress ORDER BY savedAt DESC")
    suspend fun getAll(): List<ReadingProgressEntity>

    @Query("SELECT * FROM reading_progress ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<ReadingProgressEntity>>
}
