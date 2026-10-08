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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

private val Green = Color(0xFF30D158)
private val Orange = Color(0xFFFF9F0A)
private val Red = Color(0xFFFF453A)
private val Track = Color(0xFF2C2C2E)
private val Muted = Color(0xFF8E8E93)
private val LiveBlue = Color(0xFF0A84FF)
private val DndPurple = Color(0xFF7D5CFF)

private fun IslandMode.Toggle.icon(): ImageVector = when (kind) {
    ToggleKind.Silent -> if (on) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp
    ToggleKind.Vibrate -> Icons.Rounded.Vibration
    ToggleKind.Dnd -> if (on) Icons.Rounded.DoNotDisturbOn else Icons.Rounded.NotificationsActive
    ToggleKind.Flashlight -> if (on) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff
}

private fun IslandMode.Toggle.title(): String = when (kind) {
    ToggleKind.Silent -> if (on) "Silent" else "Sound on"
    ToggleKind.Vibrate -> "Vibrate"
    ToggleKind.Dnd -> if (on) "Do Not Disturb" else "Do Not Disturb off"
    ToggleKind.Flashlight -> if (on) "Flashlight on" else "Flashlight off"
}

private fun IslandMode.Toggle.subtitle(): String = when (kind) {
    ToggleKind.Silent -> if (on) "Ringer is off" else "Ringer is back on"
    ToggleKind.Vibrate -> "Ringer vibrates only"
    ToggleKind.Dnd -> if (on) "Calls and alerts are muted" else "Alerts are back"
    ToggleKind.Flashlight -> if (on) "Torch is on" else "Torch is off"
}

private fun IslandMode.Toggle.tint(): Color = when (kind) {
    ToggleKind.Silent, ToggleKind.Vibrate -> if (on) Orange else Green
    ToggleKind.Dnd -> if (on) DndPurple else Green
    ToggleKind.Flashlight -> if (on) Color(0xFFFFD60A) else Muted
}

private fun IslandMode.Live.icon(): ImageVector {
    if (kind == LiveKind.Navigation) return Icons.Rounded.Navigation
    if (kind == LiveKind.Download) return Icons.Rounded.Download
    val a = app.lowercase()
    val ride = listOf("uber", "ola", "rapido", "taxi", "cab").any { a.contains(it) }
    return if (ride) Icons.Rounded.LocalTaxi else Icons.Rounded.DeliveryDining
}

