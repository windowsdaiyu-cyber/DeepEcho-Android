package com.deepecho.mobile.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.deepecho.mobile.data.LocalMusic
import com.deepecho.mobile.data.LocalTrack
import com.deepecho.mobile.data.PodcastEpisode
import com.deepecho.mobile.data.PodcastShow
import com.deepecho.mobile.data.Podcasts
import com.deepecho.mobile.data.Settings
import com.deepecho.mobile.data.Song
import com.deepecho.mobile.net.Bus
import com.deepecho.mobile.player.PlayerClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LocalMusicScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val tracks by LocalMusic.tracks.collectAsState()
    val allTracks by LocalMusic.allTracks.collectAsState()
    val excludedFolders by LocalMusic.excludedFolders.collectAsState()
    val scanning by LocalMusic.scanning.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (ok) LocalMusic.scan()
    }
    LaunchedEffect(granted) { if (granted && tracks.isEmpty()) LocalMusic.scan() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Icon(Icons.Filled.LibraryMusic, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Local Music", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { if (granted) LocalMusic.scan() else launcher.launch(permission) }) { Icon(Icons.Filled.Refresh, "Rescan") }
        }
        if (!granted) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
                    Text("DeepEcho ko device music scan karne ke liye audio-library permission chahiye.")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { launcher.launch(permission) }) { Text("Allow Local Music") }
                }
            }
            return
        }
        if (scanning && tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return
        }
        val tabs = listOf("Songs", "Albums", "Artists", "Folders")
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp, containerColor = androidx.compose.ui.graphics.Color.Transparent) {
            tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        when (tab) {
            0 -> SongList(tracks.map { it.song }, "No local music found. Tap Rescan.")
            1 -> LocalGroups(tracks.groupBy { it.album }, "No albums")
            2 -> LocalGroups(tracks.groupBy { it.song.artist }, "No artists")
            else -> LocalFolderGroups(allTracks = allTracks, excludedFolders = excludedFolders)
        }
    }
}

