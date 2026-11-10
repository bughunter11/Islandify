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

// SPLITKIT  IslandBody: tap / long-press / swipe ka ek hi gesture handler + swipe animation ki state

/** Swipe ke waqt pill ki khisakne (swOff / swOut) aur finger feedback (fb) ki state. */
internal class IslandGestureState {
    var swOff by mutableStateOf(Offset.Zero)   // SWIPEANIM: pill ungli ke saath khisakti hai
    var swOut by mutableStateOf(0f)            // SWIPEANIM: 0..1 swipe ke baad pill ghul rahi hai
    // Finger feedback while swiping the island: 0 = untouched .. 1 = the swipe is far enough to count
    val fb = Animatable(0f)
}

// Sab kuch ek hi handler me (tap, long-press, swipe). Alag clickable nahi: do handlers ek hi touch par ladte the.
internal fun Modifier.islandGestures(
    gs: IslandGestureState,
    fbScope: kotlinx.coroutines.CoroutineScope,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    hapticOn: () -> Boolean,
    dMsg: () -> Boolean,
    dClick: () -> Unit,
    dLong: () -> Unit,
    dDismiss: () -> Unit,
    dSide: (Int) -> Unit,
    dSwipe: () -> ((SwipeDir) -> Unit)?,
): Modifier = this.pointerInput(Unit) {
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
            if (hapticOn()) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            dLong()
            // Swallow the rest of the touch until the finger lifts
            drag(down.id) { ch -> ch.consume() }
            currentEvent.changes.forEach { it.consume() }
        } else if (swipe) {
            var dx = accum.x
            var fbCrossed = false
            var fbLast = -1f   // SMOOTHKIT
            var dy = accum.y
            drag(down.id) { ch ->
                val d = ch.positionChange()
                dx += d.x
                dy += d.y
                // SWIPEANIM: pill ungli ki disha me thoda khisakti hai (side = dismiss, upar = hide)
                val swLim = 40.dp.toPx()
                gs.swOff = if (abs(dy) >= abs(dx)) Offset(0f, (dy * 0.5f).coerceIn(-swLim, 0f))
                    else Offset((dx * 0.5f).coerceIn(-swLim, swLim), 0f)
                val fbDist = if (abs(dy) >= abs(dx)) abs(dy) else abs(dx)
                val fbP = (fbDist / (slop * 2f)).coerceIn(0f, 1f)
                // SMOOTHKIT: har touch event par naya coroutine nahi, sirf jab value sach me badle
                if (abs(fbP - fbLast) > 0.01f) {
                    fbLast = fbP
                    fbScope.launch { gs.fb.snapTo(fbP) }
                }
                if (fbP >= 1f && !fbCrossed) {
                    fbCrossed = true
                    if (hapticOn()) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                } else if (fbP < 0.6f) fbCrossed = false
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
            val fbCommit = dist >= slop * 2f || speed >= 500f
            // SWIPEANIM: message wale pill par swipe = pill ungli ki disha me ghul ke jaati hai, phir hatti hai
            val swDir = if (vertical) (if (dy < 0) SwipeDir.Up else SwipeDir.Down)
                        else (if (dx < 0) SwipeDir.Left else SwipeDir.Right)
            val goesAway = fbCommit && dMsg() && swDir != SwipeDir.Down
            val swAway = 56.dp.toPx()
            val swFrom = gs.swOff
            val fire: () -> Unit = {
                val cb = dSwipe()
                if (cb != null) cb(swDir)
                else when (swDir) {
                    SwipeDir.Up -> dDismiss()
                    SwipeDir.Left -> dSide(1)
                    SwipeDir.Right -> dSide(-1)
                    SwipeDir.Down -> Unit
                }
            }
            fbScope.launch {
                if (goesAway) {
                    val sx = when (swDir) { SwipeDir.Left -> -1f; SwipeDir.Right -> 1f; else -> 0f }
                    val sy = if (swDir == SwipeDir.Up) -1f else 0f
                    animate(0f, 1f, animationSpec = tween(150, easing = FastOutLinearInEasing)) { v, _ ->
                        gs.swOut = v
                        gs.swOff = Offset(swFrom.x + sx * swAway * v, swFrom.y + sy * swAway * v)
                    }
                    fire()
                    delay(80)
                    val swEnd = gs.swOff
                    launch { gs.fb.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f)) }
                    animate(1f, 0f, animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f)) { v, _ ->
                        gs.swOut = v
                        gs.swOff = swEnd * v
                    }
                    gs.swOut = 0f
                    gs.swOff = Offset.Zero
                } else {
                    launch {
                        animate(1f, 0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f)) { v, _ ->
                            gs.swOff = swFrom * v
                        }
                    }
                    if (fbCommit) {
                        gs.fb.animateTo(1f, tween(70))
                        gs.fb.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f))
                    } else {
                        gs.fb.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f))
                    }
                }
            }
            if (fbCommit && !goesAway) fire()
        } else if (tap) {
            dClick()
        }
    }
}