@Composable
fun IslandTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val source by IslandSettings.accentSource.collectAsState()
    val seed by IslandSettings.accentSeed.collectAsState()
    val scheme = if (source == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        dynamicDarkColorScheme(ctx) else seedScheme(seed, true)
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Callbacks for the buttons inside the island, kept in one place. */
private class IslandActions(
    val onPrev: () -> Unit,
    val onPlayPause: () -> Unit,
    val onNext: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onAnswer: () -> Unit,
    val onDecline: () -> Unit,
    val onMarkRead: () -> Unit,
    val onReply: () -> Unit,
    val onSendReply: (String) -> Unit,
    val onCancelReply: () -> Unit,
    val replying: Boolean,
    val onOpenMedia: () -> Unit,
    val queued: Int,
)

/**
 * [scale] resizes the whole island (dp and sp together).
 *
 * Gestures (all handled in one pointerInput below):
 *  - tap                    -> onClick (compact <-> card)
 *  - long-press             -> onLongPress (large card)
 *  - swipe up               -> onDismiss
 */
@Composable
fun DynamicIslandUi(
    mode: IslandMode,
    level: IslandLevel,
    scale: Float = 1f,
    widthScale: Float = 1f,
    heightScale: Float = 1f,
    cornerFactor: Float = 1f,
    animSpeed: Float = 1f,
    glow: Boolean = true,
    haptics: Boolean = true,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onPrev: () -> Unit = {},
    onPlayPause: () -> Unit = {},
    onNext: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onAnswer: () -> Unit = {},
    onDecline: () -> Unit = {},
    onMarkRead: () -> Unit = {},
    onReply: () -> Unit = {},
    onSendReply: (String) -> Unit = {},
    onCancelReply: () -> Unit = {},
    replying: Boolean = false,
    onSwipeSide: (Int) -> Unit = {},
    appAccent: Color? = null,
    onOpenMedia: () -> Unit = {},
    secondary: IslandMode.Media? = null,   // music next to a timer / navigation
    queued: Int = 0,                       // banners waiting behind the current one
    onSwipe: ((SwipeDir) -> Unit)? = null, // null = old behaviour (up = onDismiss, left / right = onSwipeSide)
) {
    val base = LocalDensity.current
    // Text size of this kind (banners scale their own text, cards / pills scale the font here)
    val kindStyles by IslandSettings.kindStyles.collectAsState()
    val textMul = if (level == IslandLevel.Banner) 1f else (kindOf(mode)?.let { kindStyles[it.key]?.text } ?: 1f)
    val actions = IslandActions(
        onPrev, onPlayPause, onNext, onSeek, onAnswer, onDecline,
        onMarkRead, onReply, onSendReply, onCancelReply, replying, onOpenMedia, queued
    )
    CompositionLocalProvider(LocalDensity provides Density(base.density * scale, base.fontScale * textMul)) {
        IslandBody(
            mode, level, widthScale, heightScale, cornerFactor, animSpeed, glow, haptics,
            onClick, onLongPress, onDismiss, actions,
            onSwipeSide, appAccent, secondary, queued, onSwipe
        )
    }
}

@Composable
private fun IslandBody(
    mode: IslandMode,
    level: IslandLevel,
    widthScale: Float,
    heightScale: Float,
    cornerFactor: Float,
    animSpeed: Float,
    glow: Boolean,
    haptics: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDismiss: () -> Unit,
    actions: IslandActions,
    onSwipeSide: (Int) -> Unit,
    appAccent: Color?,
    secondary: IslandMode.Media?,
    queued: Int,
    onSwipe: ((SwipeDir) -> Unit)?,
) {
    val lvl = level
    val kindStyles by IslandSettings.kindStyles.collectAsState()
    // Each banner type (message, charging, unplugged, ...) has its own size
    val kind = kindOf(mode)
    val bs = kind?.let { kindStyles[it.key] } ?: KindStyle()
    val isPopup = kind?.popup == true
    // Music is only the second part while a timer / navigation is the main thing
    val sec = if (mode is IslandMode.Live || mode is IslandMode.Timer) secondary else null
    val haptic = LocalHapticFeedback.current
    val hapticOn by rememberUpdatedState(haptics)

    val dClick by rememberUpdatedState(onClick)
    val dLong by rememberUpdatedState(onLongPress)
    val dDismiss by rememberUpdatedState(onDismiss)
    val dSide by rememberUpdatedState(onSwipeSide)
    val dSwipe by rememberUpdatedState(onSwipe)

    // Light haptic on expand / collapse (not on the first composition)
    var firstLevel by remember { mutableStateOf(true) }
    LaunchedEffect(lvl) {
        if (firstLevel) firstLevel = false
        else if (hapticOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    val baseW = when (lvl) {
        IslandLevel.Compact -> when (mode) {
            IslandMode.Idle -> 118.dp
            is IslandMode.Charging -> 150.dp
            is IslandMode.Timer -> if (sec != null) 214.dp else 160.dp
            is IslandMode.Notification -> 224.dp
            is IslandMode.Media -> 224.dp
            is IslandMode.Call -> 210.dp
            is IslandMode.Device -> 200.dp
            is IslandMode.LowBattery -> 150.dp
            is IslandMode.Toggle -> 190.dp
            is IslandMode.Live -> if (sec != null) 256.dp else 224.dp
        }
        IslandLevel.Banner -> bannerWidth(mode)
        else -> 368.dp
    }
    val baseH = when (lvl) {
        IslandLevel.Compact -> 38.dp
        IslandLevel.Banner -> 48.dp
        IslandLevel.Expanded -> expandedHeight(mode)
        IslandLevel.Large -> when (mode) {
            is IslandMode.Media -> 236.dp
            is IslandMode.Notification -> if (mode.canReply || mode.canMarkRead) 224.dp else 200.dp
            is IslandMode.Live -> 168.dp
            else -> expandedHeight(mode)
        }
    }

    // Never wider than the screen (density is already scaled, so derive dp from it)
    val screenPx = LocalContext.current.resources.displayMetrics.widthPixels
    val maxW = (screenPx / LocalDensity.current.density).dp - 8.dp
    val targetW = (baseW * (when (lvl) {
        IslandLevel.Banner -> bs.w
        IslandLevel.Compact -> widthScale * (if (isPopup) 1f else bs.w)
        else -> widthScale * bs.cardW
    })).coerceAtMost(maxW)
    // The small pill keeps a sane height; the height slider mainly shapes the cards
    // The mini player adds a row under the card
    val miniExtra = if (sec != null && (lvl == IslandLevel.Expanded || lvl == IslandLevel.Large)) 52.dp else 0.dp
    val targetH = (baseH + miniExtra) * (when (lvl) {
        IslandLevel.Compact -> heightScale.coerceIn(0.8f, 1.2f) * (if (isPopup) 1f else bs.h)
        IslandLevel.Banner -> bs.h   // own height for each banner type
        else -> heightScale * bs.cardH
    })

    // Higher animSpeed = faster spring
    val spec = spring<Dp>(
        dampingRatio = 0.8f,
        stiffness = (Spring.StiffnessMedium * 1.6f * animSpeed).coerceIn(300f, 6000f)
    )
    val width by animateDpAsState(targetW, spec, label = "w")
    val height by animateDpAsState(targetH, spec, label = "h")
    val cf = if (bs.corner >= 0f) bs.corner else cornerFactor
    val corner = (height / 2 * cf).coerceIn(12.dp, 46.dp)
    val shape = RoundedCornerShape(corner)

    // Glow: accent color from the album art / current mode
    val glowTarget: Color = if (!glow) Color.Transparent else when (mode) {
        is IslandMode.Media -> appAccent ?: mode.accent?.let { Color(it) } ?: Color.Transparent
        is IslandMode.Charging -> Green
        is IslandMode.Call -> Green
        is IslandMode.Timer -> Orange
        is IslandMode.LowBattery -> Red
        is IslandMode.Live -> LiveBlue
        is IslandMode.Toggle -> mode.tint()
        else -> Color.Transparent
    }
    val glowColor by animateColorAsState(glowTarget, tween(200), label = "glow")

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(shape)
            .background(Color.Black)
            .drawBehind {
                if (glowColor.alpha > 0f) {
                    drawRect(
                        Brush.radialGradient(
                            listOf(glowColor.copy(alpha = glowColor.alpha * 0.32f), Color.Transparent),
                            center = Offset(size.width * 0.16f, size.height * 0.5f),
                            radius = size.width * 0.75f
                        )
                    )
                }
            }
            .border(
                0.6.dp,
                if (glowColor.alpha > 0f) glowColor.copy(alpha = glowColor.alpha * 0.55f) else Color(0xFF1F1F21),
                shape
            )
            // One gesture handler for everything: tap, long-press and swipe.
            // (No separate clickable: two handlers fighting over the same touch is what made taps unreliable.)
            .pointerInput(Unit) {
                awaitEachGesture {
                    // Default requireUnconsumed = true: a touch that starts on an inner button
                    // (prev / play / next / answer...) belongs to that button, not to us.
                    val down = awaitFirstDown()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    val slop = viewConfiguration.touchSlop
                    var accum = Offset.Zero
                    var swipe = false
                    var tap = false

                    // Phase 1: watch until the long-press timeout.
                    // Finger moved = swipe, finger lifted = tap, still down = long-press.
                    val timedOut = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id }
                            // Lost, or an inner button consumed it: not our gesture
                            if (ch == null || ch.isConsumed) return@withTimeoutOrNull
                            if (ch.changedToUpIgnoreConsumed()) {
                                tap = true
                                return@withTimeoutOrNull
                            }
                            accum += ch.positionChange()
                            tracker.addPosition(ch.uptimeMillis, ch.position)
                            if (accum.getDistance() > slop) {
                                swipe = true
                                return@withTimeoutOrNull
                            }
                        }
                    } == null

                    if (timedOut) {
                        // Long press = large card (no dragging)
                        if (hapticOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        dLong()
                        // Swallow the rest of the touch until the finger lifts
                        drag(down.id) { ch -> ch.consume() }
                        currentEvent.changes.forEach { it.consume() }
                    } else if (swipe) {
                        var dx = accum.x
                        var dy = accum.y
                        drag(down.id) { ch ->
                            val d = ch.positionChange()
                            dx += d.x
                            dy += d.y
                            tracker.addPosition(ch.uptimeMillis, ch.position)
                            ch.consume()
                        }
                        currentEvent.changes.forEach { it.consume() }
                        // FIX: the old rule needed a long drag (18dp, and it shrank with the island scale).
                        // Now a short quick flick counts too, and the distance is not tied to the scale.
                        val velo = tracker.calculateVelocity()
                        val vertical = abs(dy) >= abs(dx)
                        val dist = if (vertical) abs(dy) else abs(dx)
                        val speed = if (vertical) abs(velo.y) else abs(velo.x)
                        if (dist >= slop * 2f || speed >= 500f) {
                            val dir = if (vertical) (if (dy < 0) SwipeDir.Up else SwipeDir.Down)
                                      else (if (dx < 0) SwipeDir.Left else SwipeDir.Right)
                            val cb = dSwipe
                            if (cb != null) cb(dir)
                            else when (dir) {
                                SwipeDir.Up -> dDismiss()
                                SwipeDir.Left -> dSide(1)
                                SwipeDir.Right -> dSide(-1)
                                SwipeDir.Down -> Unit
                            }
                        }
                    } else if (tap) {
                        dClick()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            modifier = Modifier.fillMaxSize(),
            targetState = mode to lvl,
            // A timer ticking every second must not re-trigger the transition
            contentKey = { it.first::class to it.second },
            transitionSpec = {
                (fadeIn(tween(120)) + scaleIn(tween(140), initialScale = 0.9f)) togetherWith
                    fadeOut(tween(60))
            },
            contentAlignment = Alignment.Center,
            label = "content"
        ) { (m, l) ->
            if (l == IslandLevel.Compact) {
                Compact(m, queued, sec)
            } else if (l == IslandLevel.Banner) {
                Banner(m, queued)
            } else if (sec != null && (m is IslandMode.Live || m is IslandMode.Timer)) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) { Expanded(m, l, actions) }
                    MiniPlayer(sec, actions)
                }
            } else {
                Expanded(m, l, actions)
            }
        }
    }
}

