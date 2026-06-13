package com.aeonreader.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class CategoryCount(val category: String, val count: Int)

data class DailyActivityEntity(val day: String, val count: Int)

@Dao
interface StatsDao {

    @Upsert
    suspend fun insert(session: ReadingSessionEntity)

    @Query("SELECT DISTINCT date(dateRead / 1000, 'unixepoch') as day FROM reading_sessions ORDER BY day DESC")
    suspend fun getReadingDays(): List<String>

    @Query("SELECT COUNT(*) FROM reading_sessions WHERE dateRead >= :since")
    suspend fun getArticleCountSince(since: Long): Int

    @Query("SELECT COALESCE(SUM(wordCount), 0) FROM reading_sessions WHERE dateRead >= :since")
    suspend fun getWordCountSince(since: Long): Int

    @Query("SELECT category, COUNT(*) as count FROM reading_sessions WHERE category IS NOT NULL GROUP BY category ORDER BY count DESC")
    fun getCategoryBreakdown(): Flow<List<CategoryCount>>

    @Query("SELECT date(dateRead / 1000, 'unixepoch') as day, COUNT(*) as count FROM reading_sessions WHERE dateRead >= :since GROUP BY day ORDER BY day")
    fun getDailyActivity(since: Long): Flow<List<DailyActivityEntity>>

    @Query("SELECT COUNT(DISTINCT articleUrl) FROM reading_sessions")
    suspend fun getTotalArticlesRead(): Int

    @Query("SELECT COALESCE(SUM(wordCount), 0) FROM reading_sessions")
    suspend fun getTotalWordCount(): Int
}
