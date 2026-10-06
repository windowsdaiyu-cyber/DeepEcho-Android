package com.deepecho.mobile.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.deepecho.mobile.data.Settings
import com.deepecho.mobile.data.Song
import com.deepecho.mobile.lyrics.LyricsPresentationBridge
import com.deepecho.mobile.net.Bus
import com.deepecho.mobile.player.PlayerClient
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ShareCardStyleV1128(val label: String) { Artwork("Artwork"), Lyrics("Lyrics"), Minimal("Minimal"), Theme("Theme") }

private suspend fun loadShareArtwork(context: Context, url: String?): Bitmap? = withContext(Dispatchers.IO) {
    if (url.isNullOrBlank()) return@withContext null
    runCatching {
        val result = context.imageLoader.execute(
            ImageRequest.Builder(context).data(url).allowHardware(false).size(1080).build()
        ) as? SuccessResult ?: return@runCatching null
        result.drawable.toBitmap(1080, 1080)
    }.getOrNull()
}

private fun Canvas.drawCenterCrop(bitmap: Bitmap, dst: RectF, paint: Paint) {
    val srcRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
    val dstRatio = dst.width() / dst.height()
    val src = if (srcRatio > dstRatio) {
        val wanted = (bitmap.height * dstRatio).toInt()
        val left = (bitmap.width - wanted) / 2
        Rect(left, 0, left + wanted, bitmap.height)
    } else {
        val wanted = (bitmap.width / dstRatio).toInt()
        val top = (bitmap.height - wanted) / 2
        Rect(0, top, bitmap.width, top + wanted)
    }
    drawBitmap(bitmap, src, dst, paint)
}

private fun Canvas.drawWrappedText(
    text: String,
    x: Float,
    y: Float,
    maxWidth: Float,
    paint: Paint,
    maxLines: Int = 3,
    lineGap: Float = 1.18f
): Float {
    var yy = y
    var remaining = text.trim().replace(Regex("\\s+"), " ")
    repeat(maxLines) { index ->
        if (remaining.isBlank()) return yy
        var count = paint.breakText(remaining, true, maxWidth, null).coerceAtLeast(1)
        if (count < remaining.length) {
            val candidate = remaining.substring(0, count)
            val cut = candidate.lastIndexOf(' ').takeIf { it > count / 2 } ?: count
            count = cut
        }
        var line = remaining.substring(0, count).trim()
        remaining = remaining.substring(count).trim()
        if (index == maxLines - 1 && remaining.isNotBlank()) line = line.trimEnd('.', '…') + "…"
        drawText(line, x, yy, paint)
        yy += paint.textSize * lineGap
    }
    return yy
}

private suspend fun generateShareCard(
    context: Context,
    song: Song,
    style: ShareCardStyleV1128,
    lyric: String?
): File = withContext(Dispatchers.IO) {
    val palette = paletteFor(Settings.theme.value)
    val bg = palette.bg.toArgb()
    val accent = palette.primary.toArgb()
    val text = palette.textMain.toArgb()
    val muted = palette.textMuted.toArgb()
    val artwork = loadShareArtwork(context, song.thumb)
    val bitmap = Bitmap.createBitmap(1080, 1350, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(bg)

    val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val artRect = when (style) {
        ShareCardStyleV1128.Artwork -> RectF(60f, 60f, 1020f, 1020f)
        ShareCardStyleV1128.Lyrics -> RectF(0f, 0f, 1080f, 690f)
        ShareCardStyleV1128.Minimal -> RectF(330f, 160f, 750f, 580f)
        ShareCardStyleV1128.Theme -> RectF(90f, 120f, 990f, 920f)
    }
    if (artwork != null) {
        canvas.drawCenterCrop(artwork, artRect, p)
    } else {
        p.color = accent
        p.alpha = 70
        canvas.drawRoundRect(artRect, 44f, 44f, p)
        p.alpha = 255
    }

    if (style == ShareCardStyleV1128.Lyrics && artwork != null) {
        p.color = bg
        p.alpha = 130
        canvas.drawRect(0f, 500f, 1080f, 710f, p)
        p.alpha = 255
    }
    if (style == ShareCardStyleV1128.Theme) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = 7f
        p.color = accent
        p.alpha = 170
        canvas.drawRoundRect(artRect, 52f, 52f, p)
        p.style = Paint.Style.FILL
        p.alpha = 255
        canvas.drawCircle(930f, 1050f, 82f, p)
        p.color = bg
        canvas.drawCircle(930f, 1050f, 45f, p)
    }

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = text
        textSize = if (style == ShareCardStyleV1128.Minimal) 54f else 62f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = 38f }
    val lyricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = text
        textSize = 48f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; textSize = 30f }

    var y = when (style) {
        ShareCardStyleV1128.Lyrics -> 765f
        ShareCardStyleV1128.Minimal -> 690f
        else -> 1110f
    }
    if (style == ShareCardStyleV1128.Lyrics && !lyric.isNullOrBlank()) {
        y = canvas.drawWrappedText("“$lyric”", 70f, y, 940f, lyricPaint, maxLines = 3)
        y += 28f
    }
    y = canvas.drawWrappedText(song.title, 70f, y, 940f, titlePaint, maxLines = 2)
    canvas.drawWrappedText(song.artist, 70f, y + 8f, 940f, artistPaint, maxLines = 1)
    canvas.drawText("DEEP-ECHO", 70f, 1300f, brandPaint)

    val outDir = File(context.cacheDir, "share_cards").apply { mkdirs() }
    val out = File(outDir, "deepecho-${System.currentTimeMillis()}.png")
    FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 95, it) }
    bitmap.recycle()
    out
}

@Composable
fun ShareCardDialogV1128(song: Song, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resolvedLyric = remember(song.url, PlayerClient.positionMs) {
        LyricsPresentationBridge.lineAt(song.url, PlayerClient.positionMs)
    }
    var style by remember { mutableStateOf(if (resolvedLyric != null) ShareCardStyleV1128.Lyrics else ShareCardStyleV1128.Artwork) }
    var working by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text("Share song card") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Choose a DeepEcho style. Sharing uses Android's native share sheet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShareCardStyleV1128.entries.forEach { candidate ->
                        if (candidate != ShareCardStyleV1128.Lyrics || resolvedLyric != null) {
                            FilterChip(
                                selected = style == candidate,
                                onClick = { style = candidate },
                                label = { Text(candidate.label) }
                            )
                        }
                    }
                }
                if (style == ShareCardStyleV1128.Lyrics && resolvedLyric != null) {
                    Text("“$resolvedLyric”", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                } else if (resolvedLyric == null) {
                    Text("No already-resolved lyric line is available, so DeepEcho will share a normal song card.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !working, onClick = {
                working = true
                scope.launch {
                    runCatching { generateShareCard(context, song, style, resolvedLyric) }
                        .onSuccess { file ->
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_TEXT, "${song.title} — ${song.artist}\nShared from DeepEcho")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, "Share with"))
                            onDismiss()
                        }
                        .onFailure { Bus.toast("Share card failed: ${it.message ?: "unknown error"}") }
                    working = false
                }
            }) { Text(if (working) "Creating…" else "Share") }
        },
        dismissButton = { TextButton(enabled = !working, onClick = onDismiss) { Text("Cancel") } }
    )
}
