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

// SPLITCTRL  (Calls: setCall / clearCall, answer / decline, speaker / mute.)

fun IslandController.setCall(
    name: String, incoming: Boolean, answer: PendingIntent?, decline: PendingIntent?,
    key: String? = null,
    canAnswer: Boolean = answer != null,
    speaker: PendingIntent? = null,
    mute: PendingIntent? = null,
    startedAt: Long? = null,   // POLISHKIT
    open: PendingIntent? = null,   // CALLCTL
) {
    val old = callMode
    if (key != null) callKey = key
    // POLISHKIT: ongoing call ka timer notification ke asli start (chronometer) se; warna pehle jaisa
    val nowEl = SystemClock.elapsedRealtime()
    val since = when {
        old != null && old.incoming == incoming && (startedAt == null || abs(startedAt - old.since) < 3000L) -> old.since
        startedAt != null -> startedAt
        else -> nowEl
    }
    val c = IslandMode.Call(name, incoming, since, canAnswer)
    callMode = c
    callAnswer = answer
    callDecline = decline
    callSpeaker = speaker
    callMute = mute
    callOpen = open   // CALLCTL
    if (!incoming) syncCallAudio()   // CALLCTL: UI me asli mic / speaker state
    hidden = false
    _replying.value = false
    if (stickyHeld) curFlash?.let { parkSticky(it) }   // keep the sticky pill (before cancel)
    flashJob?.cancel()
    flashQueue.removeAll { !it.seen }                   // drop only unseen pop-ups, keep sticky ones
    _queued.value = 0
    _mode.value = c
    // CALLPILL: call (incoming bhi) pill me dikhta hai, buttons pill me hi hain.
    // Naya call ya type badla (ring -> baat) to pill par; user ne card khola ho to update par band nahi hota.
    if (old == null || old.incoming != incoming) _level.value = IslandLevel.Compact
}

/** [key] = the notification that was removed. A stale removal (old ringing notif) must not kill the new call. */
fun IslandController.clearCall(key: String? = null) {
    if (callMode == null) return
    if (key != null && callKey != null && key != callKey) return
    callKey = null
    callMode = null
    callAnswer = null
    callDecline = null
    callSpeaker = null
    callMute = null
    callOpen = null   // CALLCTL
    resetCallAudio()
    _level.value = IslandLevel.Compact
    // a sticky pill waiting from before the call comes back
    if (flashQueue.isNotEmpty() && flashJob?.isActive != true) afterFlash() else refresh()
}

/** Android 14+ blocks activity PendingIntents sent from the background unless we opt in. */
internal fun IslandController.sendWithBal(pi: PendingIntent?): Boolean {
    if (pi == null) return false
    return runCatching {
        val ctx = appCtx
        if (ctx != null) {
            val opts = ActivityOptions.makeBasic()
            if (Build.VERSION.SDK_INT >= 34) {
                @Suppress("DEPRECATION")   // DEPRWARN
                val bgAllowed = ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                opts.setPendingIntentBackgroundActivityStartMode(bgAllowed)
            }
            pi.send(ctx, 0, null, null, null, null, opts.toBundle())
        } else pi.send()
    }.isSuccess
}

// CALLCTL: uthana / cut. Pehle notification ka button, na ho to Android ka TelecomManager.
fun IslandController.answerCall() {
    if (sendWithBal(callAnswer)) return
    telecomAct(true)
}

fun IslandController.declineCall() {
    if (sendWithBal(callDecline)) return
    telecomAct(false)
}

/** CALLCTL: Phone permission (ANSWER_PHONE_CALLS) mili hai? */
fun IslandController.canTelecomAnswer(): Boolean {
    val ctx = appCtx ?: return false
    return Build.VERSION.SDK_INT >= 26 &&
        ctx.checkSelfPermission("android.permission.ANSWER_PHONE_CALLS") ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
}

