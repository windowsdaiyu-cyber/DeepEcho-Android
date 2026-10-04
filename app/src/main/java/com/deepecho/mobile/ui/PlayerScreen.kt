package com.deepecho.mobile.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Shadow
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
import androidx.media3.common.Player
import com.deepecho.mobile.data.Settings
import com.deepecho.mobile.data.Song
import com.deepecho.mobile.data.Store
import com.deepecho.mobile.data.ThemeAdvisor
import com.deepecho.mobile.data.TopArtist
import com.deepecho.mobile.net.Downloads
import com.deepecho.mobile.net.Lrclib
import com.deepecho.mobile.net.Lyrics
import com.deepecho.mobile.overlay.FloatingLyricsController
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
            IconButton(onClick = { PlayerClient.seekBy(-10_000) }) {
                Icon(Icons.Filled.Replay10, "Back 10 seconds")
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

    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                IconButton(onClick = { Store.toggleLike(song) }) {
                    Icon(
                        if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        "Like",
                        tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                val downloaded = downloads.any { it.song.url == song.url }
                val p = progress[song.url]
                when {
                    p != null -> CircularProgressIndicator(progress = { p }, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    downloaded -> IconButton(onClick = { Store.removeDownload(song.url) }) {
                        Icon(Icons.Filled.DownloadDone, "Downloaded — tap to remove", tint = MaterialTheme.colorScheme.primary)
                    }
                    else -> IconButton(onClick = { Downloads.start(song) }) { Icon(Icons.Filled.Download, "Download") }
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

private fun activeWordIndex(text: String, startMs: Long, endMs: Long, positionMs: Long): Int {
    val words = Regex("\\S+").findAll(text).toList()
    if (words.isEmpty()) return -1
    val safeEnd = endMs.coerceAtLeast(startMs + 500L)
    val fraction = ((positionMs - startMs).toFloat() / (safeEnd - startMs).toFloat()).coerceIn(0f, 0.9999f)

    // Line-level lyrics do not contain true word timestamps. Give longer words slightly
    // more of the line duration instead of splitting every word equally.
    val weights = words.map { match ->
        val letters = match.value.count { it.isLetterOrDigit() }.coerceAtLeast(1)
        (0.85f + letters.coerceAtMost(10) * 0.14f)
    }
    val total = weights.sum().coerceAtLeast(0.01f)
    val target = total * fraction
    var running = 0f
    for (i in weights.indices) {
        running += weights[i]
        if (target < running) return i
    }
    return words.lastIndex
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
    val lyricGlowTransition = rememberInfiniteTransition(label = "lyrics_glow")
    val lyricGlow by lyricGlowTransition.animateFloat(
        initialValue = 0.62f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(680), repeatMode = RepeatMode.Reverse),
        label = "lyrics_glow_strength"
    )

    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (FloatingLyricsController.canDraw(context)) FloatingLyricsController.start(context)
    }

    LaunchedEffect(song.url) {
        onSyncNeededChange(false)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 470.dp
        val lyricFont = if (compact) 20.sp else 23.sp
        val activeLyricFont = if (compact) 22.sp else 25.sp
        val lyricLineHeight = if (compact) 26.sp else 31.sp

        Column(Modifier.fillMaxSize()) {
            // Visualizer intentionally removed. Lyrics get the reclaimed vertical space.
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
                Text(
                    if (overlayEnabled) "Floating lyrics: ON" else "Floating lyrics",
                    color = activeColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (overlayEnabled) {
                                FloatingLyricsController.stop(context)
                            } else if (FloatingLyricsController.canDraw(context)) {
                                FloatingLyricsController.start(context)
                            } else {
                                overlayLauncher.launch(FloatingLyricsController.permissionIntent(context))
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                )
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
                                    activeWordIndex(displayText, l.timeMs, endMs, pos)
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
                                                        alpha = (0.72f + 0.26f * lyricGlow).coerceIn(0f, 1f)
                                                    ),
                                                    blurRadius = if (wordByWord) {
                                                        19f + 10f * lyricGlow
                                                    } else {
                                                        21f + 12f * lyricGlow
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
