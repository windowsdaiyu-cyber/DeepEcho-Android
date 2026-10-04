package com.deepecho.mobile.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

data class Palette(
    val name: String,
    val primary: Color,
    val bg: Color,
    val surface: Color,
    val textMain: Color,
    val textMuted: Color
)

val Palettes = listOf(
    Palette(
        "Golden",
        Color(0xFFE8B84A),
        Color(0xFF0F0D08),
        Color(0xFF1D1911),
        Color(0xFFF3D477),
        Color(0xFFD5BC82)
    ),
    Palette(
        "Violet",
        Color(0xFF9D8CFF),
        Color(0xFF0E0C18),
        Color(0xFF1A1727),
        Color(0xFFC9C0FF),
        Color(0xFFAAA1D9)
    ),
    Palette(
        "Ocean",
        Color(0xFF4FC3F7),
        Color(0xFF07121A),
        Color(0xFF112331),
        Color(0xFF8FDCFF),
        Color(0xFF82BBD2)
    ),
    Palette(
        "Rose",
        Color(0xFFFF6B9A),
        Color(0xFF160A10),
        Color(0xFF28131C),
        Color(0xFFFFA3BF),
        Color(0xFFD98CA4)
    ),
    Palette(
        "Emerald",
        Color(0xFF3DDC97),
        Color(0xFF07140E),
        Color(0xFF11251B),
        Color(0xFF8BE8BC),
        Color(0xFF78C7A1)
    ),
    Palette(
        "AMOLED",
        Color(0xFFFFFFFF),
        Color(0xFF000000),
        Color(0xFF121212),
        Color(0xFFFFFFFF),
        Color(0xFFBEBEBE)
    )
)

@Composable
fun DeepEchoTheme(name: String, content: @Composable () -> Unit) {
    val target = Palettes.firstOrNull { it.name == name } ?: Palettes[0]
    val spec = tween<Color>(durationMillis = 520)
    val primary by animateColorAsState(target.primary, spec, label = "theme_primary")
    val bg by animateColorAsState(target.bg, spec, label = "theme_bg")
    val surface by animateColorAsState(target.surface, spec, label = "theme_surface")
    val textMain by animateColorAsState(target.textMain, spec, label = "theme_text")
    val textMuted by animateColorAsState(target.textMuted, spec, label = "theme_muted")
    val scheme = darkColorScheme(
        primary = primary,
        onPrimary = Color(0xFF111111),
        secondary = primary,
        onSecondary = Color(0xFF111111),
        secondaryContainer = primary.copy(alpha = 0.22f),
        onSecondaryContainer = textMain,
        background = bg,
        onBackground = textMain,
        surface = surface,
        onSurface = textMain,
        surfaceVariant = lerp(surface, Color.White, 0.08f),
        onSurfaceVariant = textMuted,
        surfaceContainer = surface,
        surfaceContainerHigh = lerp(surface, Color.White, 0.045f),
        surfaceContainerHighest = lerp(surface, Color.White, 0.08f),
        outline = textMuted.copy(alpha = 0.62f),
        outlineVariant = primary.copy(alpha = 0.32f)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

fun paletteFor(name: String): Palette = Palettes.firstOrNull { it.name == name } ?: Palettes[0]

fun lyricsActiveFor(name: String, live: Boolean): Color {
    val p = paletteFor(name)
    return lerp(p.primary, Color.White, if (live) 0.28f else 0.14f)
}

fun lyricsMainFor(name: String, live: Boolean): Color {
    val p = paletteFor(name)
    if (name == "AMOLED") return Color.White
    // Keep lyrics visibly theme-coloured across OEM font/rendering differences instead of
    // washing every palette toward plain white on some devices.
    return lerp(p.textMain, p.primary, if (live) 0.26f else 0.16f)
}

fun lyricsMutedFor(name: String, live: Boolean): Color {
    val p = paletteFor(name)
    if (name == "AMOLED") return p.textMuted
    return lerp(p.textMuted, p.primary, if (live) 0.13f else 0.08f)
}
