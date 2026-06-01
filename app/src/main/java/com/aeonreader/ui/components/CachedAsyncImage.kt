package com.aeonreader.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.aeonreader.data.cache.ImageCache

@Composable
fun CachedAsyncImage(
    imageUrl: String,
    contentDescription: String?,
    imageCache: ImageCache?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val cachedFile = remember(imageUrl, imageCache) { imageCache?.getCachedFile(imageUrl) }
    AsyncImage(
        model = cachedFile ?: imageUrl,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}
