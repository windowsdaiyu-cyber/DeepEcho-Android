package com.deepecho.mobile.player

/** Pure policy kept separate so rapid-skip preparation sizing is regression-testable. */
object PlaybackLookaheadPolicy {
    data class Window(val ready: Int, val warm: Int) {
        val total: Int get() = ready + warm
    }

    fun window(dataSaver: Boolean, metered: Boolean, burst: Boolean): Window = when {
        dataSaver && burst -> Window(2, 3)
        dataSaver -> Window(1, 3)
        metered && burst -> Window(3, 4)
        metered -> Window(2, 4)
        burst -> Window(3, 5)
        else -> Window(3, 4)
    }

    /** Logical queue result for N rapid Next commands; never drops or adds an extra command. */
    fun targetIndex(currentIndex: Int, nextPresses: Int, itemCount: Int): Int {
        if (itemCount <= 0) return -1
        return (currentIndex.coerceIn(0, itemCount - 1) + nextPresses.coerceAtLeast(0))
            .coerceAtMost(itemCount - 1)
    }
}
