package com.deepecho.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlayerModesV11210Test {
    @Test fun visualizerAndAmbientAreNotVisiblePlayerTabs() {
        assertEquals(
            listOf(PlayerModeV1128.Artwork, PlayerModeV1128.Lyrics, PlayerModeV1128.Minimal),
            VisiblePlayerModesV11210
        )
        assertFalse(VisiblePlayerModesV11210.contains(PlayerModeV1128.Visualizer))
        assertFalse(VisiblePlayerModesV11210.contains(PlayerModeV1128.Ambient))
    }
}
