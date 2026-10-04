package com.deepecho.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deepecho.mobile.data.Song
import com.deepecho.mobile.data.TasteEngine
import com.deepecho.mobile.data.TopArtist
import com.deepecho.mobile.net.RemotePlaylist
import com.deepecho.mobile.net.YouTubeApi
import com.deepecho.mobile.player.PlayerClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ArtistScreen(
    artist: TopArtist,
    onBack: () -> Unit,
    onOpenArtist: (TopArtist) -> Unit
) {
    BackHandler(onBack = onBack)

    var openPlaylist by remember(artist.name) { mutableStateOf<RemotePlaylist?>(null) }
    openPlaylist?.let { playlist ->
        RemotePlaylistScreen(playlist = playlist, onBack = { openPlaylist = null })
        return
    }

    val songs by produceState<List<Song>?>(null, artist.name) {
        value = withContext(Dispatchers.IO) {
            runCatching { YouTubeApi.search("${artist.name} best songs") }
                .getOrDefault(emptyList())
                .distinctBy { it.url }
                .take(24)
        }
    }

    val playlists by produceState(initialValue = emptyList<RemotePlaylist>(), artist.name) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                YouTubeApi.searchPlaylists(
                    "${artist.name} albums official playlists hits",
                    10
                )
            }.getOrDefault(emptyList())
        }
    }

    val related by produceState(initialValue = emptyList<TopArtist>(), artist.name) {
        value = withContext(Dispatchers.IO) {
            val discovered = runCatching {
                YouTubeApi.search("${artist.name} similar artists songs")
            }.getOrDefault(emptyList())
                .filter { it.artist.isNotBlank() && !it.artist.equals(artist.name, true) }
                .distinctBy { it.artist.lowercase() }
                .map { TopArtist(it.artist, it.thumb, 0) }
                .take(10)

            if (discovered.isNotEmpty()) discovered
            else TasteEngine.topArtistsExpanded(10)
                .filterNot { it.name.equals(artist.name, true) }
                .take(10)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }

                Art(artist.thumb, Modifier.size(88.dp), CircleShape)
                Spacer(Modifier.width(14.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        artist.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        if (artist.plays > 0) "${artist.plays} local taste signals"
                        else "Artist discovery",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Top songs • playlists • similar artists",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        item {
            val loaded = songs.orEmpty()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        if (loaded.isNotEmpty()) PlayerClient.playSongs(loaded, 0)
                    },
                    enabled = loaded.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Play top songs")
                }

                OutlinedButton(
                    onClick = { PlayerClient.addManyToQueue(loaded.take(12)) },
                    enabled = loaded.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.QueueMusic, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add to queue")
                }
            }
        }

        item {
            Text(
                "About Artist",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            Text(
                "Explore ${artist.name}'s top tracks, music playlists and artists with a similar sound. " +
                    "DeepEcho builds this page from the current music catalog and your listening context.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        item {
            Text(
                "Top songs",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }

        when (val loaded = songs) {
            null -> item {
                Box(
                    Modifier.fillMaxWidth().padding(36.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }

            else -> {
                if (loaded.isEmpty()) {
                    item {
                        Text(
                            "Artist ke songs load nahi ho paaye.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                } else {
                    itemsIndexed(loaded.take(12), key = { _, song -> song.url }) { _, song ->
                        SongRow(
                            song = song,
                            onClick = { PlayerClient.playDiscovery(song) }
                        )
                    }
                }
            }
        }

        if (playlists.isNotEmpty()) {
            item {
                Text(
                    "Best playlists & albums",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(playlists, key = { it.url }) { playlist ->
                        Column(
                            Modifier.width(146.dp).clickable { openPlaylist = playlist }
                        ) {
                            Art(
                                playlist.thumb,
                                Modifier.size(146.dp),
                                RoundedCornerShape(14.dp)
                            )
                            Text(
                                playlist.title,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            Text(
                                if (playlist.songCount > 0) "${playlist.songCount} tracks"
                                else playlist.uploader,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = { openPlaylist = playlist },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            ) {
                                Text("Open")
                            }
                        }
                    }
                }
            }
        }

        if (related.isNotEmpty()) {
            item {
                Text(
                    "Similar artists",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(related, key = { it.name }) { item ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(96.dp).clickable { onOpenArtist(item) }
                        ) {
                            Art(item.thumb, Modifier.size(76.dp), CircleShape)
                            Text(
                                item.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
