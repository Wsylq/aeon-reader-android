package com.aeonreader.ui.screens.article

import android.app.Activity
import android.content.Intent
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ColorScheme
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.aeonreader.domain.Article
import com.aeonreader.domain.ContentBlock
import com.aeonreader.domain.ReadingFont
import com.aeonreader.domain.ReadingPreferences
import com.aeonreader.domain.ReadingTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil

private data class ReaderColors(
    val background: Color,
    val text: Color,
    val textDim: Color,
    val primary: Color,
    val surfaceVariant: Color,
    val outlineVariant: Color
)

private fun readerColors(theme: ReadingTheme, scheme: ColorScheme): ReaderColors {
    return when (theme) {
        ReadingTheme.DEFAULT -> ReaderColors(
            background = scheme.background,
            text = scheme.onSurface,
            textDim = scheme.onSurfaceVariant,
            primary = scheme.primary,
            surfaceVariant = scheme.surfaceVariant,
            outlineVariant = scheme.outlineVariant
        )
        ReadingTheme.SEPIA -> ReaderColors(
            background = Color(0xFFF7F3EB),
            text = Color(0xFF3D3029),
            textDim = Color(0xFF7A6B5F),
            primary = Color(0xFF8B4513),
            surfaceVariant = Color(0xFFEDE4D5),
            outlineVariant = Color(0xFFD4C8B8)
        )
        ReadingTheme.GREEN -> ReaderColors(
            background = Color(0xFFF0F4EF),
            text = Color(0xFF2D3D2D),
            textDim = Color(0xFF5A6D5A),
            primary = Color(0xFF3D6B3D),
            surfaceVariant = Color(0xFFDEE5DD),
            outlineVariant = Color(0xFFC4D0C3)
        )
    }
}

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
    initialProgress: Int?,
    onToggleBookmark: () -> Unit,
    onProgressUpdate: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArticleViewModel
) {
    val listState = rememberLazyListState()
    var showSettings by remember { mutableStateOf(false) }
    val readingPrefs by viewModel.readingPrefs.collectAsState()

    val context = LocalContext.current
    val window = remember(context) { (context as? Activity)?.window }

    SideEffect {
        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (readingPrefs.isImmersiveMode) {
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
            insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val scheme = MaterialTheme.colorScheme
    val colors = remember(readingPrefs.theme) { readerColors(readingPrefs.theme, scheme) }

    val nonBodyCount = 2
    val bodyBlocks = article.bodyBlocks

    val currentBlockIndex by remember {
        derivedStateOf {
            val idx = listState.firstVisibleItemIndex - nonBodyCount
            idx.coerceIn(0, maxOf(0, bodyBlocks.size - 1))
        }
    }

    LaunchedEffect(currentBlockIndex) {
        onProgressUpdate(currentBlockIndex)
    }

    var highlightedBlockIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(initialProgress) {
        if (initialProgress != null && bodyBlocks.isNotEmpty()) {
            val targetIndex = initialProgress.coerceIn(0, bodyBlocks.size - 1)
            listState.scrollToItem(targetIndex + nonBodyCount)
            highlightedBlockIndex = targetIndex
        }
    }

    LaunchedEffect(highlightedBlockIndex) {
        if (highlightedBlockIndex != null) {
            delay(3000)
            highlightedBlockIndex = null
        }
    }



    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        if (!readingPrefs.isImmersiveMode) {
            LinearProgressIndicator(
                progress = { if (bodyBlocks.isEmpty()) 0f else (currentBlockIndex + 1).toFloat() / bodyBlocks.size },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = colors.primary,
                trackColor = colors.surfaceVariant,
            )
        }

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

                        Spacer(modifier = Modifier.height(8.dp))

                        if (article.description != null) {
                            Text(
                                text = article.description,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Normal,
                                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }

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
                    if (!readingPrefs.isImmersiveMode) {
                        val context = LocalContext.current
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                        IconButton(onClick = onToggleBookmark, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (isBookmarked) "Remove bookmark" else "Add bookmark",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, article.url)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share article"))
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = { showSettings = true }, modifier = Modifier.size(36.dp)) {
                            Text(
                                text = "Aa",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    }
                }

                itemsIndexed(bodyBlocks) { index, block ->
                    ContentBlockItem(
                        block = block,
                        prefs = readingPrefs,
                        colors = colors,
                        isHighlighted = highlightedBlockIndex == index
                    )
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
private fun ContentBlockItem(
    block: ContentBlock,
    prefs: ReadingPreferences,
    colors: ReaderColors,
    isHighlighted: Boolean = false
) {
    val highlightAlpha by animateFloatAsState(
        targetValue = if (isHighlighted) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "highlight"
    )

    val bgModifier = if (highlightAlpha > 0f) {
        Modifier.background(colors.primary.copy(alpha = 0.12f * highlightAlpha))
    } else Modifier

    when (block) {
        is ContentBlock.Paragraph -> ReaderParagraph(
            text = block.text,
            prefs = prefs,
            colors = colors,
            modifier = bgModifier
        )
        is ContentBlock.Subheading -> ReaderSubheading(
            text = block.text,
            prefs = prefs,
            colors = colors,
            modifier = bgModifier
        )
        is ContentBlock.BlockQuote -> ReaderBlockQuote(
            text = block.text,
            prefs = prefs,
            colors = colors,
            modifier = bgModifier
        )
        is ContentBlock.PullQuote -> ReaderPullQuote(
            text = block.text,
            prefs = prefs,
            colors = colors,
            modifier = bgModifier
        )
        is ContentBlock.InlineImage -> ReaderImage(block.url, block.caption)
    }
}

@Composable
private fun ReaderParagraph(text: String, prefs: ReadingPreferences, colors: ReaderColors, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = prefs.fontSize.sp,
            fontFamily = when (prefs.font) {
                ReadingFont.SANS -> FontFamily.SansSerif
                ReadingFont.SERIF -> FontFamily.Serif
                ReadingFont.MONO -> FontFamily.Monospace
            },
            lineHeight = (prefs.fontSize * 1.7).sp,
            letterSpacing = 0.15.sp
        ),
        color = colors.text,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 6.dp)
    )
}

@Composable
private fun ReaderSubheading(text: String, prefs: ReadingPreferences, colors: ReaderColors, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge.copy(
            fontSize = (prefs.fontSize + 4).sp,
            fontFamily = when (prefs.font) {
                ReadingFont.SANS -> FontFamily.SansSerif
                ReadingFont.SERIF -> FontFamily.Serif
                ReadingFont.MONO -> FontFamily.Monospace
            },
            fontWeight = FontWeight.SemiBold,
            lineHeight = (prefs.fontSize * 1.8).sp
        ),
        color = colors.text,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 20.dp)
    )
}

