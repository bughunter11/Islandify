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

// SPLITLO  (Window position: screenW, applyPos.)

internal fun IslandOverlay.screenW(): Int =
    if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds.width()
    else host.resources.displayMetrics.widthPixels

/** Center the window horizontally (+ user offset) and apply the vertical offset. */
internal fun IslandOverlay.applyPos() {
    val v = view ?: return
    val p = params ?: return
    // BUBBLESHIFT: user ka offset + (bubble ke saath balance ke liye) thoda right
    val nx = ((IslandSettings.offsetX.value + musicXAdjDp()) * density).toInt() + BubbleStore.shiftPx(screenW(), density)   // MUSICPOS
    val ny = (pillTopDp() /* NOTCHMODE */ * density).toInt().coerceAtLeast(0)   // BANNERY / MUSICPOS
    // POSGUARD: window ki gravity hamesha TOP | CENTER_HORIZONTAL. Kabhi bigad jaye to yahin seedhi.
    val home = Gravity.TOP or Gravity.CENTER_HORIZONTAL
    var dirty = false
    if (p.gravity != home) { p.gravity = home; dirty = true }
    if (v.layoutParams !== p) dirty = true
    if (p.x != nx) { p.x = nx; dirty = true }   // x turant, y smooth
    if (dirty) runCatching { wm.updateViewLayout(v, p) }
    if (p.y != ny) yMove.move(v, p, ny)   // BANNERYANIM
}
