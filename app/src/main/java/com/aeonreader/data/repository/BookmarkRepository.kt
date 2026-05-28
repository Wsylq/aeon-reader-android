package com.aeonreader.data.repository

import com.aeonreader.domain.ArticleSummary
import com.aeonreader.domain.Bookmark
import kotlinx.coroutines.flow.Flow

interface BookmarkRepository {
    fun observeBookmarks(): Flow<List<Bookmark>>
    fun observeBookmarkState(articleUrl: String): Flow<Boolean>
    suspend fun addBookmark(article: ArticleSummary): Result<Unit>
    suspend fun removeBookmark(articleUrl: String): Result<Unit>
}
