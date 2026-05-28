package com.aeonreader.ui.screens.article

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.aeonreader.domain.Article
import com.aeonreader.domain.ContentBlock
import com.aeonreader.domain.ReadingFont
import kotlinx.coroutines.launch
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
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
                modifier = modifier,
                viewModel = viewModel
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleReaderContent(
    article: Article,
    isBookmarked: Boolean,
    initialProgress: Float?,
    onToggleBookmark: () -> Unit,
    onProgressUpdate: (Float) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArticleViewModel
) {
    val listState = rememberLazyListState()
    var showSettings by remember { mutableStateOf(false) }

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

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().weight(1f)) {
            item {
                if (article.heroImageUrl != null) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        AsyncImage(
                            model = article.heroImageUrl,
                            contentDescription = article.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Column(
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 8.dp)
                ) {
                    Text(
                        text = article.title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            lineHeight = MaterialTheme.typography.headlineMedium.lineHeight
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        article.author?.let {
                            Text(text = "By $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        article.publicationDate?.let {
                            if (article.author != null) {
                                Text(text = " · ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(text = it.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(text = " · ${ceil((article.wordCount / 200.0)).toInt()} min read", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)) {
                    IconButton(onClick = onToggleBookmark, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { showSettings = true }, modifier = Modifier.size(36.dp)) {
                        Text(
                            text = "Aa",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(article.bodyBlocks) { block ->
                ContentBlockItem(block)
            }

            item {
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }

    if (showSettings) {
        ReadingSettingsSheet(
            viewModel = viewModel,
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
private fun ContentBlockItem(block: ContentBlock) {
    when (block) {
        is ContentBlock.Paragraph -> ReaderParagraph(block.text)
        is ContentBlock.Subheading -> ReaderSubheading(block.text)
        is ContentBlock.BlockQuote -> ReaderBlockQuote(block.text)
        is ContentBlock.InlineImage -> ReaderImage(block.url, block.caption)
    }
}

@Composable
private fun ReaderParagraph(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge.copy(
            lineHeight = 28.sp,
            letterSpacing = 0.15.sp
        ),
        color = Color(0xFF1C1C1E),
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
    )
}

@Composable
private fun ReaderSubheading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.SemiBold,
            lineHeight = 30.sp
        ),
        color = Color(0xFF1C1C1E),
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
    )
}

@Composable
private fun ReaderBlockQuote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontStyle = FontStyle.Italic,
            lineHeight = 28.sp,
            letterSpacing = 0.15.sp
        ),
        color = Color(0xFF3A3A3C),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .background(
                color = Color(0xFFF2F2F7),
                shape = RoundedCornerShape(6.dp)
            )
            .border(
                width = 1.dp,
                color = Color(0xFFD1D1D6),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(16.dp)
    )
}

@Composable
private fun ReaderImage(url: String, caption: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 12.dp)
    ) {
        AsyncImage(
            model = url,
            contentDescription = caption,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFFD1D1D6), RoundedCornerShape(8.dp)),
            contentScale = ContentScale.FillWidth
        )
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF636366),
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 6.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingSettingsSheet(
    viewModel: ArticleViewModel,
    onDismiss: () -> Unit
) {
    val prefs by viewModel.readingPrefs.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Reading Settings", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
                IconButton(onClick = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                }) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text("Font Size", style = MaterialTheme.typography.titleSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            Spacer(modifier = Modifier.height(8.dp))

            val sizes = listOf(14, 16, 18, 20, 22)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sizes.forEach { size ->
                    val selected = prefs.fontSize == size
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else Color(0xFFF2F2F7)
                            )
                            .clickable { viewModel.setReadingPrefs(prefs.copy(fontSize = size)) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = size.toString(),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) Color.White else Color(0xFF1C1C1E)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text("Font", style = MaterialTheme.typography.titleSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReadingFont.entries.forEach { font ->
                    val selected = prefs.font == font
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else Color(0xFFF2F2F7)
                            )
                            .clickable { viewModel.setReadingPrefs(prefs.copy(font = font)) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = font.displayName,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) Color.White else Color(0xFF1C1C1E)
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text("Preview", style = MaterialTheme.typography.titleSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8F8FA), RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "The quick brown fox jumps over the lazy dog. This is a preview of how your article text will look with the selected font and size settings.",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = prefs.fontSize.sp,
                        fontFamily = when (prefs.font) {
                            ReadingFont.SANS -> FontFamily.SansSerif
                            ReadingFont.SERIF -> FontFamily.Serif
                            ReadingFont.MONO -> FontFamily.Monospace
                        },
                        lineHeight = (prefs.fontSize * 1.7).sp
                    ),
                    color = Color(0xFF1C1C1E)
                )
            }
        }
    }
}

