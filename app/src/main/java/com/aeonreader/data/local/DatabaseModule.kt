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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AeonDatabase {
        return Room.databaseBuilder(
            context,
            AeonDatabase::class.java,
            "aeon_reader.db"
        ).addMigrations(MIGRATION_3_4)
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
