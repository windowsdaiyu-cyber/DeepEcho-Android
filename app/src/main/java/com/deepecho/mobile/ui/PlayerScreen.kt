package com.deepecho.mobile.ui

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.deepecho.mobile.data.Settings
import com.deepecho.mobile.data.Song
import com.deepecho.mobile.data.Store
import com.deepecho.mobile.data.ThemeAdvisor
import com.deepecho.mobile.data.TopArtist
import com.deepecho.mobile.lyrics.LyricsRuntimeSync
import com.deepecho.mobile.lyrics.LyricsPresentationBridge
import com.deepecho.mobile.lyrics.FloatingLyricsSyncPolicy
import com.deepecho.mobile.lyrics.LyricsProgress
import com.deepecho.mobile.lyrics.LyricVideoEligibility
import com.deepecho.mobile.lyrics.LyricsLiveVideoReader
import com.deepecho.mobile.net.Bus
import com.deepecho.mobile.net.Downloads
import com.deepecho.mobile.net.Lrclib
import com.deepecho.mobile.net.Lyrics
import com.deepecho.mobile.net.LyricLine
import com.deepecho.mobile.net.Net
import com.deepecho.mobile.net.ResolvedVideo
import com.deepecho.mobile.net.YouTubeApi
import com.deepecho.mobile.overlay.FloatingLyricsController
import com.deepecho.mobile.player.AudioReactive
import com.deepecho.mobile.player.PlayerClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Composable
private fun ReactiveSeekSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    val playing = PlayerClient.isPlaying
    val transition = rememberInfiniteTransition(label = "seek_bar_motion")
    val shine by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "seek_shine"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.045f,
        animationSpec = infiniteRepeatable(tween(720), repeatMode = RepeatMode.Reverse),
        label = "seek_pulse"
    )

    val accent = MaterialTheme.colorScheme.primary

    Box(modifier.height(30.dp), contentAlignment = Alignment.Center) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth().graphicsLayer {
                scaleY = if (playing) pulse else 1f
            }
        )
        if (playing) {
            Canvas(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
                val x = size.width * shine
                val y = size.height / 2f
                drawCircle(accent.copy(alpha = 0.10f), radius = 16f, center = Offset(x, y))
                drawCircle(accent.copy(alpha = 0.28f), radius = 8f, center = Offset(x, y))
                drawCircle(accent.copy(alpha = 0.88f), radius = 2.8f, center = Offset(x, y))
            }
        }
    }
}

@Composable
private fun VerticalVolumeSlider(
    modifier: Modifier = Modifier
) {
    val level = PlayerClient.volume
    val accent = MaterialTheme.colorScheme.primary

    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "VOL",
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.Bold
        )
        Box(
            Modifier.width(42.dp).height(86.dp),
            contentAlignment = Alignment.Center
        ) {
            Slider(
                value = level,
                onValueChange = PlayerClient::changeVolume,
                valueRange = 0f..1f,
                modifier = Modifier
                    .width(84.dp)
                    .graphicsLayer { rotationZ = -90f }
            )
        }
    }
}


