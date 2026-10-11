package com.deepecho.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkQualityV11210Test {
    @Test fun youtubeArtworkPromotesMaxResolutionWithFallbacks() {
        val source = "https://i.ytimg.com/vi/abc123/hqdefault.jpg"
        val c = ArtworkQuality.candidates(source)
        assertEquals("https://i.ytimg.com/vi/abc123/maxresdefault.jpg", c[0])
        assertEquals("https://i.ytimg.com/vi/abc123/sddefault.jpg", c[1])
        assertEquals(source, c.last())
    }

    @Test fun googleArtworkPromotesServerSideSize() {
        val source = "https://lh3.googleusercontent.com/example=w544-h544-l90-rj"
        val c = ArtworkQuality.candidates(source)
        assertTrue(c.first().contains("=w1200-h1200"))
        assertEquals(source, c.last())
    }

    @Test fun dataSaverLeavesArtworkUrlUntouched() {
        val source = "https://i.ytimg.com/vi/abc123/hqdefault.jpg"
        assertEquals(listOf(source), ArtworkQuality.candidates(source, dataSaver = true))
        assertEquals(720, ArtworkQuality.decodeSizePx(dataSaver = true))
        assertEquals(1440, ArtworkQuality.decodeSizePx(dataSaver = false))
    }
}
