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
    Downloads("download", R.string.trigger_downloads),
    Quiet("quiet", R.string.trigger_quiet),
}

/**
 * Everything the island can show. Each kind has its own look (Customize > Pop-ups & activities).
 * [popup] = shown as a quick banner; the others (music, timer, call, navigation...) live in the small pill.
 */
enum class IslandKind(val key: String, @StringRes val label: Int, val popup: Boolean) {
    Notification("notif", R.string.banner_notification, true),
    Charging("charging", R.string.banner_charging, true),
    Unplugged("unplug", R.string.banner_unplugged, true),
    LowBattery("low", R.string.banner_low, true),
    Device("device", R.string.banner_device, true),
    Silent("silent", R.string.banner_silent, true),
    Vibrate("vibrate", R.string.banner_vibrate, true),
    Dnd("dnd", R.string.banner_dnd, true),
    Flashlight("torch", R.string.banner_flash, true),
    Call("call", R.string.banner_call, false),
    Media("media", R.string.banner_music, false),
    Timer("timer", R.string.banner_timer, false),
    Navigation("nav", R.string.banner_nav, false),
    Tracking("track", R.string.banner_tracking, false),
    Download("download", R.string.banner_download, false),
}

/**
 * w / h = length / height of the pop-up banner (or of the small pill for music, timer, call...).
 * cardW / cardH = size of the card that opens when tapped. text = text size. All 1 = normal.
 * corner < 0 = follow the global roundness. durationSec 0 = auto.
 */
data class KindStyle(
    val w: Float = 1f,
    val h: Float = 1f,
    val text: Float = 1f,
    val corner: Float = -1f,
    val durationSec: Int = 0,
    val cardW: Float = 1f,
    val cardH: Float = 1f,
)

/** Which kind a mode is (null = the idle pill). */
fun kindOf(m: IslandMode): IslandKind? = when (m) {
    is IslandMode.Notification -> IslandKind.Notification
    is IslandMode.Charging -> if (m.connected) IslandKind.Charging else IslandKind.Unplugged
    is IslandMode.LowBattery -> IslandKind.LowBattery
    is IslandMode.Device -> IslandKind.Device
    is IslandMode.Toggle -> when (m.kind) {
        ToggleKind.Silent -> IslandKind.Silent
        ToggleKind.Vibrate -> IslandKind.Vibrate
        ToggleKind.Dnd -> IslandKind.Dnd
        ToggleKind.Flashlight -> IslandKind.Flashlight
    }
    is IslandMode.Call -> IslandKind.Call
    is IslandMode.Media -> IslandKind.Media
    is IslandMode.Timer -> IslandKind.Timer
    is IslandMode.Live -> when (m.kind) {
        LiveKind.Navigation -> IslandKind.Navigation
        LiveKind.Tracking -> IslandKind.Tracking
        LiveKind.Download -> IslandKind.Download
    }
    IslandMode.Idle -> null
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

    // Every quick pop-up banner type has its own size / text / roundness / show time
    val kindStyles = MutableStateFlow<Map<String, KindStyle>>(emptyMap())

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
        kindStyles.value = IslandKind.entries.associate { t ->
            val k = "bn_${t.key}"
            t.key to KindStyle(
                w = p.getFloat("${k}_w", 1f),
                h = p.getFloat("${k}_h", 1f),
                text = p.getFloat("${k}_t", 1f),
                corner = p.getFloat("${k}_c", -1f),
                durationSec = p.getInt("${k}_d", 0),
                cardW = p.getFloat("${k}_cw", 1f),
                cardH = p.getFloat("${k}_ch", 1f),
            )
        }
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
        // Banner styles (one set of keys per banner type)
        prefs?.edit()?.also { e ->
            IslandKind.entries.forEach { t ->
                val b = kindStyle(t)
                val k = "bn_${t.key}"
                e.putFloat("${k}_w", b.w).putFloat("${k}_h", b.h).putFloat("${k}_t", b.text)
                    .putFloat("${k}_c", b.corner).putInt("${k}_d", b.durationSec)
                    .putFloat("${k}_cw", b.cardW).putFloat("${k}_ch", b.cardH)
            }
        }?.apply()
    }

    fun kindStyle(t: IslandKind): KindStyle = kindStyles.value[t.key] ?: KindStyle()

    fun setKindStyle(t: IslandKind, style: KindStyle) {
        kindStyles.value = kindStyles.value + (t.key to style)
    }

    /** Custom "show time" for this pop-up, or null = use the app's default time. */
    fun kindDurationMs(m: IslandMode): Long? =
        kindOf(m)?.let { kindStyle(it).durationSec }?.takeIf { it > 0 }?.let { it * 1000L }

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

    fun resetKinds() {
        kindStyles.value = emptyMap()
        save()
    }

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
        resetKinds()
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
