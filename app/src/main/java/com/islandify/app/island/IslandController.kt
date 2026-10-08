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
import android.os.Build
import android.os.Bundle
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.ceil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

sealed interface IslandMode {
    data object Idle : IslandMode
    data class Charging(val percent: Int, val connected: Boolean = true) : IslandMode
    data class Notification(
        val app: String,
        val title: String,
        val text: String,
        val canReply: Boolean = false,      // notification has an inline-reply action
        val canMarkRead: Boolean = false,   // notification has a "Mark as read" action
    ) : IslandMode
    data class Media(
        val title: String,
        val artist: String,
        val playing: Boolean,
        val art: Bitmap? = null,
        val accent: Int? = null,        // ARGB color extracted from the album art (for the glow)
        val durationMs: Long = 0,
        val positionMs: Long = 0,
        val updatedAt: Long = 0,        // elapsedRealtime when positionMs was read
        val speed: Float = 1f,
    ) : IslandMode
    data class Timer(val remainingSec: Int, val totalSec: Int, val external: Boolean = false) : IslandMode
    data class Call(
        val name: String,
        val incoming: Boolean,
        val since: Long,
        val canAnswer: Boolean = true,   // false = no Answer button found, so none is shown
    ) : IslandMode
    data class Device(val name: String, val headphones: Boolean) : IslandMode
    /** Battery warning at 20% and 10% (only while not charging). */
    data class LowBattery(val percent: Int) : IslandMode
    /** Silent / vibrate / Do Not Disturb / flashlight just changed. [on] = the new state. */
    data class Toggle(val kind: ToggleKind, val on: Boolean) : IslandMode
    /** Navigation or delivery / ride tracking, read from the other app's ongoing notification. */
    data class Live(
        val kind: LiveKind,
        val app: String,
        val title: String,
        val text: String,
        val progress: Int = -1,    // 0..100, -1 = unknown (spinner)
    ) : IslandMode
}

enum class ToggleKind { Silent, Vibrate, Dnd, Flashlight }

enum class LiveKind { Navigation, Tracking, Download }

/** The 4 island sizes: small pill, banner (quick popups), card, and large card. */
enum class IslandLevel { Compact, Banner, Expanded, Large }

/** Single source of truth. The services, the listener and the UI all talk through this. */
object IslandController {
    /** How long a paused song stays on the island before it hides. */
    private const val PAUSE_HIDE_MS = 10_000L

    /** Notifications waiting behind the banner that is on screen (oldest is dropped when full). */
    private const val MAX_QUEUE = 5

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _mode = MutableStateFlow<IslandMode>(IslandMode.Idle)
    val mode = _mode.asStateFlow()

    private val _level = MutableStateFlow(IslandLevel.Compact)
    val level = _level.asStateFlow()

    var media: MediaController? = null

    private var appCtx: Context? = null
    private var flashOpen: PendingIntent? = null   // intent opened when the notification card is tapped
    private var flashPkg: String? = null           // source app of the notification (fallback launch)
    private var flashReply: android.app.Notification.Action? = null   // inline reply action (has RemoteInput)
    private var flashMarkRead: PendingIntent? = null                    // "Mark as read" action

    /** True while the user is typing a reply inside the island (the window becomes focusable for the keyboard). */
    private val _replying = MutableStateFlow(false)
    val replying = _replying.asStateFlow()

    fun attach(ctx: Context) { appCtx = ctx.applicationContext }

    private var callKey: String? = null
    private var callAnswer: PendingIntent? = null
    private var callDecline: PendingIntent? = null

    private var mediaMode: IslandMode.Media? = null
    private var timerMode: IslandMode.Timer? = null
    private var callMode: IslandMode.Call? = null
    private var liveMode: IslandMode.Live? = null
    private var liveKey: String? = null
    private var liveOpen: PendingIntent? = null
    private var livePkg: String? = null

    // Downloads / progress-bar notifications. Insertion order = the first running download is shown.
    private class Dl(val mode: IslandMode.Live, val open: PendingIntent?, val pkg: String?)
    private val downloads = LinkedHashMap<String, Dl>()
    private var dlMode: IslandMode.Live? = null
    private var hidden = false
    private var flashJob: Job? = null

    /** A banner (notification / charging / device...) that is on screen or waiting for its turn. */
    private data class Flash(
        val mode: IslandMode,
        val durationMs: Long,
        val level: IslandLevel,
        val open: PendingIntent?,
        val pkg: String?,
        val reply: android.app.Notification.Action?,
        val markRead: PendingIntent?,
        val key: String? = null,        // source notification key (so removing it from the shade clears the pill)
        val seen: Boolean = false,      // sticky notification that was already shown: comes back as a small pill
    )

