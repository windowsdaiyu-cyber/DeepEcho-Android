package com.deepecho.android.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.deepecho.android.ui.theme.DeepEchoThemeSpec
import com.deepecho.android.ui.theme.ThemeEffect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AmbientBackground(spec: DeepEchoThemeSpec, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "deepecho-ambient")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000), RepeatMode.Restart),
        label = "phase"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "pulse"
    )

    Canvas(modifier.fillMaxSize()) {
        drawRect(spec.background)
        when (spec.effect) {
            ThemeEffect.LIGHT -> {
                drawCircle(spec.accent.copy(alpha = .12f), size.minDimension * .42f, Offset(size.width * .22f, size.height * .18f))
                drawCircle(Color.White.copy(alpha = .45f), size.minDimension * .32f, Offset(size.width * .78f, size.height * .72f))
            }
            ThemeEffect.RGB -> {
                repeat(6) { i ->
                    val hue = ((phase * 360f) + i * 60f) % 360f
                    val x = size.width * (.15f + .14f * i)
                    val y = size.height * (.22f + .08f * sin((phase * 2 * PI + i).toFloat()))
                    drawCircle(Color.hsv(hue, .72f, 1f).copy(alpha = .16f), size.minDimension * .27f, Offset(x, y))
                }
            }
            ThemeEffect.LOVE -> {
                repeat(18) { i ->
                    val angle = phase * (2f * PI.toFloat()) + i * .63f
                    val x = size.width * (.5f + .42f * sin(angle * .67f + i))
                    val y = size.height * ((i % 9) / 9f + phase) % size.height
                    drawHeart(Offset(x, y), 8f + (i % 4) * 3f, if (i % 2 == 0) spec.accent else spec.accent2, .16f)
                }
            }
            ThemeEffect.BLOOD -> {
                repeat(10) { i ->
                    val x = size.width * ((i * .097f + phase * .13f) % 1f)
                    val y = size.height * ((i * .21f + phase * .7f) % 1f)
                    drawCircle(spec.accent.copy(alpha = .12f), 18f + (i % 3) * 11f, Offset(x, y))
                    drawLine(spec.accent.copy(alpha = .09f), Offset(x, y), Offset(x, y + 60f + i * 7f), strokeWidth = 5f)
                }
            }
            ThemeEffect.DRAGON -> {
                val center = Offset(size.width * .5f, size.height * .42f)
                repeat(5) { ring ->
                    val radius = size.minDimension * (.16f + ring * .07f) * pulse
                    drawCircle(spec.accent.copy(alpha = .08f - ring * .009f), radius, center, style = Stroke(width = 4f + ring))
                }
                repeat(24) { i ->
                    val angle = (i / 24f) * 2f * PI.toFloat() + phase * 2f
                    val radius = size.minDimension * (.16f + .08f * sin(i * 1.7f + phase * 6f))
                    drawCircle(spec.accent2.copy(alpha = .18f), 3f + i % 4, center + Offset(cos(angle) * radius, sin(angle) * radius))
                }
            }
            ThemeEffect.SPACE -> {
                repeat(65) { i ->
                    val x = size.width * (((i * 37) % 100) / 100f)
                    val y = size.height * ((((i * 61) % 100) / 100f + phase * .06f) % 1f)
                    drawCircle((if (i % 4 == 0) spec.accent2 else Color.White).copy(alpha = .18f + (i % 5) * .05f), 1f + (i % 3), Offset(x, y))
                }
                drawCircle(spec.accent.copy(alpha = .12f), size.minDimension * .38f, Offset(size.width * .22f, size.height * .32f))
                drawCircle(spec.accent2.copy(alpha = .10f), size.minDimension * .33f, Offset(size.width * .78f, size.height * .64f))
            }
            ThemeEffect.FLUID -> {
                drawCircle(spec.accent.copy(alpha = .14f), size.minDimension * .42f * pulse, Offset(size.width * (.25f + .08f * sin(phase * 6f)), size.height * .3f))
                drawCircle(spec.accent2.copy(alpha = .12f), size.minDimension * .36f, Offset(size.width * (.7f + .08f * cos(phase * 5f)), size.height * .68f))
            }
            else -> {
                repeat(34) { i ->
                    val angle = i * .83f + phase * 3.5f
                    val radius = size.minDimension * (.12f + (i % 9) * .035f)
                    val center = Offset(size.width * .5f, size.height * .42f)
                    drawCircle(spec.accent.copy(alpha = .09f + (i % 3) * .03f), 2f + (i % 4), center + Offset(cos(angle) * radius, sin(angle) * radius))
                }
                drawCircle(spec.accent.copy(alpha = .10f), size.minDimension * .36f * pulse, Offset(size.width * .5f, size.height * .42f))
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHeart(center: Offset, r: Float, color: Color, alpha: Float) {
    val p = Path().apply {
        moveTo(center.x, center.y + r)
        cubicTo(center.x - r * 1.7f, center.y - r * .25f, center.x - r * .9f, center.y - r * 1.4f, center.x, center.y - r * .55f)
        cubicTo(center.x + r * .9f, center.y - r * 1.4f, center.x + r * 1.7f, center.y - r * .25f, center.x, center.y + r)
        close()
    }
    drawPath(p, color.copy(alpha = alpha))
}
