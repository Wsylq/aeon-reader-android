package com.aeonreader.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Upsert
    suspend fun insert(entity: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE articleUrl = :url")
    suspend fun delete(url: String)

    @Query("SELECT * FROM bookmarks ORDER BY bookmarkedAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE articleUrl = :url")
    fun observeByUrl(url: String): Flow<BookmarkEntity?>

    @Query("SELECT * FROM bookmarks")
    suspend fun getAll(): List<BookmarkEntity>
}
