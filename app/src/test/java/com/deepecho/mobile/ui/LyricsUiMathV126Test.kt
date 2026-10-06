package com.deepecho.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsUiMathV126Test {
    @Test fun activeLineBelowCenterScrollsForward() {
        assertEquals(260, LyricsUiMath.centerScrollDelta(0, 1000, 720, 80))
    }

    @Test fun activeLineAboveCenterScrollsBackward() {
        assertEquals(-260, LyricsUiMath.centerScrollDelta(0, 1000, 200, 80))
    }

    @Test fun centeredLineNeedsNoMovement() {
        assertEquals(0, LyricsUiMath.centerScrollDelta(0, 1000, 460, 80))
    }
}