@Composable
private fun LocalGroups(groups: Map<String, List<LocalTrack>>, empty: String) {
    var open by remember { mutableStateOf<Pair<String, List<Song>>?>(null) }
    val selected = open
    if (selected != null) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { open = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(selected.first, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            SongList(selected.second, "No songs")
        }
        return
    }
    if (groups.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(empty, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        return
    }
    LazyColumn(contentPadding = PaddingValues(12.dp)) {
        items(groups.entries.sortedBy { it.key.lowercase() }) { entry ->
            Row(
                Modifier.fillMaxWidth().clickable { open = entry.key to entry.value.map { it.song } }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val art = entry.value.firstOrNull()?.song?.thumb
                Art(art, Modifier.size(58.dp), RoundedCornerShape(10.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.key.ifBlank { "Unknown" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${entry.value.size} songs", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun PodcastsScreen(onBack: (() -> Unit)? = null) {
    val scope = rememberCoroutineScope()
    val followed by Podcasts.followed.collectAsState()
    val progress by Podcasts.progressMs.collectAsState()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PodcastShow>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var openShow by remember { mutableStateOf<PodcastShow?>(null) }
    var episodes by remember { mutableStateOf<List<PodcastEpisode>>(emptyList()) }

    val show = openShow
    if (show != null) {
        LaunchedEffect(show.feedUrl) {
            loading = true
            episodes = withContext(Dispatchers.IO) { runCatching { Podcasts.episodes(show) }.getOrElse { emptyList() } }
            loading = false
        }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { openShow = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Art(show.artwork, Modifier.size(58.dp), RoundedCornerShape(12.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(show.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(show.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val isFollowed = followed.any { it.feedUrl == show.feedUrl }
                TextButton(onClick = { Podcasts.toggleFollow(show) }) { Text(if (isFollowed) "Following" else "Follow") }
            }
            if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else LazyColumn {
                itemsIndexed(episodes, key = { _, e -> e.id }) { index, ep ->
                    val song = ep.asSong()
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            val songs = episodes.map { it.asSong() }
                            PlayerClient.playSongs(songs, index)
                            val saved = progress[ep.audioUrl] ?: 0L
                            if (saved > 5_000L) PlayerClient.seekTo(saved)
                        }.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Art(ep.artwork, Modifier.size(56.dp), RoundedCornerShape(10.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ep.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (ep.description.isNotBlank()) {
                                Text(ep.description, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                buildString {
                                    if (ep.durationSec > 0) append(fmtTime(ep.durationSec * 1000))
                                    val p = progress[ep.audioUrl] ?: 0L
                                    if (p > 0) append(" • ${fmtTime(p)} listened")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        var actions by remember(ep.audioUrl) { mutableStateOf(false) }
                        IconButton(onClick = { actions = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        if (actions) {
                            val played = ep.durationSec > 0 && (progress[ep.audioUrl] ?: 0L) >= (ep.durationSec * 1000L - 5_000L).coerceAtLeast(0L)
                            SongActionsSheet(
                                song,
                                onDismiss = { actions = false },
                                extraLabel = if (played) "Mark unplayed" else "Mark played",
                                onExtra = { Podcasts.saveProgress(ep.audioUrl, if (played) 0L else ep.durationSec * 1000L) }
                            )
                        }
                    }
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Icon(Icons.Filled.Podcasts, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Podcasts", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            singleLine = true,
            placeholder = { Text("Search podcasts") },
            trailingIcon = { IconButton(onClick = {
                if (query.isBlank()) return@IconButton
                loading = true
                scope.launch {
                    results = withContext(Dispatchers.IO) { runCatching { Podcasts.search(query) }.getOrElse { Bus.toast("Podcast search fail: ${it.message}"); emptyList() } }
                    loading = false
                }
            }) { Icon(Icons.Filled.Search, "Search") } }
        )
        if (followed.isNotEmpty()) {
            Text("Followed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 14.dp, 16.dp, 4.dp))
            followed.take(8).forEach { showItem -> PodcastShowRow(showItem) { openShow = showItem } }
        }
        if (loading) Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else LazyColumn {
            if (results.isNotEmpty()) item { Text("Search results", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 14.dp, 16.dp, 4.dp)) }
            items(results, key = { it.feedUrl }) { showItem -> PodcastShowRow(showItem) { openShow = showItem } }
        }
    }
}

@Composable
private fun PodcastShowRow(show: PodcastShow, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Art(show.artwork, Modifier.size(58.dp), RoundedCornerShape(12.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(show.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(show.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun BlockedArtistsScreen(onBack: () -> Unit) {
    val blocked by Settings.blockedArtists.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Icon(Icons.Filled.Block, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Blocked Artists", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (blocked.isNotEmpty()) TextButton(onClick = Settings::clearBlockedArtists) { Text("Clear all") }
        }
        if (blocked.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No blocked artists", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else LazyColumn { items(blocked.toList().sorted()) { artist ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Block, null) }
                Text(artist, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                TextButton(onClick = { Settings.toggleBlockedArtist(artist) }) { Text("Unblock") }
            }
        } }
    }
}


@Composable
private fun LocalFolderGroups(allTracks: List<LocalTrack>, excludedFolders: Set<String>) {
    var open by remember { mutableStateOf<Pair<String, List<Song>>?>(null) }
    val selected = open
    if (selected != null) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { open = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(selected.first, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            SongList(selected.second, "No songs")
        }
        return
    }
    val groups = allTracks.groupBy { it.folder }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
    LazyColumn(contentPadding = PaddingValues(12.dp)) {
        if (excludedFolders.isNotEmpty()) {
            item {
                Text("Excluded folders", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp))
                Text("Tap Include to add a folder back to Local Music.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
            }
            items(excludedFolders.toList().sorted()) { folder ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Folder, null)
                    Spacer(Modifier.width(10.dp))
                    Text(folder, modifier = Modifier.weight(1f))
                    TextButton(onClick = { LocalMusic.toggleExcludedFolder(folder) }) { Text("Include") }
                }
            }
        }
        item { Text("Device folders", fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp)) }
        items(groups.entries.toList(), key = { it.key }) { entry ->
            val excluded = entry.key in excludedFolders
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).clickable(enabled = !excluded) { open = entry.key to entry.value.map { it.song } }) {
                    Text(entry.key, fontWeight = FontWeight.SemiBold, color = if (excluded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    Text("${entry.value.size} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { LocalMusic.toggleExcludedFolder(entry.key) }) { Text(if (excluded) "Include" else "Exclude") }
            }
        }
    }
}
