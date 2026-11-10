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

// SPLITCTRL  (Downloads: bubbles sync, setDownload / clearDownload.)

internal fun IslandController.syncDownloadBubbles() {
    val onPill = dlMode != null && base() === dlMode
    val pillKey = if (onPill) downloads.keys.firstOrNull() else null
    val sb = StringBuilder()
    sb.append(pillKey ?: "-")
    for ((k, d) in downloads) {
        if (k != pillKey) sb.append('|').append(k).append('#').append(d.mode.progress).append(d.mode.title).append(d.mode.text)
    }
    val sig = sb.toString()
    if (sig == dlBubbleSig) return
    dlBubbleSig = sig
    val want = HashSet<String>()
    for ((k, d) in downloads) {
        if (k == pillKey) continue
        if (want.size >= 4) break   // zyada bubbles se baaki messages dab na jaye; pill par (+N) dikhta hai
        val id = "dl" + Integer.toHexString(k.hashCode())
        want.add(id)
        val m = d.mode
        val pct = if (m.progress >= 0) "${m.progress}%" else ""
        val txt = when {
            m.text.isBlank() -> pct
            pct.isEmpty() || m.text.contains(pct) -> m.text
            else -> "$pct  ·  ${m.text}"
        }
        BubbleStore.putStatus(id, m.app, m.title.ifBlank { m.app }, txt, 0xFF2A7FFF.toInt(), appIcon(d.pkg), d.open, 1)
    }
    for (id in dlBubbleIds) if (id !in want) BubbleStore.removeStatus(id)
    dlBubbleIds.clear()
    dlBubbleIds.addAll(want)
}

/** Download / progress notification started or updated. [key] = the source notification. */
fun IslandController.setDownload(m: IslandMode.Live, key: String, open: PendingIntent?, pkg: String?) {
    val old = downloads[key]
    if (old != null && old.mode == m) return          // nothing changed
    if (old == null) hidden = false                   // a new download shows again after a swipe-up
    downloads[key] = Dl(m, open, pkg)                 // replacing keeps the order
    syncDownload()
}

/** [key] = the notification that was removed. null = drop all downloads. */
fun IslandController.clearDownload(key: String? = null) {
    if (key == null) downloads.clear()
    else if (downloads.remove(key) == null) return
    syncDownload()
}

internal fun IslandController.syncDownload() {
    val first = downloads.values.firstOrNull()
    dlMode = first?.mode?.let {
        // More than one download running: show "(+N)"
        if (downloads.size > 1) it.copy(text = "${it.text}  (+${downloads.size - 1})") else it
    }
    // DLBUBBLE: download bubbles ab refresh() me syncDownloadBubbles() se bante hain
    val cur = _mode.value
    if (dlMode == null && cur is IslandMode.Live && cur.kind == LiveKind.Download) {
        _level.value = IslandLevel.Compact
    }
    refresh()
}

/* ---------------- auto-collapse + swipe skip ---------------- */
