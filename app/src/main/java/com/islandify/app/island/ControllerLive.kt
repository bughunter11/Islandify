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

// SPLITCTRL  (Live activities: setLive / showLive / clearLive.)

/** Shows / updates a live activity. [key] = the source notification (used to remove it later). */
fun IslandController.setLive(m: IslandMode.Live, key: String, open: PendingIntent?, pkg: String?, announce: Boolean = true) {
    val old = liveMode
    if (old == m && liveKey == key) return
    if (key in dismissedLive) return                       // user ne bubble se hata diya tha
    // Ye live pehle se bubble me parked hai: bas bubble ka text update, pill ko mat chhedo
    if (key != liveKey && parked.containsKey(key)) {
        parked[key] = Parked(m, open, pkg)
        BubbleStore.putLive(key, m, open, pkg, appIcon(pkg))
        return
    }
    val isNew = key != liveKey
    // Bilkul naya live aaya aur pill me kuch aur chal raha hai: purana bubble me chala jaye
    if (isNew && old != null) parkCurrent()
    // A swipe-up hides it until a different app starts its own live activity
    if (old == null || old.app != m.app || isNew) hidden = false
    liveMode = m
    liveKey = key
    liveOpen = open
    livePkg = pkg
    refresh()
    // Naya live message ki tarah pehle dikhe (card), phir pill bane
    if (isNew && announce && callMode == null && flashJob?.isActive != true &&
        !_replying.value && base() is IslandMode.Live
    ) {
        _level.value = IslandLevel.Expanded
    }
}

/** Forces the island to show again even if the same live activity was swiped away before. */
fun IslandController.unhide() {
    hidden = false
    refresh()
}

fun IslandController.showLive(m: IslandMode.Live, key: String, open: PendingIntent?, pkg: String?) {
    hidden = false
    setLive(m, key, open, pkg)
    refresh()
}

/** [key] = the notification that was removed. A stale removal must not kill a newer live activity. */
fun IslandController.clearLive(key: String? = null) {
    if (key == null) {
        // sab live hatao (settings change)
        parked.keys.toList().forEach { BubbleStore.remove(BubbleStore.LIVE_PREFIX + it) }
        parked.clear()
        dismissedLive.clear()
    } else {
        dismissedLive.remove(key)
        if (parked.remove(key) != null) BubbleStore.remove(BubbleStore.LIVE_PREFIX + key)
    }
    if (liveMode == null) return
    if (key != null && liveKey != null && key != liveKey) return
    liveMode = null
    liveKey = null
    liveOpen = null
    livePkg = null
    // pill ka live khatam: bubble me jo live park tha wo pill me wapas aa jaye
    if (promoteParked()) { refresh(); return }
    if (_mode.value is IslandMode.Live) _level.value = IslandLevel.Compact
    refresh()
}

/* ---------------- downloads ---------------- */
