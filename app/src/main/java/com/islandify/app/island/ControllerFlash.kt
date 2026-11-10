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

// SPLITCTRL  (Banners: flash queue, sticky, reply.)

/**
 * Temporary banner (notification / charging / device) that goes away by itself.
 *
 * Queue: while a banner is on screen (or the user is typing a reply) the next one WAITS and is
 * shown when the current one is done, so two quick messages are both seen.
 *  - same sender again / same kind of status (e.g. flashlight toggled twice) = replaces, never stacks
 *  - at most [MAX_QUEUE] wait; the oldest one is dropped
 */
fun IslandController.flash(
    m: IslandMode,
    durationMs: Long = 4500,
    level: IslandLevel = IslandLevel.Banner,
    open: PendingIntent? = null,
    pkg: String? = null,
    reply: android.app.Notification.Action? = null,
    markRead: PendingIntent? = null,
    key: String? = null,
) {
    // Charging, Bluetooth, low battery, toggles, timer: bubble me bhi rakho (messages ka bubble listener banata hai)
    if (key == null) BubbleStore.addFromMode(m)
    // A ringing / ongoing call owns the island: a message must never cover Answer / Decline.
    if (callMode != null) return
    // BANNERFIX: central dedup+debounce — stops every kind of stuck banner
    run {
        val nowMs = android.os.SystemClock.uptimeMillis()
        val modeToken = m.toString()
        val keyToken  = key ?: modeToken

        // Guard 1: same notification key firing within 500 ms → ignore
        if (key != null && key == flashDedupeKey && nowMs - flashDedupeKeyMs < 500L) return
        // Guard 2: same mode (type+direction) firing within 1500 ms → ignore
        if (modeToken == flashDedupeMode && nowMs - flashDedupeModeMs < 1500L) return
        // Guard 3: flashJob already running THIS exact mode → don't interrupt it
        if (flashJob?.isActive == true && curFlash?.mode?.toString() == modeToken) return

        // Update dedup state
        if (key != null) { flashDedupeKey = key; flashDedupeKeyMs = nowMs }
        flashDedupeMode   = modeToken
        flashDedupeModeMs = nowMs

        // Guard 4: debounce map tracking for rapid bursts
        if (m !is IslandMode.Notification) {
            val prevRunnable = flashDebounceMap.remove(modeToken)
            if (prevRunnable != null) flashDebounceHandler.removeCallbacks(prevRunnable)
            flashDebounceMap[modeToken] = Runnable {}
        }
    }
    // MSGCARDKEEP: message card khula hai = naya message banner nahi banega (card band ho jata tha),
    // wo bubble me pehle se hai (listener BubbleStore.add pehle karta hai) aur bubbles row me dikhega
    // DIRECTCARD4: card khula + reply nahi = naya message banner banega (card bubble me jayega); reply me upar block ho chuka
    if (m is IslandMode.Notification && key != null && BubbleStore.cardOpen.value && BubbleStore.has(key) &&
        BubbleStore.replying.value) return
    // DIRECTCARD3: card me reply likh rahe ho = banner (charging, earbuds, timer...) card ko band na kare, bubble me rahega
    if (BubbleStore.cardOpen.value && BubbleStore.replying.value) return
    // Per-banner "show time" from Customize (0 / unset = the default time of the caller)
    // APPSTYLEKIT (PATCH_10_11_12): is app ka apna show time (Apps tab) sabse upar; phir banner-type time; phir default
    val appMs = pkg?.let { AppStyles.durationMs(it) }
    val shownMs = appMs ?: if (level == IslandLevel.Banner) IslandSettings.kindDurationMs(m) ?: durationMs else durationMs
    val item = Flash(m, shownMs, level, open, pkg, reply, markRead, key)

    val showing = flashJob?.isActive == true
    if (showing || _replying.value) {
        val cur = _mode.value
        // Same sender / same kind of status as the banner on screen: update it in place
        val updatesCurrent = showing && !_replying.value && when {
            m is IslandMode.Notification && cur is IslandMode.Notification ->
                cur.app == m.app && cur.title == m.title
            m !is IslandMode.Notification -> cur::class == m::class
            else -> false
        }
        if (!updatesCurrent) {
            // A sticky notification that has shrunk to the small pill must not block newer banners:
            // park it at the front of the queue (it comes back as a pill when they are done).
            val parked = curFlash
            if (stickyHeld && !_replying.value && _level.value == IslandLevel.Compact && parked != null) {
                parkSticky(parked)
            } else {
                enqueue(item)
                return
            }
        }
    }
    flashJob?.cancel()
    startFlash(item)
}