private fun expandedHeight(mode: IslandMode): Dp = when (mode) {
    IslandMode.Idle -> 96.dp
    is IslandMode.Charging -> 132.dp
    is IslandMode.Timer -> 132.dp
    is IslandMode.Notification -> if (mode.canReply || mode.canMarkRead) 156.dp else 136.dp
    is IslandMode.Media -> 156.dp
    is IslandMode.Call -> 120.dp
    is IslandMode.Device -> 108.dp
    is IslandMode.LowBattery -> 132.dp
    is IslandMode.Toggle -> 108.dp
    is IslandMode.Live -> 132.dp
}

private fun IslandMode.Media.accentColor(fallback: Color): Color = accent?.let { Color(it) } ?: fallback

private fun IslandMode.Media.currentPosition(): Long {
    if (!playing || updatedAt == 0L) return positionMs
    val p = positionMs + ((SystemClock.elapsedRealtime() - updatedAt) * speed).toLong()
    return if (durationMs > 0) p.coerceIn(0L, durationMs) else p
}

/* ------------------------------ COMPACT ------------------------------ */

/** Download ring: filled ring when the progress is known, spinner otherwise. */
@Composable
private fun DownloadRing(progress: Int, size: Dp) {
    if (progress >= 0) {
        CircularProgressIndicator(
            progress = { progress / 100f },
            modifier = Modifier.size(size),
            color = LiveBlue, trackColor = LiveBlue.copy(alpha = 0.18f),
            strokeWidth = 2.dp, strokeCap = StrokeCap.Round
        )
    } else {
        CircularProgressIndicator(
            modifier = Modifier.size(size),
            color = LiveBlue, trackColor = LiveBlue.copy(alpha = 0.18f),
            strokeWidth = 2.dp, strokeCap = StrokeCap.Round
        )
    }
}

