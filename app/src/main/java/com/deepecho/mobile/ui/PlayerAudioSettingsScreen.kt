package com.deepecho.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepecho.mobile.data.Settings
import com.deepecho.mobile.data.Store
import com.deepecho.mobile.player.AudioFx

@Composable
fun PlayerAudioSettingsScreen(onBack: () -> Unit) {
    val dataSaver by Settings.dataSaverMode.collectAsState()
    val musicQuality by Settings.quality.collectAsState()
    val downloadQuality by Settings.downloadQuality.collectAsState()
    val wifiQuality by Settings.wifiQuality.collectAsState()
    val mobileQuality by Settings.mobileQuality.collectAsState()
    val downloadMetadata by Settings.downloadWithMetadata.collectAsState()
    val wifiOnly by Settings.downloadWifiOnly.collectAsState()
    val playbackEngine by Settings.playbackEngine.collectAsState()
    val crossfadeSeconds by Settings.crossfadeSeconds.collectAsState()
    val skipSilence by Settings.skipSilence.collectAsState()
    val instantSkip by Settings.instantSkipSilence.collectAsState()
    val normalization by Settings.audioNormalization.collectAsState()
    val loudness by Settings.loudnessPreset.collectAsState()
    val spatial by Settings.spatialAudio.collectAsState()
    val offload by Settings.audioOffload.collectAsState()
    val preloadNext by Settings.preloadNextSong.collectAsState()
    val preloadLimit by Settings.preloadLimit.collectAsState()
    val preloadLyrics by Settings.preloadLyrics.collectAsState()
    val progressiveSeek by Settings.progressiveSeek.collectAsState()
    val persistentQueue by Settings.persistentQueue.collectAsState()
    val autoLoad by Settings.autoLoadMoreSongs.collectAsState()
    val autoDownloadLike by Settings.autoDownloadOnLike.collectAsState()
    val similarContent by Settings.enableSimilarContent.collectAsState()
    val persistentShuffle by Settings.persistentShuffle.collectAsState()
    val rememberShuffleRepeat by Settings.rememberShuffleRepeat.collectAsState()
    val shuffleCollectionFirst by Settings.shufflePlaylistFirst.collectAsState()
    val preventDuplicates by Settings.preventDuplicateTracks.collectAsState()
    val autoSkipError by Settings.autoSkipOnError.collectAsState()
    val stopTaskClear by Settings.stopMusicOnTaskClear.collectAsState()
    val pauseMuted by Settings.pauseWhenMediaMuted.collectAsState()
    val resumeBluetooth by Settings.resumeOnBluetoothConnect.collectAsState()
    val keepScreenOn by Settings.keepScreenOnExpanded.collectAsState()
    val historyDays by Settings.historyDurationDays.collectAsState()
    val fx by AudioFx.info.collectAsState()
    val eqLevels by AudioFx.levels.collectAsState()
    val bass by AudioFx.bass.collectAsState()
    val eqPreset by AudioFx.preset.collectAsState()

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(
                    "Player and audio",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item { PlayerAudioSection("DATA SAVER") }
        item {
            SettingSwitchCard(
                icon = Icons.Filled.Tune,
                title = "Data Saver Mode (Beta)",
                description = "Uses low stream quality and reduces speculative preloading to a small source-only lookahead. Ambient video stays blocked while this is ON.",
                checked = dataSaver,
                onCheckedChange = Settings::setDataSaverMode
            )
        }

        item { PlayerAudioSection("PLAYER") }
        item {
            SettingChoiceCard(Icons.Filled.MusicNote, "Music Quality", qualityLabel(musicQuality)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(3) { i ->
                        val label = listOf("Low", "Balanced", "High")[i]
                        FilterChip(
                            selected = musicQuality == i,
                            onClick = { Settings.setQuality(i) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
        item {
            SettingChoiceCard(Icons.Filled.MusicNote, "Wi-Fi Quality", qualityLabel(wifiQuality)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(3) { i ->
                        val label = listOf("Data Saver", "Normal", "High")[i]
                        FilterChip(selected = wifiQuality == i, onClick = { Settings.setWifiQuality(i) }, label = { Text(label) })
                    }
                }
            }
        }
        item {
            SettingChoiceCard(Icons.Filled.MusicNote, "Mobile Data Quality", qualityLabel(mobileQuality)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(3) { i ->
                        val label = listOf("Data Saver", "Normal", "High")[i]
                        FilterChip(selected = mobileQuality == i, onClick = { Settings.setMobileQuality(i) }, label = { Text(label) })
                    }
                }
            }
        }
        item {
            SettingChoiceCard(Icons.Filled.Download, "Download Quality", qualityLabel(downloadQuality)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(3) { i ->
                        val label = listOf("Low", "Balanced", "High")[i]
                        FilterChip(
                            selected = downloadQuality == i,
                            onClick = { Settings.setDownloadQuality(i) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
        item {
            SettingSwitchCard(
                Icons.Filled.Download,
                "Download with metadata",
                "Saves song info plus available synced/plain lyrics beside the downloaded audio.",
                downloadMetadata,
                Settings::setDownloadWithMetadata
            )
        }
        item {
            SettingSwitchCard(
                Icons.Filled.Download,
                "Download on Wi-Fi only",
                "Blocks new song downloads on mobile data.",
                wifiOnly,
                Settings::setDownloadWifiOnly
            )
        }
        item {
            SettingChoiceCard(Icons.Filled.PlayArrow, "Playback Engine", playbackEngine) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Auto", "Media3").forEach { label ->
                        FilterChip(
                            selected = playbackEngine == label,
                            onClick = { Settings.setPlaybackEngine(label) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
        item {
            SettingChoiceCard(Icons.Filled.GraphicEq, "Crossfade", if (crossfadeSeconds == 0) "Off" else "${crossfadeSeconds}s") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(6) { sec ->
                        FilterChip(
                            selected = crossfadeSeconds == sec,
                            onClick = { Settings.setCrossfadeSeconds(sec) },
                            label = { Text(if (sec == 0) "Off" else "${sec}s") }
                        )
                    }
                }
                Text(
                    "DeepEcho uses a stability-first fade transition: current track fades out and the next track fades in without delaying manual Next.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            UnsupportedSettingCard(
                Icons.Filled.GraphicEq,
                "Automix (Beta)",
                "Beat-matched overlap is not faked; this stays disabled until the dedicated transition engine is ready."
            )
        }
        item {
            SettingChoiceCard(Icons.Filled.History, "History duration", "$historyDays days") {
                Column {
                    Slider(
                        value = historyDays.toFloat(),
                        onValueChange = { Settings.setHistoryDurationDays(it.toInt()); Store.refreshHistoryRetention() },
                        valueRange = 1f..365f,
                        steps = 11
                    )
                    Text(
                        "$historyDays days",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item { PlayerAudioSection("PLAYBACK PROCESSING") }
        item {
            SettingSwitchCard(
                Icons.Filled.SkipNext,
                "Skip silence",
                "Uses Media3's audio silence skipper for silent passages.",
                skipSilence,
                Settings::setSkipSilence
            )
        }
        item {
            SettingSwitchCard(
                Icons.Filled.SkipNext,
                "Instantly skip silence",
                "Turns on the safe silence skipper immediately; kept separate so the UI matches your requested control set.",
                instantSkip,
                { enabled ->
                    Settings.setInstantSkipSilence(enabled)
                    if (enabled) Settings.setSkipSilence(true)
                }
            )
        }
        item {
            SettingSwitchCard(
                Icons.Filled.GraphicEq,
                "Audio normalization",
                "Applies DeepEcho's loudness post-processing to the active audio session.",
                normalization,
                { enabled -> Settings.setAudioNormalization(enabled); AudioFx.applyPlayerSettings() }
            )
        }
        item {
            SettingChoiceCard(Icons.Filled.GraphicEq, "Loudness Preset", loudness) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Quiet", "Normal", "Loud").forEach { label ->
                        FilterChip(
                            selected = loudness == label,
                            onClick = { Settings.setLoudnessPreset(label); AudioFx.applyPlayerSettings() },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
        item {
            SettingSwitchCard(
                Icons.Filled.GraphicEq,
                "Spatial Audio",
                "Adds a device-supported stereo virtualizer effect for a wider sound stage.",
                spatial,
                { enabled -> Settings.setSpatialAudio(enabled); AudioFx.applyPlayerSettings() }
            )
        }
        item {
            SettingSwitchCard(
                Icons.Filled.GraphicEq,
                "Enable offload",
                "Lets supported devices use the hardware audio-offload path. Unsupported formats fall back normally.",
                offload,
                Settings::setAudioOffload
            )
        }
        item {
            SettingChoiceCard(Icons.Filled.GraphicEq, "Equalizer", if (fx == null) "Play a song to activate" else eqPreset) {
                val info = fx
                if (info == null) {
                    Text(
                        "Koi gaana play karo — active audio session milte hi equalizer controls yahan aa jayenge.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(AudioFx.presets.size) { index ->
                                val name = AudioFx.presets.keys.elementAt(index)
                                FilterChip(
                                    selected = eqPreset == name,
                                    onClick = { AudioFx.applyPreset(name) },
                                    label = { Text(name) }
                                )
                            }
                        }
                        info.freqsHz.forEachIndexed { band, hz ->
                            Text(
                                if (hz >= 1000) "${"%.1f".format(hz / 1000f)} kHz" else "$hz Hz",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = (eqLevels.getOrNull(band) ?: 0).toFloat(),
                                onValueChange = { AudioFx.setBand(band, it.toInt()) },
                                valueRange = info.minMb.toFloat()..info.maxMb.toFloat()
                            )
                        }
                        Text("Bass boost", style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = bass.toFloat(),
                            onValueChange = { AudioFx.setBass(it.toInt()) },
                            valueRange = 0f..1000f
                        )
                    }
                }
            }
        }

        item { PlayerAudioSection("PRELOAD") }
        item {
            SettingSwitchCard(
                Icons.Filled.SkipNext,
                "Preload Next Song",
                "Resolve likely next tracks ahead of time for faster transitions.",
                preloadNext,
                Settings::setPreloadNextSong
            )
        }
        item {
            SettingChoiceCard(Icons.Filled.MusicNote, "Preload Limit", preloadLimit.toString()) {
                Column {
                    Slider(
                        value = preloadLimit.toFloat(),
                        onValueChange = { Settings.setPreloadLimit(it.toInt()) },
                        valueRange = 1f..10f,
                        steps = 8
                    )
                    Text("$preloadLimit", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            SettingSwitchCard(
                Icons.Filled.MusicNote,
                "Preload Lyrics",
                "Cache lyrics for preloaded songs while data saver is off.",
                preloadLyrics,
                Settings::setPreloadLyrics
            )
        }
        item {
            SettingSwitchCard(
                Icons.Filled.PlayArrow,
                "Progressive seek",
                "Repeated ±10s presses add another 5 seconds each time, up to a safe limit.",
                progressiveSeek,
                Settings::setProgressiveSeek
            )
        }

        item { PlayerAudioSection("QUEUE") }
        item {
            SettingSwitchCard(Icons.Filled.QueueMusic, "Persistent queue", "Optional full-queue restore. Keep OFF for DeepEcho's default song-only paused session restore.", persistentQueue, Settings::setPersistentQueue)
        }
        item {
            SettingSwitchCard(Icons.Filled.QueueMusic, "Auto load more songs", "Allow taste-aware similar songs near the end of a discovery queue.", autoLoad, Settings::setAutoLoadMoreSongs)
        }
        item {
            SettingSwitchCard(Icons.Filled.Download, "Auto download on like", "Automatically download a song when you like it.", autoDownloadLike, Settings::setAutoDownloadOnLike)
        }
        item {
            SettingSwitchCard(Icons.Filled.MusicNote, "Enable similar content", "Allow Smart Autoplay to append related songs after discovery playback.", similarContent, Settings::setEnableSimilarContent)
        }
        item {
            SettingSwitchCard(Icons.Filled.Shuffle, "Persistent shuffle", "Keep shuffle enabled when starting new songs or collections.", persistentShuffle, Settings::setPersistentShuffle)
        }
        item {
            SettingSwitchCard(Icons.Filled.Shuffle, "Remember shuffle and repeat", "Restore your previous shuffle and repeat modes.", rememberShuffleRepeat, Settings::setRememberShuffleRepeat)
        }
        item {
            SettingSwitchCard(Icons.Filled.Shuffle, "Shuffle playlist/album first", "Shuffle the original collection first, then continue with similar content when enabled.", shuffleCollectionFirst, Settings::setShufflePlaylistFirst)
        }
        item {
            SettingSwitchCard(Icons.Filled.QueueMusic, "Prevent duplicate tracks in queue", "Remove an older queued copy before inserting the same song again.", preventDuplicates, Settings::setPreventDuplicateTracks)
        }
        item {
            SettingSwitchCard(Icons.Filled.SkipNext, "Auto skip to next song when error occurs", "PlaybackService retries once; if the same track still fails, DeepEcho advances to the next queued song.", autoSkipError, Settings::setAutoSkipOnError)
        }

        item { PlayerAudioSection("MISC") }
        item {
            SettingSwitchCard(Icons.Filled.QueueMusic, "Stop music on task clear", "Stops playback when DeepEcho is swiped away from recents.", stopTaskClear, Settings::setStopMusicOnTaskClear)
        }
        item {
            SettingSwitchCard(Icons.Filled.MusicNote, "Pause music when media is muted", "Pauses playback when the music stream volume reaches zero.", pauseMuted, Settings::setPauseWhenMediaMuted)
        }
        item {
            SettingSwitchCard(Icons.Filled.Bluetooth, "Resume on Bluetooth connect", "Resume playback when a Bluetooth audio output is added.", resumeBluetooth, Settings::setResumeOnBluetoothConnect)
        }
        item {
            SettingSwitchCard(Icons.Filled.PlayArrow, "Keep screen on when player is expanded", "Keeps the display awake while the full player is open.", keepScreenOn, Settings::setKeepScreenOnExpanded)
        }
        item {
            UnsupportedSettingCard(
                Icons.Filled.Download,
                "Export as MP3",
                "The switch is intentionally disabled until a safe on-device encoder is bundled; DeepEcho will not fake-transcode files."
            )
        }
    }
}

private fun qualityLabel(value: Int): String = when (value) {
    0 -> "Low"
    1 -> "Balanced"
    else -> "High"
}

@Composable
private fun PlayerAudioSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingSwitchCard(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Surface(
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.62f),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}

@Composable
private fun UnsupportedSettingCard(icon: ImageVector, title: String, description: String) {
    SettingSwitchCard(icon, title, description, checked = false, onCheckedChange = {}, enabled = false)
}

@Composable
private fun SettingChoiceCard(
    icon: ImageVector,
    title: String,
    value: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                    Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.size(10.dp))
            content()
        }
    }
}

@Composable
private fun SettingActionCard(icon: ImageVector, title: String, description: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
