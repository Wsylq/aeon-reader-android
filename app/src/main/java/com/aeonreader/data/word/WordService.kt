package com.aeonreader.data.word

import com.aeonreader.data.local.ArticleDao
import com.aeonreader.data.local.WordDefinitionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WordService @Inject constructor(
    private val articleDao: ArticleDao
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun getDefinition(word: String): String {
        if (word.isBlank()) return "No word selected"

        val cached = articleDao.getWordDefinition(word)
        if (cached != null) return cached.definition

        return withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(word, "UTF-8")
                val request = Request.Builder()
                    .url("https://api.dictionaryapi.dev/api/v2/entries/en/$encoded")
                    .header("User-Agent", "Eon/0.10 (Android; ae-on-reader)")
                    .header("Accept", "application/json")
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    val code = response.code
                    return@withContext when (code) {
                        404 -> "No definition found for \"$word\"."
                        429 -> "Rate limited.\nTry again in a moment."
                        else -> "Server error ($code).\nTry again later."
                    }
                }
                val body = response.body?.string() ?: return@withContext "Definition not found"
                val definition = parseDefinition(body)
                articleDao.upsertWordDefinition(
                    WordDefinitionEntity(
                        word = word,
                        definition = definition,
                        cachedAt = System.currentTimeMillis()
                    )
                )
                definition
            } catch (e: Exception) {
                when {
                    e is java.net.UnknownHostException -> "No internet connection.\nConnect to WiFi and try again."
                    e is java.net.SocketTimeoutException -> "Request timed out.\nTry again later."
                    e.message?.contains("Unable to resolve host") == true -> "DNS lookup failed.\nCheck your internet connection."
                    else -> "Error: ${e.message ?: "Unknown error"}"
                }
            }
        }
    }

    private fun parseDefinition(json: String): String {
        return try {
            val arr = JSONArray(json)
            val first = arr.getJSONObject(0)
            val meanings = first.getJSONArray("meanings")
            val parts = mutableListOf<String>()
            for (i in 0 until minOf(meanings.length(), 2)) {
                val meaning = meanings.getJSONObject(i)
                val partOfSpeech = meaning.getString("partOfSpeech")
                val defs = meaning.getJSONArray("definitions")
                if (defs.length() > 0) {
                    val def = defs.getJSONObject(0).getString("definition")
                    parts.add("($partOfSpeech) $def")
                }
            }
            parts.joinToString("\n")
        } catch (_: Exception) {
            "Definition not found"
        }
    }
}
