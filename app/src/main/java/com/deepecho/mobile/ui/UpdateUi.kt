package com.deepecho.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepecho.mobile.net.AppUpdater

@Composable
fun AppUpdateDialog() {
    val context = LocalContext.current
    val state by AppUpdater.state.collectAsState()

    when (val s = state) {
        is AppUpdater.State.Available -> {
            AlertDialog(
                onDismissRequest = AppUpdater::dismiss,
                title = { Text("DEEP-ECHO update available", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "${AppUpdater.currentVersion(context)}  →  ${s.release.version}",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (s.release.notes.isNotBlank()) {
                            Text(
                                s.release.notes.take(700),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                "A newer DEEP-ECHO Mobile build is ready.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { AppUpdater.download(context, s.release) }) {
                        Text("Update Now")
                    }
                },
                dismissButton = {
                    TextButton(onClick = AppUpdater::dismiss) { Text("Later") }
                }
            )
        }

        is AppUpdater.State.Downloading -> {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Downloading update") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "DEEP-ECHO Mobile ${s.release.version}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LinearProgressIndicator(
                            progress = { s.percent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("${s.percent}%", fontWeight = FontWeight.SemiBold)
                    }
                },
                confirmButton = { }
            )
        }

        is AppUpdater.State.Ready -> {
            AlertDialog(
                onDismissRequest = AppUpdater::dismiss,
                title = { Text("Update downloaded") },
                text = {
                    Text(
                        "Android will now open its installer. Your DEEP-ECHO data and settings stay in place.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    TextButton(onClick = { AppUpdater.installReady(context) }) { Text("Install") }
                },
                dismissButton = {
                    TextButton(onClick = AppUpdater::dismiss) { Text("Later") }
                }
            )
        }

        is AppUpdater.State.InstallPermission -> {
            AlertDialog(
                onDismissRequest = AppUpdater::dismiss,
                title = { Text("Allow app updates") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Android needs one permission before DEEP-ECHO can hand its downloaded APK to the system installer.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Enable “Allow from this source”, then return to DEEP-ECHO. The installer will continue automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { AppUpdater.openInstallPermission(context) }) {
                        Text("Open Settings")
                    }
                },
                dismissButton = {
                    TextButton(onClick = AppUpdater::dismiss) { Text("Later") }
                }
            )
        }

        is AppUpdater.State.Error -> {
            AlertDialog(
                onDismissRequest = AppUpdater::dismiss,
                title = { Text("Update failed") },
                text = {
                    Text(
                        s.message,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                },
                confirmButton = {
                    TextButton(onClick = { AppUpdater.checkNow(context) }) { Text("Try Again") }
                },
                dismissButton = {
                    TextButton(onClick = AppUpdater::dismiss) { Text("Close") }
                }
            )
        }

        AppUpdater.State.Idle,
        AppUpdater.State.Checking -> Unit
    }
}
