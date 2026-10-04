package com.deepecho.mobile.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deepecho.mobile.data.HomeMix
import com.deepecho.mobile.data.QuoteDeck
import com.deepecho.mobile.data.Settings
import com.deepecho.mobile.data.Song
import com.deepecho.mobile.data.Store
import com.deepecho.mobile.data.TasteEngine
import com.deepecho.mobile.data.TopArtist
import com.deepecho.mobile.net.RemotePlaylist
import com.deepecho.mobile.net.YouTubeApi
import com.deepecho.mobile.player.PlayerClient
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ───────────────────────── HOME ─────────────────────────

@Composable
fun HomeScreen(onOpenSearch: () -> Unit) {
    val history by Store.history.collectAsState()
    val liked by Store.liked.collectAsState()
    val smartAutoplay by Settings.smartAutoplay.collectAsState()
    val mixes = remember(history, liked) { TasteEngine.homeMixes(history, liked) }
    val artists by produceState<List<TopArtist>>(
        initialValue = TasteEngine.topArtists(10),
        history,
        liked
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching { TasteEngine.topArtistsExpanded(10) }
                .getOrDefault(TasteEngine.topArtists(10))
        }
    }
    var openPlaylist by remember { mutableStateOf<RemotePlaylist?>(null) }
    var openArtist by remember { mutableStateOf<TopArtist?>(null) }

    openPlaylist?.let { playlist ->
        RemotePlaylistScreen(playlist = playlist, onBack = { openPlaylist = null })
        return
    }
    openArtist?.let { artist ->
        ArtistScreen(
            artist = artist,
            onBack = { openArtist = null },
            onOpenArtist = { openArtist = it }
        )
        return
    }

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when {
        hour < 5 -> "Good night"
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 18.dp)) {
        item {
            Column(Modifier.padding(16.dp, 20.dp, 16.dp, 8.dp)) {
                Text(greeting, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    QuoteDeck.current(),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 3.dp)
                )
                Text(
                    "Tumhare taste ke hisaab se aaj ka DeepEcho",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp)
                )
                Spacer(Modifier.size(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onOpenSearch) {
                        Icon(Icons.Filled.Search, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Search songs")
                    }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Smart Autoplay", style = MaterialTheme.typography.labelMedium)
                        Switch(checked = smartAutoplay, onCheckedChange = { Settings.setSmartAutoplay(it) })
                    }
                }
            }
        }

        if (history.isNotEmpty()) item { Shelf("Recently played", history.take(15), prewarmCount = 1) }
        if (liked.isNotEmpty()) item { Shelf("Your likes", liked.take(15), prewarmCount = 1) }
        mixes.forEach { mix ->
            item(key = mix.query) { TasteShelf(mix) }
        }
        item {
            HomePlaylistShelf(
                query = remember(history, liked) { TasteEngine.playlistQuery() },
                onOpen = { openPlaylist = it }
            )
        }

        // User requested Top Artists as the LAST Home section.
        item {
            TopArtistsShelf(
                artists = artists,
                onArtist = { artist -> openArtist = artist }
            )
        }
    }
}

@Composable
private fun TasteShelf(mix: HomeMix) {
    val songs by produceState<List<Song>>(emptyList(), mix.query) {
        value = withContext(Dispatchers.IO) {
            runCatching { YouTubeApi.search(mix.query) }.getOrDefault(emptyList())
        }
    }
    Shelf(mix.title, songs, prewarmCount = 1)
}

