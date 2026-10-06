package com.deepecho.mobile.lyrics

import com.deepecho.mobile.data.Song
import com.deepecho.mobile.net.LyricLine
import com.deepecho.mobile.net.Lyrics
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class UniversalLyricsV2Test {
    @Test
    fun rapGodStyleExactMetadataRemainsStrong() {
        val song = Song("https://youtube.com/watch?v=abcdefghijk", "Rap God", "Eminem", 363, null)
        val meta = LyricsMetadata.normalize(song)
        val score = LyricsMetadata.scoreCandidate(meta, "Rap God", "Eminem", 363.0, true)
        assertFalse(meta.genericTitle)
        assertFalse(score.suspicious)
        assertTrue(score.total >= 90)
        assertTrue(score.performer >= 90)
    }

    @Test
    fun genericDubplateIsAlwaysSuspiciousUntilAudioVerified() {
        val song = Song("https://youtube.com/watch?v=abcdefghijk", "General Levy - Dubplate", "Little Lion Sound", 180, null)
        val meta = LyricsMetadata.normalize(song)
        val score = LyricsMetadata.scoreCandidate(meta, "Dubplate", "Little Lion Sound", 181.0, true)
        assertTrue(meta.coreTitle.equals("Dubplate", ignoreCase = true))
        assertTrue(meta.performerCandidates.any { it.equals("General Levy", ignoreCase = true) })
        assertTrue(meta.qualifiers == setOf("dubplate"))
        assertTrue(meta.genericTitle)
        assertTrue(score.suspicious)
    }

    @Test
    fun wrongDubplateTextHasPoorCaptionAgreement() {
        val wrong = Lyrics(
            synced = listOf(
                LyricLine(10_000, "no love no love this is another tune"),
                LyricLine(20_000, "different chorus for a different singer")
            ),
            plain = "no love no love this is another tune\ndifferent chorus for a different singer"
        )
        val actual = listOf(
            CaptionLine(10_000, 13_000, "general levy wicked inna jungle massive"),
            CaptionLine(20_000, 23_000, "original dubplate vocal soundboy run")
        )
        val result = LyricsAudioVerifier.verify(wrong, actual)
        assertTrue(result.agreement < 35)
        assertTrue(result.suspicious)
    }

    @Test
    fun instrumentalIntroMovesFirstLyricToActualVocalOnset() {
        val provider = Lyrics(
            synced = listOf(
                LyricLine(5_000, "arz kiya hai"),
                LyricLine(10_000, "next sung line"),
                LyricLine(15_000, "third sung line")
            ),
            plain = null,
            source = "LRCLIB"
        )
        val captions = listOf(
            CaptionLine(13_800, 16_000, "arz kiya hai"),
            CaptionLine(19_000, 21_000, "next sung line"),
            CaptionLine(25_000, 27_000, "third sung line")
        )
        val result = LyricsAlignmentEngine.align(provider, captions)
        assertNotNull(result)
        val first = result!!.lyrics.synced.first().timeMs
        assertTrue(abs(first - 13_800L) <= 500L)
        assertTrue(result.firstVocalOnsetMs == 13_800L)
    }

    @Test
    fun piecewiseAlignmentHandlesInsertedInstrumentalSection() {
        val provider = Lyrics(
            synced = listOf(
                LyricLine(10_000, "line alpha"),
                LyricLine(20_000, "line beta"),
                LyricLine(30_000, "line gamma"),
                LyricLine(40_000, "line delta")
            ),
            plain = null,
            source = "LRCLIB"
        )
        val captions = listOf(
            CaptionLine(12_000, 14_000, "line alpha"),
            CaptionLine(22_000, 24_000, "line beta"),
            CaptionLine(47_000, 49_000, "line gamma"),
            CaptionLine(57_000, 59_000, "line delta")
        )
        val result = LyricsAlignmentEngine.align(provider, captions)!!
        val times = result.lyrics.synced.map { it.timeMs }
        assertTrue(abs(times[0] - 12_000L) < 700L)
        assertTrue(abs(times[1] - 22_000L) < 700L)
        assertTrue(abs(times[2] - 47_000L) < 700L)
        assertTrue(abs(times[3] - 57_000L) < 700L)
    }

    @Test
    fun goodTimingStaysNearOriginal() {
        val provider = Lyrics(
            synced = listOf(
                LyricLine(8_000, "first exact lyric"),
                LyricLine(13_000, "second exact lyric"),
                LyricLine(18_000, "third exact lyric")
            ),
            plain = null,
            source = "LRCLIB"
        )
        val captions = listOf(
            CaptionLine(8_100, 10_000, "first exact lyric"),
            CaptionLine(13_050, 15_000, "second exact lyric"),
            CaptionLine(18_100, 20_000, "third exact lyric")
        )
        val result = LyricsAlignmentEngine.align(provider, captions)!!
        result.lyrics.synced.zip(provider.synced).forEach { (a, b) ->
            assertTrue(abs(a.timeMs - b.timeMs) < 500L)
        }
    }

    @Test
    fun sameTitleDifferentPerformerIsSuspicious() {
        val song = Song("https://youtube.com/watch?v=abcdefghijk", "Stay", "Artist A", 210, null)
        val meta = LyricsMetadata.normalize(song)
        val score = LyricsMetadata.scoreCandidate(meta, "Stay", "Artist B", 210.0, true)
        assertTrue(score.performer < 50)
        assertTrue(score.suspicious)
    }

    @Test
    fun normalSongWithoutVariantKeepsMetadataFastPathEligible() {
        val song = Song("https://youtube.com/watch?v=abcdefghijk", "Blinding Lights", "The Weeknd", 200, null)
        val meta = LyricsMetadata.normalize(song)
        val score = LyricsMetadata.scoreCandidate(meta, "Blinding Lights", "The Weeknd", 200.0, true)
        assertFalse(meta.genericTitle)
        assertTrue(meta.qualifiers.isEmpty())
        assertTrue(score.total >= 90)
        assertFalse(score.suspicious)
    }

    @Test
    fun slowedVariantIsClassifiedInsteadOfDestroyed() {
        val song = Song("https://youtube.com/watch?v=abcdefghijk", "Example Song (Slowed + Reverb)", "Artist", 260, null)
        val meta = LyricsMetadata.normalize(song)
        assertTrue(meta.coreTitle.equals("Example Song", ignoreCase = true))
        assertTrue("slowed + reverb" in meta.qualifiers)
    }

}
