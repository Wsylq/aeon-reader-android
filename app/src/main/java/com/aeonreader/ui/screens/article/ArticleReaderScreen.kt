package com.aeonreader.ui.screens.article

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.aeonreader.domain.Article
import com.aeonreader.domain.ContentBlock
import kotlin.math.ceil

@Composable
fun ArticleReaderScreen(
    articleUrl: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArticleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(articleUrl) {
        viewModel.loadArticle(articleUrl)
    }

    when (val state = uiState) {
        is ArticleUiState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is ArticleUiState.Error -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.loadArticle(articleUrl) }) {
                        Text("Retry")
                    }
                }
            }
        }
        is ArticleUiState.Success -> {
            ArticleReaderContent(
                article = state.article,
                isBookmarked = state.isBookmarked,
                initialProgress = state.readingProgress,
                onToggleBookmark = { viewModel.toggleBookmark() },
                onProgressUpdate = { viewModel.updateProgress(it) },
                modifier = modifier
            )
        }
    }
}

@Composable
private fun ArticleReaderContent(
    article: Article,
    isBookmarked: Boolean,
    initialProgress: Float?,
    onToggleBookmark: () -> Unit,
    onProgressUpdate: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    val progress by remember {
        derivedStateOf {
            if (listState.layoutInfo.totalItemsCount == 0) 0f
            else {
                val visibleItems = listState.layoutInfo.visibleItemsInfo
                if (visibleItems.isEmpty()) 0f
                else {
                    val firstVisible = visibleItems.first().index
                    val total = listState.layoutInfo.totalItemsCount
                    (firstVisible.toFloat() / total.toFloat()) * 100f
                }
            }
        }
    }

    LaunchedEffect(progress) {
        onProgressUpdate(progress)
    }

    LaunchedEffect(initialProgress) {
        if (initialProgress != null) {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems > 0) {
                val targetIndex = ((initialProgress / 100f) * totalItems).toInt()
                    .coerceIn(0, totalItems - 1)
                listState.scrollToItem(targetIndex)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        LinearProgressIndicator(
            progress = { progress / 100f },
            modifier = Modifier.fillMaxWidth().height(3.dp),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().weight(1f)
        ) {
            item {
                if (article.heroImageUrl != null) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        AsyncImage(
                            model = article.heroImageUrl,
                            contentDescription = article.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp)
                                .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Column(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)
                ) {
                    Text(
                        text = article.title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            lineHeight = MaterialTheme.typography.headlineMedium.lineHeight
                        )
                    )

                    if (article.author != null || article.publicationDate != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            article.author?.let {
                                Text(
                                    text = "By $it",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            article.publicationDate?.let {
                                if (article.author != null) {
                                    Text(
                                        text = " · ",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = it.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${ceil((article.wordCount / 200.0)).toInt()} min read",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 16.dp)
                ) {
                    IconButton(
                        onClick = onToggleBookmark,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            items(article.bodyBlocks) { block ->
                when (block) {
                    is ContentBlock.Paragraph -> {
                        Text(
                            text = block.text,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                            ),
                            modifier = Modifier.padding(
                                horizontal = 20.dp, vertical = 6.dp
                            )
                        )
                    }
                    is ContentBlock.Subheading -> {
                        Text(
                            text = block.text,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.padding(
                                horizontal = 20.dp, vertical = 16.dp
                            )
                        )
                    }
                    is ContentBlock.BlockQuote -> {
                        Text(
                            text = block.text,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontStyle = FontStyle.Italic,
                                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(16.dp)
                        )
                    }
                    is ContentBlock.InlineImage -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, bottom = 12.dp)
                        ) {
                            AsyncImage(
                                model = block.url,
                                contentDescription = block.caption,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(0.dp)),
                                contentScale = ContentScale.FillWidth
                            )
                            if (block.caption != null) {
                                Text(
                                    text = block.caption,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(
                                        start = 20.dp, end = 20.dp, top = 4.dp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
