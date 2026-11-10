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
import androidx.compose.animation.core.Spring   // BUBBLEPOP
import androidx.compose.animation.core.spring   // BUBBLEPOP
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

// SPLITKIT  (BubbleHost / BubbleCircle / BubbleIcon: bubble ka Compose UI.)

@Composable
internal fun BubbleHost() {
    val items by BubbleStore.shownItems.collectAsState()   // PREVIEWKIT
    val pv by BubbleStore.preview.collectAsState()
    val cardOpen by BubbleStore.cardOpen.collectAsState()
    val level by IslandController.level.collectAsState()
    val mode by IslandController.mode.collectAsState()
    val showWhen by IslandSettings.showWhen.collectAsState()
    val isLocked by IslandVisibility.locked.collectAsState()
    val isScreenOn by IslandVisibility.screenOn.collectAsState()
    val hideLand by IslandSettings.hideLandscape.collectAsState()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val quietNow = rememberIslandSuppressed()   // QUIETKIT
    val allowed = !quietNow && when (showWhen) {
        0 -> isScreenOn && !isLocked
        1 -> isScreenOn
        else -> true
    }
    // The pill / card is on screen (level above Compact) or a call is active -> bubble waits.
    // Bubble pill ke side me rehta hai: chhota pill, banner ya bada card, sab me. Pill ke saath chalta hai.
    // Sirf tab chhupta hai jab pill itna chauda ho ki left me bubble ke liye jagah hi na bache.
    val pillPx by IslandController.pillWidthPx.collectAsState()
    val offX by IslandSettings.offsetX.collectAsState()
    val musicXNow by IslandSettings.musicX.collectAsState()   // MUSICPOS
    val musicOn = (mode is IslandMode.Media && (level == IslandLevel.Expanded || level == IslandLevel.Large)) ||
        (pv == 7 && mode !is IslandMode.Call)
    val dens = LocalDensity.current.density
    val screenW = LocalContext.current.resources.displayMetrics.widthPixels
    val halfPill = maxOf(pillPx / 2, (CAM_HALF_DP * dens).toInt())
    val shiftOnNow by BubbleStore.shiftOn.collectAsState()   // BUBBLESHIFT
    val leftEdge = screenW / 2f + (offX + (if (musicOn) musicXNow else 0f)) * dens - halfPill +   // MUSICPOS
        (if (shiftOnNow) BubbleStore.shiftPx(screenW, dens) else 0)
    // BUBBLEOVERLAP: bubble ki poori jagah (size + 2*pad + gap) chahiye. Pehle sirf 30dp dekhta tha,
    // to bubble ka window screen ke kinare par atak ke bade music card / pill ke upar chadh jata tha.
    val ovOn by IslandSettings.bubbleOverlap.collectAsState()   // OVERLAPKIT
    val needDp = BUBBLE_DP + 2 * PAD_DP + GAP_DP + (if (ovOn) bubbleOverlapExtraDp(items.size) else 0f)
    val bbMoveOn by IslandSettings.bubbleMove.collectAsState()   // BUBBLEMOVE
    val bbFreeOn by IslandSettings.bubbleFreeOn.collectAsState()
    val bbFreePos = bbMoveOn && bbFreeOn   // bubble apni jagah par: pill ke left me room ki zarurat nahi
    val hasRoom = bbFreePos || leftEdge >= needDp * dens
    val pvOn = pv != 0
    val cardShown = cardOpen || pv == 3   // BUBBLEMUSIC: pv==7 (music preview) mein bubble rehta hai
    val bEnabled by IslandSettings.bubbleEnabled.collectAsState()   // BUBBLETOGGLE
    val visible = bEnabled && BubbleStore.worthShowing(items) && hasRoom &&   // SINGLEHIDE
        mode !is IslandMode.Call && (allowed || pvOn) && !(hideLand && landscape && !pvOn) && !cardShown
    // BUBBLESHIFT: pill sirf Compact / Banner me khisakti hai (bade cards jaise the waise)
    val shiftWanted = bEnabled && !bbFreePos && BubbleStore.worthShowing(items) && (level == IslandLevel.Compact || level == IslandLevel.Banner) &&   // SINGLEHIDE
        mode !is IslandMode.Call && (allowed || pvOn) && !(hideLand && landscape && !pvOn) && !cardShown
    androidx.compose.runtime.SideEffect { BubbleStore.shiftOn.value = shiftWanted }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { BubbleStore.shiftOn.value = false } }

    // keep the last list so the exit animation still has something to draw
    var last by remember { mutableStateOf(items) }
    if (items.isNotEmpty()) last = items

    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), 0.3f) + fadeIn(tween(160)),   // BUBBLEPOP
        exit = scaleOut(tween(160), 0.3f) + fadeOut(tween(140)),
    ) {
        // BUBBLESTACK: ek hi circle mein saare icons side-by-side (overlap nahi, alag circle nahi)
        BubbleCircle(last)
    }
}

