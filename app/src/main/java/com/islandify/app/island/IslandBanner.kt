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

// SPLITUI  (Banner: row, ring, badge, download ring.)

/** Width of the small banner used for notification / charging / device / toggle popups. */
internal fun bannerWidth(mode: IslandMode): Dp = when (mode) {
    is IslandMode.Charging -> 228.dp
    is IslandMode.LowBattery -> 228.dp
    is IslandMode.Toggle -> 228.dp
    is IslandMode.Device -> 240.dp
    is IslandMode.Notification -> 336.dp   // longer pill: title + message text fit (screen width is the limit)
    is IslandMode.Call -> if (mode.incoming) 316.dp else 292.dp   // CALLPILL
    else -> 258.dp
}

/** One slim row: [lead] title / subtitle [trail]. */
@Composable
internal fun BannerRow(
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
        val bnSub by IslandSettings.bannerSub.collectAsState()   // BANNERKIT
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Label(title, size = (13 * ts).roundToInt(), weight = FontWeight.SemiBold)
            if (bnSub && subtitle.isNotBlank()) Label(subtitle, color = Muted, size = (11 * ts).roundToInt(), weight = FontWeight.Normal)
        }
        if (trail != null) {
            Spacer(Modifier.width(8.dp))
            trail()
        }
    }
}

@Composable
internal fun BannerRing(percent: Int, color: Color, icon: ImageVector, ts: Float = 1f) {
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
internal fun BannerBadge(icon: ImageVector, tint: Color, bg: Color, ts: Float = 1f) {
    Box(
        Modifier.size((28 * ts).dp).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size((15 * ts).dp))
    }
}

@Composable
internal fun Banner(m: IslandMode, queued: Int) {
    val primary = MaterialTheme.colorScheme.primary
    val kindStyles by IslandSettings.kindStyles.collectAsState()
    val bnText by IslandSettings.bannerText.collectAsState()     // BANNERKIT
    val bnBadge by IslandSettings.bannerBadge.collectAsState()
    val ts = (kindOf(m)?.let { kindStyles[it.key]?.text } ?: 1f) * bnText
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
                title = if (m.connected) Isl.charging else Isl.chargerRemoved,   // ISLANDI18N
                subtitle = if (m.connected) m.detail.split(ChargeInfo.SEP).take(2).joinToString(ChargeInfo.SEP).ifBlank { Isl.powerConnected } else Isl.unplugged,   // CHARGEINFO
                trail = { Label("${m.percent}%", color = c, size = (14 * ts).roundToInt(), weight = FontWeight.Bold) },
                ts = ts
            )
        }
        is IslandMode.LowBattery -> BannerRow(
            lead = { BannerRing(m.percent, Red, Icons.Rounded.BatteryAlert, ts) },
            title = if (m.percent <= 10) Isl.batteryVeryLow else Isl.batteryLow,   // ISLANDI18N
            subtitle = Isl.plugIn,
            trail = { Label("${m.percent}%", color = Red, size = (14 * ts).roundToInt(), weight = FontWeight.Bold) },
            ts = ts
        )
        is IslandMode.Notification -> BannerRow(
            lead = { NotifAvatar(m, (28 * ts).dp) },
            title = m.title.ifBlank { m.displayApp() },   // ISLANDI18N
            subtitle = m.text.ifBlank { m.displayApp() },
            trail = {
                if (bnBadge) {
                    if (queued > 0) CountBadge(queued, primary)
                    else Box(Modifier.size((7 * ts).dp).clip(CircleShape).background(primary))
                }
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
            title = Isl.connected,   // ISLANDI18N
            subtitle = if (m.battery >= 0) "${m.name} \u00B7 ${m.battery}%" else m.name,   // BTBATT
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

/** Download ring: filled ring when the progress is known, spinner otherwise. */
@Composable
internal fun DownloadRing(progress: Int, size: Dp) {
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
