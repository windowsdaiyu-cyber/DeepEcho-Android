package com.deepecho.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance

@Composable
fun DeepEchoTheme(spec: DeepEchoThemeSpec, content: @Composable () -> Unit) {
    val scheme = if (spec.background.luminance() > 0.55f) {
        lightColorScheme(
            primary = spec.accent,
            secondary = spec.accent2,
            background = spec.background,
            surface = spec.surface,
            onPrimary = spec.background,
            onBackground = spec.text,
            onSurface = spec.text
        )
    } else {
        darkColorScheme(
            primary = spec.accent,
            secondary = spec.accent2,
            background = spec.background,
            surface = spec.surface,
            onPrimary = spec.background,
            onBackground = spec.text,
            onSurface = spec.text
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
