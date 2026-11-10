package com.islandify.app.island

import com.islandify.app.core.*

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.NotificationManager
import android.graphics.PixelFormat
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.BatteryManager
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

// SPLITLO  (Window show / hide (Compose view setup).)

fun IslandOverlay.show() {
    if (view != null) return

    val o = OverlayOwner().also { it.create() }
    val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val lp = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        windowType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        // Position is computed by us in applyPos() (not by Android's CENTER_HORIZONTAL),
        // so system transitions (app open, call screen, lock) can never leave it off-center.
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL   // window khud center me rehti hai (khisakna nahi padta)
        x = (IslandSettings.offsetX.value * density).toInt()  // center se user ka offset
        y = (pillTopDp() * density).toInt()   // NOTCHMODE
        // Let the window extend into the camera cutout area
        // The island sits at the top and the keyboard at the bottom, so never resize / pan for the IME
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        if (Build.VERSION.SDK_INT >= 30) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= 28) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    val v = ComposeView(host).apply {
        setViewTreeLifecycleOwner(o)
        setViewTreeSavedStateRegistryOwner(o)
        setViewTreeViewModelStoreOwner(o)
        setContent {
            IslandTheme {
                val mode by IslandController.mode.collectAsState()
                val level by IslandController.level.collectAsState()
                val scale by IslandSettings.scale.collectAsState()
                val widthScale by IslandSettings.widthScale.collectAsState()
                val heightScale by IslandSettings.heightScale.collectAsState()
                val corner by IslandSettings.corner.collectAsState()
                val speedUser by IslandSettings.animSpeed.collectAsState()
                val reduceMotion = com.islandify.app.core.rememberReduceMotion()   // MOTIONKIT (PATCH_10_11_12)
                val speed = if (reduceMotion) 0f else speedUser   // 0 = snap (IslandBody)
                val glow by IslandSettings.glow.collectAsState()
                val haptics by IslandSettings.haptics.collectAsState()
                val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current   // DISMISSHAPTIC
                val useAppAccent by IslandSettings.islandAppAccent.collectAsState()
                val replying by IslandController.replying.collectAsState()
                val queued by IslandController.queued.collectAsState()
                val secondary by IslandController.secondary.collectAsState()
                val idlePill by IslandSettings.idleInfo.collectAsState()   // time/battery toggle = idle pill on/off
                val showWhen by IslandSettings.showWhen.collectAsState()
                val isLocked by IslandVisibility.locked.collectAsState()
                val isScreenOn by IslandVisibility.screenOn.collectAsState()
                val launcherOnly by IslandSettings.launcherOnly.collectAsState()
                val hideInOwner by IslandSettings.hideInOwnerApp.collectAsState()
                val fg by IslandVisibility.foreground.collectAsState()
                val hideLand by IslandSettings.hideLandscape.collectAsState()
                val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE
                val home = remember { homePackage(host) }
                val ruleOk = !launcherOnly || fg == null || fg == home
                val ownerHidden = hideInOwner && fg != null && fg == IslandController.sourcePackage()
                val quietNow = rememberIslandSuppressed()   // QUIETKIT
                val allowed = !quietNow && when (showWhen) {
                    0 -> isScreenOn && !isLocked
                    1 -> isScreenOn
                    else -> true
                }
                // Nothing happening -> the island is not on screen at all.
                // It grows out of the top when an activity starts and shrinks away when it ends.
                val last = remember { LastMode() }
                if (mode !is IslandMode.Idle) last.value = mode
                // PREVIEWKIT: Customize page khula ho to wahi cheez island par dikhao
                val pv by BubbleStore.preview.collectAsState()
                val pvOn = pv != 0
                // PREVIEWMORE: 5 = Visuals (sample banner), 6 = Duration (banner baar baar, show time tak)
                var pvLoop by remember { mutableStateOf(false) }
                LaunchedEffect(pv) {
                    pvLoop = false
                    if (pv == 6) {
                        while (true) {
                            pvLoop = true
                            delay(IslandSettings.kindDurationMs(BANNER_DEMO) ?: 4500L)
                            pvLoop = false
                            delay(1800L)
                        }
                    }
                }
                val pvBanner = (pv == 2 || pv == 5 || (pv == 6 && pvLoop)) && mode !is IslandMode.Call
                val pvMusic = pv == 7 && mode !is IslandMode.Call   // MUSICKIT: Customize > Music card

                // PRE-WARM: draw the island once, invisible and tiny, right after the window is added.
                // The first real composition (icons, theme, springs) is what made the first call /
                // notification lag. After 2 frames everything is already cached.
                var warm by remember { mutableStateOf(false) }
                if (!warm) {
                    LaunchedEffect(Unit) {
                        withFrameNanos { }
                        withFrameNanos { }
                        warm = true
                    }
                    Box(Modifier.size(1.dp).alpha(0f)) {
                        DynamicIslandUi(
                            mode = IslandMode.Call("", true, 0L),
                            level = IslandLevel.Expanded,
                            onClick = {},
                        )
                    }
                }

                AnimatedVisibility(
                    // Idle pill stays on screen so there is always something to tap (time / date / battery)
                    visible = pvOn || ((!(hideLand && landscape) || mode is IslandMode.Call) && (allowed || mode is IslandMode.Call) &&
                        (mode is IslandMode.Call || (ruleOk && !ownerHidden)) &&
                        (mode !is IslandMode.Idle || idlePill)),
                    // Fast + snappy: no slow spring, appears almost instantly
                    enter = if (reduceMotion) androidx.compose.animation.EnterTransition.None
                        else scaleIn(tween(140), 0.6f, TransformOrigin(0.5f, 0f)) + fadeIn(tween(90)),
                    exit = if (reduceMotion) androidx.compose.animation.ExitTransition.None
                        else scaleOut(tween(160), 0.6f, TransformOrigin(0.5f, 0f)) + fadeOut(tween(140)),
                ) {
                DynamicIslandUi(
                    mode = if (pvBanner) BANNER_DEMO else if (pvMusic) (if (mode is IslandMode.Media) mode else MUSIC_DEMO)
                        else if (mode is IslandMode.Idle && (idlePill || pvOn)) mode else last.value,   // MUSICKIT
                    level = if (pvBanner) IslandLevel.Banner else if (pvMusic) IslandLevel.Expanded else level,   // MUSICKIT
                    scale = scale,
                    widthScale = widthScale,
                    heightScale = heightScale,
                    cornerFactor = corner,
                    animSpeed = speed,
                    glow = glow,
                    haptics = haptics,
                    onClick = { IslandController.toggle() },   // SPLITCTRL: extension fn, isliye lambda
                    onLongPress = { IslandController.large() },
                    onDismiss = {
                        // DISMISSHAPTIC: notification dismiss par haptic feedback
                        if (haptics && IslandController.isMessageShowing())
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        IslandController.dismiss()
                    },
                    onPrev = { IslandController.prev() },
                    onPlayPause = { IslandController.playPause() },
                    onNext = { IslandController.next() },
                    onSeek = { IslandController.seekTo(it) },
                    onShuffle = { IslandController.toggleShuffle() },   // MUSICLAYOUT
                    onRepeat = { IslandController.cycleRepeat() },       // MUSICLAYOUT
                    onVolumeUp = { IslandController.volume(true) },      // MUSICLAYOUT
                    onVolumeDown = { IslandController.volume(false) },   // MUSICLAYOUT
                    onAnswer = { IslandController.answerCall() },
                    onDecline = { IslandController.declineCall() },
                    onMarkRead = { IslandController.markRead() },
                    onReply = { IslandController.startReply() },
                    onSendReply = { IslandController.sendReply(it) },
                    onCancelReply = { IslandController.cancelReply() },
                    replying = replying,
                    onOpenMedia = { IslandController.openMedia() },
                    secondary = secondary,
                    queued = queued,
                    onSwipeSide = { IslandController.skip(it) },
                    onSwipe = { dir ->   // DISMISSHAPTIC
                        if (haptics && dir != SwipeDir.Down && IslandController.isMessageShowing())   // DISMISSHAPTIC: swipe up bhi
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        IslandController.swipe(dir)
                    },
                    reportWidth = true,   // WIDTHOWNER
                    appAccent = if (useAppAccent) MaterialTheme.colorScheme.primary else null,
                )
                }
            }
        }
    }

    // Tap outside the island while typing = cancel the reply (needs FLAG_WATCH_OUTSIDE_TOUCH, set below)
    v.setOnTouchListener { _, e ->
        if (e.action == MotionEvent.ACTION_OUTSIDE) {
            if (IslandController.replying.value) IslandController.cancelReply()
            else IslandController.outsideTouch()
        }
        false
    }

    // Whenever the window size changes (pill grows / shrinks), re-center it ourselves
    v.addOnLayoutChangeListener { _, l, _, r, _, ol, _, or, _ ->
        if (r - l != or - ol) {
            v.post { applyPos() }
            centerGuard?.poke()   // CENTERGUARD
        }
    }

    val added = runCatching { wm.addView(v, lp) }
    if (added.isFailure) {
        // e.g. overlay permission revoked. Leave everything clean so show() can be retried.
        s.cancel()
        o.destroy()
        return
    }

    owner = o
    scope = s
    params = lp
    view = v
    centerGuard = CenterGuard(v, wm, { params }, { screenW() })   // CENTERGUARD
    registerReceivers()
    bubble = BubbleOverlay(host, windowType, wm).also { it.show(o) }

    // Replying needs the keyboard: make the window focusable only while the user is typing
    s.launch {
        IslandController.replying.collect { typing ->
            val p = params ?: return@collect
            val f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            p.flags = if (typing) p.flags and f.inv() else p.flags or f
            view?.let { runCatching { wm.updateViewLayout(it, p) } }
        }
    }

    // Typing a reply OR an open card with "Tap outside to close": watch touches outside the window
    s.launch {
        combine(IslandController.level, IslandSettings.dismissOnOutside, IslandController.replying) { l, on, typing ->
            (on && l != IslandLevel.Compact) || typing
        }.collect { watch ->
            val p = params ?: return@collect
            val w = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            p.flags = if (watch) p.flags or w else p.flags and w.inv()
            view?.let { runCatching { wm.updateViewLayout(it, p) } }
        }
    }
    // Sliders in the app move the island live
    s.launch {
        combine(IslandSettings.offsetX, IslandSettings.offsetY, IslandSettings.notchMode) { x, y, n -> Triple(x, y, n) }   // NOTCHMODE
            .collect { applyPos() }
    }
    // BUBBLESHIFT: pill ki chaudai / bubble ki halat badle to khisakna dobara nikalo
    s.launch {
        combine(
            BubbleStore.shiftOn, IslandController.pillWidthPx,
            IslandSettings.bubbleSize, IslandSettings.bubbleGap
        ) { a, b, c, d -> "$a$b$c$d" }
            .collect { applyPos() }
    }
    // BANNERY: banner dikhne par (ya Banner preview) up/down offset lagao
    s.launch {
        combine(IslandController.level, IslandSettings.bannerY, BubbleStore.preview, IslandSettings.kindStyles) { l, b, p, k -> "$l$b$p$k" }
            .collect { applyPos() }
    }
    // MUSICPOS: music card ka left/right + up/down slider (ya level / mode / preview badle) to window dobara rakho
    s.launch {
        combine(
            IslandController.level, IslandController.mode, IslandSettings.musicX, IslandSettings.musicY, BubbleStore.preview
        ) { l, m, x, y, p -> "$l${m is IslandMode.Media}$x$y$p" }
            .collect { applyPos() }
    }
    // CENTERGUARD: level / mode badle (pill bada-chhota) to thodi der baad window ki jagah check
    s.launch {
        combine(IslandController.level, IslandController.mode) { a, b -> a to b }
            .collect { centerGuard?.poke() }
    }
    // WATCHGUARD: app badalne / lock-unlock / screen on ke baad bhi window ki jagah check
    s.launch {
        combine(IslandVisibility.foreground, IslandVisibility.locked, IslandVisibility.screenOn) { a, b, c -> "$a$b$c" }
            .collect { centerGuard?.poke() }
    }
}

fun IslandOverlay.hide() {
    val v = view ?: return
    centerGuard?.stop()   // CENTERGUARD
    centerGuard = null
    bubble?.hide()
    bubble = null
    unregisterReceivers()
    scope?.cancel()
    scope = null
    runCatching { wm.removeView(v) }
    view = null
    params = null
    owner?.destroy()
    owner = null
}