@Composable
private fun Compact(m: IslandMode, queued: Int, sec: IslandMode.Media?) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxSize().padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        when (m) {
            IslandMode.Idle -> Unit
            is IslandMode.Charging -> {
                val c = if (m.connected) Green else Orange
                Icon(
                    if (m.connected) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
                    null, tint = c, modifier = Modifier.size(20.dp)
                )
                Label("${m.percent}%", color = c, size = 14, weight = FontWeight.Bold)
            }
            is IslandMode.Notification -> {
                Avatar(m.app, 24.dp)
                Label(
                    m.title.ifBlank { m.app },
                    Modifier.weight(1f).padding(horizontal = 9.dp),
                    size = 13
                )
                if (queued > 0) CountBadge(queued, primary)
                else Box(Modifier.size(7.dp).clip(CircleShape).background(primary))
            }
            is IslandMode.Media -> {
                Art(m, 25.dp, 7.dp)
                Waveform(m.playing, m.accentColor(primary), Modifier.height(16.dp))
            }
            is IslandMode.Timer -> {
                if (sec == null) {
                    Icon(Icons.Rounded.Timer, null, tint = Orange, modifier = Modifier.size(20.dp))
                    Label(formatTime(m.remainingSec), color = Orange, size = 14, weight = FontWeight.Bold, tabular = true)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Timer, null, tint = Orange, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Label(formatTime(m.remainingSec), color = Orange, size = 14, weight = FontWeight.Bold, tabular = true)
                    }
                    MiniMusic(sec, primary)
                }
            }
            is IslandMode.Call -> {
                Icon(Icons.Rounded.Call, null, tint = Green, modifier = Modifier.size(19.dp))
                Label(m.name, Modifier.weight(1f).padding(horizontal = 9.dp), size = 13)
                if (m.incoming) {
                    Waveform(true, Green, Modifier.height(14.dp))
                } else {
                    val sec = rememberElapsedSec(m.since)
                    Label(formatTime(sec), color = Green, size = 13, weight = FontWeight.Bold, tabular = true)
                }
            }
            is IslandMode.Device -> {
                Icon(
                    if (m.headphones) Icons.Rounded.Headphones else Icons.Rounded.Bluetooth,
                    null, tint = Color(0xFF0A84FF), modifier = Modifier.size(19.dp)
                )
                Label(m.name, Modifier.weight(1f).padding(start = 9.dp), size = 13)
            }
            is IslandMode.LowBattery -> {
                Icon(Icons.Rounded.BatteryAlert, null, tint = Red, modifier = Modifier.size(20.dp))
                Label("${m.percent}%", color = Red, size = 14, weight = FontWeight.Bold)
            }
            is IslandMode.Toggle -> {
                Icon(m.icon(), null, tint = m.tint(), modifier = Modifier.size(19.dp))
                Label(m.title(), Modifier.weight(1f).padding(start = 9.dp), size = 13)
            }
            is IslandMode.Live -> {
                Icon(m.icon(), null, tint = LiveBlue, modifier = Modifier.size(20.dp))
                Label(
                    m.title.ifBlank { m.app },
                    Modifier.weight(1f).padding(horizontal = 9.dp),
                    size = 13
                )
                if (m.kind == LiveKind.Download) {
                    DownloadRing(m.progress, 18.dp)
                    if (sec != null) {
                        Spacer(Modifier.width(6.dp))
                        MiniMusic(sec, primary)
                    }
                } else if (sec != null) MiniMusic(sec, primary)
                else Box(Modifier.size(7.dp).clip(CircleShape).background(LiveBlue))
            }
        }
    }
}

/* ------------------------------ BANNER (quick popups) ------------------------------ */

/** Width of the small banner used for notification / charging / device / toggle popups. */
private fun bannerWidth(mode: IslandMode): Dp = when (mode) {
    is IslandMode.Charging -> 228.dp
    is IslandMode.LowBattery -> 228.dp
    is IslandMode.Toggle -> 228.dp
    is IslandMode.Device -> 240.dp
    else -> 258.dp
}

/** One slim row: [lead] title / subtitle [trail]. */
@Composable
private fun BannerRow(
    lead: @Composable () -> Unit,
    title: String,
    subtitle: String,
    trail: (@Composable () -> Unit)? = null,
    ts: Float = 1f,
) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        lead()
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Label(title, size = (13 * ts).roundToInt(), weight = FontWeight.SemiBold)
            if (subtitle.isNotBlank()) Label(subtitle, color = Muted, size = (11 * ts).roundToInt(), weight = FontWeight.Normal)
        }
        if (trail != null) {
            Spacer(Modifier.width(8.dp))
            trail()
        }
    }
}

