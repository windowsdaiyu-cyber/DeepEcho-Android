package com.deepecho.mobile.lyrics

import com.deepecho.mobile.data.Song
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveVideoLyricsV125Test {
    private fun song(title: String, url: String = "https://www.youtube.com/watch?v=abcdefghijk") =
        Song(url, title, "Artist", 180L, null)

    @Test fun strictGateAcceptsActualLyricVideoTitles() {
        assertTrue(LyricVideoEligibility.isEligible(song("Track Name (Official Lyric Video)")))
        assertTrue(LyricVideoEligibility.isEligible(song("Track Name - Lyrics")))
        assertTrue(LyricVideoEligibility.isEligible(song("Track Name Lyrical Video")))
    }

    @Test fun strictGateDoesNotRunOnNormalMusicVideos() {
        assertFalse(LyricVideoEligibility.isEligible(song("Track Name (Official Music Video)")))
        assertFalse(LyricVideoEligibility.isEligible(song("Track Name (Official Audio)")))
    }

    @Test fun strictGateRejectsKaraokeCoversAndLocalFiles() {
        assertFalse(LyricVideoEligibility.isEligible(song("Track Name Lyrics Karaoke")))
        assertFalse(LyricVideoEligibility.isEligible(song("Track Name Lyrics Cover")))
        assertFalse(LyricVideoEligibility.isEligible(song("Track Name Lyrics", "/storage/emulated/0/track.mp3")))
    }
}
