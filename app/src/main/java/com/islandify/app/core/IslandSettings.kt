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
import kotlinx.coroutines.flow.MutableStateFlow

/** Live activity types the island can show. Each one can be switched on/off in the app. */
enum class Trigger(val key: String, @StringRes val label: Int) {
    Notifications("notif", R.string.trigger_notifications),
    Media("media", R.string.trigger_media),
    Calls("call", R.string.trigger_calls),
    Charging("charge", R.string.trigger_charging),
    Timer("timer", R.string.trigger_timer),
    Devices("device", R.string.trigger_devices),
    Battery("battery", R.string.trigger_battery),
    Toggles("toggles", R.string.trigger_toggles),
    Navigation("nav", R.string.trigger_navigation),
    Tracking("track", R.string.trigger_tracking),
}

/**
 * Island shape, position, look and toggles. The app, the services and the listener all share this.
 *  - scale        : whole island bigger/smaller (content included)
 *  - widthScale   : width only
 *  - heightScale  : height only
 *  - corner       : 0.2 = less round, 1.0 = full pill
 *  - animSpeed    : spring speed (0.5 slow .. 2 fast)
 */
object IslandSettings {
    private var prefs: SharedPreferences? = null

    val scale = MutableStateFlow(1f)        // 0.5x .. 1.8x
    val widthScale = MutableStateFlow(1f)   // 0.6x .. 1.6x
    val heightScale = MutableStateFlow(1f)  // 0.7x .. 1.6x
    val offsetX = MutableStateFlow(0f)      // dp, left (-) / right (+) of center
    val offsetY = MutableStateFlow(10f)     // dp, down from the top of the screen

    val corner = MutableStateFlow(1f)       // 0.2 .. 1
    val animSpeed = MutableStateFlow(1f)    // 0.5 .. 2
    val glow = MutableStateFlow(true)       // album art / accent glow
    val haptics = MutableStateFlow(true)

    val triggers = MutableStateFlow(Trigger.entries.map { it.key }.toSet())
    val blockedApps = MutableStateFlow<Set<String>>(emptySet())

    val theme = MutableStateFlow(0)          // 0 system, 1 light, 2 dark, 3 amoled
    // Colors (whole app) + island behaviour
    val accentSource = MutableStateFlow(0)                       // 0 wallpaper (Material You), 1 custom seed color
    val accentSeed = MutableStateFlow(0xFF7B3DF5.toInt())        // seed used when accentSource == 1
    val bgStyle = MutableStateFlow(0)                            // 0 default, 1 tinted by accent, 2 custom color
    val bgColor = MutableStateFlow(0xFF14121C.toInt())           // used when bgStyle == 2
    val islandAppAccent = MutableStateFlow(false)                // island glow uses the app accent instead of album art
    val autoCollapseSec = MutableStateFlow(6)                    // expanded card collapses by itself (0 = never)
    val idlePill = MutableStateFlow(true)                        // small pill stays on screen when idle (tap = time / date / battery)
    val swipeSkip = MutableStateFlow(true)                       // swipe left/right on the island to skip tracks

    val onboarded = MutableStateFlow(false)  // has the setup screen been completed once?
    val enabled = MutableStateFlow(true)     // has the user left the island switched on?

    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("islandify", Context.MODE_PRIVATE)
        prefs = p
        scale.value = p.getFloat("scale", 1f)
        widthScale.value = p.getFloat("w", 1f)
        heightScale.value = p.getFloat("h", 1f)
        offsetX.value = p.getFloat("x", 0f)
        offsetY.value = p.getFloat("y", 10f)
        corner.value = p.getFloat("corner", 1f)
        animSpeed.value = p.getFloat("speed", 1f)
        glow.value = p.getBoolean("glow", true)
        haptics.value = p.getBoolean("haptics", true)
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
        islandAppAccent.value = p.getBoolean("islandAppAccent", false)
        autoCollapseSec.value = p.getInt("autoCollapse", 6)
        swipeSkip.value = p.getBoolean("swipeSkip", true)
        idlePill.value = p.getBoolean("idlePill", true)
        onboarded.value = p.getBoolean("onboarded", false)
        enabled.value = p.getBoolean("enabled", true)
    }

    fun save() {
        prefs?.edit()
            ?.putFloat("scale", scale.value)
            ?.putFloat("w", widthScale.value)
            ?.putFloat("h", heightScale.value)
            ?.putFloat("x", offsetX.value)
            ?.putFloat("y", offsetY.value)
            ?.putFloat("corner", corner.value)
            ?.putFloat("speed", animSpeed.value)
            ?.putBoolean("glow", glow.value)
            ?.putBoolean("haptics", haptics.value)
            ?.putStringSet("triggers", triggers.value)
            ?.putStringSet("triggersSeen", Trigger.entries.map { it.key }.toSet())
            ?.putStringSet("blocked", blockedApps.value)
            ?.putInt("theme", theme.value)
            ?.putInt("accentSource", accentSource.value)
            ?.putInt("accentSeed", accentSeed.value)
            ?.putInt("bgStyle", bgStyle.value)
            ?.putInt("bgColor", bgColor.value)
            ?.putBoolean("islandAppAccent", islandAppAccent.value)
            ?.putInt("autoCollapse", autoCollapseSec.value)
            ?.putBoolean("swipeSkip", swipeSkip.value)
            ?.putBoolean("idlePill", idlePill.value)
            ?.putBoolean("onboarded", onboarded.value)
            ?.putBoolean("enabled", enabled.value)
            ?.apply()
    }

    fun isOn(t: Trigger) = t.key in triggers.value

    fun setTrigger(t: Trigger, on: Boolean) {
        triggers.value = if (on) triggers.value + t.key else triggers.value - t.key
        save()
    }

    fun setBlocked(pkg: String, blocked: Boolean) {
        blockedApps.value = if (blocked) blockedApps.value + pkg else blockedApps.value - pkg
        save()
    }

    fun preset(size: Float, w: Float, h: Float) {
        scale.value = size
        widthScale.value = w
        heightScale.value = h
        save()
    }

    fun resetShape() = preset(1f, 1f, 1f)

    fun resetLook() {
        corner.value = 1f
        animSpeed.value = 1f
        glow.value = true
        haptics.value = true
        save()
    }

    fun reset() {
        scale.value = 1f
        widthScale.value = 1f
        heightScale.value = 1f
        offsetX.value = 0f
        offsetY.value = 10f
        save()
    }

    /** Island + look + behaviour + colors + theme. (Triggers and blocked apps are kept.) */
    fun resetAll() {
        reset()
        resetLook()
        autoCollapseSec.value = 6
        swipeSkip.value = true
        idlePill.value = true
        islandAppAccent.value = false
        theme.value = 0
        accentSource.value = 0
        accentSeed.value = 0xFF7B3DF5.toInt()
        bgStyle.value = 0
        bgColor.value = 0xFF14121C.toInt()
        save()
    }
}
