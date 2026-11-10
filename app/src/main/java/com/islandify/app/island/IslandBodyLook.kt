package com.islandify.app.island

import com.islandify.app.core.*
import com.islandify.app.ui.components.*
import com.islandify.app.ui.onboarding.*
import com.islandify.app.ui.screens.*
import com.islandify.app.ui.theme.*

import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

// SPLITKIT  IslandBody: glow rang, border outline aur pulse

internal class IslandLook(
    val glowColor: Color,
    val pulseColor: Color,
    val outlineColor: Color,
    val outlineW: Dp,
)

@Composable
internal fun rememberIslandLook(
    mode: IslandMode,
    lvl: IslandLevel,
    glow: Boolean,
    appAccent: Color?,
    isPopup: Boolean,
): IslandLook {
    // Glow: accent color from the album art / current mode
    val glowTarget: Color = if (!glow) Color.Transparent else when (mode) {
        is IslandMode.Media -> appAccent ?: mode.accent?.let { Color(it) } ?: Color.Transparent
        is IslandMode.Charging -> Green
        is IslandMode.Call -> Green
        is IslandMode.Timer -> Orange
        is IslandMode.LowBattery -> Red
        is IslandMode.Notification -> mode.accent?.let { Color(it) } ?: Color.Transparent
        is IslandMode.Live -> LiveBlue
        is IslandMode.Toggle -> mode.tint()
        else -> Color.Transparent
    }
    val glowColor by animateColorAsState(glowTarget, tween(200), label = "glow")

    // ---- Essentials-style border outline + pulse ----
    val outlineOn by IslandSettings.outlineOn.collectAsState()
    val outlineThick by IslandSettings.outlineThickness.collectAsState()
    val outlineDyn by IslandSettings.outlineDynamic.collectAsState()
    val outlineHideExp by IslandSettings.outlineHideExpanded.collectAsState()
    val pulseOn by IslandSettings.pulseShadow.collectAsState()
    val pulseMs by IslandSettings.pulseDurationMs.collectAsState()
    val bigLevel = lvl == IslandLevel.Expanded || lvl == IslandLevel.Large
    val outlineTarget = when {
        !outlineOn || (outlineHideExp && bigLevel) -> Color.Transparent
        outlineDyn && glowColor.alpha > 0f -> glowColor.copy(alpha = glowColor.alpha * 0.55f)
        outlineDyn -> Color(0xFF1F1F21)
        else -> Color.White.copy(alpha = 0.28f)
    }
    val outlineBase by animateColorAsState(outlineTarget, tween(200), label = "outline")
    val reduceMotion = com.islandify.app.core.rememberReduceMotion()   // MOTIONKIT (PATCH_10_11_12): glow flash band
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(if (mode is IslandMode.Notification) mode.copy(icon = null) else mode) {   // BUTTERICON2: sirf icon badla to pulse dobara nahi
        if (pulseOn && isPopup && !reduceMotion) {
            pulse.snapTo(1f)
            pulse.animateTo(0f, tween(pulseMs, easing = LinearOutSlowInEasing))
        }
    }
    val pulseColor = if (glowColor.alpha > 0f) glowColor.copy(alpha = 1f) else MaterialTheme.colorScheme.primary
    val outlineColor = if (pulse.value > 0.01f)
        androidx.compose.ui.graphics.lerp(outlineBase, pulseColor, pulse.value) else outlineBase
    val outlineW = (outlineThick + pulse.value * 2f).dp
    return IslandLook(glowColor, pulseColor, outlineColor, outlineW)
}
