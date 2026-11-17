package com.islandify.app.island

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.islandify.app.core.IslandSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Runs Islandify's own Always-On screen.
 *
 * Android does not let apps paint on the real AOD (that window belongs to SystemUI / the OEM and
 * is only drawn in doze), so this keeps the panel on instead and shows a black, almost unlit
 * [AodActivity] — the same workaround every third-party "Always On AMOLED" app uses.
 *
 * Lifecycle:
 *  - display goes off (power or timeout) -> after a short pause the AOD is shown;
 *  - power pressed again while the AOD is up -> stay dark until the next screen-on (a second
 *    press must switch the phone off, like on a real AOD phone);
 *  - covered proximity sensor -> the panel goes off through the framework (pocket / face down),
 *    back on when uncovered;
 *  - unlock -> hide and forget the notifications that were shown;
 *  - battery at [MIN_LEVEL] or lower while unplugged -> hide and don't start.
 *
 * Attached by [IslandA11yService]: a third-party app can only draw above the keyguard with an
 * accessibility overlay, and starting the AOD window from an accessibility service is also what
 * keeps Android from blocking the background activity start.
 */
object AodController {
    private const val TAG = "islandify:aod"
    /** The display is still fading out right after SCREEN_OFF, wait a little. */
    private const val ENTER_DELAY_MS = 700L
    private const val MIN_LEVEL = 5

    private var app: Context? = null
    private var scope: CoroutineScope? = null
    private var settingsJob: Job? = null
    private var enterJob: Job? = null

    /** Lights the panel up for a moment; the activity's KEEP_SCREEN_ON then holds it. */
    private var wakeLock: PowerManager.WakeLock? = null
    /** Turns the panel off while something covers the proximity sensor (pocket, face down). */
    private var pocketLock: PowerManager.WakeLock? = null

    private var sensors: SensorManager? = null
    private var sensorOn = false

    /** True while the AOD activity is on screen. */
    var showing = false
        private set

    /** Power button used to switch the AOD off: do not come back until the next screen-on. */
    private var offByUser = false
    private var near = false
    private var level = 100
    private var charging = false
    private var registered = false

