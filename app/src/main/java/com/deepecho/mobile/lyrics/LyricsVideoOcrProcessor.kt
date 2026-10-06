package com.deepecho.mobile.lyrics

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max

/**
 * Bounded, manual-only on-device OCR for lyric videos.
 * It samples roughly once per second, skips visually unchanged lyric regions and caps OCR work.
 */
object LyricsVideoOcrProcessor {
    private const val BASE_STEP_MS = 1_050L
    private const val MAX_OCR_FRAMES = 90
    private const val MAX_VIDEO_MS = 12 * 60_000L

    fun extract(
        directVideoUrl: String,
        expectedDurationMs: Long,
        isCancelled: () -> Boolean
    ): List<CaptionLine> {
        if (directVideoUrl.isBlank() || isCancelled()) return emptyList()
        val retriever = MediaMetadataRetriever()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val raw = ArrayList<OcrLyricsCleaner.RawLine>()
        try {
            retriever.setDataSource(
                directVideoUrl,
                mapOf("User-Agent" to com.deepecho.mobile.net.Net.UA)
            )
            val mediaDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.coerceAtLeast(0L) ?: expectedDurationMs
            val duration = when {
                mediaDuration > 0L && expectedDurationMs > 0L -> minOf(mediaDuration, max(expectedDurationMs + 45_000L, expectedDurationMs * 13 / 10))
                mediaDuration > 0L -> mediaDuration
                else -> expectedDurationMs
            }.coerceIn(1_000L, MAX_VIDEO_MS)

            var lastSignature: FloatArray? = null
            var lastOcrAt = -10_000L
            var ocrFrames = 0
            var t = 0L
            while (t <= duration && ocrFrames < MAX_OCR_FRAMES && !isCancelled()) {
                val frame = runCatching {
                    retriever.getFrameAtTime(t * 1_000L, MediaMetadataRetriever.OPTION_CLOSEST)
                }.getOrNull()
                if (frame != null) {
                    val work = downscale(frame)
                    if (work !== frame) frame.recycle()
                    val signature = signature(work)
                    val changed = lastSignature == null || signatureDistance(lastSignature!!, signature) >= 0.075f
                    val refresh = t - lastOcrAt >= 6_500L
                    if (changed || refresh) {
                        val result = runCatching {
                            Tasks.await(recognizer.process(InputImage.fromBitmap(work, 0)), 8, TimeUnit.SECONDS)
                        }.getOrNull()
                        if (result != null) {
                            for (block in result.textBlocks) {
                                for (line in block.lines) {
                                    val box = line.boundingBox ?: continue
                                    if (box.height() <= 0 || box.width() <= 0) continue
                                    val y = ((box.top + box.bottom) / 2f / work.height.toFloat()).coerceIn(0f, 1f)
                                    if (y < 0.045f || y > 0.955f) continue
                                    val txt = line.text.trim()
                                    if (txt.isBlank()) continue
                                    raw += OcrLyricsCleaner.RawLine(
                                        startMs = t,
                                        endMs = minOf(duration, t + BASE_STEP_MS * 2),
                                        text = txt,
                                        centerYRatio = y,
                                        confidence = 70
                                    )
                                }
                            }
                            ocrFrames++
                            lastOcrAt = t
                        }
                    }
                    lastSignature = signature
                    work.recycle()
                }
                t += BASE_STEP_MS
            }
        } catch (_: Throwable) {
            return emptyList()
        } finally {
            runCatching { recognizer.close() }
            runCatching { retriever.release() }
        }
        if (isCancelled()) return emptyList()
        return OcrLyricsCleaner.clean(raw)
    }


