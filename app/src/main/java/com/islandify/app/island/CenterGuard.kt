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

// SPLITKIT  (CenterGuard: overlay window ko screen ke center me rakhne wala watchdog.)

/**
 * CENTERGUARD: WRAP_CONTENT + CENTER_HORIZONTAL window kabhi kabhi chhoti (purani) size ki jagah par atak jati hai
 * aur content sirf right me badhta hai (banner / card aadha screen se bahar). Size badalne ke thodi der baad
 * window ki asli jagah (screen par) check hoti hai; galat ho to window ko dobara layout karwa ke center me laate hain.
 */
// CENTERGUARD2: shared params ko kabhi bigaadta nahi (nudge sirf copy par), gravity khud theek karta hai.
internal class CenterGuard(
    private val v: android.view.View,
    private val wm: WindowManager,
    private val params: () -> WindowManager.LayoutParams?,
    private val screenW: () -> Int,
) {
    private val loc = IntArray(2)
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    // Window ki sahi gravity (jis se ye bani thi). Isse bahar jaana = bug.
    private val home: Int = params()?.gravity ?: (Gravity.TOP or Gravity.CENTER_HORIZONTAL)

    private var watching = false
    private var busy = false      // nudge / re-add chal raha hai: abhi window ko judge mat karo
    private var bad = 0
    private var stuck = 0
    private var lastReadd = 0L
    private var lastFix = 0L

    private fun wantX(p: WindowManager.LayoutParams): Int = (screenW() - v.width) / 2 + p.x

    private fun isOff(p: WindowManager.LayoutParams): Boolean {
        if (!v.isAttachedToWindow || v.width <= 0) return false
        if (v.resources.configuration.orientation != Configuration.ORIENTATION_PORTRAIT) return false
        v.getLocationOnScreen(loc)
        return abs(loc[0] - wantX(p)) > 3
    }

    /** Shared params hi sach hain: gravity seedhi karo aur wahi window par lagao. */
    private fun restore() {
        val p = params() ?: return
        p.gravity = home
        runCatching { wm.updateViewLayout(v, p) }
    }

    private val restoreTask = Runnable {
        busy = false
        restore()
    }

    /**
     * Window ko sach-much relayout karwane ke liye: pehle COPY par alag value lagao, thodi der baad
     * (alag frame me) asli params wapas. Asli params kabhi nahi chhede jaate, isliye beech me kuch fail
     * ho jaye to bhi gravity / x bigadte nahi.
     */
    private fun nudge(p: WindowManager.LayoutParams, viaLeft: Boolean) {
        val tmp = WindowManager.LayoutParams()
        tmp.copyFrom(p)
        if (viaLeft) {
            tmp.gravity = Gravity.TOP or Gravity.LEFT
            tmp.x = wantX(p)
        } else {
            tmp.x = p.x + 1
        }
        busy = true
        val ok = runCatching { wm.updateViewLayout(v, tmp) }.isSuccess
        main.removeCallbacks(restoreTask)
        main.postDelayed(restoreTask, if (ok) 90L else 0L)
    }

    /** Aakhri raasta: bahut door atki ho aur 6 s me pehle re-add na hua ho to window nikaal ke dobara lagao. */
    private fun readd(p: WindowManager.LayoutParams) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastReadd < 6000L) return
        v.getLocationOnScreen(loc)
        if (abs(loc[0] - wantX(p)) <= 40) return
        lastReadd = now
        busy = true
        p.gravity = home
        runCatching { wm.removeViewImmediate(v) }
        addBack(0)
    }

    private fun addBack(attempt: Int) {
        val p = params()
        if (!watching || p == null || v.isAttachedToWindow) {
            busy = false
            return
        }
        p.gravity = home
        val ok = runCatching { wm.addView(v, p) }.isSuccess
        if (ok || attempt >= 8) busy = false
        else main.postDelayed({ addBack(attempt + 1) }, 350L)
    }

    private fun step() {
        val p = params() ?: return
        if (busy || !v.isAttachedToWindow) return

        // Gravity bigdi ya window par koi aur LayoutParams object lag gaya (adhoori nudge) -> seedha wapas
        val gravityBad = p.gravity != home
        val swapped = v.layoutParams !== p
        if (gravityBad || swapped) {
            val now = android.os.SystemClock.uptimeMillis()
            if (gravityBad || now - lastFix > 3000L) {
                lastFix = now
                restore()
                bad = 1
                return
            }
        }

        if (isOff(p)) {
            bad++
            if (bad >= 2) {
                stuck++
                when (if (stuck > 4) 4 else stuck) {
                    1 -> restore()
                    2 -> nudge(p, false)
                    3 -> nudge(p, true)
                    else -> readd(p)
                }
                bad = 1
            }
        } else {
            bad = 0
            stuck = 0
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!watching) return
            runCatching { step() }   // koi bhi exception loop ko marne na de
            main.postDelayed(this, if (bad > 0 || busy) 250L else 1500L)
        }
    }

    fun poke() {
        watching = true
        main.removeCallbacks(tick)
        main.postDelayed(tick, 160L)
    }

    fun stop() {
        watching = false
        busy = false
        main.removeCallbacks(tick)
        main.removeCallbacks(restoreTask)
    }
}
