package com.deepecho.mobile.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import androidx.media3.common.C
import com.deepecho.mobile.data.Settings
import kotlinx.coroutines.flow.MutableStateFlow

/** System Equalizer + BassBoost. Service player ka audio session milte hi attach hota hai. */
object AudioFx {
    class Info(val freqsHz: List<Int>, val minMb: Int, val maxMb: Int)

    val info = MutableStateFlow<Info?>(null)
    val levels = MutableStateFlow<List<Int>>(emptyList())
    val bass = MutableStateFlow(0)          // 0..1000
    val preset = MutableStateFlow("Custom")
    val audioSessionId = MutableStateFlow(C.AUDIO_SESSION_ID_UNSET)

    val presets: Map<String, List<Float>> = linkedMapOf(
        "Flat" to listOf(0f, 0f, 0f, 0f, 0f),
        "Bass" to listOf(0.9f, 0.6f, 0f, 0f, 0f),
        "Treble" to listOf(0f, 0f, 0f, 0.6f, 0.9f),
        "Vocal" to listOf(-0.3f, 0.2f, 0.7f, 0.4f, -0.1f),
        "Rock" to listOf(0.6f, 0.3f, -0.2f, 0.4f, 0.7f),
        "Pop" to listOf(-0.2f, 0.3f, 0.6f, 0.3f, -0.2f)
    )

    private var eq: Equalizer? = null
    private var bassFx: BassBoost? = null
    private var session = -1

    fun attach(sessionId: Int) {
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId <= 0) return
        if (sessionId == session && eq != null) {
            audioSessionId.value = sessionId
            return
        }
        releaseEffects()
        session = sessionId
        audioSessionId.value = sessionId
        try {
            val e = Equalizer(0, sessionId)
            e.enabled = true
            eq = e
            val n = e.numberOfBands.toInt()
            val range = e.bandLevelRange
            val freqs = (0 until n).map { e.getCenterFreq(it.toShort()) / 1000 }
            info.value = Info(freqs, range[0].toInt(), range[1].toInt())

            val saved = Settings.getString("eq_levels", "").split(",").mapNotNull { it.toIntOrNull() }
            val lv = if (saved.size == n) saved else List(n) { 0 }
            levels.value = lv
            lv.forEachIndexed { i, v -> e.setBandLevel(i.toShort(), v.toShort()) }
            preset.value = Settings.getString("eq_preset", "Flat")
        } catch (_: Throwable) {
            eq = null
        }
        try {
            val b = BassBoost(0, sessionId)
            b.enabled = true
            bassFx = b
            val s = Settings.getInt("bass", 0)
            bass.value = s
            if (b.strengthSupported) b.setStrength(s.toShort())
        } catch (_: Throwable) {
            bassFx = null
        }
    }

    fun setBand(index: Int, mb: Int) {
        val i = info.value ?: return
        val v = mb.coerceIn(i.minMb, i.maxMb)
        eq?.setBandLevel(index.toShort(), v.toShort())
        levels.value = levels.value.toMutableList().also { if (index in it.indices) it[index] = v }
        preset.value = "Custom"
        persist()
    }

    fun applyPreset(name: String) {
        val i = info.value ?: return
        val curve = presets[name] ?: return
        val n = i.freqsHz.size
        val out = (0 until n).map { b ->
            val pos = if (n == 1) 0f else b * (curve.size - 1f) / (n - 1)
            val f = curve[Math.round(pos)]
            val mb = if (f >= 0) f * i.maxMb * 0.8f else -f * i.minMb * 0.8f
            mb.toInt()
        }
        out.forEachIndexed { b, v -> eq?.setBandLevel(b.toShort(), v.toShort()) }
        levels.value = out
        preset.value = name
        persist()
    }

    fun setBass(strength: Int) {
        val s = strength.coerceIn(0, 1000)
        bass.value = s
        try {
            if (bassFx?.strengthSupported == true) bassFx?.setStrength(s.toShort())
        } catch (_: Throwable) {
        }
        Settings.putInt("bass", s)
    }

    private fun persist() {
        Settings.putString("eq_levels", levels.value.joinToString(","))
        Settings.putString("eq_preset", preset.value)
    }

    private fun releaseEffects() {
        try { eq?.release() } catch (_: Throwable) {}
        try { bassFx?.release() } catch (_: Throwable) {}
        eq = null
        bassFx = null
    }
}
