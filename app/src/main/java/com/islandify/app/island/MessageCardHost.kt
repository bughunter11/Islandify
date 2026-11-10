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

// SPLITKIT  (BubbleCardHost: card kab dikhe / blocked / auto-collapse.)

@Composable
internal fun BubbleCardHost() {
    val items by BubbleStore.shownItems.collectAsState()   // PREVIEWKIT
    val pv by BubbleStore.preview.collectAsState()
    val cardOpen by BubbleStore.cardOpen.collectAsState()
    val selectedKey by BubbleStore.selected.collectAsState()
    val replying by BubbleStore.replying.collectAsState()
    val readTick by BubbleStore.readTick.collectAsState()   // CARDSCROLL
    val level by IslandController.level.collectAsState()
    val mode by IslandController.mode.collectAsState()
    val showWhen by IslandSettings.showWhen.collectAsState()
    val isLocked by IslandVisibility.locked.collectAsState()
    val isScreenOn by IslandVisibility.screenOn.collectAsState()
    val hideLand by IslandSettings.hideLandscape.collectAsState()
    val autoSec by IslandSettings.autoCollapseSec.collectAsState()
    val scale by IslandSettings.scale.collectAsState()
    val islandFont by IslandSettings.fontScale.collectAsState()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val quietNow = rememberIslandSuppressed()   // QUIETKIT
    val allowed = !quietNow && when (showWhen) {
        0 -> isScreenOn && !isLocked
        1 -> isScreenOn
        else -> true
    }
    // a pop-up / call took over the island: the card folds back into the bubble
    val pvCard = pv == 3   // PREVIEWKIT: Customize > Message card khula hai
    val blocked = if (pvCard) mode is IslandMode.Call
        else level != IslandLevel.Compact || mode is IslandMode.Call || !allowed || (hideLand && landscape)
    val visible = (cardOpen || pvCard) && items.isNotEmpty() && !blocked

    LaunchedEffect(cardOpen, blocked) { if (cardOpen && blocked) BubbleStore.closeCard() }
    // auto-collapse (same time as the other cards), but never while typing
    LaunchedEffect(visible, selectedKey, replying, autoSec, pvCard, readTick) {   // CARDSCROLL
        if (visible && !replying && autoSec > 0 && !pvCard) {
            delay(autoSec * 1000L)
            BubbleStore.closeCard()
        }
    }

    var last by remember { mutableStateOf(items) }
    if (items.isNotEmpty()) last = items
    var lastKey by remember { mutableStateOf(selectedKey) }
    if (selectedKey != null) lastKey = selectedKey
    val liveItem = last.firstOrNull { it.key == lastKey } ?: last.lastOrNull()
    // DIRECTCARD3: reply likhte waqt card wahi message dikhaye jis par reply de rahe ho
    val frozen = remember(replying) { if (replying && cardOpen) liveItem else null }
    val item = frozen ?: liveItem

    val base = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(base.density * scale, base.fontScale * islandFont)) {
        AnimatedVisibility(
            visible = visible && item != null,
            enter = scaleIn(tween(220), 0.5f, TransformOrigin(0.5f, 0f)) + fadeIn(tween(160)),
            exit = scaleOut(tween(180), 0.3f, TransformOrigin(0.5f, 0f)) + fadeOut(tween(140)),
        ) {
            if (item != null) {
                val screenPx = LocalContext.current.resources.displayMetrics.widthPixels
                val maxW = (screenPx / LocalDensity.current.density).dp - 8.dp
                // first message = no bubble row; from the second message on = card + bubbles (image 3)
                val showRow = last.size > 1 || item.count > 1
                BubbleCardColumn(last, item, maxW, replying, showRow)
            }
        }
    }
}
