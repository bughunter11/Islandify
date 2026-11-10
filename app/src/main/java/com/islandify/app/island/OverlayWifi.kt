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

// SPLITLO  (Wi-Fi watcher: wifiEvent, startWifiWatch, stopWifiWatch.)

internal fun IslandOverlay.wifiEvent(on: Boolean) {
    // The first callbacks right after registering only report the current state, not a change
    if (android.os.SystemClock.uptimeMillis() < netQuietUntil) return
    if (!IslandSettings.isOn(Trigger.Network)) return
    IslandController.flash(IslandMode.Toggle(ToggleKind.Wifi, on), 2500)
}

internal fun IslandOverlay.startWifiWatch() {
    val cm = host.getSystemService(android.net.ConnectivityManager::class.java) ?: return
    netQuietUntil = android.os.SystemClock.uptimeMillis() + 2000
    val cb = object : android.net.ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) {   // POLISHKIT
            val wasEmpty = wifiNets.isEmpty()
            val quick = wifiOffPending      // "off" abhi dikhaya nahi gaya = AP badla, jaldi wapas juda
            netHandler.removeCallbacks(wifiOffTask)
            wifiOffPending = false
            wifiNets.add(network)
            if (wasEmpty && !quick) wifiEvent(true)
        }
        override fun onLost(network: android.net.Network) {
            wifiNets.remove(network)
            if (wifiNets.isEmpty()) {
                wifiOffPending = true
                netHandler.removeCallbacks(wifiOffTask)
                netHandler.postDelayed(wifiOffTask, 1500L)
            }
        }
    }
    val req = android.net.NetworkRequest.Builder()
        .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
        .build()
    runCatching { cm.registerNetworkCallback(req, cb, netHandler) }.onSuccess { wifiCallback = cb }
}

internal fun IslandOverlay.stopWifiWatch() {
    netHandler.removeCallbacks(wifiOffTask)   // POLISHKIT
    wifiNets.clear()
    wifiOffPending = false
    val cb = wifiCallback ?: return
    runCatching { host.getSystemService(android.net.ConnectivityManager::class.java).unregisterNetworkCallback(cb) }
    wifiCallback = null
}
