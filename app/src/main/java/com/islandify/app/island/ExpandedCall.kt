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

// SPLITKIT  Expanded / Large card: Call. (Kaun sa card dikhana hai ye IslandExpanded.kt me chunta hai.)

@Composable
internal fun ExpandedCall(m: IslandMode.Call, a: IslandActions) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(m.name, 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Label(m.name, size = 18, weight = FontWeight.SemiBold)
            if (m.incoming) {
                Label(Isl.incomingCall, color = Green, size = 14, weight = FontWeight.Normal)   // ISLANDI18N
            } else {
                val sec = rememberElapsedSec(m.since)
                Label(formatTime(sec), color = Green, size = 14, weight = FontWeight.Normal, tabular = true)
            }
        }
        if (!m.incoming) {
            val spk by IslandController.speakerOn.collectAsState()
            val mut by IslandController.muted.collectAsState()
            RoundIcon(
                if (mut) Icons.Rounded.MicOff else Icons.Rounded.Mic, 44.dp,
                if (mut) Color.White else Color(0xFF2C2C2E),
                if (mut) Color.Black else Color.White,
            ) { IslandController.toggleMute() }
            Spacer(Modifier.width(10.dp))
            RoundIcon(
                Icons.AutoMirrored.Rounded.VolumeUp, 44.dp,
                if (spk) Color.White else Color(0xFF2C2C2E),
                if (spk) Color.Black else Color.White,
            ) { IslandController.toggleSpeaker() }
            Spacer(Modifier.width(10.dp))
        }
        RoundIcon(Icons.Rounded.CallEnd, 52.dp, Red, Color.White, a.onDecline)
        if (m.incoming && m.canAnswer) {
            Spacer(Modifier.width(12.dp))
            RoundIcon(Icons.Rounded.Call, 52.dp, Green, Color.White, a.onAnswer)
        }
    }
}
