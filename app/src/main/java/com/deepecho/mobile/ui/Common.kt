package com.deepecho.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.deepecho.mobile.data.Song
import com.deepecho.mobile.data.Store
import com.deepecho.mobile.net.Downloads
import com.deepecho.mobile.player.PlayerClient

fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun Art(url: String?, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(10.dp)) {
    Box(
        modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (url != null) {
            AsyncImage(
                model = url, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    isCurrent: Boolean = false,
    extraLabel: String? = null,
    onExtra: (() -> Unit)? = null
) {
    val liked by Store.liked.collectAsState()
    val downloads by Store.downloads.collectAsState()
    val progress by Downloads.progress.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var addDialog by remember { mutableStateOf(false) }
    val isLiked = liked.any { it.url == song.url }
    val downloaded = downloads.any { it.song.url == song.url }

    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Art(song.thumb, Modifier.size(52.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                "${song.artist} • ${fmtTime(song.durationSec * 1000)}",
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val p = progress[song.url]
        if (p != null) {
            CircularProgressIndicator(progress = { p }, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else if (downloaded) {
            Icon(Icons.Filled.DownloadDone, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Play next") }, onClick = { menu = false; PlayerClient.playNext(song) })
                DropdownMenuItem(text = { Text("Add to queue") }, onClick = { menu = false; PlayerClient.addToQueue(song) })
                DropdownMenuItem(
                    text = { Text(if (isLiked) "Unlike" else "Like") },
                    onClick = { menu = false; Store.toggleLike(song) }
                )
                DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { menu = false; addDialog = true })
                if (downloaded) {
                    DropdownMenuItem(text = { Text("Remove download") }, onClick = { menu = false; Store.removeDownload(song.url) })
                } else {
                    DropdownMenuItem(text = { Text("Download") }, onClick = { menu = false; Downloads.start(song) })
                }
                if (extraLabel != null && onExtra != null) {
                    DropdownMenuItem(text = { Text(extraLabel) }, onClick = { menu = false; onExtra() })
                }
            }
        }
    }
    if (addDialog) AddToPlaylistDialog(song) { addDialog = false }
}

@Composable
fun AddToPlaylistDialog(song: Song, onDismiss: () -> Unit) {
    val lists by Store.playlists.collectAsState()
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        NameDialog("New playlist", onDismiss = { creating = false }) { name ->
            val id = Store.createPlaylist(name)
            Store.addToPlaylist(id, song)
            onDismiss()
        }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column {
                if (lists.isEmpty()) Text("Abhi koi playlist nahi hai.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                lists.forEach { pl ->
                    Text(
                        pl.name,
                        Modifier.fillMaxWidth().clickable { Store.addToPlaylist(pl.id, song); onDismiss() }.padding(vertical = 12.dp)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { creating = true }) { Text("New playlist") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun NameDialog(title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, placeholder = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun SongList(
    songs: List<Song>,
    empty: String,
    modifier: Modifier = Modifier,
    extraLabel: String? = null,
    onExtra: ((Song) -> Unit)? = null,
    discoveryMode: Boolean = false
) {
    if (songs.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                empty, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(32.dp)
            )
        }
        return
    }
    val current = PlayerClient.currentSong?.url
    LazyColumn(modifier) {
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { PlayerClient.playSongs(songs, 0) }) { Text("Play all") }
                OutlinedButton(onClick = { PlayerClient.playSongs(songs.shuffled(), 0) }) { Text("Shuffle") }
            }
        }
        itemsIndexed(songs, key = { i, s -> "${s.url}#$i" }) { i, s ->
            SongRow(
                song = s,
                isCurrent = current == s.url,
                onClick = { if (discoveryMode) PlayerClient.playDiscovery(s) else PlayerClient.playSongs(songs, i) },
                extraLabel = extraLabel,
                onExtra = onExtra?.let { f -> { f(s) } }
            )
        }
    }
}
