package com.aeonreader.data.repository

import com.aeonreader.data.local.BookmarkDao
import com.aeonreader.data.local.BookmarkEntity
import com.aeonreader.domain.ArticleSummary
import com.aeonreader.domain.Bookmark
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkRepositoryImpl @Inject constructor(
    private val bookmarkDao: BookmarkDao
) : BookmarkRepository {

    override fun observeBookmarks(): Flow<List<Bookmark>> {
        return bookmarkDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeBookmarkState(articleUrl: String): Flow<Boolean> {
        return bookmarkDao.observeByUrl(articleUrl).map { it != null }
    }

    override suspend fun addBookmark(article: ArticleSummary): Result<Unit> {
        return try {
            val entity = BookmarkEntity(
                articleUrl = article.url,
                title = article.title,
                author = article.author,
                heroImageUrl = article.heroImageUrl,
                bookmarkedAt = System.currentTimeMillis()
            )
            bookmarkDao.insert(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeBookmark(articleUrl: String): Result<Unit> {
        return try {
            bookmarkDao.delete(articleUrl)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

private fun BookmarkEntity.toDomain(): Bookmark {
    return Bookmark(
        articleUrl = articleUrl,
        title = title,
        author = author,
        heroImageUrl = heroImageUrl,
        bookmarkedAt = Instant.ofEpochMilli(bookmarkedAt)
    )
}
