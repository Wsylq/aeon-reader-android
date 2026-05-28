package com.aeonreader.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface RemoteKeyDao {

    @Upsert
    suspend fun upsert(entity: RemoteKeyEntity)

    @Query("SELECT * FROM remote_keys WHERE category = :category")
    suspend fun get(category: String): RemoteKeyEntity?

    @Query("DELETE FROM remote_keys WHERE category = :category")
    suspend fun deleteByCategory(category: String)
}
