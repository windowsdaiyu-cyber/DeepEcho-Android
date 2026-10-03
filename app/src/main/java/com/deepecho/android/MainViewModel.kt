package com.deepecho.android

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import org.json.JSONObject
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deepecho.android.data.DeepEchoCatalogRepository
import com.deepecho.android.data.SettingsStore
import com.deepecho.android.model.Track
import com.deepecho.android.playback.FloatingLyricsBus
import com.deepecho.android.playback.PlayerConnection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = SettingsStore(application)
    private val catalog = DeepEchoCatalogRepository()
    private val player = PlayerConnection(application)
    private val warmStreams = LinkedHashMap<String, Track>()
    private val downloadPrefs = application.getSharedPreferences("deepecho_downloads", Context.MODE_PRIVATE)

    val themeKey = settings.theme.stateIn(viewModelScope, SharingStarted.Eagerly, "golden")
    val bridgeUrl = settings.bridgeUrl.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsStore.DEFAULT_BRIDGE_URL)
    val smartAutoplay = settings.smartAutoplay.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val lyricsFx = settings.lyricsFx.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val playerState = player.state

    private val _home = MutableStateFlow<List<Track>>(DeepEchoCatalogRepository.demoTracks)
    val home: StateFlow<List<Track>> = _home.asStateFlow()

    private val _search = MutableStateFlow<List<Track>>(emptyList())
    val search: StateFlow<List<Track>> = _search.asStateFlow()

    private val _downloads = MutableStateFlow<List<Track>>(emptyList())
    val downloads: StateFlow<List<Track>> = _downloads.asStateFlow()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _activeLyric = MutableStateFlow("Play a song to start synced lyrics")
    val activeLyric: StateFlow<String> = _activeLyric.asStateFlow()

    private val _status = MutableStateFlow("DeepEcho Android Alpha ready")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _bridgeConnected = MutableStateFlow(false)
    val bridgeConnected: StateFlow<Boolean> = _bridgeConnected.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _floatingLyricsEnabled = MutableStateFlow(false)
    val floatingLyricsEnabled: StateFlow<Boolean> = _floatingLyricsEnabled.asStateFlow()

    init {
        player.connect()
        refreshDownloads()
        refreshHome()
        viewModelScope.launch {
            while (true) {
                player.tick()
                updateLyrics()
                delay(250)
            }
        }
        viewModelScope.launch {
            while (true) {
                _bridgeConnected.value = catalog.health(bridgeUrl.value)
                delay(12_000)
            }
        }
    }

    fun refreshHome() {
        viewModelScope.launch {
            val result = catalog.home(bridgeUrl.value)
            _home.value = result.value
            _bridgeConnected.value = result.fromBridge
            _status.value = if (result.fromBridge) "Live DeepEcho catalog connected" else "Demo catalog · start PC Bridge for live search"
            if (result.fromBridge) warmLikelyTracks(result.value.take(1))
        }
    }

    fun search(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _isSearching.value = true
            _status.value = "Searching…"
            val result = catalog.search(bridgeUrl.value, query)
            _search.value = result.value
            _bridgeConnected.value = result.fromBridge
            _status.value = when {
                result.value.isNotEmpty() && result.fromBridge -> "${result.value.size} results · live catalog"
                result.value.isNotEmpty() -> "Bridge offline · showing local demo matches"
                else -> result.error ?: "No results"
            }
            _isSearching.value = false
            if (result.fromBridge) warmLikelyTracks(result.value.take(2))
        }
    }

    fun play(track: Track) {
        viewModelScope.launch {
            val offlineUri = localDownloadUri(track.id)
            val warmed = warmStreams.remove(track.id)
            val result = when {
                offlineUri.isNotBlank() -> {
                    _status.value = "Starting offline ${track.title}…"
                    com.deepecho.android.data.CatalogResult(track.copy(streamUrl = offlineUri, downloaded = true), false)
                }
                warmed != null && warmed.streamUrl.isNotBlank() -> {
                    _status.value = "Starting ${track.title}…"
                    com.deepecho.android.data.CatalogResult(warmed, true)
                }
                else -> {
                    _status.value = "Resolving ${track.title}…"
                    catalog.resolve(bridgeUrl.value, track)
                }
            }
            val resolved = result.value
            if (resolved.streamUrl.isBlank()) {
                _status.value = result.error ?: "Playable source unavailable"
                return@launch
            }
            _currentTrack.value = resolved
            _bridgeConnected.value = _bridgeConnected.value || result.fromBridge
            player.play(resolved)
            _status.value = "Playing ${resolved.title}"
            updateLyrics()
            if (resolved.lyrics.isEmpty() && result.fromBridge) {
                launch {
                    val lyricResult = catalog.lyrics(bridgeUrl.value, resolved)
                    if (_currentTrack.value?.id == resolved.id && lyricResult.value.isNotEmpty()) {
                        _currentTrack.value = resolved.copy(lyrics = lyricResult.value)
                        _status.value = "Playing ${resolved.title} · synced lyrics ready"
                        updateLyrics()
                    }
                }
            }
        }
    }

    fun togglePlayback() = player.toggle()
    fun seekTo(ms: Long) = player.seekTo(ms)

    fun download(track: Track) {
        viewModelScope.launch {
            _status.value = "Preparing download…"
            val result = catalog.resolve(bridgeUrl.value, track)
            val resolved = result.value
            if (resolved.streamUrl.isBlank()) {
                _status.value = result.error ?: "Download source unavailable"
                return@launch
            }
            runCatching {
                val context = getApplication<Application>()
                val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val safeName = resolved.title.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(80)
                val request = DownloadManager.Request(Uri.parse(resolved.streamUrl))
                    .setTitle(resolved.title)
                    .setDescription("DeepEcho offline audio")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "DeepEcho/$safeName.m4a")
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(false)
                    .addRequestHeader("User-Agent", "Mozilla/5.0 DeepEcho-Android/0.1")
                val downloadId = manager.enqueue(request)
                persistDownload(resolved, downloadId)
                if (_downloads.value.none { it.id == resolved.id }) _downloads.value = listOf(resolved.copy(downloaded = true)) + _downloads.value
            }.onSuccess {
                _status.value = "Download started · ${resolved.title}"
            }.onFailure {
                _status.value = "Download failed: ${it.message ?: "unknown error"}"
            }
        }
    }

    fun setTheme(key: String) { viewModelScope.launch { settings.setTheme(key) } }
    fun setBridgeUrl(url: String) { viewModelScope.launch { settings.setBridgeUrl(url); delay(120); refreshHome() } }
    fun setSmartAutoplay(value: Boolean) { viewModelScope.launch { settings.setSmartAutoplay(value) } }
    fun setLyricsFx(value: Boolean) { viewModelScope.launch { settings.setLyricsFx(value) } }

    fun setFloatingLyrics(enabled: Boolean) {
        _floatingLyricsEnabled.value = enabled
        FloatingLyricsBus.setEnabled(enabled)
        updateLyrics()
    }

    private fun persistDownload(track: Track, downloadId: Long) {
        val payload = JSONObject()
            .put("id", track.id)
            .put("title", track.title)
            .put("artist", track.artist)
            .put("durationMs", track.durationMs)
            .put("thumbnail", track.thumbnail)
            .put("sourceUrl", track.sourceUrl)
            .put("downloadId", downloadId)
        downloadPrefs.edit().putString("download_${track.id}", payload.toString()).apply()
    }

    private fun refreshDownloads() {
        viewModelScope.launch {
            val restored = downloadPrefs.all.values.mapNotNull { raw ->
                val text = raw as? String ?: return@mapNotNull null
                val obj = runCatching { JSONObject(text) }.getOrNull() ?: return@mapNotNull null
                val track = Track(
                    id = obj.optString("id"),
                    title = obj.optString("title", "Downloaded track"),
                    artist = obj.optString("artist", ""),
                    durationMs = obj.optLong("durationMs"),
                    thumbnail = obj.optString("thumbnail"),
                    sourceUrl = obj.optString("sourceUrl"),
                    downloaded = true
                )
                if (track.id.isBlank()) null else track.copy(streamUrl = localDownloadUri(track.id))
            }.sortedBy { it.title.lowercase() }
            _downloads.value = restored
        }
    }

    private fun localDownloadUri(trackId: String): String {
        val raw = downloadPrefs.getString("download_$trackId", null) ?: return ""
        val id = runCatching { JSONObject(raw).optLong("downloadId", -1L) }.getOrDefault(-1L)
        if (id <= 0) return ""
        val manager = getApplication<Application>().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return runCatching {
            manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                if (!cursor.moveToFirst()) return@use ""
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                if (status != DownloadManager.STATUS_SUCCESSFUL) return@use ""
                cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)).orEmpty()
            }
        }.getOrDefault("")
    }

    private fun warmLikelyTracks(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        viewModelScope.launch {
            for (track in tracks) {
                if (warmStreams.containsKey(track.id) || track.streamUrl.isNotBlank()) continue
                val result = catalog.resolve(bridgeUrl.value, track)
                if (result.value.streamUrl.isNotBlank()) {
                    warmStreams[track.id] = result.value
                    while (warmStreams.size > 4) warmStreams.remove(warmStreams.keys.first())
                }
            }
        }
    }

    private fun updateLyrics() {
        val track = _currentTrack.value
        if (track == null) {
            _activeLyric.value = "Play a song to start synced lyrics"
            return
        }
        val position = player.state.value.positionMs
        val current = track.lyrics.lastOrNull { it.startMs <= position }?.text
            ?: if (track.lyrics.isEmpty()) "♪ ${track.title}" else "…"
        _activeLyric.value = current
        FloatingLyricsBus.update(track.title, current)
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}
