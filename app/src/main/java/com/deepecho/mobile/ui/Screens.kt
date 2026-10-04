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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import com.deepecho.mobile.net.Downloads
import com.deepecho.mobile.net.RemotePlaylist
import com.deepecho.mobile.net.YouTubeApi
import com.deepecho.mobile.player.PlayerClient
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val homeSongShelfCache = ConcurrentHashMap<String, List<Song>>()
private val homePlaylistShelfCache = ConcurrentHashMap<String, List<RemotePlaylist>>()

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
    val moods = remember {
        listOf(
            "Feel Good", "Romance", "Relax", "Bad Mood", "Energize",
            "Party", "Workout", "Focus", "Chill", "Bollywood", "Pop", "Devotional"
        )
    }
    var selectedMood by remember { mutableStateOf("Feel Good") }

    // Home used to start one NewPipe prewarm extraction for almost every shelf as the user
    // scrolled into it. That caused visible jank on weaker phones. Keep just one delayed,
    // stability-first warmup instead of doing heavy extractor work during scroll.
    LaunchedEffect(history.firstOrNull()?.url, liked.firstOrNull()?.url) {
        delay(1200)
        (history.firstOrNull() ?: liked.firstOrNull())?.let { PlayerClient.prewarm(listOf(it), 1) }
    }

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

        item {
            Column(Modifier.padding(top = 2.dp)) {
                Text(
                    "Pick your vibe",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(moods, key = { _, mood -> mood }) { _, mood ->
                        FilterChip(
                            selected = selectedMood == mood,
                            onClick = { selectedMood = mood },
                            label = { Text(mood, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }
            }
        }

        item(key = "mood-$selectedMood") {
            TasteShelf(HomeMix("$selectedMood for you", TasteEngine.moodQuery(selectedMood)))
        }

        item {
            TasteShelf(HomeMix("Latest Releases", TasteEngine.latestReleaseQuery()))
        }

        if (history.isNotEmpty()) item { Shelf("Recently played", history.take(15), prewarmCount = 0) }
        if (liked.isNotEmpty()) item { Shelf("Your likes", liked.take(15), prewarmCount = 0) }
        mixes.forEach { mix ->
            item(key = mix.query) { TasteShelf(mix) }
        }
        item {
            HomePlaylistShelf(
                query = remember(history, liked) { TasteEngine.playlistQuery() },
                onOpen = { openPlaylist = it }
            )
        }

        // Top Artists remains the final Home section.
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
    val cached = homeSongShelfCache[mix.query].orEmpty()
    val songs by produceState<List<Song>>(cached, mix.query) {
        if (value.isEmpty()) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { YouTubeApi.search(mix.query) }.getOrDefault(emptyList())
            }
            if (loaded.isNotEmpty()) homeSongShelfCache[mix.query] = loaded
            value = loaded
        }
    }
    Shelf(mix.title, songs, prewarmCount = 0)
}

@Composable
private fun Shelf(title: String, songs: List<Song>, prewarmCount: Int = 1) {
    val liked by Store.liked.collectAsState()
    val downloads by Store.downloads.collectAsState()
    val downloadProgress by Downloads.progress.collectAsState()

    LaunchedEffect(songs.firstOrNull()?.url, prewarmCount) {
        if (prewarmCount > 0 && songs.isNotEmpty()) PlayerClient.prewarm(songs, prewarmCount)
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
                itemsIndexed(songs, key = { i, song -> "${song.url}#$i" }) { _, song ->
                    val isLiked = liked.any { it.url == song.url }
                    val downloaded = downloads.any { it.song.url == song.url }
                    val progress = downloadProgress[song.url]

                    Column(Modifier.width(140.dp)) {
                        Column(Modifier.clickable { PlayerClient.playDiscovery(song) }) {
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
                        Row(
                            Modifier.fillMaxWidth().padding(top = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { PlayerClient.addToQueue(song) }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Filled.QueueMusic, "Add to queue", modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            if (progress != null) {
                                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        if (downloaded) Store.removeDownload(song.url) else Downloads.start(song)
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        if (downloaded) Icons.Filled.DownloadDone else Icons.Filled.Download,
                                        if (downloaded) "Downloaded" else "Download",
                                        modifier = Modifier.size(19.dp),
                                        tint = if (downloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { Store.toggleLike(song) }, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    if (isLiked) "Unlike" else "Like",
                                    modifier = Modifier.size(19.dp),
                                    tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomePlaylistShelf(query: String, onOpen: (RemotePlaylist) -> Unit) {
    val cached = homePlaylistShelfCache[query].orEmpty()
    val lists by produceState<List<RemotePlaylist>>(cached, query) {
        if (value.isEmpty()) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { YouTubeApi.searchPlaylists(query, 8) }.getOrDefault(emptyList())
            }
            if (loaded.isNotEmpty()) homePlaylistShelfCache[query] = loaded
            value = loaded
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
    var videos by mutableStateOf<List<Song>>(emptyList())
    var albums by mutableStateOf<List<RemotePlaylist>>(emptyList())
    var playlists by mutableStateOf<List<RemotePlaylist>>(emptyList())
    var artists by mutableStateOf<List<TopArtist>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var hasSearched by mutableStateOf(false)
    var submittedQuery by mutableStateOf("")

    fun edit(text: String) {
        query = text
        if (text.isBlank()) {
            generation += 1
            results = emptyList()
            videos = emptyList()
            albums = emptyList()
            playlists = emptyList()
            artists = emptyList()
            loading = false
            error = null
            hasSearched = false
            submittedQuery = ""
        }
    }

    fun run(q: String) {
        val text = q.trim().replace(Regex("\\s+"), " ")
        if (text.isEmpty()) return
        val request = ++generation
        query = text
        submittedQuery = text
        loading = true
        hasSearched = true
        error = null
        results = emptyList()
        videos = emptyList()
        albums = emptyList()
        playlists = emptyList()
        artists = emptyList()
        Store.addSearchQuery(text)

        // One coordinated request keeps loading state correct and runs the independent
        // YouTube Music buckets concurrently instead of serializing them on the UI path.
        scope.launch {
            val songsJob = async(Dispatchers.IO) { runCatching { YouTubeApi.searchSongs(text) } }
            val videosJob = async(Dispatchers.IO) { runCatching { YouTubeApi.searchVideos(text) } }
            val albumsJob = async(Dispatchers.IO) { runCatching { YouTubeApi.searchAlbums(text, 10) } }
            val playlistsJob = async(Dispatchers.IO) { runCatching { YouTubeApi.searchPlaylists(text, 8) } }
            val artistsJob = async(Dispatchers.IO) { runCatching { YouTubeApi.searchArtists(text, 12) } }

            val songsResult = songsJob.await()
            val videosResult = videosJob.await()
            val albumsResult = albumsJob.await()
            val playlistsResult = playlistsJob.await()
            val artistsResult = artistsJob.await()
            if (request != generation) return@launch

            results = songsResult.getOrDefault(emptyList())
            videos = videosResult.getOrDefault(emptyList())
            albums = albumsResult.getOrDefault(emptyList())
            playlists = playlistsResult.getOrDefault(emptyList())
            artists = artistsResult.getOrDefault(emptyList())
            loading = false

            if (results.isNotEmpty()) PlayerClient.prewarm(results, 1)
            if (results.isEmpty() && videos.isEmpty() && albums.isEmpty() && playlists.isEmpty() && artists.isEmpty()) {
                val failure = songsResult.exceptionOrNull()
                    ?: videosResult.exceptionOrNull()
                    ?: albumsResult.exceptionOrNull()
                    ?: playlistsResult.exceptionOrNull()
                    ?: artistsResult.exceptionOrNull()
                error = if (failure != null) {
                    "Search fail: ${failure.message ?: "network error"}"
                } else {
                    "Kuch nahi mila"
                }
            }
        }
    }
}

@Composable
fun SearchScreen() {
    val focus: FocusManager = LocalFocusManager.current
    val history by Store.history.collectAsState()
    val liked by Store.liked.collectAsState()
    val searchHistory by Store.searchHistory.collectAsState()
    var openPlaylist by remember { mutableStateOf<RemotePlaylist?>(null) }
    var openArtist by remember { mutableStateOf<TopArtist?>(null) }
    var filter by remember { mutableIntStateOf(0) }

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

    val recommendations by produceState<List<Song>>(emptyList(), history, liked) {
        value = withContext(Dispatchers.IO) {
            runCatching { TasteEngine.searchRecommendations(12) }.getOrDefault(emptyList())
        }
    }
    val suggestions = remember(SearchVm.query, SearchVm.results, history, liked, searchHistory) {
        TasteEngine.searchSuggestions(SearchVm.query, SearchVm.results, 8)
    }

    LaunchedEffect(recommendations.firstOrNull()?.url) {
        PlayerClient.prewarm(recommendations, 1)
    }
    LaunchedEffect(SearchVm.submittedQuery) { filter = 0 }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = SearchVm.query,
            onValueChange = SearchVm::edit,
            singleLine = true,
            placeholder = { Text("Song, video, artist ya album") },
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

        if (SearchVm.hasSearched) {
            val tabs = listOf("All", "Songs", "Videos", "Albums", "Artists")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(tabs) { index, label ->
                    FilterChip(
                        selected = filter == index,
                        onClick = { filter = index },
                        label = { Text(label) }
                    )
                }
            }
        }

        if (SearchVm.query.isBlank() && searchHistory.isNotEmpty()) {
            SearchHistoryStrip(
                history = searchHistory,
                onSearch = { query ->
                    SearchVm.query = query
                    focus.clearFocus()
                    SearchVm.run(query)
                },
                onClear = Store::clearSearchHistory
            )
        }

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

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val hasAny = SearchVm.results.isNotEmpty() || SearchVm.videos.isNotEmpty() ||
                SearchVm.albums.isNotEmpty() || SearchVm.playlists.isNotEmpty() || SearchVm.artists.isNotEmpty()
            when {
                SearchVm.loading && !hasAny ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

                SearchVm.hasSearched && !SearchVm.loading && !hasAny ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            SearchVm.error ?: "Kuch nahi mila",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(32.dp)
                        )
                    }

                hasAny ->
                    RichSearchResults(
                        songs = SearchVm.results,
                        videos = SearchVm.videos,
                        albums = SearchVm.albums,
                        playlists = SearchVm.playlists,
                        searchedArtists = SearchVm.artists,
                        filter = filter,
                        onOpenPlaylist = { openPlaylist = it },
                        onOpenArtist = { openArtist = it }
                    )

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
private fun SearchHistoryStrip(
    history: List<String>,
    onSearch: (String) -> Unit,
    onClear: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.History, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(7.dp))
            Text("Recent searches", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onClear) { Text("Clear") }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(history.take(10), key = { i, text -> "$text#$i" }) { _, text ->
                FilterChip(
                    selected = false,
                    onClick = { onSearch(text) },
                    modifier = Modifier.height(32.dp),
                    label = {
                        Text(
                            text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun ResultTypeLabel(label: String) {
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun SearchTopResult(song: Song, kind: String = "Song") {
    val liked by Store.liked.collectAsState()
    val downloads by Store.downloads.collectAsState()
    val progress by Downloads.progress.collectAsState()
    val isLiked = liked.any { it.url == song.url }
    val downloaded = downloads.any { it.song.url == song.url }
    val p = progress[song.url]

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "TOP RESULT",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().clickable { PlayerClient.playDiscovery(song) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Art(song.thumb, Modifier.size(88.dp), RoundedCornerShape(14.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    ResultTypeLabel(kind)
                    Spacer(Modifier.height(5.dp))
                    Text(song.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                    Text(
                        song.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(" Play", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { PlayerClient.addToQueue(song) }) {
                    Icon(Icons.Filled.QueueMusic, "Add to queue")
                }
                if (p != null) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(progress = { p }, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                } else {
                    IconButton(onClick = { if (downloaded) Store.removeDownload(song.url) else Downloads.start(song) }) {
                        Icon(if (downloaded) Icons.Filled.DownloadDone else Icons.Filled.Download, "Download")
                    }
                }
                IconButton(onClick = { Store.toggleLike(song) }) {
                    Icon(
                        if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        "Like",
                        tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultSongRow(song: Song, kind: String) {
    Row(
        Modifier.fillMaxWidth()
            .clickable { PlayerClient.playDiscovery(song) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Art(song.thumb, Modifier.size(58.dp), RoundedCornerShape(10.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(
                song.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(3.dp))
            ResultTypeLabel(kind)
        }
        IconButton(onClick = { PlayerClient.addToQueue(song) }) {
            Icon(Icons.Filled.QueueMusic, "Add to queue")
        }
    }
}

@Composable
private fun SearchArtistsShelf(artists: List<TopArtist>, onArtist: (TopArtist) -> Unit, vertical: Boolean = false) {
    if (artists.isEmpty()) return
    if (vertical) {
        Column {
            artists.forEach { artist ->
                Row(
                    Modifier.fillMaxWidth().clickable { onArtist(artist) }.padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Art(artist.thumb, Modifier.size(58.dp), CircleShape)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        ResultTypeLabel("Artist")
                    }
                }
            }
        }
        return
    }

    Column(Modifier.padding(top = 12.dp)) {
        Text(
            "Artists",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(artists, key = { _, a -> a.name }) { _, artist ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(104.dp).clickable { onArtist(artist) }
                ) {
                    Art(artist.thumb, Modifier.size(82.dp), CircleShape)
                    Text(
                        artist.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                    Spacer(Modifier.height(3.dp))
                    ResultTypeLabel("Artist")
                }
            }
        }
    }
}

@Composable
private fun SearchSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun RichSearchResults(
    songs: List<Song>,
    videos: List<Song>,
    albums: List<RemotePlaylist>,
    playlists: List<RemotePlaylist>,
    searchedArtists: List<TopArtist>,
    filter: Int,
    onOpenPlaylist: (RemotePlaylist) -> Unit,
    onOpenArtist: (TopArtist) -> Unit
) {
    val artists = remember(songs, videos, searchedArtists) {
        if (searchedArtists.isNotEmpty()) {
            searchedArtists
        } else {
            (songs + videos).filter { it.artist.isNotBlank() }
                .groupBy { it.artist.trim() }
                .entries
                .sortedByDescending { it.value.size }
                .take(12)
                .map { (name, items) -> TopArtist(name, items.firstOrNull()?.thumb, items.size) }
        }
    }
    val top = songs.firstOrNull() ?: videos.firstOrNull()
    val topKind = if (songs.isNotEmpty()) "Song" else "Video"

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 18.dp)) {
        when (filter) {
            0 -> {
                if (top != null) item { SearchTopResult(top, topKind) }

                if (songs.isNotEmpty()) {
                    item { SearchSectionTitle("Songs") }
                    itemsIndexed(songs.take(5), key = { i, song -> "all-song-${song.url}-$i" }) { _, song ->
                        SearchResultSongRow(song, "Song")
                    }
                }

                if (videos.isNotEmpty()) {
                    item { SearchSectionTitle("Videos") }
                    itemsIndexed(videos.take(5), key = { i, song -> "all-video-${song.url}-$i" }) { _, video ->
                        SearchResultSongRow(video, "Video")
                    }
                }

                if (albums.isNotEmpty()) {
                    item { PlaylistShelf("Albums", albums.take(10), onOpenPlaylist, showTypeLabel = true, typeLabel = "Album") }
                }
                if (playlists.isNotEmpty()) {
                    item { PlaylistShelf("Playlists", playlists.take(8), onOpenPlaylist, showTypeLabel = true, typeLabel = "Playlist") }
                }

                if (artists.isNotEmpty()) item { SearchArtistsShelf(artists, onOpenArtist) }
            }

            1 -> {
                item { SearchSectionTitle("Songs") }
                itemsIndexed(songs, key = { i, song -> "song-${song.url}-$i" }) { _, song ->
                    SearchResultSongRow(song, "Song")
                }
            }

            2 -> {
                item { SearchSectionTitle("Videos") }
                itemsIndexed(videos, key = { i, song -> "video-${song.url}-$i" }) { _, video ->
                    SearchResultSongRow(video, "Video")
                }
            }

            3 -> {
                if (albums.isEmpty()) {
                    item {
                        Text(
                            "Is search ke liye album result nahi mila.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                } else {
                    item { PlaylistShelf("Albums", albums, onOpenPlaylist, showTypeLabel = true, typeLabel = "Album") }
                }
            }

            else -> {
                item { SearchSectionTitle("Artists") }
                item { SearchArtistsShelf(artists, onOpenArtist, vertical = true) }
            }
        }
    }
}

@Composable
private fun PlaylistShelf(
    title: String,
    playlists: List<RemotePlaylist>,
    onOpen: (RemotePlaylist) -> Unit,
    showTypeLabel: Boolean = false,
    typeLabel: String = "Playlist"
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
                    if (showTypeLabel) {
                        Spacer(Modifier.height(4.dp))
                        ResultTypeLabel(typeLabel)
                    }
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
    val titles = listOf("Downloads", "Liked", "Playlists", "History", "Stats")
    val downloads by Store.downloads.collectAsState()
    val liked by Store.liked.collectAsState()
    val history by Store.history.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Library",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { tab = 4 }) {
                Text("Listening Stats", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp, containerColor = Color.Transparent) {
            titles.forEachIndexed { i, title ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
            }
        }
        when (tab) {
            0 -> SongList(downloads.map { it.song }, "Abhi koi download nahi.\nKisi gaane ke ⋮ menu se Download karo.")
            1 -> SongList(liked, "Abhi koi liked song nahi.")
            2 -> PlaylistsTab()
            3 -> Column {
                if (history.isNotEmpty()) {
                    TextButton(onClick = { Store.clearHistory() }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text("Clear history")
                    }
                }
                SongList(history, "History khali hai.")
            }
            else -> ListeningStatsScreen()
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
