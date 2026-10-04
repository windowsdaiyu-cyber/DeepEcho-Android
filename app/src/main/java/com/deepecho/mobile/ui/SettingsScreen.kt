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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import com.deepecho.mobile.player.AudioFx
import com.deepecho.mobile.player.PlayerClient

@Composable
fun SettingsScreen() {
    val theme by Settings.theme.collectAsState()
    val liveTheme by Settings.liveTheme.collectAsState()
    val quality by Settings.quality.collectAsState()
    val smartAutoplay by Settings.smartAutoplay.collectAsState()
    val autoThemeWithSong by Settings.autoThemeWithSong.collectAsState()
    val ambientMode by Settings.ambientMode.collectAsState()
    val smartCache by Settings.smartCache.collectAsState()
    val autoUpdateCheck by Settings.autoUpdateCheck.collectAsState()
    val updateState by AppUpdater.state.collectAsState()
    val fx by AudioFx.info.collectAsState()
    val levels by AudioFx.levels.collectAsState()
    val bass by AudioFx.bass.collectAsState()
    val preset by AudioFx.preset.collectAsState()
    val context = LocalContext.current
    var showDeveloper by remember { mutableStateOf(false) }

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
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }

        item { Section("Classic Theme") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(Palettes.size) { i ->
                    val palette = Palettes[i]
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            Settings.setLiveTheme(false)
                            Settings.setTheme(palette.name)
                        }
                    ) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(palette.primary)
                                .border(
                                    if (!liveTheme && theme == palette.name) 3.dp else 0.dp,
                                    MaterialTheme.colorScheme.onBackground,
                                    CircleShape
                                )
                        )
                        Text(palette.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        item { Section("Live Theme") }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Animated live particles")
                    Text(
                        "PC DeepEcho-inspired full-screen particle fields with theme-specific motion, soft depth and a readability veil so text, buttons and lyrics stay clear.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = liveTheme, onCheckedChange = { enabled ->
                    Settings.setLiveTheme(enabled)
                })
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(Palettes.size) { i ->
                    val palette = Palettes[i]
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            Settings.selectLiveTheme(palette.name)
                        }
                    ) {
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(palette.primary.copy(alpha = 0.88f))
                                .border(
                                    if (liveTheme && theme == palette.name) 3.dp else 1.dp,
                                    if (liveTheme && theme == palette.name) MaterialTheme.colorScheme.onBackground
                                    else palette.primary.copy(alpha = 0.55f),
                                    CircleShape
                                )
                        )
                        Text("Live ${palette.name}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
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
                        "Theme-specific Now Playing ambience: Golden dust, Ocean currents, Violet orbit glow, Rose drift, Emerald fireflies and AMOLED stars. Quick toggle is also beside Repeat in the player.",
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
                        "DeepEcho suggests a theme from the song title/artist. When ON, only the colour family changes automatically; Classic/Live mode stays as you selected it.",
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

        item { Section("Audio quality (stream + download)") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Low", "Medium", "High").forEachIndexed { i, label ->
                    FilterChip(selected = quality == i, onClick = { Settings.setQuality(i) }, label = { Text(label) })
                }
            }
        }

        item { Section("Equalizer") }
        if (fx == null) {
            item { Text("Koi gaana play karo — tab equalizer active hoga.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            val info = fx!!
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val names = AudioFx.presets.keys.toList()
                    items(names.size) { i ->
                        FilterChip(selected = preset == names[i], onClick = { AudioFx.applyPreset(names[i]) }, label = { Text(names[i]) })
                    }
                }
            }
            items(info.freqsHz.size) { band ->
                val hz = info.freqsHz[band]
                val label = if (hz >= 1000) "${"%.1f".format(hz / 1000f)} kHz" else "$hz Hz"
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        Modifier.padding(end = 8.dp).size(width = 64.dp, height = 24.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = (levels.getOrNull(band) ?: 0).toFloat(),
                        onValueChange = { AudioFx.setBand(band, it.toInt()) },
                        valueRange = info.minMb.toFloat()..info.maxMb.toFloat()
                    )
                }
            }
            item { Text("Bass boost", style = MaterialTheme.typography.bodyMedium) }
            item { Slider(value = bass.toFloat(), onValueChange = { AudioFx.setBass(it.toInt()) }, valueRange = 0f..1000f) }
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
                "DEEP-ECHO Mobile 1.10.1 TEST • brighter PC-style particles, responsive animated lyrics, dedicated Floating Lyrics switch, audio-reactive lyric graph/bounce/shine, theme-specific Like/Download bursts and visible Listening Stats shortcut. " +
                    "PlaybackService and download pipeline remain protected while search, lyrics matching and UI layers are upgraded.",
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
