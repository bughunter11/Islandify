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

// SPLITUI  (Toggle / Live mode ka icon, title, subtitle, tint.)

internal fun IslandMode.Toggle.icon(): ImageVector = when (kind) {
    ToggleKind.Silent -> if (on) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp
    ToggleKind.Vibrate -> Icons.Rounded.Vibration
    ToggleKind.Dnd -> if (on) Icons.Rounded.DoNotDisturbOn else Icons.Rounded.NotificationsActive
    ToggleKind.Flashlight -> if (on) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff
    ToggleKind.Wifi -> if (on) Icons.Rounded.Wifi else Icons.Rounded.WifiOff
    ToggleKind.Airplane -> if (on) Icons.Rounded.AirplanemodeActive else Icons.Rounded.AirplanemodeInactive
    ToggleKind.Hotspot -> Icons.Rounded.WifiTethering   // EXTRAKIT
    ToggleKind.Vpn -> Icons.Rounded.VpnKey
    ToggleKind.Camera -> Icons.Rounded.Videocam
    ToggleKind.Mic -> Icons.Rounded.Mic
    ToggleKind.ScreenRec -> Icons.Rounded.FiberManualRecord
}

internal fun IslandMode.Toggle.title(): String = when (kind) {   // ISLANDI18N
    ToggleKind.Silent -> if (on) Isl.tSilentOn else Isl.tSilentOff
    ToggleKind.Vibrate -> Isl.tVibrate
    ToggleKind.Dnd -> if (on) Isl.tDndOn else Isl.tDndOff
    ToggleKind.Flashlight -> if (on) Isl.tFlashOn else Isl.tFlashOff
    ToggleKind.Wifi -> if (on) Isl.tWifiOn else Isl.tWifiOff
    ToggleKind.Airplane -> if (on) Isl.tAirOn else Isl.tAirOff
    ToggleKind.Hotspot -> if (on) Isl.tHotspotOn else Isl.tHotspotOff   // EXTRAKIT
    ToggleKind.Vpn -> if (on) Isl.tVpnOn else Isl.tVpnOff
    ToggleKind.Camera -> Isl.tCamera
    ToggleKind.Mic -> Isl.tMic
    ToggleKind.ScreenRec -> if (on) Isl.tRecOn else Isl.tRecOff
}

internal fun IslandMode.Toggle.subtitle(): String = when (kind) {   // ISLANDI18N
    ToggleKind.Silent -> if (on) Isl.sSilentOn else Isl.sSilentOff
    ToggleKind.Vibrate -> Isl.sVibrate
    ToggleKind.Dnd -> if (on) Isl.sDndOn else Isl.sDndOff
    ToggleKind.Flashlight -> if (on) Isl.sFlashOn else Isl.sFlashOff
    ToggleKind.Wifi -> if (on) Isl.sWifiOn else Isl.sWifiOff
    ToggleKind.Airplane -> if (on) Isl.sAirOn else Isl.sAirOff
    ToggleKind.Hotspot -> if (on) Isl.sHotspotOn else Isl.sHotspotOff   // EXTRAKIT
    ToggleKind.Vpn -> if (on) Isl.sVpnOn else Isl.sVpnOff
    ToggleKind.Camera -> Isl.sCamera
    ToggleKind.Mic -> Isl.sMic
    ToggleKind.ScreenRec -> if (on) Isl.sRecOn else Isl.sRecOff
}

internal fun IslandMode.Toggle.tint(): Color = when (kind) {
    ToggleKind.Silent, ToggleKind.Vibrate -> if (on) Orange else Green
    ToggleKind.Dnd -> if (on) DndPurple else Green
    ToggleKind.Flashlight -> if (on) Color(0xFFFFD60A) else Muted
    ToggleKind.Wifi -> if (on) LiveBlue else Muted
    ToggleKind.Airplane -> if (on) Orange else Green
    ToggleKind.Hotspot -> if (on) LiveBlue else Muted   // EXTRAKIT
    ToggleKind.Vpn -> if (on) Green else Muted
    ToggleKind.Camera -> Green
    ToggleKind.Mic -> Orange
    ToggleKind.ScreenRec -> if (on) Red else Muted
}

internal fun IslandMode.Live.icon(): ImageVector {
    if (kind == LiveKind.Navigation) return Icons.Rounded.Navigation
    if (kind == LiveKind.Download) return Icons.Rounded.Download
    val a = app.lowercase()
    val ride = listOf("uber", "ola", "rapido", "taxi", "cab").any { a.contains(it) }
    return if (ride) Icons.Rounded.LocalTaxi else Icons.Rounded.DeliveryDining
}
