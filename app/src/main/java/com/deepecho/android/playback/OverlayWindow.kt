package com.deepecho.android.playback

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

class OverlayWindow(private val context: Context) {
    private val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: LinearLayout? = null
    private var titleView: TextView? = null
    private var lineView: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    fun canShow(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    fun show(title: String, line: String) {
        if (!canShow()) return
        if (root == null) create()
        update(title, line)
        root?.visibility = View.VISIBLE
    }

    fun update(title: String, line: String) {
        titleView?.text = title
        lineView?.text = line
    }

    fun hide() {
        root?.visibility = View.GONE
    }

    fun destroy() {
        root?.let { runCatching { manager.removeView(it) } }
        root = null
        titleView = null
        lineView = null
        params = null
    }

    private fun create() {
        val density = context.resources.displayMetrics.density
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((14 * density).roundToInt(), (8 * density).roundToInt(), (14 * density).roundToInt(), (9 * density).roundToInt())
            setBackgroundColor(Color.argb(218, 10, 10, 14))
            elevation = 18 * density
        }
        val title = TextView(context).apply {
            setTextColor(Color.rgb(232, 180, 81))
            textSize = 11f
            maxLines = 1
        }
        val lyric = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            maxLines = 2
        }
        container.addView(title)
        container.addView(lyric)

        val lp = WindowManager.LayoutParams(
            (320 * density).roundToInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = (88 * density).roundToInt()
        }

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        container.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = startX + (event.rawX - downX).roundToInt()
                    lp.y = startY + (event.rawY - downY).roundToInt()
                    runCatching { manager.updateViewLayout(container, lp) }
                    true
                }
                else -> true
            }
        }

        manager.addView(container, lp)
        root = container
        titleView = title
        lineView = lyric
        params = lp
    }
}
