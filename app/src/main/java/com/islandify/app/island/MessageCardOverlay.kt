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

// SPLITKIT  (BubbleCardOverlay: message card ka window.)

class BubbleCardOverlay(
    private val host: Context,
    private val windowType: Int,
    private val wm: WindowManager,
) {
    private val density get() = host.resources.displayMetrics.density
    private var view: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var scope: CoroutineScope? = null
    private var centerGuard: CenterGuard? = null   // CENTERGUARD
    private val yMove by lazy { YMover(wm) }   // YANIM

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
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = (IslandSettings.offsetX.value * density).toInt()
            y = (((if (notchOn()) 0f else IslandSettings.offsetY.value) + IslandSettings.cardY.value) * density).toInt().coerceAtLeast(0)   // CARDY
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            if (Build.VERSION.SDK_INT >= 30) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val v = ComposeView(host).apply {
            setViewTreeLifecycleOwner(owner as LifecycleOwner)
            setViewTreeSavedStateRegistryOwner(owner as SavedStateRegistryOwner)
            setViewTreeViewModelStoreOwner(owner as ViewModelStoreOwner)
            setContent { IslandTheme { BubbleCardHost() } }
        }
        v.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_OUTSIDE) {
                if (BubbleStore.replying.value) BubbleStore.cancelReply()
                else BubbleStore.closeCard()
            }
            false
        }
        v.addOnLayoutChangeListener { _, l, _, r, _, ol, _, or, _ ->
            if (r - l != or - ol) {
                v.post { applyPos() }
                centerGuard?.poke()   // CENTERGUARD
            }
        }

        val added = runCatching { wm.addView(v, lp) }
        if (added.isFailure) { s.cancel(); return }

        scope = s
        params = lp
        view = v
        centerGuard = CenterGuard(v, wm, { params }, { screenW() })   // CENTERGUARD

        // keyboard needs a focusable window: only while typing a reply
        s.launch {
            BubbleStore.replying.collect { typing ->
                val p = params ?: return@collect
                val f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                p.flags = if (typing) p.flags and f.inv() else p.flags or f
                view?.let { runCatching { wm.updateViewLayout(it, p) } }
            }
        }
        // tap outside closes the card (setting) / cancels the reply
        s.launch {
            combine(BubbleStore.cardOpen, IslandSettings.dismissOnOutside, BubbleStore.replying) { open, on, typing ->
                (open && on) || typing
            }.collect { watch ->
                val p = params ?: return@collect
                val w = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                p.flags = if (watch) p.flags or w else p.flags and w.inv()
                view?.let { runCatching { wm.updateViewLayout(it, p) } }
            }
        }
        s.launch {
            combine(IslandSettings.offsetX, IslandSettings.offsetY, IslandSettings.cardY, IslandSettings.notchMode) { x, y, c, n -> Triple(x, y + c, n) }   // NOTCHMODE   // CARDY
                .collect { applyPos() }
        }
    }

    private fun screenW(): Int =
        if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds.width()
        else host.resources.displayMetrics.widthPixels

    private fun applyPos() {
        val v = view ?: return
        val p = params ?: return
        val nx = (IslandSettings.offsetX.value * density).toInt()
        val ny = (((if (notchOn()) 0f else IslandSettings.offsetY.value) + IslandSettings.cardY.value) * density).toInt().coerceAtLeast(0)   // CARDY
        // POSGUARD: gravity hamesha TOP | CENTER_HORIZONTAL
        val home = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        var dirty = false
        if (p.gravity != home) { p.gravity = home; dirty = true }
        if (v.layoutParams !== p) dirty = true
        if (p.x != nx) { p.x = nx; dirty = true }   // x turant, y smooth
        if (dirty) runCatching { wm.updateViewLayout(v, p) }
        if (p.y != ny) yMove.move(v, p, ny)   // CARDYANIM
    }

    fun hide() {
        val v = view ?: return
        centerGuard?.stop()   // CENTERGUARD
        centerGuard = null
        scope?.cancel()
        scope = null
        runCatching { wm.removeView(v) }
        view = null
        params = null
    }
}
