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

// SPLITSET  Pill: shape, look, duration, gestures, idle pill, music.
// Flows IslandSettings object me hi hain (poore app me IslandSettings.x se chalte hain). Yahan sirf load / save.

internal fun IslandSettings.loadPill(p: SharedPreferences) {
    scale.value = p.getFloat("scale", 1f)
    widthScale.value = p.getFloat("w", 1f)
    heightScale.value = p.getFloat("h", 1f)
    offsetX.value = p.getFloat("x", 0f)
    offsetY.value = p.getFloat("y", 10f)
    corner.value = p.getFloat("corner", 1f)
    animSpeed.value = p.getFloat("speed", 1f)
    glow.value = p.getBoolean("glow", true)
    haptics.value = p.getBoolean("haptics", true)
    islandAppAccent.value = p.getBoolean("islandAppAccent", false)
    autoCollapseSec.value = p.getInt("autoCollapse", 6)
    swipeSkip.value = p.getBoolean("swipeSkip", true)
    stickyNotif.value = p.getBoolean("stickyNotif", false)
    pillStyle.value = p.getInt("stPill", 0).coerceIn(0, 11)   // STYLEKIT
    notchMode.value = p.getBoolean("notchMode", false)   // NOTCHMODE
    pillAlpha.value = p.getFloat("stPillAlpha", 1f)   // STYLEKIT2
    pillAccent.value = p.getFloat("stPillAccent", 1f)   // STYLEKIT2
    appleMusic.value = p.getBoolean("appleMus", true)   // APPLEMUSIC
    swipeUp.value = SwipeAction.from(p.getString("swipeUp", null), SwipeAction.Dismiss)
    swipeDown.value = SwipeAction.from(p.getString("swipeDown", null), SwipeAction.None)
    swipeLeft.value = SwipeAction.from(p.getString("swipeLeft", null), SwipeAction.NextTrack)
    swipeRight.value = SwipeAction.from(p.getString("swipeRight", null), SwipeAction.PrevTrack)
    // Old "Swipe to skip tracks" switch was OFF: keep left / right off
    if (!p.contains("swipeLeft") && !swipeSkip.value) {
        swipeLeft.value = SwipeAction.None
        swipeRight.value = SwipeAction.None
    }
    idlePill.value = p.getBoolean("idlePill", false) && p.getBoolean("idleInfo", false)
    outlineOn.value = p.getBoolean("outlineOn", true)
    outlineThickness.value = p.getFloat("outlineW", 0.6f)
    outlineDynamic.value = p.getBoolean("outlineDyn", true)
    outlineHideExpanded.value = p.getBoolean("outlineHideExp", false)
    pulseShadow.value = p.getBoolean("pulse", false)
    pulseDurationMs.value = p.getInt("pulseMs", 1450)
    expandedCorner.value = p.getFloat("expCorner", 0f)
    fontScale.value = p.getFloat("fontScale", 1f)
    mediaPeek.value = p.getBoolean("mediaPeek", false)
    mediaKeepPaused.value = p.getBoolean("mediaKeepPaused", false)
    briefCalendar.value = p.getBoolean("briefCal", false)
    briefWeather.value = p.getBoolean("briefWeather", false)   // WEATHERKIT
    weatherF.value = p.getBoolean("weatherF", false)   // WEATHERKIT
    calHours.value = p.getInt("calHours", 24).coerceIn(1, 24)   // CALKIT
    calAccounts.value = p.getStringSet("calAccounts", emptySet())?.toSet() ?: emptySet()   // CALKIT
    idleInfo.value = p.getBoolean("idleInfo", false)
    idleBatteryOnlyLow.value = p.getBoolean("idleLow", false)
    idleAlarmHours.value = p.getInt("idleAlarmH", 0)
    flashlightPill.value = p.getBoolean("flashPill", false)
    baseOrder.value = normalizeBaseOrder(p.getString("baseOrder", null)?.split(','))
}

internal fun IslandSettings.savePill(e: SharedPreferences.Editor) {
    e.putFloat("scale", scale.value)
    e.putFloat("w", widthScale.value)
    e.putFloat("h", heightScale.value)
    e.putFloat("x", offsetX.value)
    e.putFloat("y", offsetY.value)
    e.putFloat("corner", corner.value)
    e.putFloat("speed", animSpeed.value)
    e.putBoolean("glow", glow.value)
    e.putBoolean("haptics", haptics.value)
    e.putBoolean("islandAppAccent", islandAppAccent.value)
    e.putInt("autoCollapse", autoCollapseSec.value)
    e.putBoolean("swipeSkip", swipeSkip.value)
    e.putBoolean("stickyNotif", stickyNotif.value)
    e.putInt("stPill", pillStyle.value)   // STYLEKIT
    e.putBoolean("notchMode", notchMode.value)   // NOTCHMODE
    e.putFloat("stPillAlpha", pillAlpha.value)   // STYLEKIT2
    e.putFloat("stPillAccent", pillAccent.value)   // STYLEKIT2
    e.putBoolean("appleMus", appleMusic.value)   // APPLEMUSIC
    e.putString("swipeUp", swipeUp.value.key)
    e.putString("swipeDown", swipeDown.value.key)
    e.putString("swipeLeft", swipeLeft.value.key)
    e.putString("swipeRight", swipeRight.value.key)
    e.putBoolean("idlePill", idlePill.value)
    e.putBoolean("outlineOn", outlineOn.value)
    e.putFloat("outlineW", outlineThickness.value)
    e.putBoolean("outlineDyn", outlineDynamic.value)
    e.putBoolean("outlineHideExp", outlineHideExpanded.value)
    e.putBoolean("pulse", pulseShadow.value)
    e.putInt("pulseMs", pulseDurationMs.value)
    e.putFloat("expCorner", expandedCorner.value)
    e.putFloat("fontScale", fontScale.value)
    e.putBoolean("mediaPeek", mediaPeek.value)
    e.putBoolean("mediaKeepPaused", mediaKeepPaused.value)
    e.putBoolean("briefCal", briefCalendar.value)
    e.putBoolean("briefWeather", briefWeather.value)   // WEATHERKIT
    e.putBoolean("weatherF", weatherF.value)   // WEATHERKIT
    e.putInt("calHours", calHours.value)   // CALKIT
    e.putStringSet("calAccounts", calAccounts.value)   // CALKIT
    e.putBoolean("idleInfo", idleInfo.value)
    e.putBoolean("idleLow", idleBatteryOnlyLow.value)
    e.putInt("idleAlarmH", idleAlarmHours.value)
    e.putBoolean("flashPill", flashlightPill.value)
    e.putString("baseOrder", baseOrder.value.joinToString(","))
}
