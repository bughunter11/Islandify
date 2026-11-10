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

// SPLITCTRL  (Flashlight / torch.)

/** Flashlight on / off. Torch mode needs no camera permission. */
fun IslandController.toggleTorch() {
    val ctx = appCtx ?: return
    val cm = ctx.getSystemService(android.hardware.camera2.CameraManager::class.java) ?: return
    runCatching {
        if (!torchWatching) {
            torchWatching = true
            cm.registerTorchCallback(object : android.hardware.camera2.CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { torchOn = enabled }
            }, null)
        }
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return
        cm.setTorchMode(id, !_torchLive.value)   // POLISHKIT: overlay ke asli torch state se
    }
}

/** Called by the overlay's torch callback every time the flashlight really turns on / off. */
fun IslandController.onTorchChanged(on: Boolean) {
    if (_torchLive.value == on) return
    _torchLive.value = on
    if (on) {
        _torchLevel.value = torchDefaultLevel()
        if (IslandSettings.flashlightPill.value) hidden = false
    } else {
        val m = _mode.value
        if (m is IslandMode.Toggle && m.kind == ToggleKind.Flashlight && flashJob?.isActive != true) {
            _level.value = IslandLevel.Compact
        }
    }
    refresh()
}

/** SMOOTHKIT: torch wala camera ek baar dhundo (slider har move par binder call na kare). */
internal fun IslandController.torchCamera(): Pair<android.hardware.camera2.CameraManager, String>? =
    torchCamCache ?: findTorchCamera().also { torchCamCache = it }

internal fun IslandController.findTorchCamera(): Pair<android.hardware.camera2.CameraManager, String>? = runCatching {
    val cm = appCtx?.getSystemService(android.hardware.camera2.CameraManager::class.java)
        ?: return@runCatching null
    val id = cm.cameraIdList.firstOrNull {
        cm.getCameraCharacteristics(it)
            .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    } ?: return@runCatching null
    cm to id
}.getOrNull()

fun IslandController.torchMaxLevel(): Int {
    if (torchMaxCache > 0) return torchMaxCache
    return readTorchMax().also { torchMaxCache = it }
}

internal fun IslandController.readTorchMax(): Int {
    if (Build.VERSION.SDK_INT < 33) return 1
    return runCatching {
        val (cm, id) = torchCamera() ?: return 1
        cm.getCameraCharacteristics(id)
            .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
    }.getOrDefault(1)
}

internal fun IslandController.torchDefaultLevel(): Int {
    if (Build.VERSION.SDK_INT < 33) return 1
    return runCatching {
        val (cm, id) = torchCamera() ?: return 1
        cm.getCameraCharacteristics(id)
            .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_STRENGTH_DEFAULT_LEVEL) ?: 1
    }.getOrDefault(1)
}

/** Brightness slider of the flashlight card. */
fun IslandController.setTorchLevel(level: Int) {
    if (Build.VERSION.SDK_INT < 33) return
    val l = level.coerceIn(1, torchMaxLevel())
    if (l == _torchLevel.value) return   // SMOOTHKIT: same level = kuch nahi karna
    _torchLevel.value = l
    runCatching {
        val (cm, id) = torchCamera() ?: return
        cm.turnOnTorchWithStrengthLevel(id, l)
    }
}

fun IslandController.turnTorchOff() {
    runCatching {
        val (cm, id) = torchCamera() ?: return
        cm.setTorchMode(id, false)
    }
}
