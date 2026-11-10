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

// SPLITLO  (Receivers register / unregister.)

internal fun IslandOverlay.registerReceivers() {
    if (receiversOn) return
    receiversOn = true
    ContextCompat.registerReceiver(
        host, powerReceiver,
        IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        },
        ContextCompat.RECEIVER_NOT_EXPORTED
    )
    ContextCompat.registerReceiver(
        host, deviceReceiver,
        IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BtBattery.ACTION)   // BTBATT
            addAction(Intent.ACTION_HEADSET_PLUG)
        },
        ContextCompat.RECEIVER_NOT_EXPORTED
    )

    // Wi-Fi + airplane mode
    startWifiWatch()
    extra = ExtraWatchers(host).also { it.start() }   // EXTRAKIT: hotspot / VPN / camera / mic / screen record
    ContextCompat.registerReceiver(
        host, airplaneReceiver, IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED),
        ContextCompat.RECEIVER_NOT_EXPORTED
    )

    // Screen on / off / unlock (for "Show island when")
    IslandVisibility.refresh(host)
    ContextCompat.registerReceiver(
        host, screenReceiver,
        IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        },
        ContextCompat.RECEIVER_NOT_EXPORTED
    )

    // Low battery
    batteryPrimed = false
    lowWarned = 100
    ContextCompat.registerReceiver(
        host, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ContextCompat.RECEIVER_NOT_EXPORTED
    )

    // Silent / DND: remember the current state first, so only real changes show up
    lastRinger = runCatching { host.getSystemService(AudioManager::class.java).ringerMode }.getOrDefault(-1)
    lastFilter = runCatching {
        host.getSystemService(NotificationManager::class.java).currentInterruptionFilter
    }.getOrDefault(-1)
    ContextCompat.registerReceiver(
        host, soundReceiver,
        IntentFilter().apply {
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        },
        ContextCompat.RECEIVER_NOT_EXPORTED
    )

    // Flashlight
    torchStates.clear()
    IslandController.onTorchChanged(false)
    runCatching {
        host.getSystemService(CameraManager::class.java)
            .registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
    }
}

internal fun IslandOverlay.unregisterReceivers() {
    if (!receiversOn) return
    receiversOn = false
    runCatching { host.unregisterReceiver(powerReceiver) }
    runCatching { host.unregisterReceiver(deviceReceiver) }
    runCatching { host.unregisterReceiver(batteryReceiver) }
    runCatching { host.unregisterReceiver(screenReceiver) }
    runCatching { host.unregisterReceiver(airplaneReceiver) }
    stopWifiWatch()
    runCatching { extra?.stop() }   // EXTRAKIT
    extra = null
    runCatching { host.unregisterReceiver(soundReceiver) }
    runCatching { host.getSystemService(CameraManager::class.java).unregisterTorchCallback(torchCallback) }
}
