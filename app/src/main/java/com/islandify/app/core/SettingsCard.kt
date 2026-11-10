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

// SPLITSET  Message card page.
// Flows IslandSettings object me hi hain (poore app me IslandSettings.x se chalte hain). Yahan sirf load / save.

internal fun IslandSettings.loadCard(p: SharedPreferences) {
    cardWidth.value = p.getFloat("mcW", 0.92f)
    cardCorner.value = p.getFloat("mcCorner", 30f)
    cardTextScale.value = p.getFloat("mcText", 1f)
    cardAlpha.value = p.getFloat("mcAlpha", 1f)
    cardAccent.value = p.getFloat("mcAccent", 0.45f)
    cardMaxLines.value = p.getInt("mcLines", 10)   // CARDGROW
    cardScrollSpeed.value = p.getFloat("mcScroll", 1f).coerceIn(0.3f, 3f)   // CARDSPEED
    cardButtons.value = p.getBoolean("mcBtns", true)
    cardRow.value = p.getBoolean("mcRow", true)
    cardUpDist.value = p.getFloat("mcUpD", 40f)
    cardSideDist.value = p.getFloat("mcSideD", 64f)
    cardStyle.value = p.getInt("stCard", 0).coerceIn(0, 11)   // STYLEKIT
    cardY.value = p.getFloat("mcY", 0f)   // CARDY
    clearOnDismiss.value = p.getBoolean("clearOnDismiss", true)
    directCardAll.value = p.getBoolean("directCardAll", false)
    directCardReply.value = p.getBoolean("directCardReply", false)
    // MUSICKIT
    musicStyle.value = p.getInt("stMusic", 0).coerceIn(0, 11)
    musicAlpha.value = p.getFloat("musicAlpha", 1f)
    musicAccent.value = p.getFloat("musicAccent", 1f)
    musicCorner.value = p.getFloat("musicCorner", 0f)
    musicArtSize.value = p.getFloat("musicArt", 1f)
    musicArtCorner.value = p.getFloat("musicArtR", 14f)
    musicWave.value = p.getBoolean("musicWave", true)
    waveSpeed.value = p.getFloat("waveSpeed", 1f)           // WAVESYNC
    waveFollow.value = p.getBoolean("waveFollow", true)     // WAVESYNC
    musicAuto.value = p.getBoolean("musicAuto", true)   // MUSICAUTO
    musicPillAuto.value = p.getBoolean("musicPillAuto", true)
    musicX.value = p.getFloat("musicX", 0f)   // MUSICPOS
    musicY.value = p.getFloat("musicY", 0f)   // MUSICPOS
    musicPlayerLayout.value = p.getInt("musicPlayerLayout", 0)
    seekBarColorMode.value = p.getInt("seekBarColorMode", 0)
    seekDesign.value = p.getInt("seekDesign", 0)   // SEEKDESIGN
    seekCustomColor.value = p.getInt("seekCustomColor", 0xFF22D3EE.toInt())   // SEEKDESIGN
    seekTimeTint.value = p.getBoolean("seekTimeTint", false)   // SEEKDESIGN
}

internal fun IslandSettings.saveCard(e: SharedPreferences.Editor) {
    e.putFloat("mcW", cardWidth.value)
    e.putFloat("mcCorner", cardCorner.value)
    e.putFloat("mcText", cardTextScale.value)
    e.putFloat("mcAlpha", cardAlpha.value)
    e.putFloat("mcAccent", cardAccent.value)
    e.putInt("mcLines", cardMaxLines.value)
    e.putFloat("mcScroll", cardScrollSpeed.value)   // CARDSPEED
    e.putBoolean("mcBtns", cardButtons.value)
    e.putBoolean("mcRow", cardRow.value)
    e.putFloat("mcUpD", cardUpDist.value)
    e.putFloat("mcSideD", cardSideDist.value)
    e.putInt("stCard", cardStyle.value)   // STYLEKIT
    e.putFloat("mcY", cardY.value)   // CARDY
    e.putBoolean("clearOnDismiss", clearOnDismiss.value)
    e.putBoolean("directCardAll", directCardAll.value)
    e.putBoolean("directCardReply", directCardReply.value)
    // MUSICKIT
    e.putInt("stMusic", musicStyle.value)
    e.putFloat("musicAlpha", musicAlpha.value)
    e.putFloat("musicAccent", musicAccent.value)
    e.putFloat("musicCorner", musicCorner.value)
    e.putFloat("musicArt", musicArtSize.value)
    e.putFloat("musicArtR", musicArtCorner.value)
    e.putBoolean("musicWave", musicWave.value)
    e.putFloat("waveSpeed", waveSpeed.value)                // WAVESYNC
    e.putBoolean("waveFollow", waveFollow.value)            // WAVESYNC
    e.putBoolean("musicAuto", musicAuto.value)   // MUSICAUTO
    e.putBoolean("musicPillAuto", musicPillAuto.value)
    e.putFloat("musicX", musicX.value)   // MUSICPOS
    e.putFloat("musicY", musicY.value)   // MUSICPOS
    e.putInt("musicPlayerLayout", musicPlayerLayout.value)
    e.putInt("seekBarColorMode", seekBarColorMode.value)
    e.putInt("seekDesign", seekDesign.value)   // SEEKDESIGN
    e.putInt("seekCustomColor", seekCustomColor.value)   // SEEKDESIGN
    e.putBoolean("seekTimeTint", seekTimeTint.value)   // SEEKDESIGN
}
