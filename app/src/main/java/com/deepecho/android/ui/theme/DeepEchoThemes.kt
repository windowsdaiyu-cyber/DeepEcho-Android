package com.deepecho.android.ui.theme

import androidx.compose.ui.graphics.Color

data class DeepEchoThemeSpec(
    val key: String,
    val label: String,
    val background: Color,
    val surface: Color,
    val accent: Color,
    val accent2: Color,
    val text: Color,
    val muted: Color,
    val effect: ThemeEffect
)

enum class ThemeEffect { GOLD, LIGHT, DARK, PURPLE, TOXIC, BLOOD, SILVER, GLASS, FLUID, RGB, LOVE, SPACE, DRAGON }

object DeepEchoThemes {
    val all = listOf(
        DeepEchoThemeSpec("golden", "Golden Particle", Color(0xFF090806), Color(0xE6171510), Color(0xFFE8B451), Color(0xFFFFD87A), Color(0xFFFFF7E4), Color(0xFFB9A98A), ThemeEffect.GOLD),
        DeepEchoThemeSpec("light", "Light", Color(0xFFF3EDE4), Color(0xEEFDF9F2), Color(0xFFB88845), Color(0xFF7C5C32), Color(0xFF201B15), Color(0xFF75695C), ThemeEffect.LIGHT),
        DeepEchoThemeSpec("dark", "Dark", Color(0xFF070A0F), Color(0xE6121720), Color(0xFF90A7C5), Color(0xFFDCEAFF), Color(0xFFF1F5FA), Color(0xFF94A0B0), ThemeEffect.DARK),
        DeepEchoThemeSpec("dragon", "Molten Dragon", Color(0xFF0C0704), Color(0xE61D1008), Color(0xFFFF7A22), Color(0xFFFFC15B), Color(0xFFFFF2E0), Color(0xFFC69976), ThemeEffect.DRAGON),
        DeepEchoThemeSpec("purple", "Purple Storm", Color(0xFF0A0612), Color(0xE6191026), Color(0xFF9E63FF), Color(0xFFD8BAFF), Color(0xFFF6F0FF), Color(0xFFA996BE), ThemeEffect.PURPLE),
        DeepEchoThemeSpec("toxic", "Green Toxic", Color(0xFF041009), Color(0xE60C1D12), Color(0xFF45F57A), Color(0xFFA6FFBF), Color(0xFFECFFF1), Color(0xFF87AD91), ThemeEffect.TOXIC),
        DeepEchoThemeSpec("blood", "Red Blood", Color(0xFF100307), Color(0xE6200710), Color(0xFFEF315D), Color(0xFFFF8AA4), Color(0xFFFFEEF2), Color(0xFFB38C95), ThemeEffect.BLOOD),
        DeepEchoThemeSpec("silver", "Silver", Color(0xFF080B0F), Color(0xE6171D24), Color(0xFFBFD3E8), Color(0xFFF4F9FF), Color(0xFFF5F8FC), Color(0xFF9FAEBE), ThemeEffect.SILVER),
        DeepEchoThemeSpec("transparent", "Transparent Glass", Color(0xFF10131C), Color(0x991A2030), Color(0xFFA8BCE9), Color(0xFFEDF3FF), Color(0xFFF6F8FF), Color(0xFFA7B0C7), ThemeEffect.GLASS),
        DeepEchoThemeSpec("fluids", "Fluids", Color(0xFF03121A), Color(0xCC0B2330), Color(0xFF42DFFF), Color(0xFF70FFD0), Color(0xFFF0FDFF), Color(0xFF8CAEB8), ThemeEffect.FLUID),
        DeepEchoThemeSpec("rgb", "RGB", Color(0xFF070711), Color(0xD9141422), Color(0xFF73E7FF), Color(0xFFFF4ECD), Color(0xFFFFFFFF), Color(0xFFA5A5B9), ThemeEffect.RGB),
        DeepEchoThemeSpec("love", "Love RGB", Color(0xFF120711), Color(0xE6211021), Color(0xFFFF61B8), Color(0xFF9D46E8), Color(0xFFFFF0FA), Color(0xFFBE98B3), ThemeEffect.LOVE),
        DeepEchoThemeSpec("space", "Vibrant Space", Color(0xFF03050D), Color(0xD90D1425), Color(0xFF6FC7FF), Color(0xFFB269FF), Color(0xFFF3F6FF), Color(0xFF8E9DBD), ThemeEffect.SPACE)
    )

    fun byKey(key: String?): DeepEchoThemeSpec = all.firstOrNull { it.key == key } ?: all.first()
}
