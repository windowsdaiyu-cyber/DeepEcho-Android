package com.deepecho.mobile.lyrics

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.deepecho.mobile.data.Song
import com.deepecho.mobile.net.LyricLine
import com.deepecho.mobile.net.Lyrics
import com.deepecho.mobile.net.Net
import com.deepecho.mobile.net.YouTubeApi
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Lightweight decoded-audio envelope matcher used only by manual Exact Lyrics recovery when the
 * playing upload has no usable captions. It does not touch PlaybackService or the active player.
 */
object LyricsAudioFingerprintAligner {
    private const val HOP_MS = 200L
    private const val MAX_DECODE_MS = 10 * 60_000L

    private data class Envelope(val values: FloatArray, val durationMs: Long)
    private data class Anchor(val sourceMs: Long, val targetMs: Long, val score: Double)
    private data class Mapping(val scale: Double, val offsetMs: Long, val score: Double, val anchors: List<Anchor>)

    fun align(
        current: Song,
        reference: Song,
        sourceLyrics: Lyrics,
        isCancelled: () -> Boolean
    ): Lyrics? {
        if (isCancelled() || sourceLyrics.synced.size < 2) return null
        val currentUrl = runCatching { YouTubeApi.resolve(current.url).url }.getOrNull() ?: return null
        if (isCancelled()) return null
        val referenceUrl = runCatching { YouTubeApi.resolve(reference.url).url }.getOrNull() ?: return null
        if (isCancelled()) return null

        val sourceEnvelope = extractEnvelope(referenceUrl, reference.durationSec * 1000L, isCancelled) ?: return null
        if (isCancelled()) return null
        val targetEnvelope = extractEnvelope(currentUrl, current.durationSec * 1000L, isCancelled) ?: return null
        if (isCancelled()) return null
        val mapping = findMapping(sourceEnvelope, targetEnvelope, isCancelled) ?: return null
        if (mapping.score < 0.32) return null

        val remapped = sourceLyrics.synced.map { line ->
            val t = mapTime(line.timeMs, mapping).coerceAtLeast(0L)
            val words = line.words.map { word -> word.copy(timeMs = mapTime(word.timeMs, mapping).coerceAtLeast(t)) }
            line.copy(timeMs = t, words = words)
        }.fold(ArrayList<LyricLine>()) { acc, line ->
            val min = (acc.lastOrNull()?.timeMs ?: -81L) + 80L
            acc.apply { add(line.copy(timeMs = maxOf(line.timeMs, min))) }
        }

        val confidence = (mapping.score * 100.0).roundToInt().coerceIn(1, 100)
        return sourceLyrics.copy(
            synced = remapped,
            estimatedSync = confidence < 55,
            alignmentConfidence = maxOf(sourceLyrics.alignmentConfidence, confidence),
            audioConfidence = maxOf(sourceLyrics.audioConfidence, confidence),
            confidence = maxOf(sourceLyrics.confidence, confidence),
            verification = "audio_fingerprint_aligned",
            firstVocalOnsetMs = remapped.firstOrNull()?.timeMs,
            verified = confidence >= 52
        )
    }

