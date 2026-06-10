package com.aeonreader.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `word_definitions` (
                    `word` TEXT NOT NULL,
                    `definition` TEXT NOT NULL,
                    `cachedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`word`)
                )"""
            )
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `highlighted_words` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT,
                    `articleUrl` TEXT NOT NULL,
                    `word` TEXT NOT NULL
                )"""
            )
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_article_summaries_pageOrder ON article_summaries(pageOrder ASC)")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE reading_progress ADD COLUMN totalBlocks INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_article_summaries_category ON article_summaries(category)")
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE article_summaries ADD COLUMN readCount INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_article_summaries_readCount ON article_summaries(readCount ASC)")
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE article_summaries ADD COLUMN relevanceScore REAL NOT NULL DEFAULT 1.0")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AeonDatabase {
        return Room.databaseBuilder(
            context,
            AeonDatabase::class.java,
            "aeon_reader.db"
        ).addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideArticleDao(db: AeonDatabase): ArticleDao = db.articleDao()

    @Provides
    fun provideBookmarkDao(db: AeonDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun provideReadingProgressDao(db: AeonDatabase): ReadingProgressDao = db.readingProgressDao()

    @Provides
    fun provideRemoteKeyDao(db: AeonDatabase): RemoteKeyDao = db.remoteKeyDao()
}
