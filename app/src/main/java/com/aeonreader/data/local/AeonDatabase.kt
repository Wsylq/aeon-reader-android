package com.aeonreader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ArticleSummaryEntity::class,
        ArticleEntity::class,
        BookmarkEntity::class,
        ReadingProgressEntity::class,
        RemoteKeyEntity::class,
        WordDefinitionEntity::class,
        HighlightedWordEntity::class,
        ReadingSessionEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AeonDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun remoteKeyDao(): RemoteKeyDao
    abstract fun statsDao(): StatsDao
}
