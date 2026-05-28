package com.aeonreader.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "article_summaries")
data class ArticleSummaryEntity(
    @PrimaryKey val url: String,
    val title: String,
    val author: String?,
    val category: String?,
    val heroImageUrl: String?,
    val estimatedReadingTimeMinutes: Int,
    val cachedAt: Long,
    val lastAccessedAt: Long,
    val page: Int,
    val pageOrder: Int
)

@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey val url: String,
    val title: String,
    val author: String?,
    val authorBio: String?,
    val publicationDate: String?,
    val category: String?,
    val heroImageUrl: String?,
    val bodyJson: String,
    val wordCount: Int,
    val cachedAt: Long,
    val lastAccessedAt: Long,
    val sizeBytes: Long
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val articleUrl: String,
    val title: String,
    val author: String?,
    val heroImageUrl: String?,
    val bookmarkedAt: Long
)

@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    @PrimaryKey val articleUrl: String,
    val progressPercent: Float,
    val savedAt: Long
)

@Entity(tableName = "remote_keys")
data class RemoteKeyEntity(
    @PrimaryKey val category: String,
    val nextPage: Int?,
    val lastUpdated: Long
)
