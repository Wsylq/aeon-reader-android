package com.aeonreader.data.repository

import com.aeonreader.data.local.DailyActivityEntity
import com.aeonreader.data.local.StatsDao
import com.aeonreader.domain.CategoryStat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

data class StatsOverview(
    val readingStreak: Int,
    val articlesThisWeek: Int,
    val articlesThisMonth: Int,
    val totalArticlesRead: Int,
    val totalReadingTimeMinutes: Int,
    val categoryBreakdown: List<CategoryStat>,
    val dailyActivity: List<DailyActivityEntity>
)

interface StatsRepository {
    suspend fun getStatsOverview(): StatsOverview
    fun getCategoryBreakdown(): Flow<List<CategoryStat>>
    fun getDailyActivity(days: Int): Flow<List<DailyActivityEntity>>
    suspend fun recordSession(
        articleUrl: String,
        articleTitle: String,
        category: String?,
        wordCount: Int,
        progressPercent: Float
    )
}

@Singleton
class StatsRepositoryImpl @Inject constructor(
    private val dao: StatsDao
) : StatsRepository {

    override suspend fun getStatsOverview(): StatsOverview {
        val now = System.currentTimeMillis()
        val weekAgo = now - 7L * 24 * 60 * 60 * 1000
        val monthAgo = now - 30L * 24 * 60 * 60 * 1000

        val streak = calculateStreak()
        val articlesThisWeek = dao.getArticleCountSince(weekAgo)
        val articlesThisMonth = dao.getArticleCountSince(monthAgo)
        val totalArticles = dao.getTotalArticlesRead()
        val totalWords = dao.getTotalWordCount()
        val totalMinutes = totalWords / 200

        return StatsOverview(
            readingStreak = streak,
            articlesThisWeek = articlesThisWeek,
            articlesThisMonth = articlesThisMonth,
            totalArticlesRead = totalArticles,
            totalReadingTimeMinutes = totalMinutes,
            categoryBreakdown = emptyList(),
            dailyActivity = emptyList()
        )
    }

    override fun getCategoryBreakdown(): Flow<List<CategoryStat>> {
        return dao.getCategoryBreakdown().map { counts ->
            val total = counts.sumOf { it.count }
            if (total == 0) emptyList()
            else counts.map { CategoryStat(it.category, it.count, it.count.toFloat() / total) }
        }
    }

    override fun getDailyActivity(days: Int): Flow<List<DailyActivityEntity>> {
        val since = System.currentTimeMillis() - days.toLong() * 24 * 60 * 60 * 1000
        return dao.getDailyActivity(since)
    }

    override suspend fun recordSession(
        articleUrl: String,
        articleTitle: String,
        category: String?,
        wordCount: Int,
        progressPercent: Float
    ) {
        dao.insert(
            com.aeonreader.data.local.ReadingSessionEntity(
                articleUrl = articleUrl,
                articleTitle = articleTitle,
                category = category,
                dateRead = System.currentTimeMillis(),
                wordCount = wordCount,
                progressPercent = progressPercent
            )
        )
    }

    private suspend fun calculateStreak(): Int {
        val days = dao.getReadingDays()
        if (days.isEmpty()) return 0

        val today = LocalDate.now()
        var streak = 0
        for (i in days.indices) {
            val day = try {
                LocalDate.parse(days[i])
            } catch (e: Exception) {
                break
            }
            if (day == today.minusDays(streak.toLong())) {
                streak++
            } else {
                break
            }
        }
        return streak
    }
}