@Composable
private fun ThemeActionBurst(
    trigger: Int,
    theme: String,
    kind: String,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(720))
        }
    }
    if (trigger <= 0 || progress.value >= 0.999f) return

    val p = progress.value
    val accent = accentFor(theme)
    Canvas(modifier.size(72.dp)) {
        val alpha = (1f - p).coerceIn(0f, 1f)
        val cx = size.width / 2f
        val cy = size.height / 2f
        val travel = size.minDimension * (0.12f + 0.34f * p)
        when (theme) {
            "Ruby" -> {
                repeat(13) { i ->
                    val a = (i / 13f) * (Math.PI * 2.0) + p * 1.6f
                    val localTravel = travel * (0.78f + (i % 4) * 0.10f)
                    val x = cx + kotlin.math.cos(a).toFloat() * localTravel
                    val y = cy + kotlin.math.sin(a).toFloat() * localTravel
                    val r = 1.9f + (i % 4) * 0.75f
                    drawCircle(Color(0xFFFF5A58).copy(alpha = alpha * 0.94f), r, Offset(x, y))
                    if (i % 3 == 0) {
                        drawLine(
                            Color(0xFFFFC1B8).copy(alpha = alpha * 0.76f),
                            Offset(x - 5f, y + 2f), Offset(x + 5f, y - 2f),
                            1.6f, StrokeCap.Round
                        )
                    }
                }
                drawCircle(
                    Color(0xFFFF3347).copy(alpha = alpha * 0.42f),
                    radius = size.minDimension * (0.10f + p * 0.28f),
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.2f)
                )
            }
            "Rose" -> {
                repeat(7) { i ->
                    val a = (i / 7f) * (Math.PI * 2.0) - Math.PI / 2.0
                    val x = cx + kotlin.math.cos(a).toFloat() * travel
                    val y = cy + kotlin.math.sin(a).toFloat() * travel
                    val r = size.minDimension * (0.035f - 0.010f * p)
                    val heart = Path().apply {
                        moveTo(x, y + r * 0.9f)
                        cubicTo(x - r * 1.45f, y - r * 0.05f, x - r * 0.85f, y - r * 1.25f, x, y - r * 0.45f)
                        cubicTo(x + r * 0.85f, y - r * 1.25f, x + r * 1.45f, y - r * 0.05f, x, y + r * 0.9f)
                        close()
                    }
                    drawPath(heart, accent.copy(alpha = alpha * 0.92f))
                }
            }
            "Ocean" -> {
                repeat(3) { ring ->
                    drawCircle(
                        accent.copy(alpha = alpha * (0.62f - ring * 0.13f)),
                        radius = size.minDimension * (0.12f + p * (0.22f + ring * 0.08f)),
                        center = Offset(cx, cy),
                        style = Stroke(width = 2.1f + ring)
                    )
                }
                repeat(8) { i ->
                    val a = i / 8f * (Math.PI * 2.0)
                    drawCircle(
                        accent.copy(alpha = alpha * 0.85f),
                        2.4f + (i % 3),
                        Offset(cx + kotlin.math.cos(a).toFloat() * travel, cy + kotlin.math.sin(a).toFloat() * travel)
                    )
                }
            }
            "Violet" -> {
                repeat(10) { i ->
                    val a = i / 10f * (Math.PI * 2.0) + p * 4.2f
                    val r = travel * (0.72f + (i % 3) * 0.14f)
                    drawCircle(
                        accent.copy(alpha = alpha * 0.92f),
                        2.1f + (i % 4) * 0.7f,
                        Offset(cx + kotlin.math.cos(a).toFloat() * r, cy + kotlin.math.sin(a).toFloat() * r)
                    )
                }
            }
            "Emerald" -> {
                repeat(8) { i ->
                    val a = i / 8f * (Math.PI * 2.0) + if (kind == "download") 0.6 else 0.0
                    val x = cx + kotlin.math.cos(a).toFloat() * travel
                    val y = cy + kotlin.math.sin(a).toFloat() * travel
                    drawOval(
                        accent.copy(alpha = alpha * 0.88f),
                        topLeft = Offset(x - 2.5f, y - 5.2f),
                        size = androidx.compose.ui.geometry.Size(5f, 10.4f)
                    )
                }
            }
            "AMOLED" -> {
                repeat(10) { i ->
                    val a = i / 10f * (Math.PI * 2.0)
                    val inner = size.minDimension * 0.12f
                    drawLine(
                        Color.White.copy(alpha = alpha * 0.90f),
                        Offset(cx + kotlin.math.cos(a).toFloat() * inner, cy + kotlin.math.sin(a).toFloat() * inner),
                        Offset(cx + kotlin.math.cos(a).toFloat() * travel, cy + kotlin.math.sin(a).toFloat() * travel),
                        2f,
                        StrokeCap.Round
                    )
                }
            }
            else -> {
                repeat(9) { i ->
                    val a = i / 9f * (Math.PI * 2.0)
                    val x = cx + kotlin.math.cos(a).toFloat() * travel
                    val y = cy + kotlin.math.sin(a).toFloat() * travel
                    drawCircle(accent.copy(alpha = alpha * 0.95f), 2.2f + (i % 3), Offset(x, y))
                    if (i % 2 == 0) {
                        drawLine(
                            accent.copy(alpha = alpha * 0.72f),
                            Offset(x - 5f, y), Offset(x + 5f, y), 1.8f, StrokeCap.Round
                        )
                        drawLine(
                            accent.copy(alpha = alpha * 0.72f),
                            Offset(x, y - 5f), Offset(x, y + 5f), 1.8f, StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}

// ───────────────────────── MINI PLAYER ─────────────────────────

@Composable
fun MiniPlayer(onClick: () -> Unit) {
    val theme by Settings.theme.collectAsState()
    val song = PlayerClient.currentSong ?: return
    val dur = (if (PlayerClient.durationMs > 0) PlayerClient.durationMs else song.durationSec * 1000).coerceAtLeast(1)
    var dragging by remember(song.url) { mutableStateOf(false) }
    var dragValue by remember(song.url) { mutableFloatStateOf(0f) }
    val shown = if (dragging) dragValue else PlayerClient.positionMs.toFloat()

    var swipeX by remember(song.url) { mutableFloatStateOf(0f) }
    var swipeY by remember(song.url) { mutableFloatStateOf(0f) }
    Column(
        Modifier.fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface.copy(
                    alpha = if (theme == "Ruby") 0.72f else 1f
                )
            )
            .pointerInput(song.url) {
                detectDragGestures(
                    onDragStart = { swipeX = 0f; swipeY = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        swipeX += amount.x
                        swipeY += amount.y
                    },
                    onDragEnd = {
                        when {
                            swipeY < -70f && kotlin.math.abs(swipeY) > kotlin.math.abs(swipeX) -> onClick()
                            swipeX < -80f -> PlayerClient.next()
                            swipeX > 80f -> PlayerClient.prev()
                        }
                        swipeX = 0f; swipeY = 0f
                    },
                    onDragCancel = { swipeX = 0f; swipeY = 0f }
                )
            }
            .clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Art(song.thumb, Modifier.size(42.dp), RoundedCornerShape(8.dp))
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    song.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { PlayerClient.prev() }) {
                Icon(Icons.Filled.SkipPrevious, "Previous song")
            }
            if (PlayerClient.buffering) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = { PlayerClient.toggle() }) {
                    Icon(if (PlayerClient.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause")
                }
            }
            IconButton(onClick = { PlayerClient.seekBy(10_000) }) {
                Icon(Icons.Filled.Forward10, "Forward 10 seconds")
            }
            IconButton(onClick = { PlayerClient.next() }) {
                Icon(Icons.Filled.SkipNext, "Next")
            }
        }

        ReactiveSeekSlider(
            value = shown.coerceIn(0f, dur.toFloat()),
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                PlayerClient.seekTo(dragValue.toLong())
                dragging = false
            },
            valueRange = 0f..dur.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ───────────────────────── FULL PLAYER ─────────────────────────

@Composable
private fun AmbientPlayerBackdrop(modifier: Modifier = Modifier) {
    val enabled by Settings.ambientMode.collectAsState()
    val theme by Settings.theme.collectAsState()
    if (!enabled) return

    val playing = PlayerClient.isPlaying
    val transition = rememberInfiniteTransition(label = "ambient_player_theme")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2.0).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(if (playing) 7200 else 11200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ambient_drift"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.62f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1900), repeatMode = RepeatMode.Reverse),
        label = "ambient_pulse"
    )
    val accent = accentFor(theme)
    val energy = if (playing) 1f else 0.58f

    Canvas(modifier) {
        // Full-player atmosphere: no hard circles. This is deliberately softer than Live Theme
        // because controls and lyrics sit directly above it.
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    accent.copy(alpha = 0.125f * pulse),
                    Color.Transparent,
                    accent.copy(alpha = 0.080f * pulse)
                ),
                start = Offset(size.width * 0.08f, 0f),
                end = Offset(size.width * 0.92f, size.height)
            )
        )

        when (theme) {
            "Ruby" -> {
                repeat(4) { lane ->
                    val path = Path()
                    for (i in 0..24) {
                        val t = i / 24f
                        val x = size.width * t
                        val wave = kotlin.math.sin((t * 7.4f + drift * 0.48f + lane * 0.9f).toDouble()).toFloat()
                        val y = size.height * (0.52f - t * 0.18f) + wave * size.height * (0.034f + lane * 0.005f)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path,
                        accent.copy(alpha = (0.10f + lane * 0.018f) * pulse * energy),
                        style = Stroke(width = 1.2f + lane * 0.25f, cap = StrokeCap.Round)
                    )
                }
                repeat(54) { i ->
                    val x0 = ((i * 43) % 113) / 112f
                    val y0 = ((i * 79) % 127) / 126f
                    val travel = (y0 + drift / (2f * Math.PI.toFloat()) * (0.10f + (i % 7) * 0.018f)) % 1f
                    val x = x0 * size.width + kotlin.math.sin(drift * 0.4f + i * 0.73f) * 7f
                    val y = (1f - travel) * size.height
                    val twinkle = kotlin.math.abs(kotlin.math.sin(drift * 0.72f + i * 0.39f))
                    drawCircle(
                        Color(0xFFFF625C).copy(alpha = (0.10f + twinkle * 0.28f) * energy),
                        1f + (i % 4) * 0.62f,
                        Offset(x, y)
                    )
                }
            }
            "Ocean" -> {
                for (row in 0 until 3) {
                    var prev: Offset? = null
                    val base = size.height * (0.22f + row * 0.24f)
                    for (i in 0..18) {
                        val x = size.width * i / 18f
                        val y = base + kotlin.math.sin(drift * (0.7f + row * 0.08f) + i * 0.45f) * (5f + row * 2f)
                        val now = Offset(x, y)
                        prev?.let { drawLine(accent.copy(alpha = 0.095f * energy), it, now, 1.2f) }
                        prev = now
                    }
                }
                for (i in 0 until 34) {
                    val x0 = ((i * 31) % 101) / 100f
                    val y0 = ((i * 53) % 103) / 102f
                    val travel = (y0 + drift / (2f * Math.PI.toFloat()) * (0.18f + (i % 5) * 0.03f)) % 1f
                    val x = x0 * size.width + kotlin.math.sin(drift + i) * 5f
                    val y = (1f - travel) * size.height
                    drawCircle(accent.copy(alpha = (0.11f + 0.17f * pulse) * energy), 1f + (i % 4) * 0.6f, Offset(x, y))
                }
            }
            "Violet" -> {
                val center = Offset(size.width * 0.52f, size.height * 0.40f)
                for (i in 0 until 38) {
                    val angle = drift * (0.18f + (i % 4) * 0.025f) + i * 0.73f
                    val rx = size.width * (0.10f + (i % 9) * 0.028f)
                    val ry = size.height * (0.04f + (i % 7) * 0.016f)
                    drawCircle(
                        accent.copy(alpha = (0.11f + 0.18f * pulse) * energy),
                        1f + (i % 4) * 0.55f,
                        Offset(center.x + kotlin.math.cos(angle) * rx, center.y + kotlin.math.sin(angle) * ry)
                    )
                }
            }
            "Rose" -> {
                for (i in 0 until 36) {
                    val x0 = ((i * 43) % 103) / 102f
                    val y0 = ((i * 59) % 107) / 106f
                    val travel = (y0 + drift / (2f * Math.PI.toFloat()) * (0.13f + (i % 5) * 0.02f)) % 1f
                    val x = x0 * size.width + kotlin.math.sin(drift * 0.5f + i) * 7f
                    val y = (1f - travel) * size.height
                    drawCircle(accent.copy(alpha = (0.11f + 0.19f * pulse) * energy), 1f + (i % 5) * 0.55f, Offset(x, y))
                }
            }
            "Emerald" -> {
                for (i in 0 until 36) {
                    val x = (((i * 47) % 109) / 108f) * size.width + kotlin.math.cos(drift * 0.55f + i) * 6f
                    val y0 = ((i * 71) % 113) / 112f
                    val travel = (y0 + drift / (2f * Math.PI.toFloat()) * (0.14f + (i % 6) * 0.022f)) % 1f
                    val y = (1f - travel) * size.height
                    val glow = kotlin.math.abs(kotlin.math.sin(drift * 0.7f + i * 0.33f))
                    drawCircle(accent.copy(alpha = (0.095f + 0.21f * glow) * energy), 1f + (i % 4) * 0.65f, Offset(x, y))
                }
            }
            "AMOLED" -> {
                for (i in 0 until 42) {
                    val x = (((i * 37) % 113) / 112f) * size.width
                    val y = (((i * 67) % 127) / 126f) * size.height
                    val twinkle = kotlin.math.abs(kotlin.math.sin(drift * 0.33f + i))
                    drawCircle(Color.White.copy(alpha = (0.06f + twinkle * 0.18f) * energy), 0.8f + (i % 3) * 0.5f, Offset(x, y))
                }
            }
            else -> {
                for (i in 0 until 38) {
                    val x0 = ((i * 41) % 107) / 106f
                    val y0 = ((i * 73) % 109) / 108f
                    val travel = (y0 + drift / (2f * Math.PI.toFloat()) * (0.12f + (i % 6) * 0.022f)) % 1f
                    val x = x0 * size.width + kotlin.math.cos(drift * 0.44f + i) * 7f
                    val y = (1f - travel) * size.height
                    drawCircle(accent.copy(alpha = (0.11f + 0.18f * pulse) * energy), 1f + (i % 5) * 0.55f, Offset(x, y))
                    if (i % 12 == 0) {
                        drawLine(accent.copy(alpha = 0.105f), Offset(x - 5f, y + 5f), Offset(x + 5f, y - 5f), 1f)
                    }
                }
            }
        }

        // Foreground-readability guard; ambient remains visible but never washes out text.
        drawRect(Color.Black.copy(alpha = if (theme == "AMOLED") 0.02f else 0.045f))
    }
}

