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

// SPLITUI  (Chhote common widgets: CountBadge, PillButton, RoundIcon, Label.)

/** "+2": how many more banners are waiting. */
@Composable
internal fun CountBadge(n: Int, color: Color) {
    val look = LocalShellLook.current   // M3DESIGN
    Box(
        Modifier
            .height(18.dp)
            .widthIn(min = 18.dp)
            .clip(CircleShape)
            .background(if (look.dynamic) look.accent else color.copy(alpha = 0.35f))
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Label("+$n", color = if (look.dynamic) look.onAccent else Color.White, size = 10, weight = FontWeight.Bold)
    }
}

/** Dark-teal pill button (Mark as read / Reply). Its own click, so the card's tap gesture is not triggered. */
@Composable
internal fun PillButton(text: String, onClick: () -> Unit) {   // M3KIT
    val c = MaterialTheme.colorScheme.primary
    val look = LocalShellLook.current   // M3DESIGN
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.height(34.dp),
        shape = CircleShape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (look.dynamic) look.btn else c.copy(alpha = 0.22f),
            contentColor = if (look.dynamic) look.onBtn else Color.White,
        ),
        border = if (look.dynamic && look.btnBorder != Color.Unspecified) androidx.compose.foundation.BorderStroke(1.dp, look.btnBorder) else null,
        contentPadding = PaddingValues(horizontal = 18.dp),
    ) {
        Label(text, color = if (look.dynamic) look.onBtn else Color.White, size = 13, weight = FontWeight.SemiBold)
    }
}

@Composable
internal fun RoundIcon(
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
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),   // POLISHKIT: TalkBack role
        contentAlignment = Alignment.Center
    ) {
        val descRes = roundIconDesc(icon)
        val desc = if (descRes != null) androidx.compose.ui.res.stringResource(descRes) else null
        Icon(icon, desc, tint = tint, modifier = Modifier.size(size * 0.58f))
    }
}

/** POLISHKIT: TalkBack ke liye round button ka naam (icon ke naam se pehchan). Pehchan na ho to null = pehle jaisa. */
internal fun roundIconDesc(icon: ImageVector): Int? = when (icon.name.substringAfterLast('.')) {
    "CallEnd" -> com.islandify.app.R.string.a11y_call_end
    "Call" -> com.islandify.app.R.string.a11y_call_answer
    "Mic" -> com.islandify.app.R.string.a11y_mute          // mic dikh raha = abhi unmuted, button mute karega
    "MicOff" -> com.islandify.app.R.string.a11y_unmute
    "VolumeUp" -> com.islandify.app.R.string.a11y_speaker
    "SkipPrevious" -> com.islandify.app.R.string.a11y_prev
    "SkipNext" -> com.islandify.app.R.string.a11y_next
    "FastRewind" -> com.islandify.app.R.string.a11y_prev   // APPLEMUSIC
    "FastForward" -> com.islandify.app.R.string.a11y_next
    "PlayArrow" -> com.islandify.app.R.string.a11y_play
    "Pause" -> com.islandify.app.R.string.a11y_pause
    "Send" -> com.islandify.app.R.string.a11y_send
    "Close" -> com.islandify.app.R.string.a11y_close
    "VisibilityOff" -> com.islandify.app.R.string.a11y_hide_timer
    "FlashOff" -> com.islandify.app.R.string.a11y_torch_off
    else -> null
}

@Composable
internal fun Label(
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
    color = LocalShellLook.current.map(color),   // M3DESIGN: safed / grey -> M3 role (dynamic design me)
    fontFamily = if (weight >= FontWeight.SemiBold && size >= 15) com.islandify.app.ui.theme.AppFonts.heading
        else com.islandify.app.ui.theme.AppFonts.body,   // FONTNEON: bade bold titles Unbounded, baaki Sora
    fontSize = size.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    maxLines = lines,
    overflow = TextOverflow.Ellipsis,
    style = if (tabular) LocalTextStyle.current.copy(fontFeatureSettings = "tnum") else LocalTextStyle.current   // FONTNEON
)
