package com.aeonreader.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeonreader.data.local.DailyActivityEntity
import com.aeonreader.data.repository.StatsRepository
import com.aeonreader.domain.CategoryStat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface StatsUiState {
    data object Loading : StatsUiState
    data class Success(
        val streak: Int,
        val articlesThisWeek: Int,
        val articlesThisMonth: Int,
        val totalArticlesRead: Int,
        val totalReadingTimeMinutes: Int,
        val categoryBreakdown: List<CategoryStat>,
        val dailyActivity: List<DailyActivityEntity>
    ) : StatsUiState
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val statsRepository: StatsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<StatsUiState>(StatsUiState.Loading)
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            _uiState.value = StatsUiState.Loading
            val overview = statsRepository.getStatsOverview()

            viewModelScope.launch {
                combine(
                    statsRepository.getCategoryBreakdown(),
                    statsRepository.getDailyActivity(14)
                ) { breakdown, daily ->
                    overview to (breakdown to daily)
                }.collect { (ov, pair) ->
                    val (breakdown, daily) = pair
                    _uiState.value = StatsUiState.Success(
                        streak = ov.readingStreak,
                        articlesThisWeek = ov.articlesThisWeek,
                        articlesThisMonth = ov.articlesThisMonth,
                        totalArticlesRead = ov.totalArticlesRead,
                        totalReadingTimeMinutes = ov.totalReadingTimeMinutes,
                        categoryBreakdown = breakdown,
                        dailyActivity = daily
                    )
                }
            }
        }
    }
}
