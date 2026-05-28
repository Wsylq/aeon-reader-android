package com.aeonreader

import android.content.Context
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
    var shown by remember { mutableStateOf(false) }

    val prefs = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    val updatePromptShown = prefs.getBoolean("update_prompt_shown", false)

    LaunchedEffect(Unit) {
        if (updatePromptShown) return@LaunchedEffect
        if (shown) return@LaunchedEffect

        show@ withContext(Dispatchers.IO) {
            val currentVersion = "1.0"
            val result = updateManager.checkForUpdate(currentVersion)
            val info = result.getOrNull() ?: return@withContext
            updateInfo = info
            shown = true
        }
    }

    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = {
                prefs.edit().putBoolean("update_prompt_shown", true).apply()
                updateInfo = null
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
                    prefs.edit().putBoolean("update_prompt_shown", true).apply()
                    updateInfo = null
                }) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    prefs.edit().putBoolean("update_prompt_shown", true).apply()
                    updateInfo = null
                }) {
                    Text("Later")
                }
            }
        )
    }
}
