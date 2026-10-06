package com.deepecho.mobile.lyrics

import com.deepecho.mobile.data.Song
import com.deepecho.mobile.net.LyricLine
import com.deepecho.mobile.net.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiProviderLyricsV126Test {
    private fun song(title: String, artist: String = "Cover Singer", duration: Long = 180L) =
        Song("https://www.youtube.com/watch?v=abcdefghijk", title, artist, duration, null)

    @Test fun ttmlParserNormalizesLineAndWordTiming() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml"><body><div>
              <p begin="00:00:10.000" end="00:00:13.000">
                <span begin="00:00:10.000" end="00:00:10.500">hello </span>
                <span begin="00:00:10.600" end="00:00:11.100">world</span>
              </p>
              <p begin="15.250s" end="17.000s">second line</p>
            </div></body></tt>
        """.trimIndent()
        val lines = LyricsFormatParsers.parseTtml(ttml)
        assertEquals(2, lines.size)
        assertEquals(10_000L, lines[0].timeMs)
        assertEquals("hello world", lines[0].text)
        assertEquals(listOf(10_000L, 10_600L), lines[0].words.map { it.timeMs })
        assertEquals(15_250L, lines[1].timeMs)
    }

    @Test fun qrcParserNormalizesWordTiming() {
        val qrc = """<?xml version="1.0"?><QrcInfos><LyricInfo><Lyric_1 LyricType="1" LyricContent="[ti:Example]&#10;[10000,1800](10000,400)first (10400,500)real (10900,700)line&#10;[14000,1500](14000,500)next (14500,600)line"/></LyricInfo></QrcInfos>"""
        val lines = LyricsFormatParsers.parseQrc(qrc)
        assertEquals(2, lines.size)
        assertEquals(10_000L, lines.first().timeMs)
        assertEquals("first real line", lines.first().text)
        assertEquals(listOf(10_000L, 10_400L, 10_900L), lines.first().words.map { it.timeMs })
    }

    @Test fun coverByPerformerIsCapturedWithoutPollutingCoreTitle() {
        val meta = LyricsMetadata.normalize(song("Example Song (Cover) Cover by New Singer", artist = "Uploader Channel"))
        assertTrue("cover" in meta.qualifiers)
        assertTrue(meta.performerCandidates.any { it.equals("New Singer", true) })
        assertFalse(meta.coreTitle.contains("cover by", true))
    }

    @Test fun crossProviderConsensusCanRelaxNormalTrackButNeverCover() {
        fun candidate(provider: String, meta: NormalizedTrackMetadata) = LyricsCandidate(
            provider = provider,
            title = meta.coreTitle,
            artist = meta.performerCandidates.first(),
            album = null,
            durationSec = meta.durationSec.toDouble(),
            lyrics = Lyrics(
                synced = listOf(LyricLine(10_000, "same lyric line"), LyricLine(15_000, "another lyric line")),
                plain = "same lyric line\nanother lyric line",
                source = provider
            ),
            score = LyricsMetadata.scoreCandidate(meta, meta.coreTitle, meta.performerCandidates.first(), meta.durationSec.toDouble(), true),
            providerConfidence = 84,
            requiresAudioVerification = true
        )

        val normalMeta = LyricsMetadata.normalize(song("Example Song", "Singer"))
        val normal = OpenLyricsProviderResolver.rankAndApplyConsensus(
            listOf(candidate("KuGou", normalMeta), candidate("QQ", normalMeta)), normalMeta
        )
        assertTrue(normal.isNotEmpty())
        assertFalse(normal.first().requiresAudioVerification)

        val coverMeta = LyricsMetadata.normalize(song("Example Song (Cover)", "Cover Singer"))
        val cover = OpenLyricsProviderResolver.rankAndApplyConsensus(
            listOf(candidate("KuGou", coverMeta), candidate("QQ", coverMeta)), coverMeta
        )
        assertTrue(cover.all { it.requiresAudioVerification })
    }

    @Test fun originalTextMayBecomeCoverReferenceOnlyWhenActualCaptionsAgree() {
        val meta = LyricsMetadata.normalize(song("Example Song (Cover)", "Cover Singer"))
        val originalLyrics = Lyrics(
            synced = listOf(
                LyricLine(5_000, "we stand together tonight"),
                LyricLine(10_000, "under the same bright sky")
            ),
            plain = "we stand together tonight\nunder the same bright sky",
            source = "Original provider"
        )
        val score = LyricsMetadata.scoreCandidate(meta, "Example Song", "Original Singer", 180.0, true)
        val candidate = LyricsCandidate(
            provider = "LRCLIB",
            title = "Example Song",
            artist = "Original Singer",
            album = null,
            durationSec = 180.0,
            lyrics = originalLyrics,
            score = score,
            providerConfidence = 95,
            requiresAudioVerification = true
        )
        val matchingCaps = listOf(
            CaptionLine(20_000, 22_000, "we stand together tonight", "en", true),
            CaptionLine(25_000, 27_000, "under the same bright sky", "en", true)
        )
        val authority = OpenLyricsProviderResolver.selectTextAuthority(listOf(candidate), meta, matchingCaps)
        assertNotNull(authority)

        val wrongCaps = listOf(
            CaptionLine(20_000, 22_000, "completely different words here", "en", true),
            CaptionLine(25_000, 27_000, "nothing matches this chorus", "en", true)
        )
        assertNull(OpenLyricsProviderResolver.selectTextAuthority(listOf(candidate), meta, wrongCaps))
    }
}
