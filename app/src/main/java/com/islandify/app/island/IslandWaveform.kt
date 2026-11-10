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

// SPLITUI  (Waveform.)

/**
 * WAVESYNC: waveform speed = user slider x (optional) the player's playback speed.
 * Podcast at 1.5x -> bars move 1.5x. Audio itself is never captured.
 */
@Composable
internal fun rememberWaveSpeed(playbackSpeed: Float): Float {
    val user by IslandSettings.waveSpeed.collectAsState()
    val follow by IslandSettings.waveFollow.collectAsState()
    val ps = if (follow && playbackSpeed > 0f) playbackSpeed else 1f
    return (user * ps).coerceIn(0.25f, 4f)
}

/** Canvas waveform. Audio is not captured (that would need RECORD_AUDIO); the bars are animated with a sine wave. */
@Composable
internal fun Waveform(
    playing: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    bars: Int = 4,
    barWidth: Dp = 3.dp,
    gap: Dp = 3.dp,
    speed: Float = 1f,   // WAVESYNC
) {
    // The loop only runs while playing. Paused music = no frames are scheduled, so no battery use
    // (an always-on infinite transition kept a 60 fps animation going even for a paused pill).
    val phase = remember { Animatable(0f) }
    val spd by rememberUpdatedState(speed.coerceIn(0.25f, 4f))   // WAVESYNC
    val reduceMotion = com.islandify.app.core.rememberReduceMotion()   // MOTIONKIT (PATCH_10_11_12): bars hilte nahi
    LaunchedEffect(playing, reduceMotion) {
        if (!playing || reduceMotion) return@LaunchedEffect
        val full = (2 * PI).toFloat()
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
            last = now
            // WAVESYNC: one full cycle = 1.6 s at 1x
            phase.snapTo((phase.value + dt * spd * full / 1.6f) % full)
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
