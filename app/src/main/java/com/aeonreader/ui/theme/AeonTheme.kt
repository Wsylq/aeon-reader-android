package com.aeonreader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.aeonreader.domain.ThemeOverride
import com.aeonreader.data.repository.UserPreferencesRepository

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1A1A2E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8E8F0),
    onPrimaryContainer = Color(0xFF1A1A2E),
    secondary = Color(0xFF4A4A6A),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFF3A3A3A),
    error = Color(0xFFB00020),
    onError = Color(0xFFFFFFFF),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB0B0D0),
    onPrimary = Color(0xFF1A1A2E),
    primaryContainer = Color(0xFF2A2A4A),
    onPrimaryContainer = Color(0xFFE0E0F0),
    secondary = Color(0xFF9090B0),
    onSecondary = Color(0xFF1A1A2E),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE8E8E8),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE8E8E8),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFCCCCCC),
    error = Color(0xFFCF6679),
    onError = Color(0xFF1A1A1A),
)

@Composable
fun AeonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    userPreferencesRepository: UserPreferencesRepository? = null,
    content: @Composable () -> Unit
) {
    val themeOverride = userPreferencesRepository?.themeOverride?.collectAsState(initial = ThemeOverride.NONE)
    val activeOverride = themeOverride?.value ?: ThemeOverride.NONE

    val useDarkTheme = when (activeOverride) {
        ThemeOverride.LIGHT -> false
        ThemeOverride.DARK -> true
        ThemeOverride.NONE -> darkTheme
    }

    val colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AeonTypography,
        content = content
    )
}