@Composable
private fun ReaderBlockQuote(text: String, prefs: ReadingPreferences, colors: ReaderColors, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = prefs.fontSize.sp,
            fontFamily = when (prefs.font) {
                ReadingFont.SANS -> FontFamily.SansSerif
                ReadingFont.SERIF -> FontFamily.Serif
                ReadingFont.MONO -> FontFamily.Monospace
            },
            fontStyle = FontStyle.Italic,
            lineHeight = (prefs.fontSize * 1.7).sp,
            letterSpacing = 0.15.sp
        ),
        color = colors.textDim,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .background(
                color = colors.surfaceVariant,
                shape = RoundedCornerShape(6.dp)
            )
            .border(
                width = 1.dp,
                color = colors.outlineVariant,
                shape = RoundedCornerShape(6.dp)
            )
            .padding(16.dp)
    )
}

@Composable
private fun ReaderPullQuote(text: String, prefs: ReadingPreferences, colors: ReaderColors, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = (prefs.fontSize + 2).sp,
            fontFamily = when (prefs.font) {
                ReadingFont.SANS -> FontFamily.SansSerif
                ReadingFont.SERIF -> FontFamily.Serif
                ReadingFont.MONO -> FontFamily.Monospace
            },
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic,
            lineHeight = (prefs.fontSize * 1.6).sp,
            letterSpacing = 0.5.sp
        ),
        color = colors.primary,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp)
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
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
            contentScale = ContentScale.FillWidth
        )
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { viewModel.setReadingPrefs(prefs.copy(fontSize = size)) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = size.toString(),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface
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
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { viewModel.setReadingPrefs(prefs.copy(font = font)) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = font.displayName,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { viewModel.setReadingPrefs(prefs.copy(isImmersiveMode = !prefs.isImmersiveMode)) }
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Immersive Mode",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Hide status bar and controls for distraction-free reading",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (prefs.isImmersiveMode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (prefs.isImmersiveMode) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Theme", style = MaterialTheme.typography.titleSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReadingTheme.entries.forEach { theme ->
                    val selected = prefs.theme == theme
                    val colors = readerColors(theme, MaterialTheme.colorScheme)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.setReadingPrefs(prefs.copy(theme = theme)) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else colors.background,
                                        CircleShape
                                    )
                                    .then(
                                        if (!selected) Modifier.border(
                                            1.5.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            CircleShape
                                        ) else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = theme.displayName,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text("Preview", style = MaterialTheme.typography.titleSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
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
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

