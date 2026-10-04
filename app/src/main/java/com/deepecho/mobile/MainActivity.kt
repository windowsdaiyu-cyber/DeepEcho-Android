package com.deepecho.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.deepecho.mobile.data.Settings
import com.deepecho.mobile.net.AppUpdater
import com.deepecho.mobile.net.Bus
import com.deepecho.mobile.player.PlayerClient
import com.deepecho.mobile.ui.AppUpdateDialog
import com.deepecho.mobile.ui.DeepEchoTheme
import com.deepecho.mobile.ui.HomeScreen
import com.deepecho.mobile.ui.LibraryScreen
import com.deepecho.mobile.ui.LiveThemeBackdrop
import com.deepecho.mobile.ui.MiniPlayer
import com.deepecho.mobile.ui.PlayerScreen
import com.deepecho.mobile.ui.SearchScreen
import com.deepecho.mobile.ui.SettingsScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        PlayerClient.connect(applicationContext)
        setContent { App() }
    }

    override fun onResume() {
        super.onResume()
        AppUpdater.onAppResumed(this)
    }
}

@Composable
fun App() {
    val theme by Settings.theme.collectAsState()
    val ctx = LocalContext.current

    DeepEchoTheme(theme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            var tab by rememberSaveable { mutableIntStateOf(0) }
            var showPlayer by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                Bus.messages.collect { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
            }
            LaunchedEffect(Unit) {
                delay(1200)
                AppUpdater.autoCheck(ctx)
            }
            BackHandler(enabled = showPlayer) { showPlayer = false }

            Box(Modifier.fillMaxSize()) {
                LiveThemeBackdrop(Modifier.fillMaxSize())
                Scaffold(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    bottomBar = {
                        Column {
                            MiniPlayer { showPlayer = true }
                            NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)) {
                                val items = listOf(
                                    "Home" to Icons.Filled.Home,
                                    "Search" to Icons.Filled.Search,
                                    "Library" to Icons.Filled.LibraryMusic,
                                    "Settings" to Icons.Filled.Settings
                                )
                                items.forEachIndexed { i, (label, icon) ->
                                    NavigationBarItem(
                                        selected = tab == i,
                                        onClick = { tab = i },
                                        icon = { Icon(icon, label) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }
                ) { pad ->
                    Box(Modifier.padding(pad).fillMaxSize()) {
                        when (tab) {
                            0 -> HomeScreen(onOpenSearch = { tab = 1 })
                            1 -> SearchScreen()
                            2 -> LibraryScreen()
                            else -> SettingsScreen()
                        }
                    }
                }
                AnimatedVisibility(
                    visible = showPlayer,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it }
                ) {
                    PlayerScreen(onClose = { showPlayer = false })
                }
                AppUpdateDialog()
            }
        }
    }
}