    fun attach(c: Context) {
        if (app != null) return
        val ctx = c.applicationContext
        app = ctx
        readBattery(ctx)

        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = s
        ContextCompat.registerReceiver(
            ctx, screen,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ContextCompat.registerReceiver(
            ctx, battery, IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        registered = true

        settingsJob = s.launch {
            IslandSettings.aod.collect { on ->
                if (!on) hide()
                listenProximity(on)
                if (on) {
                    // Switched on while the phone is already idle: show it right away
                    val pm = app?.getSystemService(PowerManager::class.java)
                    if (pm?.isInteractive == false) {
                        enterJob?.cancel()
                        enterJob = launch {
                            delay(ENTER_DELAY_MS)
                            enterIfPossible()
                        }
                    }
                }
            }
        }
    }

    fun detach() {
        hide()
        listenProximity(false)
        settingsJob?.cancel()
        settingsJob = null
        scope?.cancel()
        scope = null
        val c = app
        if (registered && c != null) {
            runCatching { c.unregisterReceiver(screen) }
            runCatching { c.unregisterReceiver(battery) }
        }
        registered = false
        app = null
    }

    /** The AOD window is gone (tap, swipe, system): release everything and stay dark. */
    fun hide() {
        enterJob?.cancel()
        enterJob = null
        if (showing) {
            showing = false
            AodActivity.close()
        }
        runCatching { pocketLock?.release() }
        pocketLock = null
        runCatching { wakeLock?.release() }
        wakeLock = null
    }

    /** Called by [AodActivity] when the user switches the phone off from the AOD. */
    fun onScreenOffWhileShowing() {
        if (!showing || near) return
        hide()
        offByUser = true
    }

    /** Called by [AodActivity] when its window is destroyed for any reason. */
    fun onActivityDestroyed() {
        showing = false
        runCatching { wakeLock?.release() }
        wakeLock = null
        runCatching { pocketLock?.release() }
        pocketLock = null
    }

    /* ------------------------------ screen state ------------------------------ */

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (!IslandSettings.aod.value) return
                    // Covering the sensor switches the panel off on purpose: not a power press.
                    if (near) return
                    if (showing) {
                        // Power pressed while the AOD was up: the user wants the phone dark.
                        hide()
                        offByUser = true
                        return
                    }
                    if (offByUser) return
                    val s = scope ?: return
                    enterJob?.cancel()
                    enterJob = s.launch {
                        delay(ENTER_DELAY_MS)
                        enterIfPossible()
                    }
                }
                Intent.ACTION_SCREEN_ON -> offByUser = false
                Intent.ACTION_USER_PRESENT -> {
                    offByUser = false
                    hide()
                    AodNotes.clear()   // the phone was unlocked, nothing left to remind about
                }
            }
        }
    }

    private fun enterIfPossible() {
        val c = app ?: return
        if (!IslandSettings.aod.value || showing || offByUser || near) return
        if (level <= MIN_LEVEL && !charging) return
        if (isInteractive(c)) return
        val kg = c.getSystemService(KeyguardManager::class.java)
        if (kg?.isKeyguardLocked != true) return
        enter(c)
    }

    private fun enter(c: Context) {
        if (showing) return
        showing = true
        // 1) Light the panel even if this ROM refuses the background activity start below.
        runCatching {
            @Suppress("DEPRECATION")
            val l = c.getSystemService(PowerManager::class.java)
                .newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    TAG
                )
            l.setReferenceCounted(false)
            l.acquire(15_000)
            wakeLock = l
        }
        // 2) Pocket mode: while this lock is held the framework switches the panel off as soon as
        //    the proximity sensor is covered (exactly like during a call) and back on afterwards.
        //    On ROMs that ignore it nothing happens — the AOD just stays on.
        if (sensorAvailable(c)) {
            runCatching {
                @Suppress("DEPRECATION")
                val l = c.getSystemService(PowerManager::class.java)
                    .newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "$TAG:pocket")
                l.setReferenceCounted(false)
                l.acquire()
                pocketLock = l
            }
        }
        // 3) The almost unlit AOD window itself; its FLAG_KEEP_SCREEN_ON takes over from the lock.
        AodActivity.start(c)
    }

    private fun isInteractive(c: Context): Boolean =
        runCatching { c.getSystemService(PowerManager::class.java)?.isInteractive }
            .getOrNull() ?: true

    /* ------------------------------- proximity ------------------------------- */

    private val prox = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val v = e.values.firstOrNull() ?: return
            near = v < e.sensor.maximumRange     // covered: pocket, face down, hand over the top
        }

        override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
    }

    private fun sensors(c: Context): SensorManager? =
        sensors ?: runCatching { c.getSystemService(SensorManager::class.java) }
            .getOrNull().also { sensors = it }

    private fun sensorAvailable(c: Context): Boolean =
        sensors(c)?.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null

    private fun listenProximity(on: Boolean) {
        if (on == sensorOn) return
        val c = app ?: return
        sensorOn = on
        val sm = sensors(c) ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY) ?: return
        if (on) {
            near = false
            runCatching { sm.registerListener(prox, sensor, SensorManager.SENSOR_DELAY_NORMAL) }
        } else {
            near = false
            runCatching { sm.unregisterListener(prox) }
        }
    }

    /* -------------------------------- battery -------------------------------- */

    private val battery = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, level)
            val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            charging = st == BatteryManager.BATTERY_STATUS_CHARGING ||
                st == BatteryManager.BATTERY_STATUS_FULL
            if (showing && level <= MIN_LEVEL && !charging) hide()
        }
    }

    private fun readBattery(c: Context) {
        runCatching {
            val i = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, level) ?: level
            val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            charging = st == BatteryManager.BATTERY_STATUS_CHARGING ||
                st == BatteryManager.BATTERY_STATUS_FULL
        }
    }
}
