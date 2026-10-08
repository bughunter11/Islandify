package com.islandify.app.island

import com.islandify.app.core.*
import com.islandify.app.ui.components.*
import com.islandify.app.ui.onboarding.*
import com.islandify.app.ui.screens.*
import com.islandify.app.ui.theme.*

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max

/**
 * Extracts a vivid accent color from album art (without the Palette library).
 * Saturated + bright pixels get more weight. Returns null on failure.
 */
fun dominantColor(src: Bitmap): Int? = runCatching {
    val n = 24
    val small = Bitmap.createScaledBitmap(src, n, n, true)
    var r = 0.0
    var g = 0.0
    var b = 0.0
    var sum = 0.0
    val hsv = FloatArray(3)
    for (y in 0 until n) for (x in 0 until n) {
        val p = small.getPixel(x, y)
        Color.colorToHSV(p, hsv)
        val w = (hsv[1] * hsv[2]).toDouble() + 0.02
        r += Color.red(p) * w
        g += Color.green(p) * w
        b += Color.blue(p) * w
        sum += w
    }
    if (small !== src) small.recycle()
    if (sum <= 0.0) return@runCatching null
    val avg = Color.rgb((r / sum).toInt(), (g / sum).toInt(), (b / sum).toInt())
    Color.colorToHSV(avg, hsv)
    hsv[1] = max(hsv[1], 0.55f)
    hsv[2] = max(hsv[2], 0.85f)
    Color.HSVToColor(hsv)
}.getOrNull()
