package com.deepecho.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepecho.android.MainViewModel
import com.deepecho.android.model.Track
import com.deepecho.android.ui.theme.DeepEchoThemeSpec
import com.deepecho.android.ui.theme.DeepEchoThemes
import kotlin.math.roundToLong

enum class AppScreen(val label: String, val glyph: String) {
    HOME("Home", "⌂"), SEARCH("Search", "⌕"), LYRICS("Lyrics", "♫"), DOWNLOADS("Downloads", "⇩"), SETTINGS("Settings", "⚙")
}

@Composable
fun DeepEchoApp(
    viewModel: MainViewModel,
    spec: DeepEchoThemeSpec,
    onFloatingLyricsRequest: () -> Unit
) {
    val home by viewModel.home.collectAsStateWithLifecycle()
    val results by viewModel.search.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val current by viewModel.currentTrack.collectAsStateWithLifecycle()
    val lyric by viewModel.activeLyric.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val player by viewModel.playerState.collectAsStateWithLifecycle()
    val bridgeConnected by viewModel.bridgeConnected.collectAsStateWithLifecycle()
    val bridgeUrl by viewModel.bridgeUrl.collectAsStateWithLifecycle()
    val smartAutoplay by viewModel.smartAutoplay.collectAsStateWithLifecycle()
    val lyricsFx by viewModel.lyricsFx.collectAsStateWithLifecycle()
    val searching by viewModel.isSearching.collectAsStateWithLifecycle()
    val floatingLyricsEnabled by viewModel.floatingLyricsEnabled.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(AppScreen.HOME) }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground(spec)
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                Column(Modifier.navigationBarsPadding()) {
                    if (current != null) MiniPlayer(
                        track = current!!,
                        position = player.positionMs,
                        duration = if (player.durationMs > 0) player.durationMs else current!!.durationMs,
                        playing = player.isPlaying,
                        spec = spec,
                        onToggle = viewModel::togglePlayback,
                        onSeek = viewModel::seekTo,
                        onOpenLyrics = { screen = AppScreen.LYRICS }
                    )
                    NavigationBar(containerColor = spec.surface.copy(alpha = .96f)) {
                        AppScreen.entries.forEach { item ->
                            NavigationBarItem(
                                selected = screen == item,
                                onClick = { screen = item },
                                icon = { Text(item.glyph, color = if (screen == item) spec.accent else spec.muted) },
                                label = { Text(item.label, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp)
            ) {
                Header(spec, bridgeConnected, status)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.weight(1f)) {
                    when (screen) {
                        AppScreen.HOME -> HomeScreen(home, spec, viewModel::play, viewModel::download, viewModel::refreshHome)
                        AppScreen.SEARCH -> SearchScreen(results, searching, spec, viewModel::search, viewModel::play, viewModel::download)
                        AppScreen.LYRICS -> LyricsScreen(current, lyric, player.positionMs, lyricsFx, spec, viewModel::setLyricsFx, onFloatingLyricsRequest)
                        AppScreen.DOWNLOADS -> DownloadsScreen(downloads, spec, viewModel::play)
                        AppScreen.SETTINGS -> SettingsScreen(spec, bridgeUrl, smartAutoplay, lyricsFx, floatingLyricsEnabled, viewModel::setTheme, viewModel::setBridgeUrl, viewModel::setSmartAutoplay, viewModel::setLyricsFx, viewModel::setFloatingLyrics, onFloatingLyricsRequest)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(spec: DeepEchoThemeSpec, connected: Boolean, status: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("DEEP-ECHO", color = spec.accent, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(status, color = spec.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(99.dp))
                .background((if (connected) Color(0xFF37D979) else Color(0xFFFFB34C)).copy(alpha = .16f))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(if (connected) "LIVE" else "ALPHA", color = if (connected) Color(0xFF66F59B) else Color(0xFFFFC76E), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HomeScreen(tracks: List<Track>, spec: DeepEchoThemeSpec, onPlay: (Track) -> Unit, onDownload: (Track) -> Unit, onRefresh: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            HeroCard(spec, onRefresh)
            Spacer(Modifier.height(14.dp))
            Text("For You", color = spec.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(tracks) { track -> CompactTrackCard(track, spec, onPlay, onDownload) }
            }
        }
        item {
            Text("DeepEcho Mobile Core", color = spec.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Native playback · synced lyrics · floating overlay · themes · downloads", color = spec.muted, modifier = Modifier.padding(top = 5.dp, bottom = 24.dp))
        }
    }
}

@Composable
private fun HeroCard(spec: DeepEchoThemeSpec, onRefresh: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = spec.surface.copy(alpha = .82f)), shape = RoundedCornerShape(26.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Your music. Your atmosphere.", color = spec.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text("Android Alpha built from the DeepEcho 2.6.2 baseline.", color = spec.muted, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
            Button(onClick = onRefresh, colors = ButtonDefaults.buttonColors(containerColor = spec.accent, contentColor = spec.background)) { Text("Refresh Home") }
        }
    }
}

@Composable
private fun SearchScreen(results: List<Track>, searching: Boolean, spec: DeepEchoThemeSpec, onSearch: (String) -> Unit, onPlay: (Track) -> Unit, onDownload: (Track) -> Unit) {
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Search songs") }
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onSearch(query) }, colors = ButtonDefaults.buttonColors(containerColor = spec.accent, contentColor = spec.background)) {
                if (searching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Go")
            }
        }
        Spacer(Modifier.height(12.dp))
        if (results.isEmpty()) {
            Text("Search is live when the DeepEcho PC Bridge is running. Emulator default: 10.0.2.2:17832", color = spec.muted)
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results) { track -> TrackRow(track, spec, onPlay, onDownload) }
            }
        }
    }
}

@Composable
private fun LyricsScreen(track: Track?, activeLine: String, position: Long, lyricsFx: Boolean, spec: DeepEchoThemeSpec, onLyricsFx: (Boolean) -> Unit, onFloatingLyricsRequest: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(track?.title ?: "Lyrics", color = spec.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(track?.artist ?: "No song playing", color = spec.muted)
            }
            Text("FX", color = spec.muted)
            Spacer(Modifier.width(6.dp))
            Switch(checked = lyricsFx, onCheckedChange = onLyricsFx)
        }
        Spacer(Modifier.height(18.dp))
        Card(colors = CardDefaults.cardColors(containerColor = spec.surface.copy(alpha = .74f)), shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("NOW", color = spec.accent, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(12.dp))
                Text(activeLine, color = if (lyricsFx) spec.accent2 else spec.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(formatTime(position), color = spec.muted, modifier = Modifier.padding(top = 12.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Button(onClick = onFloatingLyricsRequest, colors = ButtonDefaults.buttonColors(containerColor = spec.accent, contentColor = spec.background)) { Text("Enable Floating Lyrics") }
        Spacer(Modifier.height(16.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(track?.lyrics.orEmpty()) { line ->
                val isCurrent = line.text == activeLine
                Text(
                    line.text,
                    color = if (isCurrent) spec.accent2 else spec.muted,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isCurrent) spec.accent.copy(alpha = .10f) else Color.Transparent)
                        .padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun DownloadsScreen(tracks: List<Track>, spec: DeepEchoThemeSpec, onPlay: (Track) -> Unit) {
    if (tracks.isEmpty()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No Alpha downloads queued yet", color = spec.text, fontWeight = FontWeight.Bold)
            Text("Tap ⇩ on a song to save it with Android DownloadManager.", color = spec.muted, modifier = Modifier.padding(top = 6.dp))
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Downloads", color = spec.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(tracks) { track -> TrackRow(track, spec, onPlay, onDownload = {}) }
        }
    }
}

@Composable
private fun SettingsScreen(
    spec: DeepEchoThemeSpec,
    bridgeUrl: String,
    smartAutoplay: Boolean,
    lyricsFx: Boolean,
    floatingLyricsEnabled: Boolean,
    onTheme: (String) -> Unit,
    onBridgeUrl: (String) -> Unit,
    onSmartAutoplay: (Boolean) -> Unit,
    onLyricsFx: (Boolean) -> Unit,
    onFloatingLyricsChanged: (Boolean) -> Unit,
    onFloatingLyricsRequest: () -> Unit
) {
    var urlText by remember(bridgeUrl) { mutableStateOf(bridgeUrl) }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Themes", color = spec.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeepEchoThemes.all.forEach { theme ->
                    Card(
                        modifier = Modifier.width(132.dp).clickable { onTheme(theme.key) },
                        colors = CardDefaults.cardColors(containerColor = theme.surface),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Box(Modifier.size(28.dp).clip(CircleShape).background(theme.accent))
                            Text(theme.label, color = theme.text, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
        item {
            SettingCard(spec, "Smart Autoplay", "Keep discovery playback flowing from Home/Search") {
                Switch(checked = smartAutoplay, onCheckedChange = onSmartAutoplay)
            }
        }
        item {
            SettingCard(spec, "Lyrics Theme FX", "Theme-specific lyric emphasis and ambient styling") {
                Switch(checked = lyricsFx, onCheckedChange = onLyricsFx)
            }
        }
        item {
            SettingCard(spec, "Floating Lyrics", "Draggable synced lyric line above games and other apps") {
                Switch(checked = floatingLyricsEnabled, onCheckedChange = { enabled ->
                    if (enabled) onFloatingLyricsRequest() else onFloatingLyricsChanged(false)
                })
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = spec.surface.copy(alpha = .82f)), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Catalog Bridge", color = spec.text, fontWeight = FontWeight.Bold)
                    Text("Emulator uses 10.0.2.2 to reach the Windows host.", color = spec.muted, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = urlText, onValueChange = { urlText = it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true)
                    Button(onClick = { onBridgeUrl(urlText) }, modifier = Modifier.padding(top = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = spec.accent, contentColor = spec.background)) { Text("Save & Reconnect") }
                }
            }
        }
        item {
            Button(onClick = onFloatingLyricsRequest, colors = ButtonDefaults.buttonColors(containerColor = spec.accent, contentColor = spec.background)) { Text("Enable Floating Lyrics Overlay") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingCard(spec: DeepEchoThemeSpec, title: String, subtitle: String, control: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = spec.surface.copy(alpha = .82f)), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = spec.text, fontWeight = FontWeight.Bold)
                Text(subtitle, color = spec.muted, style = MaterialTheme.typography.bodySmall)
            }
            control()
        }
    }
}

@Composable
private fun CompactTrackCard(track: Track, spec: DeepEchoThemeSpec, onPlay: (Track) -> Unit, onDownload: (Track) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = spec.surface.copy(alpha = .86f)), shape = RoundedCornerShape(22.dp), modifier = Modifier.width(220.dp)) {
        Column(Modifier.padding(14.dp)) {
            ArtworkPlaceholder(track, spec, 120)
            Text(track.title, color = spec.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
            Text(track.artist, color = spec.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 10.dp)) {
                Button(onClick = { onPlay(track) }, colors = ButtonDefaults.buttonColors(containerColor = spec.accent, contentColor = spec.background)) { Text("▶ Play") }
                TextButton(onClick = { onDownload(track) }) { Text("⇩", color = spec.accent2) }
            }
        }
    }
}

@Composable
private fun TrackRow(track: Track, spec: DeepEchoThemeSpec, onPlay: (Track) -> Unit, onDownload: (Track) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = spec.surface.copy(alpha = .80f)), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ArtworkPlaceholder(track, spec, 50)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(track.title, color = spec.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = spec.muted, maxLines = 1)
            }
            TextButton(onClick = { onDownload(track) }) { Text("⇩", color = spec.accent2) }
            Button(onClick = { onPlay(track) }, colors = ButtonDefaults.buttonColors(containerColor = spec.accent, contentColor = spec.background)) { Text("▶") }
        }
    }
}

