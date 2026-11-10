package com.islandify.app.island

// EXTRAKIT-FILE
// (1) YMover = overlay window ko up/down smooth khisakta hai (jhatka nahi)
// (2) ExtraWatchers = hotspot / VPN / camera / mic / screen-record banners

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.islandify.app.core.IslandSettings
import com.islandify.app.core.Trigger

internal class YMover(private val wm: WindowManager) {
    private var anim: ValueAnimator? = null
    private var target = Int.MIN_VALUE

    fun move(v: View, p: WindowManager.LayoutParams, ny: Int) {
        if (anim?.isRunning == true && target == ny) return
        anim?.cancel()
        anim = null
        if (!v.isAttachedToWindow || kotlin.math.abs(p.y - ny) < 3) {
            p.y = ny
            runCatching { wm.updateViewLayout(v, p) }
            return
        }
        target = ny
        val a = ValueAnimator.ofInt(p.y, ny)
        a.duration = 180L
        a.interpolator = DecelerateInterpolator()
        a.addUpdateListener {
            if (!v.isAttachedToWindow) {
                it.cancel()
                return@addUpdateListener
            }
            p.y = it.animatedValue as Int
            runCatching { wm.updateViewLayout(v, p) }
        }
        a.start()
        anim = a
    }
}

internal class ExtraWatchers(private val ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var quietUntil = 0L

    private var vpnCb: ConnectivityManager.NetworkCallback? = null
    private val vpnNets = HashSet<Network>()
    private var apReceiver: BroadcastReceiver? = null
    private var camCb: CameraManager.AvailabilityCallback? = null
    private val camBusy = HashSet<String>()
    private var camShown = false
    private var micCb: AudioManager.AudioRecordingCallback? = null
    private var micShown = false
    private var srCb: java.util.function.Consumer<Int>? = null
    private var srWm: WindowManager? = null

    /** privacy = camera / mic / screen record (Toggles trigger + privacy flag), baaki Network trigger. */
    private fun fire(kind: ToggleKind, on: Boolean, privacy: Boolean) {
        main.post {
            if (SystemClock.uptimeMillis() >= quietUntil) {
                val allowed = if (privacy)
                    IslandSettings.isOn(Trigger.Toggles) && IslandSettings.privacyBanners.value
                else
                    IslandSettings.isOn(Trigger.Network)
                if (allowed) IslandController.flash(IslandMode.Toggle(kind, on), if (privacy) 3000L else 2500L)
            }
        }
    }

    fun start() {
        // register ke turant baad ke callback sirf abhi ki halat batate hain, badlaav nahi
        quietUntil = SystemClock.uptimeMillis() + 2500L
        startVpn()
        startHotspot()
        startCamera()
        startMic()
        if (Build.VERSION.SDK_INT >= 35) addScreenRec()
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        vpnCb?.let { cb ->
            runCatching { ctx.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) }
        }
        vpnCb = null
        apReceiver?.let { r -> runCatching { ctx.unregisterReceiver(r) } }
        apReceiver = null
        camCb?.let { cb ->
            runCatching { ctx.getSystemService(CameraManager::class.java)?.unregisterAvailabilityCallback(cb) }
        }
        camCb = null
        micCb?.let { cb ->
            runCatching { ctx.getSystemService(AudioManager::class.java)?.unregisterAudioRecordingCallback(cb) }
        }
        micCb = null
        if (Build.VERSION.SDK_INT >= 35) removeScreenRec()
        vpnNets.clear()
        camBusy.clear()
        camShown = false
        micShown = false
    }

    /* ---------- VPN ---------- */
    private fun startVpn() {
        runCatching {
            val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return@runCatching
            val req = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val first = vpnNets.isEmpty()
                    vpnNets.add(network)
                    if (first) fire(ToggleKind.Vpn, true, false)
                }

                override fun onLost(network: Network) {
                    vpnNets.remove(network)
                    if (vpnNets.isEmpty()) fire(ToggleKind.Vpn, false, false)
                }
            }
            cm.registerNetworkCallback(req, cb, main)
            vpnCb = cb
        }
    }

    /* ---------- Hotspot (kai phones par ye broadcast nahi aata) ---------- */
    private fun startHotspot() {
        runCatching {
            val r = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    val s = i.getIntExtra("wifi_state", -1)
                    if (s == 13) fire(ToggleKind.Hotspot, true, false)      // AP ENABLED
                    else if (s == 11) fire(ToggleKind.Hotspot, false, false)   // AP DISABLED
                }
            }
            ContextCompat.registerReceiver(
                ctx, r, IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED"),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            apReceiver = r
        }
    }

    /* ---------- Camera ---------- */
    private val camCheck = Runnable {
        if (camBusy.isNotEmpty() && !camShown && !IslandController.torchIsOn) {
            camShown = true
            fire(ToggleKind.Camera, true, true)
        }
    }

    private fun startCamera() {
        runCatching {
            val cm = ctx.getSystemService(CameraManager::class.java) ?: return@runCatching
            val cb = object : CameraManager.AvailabilityCallback() {
                override fun onCameraUnavailable(cameraId: String) {
                    camBusy.add(cameraId)
                    main.removeCallbacks(camCheck)
                    main.postDelayed(camCheck, 700L)   // torch ke liye bhi camera "busy" ho sakta hai: thoda ruk ke dekho
                }

                override fun onCameraAvailable(cameraId: String) {
                    camBusy.remove(cameraId)
                    if (camBusy.isEmpty()) {
                        main.removeCallbacks(camCheck)
                        camShown = false
                    }
                }
            }
            cm.registerAvailabilityCallback(cb, main)
            camCb = cb
        }
    }

    /* ---------- Microphone ---------- */
    private fun startMic() {
        runCatching {
            val am = ctx.getSystemService(AudioManager::class.java) ?: return@runCatching
            val cb = object : AudioManager.AudioRecordingCallback() {
                override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
                    val active = !configs.isNullOrEmpty()
                    if (active && !micShown) {
                        micShown = true
                        fire(ToggleKind.Mic, true, true)
                    } else if (!active) {
                        micShown = false
                    }
                }
            }
            am.registerAudioRecordingCallback(cb, main)
            micCb = cb
        }
    }

    /* ---------- Screen recording (Android 15+) ---------- */
    @RequiresApi(35)
    private fun addScreenRec() {
        runCatching {
            val wm = ctx.getSystemService(WindowManager::class.java) ?: return@runCatching
            // state: 0 = NOT_VISIBLE, 1 = VISIBLE
            val cb = java.util.function.Consumer<Int> { st -> fire(ToggleKind.ScreenRec, st == 1, true) }
            wm.addScreenRecordingCallback(ContextCompat.getMainExecutor(ctx), cb)
            srCb = cb
            srWm = wm
        }
    }

    @RequiresApi(35)
    private fun removeScreenRec() {
        srCb?.let { cb -> runCatching { srWm?.removeScreenRecordingCallback(cb) } }
        srCb = null
        srWm = null
    }
}
