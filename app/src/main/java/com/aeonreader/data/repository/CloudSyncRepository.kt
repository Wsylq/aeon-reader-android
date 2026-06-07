package com.aeonreader.data.repository

interface CloudSyncRepository {
    suspend fun syncBookmarks(): Result<Int>
    suspend fun syncProgress(): Result<Int>
    suspend fun syncHistory(): Result<Int>
    suspend fun syncAll(): Result<SyncAllResult>
}

data class SyncAllResult(
    val bookmarksSynced: Int,
    val progressSynced: Int,
    val historySynced: Int
)