@Composable
private fun Shelf(title: String, songs: List<Song>, prewarmCount: Int = 1) {
    LaunchedEffect(songs.firstOrNull()?.url) {
        if (songs.isNotEmpty()) PlayerClient.prewarm(songs, prewarmCount)
    }

    Column(Modifier.padding(top = 16.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.size(8.dp))
        if (songs.isEmpty()) {
            Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(songs, key = { i, s -> "${s.url}#$i" }) { _, song ->
                    Column(Modifier.width(140.dp).clickable { PlayerClient.playDiscovery(song) }) {
                        Art(song.thumb, Modifier.size(140.dp))
                        Text(
                            song.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        Text(
                            song.artist,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomePlaylistShelf(query: String, onOpen: (RemotePlaylist) -> Unit) {
    val lists by produceState<List<RemotePlaylist>>(emptyList(), query) {
        value = withContext(Dispatchers.IO) {
            runCatching { YouTubeApi.searchPlaylists(query, 8) }.getOrDefault(emptyList())
        }
    }
    if (lists.isNotEmpty()) {
        PlaylistShelf("Playlists for you", lists, onOpen)
    }
}

@Composable
private fun TopArtistsShelf(artists: List<TopArtist>, onArtist: (TopArtist) -> Unit) {
    Column(Modifier.padding(top = 20.dp, bottom = 12.dp)) {
        Text(
            "Top Artists",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.size(10.dp))
        if (artists.isEmpty()) {
            Text(
                "Thoda aur suno — tumhare Top Artists yahan banenge.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(artists, key = { _, a -> a.name }) { _, artist ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(118.dp).clickable { onArtist(artist) }
                    ) {
                        Art(artist.thumb, Modifier.size(96.dp), CircleShape)
                        Text(
                            artist.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 7.dp)
                        )
                    }
                }
            }
        }
    }
}

// ───────────────────────── SEARCH ─────────────────────────

object SearchVm {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var generation = 0

    var query by mutableStateOf("")
    var results by mutableStateOf<List<Song>>(emptyList())
    var playlists by mutableStateOf<List<RemotePlaylist>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var hasSearched by mutableStateOf(false)
    var submittedQuery by mutableStateOf("")

    fun run(q: String) {
        val text = q.trim()
        if (text.isEmpty()) return
        val request = ++generation
        query = text
        submittedQuery = text
        loading = true
        hasSearched = true
        error = null
        playlists = emptyList()

        scope.launch {
            try {
                val songs = withContext(Dispatchers.IO) { YouTubeApi.search(text) }
                if (request != generation) return@launch
                results = songs
                if (songs.isEmpty()) error = "Kuch nahi mila"
                loading = false
                PlayerClient.prewarm(songs, 1)
            } catch (e: Exception) {
                if (request != generation) return@launch
                error = "Search fail: ${e.message ?: "network error"}"
                loading = false
            }
        }

        scope.launch {
            val found = withContext(Dispatchers.IO) {
                runCatching { YouTubeApi.searchPlaylists(text, 8) }.getOrDefault(emptyList())
            }
            if (request == generation) playlists = found
        }
    }
}

@Composable
fun SearchScreen() {
    val focus: FocusManager = LocalFocusManager.current
    val history by Store.history.collectAsState()
    val liked by Store.liked.collectAsState()
    var openPlaylist by remember { mutableStateOf<RemotePlaylist?>(null) }

    openPlaylist?.let { playlist ->
        RemotePlaylistScreen(playlist = playlist, onBack = { openPlaylist = null })
        return
    }

    val recommendations by produceState<List<Song>>(emptyList(), history, liked) {
        value = withContext(Dispatchers.IO) {
            runCatching { TasteEngine.searchRecommendations(10) }.getOrDefault(emptyList())
        }
    }
    val suggestions = remember(SearchVm.query, SearchVm.results, history, liked) {
        TasteEngine.searchSuggestions(SearchVm.query, SearchVm.results, 6)
    }

    LaunchedEffect(recommendations.firstOrNull()?.url) {
        PlayerClient.prewarm(recommendations, 1)
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = SearchVm.query,
            onValueChange = { SearchVm.query = it },
            singleLine = true,
            placeholder = { Text("Song, artist ya album") },
            trailingIcon = {
                IconButton(onClick = {
                    focus.clearFocus()
                    SearchVm.run(SearchVm.query)
                }) {
                    Icon(Icons.Filled.Search, "Search")
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                focus.clearFocus()
                SearchVm.run(SearchVm.query)
            }),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
        )

        if (
            SearchVm.query.trim().length >= 2 &&
            !SearchVm.query.trim().equals(SearchVm.submittedQuery, ignoreCase = true) &&
            suggestions.isNotEmpty() &&
            !SearchVm.loading
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(vertical = 4.dp)
            ) {
                suggestions.forEach { suggestion ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable {
                                SearchVm.query = suggestion
                                focus.clearFocus()
                                SearchVm.run(suggestion)
                            }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            suggestion,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (SearchVm.playlists.isNotEmpty()) {
            PlaylistShelf(
                title = "Playlists",
                playlists = SearchVm.playlists,
                onOpen = { openPlaylist = it }
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                SearchVm.loading && SearchVm.results.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

                SearchVm.error != null && SearchVm.results.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            SearchVm.error ?: "",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(32.dp)
                        )
                    }

                SearchVm.results.isNotEmpty() ->
                    SongList(SearchVm.results, "Kuch nahi mila", discoveryMode = true)

                else ->
                    SongList(
                        recommendations,
                        "Tumhare taste ke recommendations yahan aayenge.",
                        discoveryMode = true
                    )
            }
        }
    }
}

@Composable
private fun PlaylistShelf(
    title: String,
    playlists: List<RemotePlaylist>,
    onOpen: (RemotePlaylist) -> Unit
) {
    Column(Modifier.padding(top = 14.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.size(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(playlists, key = { i, p -> "${p.url}#$i" }) { _, playlist ->
                Column(Modifier.width(154.dp)) {
                    Art(playlist.thumb, Modifier.size(154.dp), RoundedCornerShape(14.dp))
                    Text(
                        playlist.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Text(
                        playlist.uploader,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { onOpen(playlist) },
                        modifier = Modifier.fillMaxWidth().padding(top = 5.dp)
                    ) {
                        Text("Open")
                    }
                }
            }
        }
    }
}

@Composable
fun RemotePlaylistScreen(playlist: RemotePlaylist, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val songs by produceState<List<Song>?>(null, playlist.url) {
        value = withContext(Dispatchers.IO) {
            runCatching { YouTubeApi.openPlaylist(playlist.url) }.getOrDefault(emptyList())
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Art(playlist.thumb, Modifier.size(54.dp), RoundedCornerShape(10.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(playlist.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    buildString {
                        append(playlist.uploader)
                        if (playlist.songCount > 0) append(" • ${playlist.songCount} songs")
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when (val loaded = songs) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> SongList(
                songs = loaded,
                empty = "Is playlist ke songs load nahi ho paaye.",
                modifier = Modifier.fillMaxSize(),
                discoveryMode = false
            )
        }
    }
}

// ───────────────────────── LIBRARY ─────────────────────────

@Composable
fun LibraryScreen() {
    var tab by remember { mutableIntStateOf(0) }
    val titles = listOf("Downloads", "Liked", "Playlists", "History")
    val downloads by Store.downloads.collectAsState()
    val liked by Store.liked.collectAsState()
    val history by Store.history.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Text(
            "Library",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp, 20.dp, 16.dp, 4.dp)
        )
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp, containerColor = Color.Transparent) {
            titles.forEachIndexed { i, title ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
            }
        }
        when (tab) {
            0 -> SongList(downloads.map { it.song }, "Abhi koi download nahi.\nKisi gaane ke ⋮ menu se Download karo.")
            1 -> SongList(liked, "Abhi koi liked song nahi.")
            2 -> PlaylistsTab()
            else -> Column {
                if (history.isNotEmpty()) {
                    TextButton(onClick = { Store.clearHistory() }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text("Clear history")
                    }
                }
                SongList(history, "History khali hai.")
            }
        }
    }
}

@Composable
private fun PlaylistsTab() {
    val lists by Store.playlists.collectAsState()
    var openId by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val open = lists.firstOrNull { it.id == openId }

    if (open != null) {
        BackHandler { openId = null }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { openId = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(
                    open.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = {
                    Store.deletePlaylist(open.id)
                    openId = null
                }) {
                    Icon(Icons.Filled.Delete, "Delete playlist")
                }
            }
            SongList(
                open.songs,
                "Playlist khali hai.\nKisi gaane ke ⋮ menu se 'Add to playlist' use karo.",
                extraLabel = "Remove from playlist",
                onExtra = { Store.removeFromPlaylist(open.id, it) }
            )
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = { creating = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
            Icon(Icons.Filled.Add, null)
            Spacer(Modifier.width(6.dp))
            Text("New playlist")
        }
        if (lists.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Koi playlist nahi hai.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn {
                itemsIndexed(lists, key = { _, p -> p.id }) { _, playlist ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable { openId = playlist.id }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(playlist.name, fontWeight = FontWeight.Medium)
                        Text(
                            "${playlist.songs.size} songs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    if (creating) {
        NameDialog(
            "New playlist",
            onDismiss = { creating = false }
        ) {
            Store.createPlaylist(it)
            creating = false
        }
    }
}
