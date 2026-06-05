package com.aeonreader.data.network

import com.aeonreader.domain.Article
import com.aeonreader.domain.ArticleSummary

interface AeonParser {
    suspend fun parseFeedPage(html: String): Result<List<ArticleSummary>>
    fun parseArticle(html: String): Result<Article>
    suspend fun parseCategories(html: String): Result<List<String>>
    fun parseSearchResults(html: String): Result<List<ArticleSummary>>
    fun parseServerSearchResults(json: String): Result<List<ArticleSummary>>
    fun parseMojeekResults(html: String): Result<List<ArticleSummary>>
    fun serialize(article: Article): String
    fun deserialize(json: String): Result<Article>
}
