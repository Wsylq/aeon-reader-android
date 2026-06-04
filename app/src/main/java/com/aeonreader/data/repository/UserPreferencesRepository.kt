package com.aeonreader.data.repository

import com.aeonreader.domain.FeedLayout
import com.aeonreader.domain.ReadingPreferences
import com.aeonreader.domain.ThemeOverride
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val selectedCategory: Flow<String>
    val themeOverride: Flow<ThemeOverride>
    val readingPreferences: Flow<ReadingPreferences>
    val feedLayout: Flow<FeedLayout>
    suspend fun setFeedLayout(layout: FeedLayout)
    suspend fun setSelectedCategory(category: String)
    suspend fun setThemeOverride(override: ThemeOverride)
    suspend fun setReadingPreferences(prefs: ReadingPreferences)
}
