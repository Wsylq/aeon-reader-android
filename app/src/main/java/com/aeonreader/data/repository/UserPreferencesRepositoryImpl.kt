package com.aeonreader.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aeonreader.data.cloudflare.UserInfo
import com.aeonreader.domain.FeedLayout
import com.aeonreader.domain.ReadingFont
import com.aeonreader.domain.ReadingPreferences
import com.aeonreader.domain.ReadingTheme
import com.aeonreader.domain.ThemeOverride
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
        private val READING_FONT = stringPreferencesKey("reading_font")
        private val READING_FONT_SIZE = intPreferencesKey("reading_font_size")
        private val READING_IMMERSIVE_MODE = booleanPreferencesKey("reading_immersive_mode")
        private val READING_THEME = stringPreferencesKey("reading_theme")
        private val READING_MOTION_BLUR = booleanPreferencesKey("reading_motion_blur")
        private val READING_SHOW_RELATED = booleanPreferencesKey("reading_show_related")
        private val FEED_LAYOUT = stringPreferencesKey("feed_layout")
        private val AUTH_TOKEN = stringPreferencesKey("auth_token")
        private val USER_ID = intPreferencesKey("user_id")
        private val USER_EMAIL = stringPreferencesKey("user_email")
        private val USER_NAME = stringPreferencesKey("user_name")
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

    override val feedLayout: Flow<FeedLayout> = context.dataStore.data.map { prefs ->
        when (prefs[FEED_LAYOUT]) {
            "GRID" -> FeedLayout.GRID
            else -> FeedLayout.GRID
        }
    }

    override suspend fun setFeedLayout(layout: FeedLayout) {
        context.dataStore.edit { prefs ->
            prefs[FEED_LAYOUT] = layout.name
        }
    }

    override val readingPreferences: Flow<ReadingPreferences> = context.dataStore.data.map { prefs ->
        val fontName = prefs[READING_FONT] ?: "SANS"
        val fontSize = prefs[READING_FONT_SIZE] ?: 16
        val immersiveMode = prefs[READING_IMMERSIVE_MODE] ?: false
        val themeName = prefs[READING_THEME] ?: "DEFAULT"
        val motionBlur = prefs[READING_MOTION_BLUR] ?: false
        val showRelated = prefs[READING_SHOW_RELATED] ?: true
        val font = try { ReadingFont.valueOf(fontName) } catch (_: Exception) { ReadingFont.SANS }
        val theme = try { ReadingTheme.valueOf(themeName) } catch (_: Exception) { ReadingTheme.DEFAULT }
        ReadingPreferences(
            font = font,
            fontSize = fontSize,
            isImmersiveMode = immersiveMode,
            theme = theme,
            isMotionBlurEnabled = motionBlur,
            showRelatedArticles = showRelated
        )
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

    override suspend fun setReadingPreferences(prefs: ReadingPreferences) {
        context.dataStore.edit { stored ->
            stored[READING_FONT] = prefs.font.name
            stored[READING_FONT_SIZE] = prefs.fontSize
            stored[READING_IMMERSIVE_MODE] = prefs.isImmersiveMode
            stored[READING_THEME] = prefs.theme.name
            stored[READING_MOTION_BLUR] = prefs.isMotionBlurEnabled
            stored[READING_SHOW_RELATED] = prefs.showRelatedArticles
        }
    }

    override suspend fun getAuthToken(): String? {
        return context.dataStore.data.first()[AUTH_TOKEN]
    }

    override suspend fun setAuthToken(token: String) {
        context.dataStore.edit { it[AUTH_TOKEN] = token }
    }

    override suspend fun getUserId(): Int? {
        return context.dataStore.data.first()[USER_ID]
    }

    override suspend fun getUserEmail(): String? {
        return context.dataStore.data.first()[USER_EMAIL]
    }

    override suspend fun getUserName(): String? {
        return context.dataStore.data.first()[USER_NAME]
    }

    override suspend fun setAccountInfo(info: UserInfo) {
        context.dataStore.edit { stored ->
            stored[USER_ID] = info.id
            stored[USER_EMAIL] = info.email
            stored[USER_NAME] = info.username
        }
    }

    override suspend fun clearAuth() {
        context.dataStore.edit { stored ->
            stored.remove(AUTH_TOKEN)
            stored.remove(USER_ID)
            stored.remove(USER_EMAIL)
            stored.remove(USER_NAME)
        }
    }
}
