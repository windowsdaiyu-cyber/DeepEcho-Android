package com.deepecho.mobile.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.UUID

data class Song(
    val url: String,
    val title: String,
    val artist: String,
    val durationSec: Long,
    val thumb: String?
) {
    val videoId: String
        get() = url.substringAfter("v=", url.substringAfterLast('/')).take(11)
}

data class Playlist(val id: String, val name: String, val songs: List<Song>)
data class DownloadItem(val song: Song, val path: String)

data class ListeningStat(
    val song: Song,
    val playCount: Int,
    val listenedMs: Long,
    val lastPlayedAt: Long
)

private fun Song.toJson(): JSONObject = JSONObject()
    .put("url", url).put("title", title).put("artist", artist)
    .put("dur", durationSec).put("thumb", thumb ?: "")

private fun JSONObject.toSong(): Song = Song(
    getString("url"), optString("title"), optString("artist"),
    optLong("dur"), optString("thumb").ifEmpty { null }
)

private fun List<Song>.toJsonArray(): JSONArray {
    val a = JSONArray()
    forEach { a.put(it.toJson()) }
    return a
}

private fun JSONArray?.toSongs(): List<Song> =
    if (this == null) emptyList() else (0 until length()).map { getJSONObject(it).toSong() }

/** Liked / playlists / history / downloads / listening stats — all local, no account required. */
object Store {
    private lateinit var file: File

    private val _liked = MutableStateFlow<List<Song>>(emptyList())
    private val _history = MutableStateFlow<List<Song>>(emptyList())
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    private val _listeningStats = MutableStateFlow<List<ListeningStat>>(emptyList())
    private val _dailyListeningMs = MutableStateFlow<Map<String, Long>>(emptyMap())

