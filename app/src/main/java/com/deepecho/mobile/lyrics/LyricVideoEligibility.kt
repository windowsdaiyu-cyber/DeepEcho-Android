package com.deepecho.mobile.lyrics

import com.deepecho.mobile.data.Song

/**
 * Strict gate for the optional live video-lyrics reader.
 * Heavy caption/OCR work is never started for a normal music video.
 */
object LyricVideoEligibility {
    private val lyricHint = Regex(
        "(?i)(?:\\blyrics?\\b|\\blyrical\\b|\\blyric\\s+video\\b|\\bwith\\s+lyrics\\b|\\bofficial\\s+lyrics?\\b)"
    )
    private val reject = Regex(
        "(?i)\\b(karaoke|reaction|cover|instrumental|tutorial|translation|translated|piano\\s+cover|guitar\\s+cover|8d\\s+audio)\\b"
    )

    fun isEligible(song: Song): Boolean {
        if (!song.url.startsWith("http", ignoreCase = true)) return false
        val title = song.title.trim()
        if (title.isBlank() || reject.containsMatchIn(title)) return false
        return lyricHint.containsMatchIn(title)
    }

    /**
     * v1.12.6 safety rule: auto-start the lightweight live reader only when the item is
     * definitely a lyric video AND the normal DeepEcho lyrics path has already failed.
     * Existing good lyrics never trigger video OCR automatically.
     */
    fun shouldAutoStart(song: Song, normalLyricsAvailable: Boolean): Boolean =
        !normalLyricsAvailable && isEligible(song)
}
