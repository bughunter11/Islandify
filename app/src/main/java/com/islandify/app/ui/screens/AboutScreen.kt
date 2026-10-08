package com.islandify.app.ui.screens
import com.islandify.app.R
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes

import com.islandify.app.core.*
import com.islandify.app.island.*
import com.islandify.app.ui.components.*

import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** One gesture = one row. Add or remove items here and the screen updates by itself. */
private data class Gesture(val icon: ImageVector, @StringRes val action: Int, @StringRes val result: Int)

private val GESTURES = listOf(
    Gesture(Icons.Rounded.TouchApp, R.string.gesture_tap, R.string.gesture_tap_res),
    Gesture(Icons.Rounded.Widgets, R.string.gesture_card, R.string.gesture_card_res),
    Gesture(Icons.Rounded.ZoomOutMap, R.string.gesture_long, R.string.gesture_long_res),
    Gesture(Icons.Rounded.SwapHoriz, R.string.gesture_swipe_lr, R.string.gesture_swipe_lr_res),
    Gesture(Icons.Rounded.KeyboardArrowUp, R.string.gesture_swipe_up, R.string.gesture_swipe_up_res),
)

/** One permission = one row. [fix] opens the right settings screen. */
private class Status(val icon: ImageVector, @StringRes val title: Int, val ok: Boolean, val fix: () -> Unit)

/** Brands whose battery managers are known to kill background apps. */
private val AGGRESSIVE_BRANDS = listOf("xiaomi", "redmi", "poco", "oppo", "vivo", "realme", "oneplus", "samsung", "huawei", "honor")

private fun buildNumber(info: PackageInfo): Long =
    if (Build.VERSION.SDK_INT >= 28) info.longVersionCode
    else @Suppress("DEPRECATION") info.versionCode.toLong()

/**
 * About screen. Pass the same `tick` that Home gets: it changes every time the app
 * comes back to the front, so the permission status below is always fresh after the
 * user returns from Settings.
 */
@Composable
fun AboutScreen(tick: Int) {
    val ctx = LocalContext.current

    // App info: read from the installed package, nothing is typed by hand.
    val info = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull() }
    val version = info?.versionName ?: "1.0"
    val build = info?.let { buildNumber(it) } ?: 1L

    // Device info
    val brand = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
    val device = "$brand ${Build.MODEL}".trim()
    val android = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    val aggressive = AGGRESSIVE_BRANDS.any { Build.MANUFACTURER.orEmpty().lowercase().contains(it) }

    // Live status
    val running by IslandService.active.collectAsState(initial = IslandService.isActive())
    val statuses = remember(tick) {
        listOf(
            Status(Icons.Rounded.Accessibility, R.string.perm_accessibility, Perms.accessibility(ctx)) { Perms.openAccessibilitySettings(ctx) },
            Status(Icons.Rounded.NotificationsActive, R.string.perm_notification_access, Perms.listener(ctx)) { Perms.openListenerSettings(ctx) },
            Status(Icons.Rounded.Notifications, R.string.perm_show_notifications, Perms.post(ctx)) { Perms.openAppInfo(ctx) },
            Status(Icons.Rounded.Bluetooth, R.string.perm_bluetooth, Perms.bluetooth(ctx)) { Perms.openAppInfo(ctx) },
            Status(Icons.Rounded.BatteryChargingFull, R.string.perm_battery, Perms.battery(ctx)) { Perms.openBatterySettings(ctx) },
        )
    }
    val missing = statuses.count { !it.ok }
    val a11yOk = statuses.first().ok

    ScreenScaffold(stringResource(R.string.tab_about)) {
        AboutHero()

        SectionCard(title = stringResource(R.string.about_title, version), icon = Icons.Rounded.Info) {
            Text(
                stringResource(R.string.about_tagline),
                style = MaterialTheme.typography.bodyMedium
            )
            InfoRow(stringResource(R.string.about_build), build.toString())
            InfoRow(stringResource(R.string.about_device), device)
            InfoRow(stringResource(R.string.about_system), android)
            InfoRow(stringResource(R.string.about_island), stringResource(if (running) R.string.about_running else R.string.common_off))
        }

        SectionCard(title = stringResource(R.string.about_setup_title), icon = Icons.Rounded.CheckCircle) {
            Text(
                if (missing == 0) stringResource(R.string.about_all_set) else stringResource(R.string.about_needs_attention, missing, statuses.size),
                style = MaterialTheme.typography.bodyMedium
            )
            statuses.forEach { s -> PermRow(s.icon, stringResource(s.title), s.ok, s.fix) }
        }

        SectionCard(title = stringResource(R.string.about_gestures), icon = Icons.Rounded.TouchApp) {
            GESTURES.forEach { g -> GestureRow(g) }
        }

        // Only shown while taps cannot work, so it disappears once the service is on.
        if (!a11yOk) {
            SectionCard(title = stringResource(R.string.about_taps_title), icon = Icons.Rounded.TouchApp) {
                Text(
                    stringResource(R.string.about_taps_body),
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { Perms.openAccessibilitySettings(ctx) }) { Text(stringResource(R.string.about_btn_accessibility)) }
                    FilledTonalButton(onClick = { Perms.openAppInfo(ctx) }) { Text(stringResource(R.string.common_app_info)) }
                }
            }
        }

        SectionCard(title = stringResource(R.string.about_stopping_title), icon = Icons.Rounded.BatteryAlert) {
            Text(
                if (aggressive) stringResource(R.string.about_kill_brand, brand)
                else stringResource(R.string.about_kill_generic),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(stringResource(R.string.about_step1), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.about_step2), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.about_step3), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { Perms.openBatterySettings(ctx) }) { Text(stringResource(R.string.about_btn_battery)) }
                FilledTonalButton(onClick = { Perms.openAppInfo(ctx) }) { Text(stringResource(R.string.common_app_info)) }
                TextButton(onClick = {
                    runCatching {
                        ctx.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://dontkillmyapp.com"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }) { Text(stringResource(R.string.about_btn_guide)) }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            Modifier.width(72.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun GestureRow(g: Gesture) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(g.icon, null, Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(g.action), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                stringResource(g.result),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A little island that floats and "plays music" with moving waveform bars. */
@Composable
private fun AboutHero() {
    val inf = rememberInfiniteTransition(label = "hero")
    val floatY = inf.animateFloat(
        -5f, 5f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "floatY"
    )
    val b1 = inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "bar1")
    val b2 = inf.animateFloat(0.4f, 1f, infiniteRepeatable(tween(680), RepeatMode.Reverse), label = "bar2")
    val b3 = inf.animateFloat(0.25f, 0.9f, infiniteRepeatable(tween(440), RepeatMode.Reverse), label = "bar3")

    Box(
        Modifier.fillMaxWidth().padding(vertical = 12.dp).reveal(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier
                .graphicsLayer { translationY = floatY.value.dp.toPx() }
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF0B0B0C))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(Color(0xFF30D158)))
            Spacer(Modifier.width(40.dp))
            Row(
                Modifier.height(24.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(b1, b2, b3).forEach { v ->
                    Box(
                        Modifier
                            .width(4.dp)
                            .height(24.dp)
                            .graphicsLayer { scaleY = v.value }
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White)
                    )
                }
            }
        }
    }
}
