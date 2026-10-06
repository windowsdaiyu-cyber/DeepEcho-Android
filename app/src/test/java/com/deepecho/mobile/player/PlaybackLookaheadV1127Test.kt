package com.deepecho.mobile.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackLookaheadV1127Test {
    @Test fun wifiNormalKeepsThreeReadyAndFourWarm() {
        val w = PlaybackLookaheadPolicy.window(dataSaver = false, metered = false, burst = false)
        assertEquals(3, w.ready)
        assertEquals(4, w.warm)
        assertEquals(7, w.total)
    }

    @Test fun wifiBurstStaysAheadOfSevenRapidSkips() {
        val w = PlaybackLookaheadPolicy.window(dataSaver = false, metered = false, burst = true)
        assertEquals(3, w.ready)
        assertEquals(5, w.warm)
        assertTrue(w.total >= 7)
    }

    @Test fun mobileBurstStillKeepsSevenKnownPreparedSlots() {
        val w = PlaybackLookaheadPolicy.window(dataSaver = false, metered = true, burst = true)
        assertEquals(7, w.total)
    }

    @Test fun dataSaverReducesBytesWithoutDisablingLookahead() {
        val w = PlaybackLookaheadPolicy.window(dataSaver = true, metered = true, burst = false)
        assertEquals(1, w.ready)
        assertEquals(3, w.warm)
    }

    @Test fun sixNextCommandsAdvanceExactlySixLogicalPositions() {
        assertEquals(6, PlaybackLookaheadPolicy.targetIndex(0, 6, 9))
    }

    @Test fun rapidSkipNeverRunsPastQueueEnd() {
        assertEquals(8, PlaybackLookaheadPolicy.targetIndex(0, 20, 9))
    }
}
