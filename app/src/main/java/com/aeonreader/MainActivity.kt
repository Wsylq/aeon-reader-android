package com.aeonreader

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.aeonreader.data.network.AppUpdateManager
import com.aeonreader.data.network.UpdateInfo
import com.aeonreader.ui.navigation.AeonNavHost
import com.aeonreader.ui.theme.AeonTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var updateManager: AppUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AeonTheme {
                androidx.compose.foundation.layout.                Box(modifier = Modifier.fillMaxSize()) {
                    AeonNavHost()
                    UpdateDialogHost(context = this@MainActivity, updateManager = updateManager)
                }
            }
        }
    }
}

@Composable
private fun UpdateDialogHost(
    context: Context,
    updateManager: AppUpdateManager
) {
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    val prefs = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)

    LaunchedEffect(Unit) {
        val currentVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
        } catch (_: PackageManager.NameNotFoundException) { "0" }

        val lastPromptedVersion = prefs.getString("last_prompted_version", "") ?: ""

        if (lastPromptedVersion == currentVersion) return@LaunchedEffect

        check@ withContext(Dispatchers.IO) {
            val result = updateManager.checkForUpdate(currentVersion)
            val info = result.getOrNull() ?: return@withContext
            updateInfo = info
            showDialog = true
        }
    }

    if (showDialog && updateInfo != null) {
        val info = updateInfo!!
        AlertDialog(
            onDismissRequest = {
                prefs.edit().putString("last_prompted_version", info.latestVersion).apply()
                showDialog = false
            },
            title = {
                Text(
                    text = "Update Available",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Text(
                    text = "Version ${info.latestVersion} is available.\n\n${info.releaseNotes}\n\nDownload and install?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    updateManager.downloadAndInstall(context, info.downloadUrl)
                    prefs.edit().putString("last_prompted_version", info.latestVersion).apply()
                    showDialog = false
                }) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    prefs.edit().putString("last_prompted_version", info.latestVersion).apply()
                    showDialog = false
                }) {
                    Text("Later")
                }
            }
        )
    }
}
