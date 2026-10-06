package com.deepecho.mobile.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deepecho.mobile.data.ExperienceV1128
import com.deepecho.mobile.data.PersonalMixSpec
import com.deepecho.mobile.data.Settings
import com.deepecho.mobile.data.Song
import com.deepecho.mobile.data.Store
import com.deepecho.mobile.net.Bus
import com.deepecho.mobile.net.YouTubeApi
import com.deepecho.mobile.player.PlayerClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private suspend fun loadPersonalMix(spec: PersonalMixSpec): List<Song> = withContext(Dispatchers.IO) {
    runCatching { YouTubeApi.search(spec.query) }
        .getOrDefault(emptyList())
        .filter { it.durationSec in 45L..900L && !Settings.isArtistBlocked(it.artist) }
        .distinctBy { it.url }
        .take(35)
}

@Composable
fun DailyMixesV1128(onOpen: (PersonalMixSpec) -> Unit) {
    val history by Store.history.collectAsState()
    val liked by Store.liked.collectAsState()
    val downloads by Store.downloads.collectAsState()
    val mood by ExperienceV1128.selectedMood.collectAsState()
    val mixes = remember(history, liked, downloads, mood) { ExperienceV1128.dailyMixes() }
    if (mixes.isEmpty()) return
    val scope = rememberCoroutineScope()

    Column(Modifier.padding(top = 20.dp)) {
        Text(
            "Your Daily Mixes",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        Text(
            "Built from your local taste, recent listening and current mood context",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(mixes, key = { it.title + it.query }) { mix ->
                Card(
                    modifier = Modifier.width(238.dp).height(174.dp).clickable { onOpen(mix) },
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .78f)
                    )
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Art(mix.seedThumb, Modifier.size(58.dp), RoundedCornerShape(16.dp))
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(mix.title, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(mix.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val songs = loadPersonalMix(mix)
                                    if (songs.isEmpty()) Bus.toast("Mix abhi load nahi ho paaya")
                                    else PlayerClient.playSongs(songs, 0)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.PlayArrow, null)
                            Spacer(Modifier.width(5.dp))
                            Text("Play")
                        }
                        OutlinedButton(onClick = { onOpen(mix) }, modifier = Modifier.weight(1f)) { Text("Open") }
                    }
                }
            }
        }
    }
}

@Composable
fun ListeningSessionCardV1128() {
    val history by Store.history.collectAsState()
    val stats by Store.listeningStats.collectAsState()
    val daily by Store.dailyListeningMs.collectAsState()
    val insight = remember(history, stats, daily) { ExperienceV1128.listeningInsight() } ?: return

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .58f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = .15f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(insight.headline, fontWeight = FontWeight.Bold)
                Text(insight.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ContinueVibeCardV1128(onOpen: (PersonalMixSpec) -> Unit) {
    val history by Store.history.collectAsState()
    val mood by ExperienceV1128.selectedMood.collectAsState()
    val smartAutoplay by Settings.smartAutoplay.collectAsState()
    val spec = remember(history, mood, smartAutoplay) { ExperienceV1128.continueVibe() } ?: return
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).clickable { onOpen(spec) },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .60f))
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Art(spec.seedThumb, Modifier.size(66.dp), RoundedCornerShape(18.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(spec.title, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                Text(spec.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            IconButton(onClick = {
                scope.launch {
                    val songs = loadPersonalMix(spec)
                    if (songs.isEmpty()) Bus.toast("Vibe abhi load nahi ho paaya")
                    else {
                        // The user explicitly tapped Continue Your Vibe, so starting a new intended
                        // discovery context is allowed. Existing manual queue is untouched until now.
                        Settings.setSmartAutoplay(true)
                        PlayerClient.playDiscovery(songs.first())
                    }
                }
            }) { Icon(Icons.Filled.PlayArrow, "Continue") }
        }
    }
}

@Composable
fun PersonalMixDetailV1128(spec: PersonalMixSpec, onBack: () -> Unit) {
    var retry by remember { mutableIntStateOf(0) }
    val songs by produceState<List<Song>?>(initialValue = null, spec.query, retry) {
        value = loadPersonalMix(spec)
    }
    val list = songs

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text(spec.title, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                Text(spec.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            IconButton(onClick = { retry++ }) { Icon(Icons.Filled.Refresh, "Refresh mix") }
        }
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list!!.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Mix abhi available nahi hai") }
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
                item {
                    Button(
                        onClick = { PlayerClient.playSongs(list!!, 0) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    ) { Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Play Mix") }
                }
                items(list!!, key = { it.url }) { song ->
                    SongRow(song, onClick = { PlayerClient.playSongs(list!!, list!!.indexOf(song)) })
                }
            }
        }
    }
}
