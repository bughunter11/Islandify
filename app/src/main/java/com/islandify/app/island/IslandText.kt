package com.islandify.app.island

import android.content.Context
import androidx.annotation.StringRes
import com.islandify.app.R
import com.islandify.app.core.AppLanguage

// ISLANDI18N: island ke andar ka text (banner / card / bubble) ab string resources se aata hai.
// Services (IslandService, IslandA11yService, IslandListener) ka context in-app language se wrapped
// nahi hota, isliye yahan har baar AppLanguage.wrap se bana context use hota hai (cache ke saath).

internal object IslandText {
    private var cacheCode: String? = null
    private var cacheCtx: Context? = null

    private fun ctx(): Context? {
        val base = IslandController.appCtx ?: return null
        val code = AppLanguage.saved(base)
        val c = cacheCtx
        if (c != null && code == cacheCode) return c
        val w = AppLanguage.wrap(base)
        cacheCode = code
        cacheCtx = w
        return w
    }

    fun s(@StringRes id: Int, vararg args: Any): String {
        val c = ctx() ?: return ""
        return if (args.isEmpty()) c.getString(id) else c.getString(id, *args)
    }
}

/** Short names for the island strings, so call sites stay readable. */
internal object Isl {
    val charging get() = IslandText.s(R.string.isl_charging)
    val chargerRemoved get() = IslandText.s(R.string.isl_charger_removed)
    val powerConnected get() = IslandText.s(R.string.isl_power_connected)
    val unplugged get() = IslandText.s(R.string.isl_unplugged)
    val batteryLow get() = IslandText.s(R.string.isl_battery_low)
    val batteryVeryLow get() = IslandText.s(R.string.isl_battery_very_low)
    val plugIn get() = IslandText.s(R.string.isl_plug_in)
    val connected get() = IslandText.s(R.string.isl_connected)
    val headphones get() = IslandText.s(R.string.isl_headphones)
    val bluetooth get() = IslandText.s(R.string.isl_bluetooth)
    val chargerConnected get() = IslandText.s(R.string.isl_charger_connected)
    val chargerDisconnected get() = IslandText.s(R.string.isl_charger_disconnected)
    val battery get() = IslandText.s(R.string.isl_battery)
    val lowBattery get() = IslandText.s(R.string.isl_low_battery)
    val btDevice get() = IslandText.s(R.string.isl_bt_device)
    val on get() = IslandText.s(R.string.isl_on)
    val off get() = IslandText.s(R.string.isl_off)
    fun connectedBatt(p: Int) = IslandText.s(R.string.isl_connected_batt, p)
    fun percentLeft(p: Int) = IslandText.s(R.string.isl_percent_left, p)

    val reply get() = IslandText.s(R.string.isl_reply)
    val replyHint get() = IslandText.s(R.string.isl_reply_hint)
    val sent get() = IslandText.s(R.string.isl_sent)
    val replySent get() = IslandText.s(R.string.isl_reply_sent)
    val markRead get() = IslandText.s(R.string.isl_mark_read)
    val newNotification get() = IslandText.s(R.string.isl_new_notification)
    val incomingCall get() = IslandText.s(R.string.isl_incoming_call)
    val openBtn get() = IslandText.s(R.string.isl_open)
    val dismissBtn get() = IslandText.s(R.string.isl_dismiss)

    val timer get() = IslandText.s(R.string.isl_timer)
    val clockTimer get() = IslandText.s(R.string.isl_clock_timer)
    val timesUp get() = IslandText.s(R.string.isl_times_up)
    val timerComplete get() = IslandText.s(R.string.isl_timer_complete)
    val timerRunning get() = IslandText.s(R.string.isl_timer_running)
    fun complete(what: String) = IslandText.s(R.string.isl_complete, what)
    val music get() = IslandText.s(R.string.isl_music)
    fun paused(artist: String) = IslandText.s(R.string.isl_paused, artist)
    val unknown get() = IslandText.s(R.string.isl_unknown)

    fun durHr(n: Int) = IslandText.s(R.string.isl_dur_hr, n)
    fun durMin(n: Int) = IslandText.s(R.string.isl_dur_min, n)
    fun durSec(n: Int) = IslandText.s(R.string.isl_dur_sec, n)
    fun secShort(n: Int) = IslandText.s(R.string.isl_sec_short, n)

