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

// SPLITCTRL  (Parked live activities: appIcon, parkCurrent, dropParked, promoteParked.)

internal fun IslandController.appIcon(pkg: String?): Bitmap? {
    val p = pkg ?: return null
    appIconCache.get(p)?.let { return it }
    val b = runCatching { appCtx?.packageManager?.getApplicationIcon(p)?.toBitmap(96, 96) }.getOrNull()
    if (b != null) appIconCache.put(p, b)
    return b
}

internal fun IslandController.parkCurrent() {
    val m = liveMode ?: return
    val k = liveKey ?: return
    parked[k] = Parked(m, liveOpen, livePkg)
    BubbleStore.putLive(k, m, liveOpen, livePkg, appIcon(livePkg))
}

/** User ne bubble se live hata diya: update aaye to bhi wapas na aaye (jab tak notification hat ke dobara na aaye). */
fun IslandController.dropParked(key: String) { if (parked.remove(key) != null) dismissedLive.add(key) }

fun IslandController.dropAllParked() {
    dismissedLive.addAll(parked.keys)
    parked.clear()
}

/** Current live khatam ho gaya: sabse naya parked live pill me wapas aa jaye. */
internal fun IslandController.promoteParked(): Boolean {
    val k = parked.keys.lastOrNull() ?: return false
    val p = parked.remove(k) ?: return false
    liveMode = p.mode
    liveKey = k
    liveOpen = p.open
    livePkg = p.pkg
    BubbleStore.remove(BubbleStore.LIVE_PREFIX + k)
    return true
}
