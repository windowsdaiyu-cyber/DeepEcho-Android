package com.deepecho.mobile.lyrics

import com.deepecho.mobile.data.Song
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveVideoLyricsV126Test {
    private fun song(title: String, url: String = "https://www.youtube.com/watch?v=abcdefghijk") =
        Song(url, title, "Artist", 180L, null)

    @Test fun lyricVideoAutoStartsOnlyAfterNormalLyricsFail() {
        val lyricVideo = song("General Levy - Dubplate (Lyrics)")
        assertTrue(LyricVideoEligibility.shouldAutoStart(lyricVideo, normalLyricsAvailable = false))
        assertFalse(LyricVideoEligibility.shouldAutoStart(lyricVideo, normalLyricsAvailable = true))
    }

    @Test fun normalMusicVideoNeverAutoStartsVideoOcr() {
        val normal = song("Track Name (Official Music Video)")
        assertFalse(LyricVideoEligibility.shouldAutoStart(normal, normalLyricsAvailable = false))
    }
}