/** BUTTERICON2: banner pehle app icon ke saath aata hai; sender ki photo baad me mili to icon badal do (pulse dobara nahi chalta). */
fun IslandController.updateFlashIcon(key: String, icon: Bitmap) {
    val f = curFlash
    if (f != null && f.key == key) {
        val m = f.mode
        if (m is IslandMode.Notification) {
            val nm = m.copy(icon = icon)
            curFlash = f.copy(mode = nm)
            if (_mode.value == m) _mode.value = nm
        }
    }
    for (i in flashQueue.indices) {
        val q = flashQueue[i]
        val qm = q.mode
        if (q.key == key && qm is IslandMode.Notification) flashQueue[i] = q.copy(mode = qm.copy(icon = icon))
    }
}

/** BUBBLELIVE: "5 min" / "1 min 30 s" / "45 s" */
internal fun IslandController.timerText(sec: Int): String =
    if (sec >= 60) Isl.durMin(sec / 60) + (if (sec % 60 != 0) " " + Isl.secShort(sec % 60) else "") else Isl.secShort(sec)   // ISLANDI18N

/** Puts a sticky notification back at the front of the queue as an already-seen pill. */
internal fun IslandController.parkSticky(f: Flash) {
    val m = f.mode
    // Same sender already waiting? Keep only the newest.
    flashQueue.removeAll { q ->
        val qm = q.mode
        q.seen && m is IslandMode.Notification && qm is IslandMode.Notification &&
            qm.app == m.app && qm.title == m.title
    }
    flashQueue.addFirst(f.copy(seen = true, level = IslandLevel.Compact))
    while (flashQueue.size > MAX_QUEUE) flashQueue.removeLast()
    _queued.value = flashQueue.size
}

/**
 * A sticky pill is showing and a NEW (not yet seen) banner is waiting: let the new one show first,
 * the sticky notification goes back to the queue and returns as a pill afterwards.
 */
internal fun IslandController.onStickyCollapsed() {
    val cur = curFlash ?: return
    val i = flashQueue.indexOfFirst { !it.seen }
    if (i < 0) return
    val next = flashQueue.removeAt(i)
    parkSticky(cur)
    flashJob?.cancel()
    startFlash(next)
}

/** The notification was removed from the shade (or by its app): its sticky pill goes too. */
fun IslandController.notificationRemoved(key: String) {
    val before = flashQueue.size
    flashQueue.removeAll { it.key == key && it.seen }
    if (flashQueue.size != before) _queued.value = flashQueue.size
    if (stickyFlag && flashJob?.isActive == true && !_replying.value && curFlash?.key == key) endFlash()
}

internal fun IslandController.startFlash(f: Flash) {
    curFlash = f
    flashOpen = f.open
    flashPkg = f.pkg
    flashReply = f.reply
    flashMarkRead = f.markRead
    // a pop-up must not bring back a navigation / timer / download the user swiped away
    if (liveMode == null && timerMode == null && dlMode == null) hidden = false
    _mode.value = f.mode
    _level.value = if (f.seen) IslandLevel.Compact else f.level
    // "Keep notifications until I act": a real notification (it has a source app) stays on the
    // island with no timer. It goes away only by tap / swipe up / Mark as read / reply.
    // Internal banners (Sent, Time's up, charging...) keep their normal time.
    val sticky = !BubbleStore.enabled && IslandSettings.stickyNotif.value && f.mode is IslandMode.Notification && f.pkg != null
    stickyFlag = sticky
    flashJob = scope.launch {
        if (sticky) {
            // Show it for its normal time, then shrink to the small pill (auto-collapse) and stay.
            if (!f.seen) {
                delay(f.durationMs)
                _level.value = IslandLevel.Compact
            } else {
                delay(60)                // let startFlash() finish assigning flashJob first
            }
            onStickyCollapsed()          // a newer banner is waiting? show it first
            awaitCancellation()          // job stays "active" until endFlash() / dismiss() cancels it
        }
        delay(f.durationMs)
        if (flashQueue.isEmpty() && !_replying.value && base() is IslandMode.Idle && !IslandSettings.idleInfo.value) {
            // EXITKIT: dikhane ko kuch bacha nahi -> jaise bada ho kar aaya tha waise hi bada gayab ho
            // (beech me chhota hone wala step nahi). Exit animation ke baad level wapas Compact.
            afterFlash()
            scope.launch {
                delay(320)
                if (flashJob?.isActive != true && _mode.value is IslandMode.Idle) _level.value = IslandLevel.Compact
            }
        } else {
            _level.value = IslandLevel.Compact
            delay(350)
            afterFlash()
        }
    }
}

