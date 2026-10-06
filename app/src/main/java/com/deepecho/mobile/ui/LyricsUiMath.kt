package com.deepecho.mobile.ui

/** Pure geometry helpers for lyrics presentation. No timing/audio logic lives here. */
object LyricsUiMath {
    /** Distance in pixels required to move an item center onto the viewport center. */
    fun centerScrollDelta(
        viewportStart: Int,
        viewportEnd: Int,
        itemOffset: Int,
        itemSize: Int
    ): Int {
        val viewportCenter = viewportStart + (viewportEnd - viewportStart) / 2
        val itemCenter = itemOffset + itemSize / 2
        return itemCenter - viewportCenter
    }
}
