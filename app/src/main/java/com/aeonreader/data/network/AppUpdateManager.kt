package com.aeonreader.data.network

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String
)

@Singleton
class AppUpdateManager @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val GITHUB_API = "https://api.github.com/repos/Wsylq/aeon-reader-android/releases/latest"
    }

    fun checkForUpdate(currentVersion: String): Result<UpdateInfo?> {
        return try {
            val request = Request.Builder()
                .url(GITHUB_API)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.success(null)
            }

            val body = response.body?.string() ?: return Result.success(null)
            val json = JSONObject(body)
            val tagName = json.optString("tag_name", "") ?: ""
            val latestVersion = tagName.removePrefix("v")

            if (latestVersion.isBlank()) return Result.success(null)

            val currentNorm = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }
            val latestNorm = latestVersion.split(".").map { it.toIntOrNull() ?: 0 }

            for (i in 0 until maxOf(currentNorm.size, latestNorm.size)) {
                val c = currentNorm.getOrElse(i) { 0 }
                val l = latestNorm.getOrElse(i) { 0 }
                if (l > c) {
                    val assets = json.optJSONArray("assets") ?: return Result.success(null)
                    var downloadUrl: String? = null
                    for (j in 0 until assets.length()) {
                        val asset = assets.getJSONObject(j)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk")) {
                            downloadUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                    if (downloadUrl.isNullOrBlank()) return Result.success(null)
                    val releaseNotes = json.optString("body", "") ?: ""
                    return Result.success(
                        UpdateInfo(
                            latestVersion = tagName,
                            downloadUrl = downloadUrl,
                            releaseNotes = releaseNotes.take(500)
                        )
                    )
                } else if (c > l) {
                    return Result.success(null)
                }
            }
            Result.success(null)
        } catch (e: Exception) {
            Result.success(null)
        }
    }

    fun downloadAndInstall(context: Context, url: String) {
        try {
            val fileName = "aeon-reader-update.apk"
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            val uri = Uri.parse(url)
            val request = DownloadManager.Request(uri)
                .setTitle("Aeon Reader Update")
                .setDescription("Downloading latest version...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            downloadManager.enqueue(request)

        } catch (e: Exception) {
            // Fallback: open download URL in browser
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
