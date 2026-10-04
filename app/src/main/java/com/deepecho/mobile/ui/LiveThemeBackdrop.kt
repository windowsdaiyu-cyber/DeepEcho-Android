package com.deepecho.mobile.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.deepecho.mobile.data.Settings
import com.deepecho.mobile.player.PlayerClient
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * PC-style Live Theme background.
 *
 * The old oversized circular blobs were intentionally removed. This layer now uses a
 * full-screen low-alpha colour wash plus many small moving particles/threads. Everything
 * stays behind the app content and particle opacity is capped, so lyrics/buttons retain
 * strong contrast even on busy themes.
 */
@Composable
fun LiveThemeBackdrop(modifier: Modifier = Modifier) {
    val enabled by Settings.liveTheme.collectAsState()
    val theme by Settings.theme.collectAsState()
    if (!enabled) return

    val playing = PlayerClient.isPlaying
    val transition = rememberInfiniteTransition(label = "live_pc_particles")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            tween(if (playing) 7600 else 12800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particle_phase"
    )
    val shimmer by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1750), repeatMode = RepeatMode.Reverse),
        label = "particle_shimmer"
    )

    val accent = accentFor(theme)
    val energy = if (playing) 1f else 0.54f

    Canvas(modifier.fillMaxSize()) {
        // Quiet full-screen colour atmosphere instead of visible giant circles.
        drawRect(
            brush = Brush.linearGradient(
                listOf(
                    accent.copy(alpha = 0.060f),
                    Color.Transparent,
                    accent.copy(alpha = 0.040f)
                ),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height)
            )
        )

        when (theme) {
            "Ocean" -> {
                // Fine current lines + bubbles; intentionally low opacity for readable text.
                for (row in 0 until 5) {
                    var previous: Offset? = null
                    val baseY = size.height * (0.12f + row * 0.19f)
                    for (i in 0..24) {
                        val x = size.width * i / 24f
                        val y = baseY + sin(phase * (0.55f + row * 0.05f) + i * 0.38f + row) * (5f + row * 1.6f) * energy
                        val now = Offset(x, y)
                        previous?.let { drawLine(accent.copy(alpha = 0.075f), it, now, 1.15f, StrokeCap.Round) }
                        previous = now
                    }
                }
                for (i in 0 until 66) {
                    val x0 = ((i * 37) % 101) / 100f
                    val y0 = ((i * 61) % 103) / 102f
                    val speed = 0.22f + (i % 7) * 0.060f
                    val travel = (phase / (2f * PI.toFloat()) * speed + y0) % 1f
                    val x = x0 * size.width + sin(phase * 0.72f + i * 0.63f) * (4f + (i % 5) * 1.8f)
                    val y = (1f - travel) * size.height
                    val radius = 0.9f + (i % 5) * 0.55f
                    drawCircle(accent.copy(alpha = (0.11f + 0.17f * shimmer) * energy), radius, Offset(x, y))
                }
            }

            "Violet" -> {
                // Nebula-style orbital dust without opaque blobs.
                val cx = size.width * 0.52f
                val cy = size.height * 0.46f
                for (i in 0 until 72) {
                    val lane = 0.10f + (i % 12) * 0.026f
                    val angle = phase * (0.17f + (i % 5) * 0.018f) + i * 0.74f
                    val rx = size.width * lane
                    val ry = size.height * (0.055f + (i % 9) * 0.012f)
                    val x = cx + cos(angle) * rx
                    val y = cy + sin(angle) * ry
                    val radius = 0.9f + (i % 4) * 0.6f
                    drawCircle(accent.copy(alpha = (0.10f + 0.18f * shimmer) * energy), radius, Offset(x, y))
                    if (i % 12 == 0) {
                        drawLine(
                            accent.copy(alpha = 0.055f * energy),
                            Offset(x - 9f, y),
                            Offset(x + 9f, y),
                            1f,
                            StrokeCap.Round
                        )
                    }
                }
            }

            "Rose" -> {
                // Spark/petal drift.
                for (i in 0 until 70) {
                    val x0 = ((i * 47) % 109) / 108f
                    val y0 = ((i * 71) % 113) / 112f
                    val travel = (y0 + phase / (2f * PI.toFloat()) * (0.13f + (i % 6) * 0.023f)) % 1f
                    val x = x0 * size.width + sin(phase * 0.55f + i) * 10f
                    val y = (1f - travel) * size.height
                    val alpha = (0.10f + 0.17f * shimmer) * energy
                    drawCircle(accent.copy(alpha = alpha), 1f + (i % 5) * 0.55f, Offset(x, y))
                    if (i % 9 == 0) {
                        drawLine(accent.copy(alpha = 0.05f), Offset(x - 5f, y + 3f), Offset(x + 5f, y - 3f), 1f, StrokeCap.Round)
                    }
                }
            }

            "Emerald" -> {
                // Fireflies with short upward trails.
                for (i in 0 until 68) {
                    val x0 = ((i * 43) % 107) / 106f
                    val y0 = ((i * 67) % 109) / 108f
                    val travel = (y0 + phase / (2f * PI.toFloat()) * (0.14f + (i % 7) * 0.025f)) % 1f
                    val x = x0 * size.width + cos(phase * 0.61f + i * 0.81f) * 8f
                    val y = (1f - travel) * size.height
                    val glow = abs(sin(phase * 0.8f + i * 0.37f))
                    drawCircle(accent.copy(alpha = (0.10f + glow * 0.21f) * energy), 1f + (i % 4) * 0.62f, Offset(x, y))
                    if (playing && i % 10 == 0) {
                        drawLine(accent.copy(alpha = 0.055f), Offset(x, y + 10f), Offset(x, y + 2f), 1f, StrokeCap.Round)
                    }
                }
            }

            "AMOLED" -> {
                // True-black-friendly star field: no colour wash except tiny stars.
                drawRect(Color.Black.copy(alpha = 0.12f))
                for (i in 0 until 78) {
                    val x = (((i * 47) % 127) / 126f) * size.width
                    val y = (((i * 83) % 131) / 130f) * size.height
                    val twinkle = abs(sin(phase * (0.23f + (i % 7) * 0.025f) + i))
                    drawCircle(Color.White.copy(alpha = (0.060f + twinkle * 0.16f) * energy), 0.7f + (i % 4) * 0.45f, Offset(x, y))
                }
            }

            else -> {
                // Golden: premium floating dust + sparse diagonal spark streaks.
                for (i in 0 until 74) {
                    val x0 = ((i * 41) % 109) / 108f
                    val y0 = ((i * 73) % 113) / 112f
                    val travel = (y0 + phase / (2f * PI.toFloat()) * (0.11f + (i % 7) * 0.040f)) % 1f
                    val x = x0 * size.width + cos(phase * 0.42f + i * 0.63f) * 9f
                    val y = (1f - travel) * size.height + sin(phase * 0.31f + i) * 5f
                    val radius = 0.85f + (i % 5) * 0.55f
                    drawCircle(accent.copy(alpha = (0.11f + 0.18f * shimmer) * energy), radius, Offset(x, y))
                    if (playing && i % 11 == 0) {
                        drawLine(
                            accent.copy(alpha = 0.07f + 0.060f * shimmer),
                            Offset(x - 6f, y + 6f),
                            Offset(x + 6f, y - 6f),
                            1f,
                            StrokeCap.Round
                        )
                    }
                }
            }
        }

        // Readability guard: a tiny neutral veil keeps bright particle clusters from competing
        // with foreground text/buttons, while the animation remains clearly visible.
        drawRect(Color.Black.copy(alpha = if (theme == "AMOLED") 0.03f else 0.050f))
    }
}

fun accentFor(theme: String): Color = when (theme) {
    "Violet" -> Color(0xFF9D8CFF)
    "Ocean" -> Color(0xFF4FC3F7)
    "Rose" -> Color(0xFFFF6B9A)
    "Emerald" -> Color(0xFF3DDC97)
    "AMOLED" -> Color.White
    else -> Color(0xFFE8B84A)
}
