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

// SPLITKIT  IslandBody ab 4 file me: IslandBody.kt (frame), IslandBodySize.kt, IslandBodyLook.kt, IslandBodyGesture.kt
// SPLITUI  (IslandBody: level / mode ke hisaab se Compact, Banner, Expanded chunta hai.)

@Composable
internal fun IslandBody(
    mode: IslandMode,
    level: IslandLevel,
    widthScale: Float,
    heightScale: Float,
    cornerFactor: Float,
    animSpeed: Float,
    glow: Boolean,
    haptics: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDismiss: () -> Unit,
    actions: IslandActions,
    onSwipeSide: (Int) -> Unit,
    appAccent: Color?,
    secondary: IslandMode.Media?,
    queued: Int,
    onSwipe: ((SwipeDir) -> Unit)?,
    reportWidth: Boolean,
) {
    val lvl = level
    val kindStyles by IslandSettings.kindStyles.collectAsState()
    // Each banner type (message, charging, unplugged, ...) has its own size
    val kind = kindOf(mode)
    val bs = kind?.let { kindStyles[it.key] } ?: KindStyle()
    val isPopup = kind?.popup == true
    // Music is only the second part while a timer / navigation is the main thing
    val sec = if (mode is IslandMode.Live || mode is IslandMode.Timer) secondary else null
    val haptic = LocalHapticFeedback.current
    val hapticOn by rememberUpdatedState(haptics)

    val dClick by rememberUpdatedState(onClick)
    val dLong by rememberUpdatedState(onLongPress)
    val dDismiss by rememberUpdatedState(onDismiss)
    val dSide by rememberUpdatedState(onSwipeSide)
    val dSwipe by rememberUpdatedState(onSwipe)
    val dMsg by rememberUpdatedState(mode is IslandMode.Notification && !actions.replying)   // SWIPEANIM
    val gs = remember { IslandGestureState() }   // SPLITKIT: swipe state IslandBodyGesture.kt me

    val fbScope = rememberCoroutineScope()

    // Tell the bubble how wide the pill really is, so it always keeps a small gap
    val pillToken = remember { Any() }
    DisposableEffect(reportWidth) { onDispose { if (reportWidth) IslandController.clearPillWidth(pillToken) } }

    // Light haptic on expand / collapse (not on the first composition)
    var firstLevel by remember { mutableStateOf(true) }
    LaunchedEffect(lvl) {
        if (firstLevel) firstLevel = false
        else if (hapticOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    // SPLITKIT: target width / height IslandBodySize.kt me
    val (targetW, targetH) = islandTargetSize(mode, lvl, widthScale, heightScale, bs, isPopup, sec)
    val bnCorner by IslandSettings.bannerCorner.collectAsState()
    val bnAlpha by IslandSettings.bannerAlpha.collectAsState()
    val bnAccent by IslandSettings.bannerAccent.collectAsState()
    val pillAlphaV by IslandSettings.pillAlpha.collectAsState()   // STYLEKIT2
    val pillAccentV by IslandSettings.pillAccent.collectAsState()   // STYLEKIT2
    // MUSICKIT: gaana chalte waqt pill pe click = bada music card. Iska apna design / opacity / glow / corner.
    val musicBig = mode is IslandMode.Media && (lvl == IslandLevel.Expanded || lvl == IslandLevel.Large)
    val musicStyleV by IslandSettings.musicStyle.collectAsState()
    val musicAlphaV by IslandSettings.musicAlpha.collectAsState()
    val musicAccentV by IslandSettings.musicAccent.collectAsState()
    val musicCornerV by IslandSettings.musicCorner.collectAsState()
    // MUSICAUTO: gaane ke rang (album art) se card / pill
    val musicAutoCard by IslandSettings.musicAuto.collectAsState()
    val musicAutoPill by IslandSettings.musicPillAuto.collectAsState()
    val musicPill = mode is IslandMode.Media && lvl == IslandLevel.Compact
    val songColor: Color? = (mode as? IslandMode.Media)?.let { mm ->
        mm.accent?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    }
    val autoPaint = songColor != null && ((musicBig && musicAutoCard) || (musicPill && musicAutoPill))
    val songAuto: Color? = if (autoPaint) songColor else null
    val glowMul = if (musicBig) musicAccentV else if (lvl == IslandLevel.Banner) bnAccent else pillAccentV   // STYLEKIT2 / MUSICKIT
    val pillStyleV by IslandSettings.pillStyle.collectAsState()   // STYLEKIT
    val bannerStyleV by IslandSettings.bannerStyle.collectAsState()   // STYLEKIT
    val activeStyle = if (lvl == IslandLevel.Banner) bannerStyleV else pillStyleV   // STYLEKIT
    val shellLook = rememberShellLook(if (autoPaint) 0 else if (musicBig) musicStyleV else activeStyle)   // MUSICAUTO   // M3DESIGN: 6..11 me wallpaper ke M3 rang (MUSICKIT)
    // Higher animSpeed = faster spring
    // MOTIONKIT (PATCH_10_11_12): animSpeed 0 = Reduce motion = seedha snap
    val spec: androidx.compose.animation.core.AnimationSpec<Dp> =
        if (animSpeed <= 0f) androidx.compose.animation.core.snap<Dp>()
        else spring<Dp>(
            dampingRatio = 0.8f,
            stiffness = (Spring.StiffnessMedium * 1.6f * animSpeed).coerceIn(300f, 6000f)
        )
    val width by animateDpAsState(targetW, spec, label = "w")
    val height by animateDpAsState(targetH, spec, label = "h")
    val cf = if (bs.corner >= 0f) bs.corner
        else if (lvl == IslandLevel.Banner && bnCorner >= 0.2f) bnCorner else cornerFactor
    val expCorner by IslandSettings.expandedCorner.collectAsState()
    val corner = if (musicBig && musicCornerV >= 8f) musicCornerV.dp   // MUSICKIT
        else if (expCorner > 0f && (lvl == IslandLevel.Expanded || lvl == IslandLevel.Large)) expCorner.dp
        else (height / 2 * cf).coerceIn(12.dp, 46.dp)
    // STYLEKIT: Graphite (5) chhote pill / banner me gol nahi, narm chaukor
    val styleSquare = activeStyle == 5 && (lvl == IslandLevel.Compact || lvl == IslandLevel.Banner)
    val notchShape by IslandSettings.notchMode.collectAsState()   // NOTCHMODE
    val baseRadius = if (styleSquare) (height * 0.36f).coerceIn(9.dp, 18.dp) else corner
    val shape = if (notchShape) RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = baseRadius, bottomEnd = baseRadius)
        else RoundedCornerShape(baseRadius)

    // SPLITKIT: glow / outline / pulse IslandBodyLook.kt me
    val look = rememberIslandLook(mode, lvl, glow, appAccent, isPopup)
    val glowColor = look.glowColor
    val pulseColor = look.pulseColor
    val outlineColor = look.outlineColor
    val outlineW = look.outlineW

    Box(
        modifier = Modifier
            .graphicsLayer {
                val p = gs.fb.value
                val sc = (1f - 0.12f * p) * (1f - 0.15f * gs.swOut)   // SWIPEANIM
                scaleX = sc
                scaleY = sc
                translationX = gs.swOff.x
                translationY = gs.swOff.y
                alpha = (1f - 0.55f * p) * (1f - gs.swOut)
            }
            .width(width)
            .height(height)
            .onSizeChanged { if (reportWidth) IslandController.reportPillWidth(pillToken, it.width) }
            .clip(shape)
            .then(   // MUSICKIT
                if (songAuto != null) Modifier.songPaint(songAuto, if (musicBig) musicAlphaV else pillAlphaV, shape, musicBig)   // MUSICAUTO
                else if (musicBig) Modifier.cardStyleBg(musicStyleV, pulseColor, musicAlphaV, musicAccentV * 0.45f, shape)
                else Modifier.shellStyle(activeStyle, pulseColor, if (lvl == IslandLevel.Banner) bnAlpha else pillAlphaV, shape)   // STYLEKIT2
            )
            .drawBehind {
                if (glowColor.alpha > 0f) {
                    drawRect(
                        Brush.radialGradient(
                            listOf(glowColor.copy(alpha = (glowColor.alpha * 0.32f * glowMul).coerceIn(0f, 1f)), Color.Transparent),
                            center = Offset(size.width * 0.16f, size.height * 0.5f),
                            radius = size.width * 0.75f
                        )
                    )
                }
            }
            .border(
                outlineW,
                outlineColor,
                shape
            )
            // One gesture handler for everything: tap, long-press and swipe.
            // (No separate clickable: two handlers fighting over the same touch is what made taps unreliable.)
            .islandGestures(
                gs = gs, fbScope = fbScope, haptic = haptic,
                hapticOn = { hapticOn }, dMsg = { dMsg },
                dClick = { dClick() }, dLong = { dLong() }, dDismiss = { dDismiss() },
                dSide = { dSide(it) }, dSwipe = { dSwipe },
            )
            // A11YKIT (PATCH_10_11_12): TalkBack ko tap / long-press / dismiss dikhao
            .islandSemantics(mode, tap = { dClick() }, hold = { dLong() }, dismiss = { dDismiss() }),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalShellLook provides shellLook) {   // M3DESIGN
        AnimatedContent(
            modifier = Modifier.fillMaxSize(),
            targetState = mode to lvl,
            // A timer ticking every second must not re-trigger the transition
            contentKey = { it.first::class to it.second },
            transitionSpec = {
                (fadeIn(tween(120)) + scaleIn(tween(140), initialScale = 0.9f)) togetherWith
                    fadeOut(tween(60))
            },
            contentAlignment = Alignment.Center,
            label = "content"
        ) { (m, l) ->
            if (l == IslandLevel.Compact) {
                Compact(m, queued, sec)
            } else if (l == IslandLevel.Banner) {
                Banner(m, queued)
            } else if (sec != null && (m is IslandMode.Live || m is IslandMode.Timer)) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) { Expanded(m, l, actions) }
                    MiniPlayer(sec, actions)
                }
            } else {
                Expanded(m, l, actions)
            }
        }
        }   // M3DESIGN: CompositionLocalProvider
    }
}

internal fun expandedHeight(mode: IslandMode): Dp = when (mode) {
    IslandMode.Idle -> 112.dp
    is IslandMode.Charging -> 132.dp
    is IslandMode.Timer -> 132.dp
    is IslandMode.Notification -> if (mode.canReply || mode.canMarkRead) 176.dp else 150.dp
    is IslandMode.Media -> 208.dp   // APPLEMUSIC: bade buttons ke liye thodi zyada jagah (SEEKIMG3: +8dp seekbar)
    is IslandMode.Call -> 120.dp
    is IslandMode.Device -> 108.dp
    is IslandMode.LowBattery -> 132.dp
    is IslandMode.Toggle -> 108.dp
    is IslandMode.Live -> 132.dp
}
