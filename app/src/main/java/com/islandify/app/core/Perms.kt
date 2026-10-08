package com.islandify.app.core

import com.islandify.app.island.*

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** All permission checks in one place. */
object Perms {
    /** Some ROMs have no screen for a settings intent: that used to crash the app. */
    private fun go(c: Context, i: Intent) { runCatching { c.startActivity(i) } }

    fun overlay(c: Context) = Settings.canDrawOverlays(c)

    fun listener(c: Context) =
        NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)

    /**
     * Is the Islandify accessibility service switched on in Settings?
     * It is what lets the island sit above the status bar and receive taps there.
     */
    fun accessibility(c: Context): Boolean {
        val enabled = runCatching {
            Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        }.getOrNull().orEmpty()
        if (enabled.isBlank()) return false
        val cn = ComponentName(c, IslandA11yService::class.java)
        val full = cn.flattenToString()
        val short = cn.flattenToShortString()
        return enabled.split(':').any { it.equals(full, true) || it.equals(short, true) }
    }

    fun post(c: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    fun bluetooth(c: Context) = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(c, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED

    fun battery(c: Context) = runCatching {
        c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)
    }.getOrDefault(false)

    /**
     * The app cannot work without these.
     * Accessibility is preferred (taps work everywhere); "draw over other apps" is the
     * older fallback where the island shows but taps in the status bar area are blocked by Android.
     */
    fun coreOk(c: Context) = (accessibility(c) || overlay(c)) && listener(c) && post(c)

    fun openOverlaySettings(c: Context) = go(c, 
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${c.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )

    fun openAccessibilitySettings(c: Context) = go(c, 
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )

    fun openListenerSettings(c: Context) = go(c, 
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )

    fun openBatterySettings(c: Context) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${c.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { c.startActivity(direct) }.onFailure {
            runCatching { c.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    /**
     * On sideloaded apps the Accessibility / Notification access toggle can be greyed out.
     * App info > menu > "Allow restricted settings" fixes that.
     */
    fun openAppInfo(c: Context) = go(c, 
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${c.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

/** Island service on / off. */
object IslandControl {
    fun start(c: Context) {
        IslandSettings.enabled.value = true
        IslandSettings.save()
        when {
            Perms.accessibility(c) || Perms.overlay(c) ->
                runCatching { ContextCompat.startForegroundService(c, Intent(c, IslandService::class.java)) }
            else -> Perms.openAccessibilitySettings(c)
        }
    }

    fun stop(c: Context) {
        IslandSettings.enabled.value = false
        IslandSettings.save()
        c.stopService(Intent(c, IslandService::class.java))
    }
}
