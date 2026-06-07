package com.aeonreader.data.repository

data class AccountInfo(
    val id: Int,
    val email: String,
    val username: String
)

interface AuthRepository {
    suspend fun register(email: String, username: String, password: String): Result<AccountInfo>
    suspend fun login(email: String, password: String): Result<AccountInfo>
    suspend fun logout()
    suspend fun refreshSession(): Result<AccountInfo>
    suspend fun isLoggedIn(): Boolean
    suspend fun getAccountInfo(): AccountInfo?
}
