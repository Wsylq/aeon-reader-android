package com.aeonreader.ui.screens.feed

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.aeonreader.domain.ArticleSummary
import com.aeonreader.domain.FeedLayout
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

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
                onToggleBookmark = { viewModel.toggleBookmark(it) },
                viewModel = viewModel,
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
    onToggleBookmark: (ArticleSummary) -> Unit,
    viewModel: FeedViewModel,
    modifier: Modifier = Modifier
) {
    val articlesFlow = remember(state.articles) { state.articles }
    val pagingItems = articlesFlow.collectAsLazyPagingItems()

    val footer: @Composable () -> Unit = remember(pagingItems) { {
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
    } }

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

        if (feedLayout == FeedLayout.GRID) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f)
            ) {
                items(pagingItems.itemCount, key = { index -> "article_$index" }, contentType = { _ -> "article" }) { index ->
                    pagingItems[index]?.let { summary ->
                        val swipeLeft = index % 2 == 0
                        SwipeableFeedItem(
                            summary = summary,
                            viewModel = viewModel,
                            swipeDirection = if (swipeLeft) 0 else 1,
                            onClick = { onArticleClick(summary.url) },
                            onBookmark = { onToggleBookmark(summary) }
                        ) {
                            ArticleGridCard(summary = summary)
                        }
                    }
                }

                item(span = { GridItemSpan(2) }) { footer() }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(pagingItems.itemCount, key = { index -> "article_$index" }, contentType = { _ -> "article" }) { index ->
                    pagingItems[index]?.let { summary ->
                        SwipeableFeedItem(
                            summary = summary,
                            viewModel = viewModel,
                            swipeDirection = 1,
                            onClick = { onArticleClick(summary.url) },
                            onBookmark = { onToggleBookmark(summary) }
                        ) {
                            ArticleRow(summary = summary)
                        }
                    }
                }

                item { footer() }
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(2.dp, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val imageWidthPx = remember(density, configuration) {
        with(density) { ((configuration.screenWidthDp.dp / 2).toPx()).toInt() }
    }

    Column(
        modifier = modifier
            .padding(4.dp)
            .shadow(2.dp, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
    ) {
        if (summary.heroImageUrl != null) {
            val imageRequest = remember(summary.heroImageUrl, imageWidthPx) {
                ImageRequest.Builder(context)
                    .data(summary.heroImageUrl)
                    .size(imageWidthPx)
                    .memoryCachePolicy(CachePolicy.DISABLED)
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
    ArticleRow(
        summary = summary,
        modifier = modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun SwipeableFeedItem(
    summary: ArticleSummary,
    viewModel: FeedViewModel,
    swipeDirection: Int,
    onClick: () -> Unit,
    onBookmark: () -> Unit,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val pulseScale = remember { Animatable(1f) }
    val density = LocalDensity.current
    val thresholdPx = with(density) { 150.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    val bookmarks by viewModel.bookmarkedUrls.collectAsState()
    val isBookmarked by remember(summary.url) {
        derivedStateOf { bookmarks.contains(summary.url) }
    }
    val showStar by remember {
        derivedStateOf { abs(offsetX.value) > thresholdPx * 0.3f }
    }

    val dragState = rememberDraggableState { delta ->
        scope.launch {
            val next = offsetX.value + delta
            val clamped = if (swipeDirection == 0) next.coerceIn(-thresholdPx * 1.3f, 0f)
            else next.coerceIn(0f, thresholdPx * 1.3f)
            offsetX.snapTo(clamped)
        }
    }

    Box(modifier = Modifier.clipToBounds()) {
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .graphicsLayer { scaleX = pulseScale.value; scaleY = pulseScale.value }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = dragState,
                    onDragStopped = {
                        scope.launch {
                            if (abs(offsetX.value) >= thresholdPx && !isBookmarked) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onBookmark()
                                pulseScale.snapTo(0.95f)
                                pulseScale.animateTo(1.05f, spring(dampingRatio = 0.3f, stiffness = 900f))
                                pulseScale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 400f))
                            }
                            offsetX.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 400f))
                        }
                    }
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
        ) {
            content()
            if (showStar) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "Bookmark",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(if (swipeDirection == 0) Alignment.CenterEnd else Alignment.CenterStart)
                        .padding(horizontal = 24.dp)
                        .size(32.dp)
                )
            }
        }
    }
}


