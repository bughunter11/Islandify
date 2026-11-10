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

// SPLITSET  App level: triggers, blocked apps, theme / colors, onboarding.
// Flows IslandSettings object me hi hain (poore app me IslandSettings.x se chalte hain). Yahan sirf load / save.

internal fun IslandSettings.loadApp(p: SharedPreferences) {
    // New activity types added in an update are switched ON once; after that the user decides.
    val allKeys = Trigger.entries.map { it.key }.toSet()
    val legacyKeys = setOf("notif", "media", "call", "charge", "timer", "device")
    p.getStringSet("triggers", null)?.let { saved ->
        val seen = p.getStringSet("triggersSeen", null)?.toSet() ?: legacyKeys
        triggers.value = saved.toSet() + (allKeys - seen)
    }
    blockedApps.value = p.getStringSet("blocked", emptySet())?.toSet() ?: emptySet()
    theme.value = p.getInt("theme", 0)
    accentSource.value = p.getInt("accentSource", 0)
    accentSeed.value = p.getInt("accentSeed", 0xFF7B3DF5.toInt())
    bgStyle.value = p.getInt("bgStyle", 0)
    bgColor.value = p.getInt("bgColor", 0xFF14121C.toInt())
    onboarded.value = p.getBoolean("onboarded", false)
    enabled.value = p.getBoolean("enabled", true)
    AppStyles.load(p)   // APPSTYLEKIT (PATCH_10_11_12)
    Motion.load(p)      // MOTIONKIT (PATCH_10_11_12)
}

internal fun IslandSettings.saveApp(e: SharedPreferences.Editor) {
    e.putStringSet("triggers", triggers.value)
    e.putStringSet("triggersSeen", Trigger.entries.map { it.key }.toSet())
    e.putStringSet("blocked", blockedApps.value)
    e.putInt("theme", theme.value)
    e.putInt("accentSource", accentSource.value)
    e.putInt("accentSeed", accentSeed.value)
    e.putInt("bgStyle", bgStyle.value)
    e.putInt("bgColor", bgColor.value)
    e.putBoolean("onboarded", onboarded.value)
    e.putBoolean("enabled", enabled.value)
    AppStyles.save(e)   // APPSTYLEKIT (PATCH_10_11_12)
    Motion.save(e)      // MOTIONKIT (PATCH_10_11_12)
}
