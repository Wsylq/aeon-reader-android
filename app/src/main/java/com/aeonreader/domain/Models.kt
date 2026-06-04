package com.aeonreader.domain

import androidx.compose.runtime.Immutable
import java.time.Instant
import java.time.LocalDate

@Immutable
data class ArticleSummary(
    val url: String,
    val title: String,
    val description: String?,
    val author: String?,
    val category: String?,
    val heroImageUrl: String?,
    val estimatedReadingTimeMinutes: Int,
    val cachedAt: Long?
)

data class Article(
    val url: String,
    val title: String,
    val description: String?,
    val author: String?,
    val authorBio: String?,
    val publicationDate: LocalDate?,
    val category: String?,
    val heroImageUrl: String?,
    val bodyBlocks: List<ContentBlock>,
    val wordCount: Int,
    val relatedArticles: List<ArticleSummary> = emptyList()
)

sealed class ContentBlock {
    data class Paragraph(val text: String) : ContentBlock()
    data class Subheading(val text: String) : ContentBlock()
    data class BlockQuote(val text: String) : ContentBlock()
    data class PullQuote(val text: String) : ContentBlock()
    data class InlineImage(val url: String, val caption: String?) : ContentBlock()
}

data class Bookmark(
    val articleUrl: String,
    val title: String,
    val author: String?,
    val heroImageUrl: String?,
    val bookmarkedAt: Instant,
    val progressPercent: Float? = null
)

data class ReadingProgress(
    val articleUrl: String,
    val progressPercent: Float,
    val savedAt: Instant
)

enum class ThemeOverride { NONE, LIGHT, DARK }

enum class ReadingFont(val displayName: String) {
    SANS("Sans Serif"),
    SERIF("Serif"),
    MONO("Monospace")
}

enum class FeedLayout { LIST, GRID }

enum class ReadingTheme(val displayName: String) {
    DEFAULT("Default"),
    SEPIA("Sepia"),
    GREEN("Green"),
    AEON("Aeon")
}

data class ReadingPreferences(
    val font: ReadingFont = ReadingFont.SANS,
    val fontSize: Int = 16,
    val isImmersiveMode: Boolean = false,
    val theme: ReadingTheme = ReadingTheme.DEFAULT,
    val isMotionBlurEnabled: Boolean = true,
    val showRelatedArticles: Boolean = true
)
