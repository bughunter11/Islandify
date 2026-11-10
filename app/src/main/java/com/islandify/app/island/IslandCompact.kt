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

// SPLITUI  (Compact pill.)

@Composable
internal fun Compact(m: IslandMode, queued: Int, sec: IslandMode.Media?) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxSize().padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        when (m) {
            IslandMode.Idle -> IdlePillInfo()
            is IslandMode.Charging -> {
                val c = if (m.connected) Green else Orange
                Icon(
                    if (m.connected) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
                    null, tint = c, modifier = Modifier.size(20.dp)
                )
                Label("${m.percent}%", color = c, size = 14, weight = FontWeight.Bold)
            }
            is IslandMode.Notification -> {
                NotifAvatar(m, 24.dp)
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
                Waveform(m.playing, m.accentColor(primary), Modifier.height(16.dp), speed = rememberWaveSpeed(m.speed))
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
            is IslandMode.Call -> CallPill(m)   // CALLPILL
            is IslandMode.Device -> {
                Icon(
                    if (m.headphones) Icons.Rounded.Headphones else Icons.Rounded.Bluetooth,
                    null, tint = Color(0xFF0A84FF), modifier = Modifier.size(19.dp)
                )
                Label(if (m.battery >= 0) "${m.name} ${m.battery}%" else m.name, Modifier.weight(1f).padding(start = 9.dp), size = 13)
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