@Composable
private fun BannerRing(percent: Int, color: Color, icon: ImageVector, ts: Float = 1f) {
    Box(Modifier.size((28 * ts).dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier.fillMaxSize(),
            color = color, trackColor = color.copy(alpha = 0.18f),
            strokeWidth = 2.5.dp, strokeCap = StrokeCap.Round
        )
        Icon(icon, null, tint = color, modifier = Modifier.size((14 * ts).dp))
    }
}

@Composable
private fun BannerBadge(icon: ImageVector, tint: Color, bg: Color, ts: Float = 1f) {
    Box(
        Modifier.size((28 * ts).dp).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size((15 * ts).dp))
    }
}

@Composable
private fun Banner(m: IslandMode, queued: Int) {
    val primary = MaterialTheme.colorScheme.primary
    val kindStyles by IslandSettings.kindStyles.collectAsState()
    val ts = kindOf(m)?.let { kindStyles[it.key]?.text } ?: 1f
    when (m) {
        is IslandMode.Charging -> {
            val c = if (m.connected) Green else Orange
            BannerRow(
                lead = {
                    BannerRing(
                        m.percent, c,
                        if (m.connected) Icons.Rounded.Bolt else Icons.Rounded.BatteryStd, ts
                    )
                },
                title = if (m.connected) "Charging" else "Charger removed",
                subtitle = if (m.connected) "Power connected" else "Unplugged",
                trail = { Label("${m.percent}%", color = c, size = (14 * ts).roundToInt(), weight = FontWeight.Bold) },
                ts = ts
            )
        }
        is IslandMode.LowBattery -> BannerRow(
            lead = { BannerRing(m.percent, Red, Icons.Rounded.BatteryAlert, ts) },
            title = if (m.percent <= 10) "Battery very low" else "Battery low",
            subtitle = "Plug in your charger",
            trail = { Label("${m.percent}%", color = Red, size = (14 * ts).roundToInt(), weight = FontWeight.Bold) },
            ts = ts
        )
        is IslandMode.Notification -> BannerRow(
            lead = { Avatar(m.app, (28 * ts).dp) },
            title = m.title.ifBlank { m.app },
            subtitle = m.text.ifBlank { m.app },
            trail = {
                if (queued > 0) CountBadge(queued, primary)
                else Box(Modifier.size((7 * ts).dp).clip(CircleShape).background(primary))
            },
            ts = ts
        )
        is IslandMode.Device -> BannerRow(
            lead = {
                BannerBadge(
                    if (m.headphones) Icons.Rounded.Headphones else Icons.Rounded.BluetoothConnected,
                    Color(0xFF0A84FF), Color(0xFF0A2540), ts
                )
            },
            title = "Connected",
            subtitle = m.name,
            trail = { Icon(Icons.Rounded.CheckCircle, null, tint = Green, modifier = Modifier.size((17 * ts).dp)) },
            ts = ts
        )
        is IslandMode.Toggle -> BannerRow(
            lead = { BannerBadge(m.icon(), m.tint(), m.tint().copy(alpha = 0.18f), ts) },
            title = m.title(),
            subtitle = m.subtitle(),
            ts = ts
        )
        else -> Compact(m, queued, null)
    }
}

/* ------------------------------ EXPANDED / LARGE ------------------------------ */

