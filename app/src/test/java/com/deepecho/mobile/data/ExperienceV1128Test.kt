package com.deepecho.mobile.data

import org.junit.Assert.assertTrue
import org.junit.Test

class ExperienceV1128Test {
    @Test fun requestedMoodsProduceUsefulRecommendationTerms() {
        listOf("Chill", "Romantic", "Sad", "Energy", "Focus", "Party").forEach { mood ->
            assertTrue("$mood should map to recommendation terms", ExperienceV1128.moodTerms(mood).isNotBlank())
        }
    }

    @Test fun unknownMoodFallsBackWithoutInventingPersistentState() {
        assertTrue(ExperienceV1128.moodTerms("unknown").isBlank())
    }
}
