package com.deepecho.mobile.player

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Safe audio-reactive signal used only for visual UI layers.
 *
 * It never changes playback. When RECORD_AUDIO is granted it reads FFT data from the current
 * player audio session through Android Visualizer. If capture is unavailable, a deterministic
 * playback-position fallback keeps visual motion alive without blocking lyrics/playback.
 */
object AudioReactive {
    val bands = MutableStateFlow(List(16) { 0.08f })
    val energy = MutableStateFlow(0.08f)
    val realCapture = MutableStateFlow(false)

    private var visualizer: Visualizer? = null
    private var sessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var smooth = FloatArray(16) { 0.08f }

    @Synchronized
    fun attachIfPermitted(context: Context): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            realCapture.value = false
            return false
        }
        val wanted = AudioFx.audioSessionId.value
        if (wanted == C.AUDIO_SESSION_ID_UNSET || wanted <= 0) {
            realCapture.value = false
            return false
        }
        if (visualizer != null && sessionId == wanted) return true

        release()
        return runCatching {
            val v = Visualizer(wanted)
            val range = Visualizer.getCaptureSizeRange()
            v.captureSize = 512.coerceIn(range[0], range[1])
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) = Unit

                override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    if (fft == null || fft.size < 8) return
                    val half = fft.size / 2
                    val next = FloatArray(16)
                    for (bar in next.indices) {
                        // Slightly denser low-frequency bins gives music a more natural visual pulse.
                        val start = 1 + ((bar.toFloat() / next.size) * (bar.toFloat() / next.size) * (half - 2)).toInt()
                        val end = 1 + ((((bar + 1).toFloat() / next.size) * ((bar + 1).toFloat() / next.size)) * (half - 2)).toInt()
                        var total = 0.0
                        var count = 0
                        for (bin in start.coerceAtLeast(1)..end.coerceAtLeast(start + 1).coerceAtMost(half - 1)) {
                            val re = fft.getOrElse(bin * 2) { 0 }.toInt().toDouble()
                            val im = fft.getOrElse(bin * 2 + 1) { 0 }.toInt().toDouble()
                            total += sqrt(re * re + im * im)
                            count++
                        }
                        val raw = if (count == 0) 0f else (total / count / 90.0).toFloat().coerceIn(0f, 1f)
                        smooth[bar] = smooth[bar] * 0.70f + raw * 0.30f
                        next[bar] = smooth[bar].coerceIn(0.02f, 1f)
                    }
                    bands.value = next.toList()
                    val avg = next.average().toFloat()
                    val peak = next.maxOrNull() ?: 0f
                    energy.value = (avg * 0.55f + peak * 0.45f).coerceIn(0.03f, 1f)
                    realCapture.value = true
                }
            }, (Visualizer.getMaxCaptureRate() / 2).coerceAtLeast(1000), false, true)
            v.enabled = true
            visualizer = v
            sessionId = wanted
            realCapture.value = true
            true
        }.getOrElse {
            realCapture.value = false
            release()
            false
        }
    }

    fun updateFallback(positionMs: Long, playing: Boolean) {
        if (realCapture.value) return
        val t = positionMs.coerceAtLeast(0L) / 1000.0
        val strength = if (playing) 1.0 else 0.34
        val out = List(16) { i ->
            val a = abs(sin(t * (2.05 + i * 0.07) + i * 0.73))
            val b = abs(cos(t * (1.31 + i * 0.11) + i * 0.39))
            ((0.08 + (a * 0.56 + b * 0.24) * strength) * (0.90 + (i % 5) * 0.025)).toFloat().coerceIn(0.04f, 0.86f)
        }
        bands.value = out
        energy.value = if (playing) ((out.average().toFloat() * 0.85f) + 0.08f).coerceIn(0.08f, 0.74f) else 0.06f
    }

    @Synchronized
    fun release() {
        runCatching { visualizer?.enabled = false }
        runCatching { visualizer?.release() }
        visualizer = null
        sessionId = C.AUDIO_SESSION_ID_UNSET
        realCapture.value = false
    }
}
