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

// SPLITLO  (Card buttons + verified cancel from the shade.)

/**
 * CARDACTIONS: notification ke asli action buttons -> message card.
 * Reply (free text) + baaki plain buttons (Mark as read, No thanks, Turn on, Archive ...), max 4.
 * Smart-reply suggestions (contextual) aur bina-intent / sirf-choices wale actions chhod diye jaate hain.
 */
internal fun IslandListener.buildCardActions(acts: List<Notification.Action>, reply: Notification.Action?): List<BubbleAction> {
    val all = ArrayList<BubbleAction>()
    for (a in acts) {
        val label = a.title?.toString()?.trim().orEmpty()
        if (label.isEmpty()) continue
        if (Build.VERSION.SDK_INT >= 28 && a.isContextual) continue
        if (all.any { it.label.equals(label, ignoreCase = true) }) continue
        if (a === reply) {
            all.add(BubbleAction(label, a.actionIntent, reply = a))
            continue
        }
        if (a.remoteInputs?.isNotEmpty() == true) continue   // bina text ke fire nahi hota
        val pi = a.actionIntent ?: continue
        all.add(BubbleAction(label, pi))
    }
    if (all.size <= 4) return all
    val keep = HashSet<BubbleAction>()
    all.firstOrNull { it.reply != null }?.let { keep.add(it) }
    for (x in all) if (keep.size < 4) keep.add(x)
    return all.filter { it in keep }
}

/** CANCELFIX: cancel + verify. Kuch apps / ROM notification wapas laga dete hain -> 700 ms baad check, 2 baar aur try. */
internal fun IslandListener.cancelVerified(key: String, attempt: Int) {
    runCatching { cancelNotification(key) }
    if (attempt >= 2) return
    scope.launch {
        delay(700)
        val still = runCatching { activeNotifications?.any { it.key == key } == true }.getOrDefault(false)
        if (still) cancelVerified(key, attempt + 1)
    }
}
