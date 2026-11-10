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

// SPLITKIT  (BubbleCardColumn: card UI (swipe, text auto-scroll, buttons, bubbles row).)

@Composable
internal fun BubbleCardColumn(items: List<BubbleItem>, item: BubbleItem, maxW: Dp, replying: Boolean, showRow: Boolean) {
    val density = LocalDensity.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current   // DISMISSHAPTIC
    val cWidth by IslandSettings.cardWidth.collectAsState()
    val cCorner by IslandSettings.cardCorner.collectAsState()
    val cText by IslandSettings.cardTextScale.collectAsState()
    val cAlpha by IslandSettings.cardAlpha.collectAsState()
    val cAccent by IslandSettings.cardAccent.collectAsState()
    val cStyle by IslandSettings.cardStyle.collectAsState()   // STYLEKIT
    val look = rememberShellLook(cStyle)   // M3DESIGN: text / tile / button / patti ke rang
    val cLines by IslandSettings.cardMaxLines.collectAsState()
    val cScrollSpeed by IslandSettings.cardScrollSpeed.collectAsState()   // CARDSPEED
    val cBtns by IslandSettings.cardButtons.collectAsState()
    val cRow by IslandSettings.cardRow.collectAsState()
    val upAct = 0   // SWIPERULE: swipe up = hamesha bubble me chhupao (setting nahi padhte)
    val sideAct = 0   // SWIPERULE: swipe left / right = hamesha dismiss
    val upDist by IslandSettings.cardUpDist.collectAsState()
    val sideDist by IslandSettings.cardSideDist.collectAsState()
    val dismissedKeys by BubbleStore.dismissed.collectAsState()   // KEEPBUBBLE
    val thresholdPx = with(density) { upDist.dp.toPx() }        // swipe up
    val sideThresholdPx = with(density) { sideDist.dp.toPx() }  // swipe left / right
    val maxPull = with(density) { 16.dp.toPx() }
    val screenPx = LocalContext.current.resources.displayMetrics.widthPixels.toFloat()
    val dragX = remember { Animatable(0f) }
    val dragY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val progress = (-dragY.value / thresholdPx).coerceIn(0f, 1f)   // swipe up = negative
    val sideP = (abs(dragX.value) / maxOf(sideThresholdPx, 1f)).coerceIn(0f, 1f)   // SWIPEANIM
    val dim = maxOf(progress, if (sideAct != 2) sideP else 0f)                      // SWIPEANIM
    val appear = remember { Animatable(1f) }   // SWIPEANIM: naya message narmi se aata hai
    val reduceMotion = com.islandify.app.core.rememberReduceMotion()   // MOTIONKIT (PATCH_10_11_12)
    val upExit = remember { Animatable(0f) }   // SWIPEANIM: swipe-up poora hone par card upar chadh ke ghulta hai
    var firstItem by remember { mutableStateOf(true) }
    // KEEPCARD: message hatne ke baad agla message sahi jagah dikhe (swipe offset zero)
    LaunchedEffect(item.key) {
        dragX.snapTo(0f)
        dragY.snapTo(0f)
        upExit.snapTo(0f)
        if (firstItem) firstItem = false
        else {
            appear.snapTo(0f)
            if (reduceMotion) appear.snapTo(1f)
            else appear.animateTo(1f, tween(240, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        }
    }
    val fade = (1f - abs(dragX.value) / (sideThresholdPx * 2.5f)).coerceIn(0f, 1f)
    val accent = Color(item.color)
    // MSGCARDSIZE: Layout > Pop-ups & activities > Notification / message ka Card width / height sirf is message card par
    val msgStyles by IslandSettings.kindStyles.collectAsState()
    val msgK = msgStyles[IslandKind.Notification.key] ?: KindStyle()
    val cardW = (maxW * cWidth * msgK.cardW)
        .let { if (it < (CARD_W_DP * msgK.cardW).dp) it else (CARD_W_DP * msgK.cardW).dp }
        .let { if (it < maxW) it else maxW }

    androidx.compose.runtime.CompositionLocalProvider(LocalShellLook provides look) {   // M3DESIGN
    Column(
        Modifier
            .width(cardW),   // CARDONLY: swipe sirf card ko khisakata hai, niche ki bubbles row jagah par rehti hai
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = dragX.value   // CARDONLY
                    alpha = fade * appear.value * (1f - upExit.value)   // SWIPEANIM
                    translationY = (dragY.value * 0.35f).coerceIn(-maxPull, 0f) -
                        upExit.value * 40.dp.toPx() + (1f - appear.value) * 10.dp.toPx()
                    val sc = (1f - 0.05f * progress) * (0.94f + 0.06f * appear.value) * (1f - 0.12f * upExit.value)
                    scaleX = sc
                    scaleY = sc
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
                // CARDNOANIM: card ki unchai animate nahi hoti, message jitna bada hai card pehle frame se utna dikhta hai
                .clip(RoundedCornerShape(cCorner.dp))
                .cardStyleBg(cStyle, accent, cAlpha, cAccent, RoundedCornerShape(cCorner.dp))   // STYLEKIT
                // tap on the card = open that chat
                .pointerInput(Unit) { detectTapGestures(onTap = { BubbleStore.openSelected() }) }
                .cardSemantics(item)   // A11YKIT (PATCH_10_11_12)
                // swipe up / left / right: action user ne Customize > Message card me chuna hai
                .pointerInput(thresholdPx, sideThresholdPx, upAct, sideAct) {
                    var sumX = 0f   // SWIPEFIX: asli drag ka total (async snapTo se nahi bigadta)
                    var sumY = 0f
                    detectDragGestures(
                        onDragStart = { sumX = 0f; sumY = 0f },
                        onDragEnd = {
                            scope.launch {
                                val x = sumX
                                val y = sumY
                                when {
                                    abs(x) >= sideThresholdPx && abs(x) > abs(y) && sideAct != 2 -> {
                                        // SWIPEANIM: card dheere shuru hoke tezi se bahar jaata hai, tab message hatta hai
                                        dragX.animateTo(
                                            if (x < 0) -screenPx else screenPx,
                                            tween(190, easing = androidx.compose.animation.core.FastOutLinearInEasing)
                                        )
                                        if (IslandSettings.haptics.value) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)   // DISMISSHAPTIC
                                        if (sideAct == 1) BubbleStore.archiveSelected() else BubbleStore.dismissSelected()
                                        delay(350)
                                        if (dragX.value != 0f) dragX.animateTo(0f, tween(200))   // message na hata ho to card wapas aaye
                                    }
                                    -y >= thresholdPx && abs(y) >= abs(x) && upAct != 2 -> {
                                        // SWIPEANIM: card upar chadh ke ghulta hai (hide / dismiss), achanak gayab nahi
                                        launch { dragX.animateTo(0f, tween(120)) }
                                        upExit.animateTo(1f, tween(170, easing = androidx.compose.animation.core.FastOutLinearInEasing))
                                        if (upAct == 1) {   // DISMISSHAPTIC
                                            if (IslandSettings.haptics.value) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                            BubbleStore.dismissSelected()
                                        } else BubbleStore.closeCard()
                                        delay(350)
                                        if (upExit.value > 0f) {
                                            launch { upExit.animateTo(0f, tween(200)) }
                                            dragY.animateTo(0f, tween(200))
                                        }
                                    }
                                    else -> {
                                        launch { dragX.animateTo(0f, tween(180)) }
                                        dragY.animateTo(0f, tween(180))
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                launch { dragX.animateTo(0f, tween(180)) }
                                dragY.animateTo(0f, tween(180))
                            }
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            sumX += amount.x   // SWIPEFIX
                            sumY = (sumY + amount.y).coerceAtMost(0f)
                            val tx = sumX
                            val ty = sumY
                            scope.launch {
                                dragX.snapTo(tx)
                                dragY.snapTo(ty)
                            }
                        },
                    )
                }
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 24.dp, end = 24.dp,
                        top = (16f + (msgK.cardH - 1f) * 40f).coerceAtLeast(4f).dp,       // MSGCARDSIZE
                        bottom = (14f + (msgK.cardH - 1f) * 40f).coerceAtLeast(4f).dp,
                    )
                    .alpha(1f - 0.6f * dim)   // SWIPEANIM: side swipe me bhi content dheere dhundhla
            ) {
                if (look.dynamic) {
                    // M3DESIGN: tonal tile me app icon + chhota app naam (overline) + title
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(look.tile),
                            contentAlignment = Alignment.Center,
                        ) { BubbleTile(item, 32.dp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            if (item.title.isNotBlank() && item.app.isNotBlank()) {
                                Text(
                                    item.app,
                                    color = look.muted,
                                    fontSize = (12f * cText).sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.4.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                item.title.ifBlank { item.app },
                                color = look.on,
                                fontSize = (cardTitleSp(cStyle) * cText).sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                } else Row(verticalAlignment = Alignment.CenterVertically) {
                    if (cStyle != 3) {   // STYLEKIT: Minimal me icon nahi
                        BubbleTile(item, if (cStyle == 2 || cStyle == 6) 34.dp else 26.dp)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        item.title.ifBlank { item.app },
                        modifier = Modifier.weight(1f),
                        color = when (cStyle) {
                            4 -> androidx.compose.ui.graphics.lerp(accent, Color.White, 0.4f)
                            10 -> androidx.compose.ui.graphics.lerp(androidx.compose.material3.MaterialTheme.colorScheme.primary, Color.White, 0.55f)   // M3 DYNAMIC
                            else -> Color.White
                        },
                        fontSize = (cardTitleSp(cStyle) * cText).sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.text.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    // CARDSCROLL: text ka area fixed max height (Max lines setting, screen ke ~30% se zyada nahi).
                    // CARDAUTOSCROLL: bada message = card wahi rehta hai, text khud dheere-dheere scroll hota hai (haath se nahi).
                    val textScroll = rememberScrollState()
                    val scrollFrac = remember { mutableStateOf(0f) }   // SMOOTHTEXT
                    val screenHPx = LocalContext.current.resources.displayMetrics.heightPixels
                    val capDp = screenHPx * 0.40f / density.density   // CARDGROW: 30% -> 40%, bada message = card zyada badhta hai
                    val oneLineDp = 21f * cText * density.fontScale
                    val textMaxH = minOf(oneLineDp * cLines.coerceAtLeast(1) + 3f, capDp).dp
                    val readPxPerSec = 16f * density.density * cScrollSpeed.coerceIn(0.3f, 3f)   // CARDAUTOSCROLL + CARDSPEED: padhne ki raftaar, 1 line / 1.3 sec at 1x, slider se badalti hai
                    LaunchedEffect(item.key, item.text, textMaxH, readPxPerSec) {   // SMOOTHTEXT: frame-by-frame, sub-pixel
                        textScroll.scrollTo(0)
                        scrollFrac.value = 0f
                        delay(1500)   // shuru ki lines padhne do
                        val mv = textScroll.maxValue
                        if (mv > 0 && mv != Int.MAX_VALUE) {
                            // scroll chalte waqt card auto-collapse na ho
                            val pulse = launch { while (true) { BubbleStore.bumpRead(); delay(1000) } }
                            try {
                                var pos = 0f
                                var last = androidx.compose.runtime.withFrameNanos { it }
                                while (pos < mv) {
                                    val nowN = androidx.compose.runtime.withFrameNanos { it }
                                    val dt = ((nowN - last) / 1_000_000_000f).coerceIn(0f, 0.05f)   // lamba frame gap = jhatka nahi
                                    last = nowN
                                    pos = (pos + readPxPerSec * dt).coerceAtMost(mv.toFloat())
                                    val ip = pos.toInt()
                                    textScroll.scrollTo(ip)          // poora pixel ScrollState se
                                    scrollFrac.value = pos - ip      // bacha hua sub-pixel graphicsLayer se
                                }
                            } finally {
                                pulse.cancel()
                                scrollFrac.value = 0f
                            }
                            BubbleStore.bumpRead()   // end tak pahunch ke aakhri lines padhne ka waqt
                        }
                    }
                    Text(
                        item.text,
                        color = look.on,   // M3DESIGN
                        fontSize = (16f * cText).sp,
                        lineHeight = (21f * cText).sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = textMaxH)
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; translationY = -scrollFrac.value }   // SMOOTHTEXT
                            .drawWithContent {
                                drawContent()
                                if (textScroll.canScrollForward) {   // neeche aur text hai: halka fade
                                    drawRect(
                                        Brush.verticalGradient(
                                            0f to Color.Black,
                                            1f to Color.Transparent,
                                            startY = size.height - 28.dp.toPx(),
                                            endY = size.height,
                                        ),
                                        blendMode = BlendMode.DstIn,
                                    )
                                }
                                // CARDPROGRESS: right edge par patli line. Chamakdar hissa = abhi dikh raha text, neeche ka khali = kitna bacha
                                val mvP = textScroll.maxValue
                                if (mvP > 0 && mvP != Int.MAX_VALUE) {
                                    val lw = 2.dp.toPx()
                                    val fracP = (textScroll.value.toFloat() / mvP).coerceIn(0f, 1f)
                                    val thumbH = (size.height * size.height / (size.height + mvP)).coerceIn(minOf(12.dp.toPx(), size.height), size.height)
                                    val xP = size.width - lw
                                    drawRoundRect(
                                        look.on.copy(alpha = 0.16f),
                                        androidx.compose.ui.geometry.Offset(xP, 0f),
                                        androidx.compose.ui.geometry.Size(lw, size.height),
                                        androidx.compose.ui.geometry.CornerRadius(lw / 2f),
                                    )
                                    drawRoundRect(
                                        look.on.copy(alpha = 0.7f),
                                        androidx.compose.ui.geometry.Offset(xP, (size.height - thumbH) * fracP),
                                        androidx.compose.ui.geometry.Size(lw, thumbH),
                                        androidx.compose.ui.geometry.CornerRadius(lw / 2f),
                                    )
                                }
                            }
                            .verticalScroll(textScroll, enabled = false),   // haath se scroll band: swipe / hide pehle jaisa
                    )
                }
                // CARDACTIONS: buttons = notification ke asli actions (Reply / Mark as read / No thanks / Turn on ...)
                val acts = item.actions
                val canReply = item.reply != null
                val canOpen = item.kind == 1
                val noActions = item.kind == 0 && acts.isEmpty()   // koi action nahi: sirf "Dismiss"
                if (cBtns && (acts.isNotEmpty() || canOpen || noActions)) {
                    Spacer(Modifier.height(14.dp))
                    if (replying && canReply) {
                        ReplyBar(onSend = { BubbleStore.sendReply(it) }, onCancel = { BubbleStore.cancelReply() })
                    } else if (acts.isNotEmpty()) {
                        CardActionRow(acts) { BubbleStore.runAction(it) }
                    } else if (canOpen) {
                        Row(Modifier.fillMaxWidth()) { CardButton(Isl.openBtn, Modifier.weight(1f))   /* ISLANDI18N */ { BubbleStore.openSelected() } }
                    } else {
                        Row(Modifier.fillMaxWidth()) { CardButton(Isl.dismissBtn, Modifier.weight(1f))   /* ISLANDI18N */ { if (IslandSettings.haptics.value) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); BubbleStore.dismissButton() } }   // DISMISSHAPTIC2
                    }
                }
            }
            // small "collapse" icon: gets clearer while swiping up
            Canvas(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 3.dp)
                    .size(24.dp)
                    .alpha(if (upAct == 1 || upAct == 2) 0f else progress)   // SWIPEANIM: sirf "hide" me chevron
            ) {
                drawCircle(Color.White.copy(alpha = 0.18f))
                val w = 2.2.dp.toPx()
                val cx = size.width / 2f
                val cy = size.height / 2f
                val a = size.width * 0.18f
                drawLine(Color.White, Offset(cx - a * 1.6f, cy + a * 0.8f), Offset(cx, cy - a * 0.8f), w, StrokeCap.Round)
                drawLine(Color.White, Offset(cx, cy - a * 0.8f), Offset(cx + a * 1.6f, cy + a * 0.8f), w, StrokeCap.Round)
            }
            // SWIPEANIM: swipe karte waqt saaf likha aata hai ki chhodne par kya hoga (Dismiss / Hide / Archive)
            val upP = if (upAct != 2) progress else 0f
            val hintP = maxOf(upP, if (sideAct != 2) sideP else 0f)
            if (hintP > 0.08f) {
                val sideWins = sideAct != 2 && sideP >= upP
                // REDDISMISS: hint "Dismiss" ho (side swipe = dismiss, ya up swipe = dismiss) to pill laal
                val hintRed = if (sideWins) sideAct != 1 else upAct == 1
                val hintText = if (sideWins) {
                    if (sideAct == 1) androidx.compose.ui.res.stringResource(com.islandify.app.R.string.swipe_hint_archive)
                    else androidx.compose.ui.res.stringResource(com.islandify.app.R.string.swipe_hint_dismiss)
                } else {
                    if (upAct == 1) androidx.compose.ui.res.stringResource(com.islandify.app.R.string.swipe_hint_dismiss)
                    else androidx.compose.ui.res.stringResource(com.islandify.app.R.string.swipe_hint_hide)
                }
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            alpha = hintP
                            val hs = 0.85f + 0.15f * hintP
                            scaleX = hs
                            scaleY = hs
                        }
                        .clip(CircleShape)
                        .background(if (hintRed) look.badge else look.hint)   // REDDISMISS + M3DESIGN
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(hintText, color = if (hintRed) look.onBadge else look.on, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }

        // bubbles row (newest first): only from the 2nd message on. Tap = show that message in the card
        AnimatedVisibility(   // SWIPEANIM: row narmi se aati / jaati hai
            visible = showRow && cRow,
            enter = fadeIn(tween(200)) + expandVertically(tween(220)),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(180)),
        ) {
            Row(
                Modifier
                    .padding(top = 8.dp)
                    .alpha(1f - 0.6f * progress)
                    .clip(CircleShape)
                    .background(look.rail)   // M3DESIGN
                    .horizontalScroll(rememberScrollState())
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.asReversed().forEach { b ->
                    val sel = b.key == item.key
                    Box(
                        Modifier
                            .size(38.dp)
                            .alpha(if (b.key in dismissedKeys) 0.45f else 1f)   // KEEPBUBBLE
                            .clip(CircleShape)
                            .clickable { BubbleStore.select(b.key) }
                            .border(1.5.dp, if (sel) look.ring else Color.Transparent, CircleShape)
                            .padding(3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BubbleIcon(b, 30.dp)
                    }
                }
            }
        }
    }
    }   // M3DESIGN: CompositionLocalProvider
}
