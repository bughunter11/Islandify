package com.islandify.app.island

// STATUSKIT-FILE
// (1) rememberIslandSuppressed = Quiet hours + games / video app me island chhupana
// (2) ChargeInfo = charging detail (fast / wireless, ~watt, full hone me time)
// (3) BtBattery = earbuds ka battery %

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.islandify.app.R
import com.islandify.app.core.IslandSettings
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun quietNowMinute(): Int {
    val c = java.util.Calendar.getInstance()
    return c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)
}

/** from == to -> kabhi nahi. from > to -> raat paar karta window (jaise 23:00 se 07:00). */
internal fun inQuietWindow(now: Int, from: Int, to: Int): Boolean = when {
    from == to -> false
    from < to -> now >= from && now < to
    else -> now >= from || now < to
}

private val KNOWN_FULLSCREEN_APPS = setOf(
    // video
    "com.google.android.youtube", "com.netflix.mediaclient", "com.amazon.avod.thirdpartyclient",
    "in.startv.hotstar", "com.disney.disneyplus", "com.jio.media.ondemand", "com.sonyliv",
    "com.graymatrix.did", "com.mxtech.videoplayer.ad", "com.mxtech.videoplayer.pro",
    "org.videolan.vlc", "tv.twitch.android.app",
    // games
    "com.pubg.imobile", "com.tencent.ig", "com.dts.freefireth", "com.dts.freefiremax",
    "com.activision.callofduty.shooter", "com.miHoYo.GenshinImpact", "com.mojang.minecraftpe",
    "com.roblox.client", "com.supercell.clashofclans", "com.kiloo.subwaysurf",
    "com.mobile.legends", "com.gameloft.android.ANMP.GloftA9HM",
)

/** Game / video app? App ki apni category + FLAG_IS_GAME + ek chhoti known list. */
internal fun isGameOrVideoApp(ctx: Context, pkg: String): Boolean {
    if (pkg in KNOWN_FULLSCREEN_APPS) return true
    return runCatching {
        val info = ctx.packageManager.getApplicationInfo(pkg, 0)
        @Suppress("DEPRECATION")
        val game = (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
        game || info.category == ApplicationInfo.CATEGORY_GAME || info.category == ApplicationInfo.CATEGORY_VIDEO
    }.getOrDefault(false)
}

/** true = island abhi chhupa rehna chahiye (Quiet hours ya game / video app). Call phir bhi dikhti hai. */
@Composable
internal fun rememberIslandSuppressed(): Boolean {
    val ctx = LocalContext.current.applicationContext
    val quietOn by IslandSettings.quietOn.collectAsState()
    val from by IslandSettings.quietFrom.collectAsState()
    val to by IslandSettings.quietTo.collectAsState()
    val fsOn by IslandSettings.hideFullscreen.collectAsState()
    val fg by IslandVisibility.foreground.collectAsState()
    val nowMin by produceState(initialValue = quietNowMinute(), quietOn) {
        if (!quietOn) return@produceState
        while (true) {
            value = quietNowMinute()
            delay(20_000L)
        }
    }
    val pkg = fg
    val fullscreenApp = remember(pkg, fsOn) { fsOn && pkg != null && isGameOrVideoApp(ctx, pkg) }
    return (quietOn && inQuietWindow(nowMin, from, to)) || fullscreenApp
}

internal object ChargeInfo {
    const val SEP = " \u00B7 "

    /** "Fast charging . ~24 W . 1 h 5 min to full" (jo cheez phone se mile wahi). Khali = kuch nahi mila. */
    fun detail(ctx: Context): String = runCatching {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        var mv = i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        if (mv in 1..100) mv *= 1000
        val cur0 = runCatching { bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }.getOrDefault(0)
        val raw = if (cur0 == Int.MIN_VALUE) 0 else abs(cur0)
        val ma = if (raw > 20000) raw / 1000 else raw          // kuch phone uA dete hain, kuch mA
        val watts = (ma.toLong() * mv.toLong() / 1_000_000.0).roundToInt()
        val parts = ArrayList<String>()
        if (plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS) parts.add(ctx.getString(R.string.charge_wireless))
        else if (watts >= 18) parts.add(ctx.getString(R.string.charge_fast))
        if (watts in 1..200) parts.add("~$watts W")
        if (Build.VERSION.SDK_INT >= 28) {
            val ms = runCatching { bm.computeChargeTimeRemaining() }.getOrDefault(-1L)
            if (ms > 60_000L) {
                val mins = (ms / 60_000L).toInt()
                val t = if (mins >= 60) ctx.getString(R.string.charge_time_hm, mins / 60, mins % 60)
                else ctx.getString(R.string.charge_time_m, mins)
                parts.add(ctx.getString(R.string.charge_left, t))
            }
        }
        parts.joinToString(SEP)
    }.getOrDefault("")
}

internal object BtBattery {
    /** Bluetooth stack ka (hidden) broadcast: earbuds ka battery % badla. */
    const val ACTION = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
    private const val EXTRA_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
    private const val MAX_AGE_MS = 5 * 60_000L
    private val seen = HashMap<String, Pair<Int, Long>>()   // address -> (percent, uptime)

    private fun idOf(dev: BluetoothDevice?): String =
        runCatching { dev?.address }.getOrNull() ?: dev?.toString().orEmpty()

    fun onBroadcast(i: Intent) {
        val dev: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33)
            i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else @Suppress("DEPRECATION") i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        val lvl = i.getIntExtra(EXTRA_LEVEL, -1)
        if (lvl in 0..100) seen[idOf(dev)] = lvl to SystemClock.uptimeMillis()
    }

    /** 0..100, ya -1 = pata nahi (phone / earbuds batate hi nahi, ya abhi tak aaya nahi). */
    fun read(dev: BluetoothDevice?): Int {
        val id = idOf(dev)
        seen[id]?.let { (p, at) -> if (SystemClock.uptimeMillis() - at < MAX_AGE_MS) return p }
        if (dev == null) return -1
        val v = runCatching {
            BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(dev) as Int
        }.getOrDefault(-1)
        if (v !in 0..100) return -1
        seen[id] = v to SystemClock.uptimeMillis()
        return v
    }
}
