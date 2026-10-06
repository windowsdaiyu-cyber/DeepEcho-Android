package com.deepecho.mobile.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.media.MediaPlayer
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.deepecho.mobile.data.RingtoneMaker
import com.deepecho.mobile.data.ExperienceV1128
import com.deepecho.mobile.data.Settings
import com.deepecho.mobile.data.Song
import com.deepecho.mobile.data.Store
import com.deepecho.mobile.net.Bus
import com.deepecho.mobile.net.Downloads
import com.deepecho.mobile.net.Lrclib
import com.deepecho.mobile.net.Net
import com.deepecho.mobile.player.AudioFx
import com.deepecho.mobile.player.PlayerClient
import java.io.File
import java.net.URLEncoder
import okhttp3.Request
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SongActionsSheet(song: Song, onDismiss: () -> Unit, extraLabel: String? = null, onExtra: (() -> Unit)? = null) {
    val context = LocalContext.current
    val liked by Store.liked.collectAsState()
    val downloads by Store.downloads.collectAsState()
    val blocked by Settings.blockedArtists.collectAsState()
    val actionScope = rememberCoroutineScope()
    var ringtone by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var equalizer by remember { mutableStateOf(false) }
    var lyricsActions by remember { mutableStateOf(false) }
    var addPlaylist by remember { mutableStateOf(false) }
    var shareCard by remember { mutableStateOf(false) }
    val isLiked = liked.any { it.url == song.url }
    val downloaded = downloads.any { it.song.url == song.url }
    val localFile = song.url.startsWith("content://") || song.url.startsWith("file://")
    val isBlocked = blocked.any { it.equals(song.artist, true) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .96f)) {
        // The song action list is intentionally scrollable. On phones/emulators with
        // shorter viewports the full action set is taller than the bottom sheet; without
        // an explicit scroll container, lower actions (Ringtone/Details/Advanced/etc.)
        // become unreachable.
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Text(song.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 20.dp))
            Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            ActionRow(Icons.Filled.Radio, "Start Radio") { Settings.setSmartAutoplay(true); PlayerClient.playDiscovery(song); onDismiss() }
            ActionRow(Icons.Filled.PlayArrow, "Play next") { PlayerClient.playNext(song); onDismiss() }
            ActionRow(Icons.Filled.QueueMusic, "Add to queue") { PlayerClient.addToQueue(song); onDismiss() }
            ActionRow(Icons.Filled.LibraryAdd, "Add to playlist") { addPlaylist = true }
            ActionRow(Icons.Filled.AutoAwesome, "More Like This") {
                ExperienceV1128.markMoreLikeThis(song)
                Bus.toast("More songs like this will be preferred")
                onDismiss()
            }
            ActionRow(Icons.Filled.Block, "Less Like This") {
                ExperienceV1128.markLessLikeThis(song)
                Bus.toast("DeepEcho will lower similar recommendations")
                onDismiss()
            }
            ActionRow(Icons.Filled.Share, "Share") { shareCard = true }
            ActionRow(Icons.Filled.Person, "View artist") { UiEvents.search(song.artist); onDismiss() }
            ActionRow(Icons.Filled.Album, "View album / related releases") { UiEvents.search("${song.artist} ${song.title} album"); onDismiss() }
            ActionRow(if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (isLiked) "Remove from library" else "Add to library") { Store.toggleLike(song) }
            ActionRow(Icons.Filled.AutoAwesome, if (Settings.ambientMode.value) "Ambient Mode ON" else "Ambient Mode") { if (PlayerClient.currentSong?.url != song.url) PlayerClient.playDiscovery(song); Settings.setAmbientMode(true); UiEvents.openPlayer(); onDismiss() }
            ActionRow(Icons.Filled.Lyrics, "Show Lyrics") { if (PlayerClient.currentSong?.url != song.url) PlayerClient.playDiscovery(song); Settings.setAmbientMode(false); UiEvents.openPlayer(lyrics = true); onDismiss() }
            ActionRow(Icons.Filled.DeleteSweep, "Clear queue") { PlayerClient.clearUpcomingQueue(); onDismiss() }
            if (!localFile) {
                ActionRow(Icons.Filled.Download, if (downloaded) "Remove download" else "Download") { if (downloaded) Store.removeDownload(song.url) else Downloads.start(song) }
            }
            ActionRow(Icons.Filled.Notifications, "Set as Ringtone") { ringtone = true }
            ActionRow(Icons.Filled.Lyrics, "Lyrics • Save / Share") { lyricsActions = true }
            ActionRow(Icons.Filled.Info, "Details") { details = true }
            ActionRow(Icons.Filled.Equalizer, "Equalizer") { equalizer = true }
            ActionRow(Icons.Filled.Tune, "Advanced") { advanced = true }
            ActionRow(Icons.Filled.Block, if (isBlocked) "Unblock artist" else "Block artist") { Settings.toggleBlockedArtist(song.artist) }
            if (extraLabel != null && onExtra != null) {
                ActionRow(Icons.Filled.DeleteSweep, extraLabel) { onExtra(); onDismiss() }
            }
        }
    }
    if (ringtone) RingtoneDialog(song) { ringtone = false }
    if (details) SongDetailsDialog(song) { details = false }
    if (advanced) AdvancedAudioDialog { advanced = false }
    if (equalizer) QuickEqualizerDialog { equalizer = false }
    if (lyricsActions) LyricsExportDialog(song) { lyricsActions = false }
    if (addPlaylist) AddToPlaylistDialog(song) { addPlaylist = false }
    if (shareCard) ShareCardDialogV1128(song) { shareCard = false; onDismiss() }
}

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, action: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).clickable(onClick = action),
        supportingContent = null
    )
}

