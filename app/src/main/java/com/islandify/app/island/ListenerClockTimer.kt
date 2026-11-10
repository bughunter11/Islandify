package com.islandify.app.island

import com.islandify.app.core.*
import com.islandify.app.ui.components.*
import com.islandify.app.ui.onboarding.*
import com.islandify.app.ui.screens.*
import com.islandify.app.ui.theme.*

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import android.os.Build
import android.os.SystemClock
import android.telecom.TelecomManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay   // CANCELFIX
import com.islandify.app.island.IslandListener.Companion.NON_LETTER
import com.islandify.app.island.IslandListener.Companion.CALL_ACTION_WORDS
import com.islandify.app.island.IslandListener.Companion.CLOCK_APPS
import com.islandify.app.island.IslandListener.Companion.TIME_RE
import com.islandify.app.island.IslandListener.Companion.NAV_APPS
import com.islandify.app.island.IslandListener.Companion.TRACK_APPS
import com.islandify.app.island.IslandListener.Companion.DIALERS

// SPLITLO  (Clock app timer notifications.)

internal fun IslandListener.handleClockTimer(sbn: StatusBarNotification): Boolean {
    if (sbn.packageName !in CLOCK_APPS) return false
    val n = sbn.notification
    val ex = n.extras

    // 1) Count-down chronometer (Google Clock aur zyadatar apps)
    if (ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)) {
        if (!ex.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false)) return false // stopwatch
        if (!IslandSettings.isOn(Trigger.Timer)) return true
        val ms = n.`when` - System.currentTimeMillis()
        if (ms <= 0 || ms > 24 * 3600_000L) return true
        IslandController.syncClockTimer(sbn.key, SystemClock.elapsedRealtime() + ms)
        return true
    }

    // Count-down chronometer is not running any more: if this was our timer, it is paused / finished
    IslandController.pauseClockTimer(sbn.key)

    // 2) Text wala timer ("04:59"), jaise kuch OEM clocks: sirf tab jab time ghat raha ho
    if (n.flags and Notification.FLAG_ONGOING_EVENT == 0) return false
    val txt = listOf(
        ex.getCharSequence(Notification.EXTRA_TITLE),
        ex.getCharSequence(Notification.EXTRA_TEXT),
        ex.getCharSequence(Notification.EXTRA_SUB_TEXT),
    ).joinToString(" ") { it?.toString().orEmpty() }
    if (txt.contains("stopwatch", true) || txt.contains("\u0938\u094D\u091F\u0949\u092A\u0935\u0949\u091A")) return false
    val m = TIME_RE.find(txt) ?: return false
    if (!IslandSettings.isOn(Trigger.Timer)) return true

    val secs = (m.groupValues[1].toIntOrNull() ?: 0) * 3600 +
        m.groupValues[2].toInt() * 60 + m.groupValues[3].toInt()
    val now = SystemClock.elapsedRealtime()
    val prev = textTimers[sbn.key]
    when {
        prev == null -> textTimers[sbn.key] = secs to now
        now - prev.second < 1000 -> {}                       // duplicate post, ignore
        secs < prev.first && now - prev.second < 5000 -> {   // time ghat raha hai = countdown
            textTimers[sbn.key] = secs to now
            IslandController.syncClockTimer(sbn.key, now + secs * 1000L, needsBeat = true)
        }
        else -> {                                            // wahi / badha hua = paused ya alarm
            textTimers[sbn.key] = secs to now
            IslandController.clearClockTimer(sbn.key)
        }
    }
    return true
}

/* ---------------- navigation, delivery, rides ---------------- */
