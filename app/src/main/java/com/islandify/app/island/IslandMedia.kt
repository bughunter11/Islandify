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

// SPLITUI  (Music: mini player, art, media helpers.)

internal fun IslandMode.Media.accentColor(fallback: Color): Color = accent?.let { Color(it) } ?: fallback

internal fun IslandMode.Media.currentPosition(): Long {
    if (!playing || updatedAt == 0L) return positionMs
    val p = positionMs + ((SystemClock.elapsedRealtime() - updatedAt) * speed).toLong()
    return if (durationMs > 0) p.coerceIn(0L, durationMs) else p
}

/* ------------------------------ COMPACT ------------------------------ */

/** Music as the small second part of the compact pill: art + waveform. */
@Composable
internal fun MiniMusic(m: IslandMode.Media, fallback: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Art(m, 22.dp, 6.dp)
        Spacer(Modifier.width(6.dp))
        Waveform(m.playing, m.accentColor(fallback), Modifier.height(14.dp), speed = rememberWaveSpeed(m.speed))
    }
}

/** Music under a timer / navigation card: song (tap = open the music app) + prev / play / next. */
@Composable
internal fun MiniPlayer(m: IslandMode.Media, a: IslandActions) {
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
                Label(m.title.ifBlank { Isl.unknown }, size = 13, weight = FontWeight.SemiBold)
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

@Composable
internal fun Art(m: IslandMode.Media, size: Dp, radius: Dp) {
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
