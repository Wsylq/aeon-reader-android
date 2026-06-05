package com.aeonreader.data.cache

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cacheDir: File = File(context.cacheDir, "article_images").also { it.mkdirs() }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun getCachedFile(url: String): File? = withContext(Dispatchers.IO) {
        val file = File(cacheDir, hash(url))
        file.takeIf { it.exists() }
    }

    suspend fun cacheImage(url: String): File? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            val file = File(cacheDir, hash(url))
            response.body?.byteStream()?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file.takeIf { it.exists() }
        } catch (_: Exception) {
            null
        }
    }

    private fun hash(url: String): String {
        return MessageDigest.getInstance("MD5").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
