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

// SPLITKIT  (BubbleOverlay: pill ke left ka bubble window.)

class BubbleOverlay(
    private val host: Context,
    private val windowType: Int,
    private val wm: WindowManager,
) {
    private val density get() = host.resources.displayMetrics.density
    private var view: android.view.View? = null   // BUBBLEMOVE: ab BubbleDragLayout (wrapper)
    private var params: WindowManager.LayoutParams? = null
    private var scope: CoroutineScope? = null
    private var pillPx = 0   // real width of the island pill in px (reported by the UI)
    private var ovCount = 1   // OVERLAPKIT: aakhri baar kitne items dikhe (exit animation me bhi chaudai wahi rahe)
    private var moving = false   // BUBBLEMOVE: user bubble ko ungli se khiska raha hai
    private val yMove by lazy { YMover(wm) }   // YANIM: window up/down smooth
    private val card = BubbleCardOverlay(host, windowType, wm)

    /** [owner] is the OverlayOwner of IslandOverlay (Lifecycle + SavedState + ViewModelStore). */
    fun show(owner: Any) {
        if (view != null) return
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
            gravity = Gravity.TOP or Gravity.LEFT
            x = 0
            y = (((if (notchOn()) 0f else IslandSettings.offsetY.value) + Y_ADJ_DP) * density).toInt()   // NOTCHMODE
            if (Build.VERSION.SDK_INT >= 30) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val cv = ComposeView(host).apply {
            setViewTreeLifecycleOwner(owner as LifecycleOwner)
            setViewTreeSavedStateRegistryOwner(owner as SavedStateRegistryOwner)
            setViewTreeViewModelStoreOwner(owner as ViewModelStoreOwner)
            setContent { IslandTheme { BubbleHost() } }   // M3DESIGN: bubble ko bhi wallpaper (Material You) ke rang
        }
        val v = BubbleDragLayout(host).apply {   // BUBBLEMOVE
            setViewTreeLifecycleOwner(owner as LifecycleOwner)
            setViewTreeSavedStateRegistryOwner(owner as SavedStateRegistryOwner)
            setViewTreeViewModelStoreOwner(owner as ViewModelStoreOwner)
            addView(
                cv,
                android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                )
            )
        }
        setupMove(v, lp)   // BUBBLEMOVE
        v.addOnLayoutChangeListener { _, l, _, r, _, ol, _, or, _ ->
            if (r - l != or - ol) v.post { applyPos() }
        }

        val added = runCatching { wm.addView(v, lp) }
        if (added.isFailure) { s.cancel(); return }

        scope = s
        params = lp
        view = v
        card.show(owner)

        s.launch {
            combine(IslandSettings.offsetX, IslandSettings.offsetY, IslandSettings.notchMode, IslandController.pillWidthPx) { _, _, _, w -> w }   // NOTCHMODE
                .collect { w ->
                    pillPx = w
                    applyPos()
                }
        }
        // OVERLAPKIT: items ki ginti ya overlap option badle to window ki chaudai ke hisaab se dobara rakho
        s.launch {
            combine(BubbleStore.shownItems, IslandSettings.bubbleOverlap) { l, o -> l.size + (if (o) 1000 else 0) }
                .collect { v2 ->
                    val n = v2 % 1000
                    if (n > 0) ovCount = n
                    applyPos()
                }
        }
        // BUBBLESHIFT: pill khisakne wali ho / na ho to bubble bhi dobara rakho
        s.launch { BubbleStore.shiftOn.collect { applyPos() } }
        // BUBBLEMOVE: user ne bubble khiska / switch badla / "Reset position" dabaya -> window dobara rakho
        s.launch {
            combine(
                IslandSettings.bubbleMove, IslandSettings.bubbleFreeOn,
                IslandSettings.bubbleFreeX, IslandSettings.bubbleFreeY
            ) { a, b, x, y -> "$a$b$x$y" }
                .collect { applyPos() }
        }
        // BUBBLEKIT: size / gap / up-down badle to window dobara rakho
        s.launch {
            combine(IslandSettings.bubbleSize, IslandSettings.bubbleGap, IslandSettings.bubbleY) { a, b, c -> a + b + c }
                .collect { applyPos() }
        }
        // MUSICPOS: music card ke saath bubble bhi chale
        s.launch {
            combine(
                IslandController.level, IslandController.mode, IslandSettings.musicX, IslandSettings.musicY, BubbleStore.preview
            ) { l, m, x, y, p -> "$l${m is IslandMode.Media}$x$y$p" }
                .collect { applyPos() }
        }
        // BANNERY: banner ke saath bubble bhi upar / niche (level ya bannerY badle)
        s.launch {
            combine(IslandController.level, IslandSettings.bannerY, BubbleStore.preview, IslandSettings.kindStyles) { l, b, p, k -> "$l$b$p$k" }
                .collect { applyPos() }
        }
    }

    private fun screenH(): Int =   // BUBBLEMOVE
        if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds.height()
        else host.resources.displayMetrics.heightPixels

    // BUBBLEMOVE: bubble window ka andaaza (hidden ho tab bhi sahi clamp ke liye)
    private fun bbWinW(): Int = ((BUBBLE_DP + 2 * PAD_DP + bubbleOverlapExtraDp(ovCount)) * density).toInt()
    private fun bbWinH(): Int = (BUBBLE_DP * density).toInt()

    // BUBBLEMOVE (GitHub issue #7): long-press karke bubble ko screen par kahin bhi khisko
    private fun setupMove(v: BubbleDragLayout, lp: WindowManager.LayoutParams) {
        var sx = 0
        var sy = 0
        v.canDrag = { IslandSettings.bubbleMove.value }
        v.onStart = {
            moving = true
            sx = lp.x
            sy = lp.y
            v.alpha = 0.85f
            if (IslandSettings.haptics.value) v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        }
        v.onMove = { dx, dy ->
            lp.x = (sx + dx).toInt().coerceIn(0, (screenW() - bbWinW()).coerceAtLeast(0))
            lp.y = (sy + dy).toInt().coerceIn(0, (screenH() - bbWinH()).coerceAtLeast(0))
            runCatching { wm.updateViewLayout(v, lp) }
        }
        v.onEnd = {
            moving = false
            v.alpha = 1f
            if (lp.x != sx || lp.y != sy) {   // sirf tab save jab sach me khiska (sirf long-press se bubble pin na ho)
                IslandSettings.bubbleFreeX.value = lp.x / density
                IslandSettings.bubbleFreeY.value = lp.y / density
                IslandSettings.bubbleFreeOn.value = true
                IslandSettings.save()
            } else {
                applyPos()
            }
        }
    }

    private fun screenW(): Int =
        if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds.width()
        else host.resources.displayMetrics.widthPixels

    private fun applyPos() {
        val v = view ?: return
        val p = params ?: return
        if (moving) return   // BUBBLEMOVE: ungli ke saath chal raha hai
        if (bubbleIsFree()) {   // BUBBLEMOVE: user ki chuni hui jagah (pill / banner / music offsets lagu nahi)
            val fx = (IslandSettings.bubbleFreeX.value * density).toInt().coerceIn(0, (screenW() - bbWinW()).coerceAtLeast(0))
            val fy = (IslandSettings.bubbleFreeY.value * density).toInt().coerceIn(0, (screenH() - bbWinH()).coerceAtLeast(0))
            if (p.x != fx || p.y != fy) {
                p.x = fx
                p.y = fy
                runCatching { wm.updateViewLayout(v, p) }
            }
            return
        }
        // fixed width: bubble hidden ho tab bhi sahi jagah nikalti hai (pehle v.width=0 par purani jagah atak jata tha)
        val winW = ((BUBBLE_DP + 2 * PAD_DP + bubbleOverlapExtraDp(ovCount)) * density).toInt()   // OVERLAPKIT
        val center = screenW() / 2 + ((IslandSettings.offsetX.value + musicXAdjDp()) * density).toInt() +   // MUSICPOS
            BubbleStore.shiftPx(screenW(), density)   // BUBBLESHIFT
        val half = maxOf(pillPx / 2, (CAM_HALF_DP * density).toInt())
        val nx = (center - half - (GAP_DP * density).toInt() - winW).coerceAtLeast(0)
        val ny = ((pillTopDp() /* NOTCHMODE */ + Y_ADJ_DP) * density).toInt().coerceAtLeast(0)   // BANNERY / MUSICPOS
        if (p.x != nx) {   // x turant, y smooth
            p.x = nx
            runCatching { wm.updateViewLayout(v, p) }
        }
        if (p.y != ny) yMove.move(v, p, ny)   // BBYANIM
    }

    fun hide() {
        val v = view ?: return
        card.hide()
        BubbleStore.shiftOn.value = false   // BUBBLESHIFT
        scope?.cancel()
        scope = null
        runCatching { wm.removeView(v) }
        view = null
        params = null
    }
}
