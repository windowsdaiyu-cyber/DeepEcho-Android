package com.deepecho.mobile.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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

/** Liked / playlists / history / downloads — sab ek chhoti JSON file mein. */
object Store {
    private lateinit var file: File

    private val _liked = MutableStateFlow<List<Song>>(emptyList())
    private val _history = MutableStateFlow<List<Song>>(emptyList())
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())

    val liked: StateFlow<List<Song>> = _liked
    val history: StateFlow<List<Song>> = _history
    val playlists: StateFlow<List<Playlist>> = _playlists
    val downloads: StateFlow<List<DownloadItem>> = _downloads

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
        } catch (_: Exception) {
        }
    }

    @Synchronized
    private fun save() {
        try {
            val o = JSONObject()
            o.put("liked", _liked.value.toJsonArray())
            o.put("history", _history.value.toJsonArray())
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
            file.writeText(o.toString())
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
