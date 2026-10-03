package com.deepecho.android.data

import com.deepecho.android.model.LyricLine
import com.deepecho.android.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets

data class CatalogResult<T>(
    val value: T,
    val fromBridge: Boolean,
    val error: String? = null
)

class DeepEchoCatalogRepository {
    suspend fun health(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val obj = getJson("${baseUrl.trimEnd('/')}/health", 2200, 2200)
            obj.optBoolean("ok")
        }.getOrDefault(false)
    }

    suspend fun home(baseUrl: String): CatalogResult<List<Track>> = withContext(Dispatchers.IO) {
        try {
            val obj = getJson("${baseUrl.trimEnd('/')}/home", 4500, 25000)
            val tracks = parseTracks(obj)
            if (tracks.isNotEmpty()) CatalogResult(tracks, true) else CatalogResult(demoTracks, false, "Bridge returned no Home tracks")
        } catch (error: Throwable) {
            CatalogResult(demoTracks, false, error.message ?: "Bridge offline")
        }
    }

    suspend fun search(baseUrl: String, query: String): CatalogResult<List<Track>> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isEmpty()) return@withContext CatalogResult(emptyList(), false)
        try {
            val encoded = URLEncoder.encode(clean, StandardCharsets.UTF_8.name())
            val obj = getJson("${baseUrl.trimEnd('/')}/search?q=$encoded&limit=20", 4500, 35000)
            val tracks = parseTracks(obj)
            CatalogResult(tracks, true, obj.optString("error").ifBlank { null })
        } catch (error: Throwable) {
            val fallback = demoTracks.filter {
                it.title.contains(clean, true) || it.artist.contains(clean, true) || clean.contains("demo", true)
            }
            CatalogResult(fallback, false, error.message ?: "Bridge offline")
        }
    }


    suspend fun lyrics(baseUrl: String, track: Track): CatalogResult<List<LyricLine>> = withContext(Dispatchers.IO) {
        if (track.lyrics.isNotEmpty()) return@withContext CatalogResult(track.lyrics, false)
        try {
            val id = URLEncoder.encode(track.id, StandardCharsets.UTF_8.name())
            val obj = getJson("${baseUrl.trimEnd('/')}/lyrics?id=$id", 3500, 35000)
            if (!obj.optBoolean("success")) throw IllegalStateException(obj.optString("error", "Lyrics unavailable"))
            val array = obj.optJSONArray("lyrics") ?: return@withContext CatalogResult(emptyList(), true)
            val lines = buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val text = item.optString("text").trim()
                    if (text.isNotEmpty()) add(LyricLine(item.optLong("startMs"), text))
                }
            }
            CatalogResult(lines, true)
        } catch (error: Throwable) {
            CatalogResult(emptyList(), false, error.message ?: "Lyrics unavailable")
        }
    }

    suspend fun resolve(baseUrl: String, track: Track, quality: String = "auto"): CatalogResult<Track> = withContext(Dispatchers.IO) {
        if (track.streamUrl.isNotBlank()) return@withContext CatalogResult(track, true)
        if (track.sourceUrl.endsWith(".mp3", true) || track.sourceUrl.endsWith(".m4a", true)) {
            return@withContext CatalogResult(track.withStream(track.sourceUrl), false)
        }
        try {
            val id = URLEncoder.encode(track.id, StandardCharsets.UTF_8.name())
            val q = URLEncoder.encode(quality, StandardCharsets.UTF_8.name())
            val obj = getJson("${baseUrl.trimEnd('/')}/resolve?id=$id&quality=$q", 3500, 18000)
            if (!obj.optBoolean("success")) throw IllegalStateException(obj.optString("error", "Audio resolve failed"))
            val stream = obj.optString("streamUrl")
            if (stream.isBlank()) throw IllegalStateException("Bridge did not return an audio URL")
            CatalogResult(track.withStream(stream), true)
        } catch (error: Throwable) {
            CatalogResult(track, false, error.message ?: "Audio resolve failed")
        }
    }

    private fun parseTracks(obj: JSONObject): List<Track> {
        val array = obj.optJSONArray("tracks") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val track = Track.fromJson(item)
                if (track.id.isNotBlank()) add(track)
            }
        }
    }

    private fun getJson(url: String, connectTimeoutMs: Int, readTimeoutMs: Int): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "DeepEcho-Android/0.1")
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (text.isBlank()) throw IllegalStateException("Bridge returned HTTP $code")
            JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        val demoTracks = listOf(
            Track(
                id = "deepecho-demo-1",
                title = "DeepEcho Pulse",
                artist = "Alpha Demo",
                durationMs = 372_000,
                sourceUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                lyrics = listOf(
                    LyricLine(0, "DEEP-ECHO Android Alpha"),
                    LyricLine(6_000, "Playback first. Mobile native."),
                    LyricLine(12_000, "Theme reactive lyrics are ready."),
                    LyricLine(18_000, "Connect the desktop bridge for full search testing.")
                )
            ),
            Track(
                id = "deepecho-demo-2",
                title = "Molten Dragon",
                artist = "Theme Test",
                durationMs = 356_000,
                sourceUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                lyrics = listOf(
                    LyricLine(0, "Molten Dragon mobile theme"),
                    LyricLine(7_000, "Ember pulse follows the player"),
                    LyricLine(14_000, "Floating lyrics can stay above other apps")
                )
            ),
            Track(
                id = "deepecho-demo-3",
                title = "Love RGB",
                artist = "Theme Test",
                durationMs = 343_000,
                sourceUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
                lyrics = listOf(
                    LyricLine(0, "Love RGB"),
                    LyricLine(5_000, "Hearts move softly in the background"),
                    LyricLine(11_000, "No ads. DeepEcho identity stays intact.")
                )
            )
        )
    }
}
