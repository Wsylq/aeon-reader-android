package com.aeonreader.data.repository

import com.aeonreader.data.cloudflare.CloudflareApiService
import com.aeonreader.data.cloudflare.UserInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: CloudflareApiService,
    private val userPreferences: UserPreferencesRepository
) : AuthRepository {

    override suspend fun register(email: String, username: String, password: String): Result<AccountInfo> {
        return api.register(email, username, password).map { (token, userInfo) ->
            userPreferences.setAuthToken(token)
            userPreferences.setAccountInfo(userInfo)
            userInfo.toAccountInfo()
        }
    }

    override suspend fun login(email: String, password: String): Result<AccountInfo> {
        return api.login(email, password).map { (token, userInfo) ->
            userPreferences.setAuthToken(token)
            userPreferences.setAccountInfo(userInfo)
            userInfo.toAccountInfo()
        }
    }

    override suspend fun logout() {
        userPreferences.clearAuth()
    }

    override suspend fun refreshSession(): Result<AccountInfo> {
        val token = userPreferences.getAuthToken() ?: return Result.failure(Exception("Not logged in"))
        return api.getMe(token).map { userInfo ->
            userPreferences.setAccountInfo(userInfo)
            userInfo.toAccountInfo()
        }
    }

    override suspend fun isLoggedIn(): Boolean {
        return userPreferences.getAuthToken() != null
    }

    override suspend fun getAccountInfo(): AccountInfo? {
        val email = userPreferences.getUserEmail() ?: return null
        val username = userPreferences.getUserName() ?: return null
        val id = userPreferences.getUserId() ?: return null
        return AccountInfo(id, email, username)
    }

    private fun UserInfo.toAccountInfo() = AccountInfo(id, email, username)
}
