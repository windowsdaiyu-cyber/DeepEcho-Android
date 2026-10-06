package com.deepecho.mobile.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepecho.mobile.data.Settings
import com.deepecho.mobile.data.ThemeAdvisor
import com.deepecho.mobile.net.AppUpdater
import com.deepecho.mobile.net.Downloads
import com.deepecho.mobile.player.PlayerClient

@Composable
fun SettingsScreen(onBack: (() -> Unit)? = null) {
    val theme by Settings.theme.collectAsState()
    val smartAutoplay by Settings.smartAutoplay.collectAsState()
    val autoThemeWithSong by Settings.autoThemeWithSong.collectAsState()
    val ambientMode by Settings.ambientMode.collectAsState()
    val smartCache by Settings.smartCache.collectAsState()
    val autoUpdateCheck by Settings.autoUpdateCheck.collectAsState()
    val playerGestures by Settings.playerGestures.collectAsState()
    val miniPlayerLyrics by Settings.miniPlayerLyrics.collectAsState()
    val notificationLyrics by Settings.notificationLyrics.collectAsState()
    val artworkDynamicColors by Settings.artworkDynamicColors.collectAsState()
    val updateState by AppUpdater.state.collectAsState()
    val context = LocalContext.current
    var showDeveloper by remember { mutableStateOf(false) }
    var showPlayerAudio by remember { mutableStateOf(false) }
    var showLocalMusic by remember { mutableStateOf(false) }
    var showPodcasts by remember { mutableStateOf(false) }
    var showBlocked by remember { mutableStateOf(false) }
    var settingsQuery by remember { mutableStateOf("") }

    if (showPlayerAudio) {
        PlayerAudioSettingsScreen(onBack = { showPlayerAudio = false })
        return
    }
    if (showLocalMusic) {
        LocalMusicScreen(onBack = { showLocalMusic = false })
        return
    }
    if (showPodcasts) {
        PodcastsScreen(onBack = { showPodcasts = false })
        return
    }
    if (showBlocked) {
        BlockedArtistsScreen(onBack = { showBlocked = false })
        return
    }

    if (showDeveloper) {
        AlertDialog(
            onDismissRequest = { showDeveloper = false },
            title = { Text("About Developer", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "This is developed by Daiyan Sarwar { ALHAMDULLILAH }",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Please follow him on Instagram to support him",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "@enchanting_daiyu",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://www.instagram.com/enchanting_daiyu/")
                                    )
                                )
                            }
                        }.padding(vertical = 8.dp)
                    )
                    Text(
                        "Contact Tahsin Khan — 6290302598 [Developer's Son]",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_DIAL,
                                        Uri.parse("tel:6290302598")
                                    )
                                )
                            }
                        }.padding(vertical = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDeveloper = false }) { Text("Close") }
            }
        )
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            OutlinedTextField(
                value = settingsQuery,
                onValueChange = { settingsQuery = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text("Search settings") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (settingsQuery.isNotBlank()) {
            item {
                SettingsSearchResults(
                    query = settingsQuery,
                    onPlayerAudio = { settingsQuery = ""; showPlayerAudio = true },
                    onLocalMusic = { settingsQuery = ""; showLocalMusic = true },
                    onPodcasts = { settingsQuery = ""; showPodcasts = true },
                    onBlocked = { settingsQuery = ""; showBlocked = true }
                )
            }
        }

        item { Section("Live Themes") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Animated live particles", fontWeight = FontWeight.SemiBold)
                Text(
                    "DeepEcho uses Live Themes only. Golden is the first-launch default; Ruby adds a cinematic red particle/glass UI. Your selected live theme is remembered for future sessions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(Palettes.size) { i ->
                    val palette = Palettes[i]
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { Settings.selectLiveTheme(palette.name) }
                    ) {
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(palette.primary.copy(alpha = 0.88f))
                                .border(
                                    if (theme == palette.name) 3.dp else 1.dp,
                                    if (theme == palette.name) MaterialTheme.colorScheme.onBackground
                                    else palette.primary.copy(alpha = 0.55f),
                                    CircleShape
                                )
                        )
                        Text(palette.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }


        item { Section("Ambient Mode") }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Theme-aware player ambience")
                    Text(
                        "Theme-specific ambience: Golden dust, Ruby energy ribbons/embers, Ocean currents, Violet orbit glow, Rose drift, Emerald fireflies and AMOLED stars. Quick toggle is also beside Repeat in the player.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = ambientMode, onCheckedChange = Settings::setAmbientMode)
            }
        }

        item { Section("Auto Theme With Song") }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Change colour theme with each song")
                    Text(
                        "DeepEcho suggests a Live Theme colour family from the current song title/artist. When ON, the selected Live Theme changes automatically and is remembered.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoThemeWithSong,
                    onCheckedChange = { enabled ->
                        Settings.setAutoThemeWithSong(enabled)
                        if (enabled) {
                            PlayerClient.currentSong?.let { current ->
                                Settings.setTheme(ThemeAdvisor.suggest(current, theme))
                            }
                        }
                    }
                )
            }
        }

        item { Section("Smart Autoplay") }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Taste-aware next songs")
                    Text(
                        "Home/Search se play karne par DeepEcho similar songs smartly queue karega.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = smartAutoplay, onCheckedChange = { Settings.setSmartAutoplay(it) })
            }
        }

        item { Section("Player Experience") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Player gestures")
                        Text("Optional swipes, double-tap Like and long-press Quick Actions inside safe player regions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = playerGestures, onCheckedChange = Settings::setPlayerGestures)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Mini Player Lyrics")
                        Text("Shows only the already-resolved synced lyric line; it never starts a second lyrics lookup.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = miniPlayerLyrics, onCheckedChange = Settings::setMiniPlayerLyrics)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Show Lyrics in Notification")
                        Text("Optional line-level updates only when current synced lyrics are already available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = notificationLyrics, onCheckedChange = Settings::setNotificationLyrics)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Artwork Dynamic Colors")
                        Text("Adds a cached artwork accent behind the current Live Theme without replacing it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = artworkDynamicColors, onCheckedChange = Settings::setArtworkDynamicColors)
                }
            }
        }

        item { Section("Smart Cache") }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Keep recent stream data warm")
                    Text(
                        "Optional 256 MiB cache can save one likely-next song in the background on unmetered Wi-Fi only. Completed cache files can replay locally; it stays OFF by default to protect data/storage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = smartCache, onCheckedChange = Settings::setSmartCache)
            }
        }

        item { Section("Player") }
        item {
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f))
                    .clickable { showPlayerAudio = true }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f)) {
                    Text("Player and audio", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Playback quality, downloads, audio processing, preload, queue and device behavior",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item { Section("Library & Content") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsNavCard(Icons.Filled.LibraryMusic, "Local Music", "Device songs, albums, artists and folders") { showLocalMusic = true }
                SettingsNavCard(Icons.Filled.Podcasts, "Podcasts", "Search, follow, resume and play podcast episodes") { showPodcasts = true }
                SettingsNavCard(Icons.Filled.Block, "Blocked Artists", "Manage artists excluded from recommendations") { showBlocked = true }
            }
        }

        item { Section("Sleep Timer") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = false, onClick = { PlayerClient.startSleepTimer(15) }, label = { Text("15 min") }) }
                item { FilterChip(selected = false, onClick = { PlayerClient.startSleepTimer(30) }, label = { Text("30 min") }) }
                item { FilterChip(selected = false, onClick = { PlayerClient.startSleepTimer(60) }, label = { Text("60 min") }) }
                item { FilterChip(selected = false, onClick = { PlayerClient.sleepAtEndOfSong() }, label = { Text("End song") }) }
                item { FilterChip(selected = PlayerClient.sleepUntilMs == 0L, onClick = { PlayerClient.cancelSleepTimer() }, label = { Text("Off") }) }
            }
        }

        item { Section("Downloads") }
        item { Text(Downloads.folderPath(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }


        item { Section("App Updates") }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Check for updates automatically")
                    Text(
                        "PC-style flow: DEEP-ECHO checks official GitHub Releases on launch, downloads the APK in-app, then Android asks you to confirm installation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoUpdateCheck,
                    onCheckedChange = Settings::setAutoUpdateCheck
                )
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Current version ${AppUpdater.currentVersion(context)}")
                    Text(
                        when (val state = updateState) {
                            AppUpdater.State.Checking -> "Checking GitHub Releases…"
                            is AppUpdater.State.Available -> "Version ${state.release.version} is available"
                            is AppUpdater.State.Downloading -> "Downloading ${state.percent}%"
                            is AppUpdater.State.Ready -> "Update downloaded — ready to install"
                            is AppUpdater.State.InstallPermission -> "Android install permission is required"
                            is AppUpdater.State.Error -> state.message
                            AppUpdater.State.Idle -> "You can also check manually anytime"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = { AppUpdater.checkNow(context) },
                    enabled = updateState != AppUpdater.State.Checking && updateState !is AppUpdater.State.Downloading
                ) { Text("Check Now") }
            }
        }

        item { Section("About Developer") }
        item {
            Column(
                Modifier.fillMaxWidth()
                    .clickable { showDeveloper = true }
                    .padding(vertical = 10.dp)
            ) {
                Text(
                    "Daiyan Sarwar",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Developer info • Instagram • contact",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { Section("About") }
        item {
            Text(
                "DEEP-ECHO Mobile 1.12.8 TEST • Visible UX / Player Experience upgrade adds Daily Mixes, Mood context, " +
                    "five player presentation modes, Quick Settings tiles, share cards, Quick Actions feedback, dynamic artwork accents, " +
                    "Mini Player lyrics, session insights, optional notification lyrics and Continue Your Vibe. Existing playback, lyrics, downloads and Smart Autoplay remain the authority.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp)
    )
}


@Composable
private fun SettingsNavCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsSearchResults(
    query: String,
    onPlayerAudio: () -> Unit,
    onLocalMusic: () -> Unit,
    onPodcasts: () -> Unit,
    onBlocked: () -> Unit
) {
    val q = query.trim().lowercase()
    data class Result(val title: String, val keys: String, val action: () -> Unit)
    val all = listOf(
        Result("Player and audio", "playback quality equalizer data saver crossfade bluetooth mute preload queue audio", onPlayerAudio),
        Result("Local Music", "local music folders scan library device mp3 flac", onLocalMusic),
        Result("Podcasts", "podcast episodes continue listening follow", onPodcasts),
        Result("Blocked Artists", "block artist recommendations discovery autoplay", onBlocked),
        Result("Lyrics / Floating Lyrics", "lyrics floating romanized word by word", { UiEvents.openPlayer() }),
        Result("Ambient Mode", "ambient video fullscreen theme", { Settings.setAmbientMode(true); UiEvents.openPlayer() })
    ).filter { it.title.lowercase().contains(q) || it.keys.contains(q) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Search results", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        if (all.isEmpty()) Text("No matching setting", color = MaterialTheme.colorScheme.onSurfaceVariant)
        all.forEach { r ->
            Row(Modifier.fillMaxWidth().clickable { r.action() }.padding(vertical = 10.dp)) {
                Text(r.title, fontWeight = FontWeight.Medium)
            }
        }
    }
}
