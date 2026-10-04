package com.deepecho.mobile.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.IBinder
import android.provider.Settings as AndroidSettings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.deepecho.mobile.data.Settings
import com.deepecho.mobile.net.Lrclib
import com.deepecho.mobile.net.Lyrics
import com.deepecho.mobile.player.AudioReactive
import com.deepecho.mobile.player.PlayerClient
import com.deepecho.mobile.ui.Romanizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object FloatingLyricsController {
    val enabled = MutableStateFlow(false)

    fun canDraw(context: Context): Boolean = AndroidSettings.canDrawOverlays(context)

    fun permissionIntent(context: Context): Intent = Intent(
        AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )

    fun start(context: Context): Boolean {
        if (!canDraw(context)) return false
        context.startService(Intent(context, FloatingLyricsService::class.java))
        enabled.value = true
        return true
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, FloatingLyricsService::class.java))
        enabled.value = false
    }
}

class FloatingLyricsService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loop: Job? = null
    private var windowManager: WindowManager? = null
    private var root: View? = null
    private var titleView: TextView? = null
    private var lyricView: TextView? = null
    private var nextView: TextView? = null
    private var playPauseView: TextView? = null
    private var graphView: ReactiveGraphView? = null
    private var songUrl: String? = null
    private var lyrics: Lyrics? = null
    private var lastLine: String? = null
    private var lastCaptureAttemptAt: Long = 0L

    override fun onCreate() {
        super.onCreate()
        if (!AndroidSettings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        FloatingLyricsController.enabled.value = true
        AudioReactive.attachIfPermitted(this)
        createOverlay()
        loop = scope.launch {
            while (isActive) {
                refreshLyrics()
                delay(120)
            }
        }
    }

    /**
     * PC-style transparent waveform for Floating Lyrics only.
     * The view never paints a background/card: only a faint baseline, soft glow and a thin
     * theme-coloured waveform are drawn over the existing overlay. This keeps the graph feeling
     * like the desktop floating-lyrics visualizer instead of a row of opaque equalizer bars.
     */
    private inner class ReactiveGraphView(context: Context, private val colors: OverlayColors) : View(context) {
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val path = Path()
        private var values: List<Float> = List(24) { 0.08f }
        private var strength: Float = 0.08f

        init {
            setBackgroundColor(Color.TRANSPARENT)
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }

        fun setSignal(next: List<Float>, energy: Float) {
            values = if (next.isEmpty()) List(24) { 0.08f } else next
            strength = energy.coerceIn(0f, 1f)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (width <= 1 || height <= 1 || values.isEmpty()) return

            val centerY = height * 0.52f
            val usableHeight = height * 0.72f
            val count = values.size.coerceAtLeast(2)
            val step = width.toFloat() / (count - 1).toFloat()
            val phase = ((PlayerClient.positionMs % 2400L) / 2400f) * Math.PI.toFloat() * 2f

            baselinePaint.color = Color.argb(
                (22 + strength * 28).toInt().coerceIn(18, 56),
                Color.red(colors.accent),
                Color.green(colors.accent),
                Color.blue(colors.accent)
            )
            baselinePaint.strokeWidth = dp(1).coerceAtLeast(1).toFloat()
            canvas.drawLine(0f, centerY, width.toFloat(), centerY, baselinePaint)

            path.reset()
            values.forEachIndexed { index, raw ->
                val x = index * step
                val signed = if (index % 2 == 0) 1f else -1f
                val naturalWave = kotlin.math.sin((index * 0.72f + phase).toDouble()).toFloat() * 0.18f
                val amplitude = (raw.coerceIn(0.025f, 1f) * 0.78f + naturalWave).coerceIn(-1f, 1f)
                val y = centerY - signed * amplitude * usableHeight * (0.24f + strength * 0.24f)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            // Wide, low-alpha glow first. This is intentionally translucent and has no fill.
            glowPaint.color = Color.argb(
                (26 + strength * 66).toInt().coerceIn(22, 96),
                Color.red(colors.accent),
                Color.green(colors.accent),
                Color.blue(colors.accent)
            )
            glowPaint.strokeWidth = dp(6).toFloat()
            glowPaint.setShadowLayer(dp(7).toFloat(), 0f, 0f, colors.accent)
            canvas.drawPath(path, glowPaint)

            // Crisp PC-style waveform line above the glow.
            linePaint.shader = LinearGradient(
                0f,
                0f,
                width.toFloat(),
                0f,
                intArrayOf(
                    Color.argb(84, Color.red(colors.accent), Color.green(colors.accent), Color.blue(colors.accent)),
                    Color.argb((132 + strength * 88).toInt().coerceIn(132, 220), Color.red(colors.accent), Color.green(colors.accent), Color.blue(colors.accent)),
                    Color.argb(92, Color.red(colors.accent), Color.green(colors.accent), Color.blue(colors.accent))
                ),
                null,
                Shader.TileMode.CLAMP
            )
            linePaint.strokeWidth = dp(2).coerceAtLeast(2).toFloat()
            linePaint.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            canvas.drawPath(path, linePaint)
            linePaint.shader = null
        }
    }

    private fun createOverlay() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val colors = themeColors(Settings.theme.value)
        val accent = colors.accent

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(12))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0x6A080808, 0x4A111111, 0x30080808)
            ).apply {
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), accent)
            }
            elevation = dp(8).toFloat()
        }

        val title = TextView(this).apply {
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.04f
            setTextColor(colors.muted)
            maxLines = 1
        }

        val lyric = TextView(this).apply {
            textSize = 21f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.01f
            setTextColor(colors.main)
            maxLines = 2
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.08f)
            setShadowLayer(dp(5).toFloat(), 0f, dp(1).toFloat(), colors.accent)
            setPadding(0, dp(4), 0, 0)
        }

        val next = TextView(this).apply {
            textSize = 13f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            letterSpacing = 0.015f
            setTextColor(colors.muted)
            maxLines = 1
            gravity = Gravity.CENTER
            setPadding(0, dp(3), 0, 0)
        }

        val graph = ReactiveGraphView(this, colors).apply {
            // Transparent PC-style waveform: no card/background, only the reactive line/glow.
            alpha = 0.82f
        }

        fun controlButton(label: String, description: String, action: () -> Unit): TextView =
            TextView(this).apply {
                text = label
                contentDescription = description
                textSize = 13f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setTextColor(colors.main)
                gravity = Gravity.CENTER
                setPadding(dp(5), dp(7), dp(5), dp(7))
                background = GradientDrawable().apply {
                    cornerRadius = dp(13).toFloat()
                    setColor(Color.argb(34, Color.red(colors.accent), Color.green(colors.accent), Color.blue(colors.accent)))
                    setStroke(dp(1), colors.accent)
                }
                setOnClickListener { action() }
            }

        val back10 = controlButton("↶10", "Back 10 seconds") {
            PlayerClient.seekBy(-10_000)
        }
        val previous = controlButton("⏮", "Previous") {
            PlayerClient.prev()
        }
        val playPause = controlButton("▶", "Play or pause") {
            PlayerClient.toggle()
        }
        val nextSong = controlButton("⏭", "Next") {
            PlayerClient.next()
        }
        val forward10 = controlButton("10↷", "Forward 10 seconds") {
            PlayerClient.seekBy(10_000)
        }

        fun controlParams() =
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(7), 0, 0)
            addView(back10, controlParams())
            addView(previous, controlParams())
            addView(playPause, controlParams())
            addView(nextSong, controlParams())
            addView(forward10, controlParams())
        }

        val close = TextView(this).apply {
            text = "×"
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(accent)
            gravity = Gravity.END
            setPadding(dp(10), 0, 0, 0)
            setOnClickListener { FloatingLyricsController.stop(this@FloatingLyricsService) }
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(close, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        val accentLine = View(this).apply {
            setBackgroundColor(accent)
            alpha = 0.75f
        }

        container.addView(top)
        container.addView(
            accentLine,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(2)).apply {
                topMargin = dp(4)
                bottomMargin = dp(3)
            }
        )
        container.addView(lyric)
        container.addView(
            graph,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(28)).apply {
                topMargin = dp(2)
                bottomMargin = 0
            }
        )
        container.addView(next)
        container.addView(controls)

        titleView = title
        lyricView = lyric
        nextView = next
        playPauseView = playPause
        graphView = graph

        val overlayWidth = kotlin.math.min(dp(340), (resources.displayMetrics.widthPixels - dp(18)).coerceAtLeast(dp(260)))
        val lp = WindowManager.LayoutParams(
            overlayWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(86)
        }

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        container.setOnTouchListener { _: View, event: MotionEvent ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x
                    startY = lp.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = startX + (event.rawX - touchX).toInt()
                    lp.y = startY + (event.rawY - touchY).toInt()
                    runCatching { wm.updateViewLayout(container, lp) }
                    true
                }
                else -> false
            }
        }

        root = container
        wm.addView(container, lp)
    }

    private suspend fun refreshLyrics() {
        playPauseView?.text = if (PlayerClient.isPlaying) "Ⅱ" else "▶"
        val now = System.currentTimeMillis()
        if (!AudioReactive.realCapture.value && now - lastCaptureAttemptAt > 2_000L) {
            lastCaptureAttemptAt = now
            AudioReactive.attachIfPermitted(this)
        }
        AudioReactive.updateFallback(PlayerClient.positionMs, PlayerClient.isPlaying)
        graphView?.setSignal(AudioReactive.bands.value, AudioReactive.energy.value)
        applyReactiveLyricMotion()
        val song = PlayerClient.currentSong
        if (song == null) {
            titleView?.text = "DEEP-ECHO"
            setLyricAnimated("♪")
            nextView?.text = ""
            return
        }

        if (song.url != songUrl) {
            songUrl = song.url
            lyrics = null
            lastLine = null
            titleView?.text = "${song.title} • ${song.artist}"
            setLyricAnimated("Loading lyrics…")
            nextView?.text = ""
            lyrics = withContext(Dispatchers.IO) {
                runCatching { Lrclib.fetch(song) }.getOrNull()
            }
        }

        val data = lyrics
        var nextText = ""
        val text = if (data == null) {
            "♪ ${song.title}"
        } else if (data.synced.isNotEmpty()) {
            val pos = PlayerClient.positionMs
            val index = data.synced.indexOfLast { it.timeMs <= pos }
            nextText = data.synced.getOrNull(index + 1)?.text.orEmpty()
            data.synced.getOrNull(index)?.text
                ?: data.synced.firstOrNull()?.text
                ?: "♪ ${song.title}"
        } else {
            val lines = data.plain?.lineSequence()?.filter { it.isNotBlank() }?.toList().orEmpty()
            nextText = lines.getOrNull(1).orEmpty()
            lines.firstOrNull() ?: "♪ ${song.title}"
        }

        val shownText = if (Settings.romanizedLyrics.value) {
            Romanizer.romanize(text) ?: text
        } else {
            text
        }
        val shownNext = if (Settings.romanizedLyrics.value) {
            Romanizer.romanize(nextText) ?: nextText
        } else {
            nextText
        }

        setLyricAnimated(shownText)
        nextView?.text = shownNext
    }

    private fun applyReactiveLyricMotion() {
        val view = lyricView ?: return
        val colors = themeColors(Settings.theme.value)
        val e = if (PlayerClient.isPlaying) AudioReactive.energy.value.coerceIn(0.04f, 1f) else 0.03f
        view.scaleX = 1f + e * 0.024f
        view.scaleY = 1f + e * 0.052f
        view.translationY = -dp(3) * e
        val alpha = (105 + e * 120).toInt().coerceIn(90, 225)
        view.setShadowLayer(dp(6).toFloat() + dp(8).toFloat() * e, 0f, 0f, Color.argb(alpha, Color.red(colors.accent), Color.green(colors.accent), Color.blue(colors.accent)))
        if (view.width > 0) {
            val phase = ((PlayerClient.positionMs % 1500L) / 1500f).coerceIn(0f, 1f)
            val start = -view.width.toFloat() + phase * view.width * 2.4f
            view.paint.shader = LinearGradient(
                start, 0f, start + view.width * 0.78f, 0f,
                intArrayOf(colors.main, colors.accent, Color.WHITE, colors.accent, colors.main),
                floatArrayOf(0f, 0.28f, 0.5f, 0.72f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        view.invalidate()
    }

    private fun setLyricAnimated(text: String) {
        val view = lyricView ?: return
        if (lastLine == text) return
        lastLine = text
        view.animate().cancel()
        view.text = text
        view.alpha = 0.18f
        view.translationY = dp(8).toFloat()
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(240L)
            .start()
    }

    private data class OverlayColors(val accent: Int, val main: Int, val muted: Int)

    private fun themeColors(name: String): OverlayColors = when (name) {
        "Violet" -> OverlayColors(0xFFAA9BFF.toInt(), 0xFFE2DDFF.toInt(), 0xFFC5BDF0.toInt())
        "Ocean" -> OverlayColors(0xFF55CBFF.toInt(), 0xFFD8F3FF.toInt(), 0xFF9EDCF2.toInt())
        "Rose" -> OverlayColors(0xFFFF77A4.toInt(), 0xFFFFD7E4.toInt(), 0xFFFFAFC8.toInt())
        "Emerald" -> OverlayColors(0xFF45E7A0.toInt(), 0xFFD2FFE9.toInt(), 0xFF9EE8C4.toInt())
        "AMOLED" -> OverlayColors(Color.WHITE, Color.WHITE, 0xFFD7D7D7.toInt())
        else -> OverlayColors(0xFFF0C24F.toInt(), 0xFFFFEDB3.toInt(), 0xFFE8CD8E.toInt())
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        loop?.cancel()
        root?.let { view -> runCatching { windowManager?.removeView(view) } }
        root = null
        graphView = null
        AudioReactive.release()
        FloatingLyricsController.enabled.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
