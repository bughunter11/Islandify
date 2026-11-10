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

// SPLITCTRL  (IslandMode, ToggleKind, LiveKind)

sealed interface IslandMode {
    data object Idle : IslandMode
    data class Charging(val percent: Int, val connected: Boolean = true, val detail: String = "") : IslandMode   // CHARGEINFO
    data class Notification(
        val app: String,
        val title: String,
        val text: String,
        val canReply: Boolean = false,      // notification has an inline-reply action
        val canMarkRead: Boolean = false,
        val accent: Int? = null,            // notification colour (ARGB) for the glow
        val icon: Bitmap? = null,           // sender photo or app icon   // notification has a "Mark as read" action
        val markReadLabel: String = "Mark as read",   // CARDACTIONS: asli button ka naam (Archive, Mark as read ...)
    ) : IslandMode
    data class Media(
        val title: String,
        val artist: String,
        val playing: Boolean,
        val art: Bitmap? = null,
        val accent: Int? = null,        // ARGB color extracted from the album art (for the glow)
        val durationMs: Long = 0,
        val positionMs: Long = 0,
        val updatedAt: Long = 0,        // elapsedRealtime when positionMs was read
        val speed: Float = 1f,
        // MUSICLAYOUT: shuffle + repeat state (MediaController se milta hai)
        val shuffleOn: Boolean = false,
        val repeatMode: Int = 0,        // 0 = off, 1 = all, 2 = one
    ) : IslandMode
    data class Timer(val remainingSec: Int, val totalSec: Int, val external: Boolean = false) : IslandMode
    data class Call(
        val name: String,
        val incoming: Boolean,
        val since: Long,
        val canAnswer: Boolean = true,   // false = no Answer button found, so none is shown
    ) : IslandMode
    data class Device(val name: String, val headphones: Boolean, val battery: Int = -1) : IslandMode   // BTBATT
    /** Battery warning at 20% and 10% (only while not charging). */
    data class LowBattery(val percent: Int) : IslandMode
    /** Silent / vibrate / Do Not Disturb / flashlight just changed. [on] = the new state. */
    data class Toggle(val kind: ToggleKind, val on: Boolean) : IslandMode
    /** Navigation or delivery / ride tracking, read from the other app's ongoing notification. */
    data class Live(
        val kind: LiveKind,
        val app: String,
        val title: String,
        val text: String,
        val progress: Int = -1,    // 0..100, -1 = unknown (spinner)
    ) : IslandMode
}

enum class ToggleKind { Silent, Vibrate, Dnd, Flashlight, Wifi, Airplane, Hotspot, Vpn, Camera, Mic, ScreenRec }   // EXTRAKIT

enum class LiveKind { Navigation, Tracking, Download }