@Composable
private fun AmbientLyricsPane(song: Song, modifier: Modifier = Modifier) {
    val romanized by Settings.romanizedLyrics.collectAsState()
    val wordByWord by Settings.wordByWordLyrics.collectAsState()
    val theme by Settings.theme.collectAsState()
    val energy by AudioReactive.energy.collectAsState()
    val realCapture by AudioReactive.realCapture.collectAsState()
    val vocalLikelihood by AudioReactive.vocalLikelihood.collectAsState()
    val overlayEnabled by FloatingLyricsController.enabled.collectAsState()
    val progressMap by LyricsProgress.states.collectAsState()
    val state by produceState<LyricsState>(initialValue = LyricsState.Loading, song.url) {
        value = LyricsState.Loading
        val fetched = withContext(Dispatchers.IO) { Lrclib.fetch(song) }
        value = if (fetched == null) LyricsState.None else LyricsState.Ok(fetched)
    }
    val active = lyricsActiveFor(theme, true)
    val main = lyricsMainFor(theme, true)
    val muted = lyricsMutedFor(theme, true)
    val savedOffset = remember(song.url) { Settings.getInt("lyrics_offset_${song.videoId}", 0).toLong() }
    val rawPosition = (PlayerClient.positionMs + savedOffset).coerceAtLeast(0L)
    val currentLyrics = (state as? LyricsState.Ok)?.lyrics
    LaunchedEffect(song.url, currentLyrics) {
        currentLyrics?.let { LyricsPresentationBridge.publish(song.url, it) }
    }
    val position = currentLyrics?.let {
        val timingCapture = FloatingLyricsSyncPolicy.timingCaptureEnabled(
            overlayEnabled = overlayEnabled,
            realCapture = realCapture
        )
        LyricsRuntimeSync.effectivePosition(song.url, rawPosition, it, timingCapture, vocalLikelihood)
    } ?: rawPosition
    val duration = PlayerClient.durationMs.coerceAtLeast((song.durationSec * 1000L).coerceAtLeast(1L))
    val waitingForVocal = position < 0L
    val bounce = if (waitingForVocal) 1f else (1f + (energy * 0.052f)).coerceAtMost(1.06f)

    fun display(text: String): String {
        if (text.isBlank()) return text
        return if (romanized) Romanizer.romanize(text) ?: text else text
    }

    BoxWithConstraints(modifier) {
        val compact = maxWidth < 215.dp
        val spacious = maxWidth > 360.dp
        val currentSize = when { spacious -> 34.sp; compact -> 20.sp; else -> 27.sp }
        val nextSize = when { spacious -> 22.sp; compact -> 14.sp; else -> 18.sp }
        val currentLineHeight = when { spacious -> 42.sp; compact -> 25.sp; else -> 33.sp }
        val nextLineHeight = when { spacious -> 29.sp; compact -> 19.sp; else -> 24.sp }

        Column(
            Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, null, tint = active, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "LIVE LYRICS",
                    style = MaterialTheme.typography.labelMedium,
                    color = active,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.height(10.dp))

            when (val currentState = state) {
                LyricsState.Loading -> {
                    Text(
                        progressMap[song.url] ?: "Lyrics load ho rahe hain…",
                        color = muted,
                        fontSize = nextSize,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                LyricsState.None -> {
                    Text(
                        "Is gaane ke lyrics nahi mile",
                        color = muted,
                        fontSize = nextSize,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                is LyricsState.Ok -> {
                    val lyrics = currentState.lyrics
                    val synced = lyrics.synced
                    val plainLines = lyrics.plain.orEmpty().lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()

                    val rawCurrent: String
                    val rawNext: String
                    val rawAfter: String
                    var currentLine: LyricLine? = null
                    var currentLineEnd = 0L

                    if (synced.isNotEmpty()) {
                        val idx = if (position < 0L) -1 else synced.indexOfLast { it.timeMs <= position }
                        currentLine = synced.getOrNull(idx)
                        rawCurrent = currentLine?.text ?: synced.firstOrNull()?.text.orEmpty()
                        rawNext = if (idx < 0) synced.getOrNull(1)?.text.orEmpty() else synced.getOrNull(idx + 1)?.text.orEmpty()
                        rawAfter = if (idx < 0) synced.getOrNull(2)?.text.orEmpty() else synced.getOrNull(idx + 2)?.text.orEmpty()
                        currentLineEnd = if (idx >= 0) {
                            synced.getOrNull(idx + 1)?.timeMs
                                ?: ((currentLine?.timeMs ?: rawPosition) + estimatedLineDurationMs(rawCurrent))
                        } else 0L
                    } else if (plainLines.isNotEmpty()) {
                        val fraction = ((if (position < 0L) 0L else position).toFloat() / duration.toFloat()).coerceIn(0f, 0.999f)
                        val idx = (fraction * plainLines.size).toInt().coerceIn(0, plainLines.lastIndex)
                        rawCurrent = plainLines[idx]
                        rawNext = plainLines.getOrNull(idx + 1).orEmpty()
                        rawAfter = plainLines.getOrNull(idx + 2).orEmpty()
                    } else {
                        rawCurrent = "Lyrics will appear here"
                        rawNext = ""
                        rawAfter = ""
                    }

                    val currentDisplay = display(rawCurrent)
                    val activeWord = currentLine?.let { line ->
                        val trustworthyWordTiming = line.words.isNotEmpty() ||
                            (lyrics.verified && lyrics.alignmentConfidence >= 60)
                        if (wordByWord && trustworthyWordTiming) activeWordIndex(
                            line = line,
                            displayText = currentDisplay,
                            endMs = currentLineEnd,
                            positionMs = position,
                            allowExactWordTiming = !romanized
                        ) else -1
                    } ?: -1
                    val currentVisual = if (activeWord >= 0) {
                        wordGlowText(
                            currentDisplay, activeWord, active, main.copy(alpha = 0.58f), 1f + energy
                        )
                    } else {
                        AnnotatedString(currentDisplay)
                    }

                    Text(
                        currentVisual,
                        color = if (waitingForVocal) muted.copy(alpha = 0.78f) else main,
                        fontSize = currentSize,
                        lineHeight = currentLineHeight,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = if (compact) 4 else 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.graphicsLayer {
                            scaleX = bounce
                            scaleY = bounce
                        },
                        style = MaterialTheme.typography.headlineSmall.copy(
                            shadow = if (waitingForVocal) Shadow(muted.copy(alpha = 0.24f), blurRadius = 7f)
                            else Shadow(active.copy(alpha = 0.62f), blurRadius = 18f)
                        )
                    )
                    if (rawNext.isNotBlank()) {
                        Text(
                            display(rawNext),
                            color = muted.copy(alpha = 0.86f),
                            fontSize = nextSize,
                            lineHeight = nextLineHeight,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 14.dp),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                shadow = Shadow(active.copy(alpha = 0.27f), blurRadius = 10f)
                            )
                        )
                    }
                    if (rawAfter.isNotBlank() && !compact) {
                        Text(
                            display(rawAfter),
                            color = muted.copy(alpha = 0.52f),
                            fontSize = 14.sp,
                            lineHeight = 19.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 9.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncedSongVideo(
    video: ResolvedVideo,
    modifier: Modifier = Modifier,
    onPlaybackError: () -> Unit = {}
) {
    val context = LocalContext.current
    val exo = remember(video.url) {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(Net.UA)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to "https://www.youtube.com/",
                    "Origin" to "https://www.youtube.com"
                )
            )
        val videoLoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                10_000,
                35_000,
                1_200,
                2_000
            )
            .build()
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .setLoadControl(videoLoadControl)
            .build().apply {
                volume = 0f
                repeatMode = Player.REPEAT_MODE_OFF
                setMediaItem(MediaItem.fromUri(video.url))
                prepare()
                seekTo(PlayerClient.positionMs.coerceAtLeast(0L))
                playWhenReady = PlayerClient.isPlaying
            }
    }

    val currentErrorHandler by rememberUpdatedState(onPlaybackError)
    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                currentErrorHandler()
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    // DeepEcho audio remains the master clock. Avoid frequent hard seeks because they flush
    // the video decoder/buffer and cause visible micro-stutter on phones. Small/medium drift is
    // corrected with tiny playback-speed changes; only large drift uses a hard seek.
    LaunchedEffect(exo, video.url) {
        var lastHardSeekAt = 0L
        while (isActive) {
            val target = PlayerClient.positionMs.coerceAtLeast(0L)
            val driftMs = target - exo.currentPosition
            val masterPlaying = PlayerClient.isPlaying

            exo.volume = 0f

            if (!masterPlaying) {
                if (exo.isPlaying) exo.pause()
                if (abs(driftMs) > 1_200L && exo.playbackState != Player.STATE_BUFFERING) {
                    exo.seekTo(target)
                }
                exo.setPlaybackSpeed(1f)
            } else {
                if (exo.playbackState == Player.STATE_READY && !exo.isPlaying) exo.play()

                val now = android.os.SystemClock.elapsedRealtime()
                when {
                    abs(driftMs) > 4_000L &&
                        exo.playbackState != Player.STATE_BUFFERING &&
                        now - lastHardSeekAt > 1_800L -> {
                        exo.seekTo(target)
                        exo.setPlaybackSpeed(1f)
                        lastHardSeekAt = now
                    }
                    driftMs > 900L -> exo.setPlaybackSpeed(1.03f)
                    driftMs < -900L -> exo.setPlaybackSpeed(0.97f)
                    driftMs > 350L -> exo.setPlaybackSpeed(1.015f)
                    driftMs < -350L -> exo.setPlaybackSpeed(0.985f)
                    else -> exo.setPlaybackSpeed(1f)
                }
            }
            delay(800)
        }
    }

    AndroidView(
        modifier = modifier.clip(RoundedCornerShape(18.dp)),
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                player = exo
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                keepScreenOn = false
            }
        },
        update = { view ->
            if (view.player !== exo) view.player = exo
        }
    )
}

@Composable
private fun AmbientMediaStage(
    song: Song,
    videoEnabled: Boolean,
    onToggleVideo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme by Settings.theme.collectAsState()
    var retryNonce by remember(song.url) { mutableIntStateOf(0) }

    val videoLookup by produceState<Result<ResolvedVideo>?>(
        initialValue = null,
        song.url,
        videoEnabled,
        retryNonce
    ) {
        if (!videoEnabled) {
            value = null
            return@produceState
        }
        // Clear the previous resolved stream first. This is important on retry: YouTube can
        // return the same logical candidate with a refreshed signed URL, and Compose must tear
        // down the failed ExoPlayer before the new attempt is installed.
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching {
                // Prefer a real music-video result, but rank it by duration closeness to the
                // audio that is already playing. This avoids picking a lyric/live/remix upload
                // whose timeline can never stay in sync with the master song clock.
                val candidates = linkedMapOf<String, Song>()
                val queries = listOf(
                    "${song.title} ${song.artist} official music video",
                    "${song.title} ${song.artist} official video",
                    "${song.title} ${song.artist}"
                )
                for (query in queries) {
                    YouTubeApi.searchVideos(query).take(5).forEach { candidate ->
                        if (candidate.url != song.url) candidates.putIfAbsent(candidate.url, candidate)
                    }
                    if (candidates.size >= 9) break
                }

                val targetDuration = song.durationSec.coerceAtLeast(0L)
                val rankedVideoCandidates = candidates.values.sortedWith(
                    compareBy<Song> { candidate ->
                        if (targetDuration <= 0L || candidate.durationSec <= 0L) Long.MAX_VALUE / 4
                        else kotlin.math.abs(candidate.durationSec - targetDuration)
                    }.thenBy { candidate ->
                        // Prefer official-looking uploads when two candidates have similar timing.
                        val text = (candidate.title + " " + candidate.artist).lowercase()
                        when {
                            "official music video" in text -> 0
                            "official video" in text -> 1
                            "official" in text -> 2
                            else -> 3
                        }
                    }
                )

                // First try timeline-compatible music-video results. The currently playing URL is
                // a final fallback because YouTube Music song items often contain only static art.
                val close = if (targetDuration > 0L) {
                    rankedVideoCandidates.filter { candidate ->
                        candidate.durationSec > 0L && kotlin.math.abs(candidate.durationSec - targetDuration) <= 12L
                    }
                } else rankedVideoCandidates
                val ordered = (close + rankedVideoCandidates + song).distinctBy { it.url }

                var lastError: Throwable? = null
                for (candidate in ordered) {
                    try {
                        return@runCatching YouTubeApi.resolveVideo(
                            candidate.url,
                            forceFresh = retryNonce > 0
                        )
                    } catch (t: Throwable) {
                        lastError = t
                    }
                }
                throw (lastError ?: IllegalStateException("Video stream unavailable"))
            }
        }
    }

    BoxWithConstraints(modifier) {
        val compact = maxWidth < 430.dp
        val shortViewport = maxHeight < 360.dp
        val gap = if (compact) 10.dp else 16.dp
        val leftFraction = if (compact) 0.43f else 0.46f
        // Real phones in landscape are much shorter than the emulator window. The old
        // square used the available WIDTH only, so the artwork consumed the full height
        // and pushed the Play/Retry control below the visible viewport. Reserve a fixed
        // control budget first, then size the square from the smaller width/height budget.
        val estimatedLeftWidth = ((maxWidth - gap) * leftFraction).coerceAtLeast(80.dp)
        val controlReserve = if (shortViewport) 84.dp else 96.dp
        val artHeightBudget = (maxHeight - 16.dp - controlReserve).coerceAtLeast(64.dp)
        val artSize = minOf(estimatedLeftWidth, artHeightBudget)
        val videoButtonSize = when {
            shortViewport -> 42.dp
            compact -> 44.dp
            else -> 50.dp
        }
        val videoIconSize = when {
            shortViewport -> 22.dp
            compact -> 24.dp
            else -> 28.dp
        }

        Row(
            Modifier.fillMaxSize().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                Modifier.weight(leftFraction),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier.size(artSize),
                    contentAlignment = Alignment.Center
                ) {
                    val result = videoLookup
                    when {
                        videoEnabled && result?.isSuccess == true -> {
                            SyncedSongVideo(
                                video = result.getOrThrow(),
                                modifier = Modifier.fillMaxSize(),
                                onPlaybackError = { retryNonce += 1 }
                            )
                        }
                        videoEnabled && result?.isFailure == true -> {
                            Art(song.thumb, Modifier.fillMaxSize(), RoundedCornerShape(20.dp))
                            Box(
                                Modifier.fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.42f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Video unavailable\nTap below to retry",
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                        videoEnabled -> {
                            Art(song.thumb, Modifier.fillMaxSize(), RoundedCornerShape(20.dp))
                            CircularProgressIndicator(Modifier.size(34.dp), strokeWidth = 3.dp)
                        }
                        else -> Art(song.thumb, Modifier.fillMaxSize(), RoundedCornerShape(20.dp))
                    }
                }

                val videoFailed = videoEnabled && videoLookup?.isFailure == true
                FilledIconButton(
                    onClick = {
                        if (videoFailed) retryNonce += 1 else onToggleVideo()
                    },
                    modifier = Modifier.padding(top = if (shortViewport) 6.dp else 10.dp).size(videoButtonSize),
                    shape = CircleShape
                ) {
                    Icon(
                        when {
                            videoFailed -> Icons.Filled.Refresh
                            videoEnabled -> Icons.Filled.Pause
                            else -> Icons.Filled.PlayArrow
                        },
                        when {
                            videoFailed -> "Retry synced video"
                            videoEnabled -> "Show cover"
                            else -> "Play synced video"
                        },
                        modifier = Modifier.size(videoIconSize)
                    )
                }
                Text(
                    when {
                        videoEnabled && videoLookup == null -> "Finding video…"
                        videoEnabled && videoLookup?.isSuccess == true -> "Video synced to song"
                        videoEnabled -> "Retry video"
                        else -> "Play video"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = accentFor(theme),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = if (shortViewport) 2.dp else 4.dp)
                )
            }

            AmbientLyricsPane(
                song,
                Modifier.weight(if (compact) 0.57f else 0.54f).fillMaxSize()
            )
        }
    }
}

@Composable
private fun AmbientImmersiveWindow(enabled: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    val keepScreenOn by Settings.keepScreenOnExpanded.collectAsState()

    DisposableEffect(enabled, keepScreenOn, view) {
        val activity = context as? Activity
        if (activity == null) return@DisposableEffect onDispose { }
        val previousOrientation = activity.requestedOrientation
        val controller = WindowInsetsControllerCompat(activity.window, view)

        if (enabled) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (enabled || keepScreenOn) {
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            if (enabled) {
                controller.show(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = previousOrientation
            }
            activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@Composable
private fun AmbientFullscreenPlayer(
    song: Song,
    onExitAmbient: () -> Unit,
    onClosePlayer: () -> Unit
) {
    var videoEnabled by remember(song.url) { mutableStateOf(false) }
    val theme by Settings.theme.collectAsState()
    val dataSaver by Settings.dataSaverMode.collectAsState()
    AmbientImmersiveWindow(true)

    LaunchedEffect(dataSaver) {
        if (dataSaver) videoEnabled = false
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AmbientPlayerBackdrop(Modifier.fillMaxSize())
        AmbientMediaStage(
            song = song,
            videoEnabled = videoEnabled,
            onToggleVideo = {
                if (dataSaver) Bus.toast("Data Saver Mode mein Ambient video off hai")
                else videoEnabled = !videoEnabled
            },
            modifier = Modifier.fillMaxSize().padding(start = 42.dp, end = 42.dp, top = 22.dp, bottom = 70.dp)
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClosePlayer) {
                Icon(Icons.Filled.KeyboardArrowDown, "Close player", tint = accentFor(theme))
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, null, tint = accentFor(theme), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Ambient", color = accentFor(theme), fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Switch(checked = true, onCheckedChange = { enabled -> if (!enabled) onExitAmbient() })
            }
        }

        // Full Ambient transport controls. Keep the five primary actions available without
        // leaving Ambient and scale them down on short/narrow phone landscapes so nothing clips.
        BoxWithConstraints(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            val veryCompact = maxWidth < 540.dp || maxHeight < 300.dp
            val compact = veryCompact || maxWidth < 700.dp || maxHeight < 380.dp
            val sideButton = when {
                veryCompact -> 34.dp
                compact -> 38.dp
                else -> 42.dp
            }
            val sideIcon = when {
                veryCompact -> 21.dp
                compact -> 23.dp
                else -> 25.dp
            }
            val playButton = when {
                veryCompact -> 42.dp
                compact -> 46.dp
                else -> 52.dp
            }
            val playIcon = when {
                veryCompact -> 27.dp
                compact -> 30.dp
                else -> 34.dp
            }
            val gap = if (veryCompact) 1.dp else 4.dp

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    Modifier.weight(1f).padding(bottom = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        song.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!veryCompact) {
                        Text(
                            song.artist,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    IconButton(
                        onClick = { PlayerClient.prev() },
                        modifier = Modifier.size(sideButton)
                    ) {
                        Icon(Icons.Filled.SkipPrevious, "Previous song", Modifier.size(sideIcon), tint = accentFor(theme))
                    }
                    IconButton(
                        onClick = { PlayerClient.seekBy(-10_000) },
                        modifier = Modifier.size(sideButton)
                    ) {
                        Icon(Icons.Filled.Replay10, "Back 10 seconds", Modifier.size(sideIcon), tint = accentFor(theme))
                    }
                    FilledIconButton(
                        onClick = { PlayerClient.toggle() },
                        modifier = Modifier.size(playButton),
                        shape = CircleShape
                    ) {
                        if (PlayerClient.buffering) {
                            CircularProgressIndicator(
                                Modifier.size(if (compact) 22.dp else 26.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                if (PlayerClient.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                "Play/Pause",
                                Modifier.size(playIcon)
                            )
                        }
                    }
                    IconButton(
                        onClick = { PlayerClient.seekBy(10_000) },
                        modifier = Modifier.size(sideButton)
                    ) {
                        Icon(Icons.Filled.Forward10, "Forward 10 seconds", Modifier.size(sideIcon), tint = accentFor(theme))
                    }
                    IconButton(
                        onClick = { PlayerClient.next() },
                        modifier = Modifier.size(sideButton)
                    ) {
                        Icon(Icons.Filled.SkipNext, "Next song", Modifier.size(sideIcon), tint = accentFor(theme))
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerScreen(onClose: () -> Unit) {
    val song = PlayerClient.currentSong
    var showLyrics by remember(song?.url) { mutableStateOf(UiEvents.consumeLyricsRequest()) }
    val liked by Store.liked.collectAsState()
    val downloads by Store.downloads.collectAsState()
    val progress by Downloads.progress.collectAsState()
    var dragging by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showArtist by remember(song?.artist) { mutableStateOf<TopArtist?>(null) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var lyricsNeedsSync by remember(song?.url) { mutableStateOf(false) }
    var lyricsSyncRequest by remember(song?.url) { mutableIntStateOf(0) }
    val autoThemeWithSong by Settings.autoThemeWithSong.collectAsState()
    // Ambient is intentionally session-scoped in the player. A persisted ON value must not
    // reopen the player in forced landscape when the app/player is opened later. The user
    // explicitly opts in each time from the Ambient switch. Keep Settings in sync while this
    // screen is alive because AmbientPlayerBackdrop and existing settings surfaces observe it.
    var ambientMode by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        Settings.setAmbientMode(false)
    }
    val theme by Settings.theme.collectAsState()
    var likeBurst by remember(song?.url) { mutableIntStateOf(0) }
    var downloadBurst by remember(song?.url) { mutableIntStateOf(0) }
    var ambientVideo by remember(song?.url) { mutableStateOf(false) }
    var showSongActions by remember(song?.url) { mutableStateOf(false) }
    val suggestedTheme = PlayerClient.currentSuggestedTheme ?: remember(song?.url) { song?.let(ThemeAdvisor::suggest) }
    val queueRevision = PlayerClient.queueRevision
    val queueCount = remember(queueRevision) {
        PlayerClient.queueSnapshot().count { !it.current }
    }

    val dur = (if (PlayerClient.durationMs > 0) PlayerClient.durationMs else (song?.durationSec ?: 0) * 1000).coerceAtLeast(1)
    val posShown = if (dragging) dragValue.toLong() else PlayerClient.positionMs

    if (showQueue) UpNextDialog(onDismiss = { showQueue = false })
    if (showSongActions && song != null) SongActionsSheet(song, onDismiss = { showSongActions = false })

    if (showArtist != null) {
        ArtistScreen(
            artist = showArtist!!,
            onBack = { showArtist = null },
            onOpenArtist = { showArtist = it },
            fullScreenOverlay = true
        )
        return
    }

    if (ambientMode && song != null) {
        AmbientFullscreenPlayer(
            song = song,
            onExitAmbient = {
                ambientMode = false
                Settings.setAmbientMode(false)
            },
            onClosePlayer = {
                ambientMode = false
                Settings.setAmbientMode(false)
                onClose()
            }
        )
        return
    }

    AmbientImmersiveWindow(false)
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AmbientPlayerBackdrop(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { } }
                .statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.KeyboardArrowDown, "Close", Modifier.size(32.dp)) }
            Text("Now playing", Modifier.weight(1f), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { showLyrics = !showLyrics }) { Text(if (showLyrics) "Artwork" else "Lyrics") }
        }

        // Phone-safe Ambient control: the older layout exposed Ambient only in the
        // artwork-only action row. On real phones that made the control disappear as
        // soon as Lyrics was open even though larger emulator layouts looked fine.
        // Keep this small row outside the lyrics viewport so it is always reachable
        // while viewing lyrics. This changes UI only; playback/lyrics timing is untouched.
        if (showLyrics) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 38.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    "Ambient Mode",
                    tint = if (ambientMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "Ambient",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (ambientMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Switch(
                    checked = ambientMode,
                    onCheckedChange = { enabled ->
                        ambientMode = enabled
                        Settings.setAmbientMode(enabled)
                        if (!enabled) ambientVideo = false
                    },
                    modifier = Modifier.graphicsLayer {
                        scaleX = 0.72f
                        scaleY = 0.72f
                    }
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (song == null) {
                Text("Kuch play nahi ho raha", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (ambientMode && !showLyrics) {
                AmbientMediaStage(
                    song = song,
                    videoEnabled = ambientVideo,
                    onToggleVideo = { ambientVideo = !ambientVideo },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (showLyrics) {
                LyricsView(
                    song = song,
                    syncRequest = lyricsSyncRequest,
                    onSyncNeededChange = { lyricsNeedsSync = it }
                )
            } else {
                Art(song.thumb, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(20.dp))
            }
        }

        if (song != null) {
            Spacer(Modifier.size(if (showLyrics) 5.dp else if (ambientMode) 8.dp else 14.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title,
                        maxLines = if (showLyrics) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                        style = if (showLyrics) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        song.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = if (showLyrics) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val isLiked = liked.any { it.url == song.url }
                Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
                    ThemeActionBurst(likeBurst, theme, "like", Modifier.fillMaxSize())
                    IconButton(onClick = {
                        Store.toggleLike(song)
                        likeBurst += 1
                    }) {
                        Icon(
                            if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            "Like",
                            tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                val downloaded = downloads.any { it.song.url == song.url }
                val p = progress[song.url]
                Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
                    ThemeActionBurst(downloadBurst, theme, "download", Modifier.fillMaxSize())
                    when {
                        p != null -> CircularProgressIndicator(progress = { p }, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        downloaded -> IconButton(onClick = { Store.removeDownload(song.url) }) {
                            Icon(Icons.Filled.DownloadDone, "Downloaded — tap to remove", tint = MaterialTheme.colorScheme.primary)
                        }
                        else -> IconButton(onClick = {
                            downloadBurst += 1
                            Downloads.start(song)
                        }) { Icon(Icons.Filled.Download, "Download") }
                    }
                }
                IconButton(onClick = { showSongActions = true }) {
                    Icon(Icons.Filled.MoreVert, "More")
                }
            }

            if (!showLyrics) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 46.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    TextButton(
                        onClick = { showLyrics = true },
                        modifier = Modifier.weight(0.72f)
                    ) {
                        Text("Lyrics", maxLines = 1)
                    }

                    TextButton(
                        onClick = {
                            if (song.artist.isNotBlank()) {
                                showArtist = TopArtist(song.artist, song.thumb, 0)
                            }
                        },
                        modifier = Modifier.weight(1.15f)
                    ) {
                        Text("About Artist", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    Row(
                        Modifier.weight(0.92f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            "Ambient Mode",
                            tint = if (ambientMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Switch(
                            checked = ambientMode,
                            onCheckedChange = { enabled ->
                                ambientMode = enabled
                                Settings.setAmbientMode(enabled)
                                if (!enabled) ambientVideo = false
                            },
                            modifier = Modifier.graphicsLayer {
                                scaleX = 0.78f
                                scaleY = 0.78f
                            }
                        )
                    }
                }
            }
        }

        if (song != null && suggestedTheme != null) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 30.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (autoThemeWithSong) "Auto theme" else "Suggested theme",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = { Settings.setTheme(suggestedTheme) }
                    ) {
                        Text(
                            suggestedTheme,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (showLyrics && lyricsNeedsSync) {
                    TextButton(
                        onClick = {
                            lyricsSyncRequest += 1
                            lyricsNeedsSync = false
                        }
                    ) {
                        Text(
                            "SYNC",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        ReactiveSeekSlider(
            value = posShown.toFloat().coerceIn(0f, dur.toFloat()),
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = { PlayerClient.seekTo(dragValue.toLong()); dragging = false },
            valueRange = 0f..dur.toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmtTime(posShown), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(fmtTime(dur), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = if (showLyrics) 2.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VerticalVolumeSlider(
                Modifier.width(48.dp).height(if (showLyrics) 82.dp else 94.dp)
            )
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { PlayerClient.seekBy(-10_000) }) {
                    Icon(Icons.Filled.Replay10, "Back 10 seconds")
                }
                IconButton(onClick = { PlayerClient.prev() }, modifier = Modifier.size(46.dp)) {
                    Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(29.dp))
                }
                FilledIconButton(
                    onClick = { PlayerClient.toggle() },
                    modifier = Modifier.size(if (showLyrics) 58.dp else 66.dp),
                    shape = CircleShape
                ) {
                    if (PlayerClient.buffering) {
                        CircularProgressIndicator(
                            Modifier.size(28.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            if (PlayerClient.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            "Play/Pause",
                            Modifier.size(36.dp)
                        )
                    }
                }
                IconButton(onClick = { PlayerClient.next() }, modifier = Modifier.size(46.dp)) {
                    Icon(Icons.Filled.SkipNext, "Next", Modifier.size(29.dp))
                }
                IconButton(onClick = { PlayerClient.seekBy(10_000) }) {
                    Icon(Icons.Filled.Forward10, "Forward 10 seconds")
                }
            }
        }

        if (!showLyrics) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { PlayerClient.toggleShuffle() }) {
                    Icon(
                        Icons.Filled.Shuffle,
                        "Shuffle",
                        tint = if (PlayerClient.shuffle) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { showQueue = true }) {
                    Icon(Icons.Filled.QueueMusic, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(if (queueCount > 0) "Queue ($queueCount)" else "Queue")
                }
                IconButton(onClick = { PlayerClient.cycleRepeat() }) {
                    Icon(
                        if (PlayerClient.repeat == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        "Repeat",
                        tint = if (PlayerClient.repeat == Player.REPEAT_MODE_OFF)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else {
            Spacer(Modifier.height(3.dp))
        }
        }
    }
}

// ───────────────────────── LYRICS ─────────────────────────

private sealed interface LyricsState {
    data object Loading : LyricsState
    data object None : LyricsState
    data class Ok(val lyrics: Lyrics) : LyricsState
}

private fun estimatedLineDurationMs(text: String): Long {
    val words = Regex("\\S+").findAll(text).count().coerceAtLeast(1)
    return (words * 430L).coerceIn(1400L, 6200L)
}

private fun activeWordIndex(
    line: LyricLine,
    displayText: String,
    endMs: Long,
    positionMs: Long,
    allowExactWordTiming: Boolean
): Int {
    val displayWords = Regex("\\S+").findAll(displayText).toList()
    if (displayWords.isEmpty()) return -1

    // Enhanced-LRC timestamps are used directly whenever the displayed text is still the
    // original script. Romanization can change word count, so it safely falls back to weighting.
    if (allowExactWordTiming && line.words.isNotEmpty()) {
        val exact = line.words.indexOfLast { it.timeMs <= positionMs }
        if (exact >= 0) return exact.coerceAtMost(displayWords.lastIndex)
    }

    val safeEnd = endMs.coerceAtLeast(line.timeMs + 500L)
    val fraction = ((positionMs - line.timeMs).toFloat() / (safeEnd - line.timeMs).toFloat()).coerceIn(0f, 0.9999f)

    // Line-level fallback: punctuation and longer words receive a little more time, which
    // tracks sung phrasing better than equal-width splitting while remaining deterministic.
    val weights = displayWords.map { match ->
        val token = match.value
        val letters = token.count { it.isLetterOrDigit() }.coerceAtLeast(1)
        val pause = if (token.endsWith(",") || token.endsWith(";") || token.endsWith(":")) 0.35f
        else if (token.endsWith("!") || token.endsWith("?") || token.endsWith(".")) 0.55f else 0f
        0.78f + letters.coerceAtMost(12) * 0.145f + pause
    }
    val total = weights.sum().coerceAtLeast(0.01f)
    val target = total * fraction
    var running = 0f
    for (i in weights.indices) {
        running += weights[i]
        if (target < running) return i
    }
    return displayWords.lastIndex
}

private fun wordGlowText(
    text: String,
    activeWord: Int,
    activeColor: androidx.compose.ui.graphics.Color,
    futureColor: androidx.compose.ui.graphics.Color,
    glowStrength: Float = 1f
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    builder.append(text)
    val words = Regex("\\S+").findAll(text).toList()
    words.forEachIndexed { index, match ->
        val style = when {
            index == activeWord -> SpanStyle(
                color = activeColor,
                fontWeight = FontWeight.ExtraBold,
                shadow = Shadow(
                    color = activeColor.copy(alpha = (0.84f + 0.15f * glowStrength).coerceIn(0f, 1f)),
                    blurRadius = 24f + 12f * glowStrength
                )
            )
            index < activeWord -> SpanStyle(
                color = activeColor.copy(alpha = 0.76f),
                fontWeight = FontWeight.Bold,
                shadow = Shadow(
                    color = activeColor.copy(alpha = 0.22f),
                    blurRadius = 7f
                )
            )
            else -> SpanStyle(
                color = futureColor.copy(alpha = 0.70f),
                fontWeight = FontWeight.SemiBold
            )
        }
        builder.addStyle(style, match.range.first, match.range.last + 1)
    }
    return builder.toAnnotatedString()
}

@Composable
private fun CompactLyricsToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    textColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .height(30.dp)
            .clip(RoundedCornerShape(50))
            .background(
                if (checked) textColor.copy(alpha = 0.18f)
                else textColor.copy(alpha = 0.07f)
            )
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (checked) "$label •" else label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = if (checked) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
fun LyricsView(
    song: Song,
    syncRequest: Int = 0,
    onSyncNeededChange: (Boolean) -> Unit = {}
) {
    var repairNonce by remember(song.url) { mutableIntStateOf(0) }
    val progressMap by LyricsProgress.states.collectAsState()
    val liveVideoStates by LyricsLiveVideoReader.states.collectAsState()
    var readVideoLyrics by remember(song.url) { mutableStateOf(false) }
    var liveReaderAutoStarted by remember(song.url) { mutableStateOf(false) }
    val lyricVideoEligible = remember(song.url, song.title) { LyricVideoEligibility.isEligible(song) }
    val state by produceState<LyricsState>(LyricsState.Loading, song.url, repairNonce) {
        value = LyricsState.Loading
        val l = withContext(Dispatchers.IO) {
            if (repairNonce > 0) Lrclib.fetchExactRecovery(song) else Lrclib.fetch(song)
        }
        value = if (l == null) LyricsState.None else LyricsState.Ok(l)
    }
    LaunchedEffect(song.url, lyricVideoEligible, state) {
        // Only strict lyric videos auto-start, and only after the normal lyrics engine fails.
        // Once the user turns it off manually we do not keep forcing it back on.
        if (!liveReaderAutoStarted && state is LyricsState.None &&
            LyricVideoEligibility.shouldAutoStart(song, normalLyricsAvailable = false)
        ) {
            liveReaderAutoStarted = true
            readVideoLyrics = true
        }
    }
    DisposableEffect(song.url) {
        onDispose {
            Lrclib.cancelExactRecovery(song.url)
            LyricsLiveVideoReader.stop(song.url)
        }
    }
    LaunchedEffect(readVideoLyrics, song.url) {
        if (readVideoLyrics && lyricVideoEligible) {
            LyricsLiveVideoReader.start(song, PlayerClient.positionMs)
            while (isActive && readVideoLyrics) {
                LyricsLiveVideoReader.updatePosition(song.url, PlayerClient.positionMs)
                delay(450L)
            }
        } else {
            LyricsLiveVideoReader.stop(song.url)
        }
    }
    var offsetMs by remember(song.url) {
        mutableLongStateOf(Settings.getInt("lyrics_offset_${song.videoId}", 0).toLong())
    }
    val context = LocalContext.current
    val overlayEnabled by FloatingLyricsController.enabled.collectAsState()
    val liveTheme by Settings.liveTheme.collectAsState()
    val theme by Settings.theme.collectAsState()
    val wordByWord by Settings.wordByWordLyrics.collectAsState()
    val romanized by Settings.romanizedLyrics.collectAsState()
    val activeColor = lyricsActiveFor(theme, liveTheme)
    val mainColor = lyricsMainFor(theme, liveTheme)
    val mutedColor = lyricsMutedFor(theme, liveTheme)
    val reactiveEnergy by AudioReactive.energy.collectAsState()
    val realCapture by AudioReactive.realCapture.collectAsState()
    val vocalLikelihood by AudioReactive.vocalLikelihood.collectAsState()
    val lyricGlowTransition = rememberInfiniteTransition(label = "lyrics_glow")
    val lyricGlow by lyricGlowTransition.animateFloat(
        initialValue = 0.62f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(680), repeatMode = RepeatMode.Reverse),
        label = "lyrics_glow_strength"
    )

    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) AudioReactive.attachIfPermitted(context)
    }
    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (FloatingLyricsController.canDraw(context)) {
            FloatingLyricsController.start(context)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                AudioReactive.attachIfPermitted(context)
            } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    LaunchedEffect(overlayEnabled, song.url) {
        if (overlayEnabled && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            AudioReactive.attachIfPermitted(context)
        }
    }

    LaunchedEffect(song.url) {
        onSyncNeededChange(false)
    }

    val onFloatingLyricsToggle: (Boolean) -> Unit = { wantOn ->
        if (!wantOn) {
            FloatingLyricsController.stop(context)
        } else if (FloatingLyricsController.canDraw(context)) {
            FloatingLyricsController.start(context)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                AudioReactive.attachIfPermitted(context)
            } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else {
            overlayLauncher.launch(FloatingLyricsController.permissionIntent(context))
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 500.dp || maxWidth < 360.dp
        val lyricFont = if (compact) 17.sp else 21.sp
        val activeLyricFont = if (compact) 23.sp else 28.sp
        val lyricLineHeight = if (compact) 28.sp else 36.sp

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 26.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Live lyrics",
                    style = MaterialTheme.typography.labelLarge,
                    color = activeColor,
                    fontWeight = FontWeight.Bold
                )
            }

            // Keep all optional lyrics tools in one compact, horizontally scrollable strip.
            // This frees vertical space for actual lyric lines on real phone displays.
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactLyricsToggle(
                    "Floating",
                    overlayEnabled,
                    onFloatingLyricsToggle,
                    activeColor
                )
                CompactLyricsToggle(
                    "Word",
                    wordByWord,
                    Settings::setWordByWordLyrics,
                    mainColor
                )
                CompactLyricsToggle(
                    "Romanized",
                    romanized,
                    Settings::setRomanizedLyrics,
                    mainColor
                )
                if (lyricVideoEligible) {
                    CompactLyricsToggle(
                        "Video",
                        readVideoLyrics,
                        { readVideoLyrics = it },
                        mainColor
                    )
                }
            }

            if (lyricVideoEligible && readVideoLyrics) {
                val live = liveVideoStates[song.url]
                if (live != null && live.status.isNotBlank()) {
                    Text(
                        live.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (live.failed) mutedColor else activeColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(top = 1.dp, bottom = 1.dp)
                    )
                }
            }

            val liveLyrics = if (readVideoLyrics && lyricVideoEligible) liveVideoStates[song.url]?.lyrics else null
            val displayState: LyricsState = if (liveLyrics != null) LyricsState.Ok(liveLyrics) else state

            when (val s = displayState) {
                LyricsState.Loading -> Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(10.dp))
                    Text(
                        progressMap[song.url] ?: "Checking lyrics…",
                        color = mutedColor,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }

                LyricsState.None -> Column(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Could not verify lyrics for this version.", color = mutedColor, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { repairNonce += 1 }) { Text("Exact Lyrics / Retry") }
                }

                is LyricsState.Ok -> {
                    // Publish exactly what the main Lyrics UI is rendering so Floating Lyrics
                    // mirrors this resolved payload instead of starting a competing fetch/timeline.
                    LaunchedEffect(song.url, s.lyrics) {
                        LyricsPresentationBridge.publish(song.url, s.lyrics)
                    }
                    val lines = s.lyrics.synced
                    if (lines.isEmpty()) {
                        Column(
                            Modifier.weight(1f).fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(top = 4.dp)
                        ) {
                            val plain = s.lyrics.plain.orEmpty()
                            val displayPlain = if (romanized) {
                                Romanizer.romanize(plain) ?: plain
                            } else {
                                plain
                            }
                            Text(
                                displayPlain,
                                fontSize = if (compact) 17.sp else 18.sp,
                                lineHeight = if (compact) 23.sp else 27.sp,
                                color = mainColor,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    shadow = Shadow(
                                        color = activeColor.copy(alpha = 0.34f),
                                        blurRadius = 9f
                                    )
                                )
                            )
                        }
                    } else {
                        val rawPos = (PlayerClient.positionMs + offsetMs).coerceAtLeast(0L)
                        // Floating Lyrics may turn on Visualizer capture for animation. Do not
                        // let that UI action alter the master lyric timing mode. While the overlay
                        // is active both surfaces use the same resolved timestamps + offset.
                        val timingCapture = FloatingLyricsSyncPolicy.timingCaptureEnabled(
                            overlayEnabled = overlayEnabled,
                            realCapture = realCapture
                        )
                        val pos = LyricsRuntimeSync.effectivePosition(
                            song.url, rawPos, s.lyrics, timingCapture, vocalLikelihood
                        )
                        val idx = if (pos < 0L) -1 else lines.indexOfLast { it.timeMs <= pos }
                        val listState = rememberLazyListState()
                        var autoFollow by remember(song.url) { mutableStateOf(true) }
                        var programmaticScroll by remember(song.url) { mutableStateOf(false) }

                        suspend fun centerActiveLyric(index: Int) {
                            if (index < 0) return
                            // First make the active row visible, then move its actual measured center
                            // to the viewport center. This changes presentation only, never timestamps.
                            listState.animateScrollToItem(index)
                            val info = listState.layoutInfo
                            val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
                            val delta = LyricsUiMath.centerScrollDelta(
                                viewportStart = info.viewportStartOffset,
                                viewportEnd = info.viewportEndOffset,
                                itemOffset = item.offset,
                                itemSize = item.size
                            )
                            if (kotlin.math.abs(delta) > 2) {
                                listState.animateScrollBy(delta.toFloat())
                            }
                        }

                        LaunchedEffect(idx, autoFollow) {
                            if (idx >= 0 && autoFollow) {
                                programmaticScroll = true
                                try {
                                    centerActiveLyric(idx)
                                } finally {
                                    programmaticScroll = false
                                }
                            }
                        }

                        // Manual lyrics scrolling suspends auto-follow. The actual SYNC action is
                        // rendered above the player's seek bar, outside this lyrics viewport.
                        LaunchedEffect(listState) {
                            snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
                                if (scrolling && !programmaticScroll) {
                                    autoFollow = false
                                    onSyncNeededChange(true)
                                }
                            }
                        }

                        LaunchedEffect(syncRequest) {
                            if (syncRequest > 0) {
                                autoFollow = true
                                onSyncNeededChange(false)
                                if (idx >= 0) {
                                    programmaticScroll = true
                                    try {
                                        centerActiveLyric(idx)
                                    } finally {
                                        programmaticScroll = false
                                    }
                                }
                            }
                        }

                        Column(Modifier.fillMaxWidth().padding(bottom = 1.dp)) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(30.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { repairNonce += 1 },
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 7.dp, vertical = 0.dp)
                                ) { Text("Exact", style = MaterialTheme.typography.labelSmall) }
                                TextButton(
                                    onClick = { repairNonce += 1 },
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 7.dp, vertical = 0.dp)
                                ) { Text("Repair", style = MaterialTheme.typography.labelSmall) }
                                TextButton(
                                    onClick = {
                                        repairNonce += 1
                                        autoFollow = true
                                        onSyncNeededChange(false)
                                    },
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 7.dp, vertical = 0.dp)
                                ) { Text("Sync", style = MaterialTheme.typography.labelSmall) }
                                TextButton(
                                    onClick = {
                                        repairNonce += 1
                                        autoFollow = true
                                        onSyncNeededChange(false)
                                    },
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 7.dp, vertical = 0.dp)
                                ) { Text("Re-align", style = MaterialTheme.typography.labelSmall) }
                                TextButton(
                                    onClick = { repairNonce += 1 },
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 7.dp, vertical = 0.dp)
                                ) { Text("Missing", style = MaterialTheme.typography.labelSmall) }
                            }
                            val progress = progressMap[song.url]
                            if (!progress.isNullOrBlank()) {
                                Text(
                                    progress,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = mutedColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }

                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 28.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (pos < 0L) "Instrumental • waiting for vocals" else s.lyrics.displayLabel(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (s.lyrics.estimatedSync) mutedColor else activeColor,
                                modifier = Modifier.weight(1f)
                            )
                            Text("Timing", style = MaterialTheme.typography.bodySmall, color = mutedColor)
                            Text(
                                "−0.5s",
                                color = activeColor,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable {
                                    offsetMs = (offsetMs - 500).coerceIn(-15_000L, 15_000L)
                                    Settings.putInt("lyrics_offset_${song.videoId}", offsetMs.toInt())
                                }.padding(horizontal = 7.dp, vertical = 5.dp)
                            )
                            Text(
                                "%+.1fs".format(offsetMs / 1000f),
                                style = MaterialTheme.typography.bodySmall,
                                color = mainColor
                            )
                            Text(
                                "+0.5s",
                                color = activeColor,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable {
                                    offsetMs = (offsetMs + 500).coerceIn(-15_000L, 15_000L)
                                    Settings.putInt("lyrics_offset_${song.videoId}", offsetMs.toInt())
                                }.padding(horizontal = 7.dp, vertical = 5.dp)
                            )
                        }

                        BoxWithConstraints(
                            modifier = Modifier.weight(1f).fillMaxWidth().heightIn(min = 150.dp)
                        ) {
                            val centerPad = ((maxHeight / 2) - if (compact) 30.dp else 38.dp)
                                .coerceAtLeast(24.dp)
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    top = centerPad,
                                    bottom = centerPad
                                )
                            ) {
                                itemsIndexed(lines) { i, l ->
                                val isActive = i == idx
                                val displayText = if (romanized) {
                                    Romanizer.romanize(l.text) ?: l.text
                                } else {
                                    l.text
                                }
                                val endMs = lines.getOrNull(i + 1)?.timeMs
                                    ?: (l.timeMs + estimatedLineDurationMs(displayText))
                                val trustworthyWordTiming = l.words.isNotEmpty() ||
                                    (s.lyrics.verified && s.lyrics.alignmentConfidence >= 60)
                                val wordIndex = if (isActive && wordByWord && trustworthyWordTiming) {
                                    activeWordIndex(
                                        line = l,
                                        displayText = displayText,
                                        endMs = endMs,
                                        positionMs = pos,
                                        allowExactWordTiming = !romanized
                                    )
                                } else {
                                    -1
                                }
                                val shownText = if (isActive && wordByWord && wordIndex >= 0) {
                                    wordGlowText(
                                        displayText,
                                        wordIndex,
                                        activeColor,
                                        mainColor,
                                        lyricGlow
                                    )
                                } else {
                                    AnnotatedString(displayText)
                                }

                                Column(
                                    Modifier.fillMaxWidth()
                                        .clickable {
                                            autoFollow = true
                                            onSyncNeededChange(false)
                                            PlayerClient.seekTo(l.timeMs - offsetMs)
                                        }
                                        .padding(
                                            vertical = if (isActive) { if (compact) 6.dp else 8.dp } else { if (compact) 3.dp else 4.dp },
                                            horizontal = 6.dp
                                        )
                                        .graphicsLayer {
                                            if (isActive) {
                                                val e = reactiveEnergy.coerceIn(0f, 1f)
                                                scaleX = 1.018f + e * 0.020f
                                                scaleY = 1.018f + e * 0.050f
                                                translationY = -e * 2.4f
                                            }
                                        }
                                ) {
                                    Text(
                                        shownText,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center,
                                        fontSize = if (isActive) activeLyricFont else lyricFont,
                                        lineHeight = lyricLineHeight,
                                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        color = if (isActive && !wordByWord) {
                                            activeColor
                                        } else {
                                            mainColor.copy(alpha = if (isActive) 1f else 0.92f)
                                        },
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            shadow = when {
                                                isActive -> Shadow(
                                                    color = activeColor.copy(
                                                        alpha = (0.66f + 0.20f * lyricGlow + 0.18f * reactiveEnergy).coerceIn(0f, 1f)
                                                    ),
                                                    blurRadius = if (wordByWord) {
                                                        24f + 11f * lyricGlow + 9f * reactiveEnergy
                                                    } else {
                                                        27f + 12f * lyricGlow + 10f * reactiveEnergy
                                                    }
                                                )
                                                liveTheme -> Shadow(
                                                    color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f),
                                                    blurRadius = 5f
                                                )
                                                else -> null
                                            }
                                        )
                                    )
                                }
                            }
                        }
                        }
                    }
                }
            }
        }
    }
}
