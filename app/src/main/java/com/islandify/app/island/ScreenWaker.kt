package com.islandify.app.island

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.WindowManager

/**
 * Turns the display on when a notification arrives while the screen is off.
 *
 * Android has no API that lets a third-party app draw on the Always-On Display: AOD is drawn by
 * the system (SystemUI or the OEM) and nothing of ours can be added to it. What an app *can* do is
 * light the screen up, so the notification is readable on the lock screen. This does that:
 *
 *  1. a short SCREEN_BRIGHT wake lock with ACQUIRE_CAUSES_WAKEUP — needs nothing but WAKE_LOCK;
 *  2. an invisible [WakeActivity] with setTurnScreenOn() — for ROMs that ignore the flag above
 *     (several MIUI / One UI builds do).
 *
 * The screen goes back to sleep on its own: the wake lock is released after [WAKE_MS] and then the
 * system's screen timeout takes over. Nothing here runs when the screen is already on.
 */
object ScreenWaker {
    private const val TAG = "islandify:wake"
    private const val WAKE_MS = 8_000L

    private var lock: PowerManager.WakeLock? = null

    /** True when the display is off (screen off, AOD or doze). */
    fun screenOff(c: Context): Boolean =
        runCatching { c.getSystemService(PowerManager::class.java)?.isInteractive == false }
            .getOrDefault(false)

    fun wake(c: Context) {
        if (!screenOff(c)) return
        runCatching {
            val pm = c.getSystemService(PowerManager::class.java) ?: return@runCatching
            @Suppress("DEPRECATION")
            val l = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                TAG
            )
            l.setReferenceCounted(false)
            l.acquire(WAKE_MS)
            lock = l
        }
        // A background activity start is allowed here: the notification listener is bound by the
        // system and the app also holds "draw over other apps". If a ROM still refuses it, the
        // wake lock above has already turned the screen on.
        runCatching {
            c.startActivity(
                Intent(c, WakeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        }
    }
}

/**
 * Invisible helper window that asks the system to turn the display on and to sit above the
 * keyguard. It shows nothing and finishes itself after a moment.
 */
class WakeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            if (Build.VERSION.SDK_INT >= 27) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.setDimAmount(0f)
        }
        Handler(Looper.getMainLooper()).postDelayed({ finishAndRemoveTask() }, 600)
    }
}
