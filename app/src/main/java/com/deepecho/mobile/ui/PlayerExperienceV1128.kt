package com.deepecho.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deepecho.mobile.data.Settings
import com.deepecho.mobile.data.Song
import com.deepecho.mobile.lyrics.LyricsPresentationBridge
import com.deepecho.mobile.player.AudioReactive
import com.deepecho.mobile.player.PlayerClient

enum class PlayerModeV1128(val label: String) {
    Artwork("Artwork"), Lyrics("Lyrics"), Visualizer("Visualizer"), Ambient("Ambient"), Minimal("Minimal")
}

@Composable
fun PlayerModeSelectorV1128(mode: PlayerModeV1128, onSelect: (PlayerModeV1128) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        PlayerModeV1128.entries.forEach { item ->
            FilterChip(
                selected = mode == item,
                onClick = { onSelect(item) },
                label = { Text(item.label, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

@Composable
fun ArtworkDynamicBackdropV1128(song: Song?, modifier: Modifier = Modifier) {
    val enabled by Settings.artworkDynamicColors.collectAsState()
    val context = LocalContext.current
    val theme = Settings.theme.collectAsState().value
    val themeAccent = paletteFor(theme).primary
    val accentInt by produceState<Int?>(initialValue = null, song?.thumb, enabled) {
        value = if (enabled) ArtworkColorsV1128.accent(context, song?.thumb) else null
    }
    val accent = accentInt?.let { Color(it) } ?: themeAccent
    if (!enabled || song == null) return
    Canvas(modifier) {
        drawRect(
            Brush.radialGradient(
                colors = listOf(accent.copy(alpha = .20f), accent.copy(alpha = .055f), Color.Transparent),
                center = Offset(size.width * .50f, size.height * .34f),
                radius = size.maxDimension * .74f
            )
        )
    }
}

@Composable
fun VisualizerModeV1128(song: Song, modifier: Modifier = Modifier) {
    val bands by AudioReactive.bands.collectAsState()
    val energy by AudioReactive.energy.collectAsState()
    val context = LocalContext.current
    val theme by Settings.theme.collectAsState()
    val accent = paletteFor(theme).primary
    LaunchedEffect(song.url) { AudioReactive.attachIfPermitted(context) }

    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        Art(song.thumb, Modifier.size(176.dp), RoundedCornerShape(28.dp))
        Spacer(Modifier.height(18.dp))
        Text(song.title, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(20.dp))
        Canvas(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 10.dp, vertical = 14.dp)) {
            val count = bands.size.coerceAtLeast(1)
            val gap = size.width / (count * 2.2f)
            val barWidth = gap * .82f
            val usable = size.height * .88f
            bands.forEachIndexed { i, band ->
                val h = (usable * (0.08f + band * .92f)).coerceAtMost(usable)
                val x = gap * .60f + i * (size.width - gap) / count
                drawLine(
                    color = accent.copy(alpha = (.55f + energy * .42f).coerceIn(.55f, .98f)),
                    start = Offset(x, (size.height - h) / 2f),
                    end = Offset(x, (size.height + h) / 2f),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun MinimalModeV1128(song: Song, modifier: Modifier = Modifier) {
    val lyric = LyricsPresentationBridge.lineAt(song.url, PlayerClient.positionMs)
    Column(
        modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Art(song.thumb, Modifier.size(116.dp), RoundedCornerShape(22.dp))
        Spacer(Modifier.height(18.dp))
        Text(song.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
        Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        if (!lyric.isNullOrBlank()) {
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f), RoundedCornerShape(18.dp)).padding(14.dp)
            ) {
                Text(lyric, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