@android.annotation.SuppressLint("MissingPermission")
@Suppress("DEPRECATION") // WARNFIX: acceptRingingCall/endCall deprecated, naya replacement nahi
internal fun IslandController.telecomAct(answer: Boolean) {
    val ctx = appCtx ?: return
    if (!canTelecomAnswer()) { callFallback(com.islandify.app.R.string.call_need_perm); return }
    var ok = false
    runCatching {
        val tm = ctx.getSystemService(android.telecom.TelecomManager::class.java)
        if (tm != null) {
            if (answer) { tm.acceptRingingCall(); ok = true }
            else if (Build.VERSION.SDK_INT >= 28) ok = tm.endCall()
        }
        Unit
    }
    if (!ok) callFallback(com.islandify.app.R.string.call_audio_dialer)
}

/** CALLCTL: kaam na ho paye to chhota message + dialer ki call screen khol do. */
internal fun IslandController.callFallback(msgRes: Int) {
    val ctx = appCtx ?: return
    runCatching {
        android.widget.Toast.makeText(ctx, ctx.getString(msgRes), android.widget.Toast.LENGTH_LONG).show()
    }
    sendWithBal(callOpen)
}

internal fun IslandController.audio() = appCtx?.getSystemService(android.media.AudioManager::class.java)

// CALLCTL: speaker / mute. UI hamesha asli state se; asar na ho to dialer ki call screen khulti hai.
@Suppress("DEPRECATION")
internal fun IslandController.realSpeaker(am: android.media.AudioManager): Boolean? = runCatching {
    if (Build.VERSION.SDK_INT >= 31) {
        am.communicationDevice?.let { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
    } else {
        am.isSpeakerphoneOn
    }
}.getOrNull()

internal fun IslandController.syncCallAudio() {
    val am = audio() ?: return
    runCatching {
        _muted.value = am.isMicrophoneMute
        realSpeaker(am)?.let { _speakerOn.value = it }
    }
}

@Suppress("DEPRECATION")
internal fun IslandController.setSpeaker(am: android.media.AudioManager, on: Boolean) {
    if (Build.VERSION.SDK_INT >= 31) {
        val spk = android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        if (on) {
            am.availableCommunicationDevices.firstOrNull { it.type == spk }?.let { am.setCommunicationDevice(it) }
        } else {
            am.clearCommunicationDevice()
        }
        if (realSpeaker(am) != on) am.isSpeakerphoneOn = on   // purana raasta, kuch ROM par chalta hai
    } else {
        am.isSpeakerphoneOn = on
    }
}

fun IslandController.toggleSpeaker() {
    callSpeaker?.let { if (sendWithBal(it)) { _speakerOn.value = !_speakerOn.value; return } }
    val am = audio() ?: return
    val want = !(realSpeaker(am) ?: _speakerOn.value)
    runCatching { setSpeaker(am, want) }
    val now = realSpeaker(am) ?: want
    _speakerOn.value = now
    if (now != want) callFallback(com.islandify.app.R.string.call_audio_dialer)
}

fun IslandController.toggleMute() {
    callMute?.let { if (sendWithBal(it)) { _muted.value = !_muted.value; return } }
    val am = audio() ?: return
    val want = !am.isMicrophoneMute
    runCatching { am.isMicrophoneMute = want }
    val now = runCatching { am.isMicrophoneMute }.getOrDefault(want)
    _muted.value = now
    if (now != want) callFallback(com.islandify.app.R.string.call_audio_dialer)
}

@Suppress("DEPRECATION")
internal fun IslandController.resetCallAudio() {
    val am = audio()
    if (am != null) runCatching {
        if (_speakerOn.value) {
            if (Build.VERSION.SDK_INT >= 31) { am.clearCommunicationDevice() } else { am.isSpeakerphoneOn = false }
        }
        if (_muted.value) { am.isMicrophoneMute = false }
        Unit
    }
    _speakerOn.value = false
    _muted.value = false
}

/* ---------------- flash banner ---------------- */
