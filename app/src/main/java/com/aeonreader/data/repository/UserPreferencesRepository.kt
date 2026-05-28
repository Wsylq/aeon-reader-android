package com.aeonreader.data.repository

import com.aeonreader.domain.ReadingPreferences
import com.aeonreader.domain.ThemeOverride
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val selectedCategory: Flow<String>
    val themeOverride: Flow<ThemeOverride>
    val readingPreferences: Flow<ReadingPreferences>
    suspend fun setSelectedCategory(category: String)
    suspend fun setThemeOverride(override: ThemeOverride)
    suspend fun setReadingPreferences(prefs: ReadingPreferences)
}
