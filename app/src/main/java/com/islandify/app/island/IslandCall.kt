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

// SPLITUI  (CallPill.)

/**
 * CALLPILL: pill / banner me call ke buttons.
 * Incoming: mute, speaker, cut (decline), uthana (answer). Outgoing / ongoing: timer + mute, speaker, cut.
 */
@Composable
internal fun CallPill(m: IslandMode.Call) {
    val spk by IslandController.speakerOn.collectAsState()
    val mut by IslandController.muted.collectAsState()
    val btn = 30.dp   // POLISHKIT: bada touch target
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Call, null, tint = Green, modifier = Modifier.size(17.dp))
        Label(m.name, Modifier.weight(1f).padding(horizontal = 7.dp), size = 13)
        if (!m.incoming) {
            val sec = rememberElapsedSec(m.since)
            Label(formatTime(sec), color = Green, size = 12, weight = FontWeight.Bold, tabular = true)
            Spacer(Modifier.width(7.dp))
        }
        RoundIcon(
            if (mut) Icons.Rounded.MicOff else Icons.Rounded.Mic, btn,
            if (mut) Color.White else Color(0xFF2C2C2E),
            if (mut) Color.Black else Color.White,
        ) { IslandController.toggleMute() }
        Spacer(Modifier.width(5.dp))
        RoundIcon(
            Icons.AutoMirrored.Rounded.VolumeUp, btn,
            if (spk) Color.White else Color(0xFF2C2C2E),
            if (spk) Color.Black else Color.White,
        ) { IslandController.toggleSpeaker() }
        Spacer(Modifier.width(5.dp))
        RoundIcon(Icons.Rounded.CallEnd, btn, Red, Color.White) { IslandController.declineCall() }
        if (m.incoming && m.canAnswer) {
            Spacer(Modifier.width(5.dp))
            RoundIcon(Icons.Rounded.Call, btn, Green, Color.White) { IslandController.answerCall() }
        }
    }
}