    val liked: StateFlow<List<Song>> = _liked
    val history: StateFlow<List<Song>> = _history
    val playlists: StateFlow<List<Playlist>> = _playlists
    val downloads: StateFlow<List<DownloadItem>> = _downloads
    val searchHistory: StateFlow<List<String>> = _searchHistory
    val listeningStats: StateFlow<List<ListeningStat>> = _listeningStats
    val dailyListeningMs: StateFlow<Map<String, Long>> = _dailyListeningMs

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "library.json")
        load()
    }

    private fun load() {
        if (!file.exists()) return
        try {
            val o = JSONObject(file.readText())
            _liked.value = o.optJSONArray("liked").toSongs()
            _history.value = o.optJSONArray("history").toSongs()
            val searches = o.optJSONArray("search_history")
            _searchHistory.value = if (searches == null) emptyList() else (0 until searches.length())
                .mapNotNull { searches.optString(it).trim().takeIf(String::isNotBlank) }
                .distinctBy { it.lowercase() }
                .take(30)
            val pl = o.optJSONArray("playlists")
            _playlists.value = if (pl == null) emptyList() else (0 until pl.length()).map {
                val p = pl.getJSONObject(it)
                Playlist(p.getString("id"), p.optString("name"), p.optJSONArray("songs").toSongs())
            }
            val dl = o.optJSONArray("downloads")
            _downloads.value = if (dl == null) emptyList() else (0 until dl.length()).map {
                val d = dl.getJSONObject(it)
                DownloadItem(d.getJSONObject("song").toSong(), d.getString("path"))
            }.filter { File(it.path).exists() }

            val stats = o.optJSONArray("listening_stats")
            _listeningStats.value = if (stats == null) emptyList() else (0 until stats.length()).mapNotNull { i ->
                runCatching {
                    val s = stats.getJSONObject(i)
                    ListeningStat(
                        song = s.getJSONObject("song").toSong(),
                        playCount = s.optInt("plays", 0).coerceAtLeast(0),
                        listenedMs = s.optLong("listened_ms", 0L).coerceAtLeast(0L),
                        lastPlayedAt = s.optLong("last_played_at", 0L).coerceAtLeast(0L)
                    )
                }.getOrNull()
            }.sortedByDescending { it.lastPlayedAt }.take(500)

            val daily = o.optJSONObject("daily_listening_ms")
            if (daily != null) {
                val map = linkedMapOf<String, Long>()
                val keys = daily.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = daily.optLong(key, 0L).coerceAtLeast(0L)
                }
                _dailyListeningMs.value = map
            }
        } catch (_: Exception) {
        }
    }

    @Synchronized
    private fun save() {
        try {
            val o = JSONObject()
            o.put("liked", _liked.value.toJsonArray())
            o.put("history", _history.value.toJsonArray())
            val searches = JSONArray()
            _searchHistory.value.forEach { searches.put(it) }
            o.put("search_history", searches)
            val pl = JSONArray()
            _playlists.value.forEach {
                pl.put(JSONObject().put("id", it.id).put("name", it.name).put("songs", it.songs.toJsonArray()))
            }
            o.put("playlists", pl)
            val dl = JSONArray()
            _downloads.value.forEach {
                dl.put(JSONObject().put("song", it.song.toJson()).put("path", it.path))
            }
            o.put("downloads", dl)

            val stats = JSONArray()
            _listeningStats.value.forEach { stat ->
                stats.put(
                    JSONObject()
                        .put("song", stat.song.toJson())
                        .put("plays", stat.playCount)
                        .put("listened_ms", stat.listenedMs)
                        .put("last_played_at", stat.lastPlayedAt)
                )
            }
            o.put("listening_stats", stats)

            val daily = JSONObject()
            _dailyListeningMs.value.toSortedMap().forEach { (day, ms) -> daily.put(day, ms) }
            o.put("daily_listening_ms", daily)

            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(o.toString())
                tmp.delete()
            }
        } catch (_: Exception) {
        }
    }

    // ---- liked ----
    fun isLiked(url: String) = _liked.value.any { it.url == url }

    fun toggleLike(song: Song) {
        _liked.value = if (isLiked(song.url)) _liked.value.filter { it.url != song.url }
        else listOf(song) + _liked.value
        save()
    }

    // ---- history ----
    fun addHistory(song: Song) {
        _history.value = (listOf(song) + _history.value.filter { it.url != song.url }).take(200)
        save()
    }

    fun clearHistory() {
        _history.value = emptyList()
        save()
    }

    // ---- listening stats ----
    fun recordPlayStart(song: Song) {
        val now = System.currentTimeMillis()
        val current = _listeningStats.value.firstOrNull { it.song.url == song.url }
        val updated = ListeningStat(
            song = song,
            playCount = (current?.playCount ?: 0) + 1,
            listenedMs = current?.listenedMs ?: 0L,
            lastPlayedAt = now
        )
        _listeningStats.value = (listOf(updated) + _listeningStats.value.filter { it.song.url != song.url })
            .sortedByDescending { it.lastPlayedAt }
            .take(500)
        save()
    }

    /** Called in coarse batches by PlayerClient so stats never create per-frame disk work. */
    fun recordListening(song: Song, deltaMs: Long) {
        val safe = deltaMs.coerceIn(0L, 120_000L)
        if (safe <= 0L) return
        val now = System.currentTimeMillis()
        val current = _listeningStats.value.firstOrNull { it.song.url == song.url }
        val updated = ListeningStat(
            song = song,
            playCount = current?.playCount ?: 0,
            listenedMs = (current?.listenedMs ?: 0L) + safe,
            lastPlayedAt = maxOf(current?.lastPlayedAt ?: 0L, now)
        )
        _listeningStats.value = (listOf(updated) + _listeningStats.value.filter { it.song.url != song.url })
            .sortedByDescending { it.lastPlayedAt }
            .take(500)

        val today = LocalDate.now().toString()
        val daily = _dailyListeningMs.value.toMutableMap()
        daily[today] = (daily[today] ?: 0L) + safe
        val keepFrom = LocalDate.now().minusDays(120)
        _dailyListeningMs.value = daily.filterKeys { day ->
            runCatching { !LocalDate.parse(day).isBefore(keepFrom) }.getOrDefault(false)
        }.toSortedMap()
        save()
    }

    fun clearListeningStats() {
        _listeningStats.value = emptyList()
        _dailyListeningMs.value = emptyMap()
        save()
    }

    // ---- search history ----
    fun addSearchQuery(query: String) {
        val clean = query.trim().replace(Regex("\\s+"), " ")
        if (clean.length < 2) return
        _searchHistory.value = (listOf(clean) + _searchHistory.value.filterNot { it.equals(clean, true) }).take(30)
        save()
    }

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        save()
    }

    // ---- playlists ----
    fun createPlaylist(name: String): String {
        val id = UUID.randomUUID().toString()
        _playlists.value = _playlists.value + Playlist(id, name.trim().ifEmpty { "New playlist" }, emptyList())
        save()
        return id
    }

    fun deletePlaylist(id: String) {
        _playlists.value = _playlists.value.filter { it.id != id }
        save()
    }

    fun addToPlaylist(id: String, song: Song) {
        _playlists.value = _playlists.value.map {
            if (it.id == id && it.songs.none { s -> s.url == song.url }) it.copy(songs = it.songs + song) else it
        }
        save()
    }

    fun removeFromPlaylist(id: String, song: Song) {
        _playlists.value = _playlists.value.map {
            if (it.id == id) it.copy(songs = it.songs.filter { s -> s.url != song.url }) else it
        }
        save()
    }

    // ---- downloads ----
    fun downloadPath(url: String): String? =
        _downloads.value.firstOrNull { it.song.url == url }?.path?.takeIf { File(it).exists() }

    fun addDownload(item: DownloadItem) {
        _downloads.value = listOf(item) + _downloads.value.filter { it.song.url != item.song.url }
        save()
    }

    fun removeDownload(url: String) {
        _downloads.value.firstOrNull { it.song.url == url }?.let { File(it.path).delete() }
        _downloads.value = _downloads.value.filter { it.song.url != url }
        save()
    }
}
