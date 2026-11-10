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

// SPLITLO  (Calls: dialer / call detection, handleCall.)

/**
 * Whole-word match on an action title. A plain substring check made "end" match "Send message",
 * "hang" match "Change", etc., so a missed-call notification could become a live call and
 * Decline could be wired to the wrong action.
 */
internal fun IslandListener.titleHasWord(title: CharSequence?, words: Set<String>): Boolean {
    val tokens = title?.toString()?.lowercase().orEmpty().split(NON_LETTER).filter { it.isNotEmpty() }
    return tokens.any { it in words }
}

/**
 * Many dialers (Oplus / Xiaomi / Samsung / Google) do not always set CATEGORY_CALL,
 * so also match the CallStyle template, the callType extra and the known dialer packages.
 */
/** Known dialers + the phone's real default dialer + any package that looks like a dialer / in-call UI. */
internal fun IslandListener.isDialerPkg(pkg: String): Boolean {
    if (pkg in DIALERS) return true
    val p = pkg.lowercase()
    if (p.contains("incallui") || p.contains("dialer") || p.contains("telecom")) return true
    val def = defaultDialer ?: runCatching {
        getSystemService(TelecomManager::class.java)?.defaultDialerPackage
    }.getOrNull().also { defaultDialer = it }
    return def != null && pkg == def
}

internal fun IslandListener.isCall(sbn: StatusBarNotification): Boolean {
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
internal fun IslandListener.callAudioLive(): Boolean = runCatching {
    val m = getSystemService(android.media.AudioManager::class.java)?.mode
    m == android.media.AudioManager.MODE_RINGTONE || m == android.media.AudioManager.MODE_IN_CALL
}.getOrDefault(false)

internal fun IslandListener.ringingNow(): Boolean = runCatching {
    getSystemService(android.media.AudioManager::class.java)?.mode == android.media.AudioManager.MODE_RINGTONE
}.getOrDefault(false)

/* ---------------- Clock app timer ---------------- */

internal fun IslandListener.handleCall(sbn: StatusBarNotification) {
    if (!IslandSettings.isOn(Trigger.Calls)) return
    val n = sbn.notification
    val ex = n.extras
    val name = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().ifBlank { "Call" }

    val actions = n.actions?.toList().orEmpty()
    fun find(vararg words: String) = actions.firstOrNull { a ->
        titleHasWord(a.title, words.toSet())
    }?.actionIntent

    // CALLCTL: pehle notification ke exact extras (CallStyle, bhasha se bandhe nahi), phir button ka naam (English + Hindi).
    // Position se andaza nahi: naam na mile to Answer chhupta hai, Cut TelecomManager se chalta hai.
    @Suppress("DEPRECATION")
    fun extraPi(key: String): android.app.PendingIntent? =
        runCatching { ex.getParcelable<android.app.PendingIntent>(key) }.getOrNull()
    val answer = extraPi("android.answerIntent")
        ?: find("answer", "accept", "pick", "उठाएं", "उठाएँ", "उठाओ", "जवाब", "स्वीकार")
    val decline = extraPi("android.declineIntent") ?: extraPi("android.hangUpIntent")
        ?: find(
            "decline", "reject", "refuse", "hang", "end", "cut", "disconnect", "dismiss",
            "अस्वीकार", "अस्वीकृत", "काटें", "काटो", "रखें", "समाप्त",
        )
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

    val speaker = find("speaker", "speakerphone", "स्पीकर")   // CALLCTL
    val mute = find("mute", "unmute", "म्यूट", "अनम्यूट")
    // POLISHKIT: baat-cheet ka asli start (notification chronometer). Listener reconnect par timer 00:00 se na ho.
    val startedAt: Long? = if (!incoming && counting) {
        val age = System.currentTimeMillis() - n.`when`
        if (age in 0L..(12 * 3600_000L)) SystemClock.elapsedRealtime() - age else null
    } else null
    // CALLCTL: notification me Answer na ho to bhi TelecomManager se utha sakte hain (permission ho to).
    val telecomOk = Build.VERSION.SDK_INT >= 26 &&
        checkSelfPermission("android.permission.ANSWER_PHONE_CALLS") ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
    IslandController.setCall(
        name, incoming, answer, decline, key = sbn.key, speaker = speaker, mute = mute,
        startedAt = startedAt,
        canAnswer = answer != null || telecomOk,
        open = n.contentIntent,
    )
}
