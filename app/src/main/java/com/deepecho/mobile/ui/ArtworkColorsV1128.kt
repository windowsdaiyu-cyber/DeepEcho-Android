package com.deepecho.mobile.ui

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Small artwork palette cache for v1.12.8. It samples once per artwork URL and never runs in a
 * frame/render loop. Theme colours remain authoritative; this value is only a secondary accent.
 */
object ArtworkColorsV1128 {
    private val cache = ConcurrentHashMap<String, Int>()

    suspend fun accent(context: Context, artworkUrl: String?): Int? {
        val key = artworkUrl?.trim().orEmpty()
        if (key.isBlank()) return null
        cache[key]?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(key)
                    .allowHardware(false)
                    .size(96)
                    .build()
                val result = context.imageLoader.execute(request) as? SuccessResult ?: return@runCatching null
                val bitmap = result.drawable.toBitmap(96, 96)
                var bestColor: Int? = null
                var bestScore = Float.NEGATIVE_INFINITY
                val hsv = FloatArray(3)
                for (y in 4 until bitmap.height step 4) {
                    for (x in 4 until bitmap.width step 4) {
                        val c = bitmap.getPixel(x, y)
                        val alpha = AndroidColor.alpha(c)
                        if (alpha < 180) continue
                        AndroidColor.colorToHSV(c, hsv)
                        val saturation = hsv[1]
                        val value = hsv[2]
                        // Avoid mud, near-black and washed-out whites. The theme remains fallback.
                        if (value < 0.22f || value > 0.94f || saturation < 0.20f) continue
                        val centrality = 1f - (kotlin.math.abs(x - 48) + kotlin.math.abs(y - 48)) / 120f
                        val score = saturation * 1.35f + value * 0.42f + centrality * 0.14f
                        if (score > bestScore) {
                            bestScore = score
                            bestColor = c
                        }
                    }
                }
                bestColor?.also { cache[key] = it }
            }.getOrNull()
        }
    }
}
