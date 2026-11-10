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

// SPLITUI  (DynamicIslandUi: island ka entry composable.)

/**
 * [scale] resizes the whole island (dp and sp together).
 *
 * Gestures (all handled in one pointerInput below):
 *  - tap                    -> onClick (compact <-> card)
 *  - long-press             -> onLongPress (large card)
 *  - swipe up               -> onDismiss
 */
@Composable
fun DynamicIslandUi(
    mode: IslandMode,
    level: IslandLevel,
    scale: Float = 1f,
    widthScale: Float = 1f,
    heightScale: Float = 1f,
    cornerFactor: Float = 1f,
    animSpeed: Float = 1f,
    glow: Boolean = true,
    haptics: Boolean = true,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onPrev: () -> Unit = {},
    onPlayPause: () -> Unit = {},
    onNext: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onShuffle: () -> Unit = {},   // MUSICLAYOUT
    onRepeat: () -> Unit = {},    // MUSICLAYOUT
    onVolumeUp: () -> Unit = {},  // MUSICLAYOUT
    onVolumeDown: () -> Unit = {}, // MUSICLAYOUT
    onAnswer: () -> Unit = {},
    onDecline: () -> Unit = {},
    onMarkRead: () -> Unit = {},
    onReply: () -> Unit = {},
    onSendReply: (String) -> Unit = {},
    onCancelReply: () -> Unit = {},
    replying: Boolean = false,
    onSwipeSide: (Int) -> Unit = {},
    appAccent: Color? = null,
    onOpenMedia: () -> Unit = {},
    secondary: IslandMode.Media? = null,   // music next to a timer / navigation
    queued: Int = 0,                       // banners waiting behind the current one
    onSwipe: ((SwipeDir) -> Unit)? = null, // null = old behaviour (up = onDismiss, left / right = onSwipeSide)
    reportWidth: Boolean = false,          // WIDTHOWNER: sirf asli island window bubble ko apni chaudai batata hai
) {
    val base = LocalDensity.current
    // Text size of this kind (banners scale their own text, cards / pills scale the font here)
    val kindStyles by IslandSettings.kindStyles.collectAsState()
    val textMul = if (level == IslandLevel.Banner) 1f else (kindOf(mode)?.let { kindStyles[it.key]?.text } ?: 1f)
    val actions = IslandActions(
        onPrev = onPrev,
        onPlayPause = onPlayPause,
        onNext = onNext,
        onSeek = onSeek,
        onAnswer = onAnswer,
        onDecline = onDecline,
        onMarkRead = onMarkRead,
        onReply = onReply,
        onSendReply = onSendReply,
        onCancelReply = onCancelReply,
        replying = replying,
        onOpenMedia = onOpenMedia,
        queued = queued,
        onShuffle = onShuffle,         // MUSICLAYOUT
        onRepeat = onRepeat,           // MUSICLAYOUT
        onVolumeUp = onVolumeUp,       // MUSICLAYOUT
        onVolumeDown = onVolumeDown,   // MUSICLAYOUT
    )
    val islandFont by IslandSettings.fontScale.collectAsState()
    CompositionLocalProvider(LocalDensity provides Density(base.density * scale, base.fontScale * textMul * islandFont)) {
        IslandBody(
            mode, level, widthScale, heightScale, cornerFactor, animSpeed, glow, haptics,
            onClick, onLongPress, onDismiss, actions,
            onSwipeSide, appAccent, secondary, queued, onSwipe, reportWidth
        )
    }
}
