package com.islandify.app.ui.theme
import com.islandify.app.R
import androidx.annotation.StringRes

import com.islandify.app.core.*
import com.islandify.app.island.*
import com.islandify.app.ui.components.*
import com.islandify.app.ui.onboarding.*
import com.islandify.app.ui.screens.*

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeMode(@StringRes val label: Int) {
    System(R.string.theme_system), Light(R.string.theme_light), Dark(R.string.theme_dark), Amoled(R.string.theme_amoled)
}

/** App screens: Material You or custom accent, background styles, and System / Light / Dark / AMOLED. */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val index by IslandSettings.theme.collectAsState()
    val mode = ThemeMode.entries.getOrElse(index) { ThemeMode.System }
    val sysDark = isSystemInDarkTheme()
    val dark = when (mode) {
        ThemeMode.System -> sysDark
        ThemeMode.Light -> false
        else -> true
    }

    val source by IslandSettings.accentSource.collectAsState()
    val seed by IslandSettings.accentSeed.collectAsState()
    val bgStyle by IslandSettings.bgStyle.collectAsState()
    val bgColor by IslandSettings.bgColor.collectAsState()

    // Accent: wallpaper colors (Material You, Android 12+) or a custom seed color
    var scheme = when {
        source == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        else -> seedScheme(seed, dark)
    }
    scheme = withBackground(scheme, bgStyle, bgColor, dark)
    if (mode == ThemeMode.Amoled && bgStyle != 2) {
        scheme = if (bgStyle == 1) {
            // Tinted + AMOLED: true black background, the tint stays on the cards
            scheme.copy(
                background = Color.Black, surface = Color.Black,
                surfaceDim = Color.Black, surfaceContainerLowest = Color.Black,
            )
        } else {
            scheme.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceDim = Color.Black,
                surfaceBright = Color(0xFF2A2A2A),
                surfaceContainerLowest = Color.Black,
                surfaceContainerLow = Color(0xFF0A0A0A),
                surfaceContainer = Color(0xFF111111),
                surfaceContainerHigh = Color(0xFF181818),
                surfaceContainerHighest = Color(0xFF1F1F1F),
            )
        }
    }

    // If the app theme differs from the system theme, match the status / navigation bar icons to it
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val w = (ctx as? Activity)?.window ?: return@SideEffect
            val c = WindowCompat.getInsetsController(w, view)
            c.isAppearanceLightStatusBars = scheme.background.luminance() > 0.5f
            c.isAppearanceLightNavigationBars = scheme.background.luminance() > 0.5f
            w.decorView.setBackgroundColor(scheme.background.toArgb())
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