    /** The banner that is on screen right now (needed to park a sticky pill behind newer banners). */
    private var curFlash: Flash? = null

    /**
     * "Keep notifications until dismissed" is active for the banner on screen: after its show time it
     * shrinks to the small pill and STAYS there (no timer, so it is still there when the phone is
     * unlocked later) until tap / swipe up / Mark as read / reply / removed from the shade.
     */
    private var stickyFlag = false
    private val stickyHeld get() = stickyFlag && flashJob?.isActive == true

    private val flashQueue = ArrayDeque<Flash>()

    /** How many banners are waiting. The island shows it as a "+N" badge. */
    private val _queued = MutableStateFlow(0)
    val queued = _queued.asStateFlow()

    /**
     * Music that plays next to a timer or navigation. The island shows it as a small second part
     * (compact: art + waveform, card: a mini player). Only used while the main mode is Timer / Live.
     */
    private val _secondary = MutableStateFlow<IslandMode.Media?>(null)
    val secondary = _secondary.asStateFlow()
    private var timerJob: Job? = null

    // Music that has been paused for a while is not an "activity": the island hides until it plays again
    private var pauseJob: Job? = null
    private var pausedHide = false

    private fun base(): IslandMode {
        callMode?.let { return it }
        if (hidden) return IslandMode.Idle
        return timerMode ?: liveMode ?: dlMode ?: (if (pausedHide) null else mediaMode) ?: IslandMode.Idle
    }

    private fun refresh() {
        _secondary.value = if (pausedHide) null else mediaMode
        // startReply() cancels flashJob, so also check _replying: otherwise every timer tick /
        // music update would overwrite the notification card while the user is typing.
        if (flashJob?.isActive == true || _replying.value) return
        val b = base()
        // Idle card (time / date / battery) must not stay open when a real activity arrives
        val prev = _mode.value
        if ((prev is IslandMode.Idle) != (b is IslandMode.Idle)) _level.value = IslandLevel.Compact
        _mode.value = b
    }

    /* ---------------- gestures ---------------- */