@Composable
private fun ArtworkPlaceholder(track: Track, spec: DeepEchoThemeSpec, size: Int) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 5).dp))
            .background(spec.accent.copy(alpha = .18f)),
        contentAlignment = Alignment.Center
    ) {
        Text(track.title.take(1).uppercase(), color = spec.accent2, style = if (size > 80) MaterialTheme.typography.displayMedium else MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun MiniPlayer(track: Track, position: Long, duration: Long, playing: Boolean, spec: DeepEchoThemeSpec, onToggle: () -> Unit, onSeek: (Long) -> Unit, onOpenLyrics: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(spec.surface.copy(alpha = .97f)).padding(horizontal = 12.dp, vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(spec.accent.copy(alpha = .18f)), contentAlignment = Alignment.Center) { Text("♪", color = spec.accent) }
            Column(Modifier.weight(1f).padding(horizontal = 9.dp).clickable(onClick = onOpenLyrics)) {
                Text(track.title, color = spec.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = spec.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            TextButton(onClick = onOpenLyrics) { Text("Lyrics", color = spec.accent2) }
            Button(onClick = onToggle, colors = ButtonDefaults.buttonColors(containerColor = spec.accent, contentColor = spec.background)) { Text(if (playing) "Ⅱ" else "▶") }
        }
        val max = duration.coerceAtLeast(1L)
        Slider(value = position.coerceIn(0, max).toFloat(), onValueChange = { onSeek(it.roundToLong()) }, valueRange = 0f..max.toFloat())
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
