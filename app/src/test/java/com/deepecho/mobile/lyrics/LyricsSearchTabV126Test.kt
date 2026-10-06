package com.deepecho.mobile.lyrics

import com.deepecho.mobile.data.Song
import com.deepecho.mobile.net.LyricLine
import com.deepecho.mobile.net.LyricWord
import com.deepecho.mobile.net.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsSearchTabV126Test {
    private fun song(
        title: String = "Husn",
        artist: String = "Anuv Jain",
        duration: Long = 210L,
        id: String = "abcdefghijk"
    ) = Song("https://www.youtube.com/watch?v=$id", title, artist, duration, null)

    private fun candidate(
        song: Song,
        wordSync: Boolean = false,
        estimated: Boolean = false,
        suspicious: Boolean = false,
        requiresAudio: Boolean = false,
        exact: Boolean = true,
        providerDuration: Double = song.durationSec.toDouble(),
        providerArtist: String = song.artist
    ): LyricsCandidate {
        val meta = LyricsMetadata.normalize(song)
        val lines = listOf(
            LyricLine(10_000, "first lyric line", if (wordSync) listOf(LyricWord(10_000, "first")) else emptyList()),
            LyricLine(15_000, "second lyric line", if (wordSync) listOf(LyricWord(15_000, "second")) else emptyList())
        )
        val score = LyricsMetadata.scoreCandidate(meta, meta.coreTitle, providerArtist, providerDuration, true)
        return LyricsCandidate(
            provider = "LRCLIB",
            title = meta.coreTitle,
            artist = providerArtist,
            album = null,
            durationSec = providerDuration,
            lyrics = Lyrics(
                synced = lines,
                plain = "first lyric line\nsecond lyric line",
                estimatedSync = estimated,
                confidence = score.total,
                source = "LRCLIB",
                metadataConfidence = score.total
            ),
            score = score.copy(suspicious = suspicious),
            exactLookup = exact,
            id = "fixture",
            providerConfidence = 95,
            requiresAudioVerification = requiresAudio
        )
    }

    @Test fun strongExactProviderBecomesSyncedLyricsReady() {
        val song = song()
        val result = LyricsSearchAvailabilityIndex.describeProviderCandidate(
            song, LyricsMetadata.normalize(song), candidate(song)
        )
        assertNotNull(result)
        assertEquals(LyricsAvailabilityState.VERIFIED_SYNCED, result!!.descriptor.availabilityState)
        assertEquals(song.url, result.descriptor.playbackSourceIdentity)
    }

    @Test fun genuineWordTimingGetsWordSyncBadge() {
        val song = song()
        val result = LyricsSearchAvailabilityIndex.describeProviderCandidate(
            song, LyricsMetadata.normalize(song), candidate(song, wordSync = true)
        )
        assertNotNull(result)
        assertEquals(LyricsAvailabilityState.VERIFIED_WORD_SYNC, result!!.descriptor.availabilityState)
        assertEquals("WORD SYNC", result.descriptor.badge)
    }

    @Test fun estimatedProviderTimelineIsTextOnlyNotFakeSynced() {
        val song = song()
        val result = LyricsSearchAvailabilityIndex.describeProviderCandidate(
            song, LyricsMetadata.normalize(song), candidate(song, estimated = true)
        )
        assertNotNull(result)
        assertEquals(LyricsAvailabilityState.VERIFIED_TEXT, result!!.descriptor.availabilityState)
        assertFalse(result.descriptor.hasLineTiming)
    }

    @Test fun titleContainingLyricsDoesNotMakeUnverifiedResultReady() {
        val lyricVideo = song(title = "Husn Official Lyrics Video")
        val suspicious = candidate(lyricVideo, suspicious = true)
        assertNull(
            LyricsSearchAvailabilityIndex.describeProviderCandidate(
                lyricVideo, LyricsMetadata.normalize(lyricVideo), suspicious
            )
        )
    }


    @Test fun wrongSameTitleDifferentPerformerIsRejected() {
        val wanted = song(title = "Husn", artist = "Singer A")
        val wrong = candidate(wanted, providerArtist = "Singer B")
        val result = LyricsSearchAvailabilityIndex.describeProviderCandidate(
            wanted, LyricsMetadata.normalize(wanted), wrong
        )
        assertNull(result)
    }

    @Test fun coverNeverImportsProviderTimingWithoutCurrentAudioGrounding() {
        val cover = song(title = "Husn (Cover)", artist = "Cover Singer")
        val c = candidate(cover, requiresAudio = true)
        val result = LyricsSearchAvailabilityIndex.describeProviderCandidate(
            cover, LyricsMetadata.normalize(cover), c
        )
        assertNull(result)
    }

    @Test fun differentPlaybackVersionsRemainDistinct() {
        val audio = song(id = "abcdefghijk")
        val video = song(title = "Husn Official Video", duration = 235L, id = "lmnopqrstuv")
        val r1 = LyricsSearchAvailabilityIndex.describeProviderCandidate(
            audio, LyricsMetadata.normalize(audio), candidate(audio)
        )!!
        // Official-video qualifier prevents borrowed provider timing until current-upload grounding.
        val r2 = LyricsSearchAvailabilityIndex.describeProviderCandidate(
            video, LyricsMetadata.normalize(video), candidate(video, providerDuration = 210.0)
        )
        assertNotNull(r1)
        assertNull(r2)
        assertTrue(audio.url != video.url)
    }

    @Test fun rankingPrefersWordSyncOverPlainForSameQueryQuality() {
        val s1 = song(id = "abcdefghijk")
        val s2 = song(id = "lmnopqrstuv")
        val word = LyricsSearchAvailabilityIndex.describeProviderCandidate(
            s1, LyricsMetadata.normalize(s1), candidate(s1, wordSync = true)
        )!!
        val plainCandidate = candidate(s2, estimated = true)
        val plain = LyricsSearchAvailabilityIndex.describeProviderCandidate(
            s2, LyricsMetadata.normalize(s2), plainCandidate
        )!!
        val ranked = LyricsSearchAvailabilityIndex.rankForQuery(listOf(plain, word), "Husn")
        assertEquals(word.song.url, ranked.first().song.url)
    }
}
