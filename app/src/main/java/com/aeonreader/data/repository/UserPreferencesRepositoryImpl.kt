package com.aeonreader.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aeonreader.domain.ThemeOverride
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : UserPreferencesRepository {

    companion object {
        private val SELECTED_CATEGORY = stringPreferencesKey("selected_category")
        private val THEME_OVERRIDE = stringPreferencesKey("theme_override")
    }

    override val selectedCategory: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SELECTED_CATEGORY] ?: "all"
    }

    override val themeOverride: Flow<ThemeOverride> = context.dataStore.data.map { prefs ->
        when (prefs[THEME_OVERRIDE]) {
            "LIGHT" -> ThemeOverride.LIGHT
            "DARK" -> ThemeOverride.DARK
            else -> ThemeOverride.NONE
        }
    }

    override suspend fun setSelectedCategory(category: String) {
        context.dataStore.edit { prefs ->
            prefs[SELECTED_CATEGORY] = category
        }
    }

    override suspend fun setThemeOverride(override: ThemeOverride) {
        context.dataStore.edit { prefs ->
            prefs[THEME_OVERRIDE] = when (override) {
                ThemeOverride.NONE -> "NONE"
                ThemeOverride.LIGHT -> "LIGHT"
                ThemeOverride.DARK -> "DARK"
            }
        }
    }
}
