package com.aeonreader.data.word

import com.aeonreader.data.local.ArticleDao
import com.aeonreader.data.local.WordDefinitionEntity
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
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
        val cached = articleDao.getWordDefinition(word)
        if (cached != null) return cached.definition

        return try {
            val request = Request.Builder()
                .url("https://api.dictionaryapi.dev/api/v2/entries/en/$word")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return "Definition not found"
            val body = response.body?.string() ?: return "Definition not found"
            val definition = parseDefinition(body)
            articleDao.upsertWordDefinition(
                WordDefinitionEntity(
                    word = word,
                    definition = definition,
                    cachedAt = System.currentTimeMillis()
                )
            )
            definition
        } catch (_: Exception) {
            "No internet connection.\nOpen this article while online to cache word definitions."
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
