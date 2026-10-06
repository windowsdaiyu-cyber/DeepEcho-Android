package com.deepecho.mobile.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualVideoOcrRecoveryV124Test {
    @Test fun ocrCleanerDeduplicatesPersistentLyricFrames() {
        val cleaned = OcrLyricsCleaner.clean(listOf(
            OcrLyricsCleaner.RawLine(10_000, 11_100, "we stand on zameen", 0.55f),
            OcrLyricsCleaner.RawLine(11_000, 12_100, "we stand on zameen", 0.55f),
            OcrLyricsCleaner.RawLine(14_000, 15_100, "under the same sky", 0.55f)
        ))
        assertEquals(2, cleaned.size)
        assertEquals("we stand on zameen", cleaned.first().text)
        assertTrue(cleaned.first().endMs >= 12_100L)
    }

    @Test fun ocrCleanerRejectsStationaryCornerWatermark() {
        val input = mutableListOf<OcrLyricsCleaner.RawLine>()
        repeat(6) { i -> input += OcrLyricsCleaner.RawLine(i * 7_000L, i * 7_000L + 1_000L, "@channelname", 0.04f) }
        input += OcrLyricsCleaner.RawLine(10_000, 12_000, "first real lyric line", 0.55f)
        input += OcrLyricsCleaner.RawLine(15_000, 17_000, "second real lyric line", 0.55f)
        val cleaned = OcrLyricsCleaner.clean(input)
        assertFalse(cleaned.any { it.text.contains("channelname") })
        assertTrue(cleaned.any { it.text.contains("first real") })
    }

    @Test fun ocrCleanerDoesNotApplyDangerousSpellingReplacement() {
        val cleaned = OcrLyricsCleaner.clean(listOf(
            OcrLyricsCleaner.RawLine(1_000, 2_000, "keep calm", 0.5f),
            OcrLyricsCleaner.RawLine(3_000, 4_000, "I am calm", 0.5f)
        ))
        assertTrue(cleaned.all { it.text.contains("calm") })
    }
}
