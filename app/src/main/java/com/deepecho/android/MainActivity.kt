package com.deepecho.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepecho.android.ui.DeepEchoApp
import com.deepecho.android.ui.theme.DeepEchoTheme
import com.deepecho.android.ui.theme.DeepEchoThemes

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MainViewModel
    private var pendingOverlayEnable = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        requestNotificationPermissionIfNeeded()

        setContent {
            val themeKey = viewModel.themeKey.collectAsStateWithLifecycle().value
            val spec = DeepEchoThemes.byKey(themeKey)
            DeepEchoTheme(spec) {
                DeepEchoApp(
                    viewModel = viewModel,
                    spec = spec,
                    onFloatingLyricsRequest = { requestFloatingLyrics() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (pendingOverlayEnable && canDrawOverlays()) {
            pendingOverlayEnable = false
            viewModel.setFloatingLyrics(true)
        }
    }

    private fun requestFloatingLyrics() {
        if (canDrawOverlays()) {
            viewModel.setFloatingLyrics(true)
            return
        }
        pendingOverlayEnable = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
    }

    private fun canDrawOverlays(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 71)
        }
    }
}
