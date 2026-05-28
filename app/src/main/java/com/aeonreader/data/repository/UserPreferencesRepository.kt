package com.aeonreader.data.repository

import com.aeonreader.domain.ThemeOverride
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val selectedCategory: Flow<String>
    val themeOverride: Flow<ThemeOverride>
    suspend fun setSelectedCategory(category: String)
    suspend fun setThemeOverride(override: ThemeOverride)
}
