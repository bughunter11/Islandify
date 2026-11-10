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

// SPLITCTRL  (Music: setMedia, play / pause / next / prev, volume, seek, skip.)

fun IslandController.setMedia(m: IslandMode.Media?) {
    val old = mediaMode
    // Only the position moved a little (normal playback)? Nothing to redraw.
    if (old != null && m != null && old.playing && m.playing &&
        old.title == m.title && old.artist == m.artist && old.art === m.art &&
        old.accent == m.accent && old.durationMs == m.durationMs && old.speed == m.speed &&
        old.shuffleOn == m.shuffleOn && old.repeatMode == m.repeatMode &&
        old.updatedAt != 0L && m.updatedAt != 0L
    ) {
        val expected = old.positionMs + ((m.updatedAt - old.updatedAt) * old.speed).toLong()
        if (abs(expected - m.positionMs) < 1500) return
    }
    if ((m == null || old == null || old.title != m.title || old.playing != m.playing) &&
        timerMode == null && liveMode == null && dlMode == null) hidden = false
    mediaMode = m
    // BUBBLELIVE: music bubble (gaana badle / pause / play par hi update, position tick par nahi)
    if (m == null) BubbleStore.removeStatus("media")
    else BubbleStore.putStatus(
        "media", Isl.music, m.title.ifBlank { Isl.music },   // ISLANDI18N
        if (m.playing) m.artist else Isl.paused(m.artist),
        m.accent ?: 0xFFFF2D55.toInt(), m.art
    )
    if (m == null || m.playing) {
        pauseJob?.cancel()
        pausedHide = false
    } else if (old == null || old.playing || old.title != m.title) {
        // Just paused (or a new paused track): keep the pill for a few seconds, then hide it
        pauseJob?.cancel()
        pausedHide = false
        pauseJob = scope.launch {
            if (IslandSettings.mediaKeepPaused.value) return@launch
            delay(PAUSE_HIDE_MS)
            pausedHide = true
            refresh()
        }
    }
    if (m == null && _mode.value is IslandMode.Media) _level.value = IslandLevel.Compact
    // New song while playing: open the music card for a moment (Customize > More island options)
    if (old != null && m != null && m.playing && old.title != m.title &&
        IslandSettings.mediaPeek.value && callMode == null && flashJob?.isActive != true &&
        !_replying.value && base() is IslandMode.Media
    ) {
        refresh()
        _level.value = IslandLevel.Expanded
    }
    refresh()
}

/** Works for the music on the island even when it is only the second part (next to navigation). */
fun IslandController.playPause() {
    val c = media ?: return
    val s = c.playbackState?.state
    if (s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_BUFFERING) c.transportControls.pause()
    else c.transportControls.play()
}

fun IslandController.prev() { media?.transportControls?.skipToPrevious() }

fun IslandController.next() { media?.transportControls?.skipToNext() }

/* ---------------- quick actions (swipe gestures) ---------------- */

/** Media volume up / down. Shows the system volume panel. No permission needed. */
/** Re-evaluates what the island shows (after the priority order changed). */
fun IslandController.reprioritize() { refresh() }

/** Package of the app that owns what is on the island now (for "hide inside its own app"). */
fun IslandController.sourcePackage(): String? {
    val m = _mode.value
    return when (m) {
        is IslandMode.Media -> media?.packageName
        is IslandMode.Notification -> flashPkg
        is IslandMode.Live ->
            if (m.kind == LiveKind.Download) downloads.values.firstOrNull()?.pkg else livePkg
        else -> null
    }
}

fun IslandController.volume(up: Boolean) {
    val am = appCtx?.getSystemService(android.media.AudioManager::class.java) ?: return
    runCatching {
        am.adjustStreamVolume(
            android.media.AudioManager.STREAM_MUSIC,
            if (up) android.media.AudioManager.ADJUST_RAISE else android.media.AudioManager.ADJUST_LOWER,
            android.media.AudioManager.FLAG_SHOW_UI
        )
    }
}

/** Sound -> Vibrate -> Silent -> Sound. The silent step needs Do Not Disturb access, else it skips back. */
fun IslandController.cycleSound() {
    val am = appCtx?.getSystemService(android.media.AudioManager::class.java) ?: return
    val order = listOf(
        android.media.AudioManager.RINGER_MODE_NORMAL,
        android.media.AudioManager.RINGER_MODE_VIBRATE,
        android.media.AudioManager.RINGER_MODE_SILENT,
    )
    val cur = am.ringerMode
    val target = order[(order.indexOf(cur) + 1).mod(order.size)]
    val ok = runCatching { am.ringerMode = target }.isSuccess
    if (!ok) {
        val fallback = if (cur == android.media.AudioManager.RINGER_MODE_NORMAL)
            android.media.AudioManager.RINGER_MODE_VIBRATE else android.media.AudioManager.RINGER_MODE_NORMAL
        runCatching { am.ringerMode = fallback }
    }
    // The pop-up itself comes from the ringer receiver in IslandOverlay (Silent / DND / flashlight switch)
}

fun IslandController.seekTo(ms: Long) {
    media?.transportControls?.seekTo(ms)
}

// MUSICLAYOUT: shuffle / repeat.
// Platform MediaController.TransportControls me setShuffleMode / setRepeatMode nahi hote,
// isliye androidx.media ka MediaControllerCompat (android.support.v4.media.session.*) use hota hai.
// Compat ke constants: SHUFFLE NONE=0 ALL=1 GROUP=2 | REPEAT NONE=0 ONE=1 ALL=2 GROUP=3 (INVALID=-1).
// App ke andar repeatMode: 0 = off, 1 = all, 2 = one.
internal fun IslandController.compatOf(mc: MediaController): android.support.v4.media.session.MediaControllerCompat? {
    val ctx = appCtx ?: return null
    return runCatching {
        android.support.v4.media.session.MediaControllerCompat(
            ctx,
            android.support.v4.media.session.MediaSessionCompat.Token.fromToken(mc.sessionToken)
        )
    }.getOrNull()
}

fun IslandController.toggleShuffle() {
    val mc = media ?: return
    val cur = mediaMode?.shuffleOn ?: false
    val compat = compatOf(mc) ?: return
    runCatching {
        compat.transportControls.setShuffleMode(
            if (cur) android.support.v4.media.session.PlaybackStateCompat.SHUFFLE_MODE_NONE
            else android.support.v4.media.session.PlaybackStateCompat.SHUFFLE_MODE_ALL
        )
    }
    // Turant UI update (asli state listener se baad me aa jaati hai)
    mediaMode?.let { setMedia(it.copy(shuffleOn = !cur)) }
}

// off -> all -> one -> off
fun IslandController.cycleRepeat() {
    val mc = media ?: return
    val cur = mediaMode?.repeatMode ?: 0
    val compat = compatOf(mc) ?: return
    val (compatNext, appNext) = when (cur) {
        0 -> android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_ALL to 1
        1 -> android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_ONE to 2
        else -> android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_NONE to 0
    }
    runCatching { compat.transportControls.setRepeatMode(compatNext) }
    mediaMode?.let { setMedia(it.copy(repeatMode = appNext)) }
}

/* ---------------- flashlight pill + outside tap ---------------- */

/** Swipe left = next track, swipe right = previous (only while music is on the island). */
fun IslandController.skip(dir: Int) {
    if (_mode.value !is IslandMode.Media) return
    val t = media?.transportControls ?: return
    if (dir > 0) t.skipToNext() else t.skipToPrevious()
}
