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

// SPLITCTRL  (Tap / swipe / open / dismiss logic.)

/**
 * Tap 1: pill -> card.
 * Tap 2 (on the card): opens the app the music / notification came from.
 * No source app (charging, timer, idle...): just collapses.
 */
fun IslandController.toggle() {
    if (_replying.value) return   // tapping the text box must not open the chat
    if (_level.value == IslandLevel.Compact) {
        _level.value = IslandLevel.Expanded
        return
    }
    // Music card: tapping the black part closes it (the song photo opens the music app)
    if ((_level.value == IslandLevel.Expanded || _level.value == IslandLevel.Large) &&
        _mode.value is IslandMode.Media && flashJob?.isActive != true) {
        _level.value = IslandLevel.Compact
        return
    }
    if (_level.value == IslandLevel.Banner) {
        val m = _mode.value
        // Tap on a message banner = the bubble card (message + Mark as read / Reply).
        // First message: card only. From the second message on: card + bubbles row.
        if (m is IslandMode.Notification && flashJob?.isActive == true && BubbleStore.has(curFlash?.key)) {
            val key = curFlash?.key
            queuedMessagesToBubbles()   // QUEUE2BUBBLE: waiting messages banner na banein, bubbles me rahein
            endFlash()                  // banner folds away first (level = Compact) ...
            BubbleStore.openFor(key)    // ... then the card opens on this message
            return
        }
        // A plain notification banner opens its app right away (same as before).
        // Everything else (charging, device, toggles, notifications with buttons) grows into the card.
        val opensApp = m is IslandMode.Notification && !(m.canReply || m.canMarkRead)
        if (!opensApp) {
            if (flashJob?.isActive == true) {
                flashJob?.cancel()
                startFlash(Flash(m, 7000, IslandLevel.Expanded, flashOpen, flashPkg, flashReply, flashMarkRead, curFlash?.key))
            } else _level.value = IslandLevel.Expanded
            return
        }
    }
    val wasBanner = _level.value == IslandLevel.Banner
    // QUEUE2BUBBLE: message (card) par tap = app khulta hai, baaki waiting messages bubbles me hi rahein
    if (_mode.value is IslandMode.Notification && flashJob?.isActive == true) queuedMessagesToBubbles()
    val opened = openSource()
    if (!opened && wasBanner && flashJob?.isActive == true) {
        // no source app to open (demo / system banner): grow into the card instead of vanishing
        val cur = _mode.value
        flashJob?.cancel()
        startFlash(Flash(cur, 9000, IslandLevel.Expanded, flashOpen, flashPkg, flashReply, flashMarkRead, curFlash?.key))
        return
    }
    if (flashJob?.isActive == true) endFlash() else _level.value = IslandLevel.Compact
}

internal fun IslandController.openSource(): Boolean = when (_mode.value) {
    is IslandMode.Media -> openMediaApp()
    is IslandMode.Notification -> openNotification()
    is IslandMode.Live -> openLive()
    else -> false
}

/**
 * Opens the app the notification came from.
 * Android 14+ blocks PendingIntent activity launches from the background unless the sender
 * opts in, so we pass explicit "allow background activity start" options.
 * If the notification has no contentIntent (or sending fails) we launch the app itself.
 */
internal fun IslandController.openNotification(pi: PendingIntent? = flashOpen, pkg: String? = flashPkg): Boolean =
    sendWithBal(pi) || launchPackage(pkg)

/** Used by the bubble: open the chat / screen the notification belongs to. */
fun IslandController.openFromBubble(pi: PendingIntent?, pkg: String?): Boolean = openNotification(pi, pkg)

/** Bubble card: fires an action (Archive / Mark as read). */
fun IslandController.fireAction(pi: PendingIntent?) { runCatching { pi?.send() } }

/** Bubble card: sends [text] through the RemoteInput of [a], same as replying in the shade. */
fun IslandController.sendReplyTo(a: android.app.Notification.Action, text: String): Boolean {
    val ctx = appCtx
    if (ctx == null || text.isBlank()) return false
    val inputs = a.remoteInputs ?: return false
    return runCatching {
        val results = Bundle()
        inputs.forEach { results.putCharSequence(it.resultKey, text) }
        val fill = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        android.app.RemoteInput.addResultsToIntent(inputs, fill, results)
        if (Build.VERSION.SDK_INT >= 28) {
            android.app.RemoteInput.setResultsSource(fill, android.app.RemoteInput.SOURCE_FREE_FORM_INPUT)
        }
        a.actionIntent.send(ctx, 0, fill)
    }.isSuccess
}

