package com.aeonreader.ui.screens.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.aeonreader.data.repository.KeepReadingItem
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
    val keepReadingItems by viewModel.keepReadingItems.collectAsState()

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
                bookmarkedUrls = viewModel.bookmarkedUrls,
                keepReadingItems = keepReadingItems,
                onArticleClick = onArticleClick,
                onSettingsClick = onSettingsClick,
                onCategorySelect = { viewModel.selectCategory(it) },
                onToggleLayout = { viewModel.toggleLayout() },
                onToggleBookmark = { viewModel.toggleBookmark(it) },
                modifier = modifier
            )
        }
    }
}

@Composable
private fun FeedContent(
    state: FeedUiState.Success,
    feedLayout: FeedLayout,
    bookmarkedUrls: StateFlow<Set<String>>,
    keepReadingItems: List<KeepReadingItem>,
    onArticleClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onCategorySelect: (String) -> Unit,
    onToggleLayout: () -> Unit,
    onToggleBookmark: (ArticleSummary) -> Unit,
    modifier: Modifier = Modifier
) {
    val bookmarksState = bookmarkedUrls.collectAsState()
    val articlesFlow = state.articles

    Column(modifier = modifier.fillMaxSize()) {
        key("header") {
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

        if (keepReadingItems.isNotEmpty()) {
            KeepReadingRow(
                items = keepReadingItems,
                onArticleClick = onArticleClick,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        ArticleList(
            articlesFlow = articlesFlow,
            bookmarksState = bookmarksState,
            feedLayout = feedLayout,
            onArticleClick = onArticleClick,
            onToggleBookmark = onToggleBookmark,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ArticleList(
    articlesFlow: Flow<PagingData<ArticleSummary>>,
    bookmarksState: State<Set<String>>,
    feedLayout: FeedLayout,
    onArticleClick: (String) -> Unit,
    onToggleBookmark: (ArticleSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagingItems = articlesFlow.collectAsLazyPagingItems()

    if (feedLayout == FeedLayout.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = modifier
        ) {
            items(pagingItems.itemCount, key = { index -> "article_$index" }, contentType = { _ -> "article" }) { index ->
                pagingItems[index]?.let { summary ->
                    val isBookmarked by remember(summary.url) { derivedStateOf { bookmarksState.value.contains(summary.url) } }
                    val onClick = remember(summary.url) { { onArticleClick(summary.url) } }
                    val onBookmark = remember(summary.url) { { onToggleBookmark(summary) } }
                    ArticleGridCard(
                        summary = summary,
                        isBookmarked = isBookmarked,
                        onClick = onClick,
                        onBookmark = onBookmark
                    )
                }
            }

            item(span = { GridItemSpan(2) }) {
                FeedFooter(loadState = pagingItems.loadState.append, onRetry = { pagingItems.retry() })
            }
        }
    } else {
        LazyColumn(
            modifier = modifier
        ) {
            items(pagingItems.itemCount, key = { index -> "article_$index" }, contentType = { _ -> "article" }) { index ->
                pagingItems[index]?.let { summary ->
                    val isBookmarked by remember(summary.url) { derivedStateOf { bookmarksState.value.contains(summary.url) } }
                    val onClick = remember(summary.url) { { onArticleClick(summary.url) } }
                    val onBookmark = remember(summary.url) { { onToggleBookmark(summary) } }
                    ArticleRow(
                        summary = summary,
                        isBookmarked = isBookmarked,
                        onClick = onClick,
                        onBookmark = onBookmark
                    )
                }
            }

            item {
                FeedFooter(loadState = pagingItems.loadState.append, onRetry = { pagingItems.retry() })
            }
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
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "all") {
            FilterChip(
                selected = selectedCategory == "all",
                onClick = { onCategorySelect("all") },
                label = { Text("All") }
            )
        }
        items(categories, key = { it }) { category ->
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
    isBookmarked: Boolean,
    onClick: () -> Unit,
    onBookmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
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

            IconButton(onClick = onBookmark) {
                Icon(
                    imageVector = if (isBookmarked) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = if (isBookmarked) "Remove bookmark" else "Bookmark",
                    tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ArticleGridCard(
    summary: ArticleSummary,
    isBookmarked: Boolean,
    onClick: () -> Unit,
    onBookmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val imageWidthPx = remember(density, configuration) {
        with(density) {
            minOf((configuration.screenWidthDp.dp / 2), 300.dp).toPx().toInt()
        }
    }

    Card(
        modifier = modifier
            .padding(4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            if (summary.heroImageUrl != null) {
                val imageRequest = remember(summary.heroImageUrl, imageWidthPx) {
                    ImageRequest.Builder(context)
                        .data(summary.heroImageUrl)
                        .size(imageWidthPx)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .crossfade(true)
                        .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = summary.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${maxOf(summary.estimatedReadingTimeMinutes, 5)} min",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onBookmark) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = if (isBookmarked) "Remove bookmark" else "Bookmark",
                        tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun KeepReadingRow(
    items: List<KeepReadingItem>,
    onArticleClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 12.dp)) {
        Text(
            text = "Keep Reading",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items, key = { it.url }) { item ->
                val ctx = androidx.compose.ui.platform.LocalContext.current
                Card(
                    modifier = Modifier
                        .width(200.dp)
                        .clickable { onArticleClick(item.url) },
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column {
                        if (item.heroImageUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(ctx)
                                    .data(item.heroImageUrl)
                                    .size(400)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = item.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { item.progressPercent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${item.progressPercent.toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
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
    ArticleRow(
        summary = summary,
        isBookmarked = false,
        onClick = onClick,
        onBookmark = {},
        modifier = modifier
    )
}

@Composable
private fun FeedFooter(loadState: LoadState, onRetry: () -> Unit) {
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
                    Button(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            }
        }
        else -> {}
    }
}


