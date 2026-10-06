package com.deepecho.mobile.data

import android.util.Xml
import com.deepecho.mobile.net.Net
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser

data class PodcastShow(
    val id: Long,
    val title: String,
    val artist: String,
    val artwork: String?,
    val feedUrl: String
)

data class PodcastEpisode(
    val id: String,
    val title: String,
    val showTitle: String,
    val description: String,
    val audioUrl: String,
    val artwork: String?,
    val durationSec: Long,
    val published: String
) {
    fun asSong(): Song = Song(audioUrl, title, "Podcast • $showTitle", durationSec, artwork)
}

object Podcasts {
    private val _followed = MutableStateFlow<List<PodcastShow>>(emptyList())
    private val _progressMs = MutableStateFlow<Map<String, Long>>(emptyMap())
    val followed: StateFlow<List<PodcastShow>> = _followed
    val progressMs: StateFlow<Map<String, Long>> = _progressMs

    fun init() {
        runCatching {
            val raw = Settings.getString("followed_podcasts_json", "")
            if (raw.isNotBlank()) {
                val arr = org.json.JSONArray(raw)
                _followed.value = (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val feed = o.optString("feed").trim()
                    if (feed.isBlank()) return@mapNotNull null
                    PodcastShow(o.optLong("id"), o.optString("title"), o.optString("artist"), o.optString("art").ifBlank { null }, feed)
                }
            }
            val pRaw = Settings.getString("podcast_progress_json", "")
            if (pRaw.isNotBlank()) {
                val o = JSONObject(pRaw)
                val map = linkedMapOf<String, Long>()
                val keys = o.keys()
                while (keys.hasNext()) { val k = keys.next(); map[k] = o.optLong(k, 0L) }
                _progressMs.value = map
            }
        }
    }

    fun toggleFollow(show: PodcastShow) {
        val exists = _followed.value.any { it.feedUrl == show.feedUrl }
        _followed.value = if (exists) _followed.value.filterNot { it.feedUrl == show.feedUrl } else listOf(show) + _followed.value
        val arr = org.json.JSONArray()
        _followed.value.forEach { arr.put(JSONObject().put("id", it.id).put("title", it.title).put("artist", it.artist).put("art", it.artwork ?: "").put("feed", it.feedUrl)) }
        Settings.putString("followed_podcasts_json", arr.toString())
    }

    fun saveProgress(episodeId: String, positionMs: Long) {
        val safe = positionMs.coerceAtLeast(0L)
        _progressMs.value = (_progressMs.value + (episodeId to safe)).entries.sortedByDescending { it.value }.take(300).associate { it.toPair() }
        val o = JSONObject(); _progressMs.value.forEach { (k,v) -> o.put(k,v) }
        Settings.putString("podcast_progress_json", o.toString())
    }

    suspend fun search(term: String): List<PodcastShow> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(term.trim(), StandardCharsets.UTF_8.toString())
        val req = Request.Builder().url("https://itunes.apple.com/search?media=podcast&entity=podcast&limit=20&term=$q")
            .header("User-Agent", Net.UA).build()
        Net.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Podcast search HTTP ${resp.code}")
            val root = JSONObject(resp.body?.string().orEmpty())
            val arr = root.optJSONArray("results") ?: return@withContext emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val feed = o.optString("feedUrl").trim()
                if (feed.isBlank()) return@mapNotNull null
                PodcastShow(
                    id = o.optLong("collectionId", feed.hashCode().toLong()),
                    title = o.optString("collectionName").ifBlank { "Podcast" },
                    artist = o.optString("artistName"),
                    artwork = o.optString("artworkUrl600").ifBlank { o.optString("artworkUrl100") }.ifBlank { null },
                    feedUrl = feed
                )
            }
        }
    }

    suspend fun episodes(show: PodcastShow): List<PodcastEpisode> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(show.feedUrl).header("User-Agent", Net.UA).build()
        Net.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Podcast feed HTTP ${resp.code}")
            val input = resp.body?.byteStream() ?: return@withContext emptyList()
            val parser = Xml.newPullParser().apply { setInput(input, null) }
            val out = mutableListOf<PodcastEpisode>()
            var event = parser.eventType
            var inItem = false
            var title = ""
            var description = ""
            var audio = ""
            var duration = 0L
            var guid = ""
            var published = ""
            var itemArtwork: String? = null
            fun add() {
                if (title.isNotBlank() && audio.isNotBlank()) {
                    out += PodcastEpisode(
                        id = guid.ifBlank { audio }, title = title, showTitle = show.title,
                        description = description, audioUrl = audio, artwork = itemArtwork ?: show.artwork,
                        durationSec = duration, published = published
                    )
                }
            }
            while (event != XmlPullParser.END_DOCUMENT && out.size < 100) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name.lowercase()) {
                        "item", "entry" -> {
                            inItem = true; title = ""; description = ""; audio = ""; duration = 0; guid = ""; published = ""; itemArtwork = null
                        }
                        "title" -> if (inItem) title = parser.nextText().trim()
                        "description", "summary" -> if (inItem) description = parser.nextText().replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
                        "guid", "id" -> if (inItem) guid = parser.nextText().trim()
                        "pubdate", "published", "updated" -> if (inItem) published = parser.nextText().trim()
                        "duration" -> if (inItem) duration = parseDuration(parser.nextText())
                        "enclosure" -> if (inItem) audio = parser.getAttributeValue(null, "url").orEmpty()
                        "content" -> if (inItem && audio.isBlank()) {
                            val url = parser.getAttributeValue(null, "url").orEmpty()
                            val type = parser.getAttributeValue(null, "type").orEmpty()
                            if (type.startsWith("audio/") || url.contains(".mp3", true) || url.contains(".m4a", true)) audio = url
                        }
                        "image" -> if (inItem) {
                            val href = parser.getAttributeValue(null, "href") ?: parser.getAttributeValue(null, "url")
                            if (!href.isNullOrBlank()) itemArtwork = href
                        }
                    }
                } else if (event == XmlPullParser.END_TAG && (parser.name.equals("item", true) || parser.name.equals("entry", true))) {
                    add(); inItem = false
                }
                event = parser.next()
            }
            out
        }
    }

    private fun parseDuration(raw: String): Long {
        val s = raw.trim()
        s.toLongOrNull()?.let { return it }
        val parts = s.split(':').mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> 0L
        }
    }
}
