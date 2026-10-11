package com.deepecho.mobile.lyrics

import com.deepecho.mobile.data.Song
import com.deepecho.mobile.net.LyricLine
import com.deepecho.mobile.net.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsFastPathV11213Test {
    private fun song(
        title: String = "Husn",
        artist: String = "Anuv Jain",
        duration: Long = 217L
    ) = Song("https://www.youtube.com/watch?v=abcdefghijk", title, artist, duration, null)

    private fun candidate(song: Song, exact: Boolean = true, requiresAudio: Boolean = false): LyricsCandidate {
        val meta = LyricsMetadata.normalize(song)
        val score = LyricsMetadata.scoreCandidate(meta, meta.coreTitle, song.artist, song.durationSec.toDouble(), true)
        return LyricsCandidate(
            provider = "LRCLIB",
            title = meta.coreTitle,
            artist = song.artist,
            album = null,
            durationSec = song.durationSec.toDouble(),
            lyrics = Lyrics(
                synced = listOf(
                    LyricLine(9_500L, "first sung line"),
                    LyricLine(14_000L, "second sung line")
                ),
                plain = "first sung line\nsecond sung line",
                confidence = score.total,
                metadataConfidence = score.total,
                source = "LRCLIB"
            ),
            score = score,
            exactLookup = exact,
            requiresAudioVerification = requiresAudio
        )
    }

    @Test fun normalStrongSyncedLrclibUsesSameFastTrustAsSearchBadge() {
        val song = song()
        val c = candidate(song)
        assertTrue(LyricsOrchestrator.canUseProviderSyncWithoutAudio(song, c, LyricsMetadata.normalize(song)))
    }

    @Test fun variantNeverUsesProviderTimingShortcut() {
        val song = song(title = "Husn (Slowed + Reverb)")
        val c = candidate(song)
        assertFalse(LyricsOrchestrator.canUseProviderSyncWithoutAudio(song, c, LyricsMetadata.normalize(song)))
    }

    @Test fun currentUploadGroundingRequirementStillBlocksShortcut() {
        val song = song()
        val c = candidate(song, requiresAudio = true)
        assertFalse(LyricsOrchestrator.canUseProviderSyncWithoutAudio(song, c, LyricsMetadata.normalize(song)))
    }

    @Test fun syncedProviderWaitsUntilFirstVocalTimestampWithoutAudioCapture() {
        val lyrics = candidate(song()).lyrics
        LyricsRuntimeSync.clear("fixture")
        assertEquals(-1L, LyricsRuntimeSync.effectivePosition("fixture", 4_000L, lyrics, false, 0f))
        assertEquals(9_500L, LyricsRuntimeSync.effectivePosition("fixture", 9_500L, lyrics, false, 0f))
        LyricsRuntimeSync.clear("fixture")
    }
}
