package com.deepecho.mobile.lyrics

import com.deepecho.mobile.net.LyricLine
import com.deepecho.mobile.net.LyricWord
import com.deepecho.mobile.net.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalLyricsV123TextRepairTest {
    @Test fun textRepairNeverMovesLineOrWordTiming() {
        val input = Lyrics(
            synced = listOf(
                LyricLine(10_000, "main hoon yaham", listOf(
                    LyricWord(10_000, "main", 90, 97),
                    LyricWord(10_500, "hoon", 90, 97),
                    LyricWord(11_000, "yaham", 40, 97)
                )),
                LyricLine(20_000, "main hoon yahan"),
                LyricLine(30_000, "main hoon yahan"),
                LyricLine(40_000, "main hoon yahan")
            ), plain = null, source = "Exact Lyrics • Upload transcription", alignmentConfidence = 90
        )
        val result = LyricsTextRefiner.refine(input)
        assertEquals(input.synced.map { it.timeMs }, result.lyrics.synced.map { it.timeMs })
        assertEquals(input.synced[0].words.map { it.timeMs }, result.lyrics.synced[0].words.map { it.timeMs })
        assertTrue(result.lyrics.synced.first().text.contains("yahan"))
    }

    @Test fun genuineFinalMIsNotGloballyChangedToN() {
        val input = Lyrics(
            synced = listOf(
                LyricLine(1_000, "I am calm"),
                LyricLine(2_000, "keep calm"),
                LyricLine(3_000, "stay calm")
            ), plain = null, source = "test"
        )
        val out = LyricsTextRefiner.refine(input).lyrics
        assertTrue(out.synced.all { it.text.contains("calm") })
        assertFalse(out.synced.any { it.text.contains("caln") })
    }

    @Test fun plannerSeparatesTextAndTimingRepair() {
        assertEquals(LyricsRepairMode.TEXT_REPAIR_ONLY, LyricsRepairPlanner.choose(40, 92, true))
        assertEquals(LyricsRepairMode.TIMING_REPAIR_ONLY, LyricsRepairPlanner.choose(92, 40, true))
        assertEquals(LyricsRepairMode.NONE, LyricsRepairPlanner.choose(92, 92, true))
        assertEquals(LyricsRepairMode.FULL_RECOVERY, LyricsRepairPlanner.choose(30, 30, false))
    }

    @Test fun providerReferenceCanRepairTextWithoutRetiming() {
        val input = Lyrics(
            synced = listOf(LyricLine(5_000, "we stand on zameem"), LyricLine(10_000, "under sky")),
            plain = null, source = "generated", alignmentConfidence = 95
        )
        val out = LyricsTextRefiner.refine(input, "we stand on zameen\nunder sky").lyrics
        assertEquals(5_000L, out.synced[0].timeMs)
        assertTrue(out.synced[0].text.contains("zameen"))
    }

    @Test fun uploadTranscriptionFallbackProducesSyncedLyricsInsteadOfImmediateFailure() {
        val caps = listOf(
            CaptionLine(10_000, 12_000, "first actual vocal line", "en", true),
            CaptionLine(14_000, 16_000, "second actual vocal line", "en", true),
            CaptionLine(18_000, 20_000, "third actual vocal line", "en", true)
        )
        val song = com.deepecho.mobile.data.Song("https://youtube.com/watch?v=abcdefghijk", "Unknown Track", "Unknown Artist", 180, null)
        val recovered = LyricsTranscriptionFallback.recover(song, caps)
        assertTrue(recovered != null)
        assertEquals(10_000L, recovered!!.lyrics.synced.first().timeMs)
        assertTrue(recovered.lyrics.source.contains("Upload transcription"))
    }
}
