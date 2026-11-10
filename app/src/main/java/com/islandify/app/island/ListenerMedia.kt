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

// SPLITLO  (Media session: refreshSessions, setSessions, reselect, pushMedia.)

internal fun IslandListener.refreshSessions() =
    setSessions(runCatching { msm.getActiveSessions(cn) }.getOrNull())

internal fun IslandListener.setSessions(list: List<MediaController>?) {
    sessions.forEach { it.unregisterCallback(callback) }
    sessions = list.orEmpty()
    sessions.forEach { it.registerCallback(callback) }
    reselect()
}

/** Playing session first, apps in the blocked list never. */
internal fun IslandListener.reselect() {
    val ok = sessions.filter { it.packageName !in IslandSettings.blockedApps.value }
    val best = ok.firstOrNull { it.playbackState.live() } ?: ok.firstOrNull()
    if (best !== controller) {
        controller = best
        IslandController.media = best
    }
    // SMOOTHKIT: mar chuka media session exception de sakta hai: crash nahi, music bas hat jaye
    runCatching { pushMedia() }.onFailure { IslandController.setMedia(null) }
}

internal fun IslandListener.pushMedia() {
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
    // MUSICLAYOUT: shuffle / repeat state. App ka repeatMode: 0 = off, 1 = all, 2 = one.
    // Compat constants: SHUFFLE ALL=1 GROUP=2 | REPEAT ONE=1 ALL=2 GROUP=3 | INVALID=-1 (app support nahi karta)
    val compat = compatFor(c)
    val prev = IslandController.mediaMode?.takeIf { it.title == title }
    val shuffleRaw = runCatching { compat?.shuffleMode }.getOrNull() ?: -1
    val repeatRaw = runCatching { compat?.repeatMode }.getOrNull() ?: -1
    val shuffleOn = if (shuffleRaw < 0) prev?.shuffleOn ?: false else shuffleRaw == 1 || shuffleRaw == 2
    val repeat = if (repeatRaw < 0) prev?.repeatMode ?: 0 else when (repeatRaw) {
        2, 3 -> 1
        1 -> 2
        else -> 0
    }
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
            shuffleOn = shuffleOn,
            repeatMode = repeat,
        )
    )
}

// MUSICLAYOUT: MediaControllerCompat ek hi session ke liye cache hota hai (har update par naya nahi banta)
private var compatCacheFor: MediaController? = null
private var compatCache: android.support.v4.media.session.MediaControllerCompat? = null

private fun compatFor(c: MediaController): android.support.v4.media.session.MediaControllerCompat? {
    if (compatCacheFor !== c) {
        compatCache = IslandController.compatOf(c)
        compatCacheFor = c
    }
    return compatCache
}
