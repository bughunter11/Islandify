package com.islandify.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme

/** Ready-made accent colors shown as swatches. */
val AccentPresets: List<Pair<String, Int>> = listOf(
    "Violet" to 0xFF7B3DF5.toInt(),
    "Blue" to 0xFF2F6BFF.toInt(),
    "Cyan" to 0xFF00A3C4.toInt(),
    "Teal" to 0xFF00A88F.toInt(),
    "Green" to 0xFF34A853.toInt(),
    "Lime" to 0xFF8BC34A.toInt(),
    "Amber" to 0xFFFFB300.toInt(),
    "Orange" to 0xFFFF6D00.toInt(),
    "Red" to 0xFFE53935.toInt(),
    "Pink" to 0xFFE91E8C.toInt(),
)

fun hueOf(argb: Int): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    return hsv[0]
}

private fun hsl(h: Float, s: Float, l: Float): Color =
    Color.hsl(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))

/** Builds a real Material 3 color scheme (HCT tonal palettes) from one seed color. */
fun seedScheme(seed: Int, dark: Boolean): ColorScheme =
    dynamicColorScheme(
        seedColor = Color(seed),
        isDark = dark,
        isAmoled = false,   // required by material-kolor 2.x; AMOLED is handled in AppTheme
        style = PaletteStyle.TonalSpot,
    )

/**
 * Background styles for the whole app.
 *  0 = default (leave the scheme alone)
 *  1 = tinted: surfaces take on the accent's hue
 *  2 = custom: the user's own background color; containers are blended from it
 */
fun withBackground(scheme: ColorScheme, style: Int, custom: Int, dark: Boolean): ColorScheme {
    return when (style) {
        1 -> {
            val h = hueOf(scheme.primary.toArgb())
            fun c(s: Float, dl: Float, ll: Float) = hsl(h, s, if (dark) dl else ll)
            scheme.copy(
                background = c(0.28f, 0.07f, 0.97f), surface = c(0.28f, 0.07f, 0.97f),
                surfaceDim = c(0.28f, 0.05f, 0.87f), surfaceBright = c(0.28f, 0.22f, 0.98f),
                surfaceContainerLowest = c(0.28f, 0.04f, 1.00f), surfaceContainerLow = c(0.28f, 0.09f, 0.95f),
                surfaceContainer = c(0.28f, 0.11f, 0.93f), surfaceContainerHigh = c(0.27f, 0.15f, 0.91f),
                surfaceContainerHighest = c(0.26f, 0.19f, 0.89f),
            )
        }
        2 -> {
            val bg = Color(custom)
            val light = bg.luminance() > 0.5f
            // Text colors come from the theme itself (inverse* swaps light/dark), nothing hardcoded
            val on = if (light == !dark) scheme.onSurface else scheme.inverseOnSurface
            val toward = if (light) Color.Black else Color.White
            scheme.copy(
                background = bg, surface = bg, onBackground = on, onSurface = on,
                onSurfaceVariant = on.copy(alpha = 0.75f),
                surfaceDim = lerp(bg, Color.Black, if (light) 0.06f else 0.3f),
                surfaceBright = lerp(bg, toward, 0.2f),
                surfaceContainerLowest = lerp(bg, toward, 0.02f), surfaceContainerLow = lerp(bg, toward, 0.04f),
                surfaceContainer = lerp(bg, toward, 0.07f), surfaceContainerHigh = lerp(bg, toward, 0.11f),
                surfaceContainerHighest = lerp(bg, toward, 0.15f),
            )
        }
        else -> scheme
    }
}
