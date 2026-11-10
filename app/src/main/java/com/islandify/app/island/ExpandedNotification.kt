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

// SPLITKIT  Expanded / Large card: Notification. (Kaun sa card dikhana hai ye IslandExpanded.kt me chunta hai.)

@Composable
internal fun ExpandedNotification(m: IslandMode.Notification, large: Boolean, a: IslandActions) {
    val primary = MaterialTheme.colorScheme.primary
    val actionable = m.canReply || m.canMarkRead
    val typing = a.replying && m.canReply
    Column(
        Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = if (actionable) 14.dp else 20.dp)
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.Top) {
            NotifAvatar(m, if (actionable && !large) 40.dp else if (large) 52.dp else 46.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label(
                        m.displayApp().uppercase(), Modifier.weight(1f),   // ISLANDI18N
                        color = primary, size = 11, weight = FontWeight.Bold, spacing = 0.8f
                    )
                    if (a.queued > 0) CountBadge(a.queued, primary)
                }
                Spacer(Modifier.height(3.dp))
                Label(
                    m.title.ifBlank { m.displayApp() }, size = 17, weight = FontWeight.SemiBold,
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
                    if (m.canMarkRead) PillButton(m.markReadLabel, a.onMarkRead)   // CARDACTIONS
                    if (m.canReply) PillButton(Isl.reply, a.onReply)   // ISLANDI18N
                }
            }
        }
    }
}
