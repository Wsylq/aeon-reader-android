package com.aeonreader.data.cloudflare

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class CloudflareApiService @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json".toMediaType()

    suspend fun register(email: String, username: String, password: String): Result<Pair<String, UserInfo>> {
        return post("${ApiConfig.baseUrl}/auth/register", JSONObject().apply {
            put("email", email)
            put("username", username)
            put("password", password)
        })
    }

    suspend fun login(email: String, password: String): Result<Pair<String, UserInfo>> {
        return post("${ApiConfig.baseUrl}/auth/login", JSONObject().apply {
            put("email", email)
            put("password", password)
        })
    }

    suspend fun getMe(token: String): Result<UserInfo> {
        return get("${ApiConfig.baseUrl}/auth/me", token) { json ->
            val user = json.getJSONObject("user")
            UserInfo(user.getInt("id"), user.getString("email"), user.getString("username"))
        }
    }

    suspend fun syncBookmarks(token: String, items: List<BookmarkPayload>): Result<SyncResult> {
        val body = JSONObject().apply {
            put("items", JSONArray().apply {
                items.forEach { item ->
                    put(JSONObject().apply {
                        put("article_url", item.articleUrl)
                        put("title", item.title)
                        put("author", item.author ?: JSONObject.NULL)
                        put("hero_image_url", item.heroImageUrl ?: JSONObject.NULL)
                        put("bookmarked_at", item.bookmarkedAt)
                    })
                }
            })
        }
        return postSync("${ApiConfig.baseUrl}/bookmarks/sync", body, token)
    }

    suspend fun syncProgress(token: String, items: List<ProgressPayload>): Result<SyncResult> {
        val body = JSONObject().apply {
            put("items", JSONArray().apply {
                items.forEach { item ->
                    put(JSONObject().apply {
                        put("article_url", item.articleUrl)
                        put("last_block_index", item.lastBlockIndex)
                        put("total_blocks", item.totalBlocks)
                        put("saved_at", item.savedAt)
                    })
                }
            })
        }
        return postSync("${ApiConfig.baseUrl}/progress/sync", body, token)
    }

    suspend fun syncHistory(token: String, items: List<HistoryPayload>): Result<SyncResult> {
        val body = JSONObject().apply {
            put("items", JSONArray().apply {
                items.forEach { item ->
                    put(JSONObject().apply {
                        put("article_url", item.articleUrl)
                        put("title", item.title)
                        put("author", item.author ?: JSONObject.NULL)
                        put("hero_image_url", item.heroImageUrl ?: JSONObject.NULL)
                        put("category", item.category ?: JSONObject.NULL)
                        put("read_at", item.readAt)
                    })
                }
            })
        }
        return postSync("${ApiConfig.baseUrl}/history/sync", body, token)
    }

    suspend fun getBookmarks(token: String): Result<List<JSONObject>> {
        return get("${ApiConfig.baseUrl}/bookmarks", token) { json ->
            val items = json.getJSONArray("items")
            (0 until items.length()).map { items.getJSONObject(it) }
        }
    }

    suspend fun getProgress(token: String): Result<List<JSONObject>> {
        return get("${ApiConfig.baseUrl}/progress", token) { json ->
            val items = json.getJSONArray("items")
            (0 until items.length()).map { items.getJSONObject(it) }
        }
    }

    suspend fun getHistory(token: String): Result<List<JSONObject>> {
        return get("${ApiConfig.baseUrl}/history", token) { json ->
            val items = json.getJSONArray("items")
            (0 until items.length()).map { items.getJSONObject(it) }
        }
    }

    suspend fun getStats(token: String): Result<UserStats> {
        return get("${ApiConfig.baseUrl}/user/stats", token) { json ->
            UserStats(
                totalBookmarks = json.getInt("total_bookmarks"),
                totalRead = json.getInt("total_read"),
                totalProgressSaved = json.getInt("total_progress_saved")
            )
        }
    }

    private suspend fun post(url: String, body: JSONObject, token: String? = null): Result<Pair<String, UserInfo>> {
        return try {
            val requestBuilder = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody(jsonMediaType))
            token?.let { requestBuilder.header("Authorization", "Bearer $it") }
            val response = withContext(Dispatchers.IO) { client.newCall(requestBuilder.build()).execute() }
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errorMsg = try { JSONObject(responseBody).optString("error", "Unknown error") } catch (_: Exception) { "Request failed" }
                return Result.failure(Exception(errorMsg))
            }
            val json = JSONObject(responseBody)
            val userObj = json.getJSONObject("user")
            val userInfo = UserInfo(userObj.getInt("id"), userObj.getString("email"), userObj.getString("username"))
            val tokenStr = json.getString("token")
            Result.success(tokenStr to userInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun postSync(url: String, body: JSONObject, token: String): Result<SyncResult> {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .post(body.toString().toRequestBody(jsonMediaType))
                .build()
            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errorMsg = try { JSONObject(responseBody).optString("error", "Unknown error") } catch (_: Exception) { "Request failed" }
                return Result.failure(Exception(errorMsg))
            }
            val json = JSONObject(responseBody)
            Result.success(SyncResult(json.getInt("synced"), json.getInt("skipped")))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun <T> get(url: String, token: String, parser: (JSONObject) -> T): Result<T> {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errorMsg = try { JSONObject(responseBody).optString("error", "Unknown error") } catch (_: Exception) { "Request failed" }
                return Result.failure(Exception(errorMsg))
            }
            val json = JSONObject(responseBody)
            Result.success(parser(json))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class UserInfo(val id: Int, val email: String, val username: String)
data class SyncResult(val synced: Int, val skipped: Int)
data class UserStats(val totalBookmarks: Int, val totalRead: Int, val totalProgressSaved: Int)

data class BookmarkPayload(
    val articleUrl: String,
    val title: String,
    val author: String?,
    val heroImageUrl: String?,
    val bookmarkedAt: String
)

data class ProgressPayload(
    val articleUrl: String,
    val lastBlockIndex: Int,
    val totalBlocks: Int,
    val savedAt: String
)

data class HistoryPayload(
    val articleUrl: String,
    val title: String,
    val author: String?,
    val heroImageUrl: String?,
    val category: String?,
    val readAt: String
)