@Composable
private fun Expanded(m: IslandMode, level: IslandLevel, a: IslandActions) {
    val primary = MaterialTheme.colorScheme.primary
    val large = level == IslandLevel.Large
    when (m) {
        IslandMode.Idle -> IdleCard()

        is IslandMode.Charging -> Row(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { m.percent / 100f },
                    modifier = Modifier.size(76.dp),
                    color = Green, trackColor = Color(0xFF16301D),
                    strokeWidth = 8.dp, strokeCap = StrokeCap.Round
                )
                Label("${m.percent}", size = 22, weight = FontWeight.Bold)
            }
            Spacer(Modifier.width(20.dp))
            Column {
                Label(if (m.connected) "Charging" else "Charger removed", size = 20, weight = FontWeight.SemiBold)
                Label(if (m.connected) "Power connected" else "Unplugged", color = Muted, size = 14)
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Rounded.Bolt, null, tint = Green, modifier = Modifier.size(30.dp))
        }

        is IslandMode.Notification -> {
            val actionable = m.canReply || m.canMarkRead
            val typing = a.replying && m.canReply
            Column(
                Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = if (actionable) 14.dp else 20.dp)
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.Top) {
                    Avatar(m.app, if (actionable && !large) 40.dp else if (large) 52.dp else 46.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Label(
                                m.app.uppercase(), Modifier.weight(1f),
                                color = primary, size = 11, weight = FontWeight.Bold, spacing = 0.8f
                            )
                            if (a.queued > 0) CountBadge(a.queued, primary)
                        }
                        Spacer(Modifier.height(3.dp))
                        Label(
                            m.title.ifBlank { m.app }, size = 17, weight = FontWeight.SemiBold,
                            lines = if (large && !typing) 2 else 1
                        )
                        Spacer(Modifier.height(2.dp))
                        Label(
                            m.text, color = Color(0xFFD1D1D6), size = 14,
                            lines = if (typing) 1 else if (large) 6 else 2, weight = FontWeight.Normal
                        )
                    }
                }
                if (actionable) {
                    Spacer(Modifier.height(8.dp))
                    if (typing) {
                        ReplyBar(a.onSendReply, a.onCancelReply)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (m.canMarkRead) PillButton("Mark as read", a.onMarkRead)
                            if (m.canReply) PillButton("Reply", a.onReply)
                        }
                    }
                }
            }
        }

        is IslandMode.Media -> {
            val accent = m.accentColor(primary)
            Column(
                Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Art(m, if (large) 64.dp else 54.dp, if (large) 16.dp else 14.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Label(m.title.ifBlank { "Unknown" }, size = if (large) 18 else 17, weight = FontWeight.SemiBold)
                        Label(m.artist, color = Muted, size = 14, weight = FontWeight.Normal)
                    }
                    if (!large) {
                        Spacer(Modifier.width(10.dp))
                        Waveform(m.playing, accent, Modifier.height(24.dp))
                    }
                }
                if (large) {
                    Waveform(
                        m.playing, accent,
                        Modifier.height(28.dp).align(Alignment.CenterHorizontally),
                        bars = 24, barWidth = 4.dp, gap = 4.dp
                    )
                    SeekBar(m, accent, a.onSeek)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoundIcon(Icons.Rounded.SkipPrevious, 46.dp, Color.Transparent, Color.White, a.onPrev)
                    RoundIcon(
                        if (m.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        54.dp, Color.White, Color.Black, a.onPlayPause
                    )
                    RoundIcon(Icons.Rounded.SkipNext, 46.dp, Color.Transparent, Color.White, a.onNext)
                }
            }
        }

        is IslandMode.Timer -> Row(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { m.remainingSec / m.totalSec.toFloat() },
                    modifier = Modifier.size(76.dp),
                    color = Orange, trackColor = Color(0xFF3A2A10),
                    strokeWidth = 8.dp, strokeCap = StrokeCap.Round
                )
                Icon(Icons.Rounded.Timer, null, tint = Orange, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Label(formatTime(m.remainingSec), color = Orange, size = 34, weight = FontWeight.Light, tabular = true)
                Label(if (m.external) "Clock timer" else "Timer", color = Muted, size = 14, weight = FontWeight.Normal)
            }
            // In-app timer: the button really stops it. Clock app timer: it only hides the island
            // (the timer keeps running in Clock), so it must not look like a stop button.
            if (m.external) {
                RoundIcon(Icons.Rounded.VisibilityOff, 46.dp, Color(0xFF2C2C2E), Color.White) { IslandController.stopTimer() }
            } else {
                RoundIcon(Icons.Rounded.Close, 46.dp, Red, Color.White) { IslandController.stopTimer() }
            }
        }

        is IslandMode.Call -> Row(
            Modifier.fillMaxSize().padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(m.name, 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Label(m.name, size = 18, weight = FontWeight.SemiBold)
                if (m.incoming) {
                    Label("Incoming call", color = Green, size = 14, weight = FontWeight.Normal)
                } else {
                    val sec = rememberElapsedSec(m.since)
                    Label(formatTime(sec), color = Green, size = 14, weight = FontWeight.Normal, tabular = true)
                }
            }
            RoundIcon(Icons.Rounded.CallEnd, 52.dp, Red, Color.White, a.onDecline)
            if (m.incoming && m.canAnswer) {
                Spacer(Modifier.width(12.dp))
                RoundIcon(Icons.Rounded.Call, 52.dp, Green, Color.White, a.onAnswer)
            }
        }

        is IslandMode.Device -> Row(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(Color(0xFF0A2540)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (m.headphones) Icons.Rounded.Headphones else Icons.Rounded.BluetoothConnected,
                    null, tint = Color(0xFF0A84FF), modifier = Modifier.size(30.dp)
                )
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Label("Connected", size = 20, weight = FontWeight.SemiBold)
                Label(m.name, color = Muted, size = 14, weight = FontWeight.Normal)
            }
            Icon(Icons.Rounded.CheckCircle, null, tint = Green, modifier = Modifier.size(28.dp))
        }

        is IslandMode.LowBattery -> Row(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { m.percent / 100f },
                    modifier = Modifier.size(76.dp),
                    color = Red, trackColor = Color(0xFF3A1512),
                    strokeWidth = 8.dp, strokeCap = StrokeCap.Round
                )
                Label("${m.percent}", size = 22, weight = FontWeight.Bold)
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Label(
                    if (m.percent <= 10) "Battery very low" else "Battery low",
                    size = 20, weight = FontWeight.SemiBold
                )
                Label("Plug in your charger", color = Muted, size = 14, weight = FontWeight.Normal)
            }
            Icon(Icons.Rounded.BatteryAlert, null, tint = Red, modifier = Modifier.size(30.dp))
        }

        is IslandMode.Toggle -> Row(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(m.tint().copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(m.icon(), null, tint = m.tint(), modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Label(m.title(), size = 20, weight = FontWeight.SemiBold)
                Label(m.subtitle(), color = Muted, size = 14, weight = FontWeight.Normal)
            }
        }

        is IslandMode.Live -> Row(
            Modifier.fillMaxSize().padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(if (large) 56.dp else 50.dp).clip(CircleShape)
                    .background(LiveBlue.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(m.icon(), null, tint = LiveBlue, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Label(m.app.uppercase(), color = LiveBlue, size = 11, weight = FontWeight.Bold, spacing = 0.8f)
                Spacer(Modifier.height(3.dp))
                Label(m.title.ifBlank { m.app }, size = 17, weight = FontWeight.SemiBold, lines = if (large) 2 else 1)
                Spacer(Modifier.height(2.dp))
                val isDl = m.kind == LiveKind.Download
                Label(
                    m.text, color = Color(0xFFD1D1D6), size = 14,
                    lines = if (isDl) 1 else if (large) 3 else 2, weight = FontWeight.Normal
                )
                if (isDl) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (m.progress >= 0) {
                            LinearProgressIndicator(
                                progress = { m.progress / 100f },
                                modifier = Modifier.weight(1f).height(5.dp).clip(CircleShape),
                                color = LiveBlue, trackColor = LiveBlue.copy(alpha = 0.2f),
                                strokeCap = StrokeCap.Round
                            )
                            Spacer(Modifier.width(8.dp))
                            Label("${m.progress}%", color = LiveBlue, size = 12, weight = FontWeight.Bold)
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                                color = LiveBlue, trackColor = LiveBlue.copy(alpha = 0.2f),
                                strokeCap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------ PIECES ------------------------------ */

/** "+2": how many more banners are waiting. */
@Composable
private fun CountBadge(n: Int, color: Color) {
    Box(
        Modifier
            .height(18.dp)
            .widthIn(min = 18.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.35f))
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Label("+$n", color = Color.White, size = 10, weight = FontWeight.Bold)
    }
}

/** Music as the small second part of the compact pill: art + waveform. */
@Composable
private fun MiniMusic(m: IslandMode.Media, fallback: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Art(m, 22.dp, 6.dp)
        Spacer(Modifier.width(6.dp))
        Waveform(m.playing, m.accentColor(fallback), Modifier.height(14.dp))
    }
}

/** Music under a timer / navigation card: song (tap = open the music app) + prev / play / next. */
@Composable
private fun MiniPlayer(m: IslandMode.Media, a: IslandActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 18.dp)
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable { a.onOpenMedia() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Art(m, 34.dp, 9.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Label(m.title.ifBlank { "Unknown" }, size = 13, weight = FontWeight.SemiBold)
                Label(m.artist, color = Muted, size = 12, weight = FontWeight.Normal)
            }
        }
        RoundIcon(Icons.Rounded.SkipPrevious, 36.dp, Color.Transparent, Color.White, a.onPrev)
        RoundIcon(
            if (m.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            36.dp, Color.White, Color.Black, a.onPlayPause
        )
        RoundIcon(Icons.Rounded.SkipNext, 36.dp, Color.Transparent, Color.White, a.onNext)
    }
}

/** Dark-teal pill button (Mark as read / Reply). Its own click, so the card's tap gesture is not triggered. */
@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(c.copy(alpha = 0.22f))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Label(text, color = Color.White, size = 13, weight = FontWeight.SemiBold)
    }
}

