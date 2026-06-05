package com.aeonreader.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.aeonreader.data.cache.ImageCache
import java.io.File

@Composable
fun CachedAsyncImage(
    imageUrl: String,
    contentDescription: String?,
    imageCache: ImageCache?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    var cachedFile by remember(imageUrl, imageCache) { mutableStateOf<File?>(null) }
    LaunchedEffect(imageUrl, imageCache) {
        cachedFile = imageCache?.getCachedFile(imageUrl)
    }
    AsyncImage(
        model = cachedFile ?: imageUrl,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}
