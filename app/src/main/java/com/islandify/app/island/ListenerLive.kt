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

// SPLITLO  (Live activities + downloads.)

/** Only ONGOING notifications of these apps count; their normal alerts take the usual path. */
internal fun IslandListener.liveKind(sbn: StatusBarNotification): LiveKind? {
    if (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT == 0) return null
    return when (sbn.packageName) {
        in NAV_APPS -> LiveKind.Navigation
        in TRACK_APPS -> LiveKind.Tracking
        else -> null
    }
}

/** Returns true when the notification was a live-activity one (shown or deliberately ignored). */
internal fun IslandListener.handleLive(sbn: StatusBarNotification, announce: Boolean = true): Boolean {
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
        announce = announce,
    )
    return true
}

/* ---------------- downloads / progress ---------------- */

/**
 * True = this was a progress-bar notification (shown, or deliberately ignored).
 * False = no progress bar: continue as a normal notification. That is how the
 * "Download complete" notification becomes a normal pop-up.
 */
internal fun IslandListener.handleDownload(sbn: StatusBarNotification): Boolean {
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
