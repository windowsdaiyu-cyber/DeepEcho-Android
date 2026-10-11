package com.deepecho.mobile.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSeekV11212Test {
    @Test fun startupSeekThresholdStaysAtProvenV11211Value() {
        assertEquals(320, PlaybackSeekPolicy.START_OR_SEEK_BUFFER_MS)
    }

    @Test fun recentBackBufferIsBounded() {
        assertTrue(PlaybackSeekPolicy.BACK_BUFFER_MS in 10_000..30_000)
    }

    @Test fun seekTargetIsClampedToTrackBounds() {
        assertEquals(0L, PlaybackSeekPolicy.clampTarget(-50L, 180_000L))
        assertEquals(90_000L, PlaybackSeekPolicy.clampTarget(90_000L, 180_000L))
        assertEquals(180_000L, PlaybackSeekPolicy.clampTarget(250_000L, 180_000L))
    }

    @Test fun unknownDurationDoesNotCapForwardSeek() {
        assertEquals(250_000L, PlaybackSeekPolicy.clampTarget(250_000L, -1L))
    }
}