internal fun IslandController.openLive(): Boolean {
    val m = _mode.value as? IslandMode.Live
    return if (m?.kind == LiveKind.Download) {
        val d = downloads.values.firstOrNull()
        openNotification(d?.open, d?.pkg)
    } else openNotification(liveOpen, livePkg)
}

internal fun IslandController.launchPackage(pkg: String?): Boolean {
    val ctx = appCtx ?: return false
    if (pkg == null) return false
    val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return false
    return runCatching { ctx.startActivity(i) }.isSuccess
}

internal fun IslandController.openMediaApp(): Boolean = openNotification(media?.sessionActivity, media?.packageName)

/** Mini player: tap on the song opens the music app. */
fun IslandController.openMedia() {
    openMediaApp()
    if (flashJob?.isActive != true) _level.value = IslandLevel.Compact
}

/** Long-press (without moving): large card <-> card. */
fun IslandController.large() {
    val m = _mode.value
    // CALENDARFIX: Idle par long press hamesha kaam kare — calendar OFF ho tab bhi time/battery bada ho jaye
    if (_replying.value) return
    val next = if (_level.value == IslandLevel.Large) IslandLevel.Expanded else IslandLevel.Large
    if (flashJob?.isActive == true) {
        flashJob?.cancel()
        startFlash(Flash(m, 9000, next, flashOpen, flashPkg, flashReply, flashMarkRead, curFlash?.key))
    } else _level.value = next
}

/** Swipe up: collapse first, then hide the island (until something new arrives). */
fun IslandController.dismiss() {
    cancelFromShade(false)
    when {
        _replying.value -> cancelReply()
        flashJob?.isActive == true -> endFlash()
        _level.value != IslandLevel.Compact -> _level.value = IslandLevel.Compact
        callMode == null && _mode.value !is IslandMode.Idle -> { hidden = true; refresh() }
    }
}

/** Swipe up (default): current pop-up + all waiting pop-ups go. Music / timer / nav are NOT hidden. */
fun IslandController.clearAll() {
    cancelFromShade(true)
    flashQueue.clear()          // clear the queue first, otherwise the next pop-up shows after endFlash()
    // BANNERFIX: dedup state reset
    flashDedupeKey = null; flashDedupeKeyMs = 0L
    flashDedupeMode = null; flashDedupeModeMs = 0L
    flashDebounceHandler.removeCallbacksAndMessages(null)
    flashDebounceMap.clear()
    _queued.value = 0
    when {
        _replying.value -> cancelReply()
        flashJob?.isActive == true -> endFlash()                      // pop-up / sticky pill
        _level.value != IslandLevel.Compact -> _level.value = IslandLevel.Compact
        // nothing left to clear: the island stays as it is
    }
}

/**
 * QUEUE2BUBBLE: user ne ek message par tap kiya. Queue me waiting baaki (na dekhe hue) message pop-ups hata do:
 * wo pehle se bubbles me hain (listener BubbleStore.add pehle karta hai), to sirf tap kiya hua message dikhe,
 * baaki bubbles me rahein. Charging / Bluetooth jaise non-message banners queue me rehte hain.
 */
internal fun IslandController.queuedMessagesToBubbles() {
    val before = flashQueue.size
    flashQueue.removeAll { !it.seen && it.mode is IslandMode.Notification && BubbleStore.has(it.key) }
    if (flashQueue.size != before) _queued.value = flashQueue.size
}

/** Swipe-dismiss on a notification also removes it from the real notification shade (setting). */
internal fun IslandController.cancelFromShade(includeQueued: Boolean) {
    if (!IslandSettings.clearOnDismiss.value) return
    val keys = ArrayList<String>()
    if (_mode.value is IslandMode.Notification && flashJob?.isActive == true) {
        curFlash?.key?.let { keys.add(it) }
    }
    if (includeQueued) flashQueue.forEach { f -> f.key?.let { keys.add(it) } }
    keys.forEach { IslandListener.cancel(it) }
}

