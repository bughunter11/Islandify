package com.islandify.app.island

import com.islandify.app.core.*
import com.islandify.app.ui.components.*
import com.islandify.app.ui.onboarding.*
import com.islandify.app.ui.screens.*
import com.islandify.app.ui.theme.*

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import android.os.Build
import android.os.Bundle
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.ceil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.islandify.app.island.IslandController.Parked
import com.islandify.app.island.IslandController.Dl
import com.islandify.app.island.IslandController.Flash

// SPLITCTRL  (Timer + Clock app timer.)

fun IslandController.startTimer(seconds: Int) {
    timerJob?.cancel()
    extKey = null
    hidden = false
    timerJob = scope.launch {
        // Work from an end time (like the Clock timer): "delay(1000)" per loop adds up the
        // time spent drawing / refreshing and the countdown slowly drifts.
        val endAt = SystemClock.elapsedRealtime() + seconds * 1000L
        while (true) {
            val ms = endAt - SystemClock.elapsedRealtime()
            if (ms <= 0) break
            val left = ceil(ms / 1000.0).toInt()
            timerMode = IslandMode.Timer(left, seconds)
            refresh()
            delay(ms - (left - 1) * 1000L)   // sleep until the next whole second boundary
        }
        timerMode = null
        flash(IslandMode.Notification("Timer", Isl.timesUp, Isl.complete(durationText(seconds))))   // ISLANDI18N   // POLISHKIT
    }
}

/** POLISHKIT durationText: 300 -> "5 min", 90 -> "1 min 30 sec", 3600 -> "1 hr". */
internal fun IslandController.durationText(sec: Int): String {
    val h = sec / 3600
    val m = sec % 3600 / 60
    val s = sec % 60
    return buildList {
        if (h > 0) add(Isl.durHr(h))   // ISLANDI18N
        if (m > 0) add(Isl.durMin(m))
        if (s > 0 || (h == 0 && m == 0)) add(Isl.durSec(s))
    }.joinToString(" ")
}

fun IslandController.stopTimer() {
    if (extKey != null) {
        // Clock ka timer Clock me chalta rahega, island se sirf hide hoga
        hidden = true
        _level.value = IslandLevel.Compact
        refresh()
        return
    }
    timerJob?.cancel()
    timerMode = null
    _level.value = IslandLevel.Compact
    refresh()
}

/** Clock app ka timer start / update. [endAt] = elapsedRealtime jab wo 0 hoga. */
fun IslandController.syncClockTimer(key: String, endAt: Long, needsBeat: Boolean = false) {
    val now = SystemClock.elapsedRealtime()
    extBeat = now
    if (key == extKey && abs(endAt - extEnd) < 1500) return   // wahi timer, kuch change nahi
    val left = ceil((endAt - now) / 1000.0).toInt()
    if (left <= 0) return
    val same = key == extKey
    if (!same) hidden = false
    extTotal = if (same && extTotal >= left) extTotal else left
    extKey = key
    extEnd = endAt
    extNeedsBeat = needsBeat
    BubbleStore.putStatus("clocktimer", Isl.timer, Isl.timerRunning, timerText(extTotal), 0xFFFB8C00.toInt(), null)   // BUBBLELIVE
    timerJob?.cancel()
    timerJob = scope.launch {
        while (true) {
            val t = SystemClock.elapsedRealtime()
            val ms = extEnd - t
            if (ms <= 0) break
            if (extNeedsBeat && t - extBeat > 3500) { dropClockTimer(); return@launch }  // paused
            val secs = ceil(ms / 1000.0).toInt()
            timerMode = IslandMode.Timer(secs, extTotal, external = true)
            refresh()
            delay(ms - (secs - 1) * 1000L)
        }
        timerMode = null
        extKey = null
        BubbleStore.removeStatus("clocktimer")   // BUBBLELIVE
        flash(IslandMode.Notification("Timer", Isl.timesUp, Isl.timerComplete))
    }
}

/** Clock ki notification hati / pause hui. Khatam hone ke paas thi to "Time's up". */
fun IslandController.clearClockTimer(key: String? = null) {
    val k = extKey ?: return
    if (key != null && key != k) return
    val left = extEnd - SystemClock.elapsedRealtime()
    dropClockTimer()
    if (left in 0L..2000L) flash(IslandMode.Notification("Timer", Isl.timesUp, Isl.timerComplete))
}

/** Settings changed (Timer off / Clock app blocked): remove the Clock timer without a "Time's up" banner. */
fun IslandController.dropClockTimerSilently(key: String? = null) {
    val k = extKey ?: return
    if (key != null && key != k) return
    dropClockTimer()
}

/** The chronometer of the Clock notification stopped (timer paused). Text timers use the beat check instead. */
fun IslandController.pauseClockTimer(key: String) {
    if (key == extKey && !extNeedsBeat) clearClockTimer(key)
}

internal fun IslandController.dropClockTimer() {
    timerJob?.cancel()
    timerJob = null
    extKey = null
    timerMode = null
    BubbleStore.removeStatus("clocktimer")   // BUBBLELIVE
    if (_mode.value is IslandMode.Timer) _level.value = IslandLevel.Compact
    refresh()
}

/* ---------------- live activities (navigation, delivery, rides) ---------------- */
