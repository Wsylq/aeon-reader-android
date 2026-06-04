package com.aeonreader.data.network

import com.aeonreader.domain.ArticleSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AeonScraperImpl @Inject constructor(
    private val client: OkHttpClient,
    private val parser: AeonParser
) : AeonScraper {

    private val httpClient = client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val articleClient = client.newBuilder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun buildRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .get()
            .build()
    }

    private suspend fun executeRequest(client: OkHttpClient, url: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val request = buildRequest(url)
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        Result.success(body)
                    } else {
                        Result.failure(Exception("Empty response body"))
                    }
                } else {
                    Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun fetchFeed(page: Int): Result<List<ArticleSummary>> {
        val url = if (page <= 1) {
            "https://aeon.co/essays/feed.rss"
        } else {
            "https://aeon.co/essays?page=$page"
        }
        return executeRequest(httpClient, url).mapCatching { body ->
            if (url.endsWith(".rss")) {
                parser.parseFeedPage(body).getOrThrow()
            } else {
                parser.parseFeedPage(body).getOrThrow()
            }
        }
    }

    override suspend fun fetchCategoryFeed(category: String, page: Int): Result<List<ArticleSummary>> {
        val url = if (page <= 1) {
            "https://aeon.co/$category/feed.rss"
        } else {
            "https://aeon.co/$category?page=$page"
        }
        return executeRequest(httpClient, url).mapCatching { body ->
            if (url.endsWith(".rss")) {
                parser.parseFeedPage(body).getOrThrow()
            } else {
                parser.parseFeedPage(body).getOrThrow()
            }
        }
    }

    override suspend fun fetchArticle(url: String): Result<String> {
        return executeRequest(articleClient, url)
    }

    override suspend fun fetchCategories(): Result<List<String>> {
        val url = "https://aeon.co/essays"
        return try {
            val html = executeRequest(httpClient, url).getOrThrow()
            val categories = parser.parseCategories(html)
            if (categories.isSuccess && categories.getOrThrow().isNotEmpty()) {
                categories
            } else {
                Result.success(listOf("Philosophy", "Science", "Psychology", "Society", "Culture"))
            }
        } catch (_: Exception) {
            Result.success(listOf("Philosophy", "Science", "Psychology", "Society", "Culture"))
        }
    }

    override suspend fun searchViaServer(query: String): Result<List<ArticleSummary>> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "${ScraperConfig.serverBaseUrl}/api/search-aeon?q=$encoded"
        return executeRequest(httpClient, url).mapCatching { json ->
            parser.parseServerSearchResults(json).getOrThrow()
        }
    }

    override suspend fun search(query: String, page: Int): Result<List<ArticleSummary>> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://www.mojeek.com/search?q=site:aeon.co+$encoded"
        return executeRequest(httpClient, url).mapCatching { html ->
            parser.parseMojeekResults(html).getOrThrow()
        }
    }
}