/** The banner is gone: show the next waiting one, or go back to the normal island. */
internal fun IslandController.afterFlash() {
    var next = flashQueue.removeFirstOrNull()
    // MSGCARDKEEP: card khula ho to queue ke na-dekhe message banners skip (wo bubbles me hain), card band na ho
    while (next != null && BubbleStore.cardOpen.value && !next.seen &&
        next.mode is IslandMode.Notification && BubbleStore.has(next.key)) {
        next = flashQueue.removeFirstOrNull()
    }
    _queued.value = flashQueue.size
    if (next != null) startFlash(next) else {
        curFlash = null
        _mode.value = base()
    }
}

internal fun IslandController.enqueue(f: Flash) {
    val m = f.mode
    val same = flashQueue.indexOfFirst { q ->
        val qm = q.mode
        when {
            m is IslandMode.Notification && qm is IslandMode.Notification ->
                qm.app == m.app && qm.title == m.title
            m !is IslandMode.Notification -> qm::class == m::class
            else -> false
        }
    }
    if (same >= 0) flashQueue[same] = f
    else {
        if (flashQueue.size >= MAX_QUEUE) flashQueue.removeFirst()
        flashQueue.addLast(f)
    }
    _queued.value = flashQueue.size
}

internal fun IslandController.endFlash() {
    stickyFlag = false
    flashJob?.cancel()
    _level.value = IslandLevel.Compact
    flashJob = scope.launch {
        delay(350)
        afterFlash()
    }
}

/* ---------------- notification actions (Mark as read / Reply) ---------------- */

/** "Mark as read" button: fires the notification's own action, then closes the card. */
fun IslandController.markRead() {
    runCatching { flashMarkRead?.send() }
    _replying.value = false
    endFlash()
}

/** "Reply" button: keeps the card open (no auto-collapse) and shows the text box + keyboard. */
fun IslandController.startReply() {
    if (flashReply == null) return
    stickyFlag = false
    flashJob?.cancel()          // stop the 4.5 s auto-hide while the user types
    _replying.value = true
}

fun IslandController.cancelReply() {
    if (!_replying.value) return
    _replying.value = false
    endFlash()
}

/** Sends [text] through the notification's RemoteInput, exactly like typing in the shade. */
fun IslandController.sendReply(text: String) {
    val a = flashReply
    val ctx = appCtx
    if (a == null || ctx == null || text.isBlank()) return
    val inputs = a.remoteInputs ?: return
    val ok = runCatching {
        val results = Bundle()
        inputs.forEach { results.putCharSequence(it.resultKey, text) }
        val fill = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        android.app.RemoteInput.addResultsToIntent(inputs, fill, results)
        if (Build.VERSION.SDK_INT >= 28) {
            android.app.RemoteInput.setResultsSource(fill, android.app.RemoteInput.SOURCE_FREE_FORM_INPUT)
        }
        a.actionIntent.send(ctx, 0, fill)
    }.isSuccess
    _replying.value = false
    if (ok) {
        flash(IslandMode.Notification(Isl.sent, Isl.replySent, text), 1800)   // ISLANDI18N
    } else {
        endFlash()
    }
}

/* ---------------- timer ---------------- */