private suspend fun shareSong(context: android.content.Context, song: Song) {
    val universal = withContext(Dispatchers.IO) {
        if (!song.url.startsWith("http")) null else runCatching {
            val encoded = URLEncoder.encode(song.url, "UTF-8")
            val req = Request.Builder()
                .url("https://api.song.link/v1-alpha.1/links?url=$encoded&userCountry=IN")
                .header("User-Agent", Net.UA)
                .build()
            Net.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null
                else JSONObject(resp.body?.string().orEmpty()).optString("pageUrl").ifBlank { null }
            }
        }.getOrNull()
    }
    val text = buildString {
        append(song.title).append(" — ").append(song.artist)
        val link = universal ?: song.url.takeIf { it.startsWith("http") }
        if (!link.isNullOrBlank()) append("\n").append(link)
        append("\nShared from DeepEcho")
    }
    withContext(Dispatchers.Main) {
        context.startActivity(
            Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }, "Share song")
        )
    }
}

@Composable
private fun RingtoneDialog(song: Song, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val downloads by Store.downloads.collectAsState()
    val scope = rememberCoroutineScope()
    val available = RingtoneMaker.sourceUri(context, song) != null
    val duration = (song.durationSec.coerceAtLeast(1).coerceAtMost(60 * 60)).toFloat()
    var range by remember(song.url) { mutableStateOf(0f..minOf(30f, duration)) }
    var working by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var legacyWriteGranted by remember {
        mutableStateOf(Build.VERSION.SDK_INT >= 29 || ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
    }
    val legacyWriteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { legacyWriteGranted = it }

    DisposableEffect(Unit) { onDispose { runCatching { player?.release() } } }
    LaunchedEffect(downloads, song.url) { /* recomposition hook */ }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text("Trim Ringtone") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Select up to 30 seconds of '${song.title}' to use as ringtone.")
                if (!available) {
                    Text("Streaming song ko ringtone banane ke liye DeepEcho pehle local audio copy prepare karega.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { Downloads.start(song) }) { Text("Download for ringtone") }
                } else {
                    RangeSlider(
                        value = range,
                        onValueChange = { r ->
                            var a = r.start.coerceIn(0f, duration)
                            var b = r.endInclusive.coerceIn(a, duration)
                            if (b - a > 30f) b = a + 30f
                            range = a..b.coerceAtMost(duration)
                        },
                        valueRange = 0f..duration
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(fmtTime((range.start * 1000).toLong()))
                        Text(fmtTime((range.endInclusive * 1000).toLong()))
                    }
                    Text("Selected duration: ${fmtTime(((range.endInclusive - range.start) * 1000).toLong())}")
                    Button(onClick = {
                        runCatching { player?.release() }
                        val src = RingtoneMaker.sourceUri(context, song) ?: return@Button
                        val mp = MediaPlayer()
                        runCatching {
                            mp.setDataSource(context, src)
                            mp.prepare()
                            mp.seekTo((range.start * 1000).toInt())
                            mp.start()
                            player = mp
                            scope.launch {
                                delay(((range.endInclusive - range.start) * 1000).toLong().coerceAtLeast(500))
                                runCatching { mp.pause() }
                            }
                        }.onFailure { mp.release(); Bus.toast("Preview fail: ${it.message}") }
                    }) { Icon(Icons.Filled.PlayArrow, null); Text(" Preview") }
                }
            }
        },
        confirmButton = {
            if (available) TextButton(enabled = !working, onClick = {
                if (Build.VERSION.SDK_INT < 29 && !legacyWriteGranted) {
                    legacyWriteLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    Bus.toast("Storage permission allow karke Set as Ringtone dobara tap karo")
                    return@TextButton
                }
                if (!AndroidSettings.System.canWrite(context)) {
                    context.startActivity(Intent(AndroidSettings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
                    Bus.toast("Allow 'Modify system settings', then tap Set as Ringtone again")
                    return@TextButton
                }
                working = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { RingtoneMaker.makeAndSet(context, song, (range.start * 1000).toLong(), (range.endInclusive * 1000).toLong()) }
                    }
                    working = false
                    result.onSuccess { Bus.toast("Ringtone set: ${song.title}"); onDismiss() }
                        .onFailure { Bus.toast("Ringtone fail: ${it.message ?: "unsupported audio format"}") }
                }
            }) { Text(if (working) "Creating…" else "Set as Ringtone") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SongDetailsDialog(song: Song, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val path = Store.downloadPath(song.url)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Details") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(song.title, fontWeight = FontWeight.Bold)
            Text("Artist: ${song.artist}")
            Text("Duration: ${fmtTime(song.durationSec * 1000)}")
            Text("Source: ${when { song.url.startsWith("content://") -> "Local Music"; path != null -> "Downloaded"; else -> "Online" }}")
            if (path != null) Text("File: $path", maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (song.url.startsWith("http")) Text(song.url, maxLines = 2, overflow = TextOverflow.Ellipsis)
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun AdvancedAudioDialog(onDismiss: () -> Unit) {
    var speed by remember { mutableFloatStateOf(1f) }
    var pitch by remember { mutableFloatStateOf(1f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Advanced") },
        text = { Column {
            Text("Tempo ${"%.2f".format(speed)}×")
            Slider(value = speed, onValueChange = { speed = it; PlayerClient.setPlaybackParameters(speed, pitch) }, valueRange = .5f..2f)
            Text("Pitch ${"%.2f".format(pitch)}×")
            Slider(value = pitch, onValueChange = { pitch = it; PlayerClient.setPlaybackParameters(speed, pitch) }, valueRange = .5f..2f)
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = { PlayerClient.resetPlaybackParameters(); onDismiss() }) { Text("Reset") } }
    )
}

@Composable
private fun QuickEqualizerDialog(onDismiss: () -> Unit) {
    val preset by AudioFx.preset.collectAsState()
    val bass by AudioFx.bass.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Equalizer") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Preset: $preset")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Flat", "Bass", "Vocal", "Rock", "Pop").forEach { name ->
                    FilterChip(selected = preset == name, onClick = { AudioFx.applyPreset(name) }, label = { Text(name) })
                }
            }
            Text("Bass ${bass / 10}%")
            Slider(value = bass.toFloat(), onValueChange = { AudioFx.setBass(it.toInt()) }, valueRange = 0f..1000f)
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun LyricsExportDialog(song: Song, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pair<String, String>?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val data = pending
        pending = null
        if (uri != null && data != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri, "w")?.bufferedWriter()?.use { it.write(data.second) }
                    ?: error("File open nahi hui")
            }.onSuccess { Bus.toast("Lyrics saved") }.onFailure { Bus.toast("Lyrics save fail: ${it.message}") }
        }
        onDismiss()
    }

    suspend fun prepare(): Pair<String, String>? = withContext(Dispatchers.IO) {
        runCatching {
            val lyrics = Lrclib.fetch(song) ?: error("Lyrics nahi mili")
            val hasSync = lyrics.synced.isNotEmpty()
            val ext = if (hasSync) "lrc" else "txt"
            val body = if (hasSync) lyrics.synced.joinToString("\n") { line ->
                val total = line.timeMs.coerceAtLeast(0)
                val min = total / 60000
                val sec = (total % 60000) / 1000
                val cs = (total % 1000) / 10
                "[%02d:%02d.%02d]%s".format(min, sec, cs, line.text)
            } else (lyrics.plain ?: lyrics.synced.joinToString("\n") { it.text })
            val safe = song.title.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(50).ifBlank { "lyrics" }
            val fileName = "$safe.$ext"
            val content = buildString {
                append("# ").append(song.title).append('\n')
                append("# Artist: ").append(song.artist).append('\n')
                append("# Source: ").append(lyrics.source).append('\n')
                append("# Exported by DeepEcho\n\n")
                append(body)
            }
            fileName to content
        }.getOrElse { Bus.toast("Lyrics export fail: ${it.message}"); null }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lyrics") },
        text = { Text("Synced timing available ho toh DeepEcho .lrc export karega; otherwise plain .txt. Timing fabricate nahi hoti.") },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val data = prepare()
                    busy = false
                    if (data != null) {
                        val file = File(context.cacheDir, data.first).apply { writeText(data.second) }
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, "Share lyrics"))
                        onDismiss()
                    }
                }
            }) { Text(if (busy) "Working…" else "Share Lyrics") }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val data = prepare()
                    busy = false
                    if (data != null) {
                        pending = data
                        saveLauncher.launch(data.first)
                    }
                }
            }) { Text("Save Lyrics") }
        }
    )
}

