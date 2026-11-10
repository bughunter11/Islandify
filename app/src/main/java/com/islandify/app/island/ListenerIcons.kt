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

// SPLITLO  (Icons: app icon cache, sender photo, large icon.)

internal fun IslandListener.appIconCached(pkg: String): Bitmap? {
    appIconCache.get(pkg)?.let { return it }
    val b = runCatching { packageManager.getApplicationIcon(pkg).toBitmap(96, 96) }.getOrNull()
    if (b != null) appIconCache.put(pkg, b)
    return b
}

/** Sender photo (large icon) or the app icon, kept small (96 px) so it is cheap to hold in memory. */
internal fun IslandListener.iconFor(sbn: StatusBarNotification, appOnly: Boolean): Bitmap? {
    // AVATARFIX-iconFor: chat / group ki DP pehle, na mile to app icon
    if (!appOnly) {
        for (ic in avatarCandidates(sbn)) {
            val b = runCatching { ic.loadDrawable(this)?.toBitmap(96, 96) }.getOrNull()
            if (b != null) return b
        }
    }
    return runCatching { appIconCached(sbn.packageName) }.getOrNull()   // SMOOTHKIT
}

/** BUTTERICON: photo content:// URI se aati ho (Icon.TYPE_URI = 4, TYPE_URI_ADAPTIVE_BITMAP = 6) to loadDrawable disk / IPC karta hai. */
internal fun IslandListener.hasSlowLargeIcon(sbn: StatusBarNotification): Boolean {
    if (Build.VERSION.SDK_INT < 28) return false
    return runCatching {
        // AVATARFIX-slow
        avatarCandidates(sbn).any { val t = it.type; t == 4 || t == 6 }
    }.getOrDefault(false)
}

/** BUTTERICON: background thread par chalta hai. */
internal fun IslandListener.loadLargeIcon(sbn: StatusBarNotification): Bitmap? {
    // AVATARFIX-load
    for (ic in avatarCandidates(sbn)) {
        val b = runCatching { ic.loadDrawable(this)?.toBitmap(96, 96) }.getOrNull()
        if (b != null) return b
    }
    return null
}

/** AVATARFIX: chat / group / channel ki DP ke saare possible icons (priority order). */
@android.annotation.SuppressLint("NewApi")
@Suppress("DEPRECATION")
internal fun IslandListener.avatarCandidates(sbn: StatusBarNotification): List<android.graphics.drawable.Icon> {
    val n = sbn.notification
    val ex = n.extras
    val out = ArrayList<android.graphics.drawable.Icon>()
    runCatching { n.getLargeIcon()?.let { out.add(it) } }
    runCatching {
        when (val v = ex.get("android.largeIcon")) {
            is Bitmap -> out.add(android.graphics.drawable.Icon.createWithBitmap(v))
            is android.graphics.drawable.Icon -> out.add(v)
            else -> Unit
        }
    }
    runCatching { ex.getParcelable<android.graphics.drawable.Icon>("android.conversationIcon")?.let { out.add(it) } }
    if (Build.VERSION.SDK_INT >= 28) {
        runCatching {
            val arr = ex.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (arr != null) {
                Notification.MessagingStyle.Message.getMessagesFromBundleArray(arr)
                    .lastOrNull()?.senderPerson?.icon?.let { out.add(it) }
            }
        }
        runCatching { ex.getParcelable<android.app.Person>(Notification.EXTRA_MESSAGING_PERSON)?.icon?.let { out.add(it) } }
    }
    return out
}
