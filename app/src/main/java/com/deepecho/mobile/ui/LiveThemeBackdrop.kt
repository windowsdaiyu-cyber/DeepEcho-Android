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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.deepecho.mobile.data.Settings
import com.deepecho.mobile.player.PlayerClient
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Decorative Live Theme background only. There is intentionally NO lyrics visualizer here.
 * Motion is lightweight, theme-specific and slightly livelier while music is playing.
 */
@Composable
fun LiveThemeBackdrop(modifier: Modifier = Modifier) {
    val enabled by Settings.liveTheme.collectAsState()
    val theme by Settings.theme.collectAsState()
    if (!enabled) return

    val playing = PlayerClient.isPlaying
    val transition = rememberInfiniteTransition(label = "live_theme_motion")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            tween(if (playing) 6200 else 10500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particle_phase"
    )
    val shimmer by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1450), repeatMode = RepeatMode.Reverse),
        label = "particle_shimmer"
    )

    val accent = accentFor(theme)
    val energy = if (playing) 1f else 0.46f

    Canvas(modifier.fillMaxSize()) {
        // Large soft depth glows.
        drawCircle(
            accent.copy(alpha = 0.045f + 0.035f * shimmer),
            radius = size.minDimension * (0.52f + 0.04f * sin(phase)),
            center = Offset(size.width * 0.78f, size.height * 0.14f)
        )
        drawCircle(
            accent.copy(alpha = 0.025f + 0.025f * shimmer),
            radius = size.minDimension * 0.43f,
            center = Offset(size.width * 0.12f, size.height * 0.74f)
        )

        when (theme) {
            "Ocean" -> {
                // Flowing currents + bubbles.
                for (row in 0 until 4) {
                    var previous: Offset? = null
                    val baseY = size.height * (0.18f + row * 0.19f)
                    for (i in 0..36) {
                        val x = size.width * i / 36f
                        val y = baseY + sin(phase * (0.7f + row * 0.08f) + i * 0.34f + row) * (8f + row * 3f) * energy
                        val now = Offset(x, y)
                        previous?.let {
                            drawLine(accent.copy(alpha = 0.055f + row * 0.012f), it, now, 1.7f, StrokeCap.Round)
                        }
                        previous = now
                    }
                }
                for (i in 0 until 42) {
                    val seed = i * 0.719f
                    val drift = (phase / (2f * PI.toFloat()) + seed * 0.11f) % 1f
                    val y = (1f - drift) * (size.height + 50f) - 25f
                    val x = ((seed * 0.193f) % 1f) * size.width + sin(phase + i) * 12f
                    drawCircle(accent.copy(alpha = 0.10f + 0.14f * shimmer), 1.5f + (i % 5) * 0.75f, Offset(x.coerceIn(0f, size.width), y))
                }
            }

            "Violet" -> {
                // Slow orbital nebula particles.
                val center = Offset(size.width * 0.56f, size.height * 0.38f)
                for (i in 0 until 58) {
                    val lane = 0.18f + (i % 8) * 0.045f
                    val a = phase * (0.28f + (i % 4) * 0.035f) + i * 0.63f
                    val rx = size.width * lane
                    val ry = size.height * (0.10f + (i % 6) * 0.026f)
                    val pt = Offset(center.x + cos(a) * rx, center.y + sin(a) * ry)
                    drawCircle(accent.copy(alpha = 0.08f + 0.20f * shimmer * energy), 1.6f + (i % 4) * 0.8f, pt)
                }
            }

            "Rose" -> {
                // Soft drifting spark/petal field.
                for (i in 0 until 54) {
                    val seed = i * 0.817f
                    val drift = (phase / (2f * PI.toFloat()) + seed * 0.09f) % 1f
                    val x = ((seed * 0.267f) % 1f) * size.width + sin(phase * 0.7f + i) * 20f
                    val y = (1f - drift) * (size.height + 70f) - 35f
                    val r = 1.8f + (i % 5) * 0.8f
                    drawCircle(accent.copy(alpha = 0.09f + 0.17f * shimmer), r, Offset(x.coerceIn(0f, size.width), y))
                    if (i % 6 == 0) {
                        drawLine(
                            accent.copy(alpha = 0.08f),
                            Offset(x - 7f, y + 4f), Offset(x + 7f, y - 4f),
                            1.5f, StrokeCap.Round
                        )
                    }
                }
            }

            "Emerald" -> {
                // Rising firefly trails.
                for (i in 0 until 50) {
                    val seed = i * 0.697f
                    val drift = (phase / (2f * PI.toFloat()) + seed * 0.12f) % 1f
                    val x = ((seed * 0.241f) % 1f) * size.width + cos(phase * 0.8f + i) * 14f
                    val y = (1f - drift) * (size.height + 60f) - 30f
                    val alpha = 0.08f + 0.20f * shimmer * energy
                    drawCircle(accent.copy(alpha = alpha), 1.5f + (i % 4) * 0.9f, Offset(x.coerceIn(0f, size.width), y))
                    if (playing && i % 7 == 0) {
                        drawLine(accent.copy(alpha = 0.07f), Offset(x, y + 18f), Offset(x, y + 4f), 1.3f, StrokeCap.Round)
                    }
                }
            }

            "AMOLED" -> {
                // Minimal premium star field for true black.
                for (i in 0 until 62) {
                    val seed = i * 0.883f
                    val x = ((seed * 0.173f) % 1f) * size.width
                    val y = ((seed * 0.317f) % 1f) * size.height
                    val twinkle = abs(sin(phase * (0.35f + (i % 5) * 0.04f) + i))
                    drawCircle(Color.White.copy(alpha = 0.06f + twinkle * 0.22f * energy), 0.9f + (i % 3) * 0.7f, Offset(x, y))
                }
            }

            else -> {
                // Golden: floating dust + tasteful diagonal sparkle streaks.
                for (i in 0 until 58) {
                    val seed = i * 0.731f
                    val drift = (phase / (2f * PI.toFloat()) + seed * 0.10f) % 1f
                    val x = ((seed * 0.173f) % 1f) * size.width + cos(phase * (0.42f + (i % 5) * 0.025f) + i) * 16f
                    val y = (1f - drift) * (size.height + 70f) - 35f + sin(phase * 0.45f + i * 0.7f) * 10f
                    val r = 1.4f + (i % 5) * 0.72f
                    drawCircle(accent.copy(alpha = 0.09f + 0.19f * shimmer * energy), r, Offset(x.coerceIn(0f, size.width), y))
                    if (playing && i % 9 == 0) {
                        drawLine(
                            accent.copy(alpha = 0.10f + 0.06f * shimmer),
                            Offset(x - 8f, y + 8f), Offset(x + 8f, y - 8f),
                            1.4f, StrokeCap.Round
                        )
                    }
                }
            }
        }
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
