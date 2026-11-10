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

// SPLITLO  (core: state + overrides + receivers; baaki functions alag files me)

/** Shows notifications + calls, and tracks the active media session. */
class IslandListener : NotificationListenerService() {
    internal var sessions: List<MediaController> = emptyList()
    internal var controller: MediaController? = null
    internal lateinit var msm: MediaSessionManager
    internal val cn by lazy { ComponentName(this, IslandListener::class.java) }

    // So we do not re-extract the color from the art on every metadata / state change
    internal var lastArt: Bitmap? = null
    internal var lastAccent: Int? = null

    internal val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            kotlinx.coroutines.CoroutineExceptionHandler { _, e ->   // SMOOTHKIT
                android.util.Log.w("Islandify", "listener coroutine failed", e)
            }
    )
    internal var settingsJob: Job? = null

    // key -> signature of the last notification shown (same notification re-posted = skip)
    internal val shown = HashMap<String, Int>()
    internal val spamTimestamps = HashMap<String, Long>()   // SILENTFILTER
    internal val postBuf = ArrayList<StatusBarNotification>()   // MSGORDER: chat notifications sort hone tak yahin
    internal var postFlushJob: Job? = null

    internal val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = reselect()
        override fun onPlaybackStateChanged(state: PlaybackState?) = reselect()
        override fun onSessionDestroyed() = refreshSessions()
    }

    internal val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { setSessions(it) }

    internal fun PlaybackState?.live(): Boolean {
        val s = this?.state ?: return false
        return s == PlaybackState.STATE_PLAYING ||
            s == PlaybackState.STATE_BUFFERING ||
            s == PlaybackState.STATE_CONNECTING
    }

    override fun onCreate() {
        super.onCreate()
        IslandSettings.init(this)
        IslandController.attach(this)
    }

    override fun onListenerConnected() {
        instance = this
        // SMOOTHKIT: activeNotifications (binder call) sirf ek baar, chaaron kaam ke liye
        val active = runCatching { activeNotifications?.toList() }.getOrNull().orEmpty()
        val blockedNow = IslandSettings.blockedApps.value
        // A call that is already ringing when the listener (re)connects
        runCatching { active.firstOrNull { isCall(it) }?.let { handleCall(it) } }
        // Navigation / delivery already running when the listener (re)connects
        runCatching {
            active.firstOrNull { liveKind(it) != null && it.packageName !in blockedNow }?.let { handleLive(it) }
        }
        // Clock app ka timer jo already chal raha ho
        runCatching {
            active.filter { it.packageName !in blockedNow }.forEach { handleClockTimer(it) }
        }
        // Download jo already chal raha ho
        runCatching {
            active.filter { it.packageName !in blockedNow && it.packageName != packageName }
                .forEach { handleDownload(it) }
        }
        msm = getSystemService(MediaSessionManager::class.java)
        runCatching { msm.addOnActiveSessionsChangedListener(sessionsListener, cn) }
        refreshSessions()

        // Settings take effect right away (triggers + blocked apps), not on the next media event
        settingsJob?.cancel()
        settingsJob = scope.launch {
            combine(IslandSettings.triggers, IslandSettings.blockedApps) { t, b -> t to b }
                .drop(1)   // the current state was already applied above
                .collect { applySettings() }
        }
    }

    /** Re-evaluates everything that depends on triggers / blockedApps against what is active right now. */
    internal fun applySettings() {
        val blocked = IslandSettings.blockedApps.value
        val active = runCatching { activeNotifications?.toList() }.getOrNull().orEmpty()

        // Music: pushMedia() handles the Media trigger, reselect() the blocked apps
        reselect()

        // Calls
        if (!IslandSettings.isOn(Trigger.Calls)) IslandController.clearCall()
        else active.firstOrNull { isCall(it) }?.let { handleCall(it) }

        // Navigation / delivery / rides
        val lives = active.filter { sbn ->
            val kind = liveKind(sbn) ?: return@filter false
            val trigger = if (kind == LiveKind.Navigation) Trigger.Navigation else Trigger.Tracking
            IslandSettings.isOn(trigger) && sbn.packageName !in blocked
        }
        if (lives.isNotEmpty()) lives.forEach { handleLive(it, announce = false) } else IslandController.clearLive()

        // Clock timer
        if (!IslandSettings.isOn(Trigger.Timer)) {
            IslandController.dropClockTimerSilently()
        } else {
            active.forEach { sbn ->
                if (sbn.packageName in blocked) IslandController.dropClockTimerSilently(sbn.key)
                else handleClockTimer(sbn)
            }
        }

        // Downloads
        IslandController.clearDownload()
        if (IslandSettings.isOn(Trigger.Downloads)) {
            active.filter { it.packageName !in blocked && it.packageName != packageName }
                .forEach { handleDownload(it) }
        }
    }

    override fun onDestroy() {
        settingsJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        clearBuffered()   // MSGORDER
        settingsJob?.cancel()
        if (::msm.isInitialized) runCatching { msm.removeOnActiveSessionsChangedListener(sessionsListener) }
        setSessions(null)
        // Ask Android to bind us again (after reinstall / ROM killing the listener)
        runCatching { requestRebind(ComponentName(this, IslandListener::class.java)) }
    }

    internal val labelCache = HashMap<String, String>()

    /** SMOOTHKIT: ek ajeeb notification (kharab extras) poore app ko crash na kare. */
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (runCatching { isChatLike(sbn) }.getOrDefault(false)) {   // MSGORDER
            bufferPosted(sbn)
            return
        }
        runCatching { handlePosted(sbn) }
            .onFailure { android.util.Log.w("Islandify", "notification skipped: ${sbn.packageName}", it) }
    }

    /**
     * FIX: bubble gayab ho jata tha. Apps (Telegram...) notification ko cancel + re-post karte hain
     * ya chat padhne par hata dete hain -> pehle bubble bhi turant delete ho jata tha.
     * Ab bubble sirf tab jata hai jab user shade se notification swipe kare (ya bubble swipe kare).
     */
    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: NotificationListenerService.RankingMap?,
        reason: Int
    ) {
        if (BubbleStore.isDismissed(sbn.key)) {
            // KEEPBUBBLE: user ne card se message swipe karke hataya, bubble rehna chahiye
        } else if (reason == NotificationListenerService.REASON_CANCEL ||
            reason == NotificationListenerService.REASON_CANCEL_ALL ||
            reason == NotificationListenerService.REASON_CLICK) {
            BubbleStore.remove(sbn.key)
        } else if (BubbleStore.REMOVE_WHEN_READ) {
            BubbleStore.removeLater(sbn.key)
        }
        super.onNotificationRemoved(sbn, rankingMap, reason)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        dropBuffered(sbn.key)   // MSGORDER
        if (isCall(sbn)) IslandController.clearCall(sbn.key)
        IslandController.clearLive(sbn.key)
        IslandController.clearClockTimer(sbn.key)
        IslandController.clearDownload(sbn.key)
        IslandController.notificationRemoved(sbn.key)
        textTimers.remove(sbn.key)
        shown.remove(sbn.key)
    }

    internal var defaultDialer: String? = null

    internal val textTimers = HashMap<String, Pair<Int, Long>>()   // key -> (seconds dikhe, kab)

    // SMOOTHKIT: app icon har notification par dobara decode na ho (main thread lag): package ke hisaab se cache
    internal val appIconCache = android.util.LruCache<String, Bitmap>(64)

    companion object {
        @Volatile private var instance: IslandListener? = null

        /** Removes a notification from the real shade (used when its pop-up is swiped away). */
        fun cancel(key: String) {
            BubbleStore.markUserDismissed(key)   // CANCELFIX: bubble dim rahe, re-post dobara na dikhe
            runCatching { instance?.cancelVerified(key, 0) }
        }

        val NON_LETTER = Regex("""[^\p{L}\p{M}]+""")   // CALLCTL: Devanagari matras (\p{M}) token na toden
        val CALL_ACTION_WORDS = setOf(
            "answer", "accept", "decline", "reject", "hang", "hangup", "end",
            "उठाएं", "उठाएँ", "जवाब", "अस्वीकार", "काटें", "रखें", "समाप्त",   // CALLCTL
        )
        // Only real Clock apps. A name check like contains("clock") also matched any unrelated app.
        val CLOCK_APPS = setOf(
            "com.google.android.deskclock",       // Google Clock / Pixel
            "com.android.deskclock",              // AOSP, Xiaomi / MIUI / HyperOS
            "com.sec.android.app.clockpackage",   // Samsung
            "com.coloros.alarmclock",             // Oppo / Realme (ColorOS)
            "com.oplus.alarmclock",               // Oplus
            "com.oneplus.deskclock",              // OnePlus
            "com.android.BBKClock",               // Vivo / iQOO
            "com.huawei.deskclock",               // Huawei
            "com.asus.deskclock",                 // Asus
            "com.motorola.blur.alarmclock",       // Motorola
            "com.lge.clock",                      // LG
            "com.sonyericsson.organizer",         // Sony
            "com.nothing.deskclock",              // Nothing
        )
        val TIME_RE = Regex("""\b(?:(\d{1,2}):)?(\d{1,2}):(\d{2})\b""")
        val NAV_APPS = setOf(
            "com.google.android.apps.maps", "com.waze", "net.osmand", "net.osmand.plus",
        )
        val TRACK_APPS = setOf(
            "com.application.zomato", "in.swiggy.android", "com.grofers.customerapp",
            "com.zeptoconsumerapp", "com.ubercab", "com.olacabs.customer", "com.rapido.passenger",
        )
        val DIALERS = setOf(
            // AOSP / Android system / Google
            "com.android.incallui", "com.android.server.telecom", "com.android.phone",
            "com.android.dialer", "com.android.contacts", "com.android.telephony",
            "com.google.android.dialer", "com.google.android.apps.dialer",
            "com.google.android.contacts", "com.android.services.telephony",
            // Samsung
            "com.samsung.android.incallui", "com.samsung.android.dialer",
            "com.samsung.android.app.telephonyui", "com.samsung.android.contacts",
            "com.samsung.android.callassistant", "com.samsung.android.phone",
            // Xiaomi / Redmi / POCO (MIUI, HyperOS)
            "com.miui.voip", "com.xiaomi.phone", "com.miui.contacts",
            "com.miui.incallui", "com.xiaomi.simactivate.service",
            // Oppo / Realme / OnePlus (ColorOS, OxygenOS, realme UI)
            "com.oplus.dialer", "com.coloros.dialer", "com.oneplus.dialer",
            "com.oplus.incallui", "com.coloros.incallui", "com.oneplus.incallui",
            "com.oplus.telephony", "com.coloros.phone", "com.oneplus.contacts",
            "com.coloros.contacts", "com.oplus.contacts",
            // Vivo / iQOO (Funtouch, OriginOS)
            "com.vivo.dialer", "com.vivo.incallui", "com.vivo.contacts",
            "com.vivo.phone", "com.bbk.dialer", "com.bbk.incallui", "com.iqoo.dialer",
            // Huawei / Honor (EMUI, MagicOS)
            "com.huawei.contacts", "com.huawei.android.incallui", "com.huawei.phone",
            "com.hihonor.contacts", "com.hihonor.incallui", "com.hihonor.dialer",
            // Motorola / Lenovo
            "com.motorola.dialer", "com.motorola.incallui", "com.motorola.contacts",
            "com.lenovo.dialer", "com.lenovo.incallui",
            // Nothing / CMF
            "com.nothing.dialer", "com.nothing.incallui", "com.nothing.contacts",
            // Asus / Sony / LG / HTC / Nokia (HMD) / ZTE / Meizu / Tecno-Infinix-itel / Lava / Micromax
            "com.asus.contacts", "com.asus.incallui", "com.asus.dialer",
            "com.sonyericsson.android.dialer", "com.sonymobile.dialer", "com.sonyericsson.android.incallui",
            "com.lge.incallui", "com.lge.contacts", "com.lge.dialer",
            "com.htc.contacts", "com.htc.incallui",
            "com.hmdglobal.incallui", "com.hmdglobal.dialer", "com.nokia.incallui",
            "com.zte.incallui", "com.zte.dialer", "com.meizu.incallui", "com.meizu.dialer",
            "com.transsion.incallui", "com.transsion.dialer", "com.transsion.phonedialer",
            "com.transsion.contacts", "com.itel.dialer", "com.tecno.dialer", "com.infinix.dialer",
            "com.lava.dialer", "com.micromax.dialer", "com.gionee.dialer", "com.cloudminds.dialer",
            // Third-party dialers
            "com.truecaller", "com.simplemobiletools.dialer", "org.fossify.phone",
            "com.contacts.phone.dialer", "com.drupe.swd", "com.hiya.stingray",
            "com.mobile.dialer", "com.dialer.phone",
        )
    }
}
