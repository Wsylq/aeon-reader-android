package com.aeonreader

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.aeonreader.data.network.AppUpdateManager
import com.aeonreader.data.network.UpdateInfo
import com.aeonreader.ui.navigation.AeonNavHost
import com.aeonreader.ui.theme.AeonTheme
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
                val navController = rememberNavController()
                HandleShortcutIntent(navController)
                Box(modifier = Modifier.fillMaxSize()) {
                    AeonNavHost(navController = navController)
                    UpdateDialogHost(context = this@MainActivity, updateManager = updateManager)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@Composable
private fun HandleShortcutIntent(navController: NavHostController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? MainActivity ?: return
    val intent = activity.intent ?: return

    LaunchedEffect(intent.data) {
        val destination = when (intent.data?.toString()) {
            "eon://search" -> "search"
            "eon://bookmarks" -> "bookmarks"
            else -> null
        }
        if (destination != null) {
            navController.navigate(destination) {
                popUpTo(navController.graph.startDestinationId) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
            // Prevent re-navigation on recomposition
            activity.intent?.data = null
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
            val info = if (Build.VERSION.SDK_INT >= 33) {
                val packageInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
                packageInfo.versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }
            info ?: "0"
        } catch (_: Exception) { "0" }

        withContext(Dispatchers.IO) {
            val result = updateManager.checkForUpdate(currentVersion)
            val info = result.getOrNull() ?: return@withContext

            if (info.latestVersion == currentVersion.trim()) return@withContext

            val lastPromptedVersion = prefs.getString("last_prompted_version", "") ?: ""
            if (lastPromptedVersion == info.latestVersion) return@withContext

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
                Column {
                    Text(
                        text = "Version ${info.latestVersion} is ready to download.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (info.releaseNotes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "What's New",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(
                            modifier = Modifier
                                .heightIn(max = 250.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = info.releaseNotes,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
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
