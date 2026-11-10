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

// SPLITCTRL  (core: state + flows + refresh. Baaki functions Controller*.kt me)

/** Single source of truth. The services, the listener and the UI all talk through this. */
object IslandController {
    /** How long a paused song stays on the island before it hides. */
    internal const val PAUSE_HIDE_MS = 10_000L

    /** Notifications waiting behind the banner that is on screen (oldest is dropped when full). */
    internal const val MAX_QUEUE = 5

    internal val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            kotlinx.coroutines.CoroutineExceptionHandler { _, e ->   // SMOOTHKIT: coroutine ke andar error = crash nahi
                android.util.Log.w("Islandify", "controller coroutine failed", e)
            }
    )

    internal val _mode = MutableStateFlow<IslandMode>(IslandMode.Idle)
    val mode = _mode.asStateFlow()

    internal val _level = MutableStateFlow(IslandLevel.Compact)
    val level = _level.asStateFlow()

    var media: MediaController? = null

    internal var appCtx: Context? = null
    internal var flashOpen: PendingIntent? = null   // intent opened when the notification card is tapped
    internal var flashPkg: String? = null           // source app of the notification (fallback launch)
    internal var flashReply: android.app.Notification.Action? = null   // inline reply action (has RemoteInput)
    internal var flashMarkRead: PendingIntent? = null                    // "Mark as read" action

    /** True while the user is typing a reply inside the island (the window becomes focusable for the keyboard). */
    internal val _replying = MutableStateFlow(false)
    val replying = _replying.asStateFlow()

    fun attach(ctx: Context) { appCtx = ctx.applicationContext }

    internal var callKey: String? = null
    internal var callAnswer: PendingIntent? = null
    internal var callDecline: PendingIntent? = null
    internal var callSpeaker: PendingIntent? = null
    internal var callMute: PendingIntent? = null
    internal var callOpen: PendingIntent? = null   // CALLCTL: dialer ki call screen (contentIntent)

    internal var mediaMode: IslandMode.Media? = null
    internal var timerMode: IslandMode.Timer? = null
    internal var callMode: IslandMode.Call? = null
    internal var liveMode: IslandMode.Live? = null
    internal var liveKey: String? = null
    internal var liveOpen: PendingIntent? = null
    internal var livePkg: String? = null

    // Map / ride / order: jab naya live aaye, purana bubble me park hota hai (dono safe)
    internal class Parked(val mode: IslandMode.Live, val open: PendingIntent?, val pkg: String?)
    internal val parked = LinkedHashMap<String, Parked>()
    internal val dismissedLive = HashSet<String>()   // bubble se swipe kiye hue live: update aaye to bhi wapas na aaye

    // BUTTERCACHE: app icon har live / bubble par dobara decode na ho
    internal val appIconCache = android.util.LruCache<String, Bitmap>(16)

    // Downloads / progress-bar notifications. Insertion order = the first running download is shown.
    internal class Dl(val mode: IslandMode.Live, val open: PendingIntent?, val pkg: String?)
    internal val downloads = LinkedHashMap<String, Dl>()
    internal var dlMode: IslandMode.Live? = null

    // DLBUBBLE: pill par sirf ek download (pehla). Baaki har download apne bubble me, progress ke saath.
    internal val dlBubbleIds = HashSet<String>()
    internal var dlBubbleSig = ""

    internal var hidden = false
    internal var flashJob: Job? = null
    // BANNERFIX: dedup state — banner stuck fix (same-mode / same-key / debounce)
    internal var flashDedupeKey: String? = null
    internal var flashDedupeKeyMs: Long = 0L
    internal var flashDedupeMode: String? = null
    internal var flashDedupeModeMs: Long = 0L
    internal val flashDebounceHandler = android.os.Handler(android.os.Looper.getMainLooper())
    internal val flashDebounceMap = HashMap<String, Runnable>()

    /** A banner (notification / charging / device...) that is on screen or waiting for its turn. */
    internal data class Flash(
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
    internal var curFlash: Flash? = null

    /**
     * "Keep notifications until dismissed" is active for the banner on screen: after its show time it
     * shrinks to the small pill and STAYS there (no timer, so it is still there when the phone is
     * unlocked later) until tap / swipe up / Mark as read / reply / removed from the shade.
     */
    internal var stickyFlag = false
    internal val stickyHeld get() = stickyFlag && flashJob?.isActive == true

    internal val flashQueue = ArrayDeque<Flash>()

    /** Real width (px) of the island pill on screen. The bubble reads it to keep a gap. 0 = pill not shown. */
    internal val _pillWidthPx = MutableStateFlow(0)
    val pillWidthPx = _pillWidthPx.asStateFlow()
    internal var pillOwner: Any? = null

    fun reportPillWidth(owner: Any, px: Int) {
        if (px < 120) return            // the invisible pre-warm copy is only a few px wide
        pillOwner = owner
        _pillWidthPx.value = px
    }

    fun clearPillWidth(owner: Any) {
        if (pillOwner === owner) {
            pillOwner = null
            _pillWidthPx.value = 0
        }
    }

    /** How many banners are waiting. The island shows it as a "+N" badge. */
    internal val _queued = MutableStateFlow(0)
    val queued = _queued.asStateFlow()

    /**
     * Music that plays next to a timer or navigation. The island shows it as a small second part
     * (compact: art + waveform, card: a mini player). Only used while the main mode is Timer / Live.
     */
    internal val _secondary = MutableStateFlow<IslandMode.Media?>(null)
    val secondary = _secondary.asStateFlow()
    internal var timerJob: Job? = null

    // Music that has been paused for a while is not an "activity": the island hides until it plays again
    internal var pauseJob: Job? = null
    internal var pausedHide = false

    internal fun base(): IslandMode {
        callMode?.let { return it }
        if (hidden) return IslandMode.Idle
        // Flashlight pill: the island stays while the torch is on (Customize > More island options)
        if (_torchLive.value && IslandSettings.flashlightPill.value && IslandSettings.isOn(Trigger.Toggles)) {
            return IslandMode.Toggle(ToggleKind.Flashlight, true)
        }
        return IslandSettings.baseOrder.value.firstNotNullOfOrNull<String, IslandMode> { k ->
            when (k) {
                "timer" -> timerMode
                "live" -> liveMode
                "download" -> dlMode
                "media" -> if (pausedHide) null else mediaMode
                else -> null
            }
        } ?: IslandMode.Idle
    }

    internal fun refresh() {
        syncDownloadBubbles()   // DLBUBBLE
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

    internal var torchOn = false
    internal var torchWatching = false

    internal val _torchLive = MutableStateFlow(false)
    val torchIsOn: Boolean get() = _torchLive.value   // EXTRAKIT: camera banner torch par galat na dikhe

    /** Current torch brightness step (1..torchMaxLevel). Only meaningful on Android 13+. */
    internal val _torchLevel = MutableStateFlow(1)
    val torchLevel = _torchLevel.asStateFlow()

    internal var torchCamCache: Pair<android.hardware.camera2.CameraManager, String>? = null   // SMOOTHKIT

    /** Highest brightness step. 1 = the phone cannot change the torch brightness (needs Android 13+). */
    internal var torchMaxCache = 0   // SMOOTHKIT

    internal val _speakerOn = MutableStateFlow(false)
    val speakerOn = _speakerOn.asStateFlow()
    internal val _muted = MutableStateFlow(false)
    val muted = _muted.asStateFlow()

    // Phone ke Clock app ka timer (uski notification se padha hua)
    internal var extKey: String? = null
    internal var extEnd = 0L            // elapsedRealtime jab timer 0 hoga
    internal var extTotal = 0
    internal var extBeat = 0L           // Clock app ne notification last kab update ki
    internal var extNeedsBeat = false   // text-based timer: update ruka = paused

    internal var collapseJob: Job? = null

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
