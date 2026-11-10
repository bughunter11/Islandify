package com.islandify.app.island

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize   // SWIPEANIM
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import android.view.MotionEvent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll   // CARDSCROLL
import androidx.compose.foundation.layout.heightIn   // CARDSCROLL
import androidx.compose.ui.draw.drawWithContent   // CARDSCROLL
import androidx.compose.ui.graphics.BlendMode   // CARDSCROLL
import androidx.compose.ui.graphics.CompositingStrategy   // CARDSCROLL
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults   // M3KIT
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.islandify.app.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

// SPLITKIT  (Bubble + card ke dp constants aur bannerYAdjDp().)

internal val BUBBLE_DP: Float get() = IslandSettings.bubbleSize.value   // bubble diameter (Customize > Bubble)

internal const val PAD_DP = 6f          // transparent room for the swipe nudge
internal const val CAM_HALF_DP = 22f    // half of the camera hole + gap (bubble sits left of it)
internal val Y_ADJ_DP: Float get() = IslandSettings.bubbleY.value   // nudge up / down (Customize > Bubble)
internal val GAP_DP: Float get() = IslandSettings.bubbleGap.value   // gap between the pill and the bubble (Customize > Bubble)

// OVERLAPKIT: overlap layout me 2-3 gol ek ke upar chadhe hote hain -> bubble utna chauda (n = dikh rahe items)
internal const val OVERLAP_STEP = 0.50f   // do gol ke centre ke beech ka fasla = bubble size ka ye hissa
internal fun bubbleOverlapExtraDp(n: Int): Float =
    if (IslandSettings.bubbleOverlap.value && n > 1) (minOf(n, 3) - 1) * BUBBLE_DP * OVERLAP_STEP else 0f

// BANNERY + KINDY: banner (ya Banner preview) dikh raha ho to global Banner up/down + us banner type ka apna up/down (dp); warna 0
internal fun bannerYAdjDp(): Float {
    if (IslandController.level.value == IslandLevel.Banner) {
        val k = com.islandify.app.core.kindOf(IslandController.mode.value)
        return IslandSettings.bannerY.value + (k?.let { IslandSettings.kindStyle(it).y } ?: 0f)
    }
    val pv = BubbleStore.preview.value
    return if (pv == 2 || pv == 5) IslandSettings.bannerY.value else 0f
}

// MUSICPOS: bada music card (ya Music card preview) dikh raha ho to uska apna left/right + up/down (dp); warna 0.
// Ye main island position (Placement) ke upar judta hai, to camera align karne par music card bhi saath chalta hai.
internal fun musicCardOn(): Boolean {
    val mode = IslandController.mode.value
    if (BubbleStore.preview.value == 7) return mode !is IslandMode.Call
    val lvl = IslandController.level.value
    return mode is IslandMode.Media && (lvl == IslandLevel.Expanded || lvl == IslandLevel.Large)
}
internal fun musicXAdjDp(): Float = if (musicCardOn()) IslandSettings.musicX.value else 0f
internal fun musicYAdjDp(): Float = if (musicCardOn()) IslandSettings.musicY.value else 0f

internal const val CARD_W_DP = 420f   // screen width is the limit (see maxW)

// BUBBLEMOVE: true = user ne bubble khud khiska rakha hai (pill ke saath nahi chalta, pill ko jagah nahi deta)
internal fun bubbleIsFree(): Boolean = IslandSettings.bubbleMove.value && IslandSettings.bubbleFreeOn.value
