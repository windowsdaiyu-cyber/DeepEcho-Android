package com.deepecho.mobile.ui

import android.Manifest
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import com.deepecho.mobile.data.Settings
import com.deepecho.mobile.data.Song
import com.deepecho.mobile.data.Store
import com.deepecho.mobile.data.ThemeAdvisor
import com.deepecho.mobile.data.TopArtist
import com.deepecho.mobile.net.Downloads
import com.deepecho.mobile.net.Lrclib
import com.deepecho.mobile.net.Lyrics
import com.deepecho.mobile.net.LyricLine
import com.deepecho.mobile.overlay.FloatingLyricsController
import com.deepecho.mobile.player.AudioReactive
import com.deepecho.mobile.player.PlayerClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

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
    val song = PlayerClient.currentSong ?: return
    val dur = (if (PlayerClient.durationMs > 0) PlayerClient.durationMs else song.durationSec * 1000).coerceAtLeast(1)
    var dragging by remember(song.url) { mutableStateOf(false) }
    var dragValue by remember(song.url) { mutableFloatStateOf(0f) }
    val shown = if (dragging) dragValue else PlayerClient.positionMs.toFloat()

    var swipeX by remember(song.url) { mutableFloatStateOf(0f) }
    var swipeY by remember(song.url) { mutableFloatStateOf(0f) }
    Column(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
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
fun PlayerScreen(onClose: () -> Unit) {
    val song = PlayerClient.currentSong
    var showLyrics by remember { mutableStateOf(false) }
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
    val ambientMode by Settings.ambientMode.collectAsState()
    val theme by Settings.theme.collectAsState()
    var likeBurst by remember(song?.url) { mutableIntStateOf(0) }
    var downloadBurst by remember(song?.url) { mutableIntStateOf(0) }
    val suggestedTheme = PlayerClient.currentSuggestedTheme ?: remember(song?.url) { song?.let(ThemeAdvisor::suggest) }
    val queueRevision = PlayerClient.queueRevision
    val queueCount = remember(queueRevision) {
        PlayerClient.queueSnapshot().count { !it.current }
    }

    val dur = (if (PlayerClient.durationMs > 0) PlayerClient.durationMs else (song?.durationSec ?: 0) * 1000).coerceAtLeast(1)
    val posShown = if (dragging) dragValue.toLong() else PlayerClient.positionMs

    if (showQueue) UpNextDialog(onDismiss = { showQueue = false })

    if (showArtist != null) {
        ArtistScreen(
            artist = showArtist!!,
            onBack = { showArtist = null },
            onOpenArtist = { showArtist = it }
        )
        return
    }

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

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (song == null) {
                Text("Kuch play nahi ho raha", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Spacer(Modifier.size(if (showLyrics) 5.dp else 16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    if (!showLyrics) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { showLyrics = true }) { Text("Lyrics") }
                            TextButton(
                                onClick = {
                                    if (song.artist.isNotBlank()) {
                                        showArtist = TopArtist(song.artist, song.thumb, 0)
                                    }
                                }
                            ) {
                                Text("About Artist")
                            }
                        }
                    }
                }
                val isLiked = liked.any { it.url == song.url }
                Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
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
                Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
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
                IconButton(onClick = { Settings.setAmbientMode(!ambientMode) }) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        if (ambientMode) "Ambient mode on" else "Ambient mode off",
                        tint = if (ambientMode) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
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
private fun LyricsSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    textColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(6.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun LyricsView(
    song: Song,
    syncRequest: Int = 0,
    onSyncNeededChange: (Boolean) -> Unit = {}
) {
    val state by produceState<LyricsState>(LyricsState.Loading, song.url) {
        value = LyricsState.Loading
        val l = withContext(Dispatchers.IO) { Lrclib.fetch(song) }
        value = if (l == null) LyricsState.None else LyricsState.Ok(l)
    }
    var offsetMs by remember(song.url) { mutableLongStateOf(0L) }
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

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 500.dp || maxWidth < 360.dp
        val lyricFont = if (compact) 18.sp else 22.sp
        val activeLyricFont = if (compact) 21.sp else 25.sp
        val lyricLineHeight = if (compact) 24.sp else 31.sp

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 34.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Live lyrics",
                    style = MaterialTheme.typography.labelLarge,
                    color = activeColor,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Floating lyrics",
                        color = activeColor,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(6.dp))
                    Switch(
                        checked = overlayEnabled,
                        onCheckedChange = { wantOn ->
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
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().heightIn(min = 38.dp).padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LyricsSwitch(
                    "Word by Word",
                    wordByWord,
                    Settings::setWordByWordLyrics,
                    mainColor,
                    Modifier.weight(1f)
                )
                LyricsSwitch(
                    "Romanized",
                    romanized,
                    Settings::setRomanizedLyrics,
                    mainColor,
                    Modifier.weight(1f)
                )
            }

            when (val s = state) {
                LyricsState.Loading -> Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                LyricsState.None -> Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Is gaane ke lyrics nahi mile", color = mutedColor)
                }

                is LyricsState.Ok -> {
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
                        val pos = PlayerClient.positionMs + offsetMs
                        val idx = lines.indexOfLast { it.timeMs <= pos }
                        val listState = rememberLazyListState()
                        var autoFollow by remember(song.url) { mutableStateOf(true) }
                        var programmaticScroll by remember(song.url) { mutableStateOf(false) }

                        LaunchedEffect(idx, autoFollow) {
                            if (idx >= 0 && autoFollow) {
                                programmaticScroll = true
                                try {
                                    listState.animateScrollToItem((idx - 2).coerceAtLeast(0))
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
                                        listState.animateScrollToItem((idx - 2).coerceAtLeast(0))
                                    } finally {
                                        programmaticScroll = false
                                    }
                                }
                            }
                        }

                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 32.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (s.lyrics.estimatedSync) "Smart sync • estimated" else "${s.lyrics.source} • ${s.lyrics.confidence}% match",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (s.lyrics.estimatedSync) mutedColor else activeColor,
                                modifier = Modifier.weight(1f)
                            )
                            Text("Timing", style = MaterialTheme.typography.bodySmall, color = mutedColor)
                            Text(
                                "−0.5s",
                                color = activeColor,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { offsetMs -= 500 }.padding(horizontal = 7.dp, vertical = 5.dp)
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
                                modifier = Modifier.clickable { offsetMs += 500 }.padding(horizontal = 7.dp, vertical = 5.dp)
                            )
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f).fillMaxWidth().heightIn(min = 150.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                top = 3.dp,
                                bottom = 14.dp
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
                                val wordIndex = if (isActive && wordByWord) {
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
                                            vertical = if (compact) 4.dp else 6.dp,
                                            horizontal = 3.dp
                                        )
                                        .graphicsLayer {
                                            if (isActive) {
                                                val e = reactiveEnergy.coerceIn(0f, 1f)
                                                scaleX = 1f + e * 0.018f
                                                scaleY = 1f + e * 0.045f
                                                translationY = -e * 3.2f
                                            }
                                        }
                                ) {
                                    Text(
                                        shownText,
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
                                                        19f + 9f * lyricGlow + 8f * reactiveEnergy
                                                    } else {
                                                        21f + 10f * lyricGlow + 9f * reactiveEnergy
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
