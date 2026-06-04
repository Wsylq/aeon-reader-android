package com.aeonreader.ui.screens.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import com.aeonreader.data.local.ArticleSummaryEntity
import com.aeonreader.domain.ArticleSummary
import com.aeonreader.domain.FeedLayout

@Composable
fun FeedScreen(
    onArticleClick: (String) -> Unit,
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val feedLayout by viewModel.feedLayout.collectAsState()

    when (val state = uiState) {
        is FeedUiState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is FeedUiState.Error -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.refresh() }) {
                        Text("Retry")
                    }
                }
            }
        }
        is FeedUiState.Success -> {
            FeedContent(
                state = state,
                feedLayout = feedLayout,
                onArticleClick = onArticleClick,
                onSettingsClick = onSettingsClick,
                onCategorySelect = { viewModel.selectCategory(it) },
                onToggleLayout = { viewModel.toggleLayout() },
                modifier = modifier
            )
        }
    }
}

@Composable
private fun FeedContent(
    state: FeedUiState.Success,
    feedLayout: FeedLayout,
    onArticleClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onCategorySelect: (String) -> Unit,
    onToggleLayout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagingItems = state.articles.collectAsLazyPagingItems()

    val itemKey = remember { { index: Int -> pagingItems[index]?.url ?: index } }

    val header: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryChipRow(
                categories = state.categories,
                selectedCategory = state.selectedCategory,
                onCategorySelect = onCategorySelect,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            TextButton(onClick = onToggleLayout) {
                Text(
                    text = if (feedLayout == FeedLayout.LIST) "Grid" else "List",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
    }

    val footer: @Composable () -> Unit = {
        pagingItems.loadState.append.let { loadState ->
            when (loadState) {
                is LoadState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
                is LoadState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Failed to load more articles",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Button(onClick = { pagingItems.retry() }) {
                                Text("Retry")
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }

    if (feedLayout == FeedLayout.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(2) }) { header() }

            items(pagingItems.itemCount, key = itemKey) { index ->
                pagingItems[index]?.let { entity ->
                    val currentOnClick = rememberUpdatedState(onArticleClick)
                    val onClick = remember(entity.url) { { currentOnClick.value(entity.url) } }
                    val summary = remember(entity) { entity.toArticleSummary() }
                    ArticleGridCard(summary = summary, onClick = onClick)
                }
            }

            item(span = { GridItemSpan(2) }) { footer() }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize()
        ) {
            item { header() }

            items(pagingItems.itemCount, key = itemKey, contentType = { _ -> "article" }) { index ->
                pagingItems[index]?.let { entity ->
                    val currentOnClick = rememberUpdatedState(onArticleClick)
                    val onClick = remember(entity.url) { { currentOnClick.value(entity.url) } }
                    val summary = remember(entity) { entity.toArticleSummary() }
                    ArticleRow(summary = summary, onClick = onClick)
                }
            }

            item { footer() }
        }
    }
}

@Composable
fun CategoryChipRow(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedCategory == "all",
            onClick = { onCategorySelect("all") },
            label = { Text("All") }
        )
        categories.forEach { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onCategorySelect(category) },
                label = { Text(category) }
            )
        }
    }
}

@Composable
private fun ArticleRow(
    summary: ArticleSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(2.dp, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = summary.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (summary.category != null) {
                    Text(
                        text = summary.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "${maxOf(summary.estimatedReadingTimeMinutes, 5)} min",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ArticleGridCard(
    summary: ArticleSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(4.dp)
            .shadow(2.dp, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        if (summary.heroImageUrl != null) {
            AsyncImage(
                model = summary.heroImageUrl,
                contentDescription = summary.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                contentScale = ContentScale.Crop
            )
        }

        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = summary.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${maxOf(summary.estimatedReadingTimeMinutes, 5)} min",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ArticleCard(
    summary: ArticleSummary,
    onClick: () -> Unit,
    isOfflineAvailable: Boolean = false,
    modifier: Modifier = Modifier
) {
    ArticleRow(summary = summary, onClick = onClick, modifier = modifier)
}

private fun ArticleSummaryEntity.toArticleSummary(): ArticleSummary {
    return ArticleSummary(
        url = url,
        title = title,
        description = description,
        author = author,
        category = category,
        heroImageUrl = heroImageUrl,
        estimatedReadingTimeMinutes = estimatedReadingTimeMinutes,
        cachedAt = cachedAt
    )
}
