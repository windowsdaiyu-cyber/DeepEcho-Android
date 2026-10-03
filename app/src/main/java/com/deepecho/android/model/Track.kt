package com.deepecho.android.model

import org.json.JSONArray
import org.json.JSONObject

data class LyricLine(
    val startMs: Long,
    val text: String
)

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long = 0L,
    val thumbnail: String = "",
    val sourceUrl: String = "",
    val streamUrl: String = "",
    val lyrics: List<LyricLine> = emptyList(),
    val downloaded: Boolean = false
) {
    fun withStream(url: String) = copy(streamUrl = url)

    companion object {
        fun fromJson(obj: JSONObject): Track {
            val lyricArray = obj.optJSONArray("lyrics") ?: JSONArray()
            val lyricLines = buildList {
                for (i in 0 until lyricArray.length()) {
                    val line = lyricArray.optJSONObject(i) ?: continue
                    val text = line.optString("text").trim()
                    if (text.isNotEmpty()) add(LyricLine(line.optLong("startMs"), text))
                }
            }
            return Track(
                id = obj.optString("id"),
                title = obj.optString("title", "Untitled"),
                artist = obj.optString("artist", obj.optString("channel", "Unknown artist")),
                durationMs = obj.optLong("durationMs", parseDuration(obj.optString("duration"))),
                thumbnail = obj.optString("thumbnail"),
                sourceUrl = obj.optString("sourceUrl", obj.optString("url")),
                streamUrl = obj.optString("streamUrl"),
                lyrics = lyricLines
            )
        }

        private fun parseDuration(raw: String): Long {
            val parts = raw.split(':').mapNotNull { it.toLongOrNull() }
            if (parts.isEmpty()) return 0L
            val seconds = parts.fold(0L) { acc, value -> acc * 60L + value }
            return seconds * 1000L
        }
    }
}
