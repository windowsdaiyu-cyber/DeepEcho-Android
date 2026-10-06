package com.deepecho.mobile.lyrics

import com.deepecho.mobile.net.LyricLine
import com.deepecho.mobile.net.Lyrics
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingLyricsSyncV126Test {
    private fun sample(text: String) = Lyrics(
        synced = listOf(LyricLine(1_000L, text)),
        plain = text,
        source = "TEST",
        verification = "provider_verified",
        verified = true
    )

    @Test
    fun floatingOverlayNeverActivatesVisualCaptureAsTimingAuthority() {
        assertFalse(FloatingLyricsSyncPolicy.timingCaptureEnabled(overlayEnabled = true, realCapture = true))
        assertFalse(FloatingLyricsSyncPolicy.timingCaptureEnabled(overlayEnabled = true, realCapture = false))
        assertTrue(FloatingLyricsSyncPolicy.timingCaptureEnabled(overlayEnabled = false, realCapture = true))
        assertFalse(FloatingLyricsSyncPolicy.timingCaptureEnabled(overlayEnabled = false, realCapture = false))
    }

    @Test
    fun bridgeReturnsExactMainLyricsPayloadForSameSongOnly() {
        val lyrics = sample("same resolved line")
        LyricsPresentationBridge.publish("song-A", lyrics)
        assertSame(lyrics, LyricsPresentationBridge.current("song-A"))
        assertNull(LyricsPresentationBridge.current("song-B"))
    }

    @Test
    fun bridgeReplacesLyricsImmediatelyForSameSongRecovery() {
        val old = sample("old")
        val recovered = sample("recovered")
        LyricsPresentationBridge.publish("song-C", old)
        LyricsPresentationBridge.publish("song-C", recovered)
        assertSame(recovered, LyricsPresentationBridge.current("song-C"))
    }
}
