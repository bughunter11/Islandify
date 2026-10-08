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

/** Shows notifications + calls, and tracks the active media session. */
class IslandListener : NotificationListenerService() {

    private var sessions: List<MediaController> = emptyList()
    private var controller: MediaController? = null
    private lateinit var msm: MediaSessionManager
    private val cn by lazy { ComponentName(this, IslandListener::class.java) }

    // So we do not re-extract the color from the art on every metadata / state change
    private var lastArt: Bitmap? = null
    private var lastAccent: Int? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var settingsJob: Job? = null

    // key -> signature of the last notification shown (same notification re-posted = skip)
    private val shown = HashMap<String, Int>()

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = reselect()
        override fun onPlaybackStateChanged(state: PlaybackState?) = reselect()
        override fun onSessionDestroyed() = refreshSessions()
    }

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { setSessions(it) }

    private fun PlaybackState?.live(): Boolean {
        val s = this?.state ?: return false
        return s == PlaybackState.STATE_PLAYING ||
            s == PlaybackState.STATE_BUFFERING ||
            s == PlaybackState.STATE_CONNECTING
    }

    private fun refreshSessions() =
        setSessions(runCatching { msm.getActiveSessions(cn) }.getOrNull())

    private fun setSessions(list: List<MediaController>?) {
        sessions.forEach { it.unregisterCallback(callback) }
        sessions = list.orEmpty()
        sessions.forEach { it.registerCallback(callback) }
        reselect()
    }

    /** Playing session first, apps in the blocked list never. */
    private fun reselect() {
        val ok = sessions.filter { it.packageName !in IslandSettings.blockedApps.value }
        val best = ok.firstOrNull { it.playbackState.live() } ?: ok.firstOrNull()
        if (best !== controller) {
            controller = best
            IslandController.media = best
        }
        pushMedia()
    }

    override fun onCreate() {
        super.onCreate()
        IslandSettings.init(this)
        IslandController.attach(this)
    }

    override fun onListenerConnected() {
        // A call that is already ringing when the listener (re)connects
        runCatching { activeNotifications?.firstOrNull { isCall(it) }?.let { handleCall(it) } }
        // Navigation / delivery already running when the listener (re)connects
        runCatching {
            activeNotifications
                ?.firstOrNull { liveKind(it) != null && it.packageName !in IslandSettings.blockedApps.value }
                ?.let { handleLive(it) }
        }
        // Clock app ka timer jo already chal raha ho
        runCatching {
            activeNotifications
                ?.filter { it.packageName !in IslandSettings.blockedApps.value }
                ?.forEach { handleClockTimer(it) }
        }
        // Download jo already chal raha ho
        runCatching {
            activeNotifications
                ?.filter { it.packageName !in IslandSettings.blockedApps.value && it.packageName != packageName }
                ?.forEach { handleDownload(it) }
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
    private fun applySettings() {
        val blocked = IslandSettings.blockedApps.value
        val active = runCatching { activeNotifications?.toList() }.getOrNull().orEmpty()

        // Music: pushMedia() handles the Media trigger, reselect() the blocked apps
        reselect()

        // Calls
        if (!IslandSettings.isOn(Trigger.Calls)) IslandController.clearCall()
        else active.firstOrNull { isCall(it) }?.let { handleCall(it) }

        // Navigation / delivery / rides
        val live = active.firstOrNull { sbn ->
            val kind = liveKind(sbn) ?: return@firstOrNull false
            val trigger = if (kind == LiveKind.Navigation) Trigger.Navigation else Trigger.Tracking
            IslandSettings.isOn(trigger) && sbn.packageName !in blocked
        }
        if (live != null) handleLive(live) else IslandController.clearLive()

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
        settingsJob?.cancel()
        if (::msm.isInitialized) runCatching { msm.removeOnActiveSessionsChangedListener(sessionsListener) }
        setSessions(null)
        // Ask Android to bind us again (after reinstall / ROM killing the listener)
        runCatching { requestRebind(ComponentName(this, IslandListener::class.java)) }
    }

    private fun pushMedia() {
        val c = controller
        val md = c?.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (c == null || md == null || title == null || !IslandSettings.isOn(Trigger.Media)) {
            IslandController.setMedia(null); return
        }
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        val art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
        if (art !== lastArt) {
            lastArt = art
            lastAccent = art?.let { dominantColor(it) }
        }
        val ps = c.playbackState
        IslandController.setMedia(
            IslandMode.Media(
                title = title,
                artist = artist,
                playing = ps.live(),
                art = art,
                accent = lastAccent,
                durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0),
                positionMs = (ps?.position ?: 0L).coerceAtLeast(0),
                updatedAt = ps?.lastPositionUpdateTime ?: 0L,
                speed = ps?.playbackSpeed ?: 1f,
            )
        )
    }

    private val labelCache = HashMap<String, String>()

    /**
     * App name for a notification.
     * Android 11+ hides apps that are not in <queries>, so getApplicationInfo can fail.
     * The notification itself carries the sender's ApplicationInfo ("android.appInfo"): use that as a fallback.
     */
    @Suppress("DEPRECATION")
    private fun appLabel(sbn: StatusBarNotification): String {
        labelCache[sbn.packageName]?.let { return it }
        val pm = packageManager
        val label = runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrNull()
            ?: runCatching {
                sbn.notification.extras.getParcelable<ApplicationInfo>("android.appInfo")
                    ?.loadLabel(pm)?.toString()
            }.getOrNull()
        if (label.isNullOrBlank()) return sbn.packageName
        labelCache[sbn.packageName] = label
        return label
    }

    /** If EXTRA_TEXT is empty, use the last chat message (MessagingStyle) or the big text. */
    @Suppress("DEPRECATION")
    private fun bodyOf(n: Notification): String {
        val ex = n.extras
        ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
        val arr = ex.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (arr != null) {
            Notification.MessagingStyle.Message.getMessagesFromBundleArray(arr)
                .lastOrNull()?.text?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification

        // Calls first: even if the dialer is in the blocked list, a call must show
        if (isCall(sbn)) {
            handleCall(sbn)
            return
        }

        if (sbn.packageName in IslandSettings.blockedApps.value) return

        // Clock app ka timer
        if (handleClockTimer(sbn)) return

        // Navigation / delivery / ride tracking: their ongoing notification becomes a live activity
        if (handleLive(sbn)) return

        // Download / upload / any progress-bar notification: live progress on the island
        if (handleDownload(sbn)) return

        if (!IslandSettings.isOn(Trigger.Notifications)) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.category == Notification.CATEGORY_TRANSPORT) return   // music has its own UI

        // "Quiet" switch ON = silent, ongoing, service and progress notifications show too
        val all = IslandSettings.isOn(Trigger.Quiet)
        val ongoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0
        if (!all) {
            if (ongoing) return
            if (n.category == Notification.CATEGORY_PROGRESS ||
                n.category == Notification.CATEGORY_SERVICE
            ) return
        }

        // Silent (LOW / MIN importance) channels
        var quiet = false
        val rank = NotificationListenerService.Ranking()
        if (currentRanking.getRanking(sbn.key, rank) &&
            rank.importance < NotificationManager.IMPORTANCE_DEFAULT
        ) {
            if (!all) return
            quiet = true
        }

        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = bodyOf(n)
        if (title.isBlank() && text.isBlank()) return

        // Ongoing notifications must not pop up on every update: only the first time
        if (ongoing && shown.containsKey(sbn.key)) return

        // The same notification posted again (count update etc.): do not show it twice
        val sig = "$title|$text|${n.`when`}".hashCode()
        if (shown[sbn.key] == sig) return
        shown[sbn.key] = sig

        val app = appLabel(sbn)

        // Lock screen: never show private content, skip secret notifications completely
        val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        if (locked && n.visibility == Notification.VISIBILITY_SECRET) return
        val hide = locked && n.visibility != Notification.VISIBILITY_PUBLIC

        // Inline reply (RemoteInput) and "Mark as read" actions, e.g. Messages / WhatsApp / Telegram
        val acts: List<Notification.Action> =
            if (hide) emptyList() else n.actions.orEmpty().toList()
        val reply = acts.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }
        val markRead = acts.firstOrNull { a ->
            val t = a.title?.toString()?.lowercase().orEmpty()
            (Build.VERSION.SDK_INT >= 28 &&
                a.semanticAction == Notification.Action.SEMANTIC_ACTION_MARK_AS_READ) ||
                t.contains("mark as read") || t == "mark read"
        }?.actionIntent

        IslandController.flash(
            IslandMode.Notification(
                app,
                if (hide) app else title,
                if (hide) "New notification" else text,
                canReply = reply != null,
                canMarkRead = markRead != null,
            ),
            // Cards with buttons stay a bit longer so there is time to tap them
            durationMs = when {
                reply != null || markRead != null -> 8000L
                quiet || ongoing -> 3000L     // silent / ongoing: shorter pop-up
                else -> 4500L
            },
            open = n.contentIntent,
            pkg = sbn.packageName,
            reply = reply,
            markRead = markRead,
            key = sbn.key,
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (isCall(sbn)) IslandController.clearCall(sbn.key)
        IslandController.clearLive(sbn.key)
        IslandController.clearClockTimer(sbn.key)
        IslandController.clearDownload(sbn.key)
        IslandController.notificationRemoved(sbn.key)
        textTimers.remove(sbn.key)
        shown.remove(sbn.key)
    }

    /**
     * Whole-word match on an action title. A plain substring check made "end" match "Send message",
     * "hang" match "Change", etc., so a missed-call notification could become a live call and
     * Decline could be wired to the wrong action.
     */
    private fun titleHasWord(title: CharSequence?, words: Set<String>): Boolean {
        val tokens = title?.toString()?.lowercase().orEmpty().split(NON_LETTER).filter { it.isNotEmpty() }
        return tokens.any { it in words }
    }

    /**
     * Many dialers (Oplus / Xiaomi / Samsung / Google) do not always set CATEGORY_CALL,
     * so also match the CallStyle template, the callType extra and the known dialer packages.
     */
    /** Known dialers + the phone's real default dialer + any package that looks like a dialer / in-call UI. */
    private fun isDialerPkg(pkg: String): Boolean {
        if (pkg in DIALERS) return true
        val p = pkg.lowercase()
        if (p.contains("incallui") || p.contains("dialer") || p.contains("telecom")) return true
        val def = defaultDialer ?: runCatching {
            getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        }.getOrNull().also { defaultDialer = it }
        return def != null && pkg == def
    }

    private var defaultDialer: String? = null

    private fun isCall(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        if (n.category == Notification.CATEGORY_CALL) return true
        val ex = n.extras
        if (ex.containsKey("android.callType")) return true
        if (ex.getString(Notification.EXTRA_TEMPLATE)?.contains("CallStyle") == true) return true
        // Dialer package: only live call notifications (ringing / ongoing), never "missed call" etc.
        if (isDialerPkg(sbn.packageName)) {
            val hasCallAction = n.actions.orEmpty().any { a ->
                titleHasWord(a.title, CALL_ACTION_WORDS)
            }
            // The phone is ringing / in a call right now. Some dialers (e.g. ColorOS) post a custom-view
            // call notification with no CATEGORY_CALL, no callType and no titled actions.
            return sbn.isOngoing || n.fullScreenIntent != null || hasCallAction || callAudioLive()
        }
        return false
    }

    /** True while the phone is ringing or in a call (AudioManager mode). Needs no permission. */
    private fun callAudioLive(): Boolean = runCatching {
        val m = getSystemService(android.media.AudioManager::class.java)?.mode
        m == android.media.AudioManager.MODE_RINGTONE || m == android.media.AudioManager.MODE_IN_CALL
    }.getOrDefault(false)

    private fun ringingNow(): Boolean = runCatching {
        getSystemService(android.media.AudioManager::class.java)?.mode == android.media.AudioManager.MODE_RINGTONE
    }.getOrDefault(false)

    /* ---------------- Clock app timer ---------------- */

    private val textTimers = HashMap<String, Pair<Int, Long>>()   // key -> (seconds dikhe, kab)

    private fun handleClockTimer(sbn: StatusBarNotification): Boolean {
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

    /** Only ONGOING notifications of these apps count; their normal alerts take the usual path. */
    private fun liveKind(sbn: StatusBarNotification): LiveKind? {
        if (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT == 0) return null
        return when (sbn.packageName) {
            in NAV_APPS -> LiveKind.Navigation
            in TRACK_APPS -> LiveKind.Tracking
            else -> null
        }
    }

    /** Returns true when the notification was a live-activity one (shown or deliberately ignored). */
    private fun handleLive(sbn: StatusBarNotification): Boolean {
        val kind = liveKind(sbn) ?: return false
        val trigger = if (kind == LiveKind.Navigation) Trigger.Navigation else Trigger.Tracking
        if (!IslandSettings.isOn(trigger)) return true

        val ex = sbn.notification.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = listOf(
            ex.getCharSequence(Notification.EXTRA_TEXT),
            ex.getCharSequence(Notification.EXTRA_SUB_TEXT),
            ex.getCharSequence(Notification.EXTRA_BIG_TEXT),
        ).mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
            .distinct()
            .joinToString(", ")
        if (title.isBlank() && text.isBlank()) return true

        val app = appLabel(sbn)

        IslandController.setLive(
            IslandMode.Live(kind, app, title, text),
            key = sbn.key,
            open = sbn.notification.contentIntent,
            pkg = sbn.packageName,
        )
        return true
    }

    /* ---------------- downloads / progress ---------------- */

    /**
     * True = this was a progress-bar notification (shown, or deliberately ignored).
     * False = no progress bar: continue as a normal notification. That is how the
     * "Download complete" notification becomes a normal pop-up.
     */
    private fun handleDownload(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        val ex = n.extras
        val max = ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val indeterminate = ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)

        // Progress bar gone = download finished: remove it from the island
        if (max <= 0 && !indeterminate) {
            IslandController.clearDownload(sbn.key)
            return false
        }
        if (n.category == Notification.CATEGORY_TRANSPORT || n.category == Notification.CATEGORY_CALL) return false
        if (!IslandSettings.isOn(Trigger.Downloads)) return true

        val percent = if (!indeterminate && max > 0) {
            (ex.getInt(Notification.EXTRA_PROGRESS, 0) * 100L / max).toInt().coerceIn(0, 100)
        } else -1

        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = bodyOf(n)
        val text = body.ifBlank { if (percent >= 0) "$percent%" else "" }
        val app = appLabel(sbn)
        if (title.isBlank() && text.isBlank()) return true

        IslandController.setDownload(
            IslandMode.Live(LiveKind.Download, app, title, text, percent),
            key = sbn.key,
            open = n.contentIntent,
            pkg = sbn.packageName,
        )
        return true
    }

    private fun handleCall(sbn: StatusBarNotification) {
        if (!IslandSettings.isOn(Trigger.Calls)) return
        val n = sbn.notification
        val ex = n.extras
        val name = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().ifBlank { "Call" }

        val actions = n.actions?.toList().orEmpty()
        fun find(vararg words: String) = actions.firstOrNull { a ->
            titleHasWord(a.title, words.toSet())
        }?.actionIntent

        // No guessing by position: if the title does not match, the button is hidden instead
        val answer = find("answer", "accept", "pick")
        val decline = find("decline", "reject", "hang", "end", "cut", "disconnect", "dismiss")
            ?: if (answer != null) actions.map { it.actionIntent }.firstOrNull { it != answer } else null

        // "android.callType": 1 = incoming, 2 = ongoing / outgoing, 3 = screening (API 31+ CallStyle)
        val type = ex.getInt("android.callType", 0)
        val counting = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
        val incoming = when (type) {
            1 -> true
            2 -> false
            // Older dialers: an Answer button or a full-screen ring = incoming.
            // While dialing out neither exists, so an outgoing call is no longer "Incoming".
            else -> answer != null || (n.fullScreenIntent != null && !counting) || (ringingNow() && !counting)
        }

        IslandController.setCall(name, incoming, answer, decline, key = sbn.key)
    }

    private companion object {
        val NON_LETTER = Regex("""\P{L}+""")
        val CALL_ACTION_WORDS = setOf("answer", "accept", "decline", "reject", "hang", "hangup", "end")
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