    // toggle bubble labels
    val lSilent get() = IslandText.s(R.string.isl_l_silent)
    val lVibrate get() = IslandText.s(R.string.isl_l_vibrate)
    val lDnd get() = IslandText.s(R.string.isl_l_dnd)
    val lFlash get() = IslandText.s(R.string.isl_l_flash)
    val lWifi get() = IslandText.s(R.string.isl_l_wifi)
    val lAirplane get() = IslandText.s(R.string.isl_l_airplane)
    val lHotspot get() = IslandText.s(R.string.isl_l_hotspot)
    val lVpn get() = IslandText.s(R.string.isl_l_vpn)

    // toggle titles
    val tSilentOn get() = IslandText.s(R.string.isl_t_silent_on)
    val tSilentOff get() = IslandText.s(R.string.isl_t_silent_off)
    val tVibrate get() = IslandText.s(R.string.isl_t_vibrate)
    val tDndOn get() = IslandText.s(R.string.isl_t_dnd_on)
    val tDndOff get() = IslandText.s(R.string.isl_t_dnd_off)
    val tFlashOn get() = IslandText.s(R.string.isl_t_flash_on)
    val tFlashOff get() = IslandText.s(R.string.isl_t_flash_off)
    val tWifiOn get() = IslandText.s(R.string.isl_t_wifi_on)
    val tWifiOff get() = IslandText.s(R.string.isl_t_wifi_off)
    val tAirOn get() = IslandText.s(R.string.isl_t_air_on)
    val tAirOff get() = IslandText.s(R.string.isl_t_air_off)
    val tHotspotOn get() = IslandText.s(R.string.isl_t_hotspot_on)
    val tHotspotOff get() = IslandText.s(R.string.isl_t_hotspot_off)
    val tVpnOn get() = IslandText.s(R.string.isl_t_vpn_on)
    val tVpnOff get() = IslandText.s(R.string.isl_t_vpn_off)
    val tCamera get() = IslandText.s(R.string.isl_t_camera)
    val tMic get() = IslandText.s(R.string.isl_t_mic)
    val tRecOn get() = IslandText.s(R.string.isl_t_rec_on)
    val tRecOff get() = IslandText.s(R.string.isl_t_rec_off)

    // toggle subtitles
    val sSilentOn get() = IslandText.s(R.string.isl_s_silent_on)
    val sSilentOff get() = IslandText.s(R.string.isl_s_silent_off)
    val sVibrate get() = IslandText.s(R.string.isl_s_vibrate)
    val sDndOn get() = IslandText.s(R.string.isl_s_dnd_on)
    val sDndOff get() = IslandText.s(R.string.isl_s_dnd_off)
    val sFlashOn get() = IslandText.s(R.string.isl_s_flash_on)
    val sFlashOff get() = IslandText.s(R.string.isl_s_flash_off)
    val sWifiOn get() = IslandText.s(R.string.isl_s_wifi_on)
    val sWifiOff get() = IslandText.s(R.string.isl_s_wifi_off)
    val sAirOn get() = IslandText.s(R.string.isl_s_air_on)
    val sAirOff get() = IslandText.s(R.string.isl_s_air_off)
    val sHotspotOn get() = IslandText.s(R.string.isl_s_hotspot_on)
    val sHotspotOff get() = IslandText.s(R.string.isl_s_hotspot_off)
    val sVpnOn get() = IslandText.s(R.string.isl_s_vpn_on)
    val sVpnOff get() = IslandText.s(R.string.isl_s_vpn_off)
    val sCamera get() = IslandText.s(R.string.isl_s_camera)
    val sMic get() = IslandText.s(R.string.isl_s_mic)
    val sRecOn get() = IslandText.s(R.string.isl_s_rec_on)
    val sRecOff get() = IslandText.s(R.string.isl_s_rec_off)
}

/**
 * ISLANDI18N: Timer ka flash banner app = "Timer" ko key ki tarah use karta hai (BubbleStoreLive me
 * `m.app == "Timer"`), isliye key English hi rehti hai aur sirf dikhane ke waqt translate hoti hai.
 */
internal fun IslandMode.Notification.displayApp(): String = if (app == "Timer") Isl.timer else app