/** Inline reply: text box + send + cancel. The keyboard opens by itself. */
@Composable
private fun ReplyBar(onSend: (String) -> Unit, onCancel: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // The window turns focusable a moment after Reply is tapped, so wait a little before asking for focus
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    fun send() { if (text.isNotBlank()) onSend(text.trim()) }

    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(Color(0xFF1C1C1E))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (text.isEmpty()) Label("Reply…", color = Muted, size = 14, weight = FontWeight.Normal)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
        }
        Spacer(Modifier.width(8.dp))
        RoundIcon(Icons.AutoMirrored.Rounded.Send, 40.dp, primary, Color.White) { send() }
        Spacer(Modifier.width(6.dp))
        RoundIcon(Icons.Rounded.Close, 40.dp, Color(0xFF2C2C2E), Color.White, onCancel)
    }
}

/** Shown when nothing is active and the island is tapped: time, date, battery. */
@Composable
private fun IdleCard() {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(10_000)
        }
    }
    // (level %, charging) from the sticky battery broadcast: one read, no receiver to leak
    val (battery, charging) = remember(now) {
        runCatching {
            val i = ctx.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val level = i?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = i?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
            val status = i?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) level * 100 / scale else 0
            pct to (status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                status == android.os.BatteryManager.BATTERY_STATUS_FULL)
        }.getOrDefault(0 to false)
    }
    val batteryColor = when {
        charging -> Green
        battery <= 15 -> Red
        battery <= 30 -> Orange
        else -> Green
    }
    val batteryIcon = when {
        charging -> Icons.Rounded.BatteryChargingFull
        battery <= 15 -> Icons.Rounded.BatteryAlert
        battery <= 50 -> Icons.Rounded.Battery3Bar
        else -> Icons.Rounded.BatteryFull
    }
    val time = remember(now) {
        // Follow the phone's 12 / 24-hour setting
        val pattern = if (android.text.format.DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm"
        java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault()).format(java.util.Date(now))
    }
    val date = remember(now) {
        java.text.SimpleDateFormat("EEE, d MMM", java.util.Locale.getDefault()).format(java.util.Date(now))
    }
    Row(
        Modifier.fillMaxSize().padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Label(time, size = 34, weight = FontWeight.Light, tabular = true)
            Label(date, color = Muted, size = 14, weight = FontWeight.Normal)
        }
        Icon(batteryIcon, null, tint = batteryColor, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        Label("$battery%", size = 16, weight = FontWeight.SemiBold)
    }
}

