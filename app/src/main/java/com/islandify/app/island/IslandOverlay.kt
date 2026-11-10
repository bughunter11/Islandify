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

// SPLITLO  (core: state + overrides + receivers; baaki functions alag files me)

/**
 * Owns the island window (a ComposeView) for one host service.
 *
 * [windowType] decides where the window sits:
 *  - TYPE_ACCESSIBILITY_OVERLAY: ABOVE the status bar, so taps reach the island everywhere.
 *    Used by [IslandA11yService].
 *  - TYPE_APPLICATION_OVERLAY: Android places this BELOW the status bar window, so any tap
 *    inside the status bar strip is swallowed by SystemUI and never reaches the island.
 *    Only used as a fallback by [IslandService].
 *
 * Also listens for charging / Bluetooth / headphone events while the island is shown.
 */
class IslandOverlay(
    internal val host: Context,
    internal val windowType: Int,
) {
    internal val wm = host.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    internal val density get() = host.resources.displayMetrics.density

    internal var owner: OverlayOwner? = null
    internal var scope: CoroutineScope? = null
    internal var view: ComposeView? = null
    internal var bubble: BubbleOverlay? = null
    internal var params: WindowManager.LayoutParams? = null
    internal var centerGuard: CenterGuard? = null   // CENTERGUARD
    internal var receiversOn = false

    val shown: Boolean get() = view != null

    /** Wi-Fi connect / disconnect and airplane mode. Needs only ACCESS_NETWORK_STATE. */
    internal var netQuietUntil = 0L
    internal val yMove by lazy { YMover(wm) }   // YANIM: window up/down smooth
    internal var extra: ExtraWatchers? = null   // EXTRAKIT
    internal val netHandler = Handler(Looper.getMainLooper())
    internal var wifiCallback: android.net.ConnectivityManager.NetworkCallback? = null

    // POLISHKIT: Wi-Fi flicker fix. AP badalne (roaming) par "disconnected" + "connected" ek saath na aaye:
    // "off" tab dikhao jab 1.5 s baad bhi koi Wi-Fi na ho, aur jaldi wapas judne par "connected" dobara mat dikhao.
    internal val wifiNets = HashSet<android.net.Network>()
    internal var wifiOffPending = false
    internal val wifiOffTask = Runnable {
        wifiOffPending = false
        if (wifiNets.isEmpty()) wifiEvent(false)
    }

    internal val airplaneReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (!IslandSettings.isOn(Trigger.Network)) return
            if (i.action != Intent.ACTION_AIRPLANE_MODE_CHANGED) return
            IslandController.flash(IslandMode.Toggle(ToggleKind.Airplane, i.getBooleanExtra("state", false)), 2500)
        }
    }

    internal val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            IslandVisibility.refresh(c)
            // UNLOCKFIX: phone unlock hote hi lock screen par chhupaye bubbles asli text ke saath dikhao
            if (i.action == Intent.ACTION_USER_PRESENT &&
                c.getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked != true
            ) BubbleStore.revealHidden()
            // WATCHGUARD: lock / unlock ke baad window ki jagah dobara check (pill right me atak jaati thi)
            applyPos()
            centerGuard?.poke()
            view?.postDelayed({ centerGuard?.poke() }, 700L)
            view?.postDelayed({ centerGuard?.poke() }, 1800L)
        }
    }

    internal val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (!IslandSettings.isOn(Trigger.Charging)) return
            val connected = when (i.action) {
                Intent.ACTION_POWER_CONNECTED -> true
                Intent.ACTION_POWER_DISCONNECTED -> false
                else -> return
            }
            val bm = c.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (!connected) {
                IslandController.flash(IslandMode.Charging(pct, false), 3500)
                return
            }
            // CHARGEINFO: plug lagte hi current abhi chadha nahi hota, to ~1.2 s baad watt / time padhte hain
            val app = c.applicationContext
            Handler(Looper.getMainLooper()).postDelayed({
                val plugNow = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    ?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
                if (plugNow != 0) {
                    val pct2 = runCatching {
                        (app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    }.getOrDefault(pct)
                    IslandController.flash(IslandMode.Charging(pct2, true, ChargeInfo.detail(app)), 3500)
                }
            }, 1200L)
        }
    }

    /**
     * Low battery: warns once at 20% and once at 10%, only while not charging.
     * If the phone is already low when the island starts, nothing pops up (that is not a change).
     */
    internal var batteryPrimed = false
    internal var lowWarned = 100   // lowest step already shown (20 or 10), 100 = none

    internal val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (level < 0 || scale <= 0) return
            val pct = level * 100 / scale
            val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            if (plugged || pct > 20) {
                lowWarned = 100
                batteryPrimed = true
                return
            }
            val step = if (pct <= 10) 10 else 20
            if (!batteryPrimed) {
                batteryPrimed = true
                lowWarned = step
                return
            }
            if (step < lowWarned) {
                lowWarned = step
                if (IslandSettings.isOn(Trigger.Battery)) {
                    IslandController.flash(IslandMode.LowBattery(pct), 5000)
                }
            }
        }
    }

    /** Silent / vibrate (ringer) and Do Not Disturb. Both are readable without any permission. */
    internal var lastRinger = -1
    internal var lastFilter = -1

    internal val soundReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (!IslandSettings.isOn(Trigger.Toggles)) return
            when (i.action) {
                AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                    val mode = c.getSystemService(AudioManager::class.java)?.ringerMode ?: return
                    if (mode == lastRinger) return
                    lastRinger = mode
                    val m = when (mode) {
                        AudioManager.RINGER_MODE_SILENT -> IslandMode.Toggle(ToggleKind.Silent, true)
                        AudioManager.RINGER_MODE_VIBRATE -> IslandMode.Toggle(ToggleKind.Vibrate, true)
                        else -> IslandMode.Toggle(ToggleKind.Silent, false)
                    }
                    IslandController.flash(m, 2500)
                }
                NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> {
                    val f = c.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter ?: return
                    if (f == lastFilter || f == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) return
                    lastFilter = f
                    val on = f != NotificationManager.INTERRUPTION_FILTER_ALL
                    IslandController.flash(IslandMode.Toggle(ToggleKind.Dnd, on), 2500)
                }
            }
        }
    }

    /** Flashlight. The first report per camera is the current state, not a change, so it is skipped. */
    internal val torchStates = HashMap<String, Boolean>()
    internal val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            val prev = torchStates.put(cameraId, enabled)
            IslandController.onTorchChanged(torchStates.values.any { it })
            if (prev == null || prev == enabled) return
            if (IslandSettings.isOn(Trigger.Toggles)) {
                if (!(enabled && IslandSettings.flashlightPill.value)) IslandController.flash(IslandMode.Toggle(ToggleKind.Flashlight, enabled), 2000)
            }
        }
    }

    // POLISHKIT: ek hi Bluetooth device 15 s me baar baar judta dikhe (watch / band) to pop-up ek hi baar
    internal val lastDeviceAt = HashMap<String, Long>()
    internal val deviceCooldownMs = 15_000L

    /** Bluetooth connect + wired headphones. Reading the name needs BLUETOOTH_CONNECT (Android 12+). */
    internal val deviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (!IslandSettings.isOn(Trigger.Devices)) return
            when (i.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val dev: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33)
                        i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    else @Suppress("DEPRECATION") i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val devId = runCatching { dev?.address }.getOrNull() ?: dev?.toString().orEmpty()   // POLISHKIT
                    val nowMs = android.os.SystemClock.uptimeMillis()
                    if (nowMs - (lastDeviceAt[devId] ?: -deviceCooldownMs) < deviceCooldownMs) return
                    lastDeviceAt[devId] = nowMs
                    val name = runCatching { dev?.name }.getOrNull() ?: Isl.btDevice   // ISLANDI18N
                    val headphones = runCatching {
                        dev?.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO
                    }.getOrDefault(false)
                    // BTBATT: earbuds ka % (phone / earbuds de to). Turant na mile to ~2 s tak rukte hain
                    val battNow = BtBattery.read(dev)
                    if (battNow >= 0 || !headphones) {
                        IslandController.flash(IslandMode.Device(name, headphones, battNow), 3500)
                    } else {
                        Handler(Looper.getMainLooper()).postDelayed({
                            IslandController.flash(IslandMode.Device(name, headphones, BtBattery.read(dev)), 3500)
                        }, 2000L)
                    }
                }
                BtBattery.ACTION -> BtBattery.onBroadcast(i)   // BTBATT
                Intent.ACTION_HEADSET_PLUG -> if (i.getIntExtra("state", 0) == 1) {
                    IslandController.flash(
                        IslandMode.Device(i.getStringExtra("name") ?: "Wired headphones", true), 3000
                    )
                }
            }
        }
    }
}