    /**
     * Progressive reader for the CURRENT lyric video. Unlike Exact Lyrics recovery, timestamps here
     * already belong to the playing video, so results can be surfaced as they are discovered.
     * The sampling order prioritizes the current line and a short look-ahead window for fast UI sync.
     */
    fun extractLive(
        directVideoUrl: String,
        expectedDurationMs: Long,
        positionProvider: () -> Long,
        isCancelled: () -> Boolean,
        onUpdate: (List<CaptionLine>) -> Unit
    ) {
        if (directVideoUrl.isBlank() || isCancelled()) return
        val retriever = MediaMetadataRetriever()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val raw = ArrayList<OcrLyricsCleaner.RawLine>()
        val scannedBuckets = HashSet<Long>()
        var ocrFrames = 0
        var lastSignature: FloatArray? = null
        var lastOcrAt = -10_000L
        try {
            retriever.setDataSource(
                directVideoUrl,
                mapOf("User-Agent" to com.deepecho.mobile.net.Net.UA)
            )
            val mediaDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.coerceAtLeast(0L) ?: expectedDurationMs
            val duration = when {
                mediaDuration > 0L && expectedDurationMs > 0L -> minOf(mediaDuration, max(expectedDurationMs + 30_000L, expectedDurationMs * 12 / 10))
                mediaDuration > 0L -> mediaDuration
                else -> expectedDurationMs
            }.coerceIn(1_000L, MAX_VIDEO_MS)

            fun bucket(t: Long): Long = (t.coerceIn(0L, duration) / 320L) * 320L
            fun nextSample(): Long? {
                val now = positionProvider().coerceIn(0L, duration)
                // v1.12.6: much denser near-now sampling for fast rap/ragga lyric videos.
                // The atomic playback position is re-read every frame, so the worker follows playback
                // instead of blindly finishing a stale long look-ahead batch.
                val offsets = longArrayOf(
                    -960L, -640L, -320L, 0L, 320L, 640L, 960L, 1_280L,
                    1_700L, 2_200L, 2_900L, 3_800L, 5_000L, 6_500L,
                    8_500L, 11_000L, 14_500L, 19_000L
                )
                for (off in offsets) {
                    val b = bucket(now + off)
                    if (scannedBuckets.add(b)) return b
                }
                return null
            }

            while (!isCancelled() && ocrFrames < 260) {
                val t = nextSample()
                if (t == null) {
                    // No unscanned priority point right now. Playback/seek may expose a new window soon.
                    if (positionProvider() >= duration - 1_000L) break
                    try { Thread.sleep(140L) } catch (_: InterruptedException) { break }
                    continue
                }
                val frame = runCatching {
                    retriever.getFrameAtTime(t * 1_000L, MediaMetadataRetriever.OPTION_CLOSEST)
                }.getOrNull() ?: continue
                val work = downscale(frame)
                if (work !== frame) frame.recycle()
                val sig = signature(work)
                val changed = lastSignature == null || signatureDistance(lastSignature!!, sig) >= 0.024f
                val refresh = kotlin.math.abs(t - lastOcrAt) >= 2_400L
                if (changed || refresh) {
                    val result = runCatching {
                        Tasks.await(recognizer.process(InputImage.fromBitmap(work, 0)), 6, TimeUnit.SECONDS)
                    }.getOrNull()
                    if (result != null) {
                        for (block in result.textBlocks) {
                            for (line in block.lines) {
                                val box = line.boundingBox ?: continue
                                if (box.height() <= 0 || box.width() <= 0) continue
                                val y = ((box.top + box.bottom) / 2f / work.height.toFloat()).coerceIn(0f, 1f)
                                // Keep the broad lyric area; OcrLyricsCleaner removes persistent edge noise/watermarks.
                                if (y < 0.055f || y > 0.945f) continue
                                val txt = line.text.trim()
                                if (txt.isBlank()) continue
                                raw += OcrLyricsCleaner.RawLine(
                                    startMs = t,
                                    endMs = minOf(duration, t + 1_450L),
                                    text = txt,
                                    centerYRatio = y,
                                    confidence = 72
                                )
                            }
                        }
                        ocrFrames++
                        lastOcrAt = t
                        val cleaned = OcrLyricsCleaner.clean(raw)
                        if (cleaned.size >= 2) onUpdate(cleaned)
                    }
                }
                lastSignature = sig
                work.recycle()
            }
        } catch (_: Throwable) {
            // Live video lyrics are optional. Never propagate an OCR failure into playback/normal lyrics.
        } finally {
            runCatching { recognizer.close() }
            runCatching { retriever.release() }
        }
    }

    private fun downscale(bitmap: Bitmap): Bitmap {
        val maxSide = max(bitmap.width, bitmap.height)
        if (maxSide <= 1280) return bitmap
        val scale = 1280f / maxSide.toFloat()
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    /** Low-cost visual signature focused on the central 84% where lyric text normally appears. */
    private fun signature(bitmap: Bitmap): FloatArray {
        val cols = 18
        val rows = 14
        val out = FloatArray(cols * rows)
        val top = (bitmap.height * 0.08f).toInt()
        val bottom = (bitmap.height * 0.92f).toInt().coerceAtLeast(top + 1)
        var k = 0
        for (ry in 0 until rows) {
            val y = top + ((bottom - top - 1) * (ry + 0.5f) / rows).toInt()
            for (cx in 0 until cols) {
                val x = ((bitmap.width - 1) * (cx + 0.5f) / cols).toInt()
                val c = bitmap.getPixel(x.coerceIn(0, bitmap.width - 1), y.coerceIn(0, bitmap.height - 1))
                val r = (c shr 16) and 0xff
                val g = (c shr 8) and 0xff
                val b = c and 0xff
                out[k++] = (r * 0.299f + g * 0.587f + b * 0.114f) / 255f
            }
        }
        return out
    }

    private fun signatureDistance(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 1f
        var total = 0f
        for (i in a.indices) total += abs(a[i] - b[i])
        return total / a.size
    }
}
