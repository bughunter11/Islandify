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

// SPLITKIT  IslandBody: pill / banner / card ki target width + height (bubble ke liye jagah ka hisaab bhi)

@Composable
internal fun islandTargetSize(
    mode: IslandMode,
    lvl: IslandLevel,
    widthScale: Float,
    heightScale: Float,
    bs: KindStyle,
    isPopup: Boolean,
    sec: IslandMode.Media?,
): Pair<Dp, Dp> {
    val idleInfoOn by IslandSettings.idleInfo.collectAsState()
    val baseW = when (lvl) {
        IslandLevel.Compact -> when (mode) {
            IslandMode.Idle -> if (idleInfoOn) 156.dp else 118.dp
            is IslandMode.Charging -> 150.dp
            is IslandMode.Timer -> if (sec != null) 214.dp else 160.dp
            is IslandMode.Notification -> 224.dp
            is IslandMode.Media -> 156.dp
            is IslandMode.Call -> if (mode.incoming) 316.dp else 292.dp   // CALLPILL: buttons ke liye chauda
            is IslandMode.Device -> 200.dp
            is IslandMode.LowBattery -> 150.dp
            is IslandMode.Toggle -> 190.dp
            is IslandMode.Live -> if (sec != null) 256.dp else 224.dp
        }
        IslandLevel.Banner -> bannerWidth(mode)
        else -> 368.dp
    }
    val baseH = when (lvl) {
        IslandLevel.Compact -> 38.dp
        IslandLevel.Banner -> 48.dp
        IslandLevel.Expanded -> expandedHeight(mode)
        IslandLevel.Large -> when (mode) {
            is IslandMode.Media -> 244.dp   // SEEKIMG3: +8dp seekbar
            is IslandMode.Notification -> if (mode.canReply || mode.canMarkRead) 224.dp else 200.dp
            is IslandMode.Live -> 168.dp
            IslandMode.Idle -> 184.dp   // CALLIST: time + 3 events
            else -> expandedHeight(mode)
        }
    }

    // Never wider than the screen (density is already scaled, so derive dp from it)
    val screenPx = LocalContext.current.resources.displayMetrics.widthPixels
    val maxW = (screenPx / LocalDensity.current.density).dp - 8.dp
    // BANNERKIT: Customize > Banner
    val bnW by IslandSettings.bannerW.collectAsState()
    val bnH by IslandSettings.bannerH.collectAsState()
    val targetW0 = (baseW * (when (lvl) {
        IslandLevel.Banner -> bs.w * bnW
        IslandLevel.Compact -> widthScale * (if (isPopup) 1f else bs.w)
        else -> widthScale * bs.cardW
    })).coerceAtMost(maxW)
    // BUBBLEROOM: bubble pill ke left me rehta hai. Agar jagah kam ho to pill / banner chhota ho jaye
    // (sirf Compact + Banner; bade cards jaise the waise). Bubble hidden ho (card khula) ya call ho to ye rule nahi lagta.
    val bbItems by BubbleStore.shownItems.collectAsState()   // PREVIEWKIT
    val bbPv by BubbleStore.preview.collectAsState()
    val bbCardOpen by BubbleStore.cardOpen.collectAsState()
    val bbEnabled by IslandSettings.bubbleEnabled.collectAsState()   // BUBBLETOGGLE
    val bbSize by IslandSettings.bubbleSize.collectAsState()
    val bbGap by IslandSettings.bubbleGap.collectAsState()
    val bbOffX by IslandSettings.offsetX.collectAsState()
    val realDens = LocalContext.current.resources.displayMetrics.density
    val bbMoveOn by IslandSettings.bubbleMove.collectAsState()   // BUBBLEMOVE
    val bbFreeOn by IslandSettings.bubbleFreeOn.collectAsState()
    val roomCap = if (bbEnabled && !(bbMoveOn && bbFreeOn) && BubbleStore.worthShowing(bbItems) && !bbCardOpen && bbPv != 3 && mode !is IslandMode.Call &&   // SINGLEHIDE
        (lvl == IslandLevel.Compact || lvl == IslandLevel.Banner)
    ) {
        val centerPx = screenPx / 2f + bbOffX * realDens
        val needPx = (bbSize + 12f + bbGap) * realDens          // bubble window (6dp pad each side) + gap
        val edgePx = 6f * realDens                              // screen ke kinare se thodi doori
        // BUBBLESHIFT: pill right me need/2 khisakti hai, to dono taraf barabar jagah bachti hai
        val halfMaxPx = minOf(centerPx - needPx / 2f - edgePx, screenPx - centerPx - needPx / 2f - edgePx)
        (halfMaxPx * 2f / LocalDensity.current.density).dp
    } else null
    val targetW = if (roomCap != null && roomCap >= 96.dp) targetW0.coerceAtMost(roomCap) else targetW0
    // The small pill keeps a sane height; the height slider mainly shapes the cards
    // The mini player adds a row under the card
    val miniExtra = if (sec != null && (lvl == IslandLevel.Expanded || lvl == IslandLevel.Large)) 52.dp else 0.dp
    val targetH = (baseH + miniExtra) * (when (lvl) {
        IslandLevel.Compact -> heightScale.coerceIn(0.8f, 1.2f) * (if (isPopup) 1f else bs.h)
        IslandLevel.Banner -> bs.h * bnH   // own height for each banner type (+ BANNERKIT global)
        else -> heightScale * bs.cardH
    })
    return targetW to targetH
}