@Composable
internal fun BubbleCircle(items: List<BubbleItem>) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current   // DISMISSHAPTIC2
    val density = LocalDensity.current
    val bSize by IslandSettings.bubbleSize.collectAsState()
    val bAlpha by IslandSettings.bubbleAlpha.collectAsState()
    val bStyle by IslandSettings.bubbleStyle.collectAsState()   // STYLEKIT
    // OVERLAPKIT: ON = har notification ka apna gol, ek doosre ke upar (naya sabse aage)
    val ovSetting by IslandSettings.bubbleOverlap.collectAsState()
    val ov = ovSetting && items.size > 1
    val stepDp = bSize * OVERLAP_STEP
    val boxW = bSize + (if (ov) minOf(items.size, 3) - 1 else 0) * stepDp
    val look = rememberShellLook(bStyle)   // M3DESIGN: badge / cross ke rang M3 roles se
    val bSwipeOn by IslandSettings.bubbleSwipeOn.collectAsState()
    val bSwipeDist by IslandSettings.bubbleSwipeDist.collectAsState()
    val thresholdPx = with(density) { bSwipeDist.dp.toPx() }
    val maxNudge = with(density) { PAD_DP.dp.toPx() }
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val progress = (abs(drag.value) / thresholdPx).coerceIn(0f, 1f)
    val gone = remember { Animatable(0f) }   // SWIPEANIM: swipe poora hote hi bubble chhota hoke ghulta hai
    // BUBBLEPOP: naya message aate hi bubble spring se thoda pop karta hai; 3 se zyada ho to badge ek baar halka pulse
    val pop = remember { Animatable(1f) }
    val reduceMotion = com.islandify.app.core.rememberReduceMotion()   // MOTIONKIT (PATCH_10_11_12)
    val badgePulse = remember { Animatable(1f) }
    val newestKey = items.lastOrNull()?.key
    var seenKey by remember { mutableStateOf(newestKey) }
    var seenCount by remember { mutableStateOf(items.size) }
    LaunchedEffect(newestKey, items.size) {
        val cnt = items.size
        val fresh = newestKey != null && newestKey != seenKey
        val grew = cnt > seenCount
        seenKey = newestKey
        seenCount = cnt
        if ((fresh || grew) && !reduceMotion) {
            pop.snapTo(0.78f)
            launch { pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) }
            if (cnt > 3) {
                badgePulse.snapTo(1.3f)
                badgePulse.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
            }
        }
    }

    // BUBBLEBADGE: bahar ka Box = bubble ki jagah, andar circle, upar total count ka badge
    Box(Modifier.padding(horizontal = PAD_DP.dp).size(boxW.dp, bSize.dp)) {   // OVERLAPKIT
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = (drag.value * 0.12f).coerceIn(-maxNudge, maxNudge)
                val sc = (1f - 0.12f * progress) * (1f - 0.55f * gone.value) * pop.value   // SWIPEANIM + BUBBLEPOP
                scaleX = sc
                scaleY = sc
                alpha = 1f - gone.value
            }
            .then(if (ov) Modifier else Modifier   // OVERLAPKIT: overlap me ek bada shell nahi, har gol apna
            .clip(bubbleShape(bStyle))
            .shellStyle(   // STYLEKIT
                bubblePaint(bStyle),
                Color(items.lastOrNull()?.color ?: 0xFF8E8E93.toInt()),
                bAlpha,
                bubbleShape(bStyle),
            ))
            .pointerInput(Unit) { detectTapGestures(onTap = { BubbleStore.onTap() }) }
            .bubbleSemantics(items)   // A11YKIT (PATCH_10_11_12)
            .pointerInput(thresholdPx, bSwipeOn) {
                if (!bSwipeOn) return@pointerInput
                var sumDrag = 0f   // BUTTERDRAG: asli drag ka total (async snapTo se nahi bigadta)
                detectHorizontalDragGestures(
                    onDragStart = { sumDrag = 0f },
                    onDragEnd = {
                        scope.launch {
                            // far enough = dismiss, otherwise the bubble just stays
                            if (abs(sumDrag) >= thresholdPx) {
                                // SWIPEANIM: pehle bubble chhota hoke ghulta hai, phir hatta hai (achanak gayab nahi)
                                gone.animateTo(1f, tween(150, easing = androidx.compose.animation.core.FastOutLinearInEasing))
                                if (IslandSettings.haptics.value) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)   // DISMISSHAPTIC2
                                BubbleStore.clear()
                                delay(450)   // bubble hat gaya to ye line chalti hi nahi; na hata ho to wapas aa jaye
                                gone.animateTo(0f, tween(220))
                                drag.snapTo(0f)
                            } else drag.animateTo(0f, tween(180))
                        }
                    },
                    onDragCancel = { scope.launch { drag.animateTo(0f, tween(180)) } },
                    onHorizontalDrag = { change, dx ->
                        change.consume()
                        sumDrag += dx   // BUTTERDRAG
                        scope.launch { drag.snapTo(sumDrag) }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (ov) BubbleOverlapStack(items, bSize.dp, stepDp.dp, Modifier.alpha(1f - progress)) else Box(   // OVERLAPKIT
            Modifier.size(32.dp).graphicsLayer { scaleX = bSize / 32f; scaleY = bSize / 32f }.alpha(1f - progress),
            contentAlignment = Alignment.Center
        ) {
            if (items.size <= 1) {
                items.lastOrNull()?.let { BubbleIcon(it, 25.5.dp) }
            } else {
                // BUBBLESTACK: ek circle ke andar icons side-by-side (overlap nahi)
                // 2 icons: left-center aur right-center (horizontally split)
                // 3 icons: top-center + bottom-left + bottom-right (triangle)
                val top3 = items.takeLast(3).reversed()
                val iconSz = 13.dp   // chhota icon jo circle mein fit ho
                val gap = 1.dp       // icons ke beech gap
                if (top3.size == 2) {
                    // Side by side — center mein
                    val totalW = iconSz * 2 + gap
                    val startX = (32.dp - totalW) / 2f
                    val centerY = (32.dp - iconSz) / 2f
                    BubbleIcon(top3[0], iconSz, Modifier.align(Alignment.TopStart).offset(startX, centerY))
                    BubbleIcon(top3[1], iconSz, Modifier.align(Alignment.TopStart).offset(startX + iconSz + gap, centerY))
                } else {
                    // Triangle formation
                    val topX = (32.dp - iconSz) / 2f
                    val topY = 3.dp
                    val botY = 32.dp - iconSz - 3.dp
                    val leftX = 3.dp
                    val rightX = 32.dp - iconSz - 3.dp
                    BubbleIcon(top3[0], iconSz, Modifier.align(Alignment.TopStart).offset(topX, topY))
                    BubbleIcon(top3[1], iconSz, Modifier.align(Alignment.TopStart).offset(leftX, botY))
                    BubbleIcon(top3[2], iconSz, Modifier.align(Alignment.TopStart).offset(rightX, botY))
                }
            }
        }
        // small "dismiss" cross that fades in while swiping
        Canvas(Modifier.size(11.dp).alpha(progress)) {
            val w = 2.2.dp.toPx()
            drawLine(look.on, Offset(0f, 0f), Offset(size.width, size.height), w, StrokeCap.Round)
            drawLine(look.on, Offset(size.width, 0f), Offset(0f, size.height), w, StrokeCap.Round)
        }
    }
    // BUBBLEBADGE: 3 se zyada message -> total ginti bubble ke upar chhote laal badge me
    // SWIPEANIM: badge ginti ke saath narmi se aata / jaata hai (achanak nahi)
    var shownCount by remember { mutableStateOf(items.size) }
    if (items.size > 3) shownCount = items.size
    AnimatedVisibility(
        visible = items.size > 3,
        modifier = Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = 0.dp),
        enter = scaleIn(tween(200), 0.4f) + fadeIn(tween(160)),
        exit = scaleOut(tween(150), 0.4f) + fadeOut(tween(120)),
    ) {
        Box(
            Modifier
                .graphicsLayer { scaleX = badgePulse.value; scaleY = badgePulse.value }   // BUBBLEPOP
                .alpha(1f - progress)
                .defaultMinSize(minWidth = 15.dp, minHeight = 15.dp)
                .clip(CircleShape)
                .background(look.badge)
                .padding(horizontal = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (shownCount > 99) "99+" else shownCount.toString(),   // SWIPEANIM
                color = look.onBadge,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
    }
}

@Composable
internal fun BubbleIcon(item: BubbleItem, size: Dp, modifier: Modifier = Modifier) {
    val bmp = item.icon
    Box(
        modifier.size(size).clip(CircleShape).background(Color(item.color)),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            val img = remember(bmp) { bmp.asImageBitmap() }
            Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(item.letter, color = Color.White, fontSize = (size.value * 0.5f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

// OVERLAPKIT: 2-3 notification ek ke upar chadhe gol (naya sabse aage + halka ring), alag-alag circle nahi
@Composable
internal fun BubbleOverlapStack(items: List<BubbleItem>, d: Dp, step: Dp, modifier: Modifier = Modifier) {
    val shown = items.takeLast(3)   // purana -> naya (naya sabse upar / right, pill ke paas)
    Box(modifier.size(d + step * (shown.size - 1), d)) {
        shown.forEachIndexed { i, item ->
            val front = i == shown.lastIndex
            val ring = if (front) 2.dp else 1.dp
            val ringColor = if (front) {
                androidx.compose.ui.graphics.lerp(Color(item.color), Color.White, 0.6f).copy(alpha = 0.9f)
            } else {
                Color.White.copy(alpha = 0.22f)
            }
            Box(
                Modifier.offset(x = step * i).size(d).border(ring, ringColor, CircleShape).padding(ring),
                contentAlignment = Alignment.Center,
            ) { BubbleIcon(item, d - ring * 2) }
        }
    }
}
