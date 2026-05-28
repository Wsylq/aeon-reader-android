package com.aeonreader.domain

import java.time.Instant
import java.time.LocalDate

data class ArticleSummary(
    val url: String,
    val title: String,
    val author: String?,
    val category: String?,
    val heroImageUrl: String?,
    val estimatedReadingTimeMinutes: Int,
    val cachedAt: Instant?
)

data class Article(
    val url: String,
    val title: String,
    val author: String?,
    val authorBio: String?,
    val publicationDate: LocalDate?,
    val category: String?,
    val heroImageUrl: String?,
    val bodyBlocks: List<ContentBlock>,
    val wordCount: Int
)

sealed class ContentBlock {
    data class Paragraph(val text: String) : ContentBlock()
    data class Subheading(val text: String) : ContentBlock()
    data class BlockQuote(val text: String) : ContentBlock()
    data class InlineImage(val url: String, val caption: String?) : ContentBlock()
}

data class Bookmark(
    val articleUrl: String,
    val title: String,
    val author: String?,
    val heroImageUrl: String?,
    val bookmarkedAt: Instant
)

data class ReadingProgress(
    val articleUrl: String,
    val progressPercent: Float,
    val savedAt: Instant
)

enum class ThemeOverride { NONE, LIGHT, DARK }
