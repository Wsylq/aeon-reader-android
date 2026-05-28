package com.aeonreader.di

import com.aeonreader.data.network.AeonParser
import com.aeonreader.data.network.AeonParserImpl
import com.aeonreader.data.network.AeonScraper
import com.aeonreader.data.network.AeonScraperImpl
import com.aeonreader.data.repository.ArticleRepository
import com.aeonreader.data.repository.ArticleRepositoryImpl
import com.aeonreader.data.repository.BookmarkRepository
import com.aeonreader.data.repository.BookmarkRepositoryImpl
import com.aeonreader.data.repository.ReadingProgressRepository
import com.aeonreader.data.repository.ReadingProgressRepositoryImpl
import com.aeonreader.data.repository.UserPreferencesRepository
import com.aeonreader.data.repository.UserPreferencesRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindAeonScraper(impl: AeonScraperImpl): AeonScraper

    @Binds
    @Singleton
    abstract fun bindAeonParser(impl: AeonParserImpl): AeonParser

    @Binds
    @Singleton
    abstract fun bindArticleRepository(impl: ArticleRepositoryImpl): ArticleRepository

    @Binds
    @Singleton
    abstract fun bindBookmarkRepository(impl: BookmarkRepositoryImpl): BookmarkRepository

    @Binds
    @Singleton
    abstract fun bindReadingProgressRepository(impl: ReadingProgressRepositoryImpl): ReadingProgressRepository

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(impl: UserPreferencesRepositoryImpl): UserPreferencesRepository
}