    /**
     * Tap 1: pill -> card.
     * Tap 2 (on the card): opens the app the music / notification came from.
     * No source app (charging, timer, idle...): just collapses.
     */
    fun toggle() {
        if (_replying.value) return   // tapping the text box must not open the chat
        if (_level.value == IslandLevel.Compact) {
            _level.value = IslandLevel.Expanded
            return
        }
        if (_level.value == IslandLevel.Banner) {
            val m = _mode.value
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
        openSource()
        if (flashJob?.isActive == true) endFlash() else _level.value = IslandLevel.Compact
    }

    private fun openSource(): Boolean = when (_mode.value) {
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
    private fun openNotification(pi: PendingIntent? = flashOpen, pkg: String? = flashPkg): Boolean =
        sendWithBal(pi) || launchPackage(pkg)

    private fun openLive(): Boolean {
        val m = _mode.value as? IslandMode.Live
        return if (m?.kind == LiveKind.Download) {
            val d = downloads.values.firstOrNull()
            openNotification(d?.open, d?.pkg)
        } else openNotification(liveOpen, livePkg)
    }

    private fun launchPackage(pkg: String?): Boolean {
        val ctx = appCtx ?: return false
        if (pkg == null) return false
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return false
        return runCatching { ctx.startActivity(i) }.isSuccess
    }

    private fun openMediaApp(): Boolean = openNotification(media?.sessionActivity, media?.packageName)

    /** Mini player: tap on the song opens the music app. */
    fun openMedia() {
        openMediaApp()
        if (flashJob?.isActive != true) _level.value = IslandLevel.Compact
    }

    /** Long-press (without moving): large card <-> card. */
    fun large() {
        val m = _mode.value
        if (m is IslandMode.Idle || _replying.value) return
        val next = if (_level.value == IslandLevel.Large) IslandLevel.Expanded else IslandLevel.Large
        if (flashJob?.isActive == true) {
            flashJob?.cancel()
            startFlash(Flash(m, 9000, next, flashOpen, flashPkg, flashReply, flashMarkRead, curFlash?.key))
        } else _level.value = next
    }

    /** Swipe up: collapse first, then hide the island (until something new arrives). */
    fun dismiss() {
        when {
            _replying.value -> cancelReply()
            flashJob?.isActive == true -> endFlash()
            _level.value != IslandLevel.Compact -> _level.value = IslandLevel.Compact
            callMode == null && _mode.value !is IslandMode.Idle -> { hidden = true; refresh() }
        }
    }

    /** Swipe up (default): current pop-up + all waiting pop-ups go. Music / timer / nav are NOT hidden. */
    fun clearAll() {
        flashQueue.clear()          // clear the queue first, otherwise the next pop-up shows after endFlash()
        _queued.value = 0
        when {
            _replying.value -> cancelReply()
            flashJob?.isActive == true -> endFlash()                      // pop-up / sticky pill
            _level.value != IslandLevel.Compact -> _level.value = IslandLevel.Compact
            // nothing left to clear: the island stays as it is
        }
    }

    private fun musicOnIsland(): Boolean {
        val m = _mode.value
        return media != null && (m is IslandMode.Media ||
            ((m is IslandMode.Live || m is IslandMode.Timer) && mediaMode != null))
    }

    /** A swipe on the island: runs whatever the user chose for that direction (Customize > Swipe gestures). */
    fun swipe(dir: SwipeDir) {
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
        }
    }

    /* ---------------- media ---------------- */

    fun setMedia(m: IslandMode.Media?) {
        val old = mediaMode
        // Only the position moved a little (normal playback)? Nothing to redraw.
        if (old != null && m != null && old.playing && m.playing &&
            old.title == m.title && old.artist == m.artist && old.art === m.art &&
            old.accent == m.accent && old.durationMs == m.durationMs && old.speed == m.speed &&
            old.updatedAt != 0L && m.updatedAt != 0L
        ) {
            val expected = old.positionMs + ((m.updatedAt - old.updatedAt) * old.speed).toLong()
            if (abs(expected - m.positionMs) < 1500) return
        }
        if (m == null || old == null || old.title != m.title || old.playing != m.playing) hidden = false
        mediaMode = m
        if (m == null || m.playing) {
            pauseJob?.cancel()
            pausedHide = false
        } else if (old == null || old.playing || old.title != m.title) {
            // Just paused (or a new paused track): keep the pill for a few seconds, then hide it
            pauseJob?.cancel()
            pausedHide = false
            pauseJob = scope.launch {
                delay(PAUSE_HIDE_MS)
                pausedHide = true
                refresh()
            }
        }
        if (m == null && _mode.value is IslandMode.Media) _level.value = IslandLevel.Compact
        refresh()
    }

    /** Works for the music on the island even when it is only the second part (next to navigation). */
    fun playPause() {
        val c = media ?: return
        val s = c.playbackState?.state
        if (s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_BUFFERING) c.transportControls.pause()
        else c.transportControls.play()
    }

    fun prev() { media?.transportControls?.skipToPrevious() }
    fun next() { media?.transportControls?.skipToNext() }

    fun seekTo(ms: Long) {
        media?.transportControls?.seekTo(ms)
    }

    /* ---------------- calls ---------------- */

    fun setCall(
        name: String, incoming: Boolean, answer: PendingIntent?, decline: PendingIntent?,
        key: String? = null,
        canAnswer: Boolean = answer != null,
    ) {
        val old = callMode
        if (key != null) callKey = key
        val since = if (old != null && old.incoming == incoming) old.since else SystemClock.elapsedRealtime()
        val c = IslandMode.Call(name, incoming, since, canAnswer)
        callMode = c
        callAnswer = answer
        callDecline = decline
        hidden = false
        _replying.value = false
        if (stickyHeld) curFlash?.let { parkSticky(it) }   // keep the sticky pill (before cancel)
        flashJob?.cancel()
        flashQueue.removeAll { !it.seen }                   // drop only unseen pop-ups, keep sticky ones
        _queued.value = 0
        _mode.value = c
        if (incoming) _level.value = IslandLevel.Expanded
        else if (old == null || old.incoming) _level.value = IslandLevel.Compact
    }

    /** [key] = the notification that was removed. A stale removal (old ringing notif) must not kill the new call. */
    fun clearCall(key: String? = null) {
        if (callMode == null) return
        if (key != null && callKey != null && key != callKey) return
        callKey = null
        callMode = null
        callAnswer = null
        callDecline = null
        _level.value = IslandLevel.Compact
        // a sticky pill waiting from before the call comes back
        if (flashQueue.isNotEmpty() && flashJob?.isActive != true) afterFlash() else refresh()
    }

    /** Android 14+ blocks activity PendingIntents sent from the background unless we opt in. */
    private fun sendWithBal(pi: PendingIntent?): Boolean {
        if (pi == null) return false
        return runCatching {
            val ctx = appCtx
            if (ctx != null) {
                val opts = ActivityOptions.makeBasic()
                if (Build.VERSION.SDK_INT >= 34) {
                    opts.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                }
                pi.send(ctx, 0, null, null, null, null, opts.toBundle())
            } else pi.send()
        }.isSuccess
    }

    fun answerCall() { sendWithBal(callAnswer) }
    fun declineCall() { sendWithBal(callDecline) }

    /* ---------------- flash banner ---------------- */

    /**
     * Temporary banner (notification / charging / device) that goes away by itself.
     *
     * Queue: while a banner is on screen (or the user is typing a reply) the next one WAITS and is
     * shown when the current one is done, so two quick messages are both seen.
     *  - same sender again / same kind of status (e.g. flashlight toggled twice) = replaces, never stacks
     *  - at most [MAX_QUEUE] wait; the oldest one is dropped
     */
    fun flash(
        m: IslandMode,
        durationMs: Long = 4500,
        level: IslandLevel = IslandLevel.Banner,
        open: PendingIntent? = null,
        pkg: String? = null,
        reply: android.app.Notification.Action? = null,
        markRead: PendingIntent? = null,
        key: String? = null,
    ) {
        // A ringing / ongoing call owns the island: a message must never cover Answer / Decline.
        if (callMode != null) return
        // Per-banner "show time" from Customize (0 / unset = the default time of the caller)
        val shownMs = if (level == IslandLevel.Banner) IslandSettings.kindDurationMs(m) ?: durationMs else durationMs
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

    /** Puts a sticky notification back at the front of the queue as an already-seen pill. */
    private fun parkSticky(f: Flash) {
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
    private fun onStickyCollapsed() {
        val cur = curFlash ?: return
        val i = flashQueue.indexOfFirst { !it.seen }
        if (i < 0) return
        val next = flashQueue.removeAt(i)
        parkSticky(cur)
        flashJob?.cancel()
        startFlash(next)
    }

    /** The notification was removed from the shade (or by its app): its sticky pill goes too. */
    fun notificationRemoved(key: String) {
        val before = flashQueue.size
        flashQueue.removeAll { it.key == key && it.seen }
        if (flashQueue.size != before) _queued.value = flashQueue.size
        if (stickyFlag && flashJob?.isActive == true && !_replying.value && curFlash?.key == key) endFlash()
    }

    private fun startFlash(f: Flash) {
        curFlash = f
        flashOpen = f.open
        flashPkg = f.pkg
        flashReply = f.reply
        flashMarkRead = f.markRead
        hidden = false
        _mode.value = f.mode
        _level.value = if (f.seen) IslandLevel.Compact else f.level
        // "Keep notifications until I act": a real notification (it has a source app) stays on the
        // island with no timer. It goes away only by tap / swipe up / Mark as read / reply.
        // Internal banners (Sent, Time's up, charging...) keep their normal time.
        val sticky = IslandSettings.stickyNotif.value && f.mode is IslandMode.Notification && f.pkg != null
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
            _level.value = IslandLevel.Compact
            delay(350)
            afterFlash()
        }
    }

    /** The banner is gone: show the next waiting one, or go back to the normal island. */
    private fun afterFlash() {
        val next = flashQueue.removeFirstOrNull()
        _queued.value = flashQueue.size
        if (next != null) startFlash(next) else {
            curFlash = null
            _mode.value = base()
        }
    }

    private fun enqueue(f: Flash) {
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

    private fun endFlash() {
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
    fun markRead() {
        runCatching { flashMarkRead?.send() }
        _replying.value = false
        endFlash()
    }

    /** "Reply" button: keeps the card open (no auto-collapse) and shows the text box + keyboard. */
    fun startReply() {
        if (flashReply == null) return
        stickyFlag = false
        flashJob?.cancel()          // stop the 4.5 s auto-hide while the user types
        _replying.value = true
    }

    fun cancelReply() {
        if (!_replying.value) return
        _replying.value = false
        endFlash()
    }

    /** Sends [text] through the notification's RemoteInput, exactly like typing in the shade. */
    fun sendReply(text: String) {
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
            flash(IslandMode.Notification("Sent", "Reply sent", text), 1800)
        } else {
            endFlash()
        }
    }

    /* ---------------- timer ---------------- */

    // Phone ke Clock app ka timer (uski notification se padha hua)
    private var extKey: String? = null
    private var extEnd = 0L            // elapsedRealtime jab timer 0 hoga
    private var extTotal = 0
    private var extBeat = 0L           // Clock app ne notification last kab update ki
    private var extNeedsBeat = false   // text-based timer: update ruka = paused

    fun startTimer(seconds: Int) {
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
            flash(IslandMode.Notification("Timer", "Time's up!", "$seconds sec complete"))
        }
    }

    fun stopTimer() {
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
    fun syncClockTimer(key: String, endAt: Long, needsBeat: Boolean = false) {
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
            flash(IslandMode.Notification("Timer", "Time's up!", "Timer complete"))
        }
    }

    /** Clock ki notification hati / pause hui. Khatam hone ke paas thi to "Time's up". */
    fun clearClockTimer(key: String? = null) {
        val k = extKey ?: return
        if (key != null && key != k) return
        val left = extEnd - SystemClock.elapsedRealtime()
        dropClockTimer()
        if (left in 0L..2000L) flash(IslandMode.Notification("Timer", "Time's up!", "Timer complete"))
    }

    /** Settings changed (Timer off / Clock app blocked): remove the Clock timer without a "Time's up" banner. */
    fun dropClockTimerSilently(key: String? = null) {
        val k = extKey ?: return
        if (key != null && key != k) return
        dropClockTimer()
    }

    /** The chronometer of the Clock notification stopped (timer paused). Text timers use the beat check instead. */
    fun pauseClockTimer(key: String) {
        if (key == extKey && !extNeedsBeat) clearClockTimer(key)
    }

    private fun dropClockTimer() {
        timerJob?.cancel()
        timerJob = null
        extKey = null
        timerMode = null
        if (_mode.value is IslandMode.Timer) _level.value = IslandLevel.Compact
        refresh()
    }

    /* ---------------- live activities (navigation, delivery, rides) ---------------- */

    /** Shows / updates a live activity. [key] = the source notification (used to remove it later). */
    fun setLive(m: IslandMode.Live, key: String, open: PendingIntent?, pkg: String?) {
        val old = liveMode
        if (old == m && liveKey == key) return
        // A swipe-up hides it until a different app starts its own live activity
        if (old == null || old.app != m.app) hidden = false
        liveMode = m
        liveKey = key
        liveOpen = open
        livePkg = pkg
        refresh()
    }

    /** [key] = the notification that was removed. A stale removal must not kill a newer live activity. */
    fun clearLive(key: String? = null) {
        if (liveMode == null) return
        if (key != null && liveKey != null && key != liveKey) return
        liveMode = null
        liveKey = null
        liveOpen = null
        livePkg = null
        if (_mode.value is IslandMode.Live) _level.value = IslandLevel.Compact
        refresh()
    }

    /* ---------------- downloads ---------------- */

    /** Download / progress notification started or updated. [key] = the source notification. */
    fun setDownload(m: IslandMode.Live, key: String, open: PendingIntent?, pkg: String?) {
        val old = downloads[key]
        if (old != null && old.mode == m) return          // nothing changed
        if (old == null) hidden = false                   // a new download shows again after a swipe-up
        downloads[key] = Dl(m, open, pkg)                 // replacing keeps the order
        syncDownload()
    }

    /** [key] = the notification that was removed. null = drop all downloads. */
    fun clearDownload(key: String? = null) {
        if (key == null) downloads.clear()
        else if (downloads.remove(key) == null) return
        syncDownload()
    }

    private fun syncDownload() {
        val first = downloads.values.firstOrNull()
        dlMode = first?.mode?.let {
            // More than one download running: show "(+N)"
            if (downloads.size > 1) it.copy(text = "${it.text}  (+${downloads.size - 1})") else it
        }
        val cur = _mode.value
        if (dlMode == null && cur is IslandMode.Live && cur.kind == LiveKind.Download) {
            _level.value = IslandLevel.Compact
        }
        refresh()
    }

    /* ---------------- auto-collapse + swipe skip ---------------- */

    private var collapseJob: Job? = null

    /** Swipe left = next track, swipe right = previous (only while music is on the island). */
    fun skip(dir: Int) {
        if (_mode.value !is IslandMode.Media) return
        val t = media?.transportControls ?: return
        if (dir > 0) t.skipToNext() else t.skipToPrevious()
    }

    init {
        // An expanded / large card goes back to the small pill after a few seconds,
        // so a card can never stay open and make the island look huge.
        scope.launch {
            _level.collect { lvl ->
                collapseJob?.cancel()
                if (lvl != IslandLevel.Compact) {
                    collapseJob = launch {
                        val sec = IslandSettings.autoCollapseSec.value
                        if (sec <= 0) return@launch
                        delay(sec * 1000L)
                        // A sticky notification card (opened by tapping its pill) also collapses back to the pill
                        if ((flashJob?.isActive != true || stickyHeld) && callMode == null && !_replying.value) {
                            _level.value = IslandLevel.Compact
                            if (stickyHeld) onStickyCollapsed()
                        }
                    }
                }
            }
        }
    }
}

