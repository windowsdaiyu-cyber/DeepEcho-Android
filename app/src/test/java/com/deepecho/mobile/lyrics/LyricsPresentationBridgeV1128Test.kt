package com.deepecho.mobile.lyrics

import com.deepecho.mobile.net.LyricLine
import com.deepecho.mobile.net.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsPresentationBridgeV1128Test {
    @Test fun readsOnlyAlreadyPublishedCurrentLine() {
        val key = "song://v1128"
        LyricsPresentationBridge.publish(
            key,
            Lyrics(
                synced = listOf(
                    LyricLine(1_000L, "first"),
                    LyricLine(2_500L, "second"),
                    LyricLine(4_000L, "third")
                ),
                plain = null
            )
        )
        assertNull(LyricsPresentationBridge.lineAt(key, 500L))
        assertEquals("first", LyricsPresentationBridge.lineAt(key, 1_700L))
        assertEquals("second", LyricsPresentationBridge.lineAt(key, 3_100L))
        assertEquals("third", LyricsPresentationBridge.nextLineAfter(key, 3_100L))
        LyricsPresentationBridge.clear(key)
    }

    @Test fun ignoresAnotherSongsSnapshot() {
        LyricsPresentationBridge.publish("a", Lyrics(listOf(LyricLine(0L, "hello")), null))
        assertNull(LyricsPresentationBridge.lineAt("b", 10_000L))
        LyricsPresentationBridge.clear("a")
    }
}
