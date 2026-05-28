package com.aeonreader.data.local

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AeonDatabase {
        return Room.databaseBuilder(
            context,
            AeonDatabase::class.java,
            "aeon_reader.db"
        ).fallbackToDestructiveMigration()
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
