package com.deepecho.mobile.player

/** v1.12.14 manual Queue contract. Smart Autoplay stays hidden from the Queue UI. */
object QueueVisibilityPolicyV11214 {
    fun shouldShow(index: Int, currentIndex: Int, manuallyQueued: Boolean): Boolean {
        if (index == currentIndex) return true
        if (index < currentIndex) return false
        return manuallyQueued
    }

    fun autoplayCandidateLimit(): Int = 4
    fun autoplayPrewarmLimit(): Int = 1
}
