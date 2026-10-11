package com.deepecho.mobile.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueVisibilityV11214Test {
    @Test fun currentSongMayRemainVisibleAsContext() {
        assertTrue(QueueVisibilityPolicyV11214.shouldShow(3, 3, false))
    }

    @Test fun autoplayAndPlaylistUpcomingAreHiddenFromManualQueue() {
        assertFalse(QueueVisibilityPolicyV11214.shouldShow(4, 3, false))
    }

    @Test fun manuallyQueuedUpcomingSongIsVisible() {
        assertTrue(QueueVisibilityPolicyV11214.shouldShow(4, 3, true))
    }

    @Test fun playedHistoryNeverAppearsInQueue() {
        assertFalse(QueueVisibilityPolicyV11214.shouldShow(2, 3, true))
    }

    @Test fun autoplayStaysSmallAndOnlyOneTrackIsPrewarmed() {
        assertEquals(4, QueueVisibilityPolicyV11214.autoplayCandidateLimit())
        assertEquals(1, QueueVisibilityPolicyV11214.autoplayPrewarmLimit())
    }
}