@Composable
private fun rememberElapsedSec(since: Long): Int {
    var sec by remember(since) { mutableIntStateOf(((SystemClock.elapsedRealtime() - since) / 1000).toInt()) }
    LaunchedEffect(since) {
        while (true) {
            delay(1000)
            sec = ((SystemClock.elapsedRealtime() - since) / 1000).toInt()
        }
    }
    return sec
}

/** Tap to seek. The position advances by itself every 250 ms. */
@Composable
private fun SeekBar(m: IslandMode.Media, accent: Color, onSeek: (Long) -> Unit) {
    var pos by remember { mutableLongStateOf(m.currentPosition()) }
    LaunchedEffect(m.playing, m.positionMs, m.updatedAt, m.title) {
        pos = m.currentPosition()
        while (m.playing) {
            delay(250)
            pos = m.currentPosition()
        }
    }
    val dur = m.durationMs
    val frac = if (dur > 0) (pos / dur.toFloat()).coerceIn(0f, 1f) else 0f

    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(18.dp)
                .pointerInput(dur) {
                    detectTapGestures { off ->
                        if (dur > 0) onSeek((off.x / size.width * dur).toLong().coerceIn(0L, dur))
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Track))
            Box(Modifier.fillMaxWidth(frac).height(4.dp).clip(CircleShape).background(accent))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label(formatClock(pos), color = Muted, size = 11, tabular = true)
            Label(if (dur > 0) formatClock(dur) else "--:--", color = Muted, size = 11, tabular = true)
        }
    }
}

/** Canvas waveform. Audio is not captured (that would need RECORD_AUDIO); the bars are animated with a sine wave. */
@Composable
private fun Waveform(
    playing: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    bars: Int = 4,
    barWidth: Dp = 3.dp,
    gap: Dp = 3.dp,
) {
    // The loop only runs while playing. Paused music = no frames are scheduled, so no battery use
    // (an always-on infinite transition kept a 60 fps animation going even for a paused pill).
    val phase = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        val full = (2 * PI).toFloat()
        while (true) {
            val ms = ((full - phase.value) / full * 1600f).toInt().coerceAtLeast(1)
            phase.animateTo(full, tween(ms, easing = LinearEasing))
            phase.snapTo(0f)
        }
    }
    val amount by animateFloatAsState(if (playing) 1f else 0f, spring(), label = "amount")

    Canvas(modifier.width((barWidth + gap) * bars - gap)) {
        val bw = barWidth.toPx()
        val g = gap.toPx()
        for (i in 0 until bars) {
            val s = (sin(phase.value * (1 + i % 3) + i * 1.3f) + 1f) / 2f
            val frac = 0.18f + 0.82f * s * amount
            val h = size.height * frac
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (bw + g), (size.height - h) / 2f),
                size = Size(bw, h),
                cornerRadius = CornerRadius(bw / 2f)
            )
        }
    }
}

@Composable
private fun RoundIcon(
    icon: ImageVector,
    size: Dp,
    bg: Color,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.58f))
    }
}

@Composable
private fun Art(m: IslandMode.Media, size: Dp, radius: Dp) {
    val s = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(radius)
    if (m.art != null) {
        Image(
            bitmap = m.art.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(shape)
        )
    } else {
        Box(
            Modifier
                .size(size)
                .clip(shape)
                .background(Brush.linearGradient(listOf(s.primary, s.tertiary))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
        }
    }
}

/** First user-visible character. take(1) cut emoji (surrogate pairs, ZWJ / flag sequences) in half. */
private fun firstGrapheme(name: String): String {
    val t = name.trim()
    if (t.isEmpty()) return "?"
    val it = android.icu.text.BreakIterator.getCharacterInstance()
    it.setText(t)
    val end = it.next()
    return t.substring(0, if (end == android.icu.text.BreakIterator.DONE) t.length else end).uppercase()
}

@Composable
private fun Avatar(name: String, size: Dp) {
    val palette = listOf(0xFF0A84FF, 0xFF30D158, 0xFFFF9F0A, 0xFFFF375F, 0xFFBF5AF2, 0xFF64D2FF)
    val c = Color(palette[(name.hashCode() and 0x7fffffff) % palette.size])
    Box(
        Modifier.size(size).clip(CircleShape).background(c),
        contentAlignment = Alignment.Center
    ) {
        Text(
            firstGrapheme(name),
            color = Color.White,
            fontSize = (size.value * 0.46f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun Label(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    size: Int = 13,
    lines: Int = 1,
    weight: FontWeight = FontWeight.Medium,
    tabular: Boolean = false,
    spacing: Float = 0f,
) = Text(
    text,
    modifier = modifier,
    color = color,
    fontSize = size.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    maxLines = lines,
    overflow = TextOverflow.Ellipsis,
    style = if (tabular) TextStyle(fontFeatureSettings = "tnum") else LocalTextStyle.current
)

private fun formatTime(s: Int) =
    if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    else "%02d:%02d".format(s / 60, s % 60)

private fun formatClock(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}