/** Swipe "Next notification": shows the next waiting banner now, or just dismisses when none waits. */
fun IslandController.nextNotification() {
    if (_replying.value) return
    if (flashJob?.isActive == true && flashQueue.isNotEmpty()) {
        stickyFlag = false
        flashJob?.cancel()
        afterFlash()
    } else dismiss()
}

internal fun IslandController.musicOnIsland(): Boolean {
    val m = _mode.value
    return media != null && (m is IslandMode.Media ||
        ((m is IslandMode.Live || m is IslandMode.Timer) && mediaMode != null))
}

/** A message (pop-up banner or opened card) is on the island and the user is not typing a reply. */
internal fun IslandController.isMessageShowing(): Boolean =
    _mode.value is IslandMode.Notification && flashJob?.isActive == true && !_replying.value

/** SWIPEDISMISS: message banner / pop-up card par left-right swipe = ye message pura gaya (bubble bhi), phir agla pop-up. */
internal fun IslandController.dismissMessage() {
    val key = curFlash?.key
    if (key != null && flashQueue.removeAll { it.key == key }) _queued.value = flashQueue.size
    dismiss()   // pop-up band + (setting ON ho to) shade se bhi
    if (key != null) BubbleStore.dismissKey(key)
}

/**
 * SWIPERULE: message banner / card par swipe up = bubble me chhupao. Notification shade se NAHI hatti
 * (hatti to bubble bhi 2.5 s baad chala jata), bubble rehta hai. Agla waiting pop-up ho to wo aata hai.
 */
internal fun IslandController.hideMessage() {
    when {
        flashJob?.isActive == true -> endFlash()
        _level.value != IslandLevel.Compact -> _level.value = IslandLevel.Compact
    }
}

/** A swipe on the island: runs whatever the user chose for that direction (Customize > Swipe gestures). */
fun IslandController.swipe(dir: SwipeDir) {
    // A message is showing (banner or opened card): swipe left / right / up sends it away.
    // (Left / right used to mean next / previous track, which does nothing for a message.)
    if (isMessageShowing()) {
        when (dir) {
            SwipeDir.Left, SwipeDir.Right -> { dismissMessage(); return }   // SWIPEDISMISS
            SwipeDir.Up -> { hideMessage(); return }   // SWIPERULE
            SwipeDir.Down -> Unit
        }
    }
    when (IslandSettings.swipeFlow(dir).value) {
        SwipeAction.None -> Unit
        SwipeAction.Dismiss -> dismiss()
        SwipeAction.ClearAll -> clearAll()
        SwipeAction.Expand -> if (!_replying.value && _level.value == IslandLevel.Compact) toggle()
        SwipeAction.OpenApp -> if (!_replying.value && openSource()) {
            if (flashJob?.isActive == true) endFlash() else _level.value = IslandLevel.Compact
        }
        SwipeAction.NextTrack -> if (musicOnIsland()) next()
        SwipeAction.PrevTrack -> if (musicOnIsland()) prev()
        SwipeAction.PlayPause -> if (musicOnIsland()) playPause()
        SwipeAction.VolumeUp -> volume(true)
        SwipeAction.VolumeDown -> volume(false)
        SwipeAction.CycleSound -> cycleSound()
        SwipeAction.Flashlight -> toggleTorch()
        SwipeAction.NextNotif -> nextNotification()
    }
}

/* ---------------- media ---------------- */

/** Bubble par tap: banner / card khula ho to pehle pill chhota ho jaye (phir bubble ka card khulta hai). */
fun IslandController.collapseForBubble() {
    if (_replying.value) return
    if (flashJob?.isActive == true) endFlash() else _level.value = IslandLevel.Compact
}

/** Touch outside the island window (only delivered while a card is open). Closes the card when the setting is on. */
fun IslandController.outsideTouch() {
    if (!IslandSettings.dismissOnOutside.value || callMode != null || _replying.value) return
    if (_level.value == IslandLevel.Compact) return
    if (flashJob?.isActive == true) endFlash() else _level.value = IslandLevel.Compact
}
/* ---------------- calls ---------------- */
