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

// SPLITBIG  BubbleStore ke live / system / status bubbles (extension fun, state BubbleStore.kt me)

/** Parked live activity (map / ride / order): same slot is updated in place, never moved. */
fun BubbleStore.putLive(key: String, m: IslandMode.Live, open: PendingIntent?, pkg: String?, icon: Bitmap?) {
    val k = LIVE_PREFIX + key
    val item = BubbleItem(
        key = k, pkg = pkg ?: m.app, app = m.app,
        title = m.title.ifBlank { m.app }, text = m.text,
        icon = icon, accent = null, open = open, reply = null, markRead = null, kind = 1,
    )
    _items.update { l ->
        val i = l.indexOfFirst { it.key == k }
        if (i >= 0) {
            item.count = l[i].count + 1
            l.toMutableList().also { it[i] = item }
        } else (l + item).takeLast(MAX)
    }
}

/** Charging, Bluetooth, low battery, toggles, timer: kept as a bubble (same kind = replaces, auto-expires). */
fun BubbleStore.addFromMode(m: IslandMode) {
    val green = 0xFF34C759.toInt()
    val blue = 0xFF2A7FFF.toInt()
    val red = 0xFFE53935.toInt()
    val orange = 0xFFFB8C00.toInt()
    val grey = 0xFF6B7280.toInt()
    val sys: BubbleItem? = when (m) {
        is IslandMode.Charging -> sysItem(
            "charging", Isl.charging,   // ISLANDI18N
            if (m.connected) m.detail.ifBlank { Isl.chargerConnected } else Isl.chargerDisconnected,
            "${m.percent}%", if (m.connected) green else grey,
        )
        is IslandMode.Device -> sysItem(
            "device:${m.name}", if (m.headphones) Isl.headphones else Isl.bluetooth,
            m.name, if (m.battery >= 0) Isl.connectedBatt(m.battery) else Isl.connected, blue,
        )
        is IslandMode.LowBattery -> sysItem(
            "lowbattery", Isl.battery, Isl.lowBattery, Isl.percentLeft(m.percent), red,
        )
        is IslandMode.Toggle -> {
            val label = when (m.kind) {
                ToggleKind.Silent -> Isl.lSilent
                ToggleKind.Vibrate -> Isl.lVibrate
                ToggleKind.Dnd -> Isl.lDnd
                ToggleKind.Flashlight -> Isl.lFlash
                ToggleKind.Wifi -> Isl.lWifi
                ToggleKind.Airplane -> Isl.lAirplane
                ToggleKind.Hotspot -> Isl.lHotspot   // EXTRAKIT
                ToggleKind.Vpn -> Isl.lVpn
                ToggleKind.Camera, ToggleKind.Mic, ToggleKind.ScreenRec -> ""   // privacy banners bubble nahi banate
            }
            if (label.isEmpty()) null else sysItem("toggle:${m.kind}", label, label, if (m.on) Isl.on else Isl.off, if (m.on) blue else grey)
        }
        is IslandMode.Notification ->
            if (m.app == "Timer") sysItem("timer", Isl.timer, m.title, m.text, orange) else null
        else -> null
    }
    if (sys != null) {
        add(sys)
        expiry.remove(sys.key)?.cancel()
        expiry[sys.key] = bg.launch {
            delay(15 * 60_000L)       // 15 min baad system bubble khud hat jaye
            expiry.remove(sys.key)
            remove(sys.key)
        }
    }
}

internal fun BubbleStore.sysItem(id: String, app: String, title: String, text: String, color: Int) = BubbleItem(
    key = "sys:$id", pkg = "sys.$id", app = app, title = title, text = text,
    icon = null, accent = color, open = null, reply = null, markRead = null, kind = 2,
)

fun BubbleStore.putStatus(id: String, app: String, title: String, text: String, color: Int, icon: Bitmap?, open: PendingIntent? = null, kind: Int = 2) {   // DLBUBBLE
    if (!IslandSettings.bubbleExtras.value) { removeStatus(id); return }
    val k = "sys:$id"
    val exists = _items.value.any { it.key == k }
    if (!exists && id in statusLive) statusOff.add(id)     // user ne bubble hata diya
    if (id in statusOff) return
    statusLive.add(id)
    val item = BubbleItem(
        key = k, pkg = "sys.$id", app = app, title = title, text = text,
        icon = icon, accent = color, open = open, reply = null, markRead = null, kind = kind,   // DLBUBBLE
    )
    _items.update { l ->
        val i = l.indexOfFirst { it.key == k }
        if (i >= 0) l.toMutableList().also { it[i] = item } else (l + item).takeLast(MAX)
    }
}

fun BubbleStore.removeStatus(id: String) {
    statusLive.remove(id)
    statusOff.remove(id)
    remove("sys:$id")
}

/** BUTTERICON2: sender ki photo baad me mili: bubble ka icon badlo, baaki sab waisa hi. */
fun BubbleStore.updateIcon(key: String, icon: Bitmap) {
    _items.update { l ->
        val i = l.indexOfFirst { it.key == key }
        if (i < 0) l else {
            val o = l[i]
            val n = BubbleItem(
                o.key, o.pkg, o.app, o.title, o.text, icon, o.accent, o.open, o.reply, o.markRead,
                o.time, o.markReadLabel, o.kind, o.actions, keep = o.keep, sig = o.sig
            )
            n.count = o.count
            l.toMutableList().also { it[i] = n }
        }
    }
}

