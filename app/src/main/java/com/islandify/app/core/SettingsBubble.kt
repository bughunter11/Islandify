package com.islandify.app.core
import com.islandify.app.R
import androidx.annotation.StringRes

import com.islandify.app.island.*
import com.islandify.app.ui.components.*
import com.islandify.app.ui.onboarding.*
import com.islandify.app.ui.screens.*
import com.islandify.app.ui.theme.*

import android.content.Context
import android.content.SharedPreferences

// SPLITSET  Bubble page.
// Flows IslandSettings object me hi hain (poore app me IslandSettings.x se chalte hain). Yahan sirf load / save.

internal fun IslandSettings.loadBubble(p: SharedPreferences) {
    bubbleSize.value = p.getFloat("bbSize", 32f)
    bubbleGap.value = p.getFloat("bbGap", 2f)
    bubbleY.value = p.getFloat("bbY", 0f)
    bubbleAlpha.value = p.getFloat("bbAlpha", 1f)
    bubbleSwipeOn.value = p.getBoolean("bbSwipeOn", true)
    bubbleSwipeDist.value = p.getFloat("bbSwipe", 56f)
    bubbleStyle.value = p.getInt("stBubble", 0).coerceIn(0, 11)   // STYLEKIT
    bubbleEnabled.value = p.getBoolean("bbEnabled", true)   // BUBBLETOGGLE
    bubbleExtras.value = p.getBoolean("bbExtra", true)   // BUBBLELIVE
    bubbleOverlap.value = p.getBoolean("bbOverlap2", true)   // OVERLAPKIT / REVIVEKIT
    bubbleMove.value = p.getBoolean("bbMove", true)   // BUBBLEMOVE
    bubbleFreeOn.value = p.getBoolean("bbFreeOn", false)
    bubbleFreeX.value = p.getFloat("bbFreeX", 0f)
    bubbleFreeY.value = p.getFloat("bbFreeY", 0f)
}

internal fun IslandSettings.saveBubble(e: SharedPreferences.Editor) {
    e.putFloat("bbSize", bubbleSize.value)
    e.putFloat("bbGap", bubbleGap.value)
    e.putFloat("bbY", bubbleY.value)
    e.putFloat("bbAlpha", bubbleAlpha.value)
    e.putBoolean("bbSwipeOn", bubbleSwipeOn.value)
    e.putFloat("bbSwipe", bubbleSwipeDist.value)
    e.putInt("stBubble", bubbleStyle.value)   // STYLEKIT
    e.putBoolean("bbEnabled", bubbleEnabled.value)   // BUBBLETOGGLE
    e.putBoolean("bbExtra", bubbleExtras.value)   // BUBBLELIVE
    e.putBoolean("bbOverlap2", bubbleOverlap.value)   // OVERLAPKIT / REVIVEKIT
    e.putBoolean("bbMove", bubbleMove.value)   // BUBBLEMOVE
    e.putBoolean("bbFreeOn", bubbleFreeOn.value)
    e.putFloat("bbFreeX", bubbleFreeX.value)
    e.putFloat("bbFreeY", bubbleFreeY.value)
}
