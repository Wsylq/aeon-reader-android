package com.aeonreader.data.local

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

data class UrlTimestamp(
    val url: String,
    val cachedAt: Long
)

@Immutable
@Entity(
    tableName = "article_summaries",
    indices = [
        Index(value = ["category", "pageOrder"]),
        Index(value = ["pageOrder"])
    ]
)
data class ArticleSummaryEntity(
    @PrimaryKey val url: String,
    val title: String,
    val description: String?,
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

@Entity(tableName = "word_definitions")
data class WordDefinitionEntity(
    @PrimaryKey val word: String,
    val definition: String,
    val cachedAt: Long
)

@Entity(tableName = "highlighted_words")
data class HighlightedWordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val articleUrl: String,
    val word: String
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
    val lastBlockIndex: Int,
    val totalBlocks: Int = 0,
    val savedAt: Long
)

@Entity(tableName = "remote_keys")
data class RemoteKeyEntity(
    @PrimaryKey val category: String,
    val nextPage: Int?,
    val lastUpdated: Long
)
