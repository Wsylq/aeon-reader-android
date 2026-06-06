package com.aeonreader.data.local

import androidx.paging.PagingSource
import androidx.paging.PagingState

class ArticlePagingSource(
    private val articleDao: ArticleDao,
    private val category: String?
) : PagingSource<Int, ArticleSummaryProjection>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ArticleSummaryProjection> {
        val pageOrder = params.key ?: 0
        return try {
            val items = if (category != null)
                articleDao.getSummariesByCategoryAfter(category, pageOrder, params.loadSize)
            else
                articleDao.getAllSummariesAfter(pageOrder, params.loadSize)

            val nextKey = if (items.size < params.loadSize) null else items.last().pageOrder + 1
            val prevKey = if (pageOrder > 0) (pageOrder - params.loadSize).coerceAtLeast(0) else null

            LoadResult.Page(
                data = items,
                prevKey = prevKey,
                nextKey = nextKey
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, ArticleSummaryProjection>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestItemToPosition(anchorPosition)?.pageOrder
        }
    }
}