    private fun extractEnvelope(url: String, expectedDurationMs: Long, isCancelled: () -> Boolean): Envelope? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(url, mapOf("User-Agent" to Net.UA))
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) { track = i; format = f; break }
            }
            if (track < 0 || format == null) return null
            extractor.selectTrack(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            val hardDuration = expectedDurationMs.takeIf { it > 0L }?.coerceAtMost(MAX_DECODE_MS) ?: MAX_DECODE_MS
            val buckets = ((hardDuration / HOP_MS) + 4).toInt().coerceIn(8, 3605)
            val sums = DoubleArray(buckets)
            val counts = IntArray(buckets)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            var lastPtsMs = 0L
            var idleLoops = 0

            while (!outputDone && !isCancelled() && lastPtsMs <= hardDuration && idleLoops < 300) {
                var progressed = false
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(8_000)
                    if (inputIndex >= 0) {
                        val input = decoder.getInputBuffer(inputIndex) ?: return null
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val pts = extractor.sampleTime.coerceAtLeast(0L)
                            decoder.queueInputBuffer(inputIndex, 0, size, pts, 0)
                            extractor.advance()
                        }
                        progressed = true
                    }
                }

                when (val outputIndex = decoder.dequeueOutputBuffer(info, 8_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFormat = decoder.outputFormat
                        pcmEncoding = if (outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        } else AudioFormat.ENCODING_PCM_16BIT
                        progressed = true
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        val out = decoder.getOutputBuffer(outputIndex)
                        if (out != null && info.size > 0) {
                            val duplicate = out.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                            duplicate.position(info.offset)
                            duplicate.limit(info.offset + info.size)
                            val rms = rms(duplicate, pcmEncoding)
                            val ptsMs = info.presentationTimeUs / 1000L
                            lastPtsMs = ptsMs
                            val bucket = (ptsMs / HOP_MS).toInt()
                            if (bucket in sums.indices && rms.isFinite()) {
                                sums[bucket] += rms
                                counts[bucket]++
                            }
                        }
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        decoder.releaseOutputBuffer(outputIndex, false)
                        progressed = true
                    }
                }
                if (progressed) idleLoops = 0 else idleLoops++
            }
            if (isCancelled()) return null
            val lastBucket = counts.indexOfLast { it > 0 }
            if (lastBucket < 20) return null
            val raw = FloatArray(lastBucket + 1) { i ->
                if (counts[i] == 0) 0f else (sums[i] / counts[i]).toFloat()
            }
            Envelope(normalize(raw), (lastBucket + 1) * HOP_MS)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun rms(buffer: java.nio.ByteBuffer, pcmEncoding: Int): Double {
        if (!buffer.hasRemaining()) return 0.0
        var sum = 0.0
        var n = 0
        if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
            while (buffer.remaining() >= 4 && n < 65_536) {
                val v = buffer.float.toDouble().coerceIn(-1.0, 1.0)
                sum += v * v; n++
            }
        } else {
            while (buffer.remaining() >= 2 && n < 65_536) {
                val v = buffer.short.toDouble() / 32768.0
                sum += v * v; n++
            }
        }
        return if (n == 0) 0.0 else sqrt(sum / n)
    }

    private fun normalize(raw: FloatArray): FloatArray {
        val transformed = FloatArray(raw.size) { i -> ln(1.0 + raw[i].coerceAtLeast(0f) * 40.0).toFloat() }
        val mean = transformed.average().toFloat()
        var variance = 0.0
        transformed.forEach { variance += (it - mean) * (it - mean) }
        val std = sqrt(variance / transformed.size.coerceAtLeast(1)).toFloat().coerceAtLeast(0.05f)
        val z = FloatArray(transformed.size) { i -> ((transformed[i] - mean) / std).coerceIn(-4f, 4f) }
        // Mix loudness with local change so transcoding gain differences matter less.
        return FloatArray(z.size) { i ->
            val d = if (i == 0) 0f else abs(z[i] - z[i - 1])
            z[i] * 0.55f + d * 0.45f
        }
    }

    private fun findMapping(source: Envelope, target: Envelope, isCancelled: () -> Boolean): Mapping? {
        val ratio = (target.durationMs.toDouble() / source.durationMs.coerceAtLeast(1L).toDouble()).coerceIn(0.74, 1.34)
        val scales = linkedSetOf<Double>()
        scales += 1.0
        var s = (ratio - 0.12).coerceAtLeast(0.74)
        val end = (ratio + 0.12).coerceAtMost(1.34)
        while (s <= end + 0.0001) { scales += (s * 100.0).roundToInt() / 100.0; s += 0.02 }

        var bestScale = 1.0
        var bestOffset = 0L
        var bestScore = -1.0
        for (scale in scales) {
            if (isCancelled()) return null
            var offset = -45_000L
            while (offset <= 120_000L) {
                val score = mappingScore(source.values, target.values, scale, offset, stride = 3)
                if (score > bestScore) { bestScore = score; bestScale = scale; bestOffset = offset }
                offset += 1_000L
            }
        }
        if (bestScore < 0.24) return null

        val anchors = ArrayList<Anchor>()
        var srcMs = 12_000L
        while (srcMs < source.durationMs - 8_000L && !isCancelled()) {
            val predicted = (srcMs * bestScale + bestOffset).toLong()
            var localBestTarget = predicted
            var localBest = -1.0
            var shift = -12_000L
            while (shift <= 12_000L) {
                val targetMs = predicted + shift
                val score = windowScore(source.values, target.values, srcMs, targetMs, 10_000L)
                if (score > localBest) { localBest = score; localBestTarget = targetMs }
                shift += HOP_MS
            }
            if (localBest >= 0.30) anchors += Anchor(srcMs, localBestTarget.coerceAtLeast(0L), localBest)
            srcMs += 32_000L
        }
        val monotonic = anchors.sortedBy { it.sourceMs }.fold(ArrayList<Anchor>()) { acc, a ->
            if (acc.isEmpty() || a.targetMs > acc.last().targetMs + 500L) acc.add(a)
            acc
        }
        val localAvg = monotonic.map { it.score }.average().takeIf { !it.isNaN() } ?: bestScore
        val combined = (bestScore * 0.58 + localAvg * 0.42).coerceIn(0.0, 1.0)
        return Mapping(bestScale, bestOffset, combined, monotonic)
    }

    private fun mappingScore(source: FloatArray, target: FloatArray, scale: Double, offsetMs: Long, stride: Int): Double {
        var dot = 0.0; var aa = 0.0; var bb = 0.0; var n = 0
        var i = 0
        while (i < source.size) {
            val sourceMs = i * HOP_MS
            val targetIndex = ((sourceMs * scale + offsetMs) / HOP_MS).roundToInt()
            if (targetIndex in target.indices) {
                val a = source[i].toDouble(); val b = target[targetIndex].toDouble()
                dot += a * b; aa += a * a; bb += b * b; n++
            }
            i += stride
        }
        if (n < 80 || aa <= 1e-9 || bb <= 1e-9) return -1.0
        return (dot / sqrt(aa * bb)).coerceIn(-1.0, 1.0)
    }

    private fun windowScore(source: FloatArray, target: FloatArray, sourceMs: Long, targetMs: Long, windowMs: Long): Double {
        val half = windowMs / 2
        var sMs = sourceMs - half
        var dot = 0.0; var aa = 0.0; var bb = 0.0; var n = 0
        while (sMs <= sourceMs + half) {
            val si = (sMs / HOP_MS).toInt()
            val ti = ((targetMs + (sMs - sourceMs)) / HOP_MS).toInt()
            if (si in source.indices && ti in target.indices) {
                val a = source[si].toDouble(); val b = target[ti].toDouble()
                dot += a * b; aa += a * a; bb += b * b; n++
            }
            sMs += HOP_MS
        }
        if (n < 20 || aa <= 1e-9 || bb <= 1e-9) return -1.0
        return (dot / sqrt(aa * bb)).coerceIn(-1.0, 1.0)
    }

    private fun mapTime(sourceMs: Long, mapping: Mapping): Long {
        val anchors = mapping.anchors
        if (anchors.size < 2) return (sourceMs * mapping.scale + mapping.offsetMs).toLong()
        val before = anchors.lastOrNull { it.sourceMs <= sourceMs }
        val after = anchors.firstOrNull { it.sourceMs > sourceMs }
        return when {
            before != null && after != null && after.sourceMs > before.sourceMs -> {
                val ratio = (sourceMs - before.sourceMs).toDouble() / (after.sourceMs - before.sourceMs).toDouble()
                (before.targetMs + (after.targetMs - before.targetMs) * ratio).toLong()
            }
            before != null -> before.targetMs + ((sourceMs - before.sourceMs) * mapping.scale).toLong()
            after != null -> after.targetMs - ((after.sourceMs - sourceMs) * mapping.scale).toLong()
            else -> (sourceMs * mapping.scale + mapping.offsetMs).toLong()
        }
    }
}
