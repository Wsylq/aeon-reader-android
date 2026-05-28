package com.aeonreader.data.network

import com.aeonreader.domain.ArticleSummary

interface AeonScraper {
    suspend fun fetchFeed(page: Int): Result<List<ArticleSummary>>
    suspend fun fetchCategoryFeed(category: String, page: Int): Result<List<ArticleSummary>>
    suspend fun fetchArticle(url: String): Result<String>
    suspend fun fetchCategories(): Result<List<String>>
    suspend fun search(query: String, page: Int): Result<List<ArticleSummary>>
    suspend fun searchViaServer(query: String): Result<List<ArticleSummary>>
}
