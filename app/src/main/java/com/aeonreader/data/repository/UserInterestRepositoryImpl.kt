package com.aeonreader.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.interestStore by preferencesDataStore(name = "user_interests")

@Singleton
class UserInterestRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : UserInterestRepository {

    companion object {
        private val CATEGORY_WEIGHTS = stringPreferencesKey("category_weights")
        private val KEYWORD_WEIGHTS = stringPreferencesKey("keyword_weights")
        private val STOP_WORDS = setOf(
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "as", "is", "was", "are", "were", "be",
            "been", "being", "have", "has", "had", "do", "does", "did", "will",
            "would", "could", "should", "may", "might", "shall", "can", "not",
            "no", "nor", "so", "if", "than", "that", "this", "these", "those",
            "it", "its", "how", "what", "when", "where", "why", "which", "who",
            "whom", "about", "into", "through", "during", "before", "after",
            "above", "below", "between", "out", "off", "over", "under", "again",
            "further", "then", "once", "here", "there", "all", "each", "every",
            "both", "few", "more", "most", "other", "some", "such", "only",
            "own", "same", "too", "very", "just", "because"
        )
    }

    override suspend fun updateOnRead(category: String?, title: String) {
        context.interestStore.edit { prefs ->
            val catWeights = JSONObject(prefs[CATEGORY_WEIGHTS] ?: "{}")
            category?.let { cat ->
                val key = cat.lowercase()
                catWeights.put(key, catWeights.optInt(key, 0) + 1)
            }
            prefs[CATEGORY_WEIGHTS] = catWeights.toString()

            val kwWeights = JSONObject(prefs[KEYWORD_WEIGHTS] ?: "{}")
            val words = title.lowercase()
                .replace(Regex("[^a-z0-9\\s]"), "")
                .split("\\s+".toRegex())
                .filter { it.length > 3 && it !in STOP_WORDS }
            for (word in words.take(10)) {
                kwWeights.put(word, kwWeights.optInt(word, 0) + 1)
            }
            prefs[KEYWORD_WEIGHTS] = kwWeights.toString()
        }
    }

    override suspend fun getScore(category: String?, title: String): Float {
        val prefs = context.interestStore.data.first()
        var score = 1.0f

        val catWeights = JSONObject(prefs[CATEGORY_WEIGHTS] ?: "{}")
        category?.let { cat ->
            val weight = catWeights.optInt(cat.lowercase(), 0)
            if (weight > 0) score += weight.toFloat() * 0.3f
        }

        val kwWeights = JSONObject(prefs[KEYWORD_WEIGHTS] ?: "{}")
        val words = title.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .split("\\s+".toRegex())
            .filter { it.length > 3 && it !in STOP_WORDS }
        for (word in words) {
            val weight = kwWeights.optInt(word, 0)
            if (weight > 0) score += weight.toFloat() * 0.15f
        }

        return score
    }

    override suspend fun rescoreAll(existingScores: Map<String, Float>): Map<String, Float> {
        return existingScores.mapValues { (_, oldScore) ->
            oldScore * 0.95f
        }
    }
}
