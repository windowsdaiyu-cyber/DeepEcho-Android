package com.deepecho.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerModesV11211Test {
    @Test fun visualizerHiddenAndAmbientVisibleAsBoxedEntry() {
        assertEquals(
            listOf(
                PlayerModeV1128.Artwork,
                PlayerModeV1128.Lyrics,
                PlayerModeV1128.Minimal,
                PlayerModeV1128.Ambient
            ),
            VisiblePlayerModesV11211
        )
        assertFalse(VisiblePlayerModesV11211.contains(PlayerModeV1128.Visualizer))
        assertTrue(VisiblePlayerModesV11211.contains(PlayerModeV1128.Ambient))
    }
}
