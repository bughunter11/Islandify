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

// SPLITBIG  core: IslandSettings.kt. Types: SettingsTypes.kt, backup: SettingsBackup.kt, resets: SettingsReset.kt

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
    Network("net", R.string.trigger_network),
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
    Network("net", R.string.banner_network, true),
    Privacy("privacy", R.string.banner_privacy, true),   // EXTRAKIT
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
    val y: Float = 0f,           // KINDY: is banner type ka up/down (dp)
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
        ToggleKind.Wifi, ToggleKind.Airplane -> IslandKind.Network
        ToggleKind.Hotspot, ToggleKind.Vpn -> IslandKind.Network   // EXTRAKIT
        ToggleKind.Camera, ToggleKind.Mic, ToggleKind.ScreenRec -> IslandKind.Privacy   // EXTRAKIT
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
/** Direction of a swipe on the island. */
enum class SwipeDir { Up, Down, Left, Right }

/** What a swipe can do. The user picks one per direction in Customize > Swipe gestures. */
enum class SwipeAction(val key: String, @StringRes val label: Int) {
    None("none", R.string.swipe_act_none),
    Dismiss("dismiss", R.string.swipe_act_dismiss),          // collapse, then hide
    ClearAll("clear", R.string.swipe_act_clear),             // dismiss + drop every waiting pop-up
    Expand("expand", R.string.swipe_act_expand),             // pill -> card
    OpenApp("open", R.string.swipe_act_open),                // open the app the island is showing
    NextTrack("next", R.string.swipe_act_next),
    PrevTrack("prev", R.string.swipe_act_prev),
    PlayPause("playpause", R.string.swipe_act_playpause),
    VolumeUp("volup", R.string.swipe_act_volup),
    VolumeDown("voldown", R.string.swipe_act_voldown),
    CycleSound("sound", R.string.swipe_act_sound),
    Flashlight("torch", R.string.swipe_act_torch),
    NextNotif("nextnotif", R.string.swipe_act_nextnotif);

    companion object {
        fun from(key: String?, def: SwipeAction) = entries.firstOrNull { it.key == key } ?: def
    }
}

