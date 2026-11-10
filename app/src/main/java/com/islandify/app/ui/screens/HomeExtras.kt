package com.islandify.app.ui.screens

// HOMEKIT: Home ke do extra card: Quick switches + "Test on the real island"

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.islandify.app.R
import com.islandify.app.core.*
import com.islandify.app.island.*
import com.islandify.app.ui.components.*

/** Sabse kaam ke 5 switch seedha Home par (baaki Activities tab me). */
@Composable
internal fun HomeQuickCard() {
    val on by IslandSettings.triggers.collectAsState()
    SectionCard(title = stringResource(R.string.home_quick_title), icon = Icons.Rounded.Tune) {
        listOf(Trigger.Notifications, Trigger.Media, Trigger.Calls, Trigger.Charging, Trigger.Timer).forEach { t ->
            ToggleRow(
                icon = t.icon(),
                title = stringResource(t.label),
                checked = t.key in on,
                onChange = { IslandSettings.setTrigger(t, it) }
            )
        }
        CallPermRow()   // CALLCTL
        Text(
            stringResource(R.string.home_quick_more),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// CALLCTL: cut / uthana ke liye Phone permission (kuch dialer notification me ye buttons dete hi nahi)
@Composable
internal fun CallPermRow() {
    if (android.os.Build.VERSION.SDK_INT < 26) return
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val on by IslandSettings.triggers.collectAsState()
    val granted = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, "android.permission.ANSWER_PHONE_CALLS") ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val denied = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { ok ->
        granted.value = ok
        if (!ok) denied.value = denied.value + 1
    }
    if (Trigger.Calls.key !in on || granted.value) return
    Text(
        stringResource(R.string.call_perm_sub),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    androidx.compose.material3.FilledTonalButton(onClick = {
        if (denied.value >= 2) {
            runCatching {
                ctx.startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:" + ctx.packageName)
                    )
                )
            }
        } else {
            launcher.launch("android.permission.ANSWER_PHONE_CALLS")
        }
    }) { Text(stringResource(R.string.call_perm_btn)) }
}

/** Asli island par fake events chalao (preview card sirf app ke andar dikhta hai). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeTestCard() {
    val running by IslandService.active.collectAsState(initial = IslandService.isActive())
    SectionCard(title = stringResource(R.string.home_test_title), icon = Icons.Rounded.PlayArrow) {
        if (!running) {
            Text(
                stringResource(R.string.home_island_off_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        HomeTestGroup(R.string.test_group_basic) {
            BouncyChip(stringResource(R.string.demo_message), Icons.AutoMirrored.Rounded.Chat) { homeTestMessage(0) }
            BouncyChip(stringResource(R.string.demo_charging), Icons.Rounded.Bolt) {
                IslandController.flash(IslandMode.Charging(82), 3500)
            }
            BouncyChip(stringResource(R.string.demo_earbuds), Icons.Rounded.Headphones) {
                IslandController.flash(IslandMode.Device("AirPods Pro", true), 3500)
            }
            BouncyChip(stringResource(R.string.test_silent)) {
                IslandController.flash(IslandMode.Toggle(ToggleKind.Silent, true), 2500)
            }
            BouncyChip(stringResource(R.string.test_flashlight)) {
                IslandController.flash(IslandMode.Toggle(ToggleKind.Flashlight, true), 2500)
            }
        }
        HomeTestGroup(R.string.test_group_live) {
            BouncyChip(stringResource(R.string.test_timer_60), Icons.Rounded.Timer) { IslandController.startTimer(60) }
            BouncyChip(stringResource(R.string.demo_call), Icons.Rounded.Call) {
                IslandController.setCall("Mummy", true, null, null, key = "islandify_test_call", canAnswer = true)
            }
            BouncyChip(stringResource(R.string.test_end_call), Icons.Rounded.Close) { IslandController.clearCall() }
            BouncyChip(stringResource(R.string.test_food_order)) {
                IslandController.setLive(
                    IslandMode.Live(LiveKind.Tracking, "Zomato", "Order on the way", "Arriving in 12 min", 60),
                    "islandify_test_food", null, null
                )
            }
            BouncyChip(stringResource(R.string.test_clear_live), Icons.Rounded.Close) { IslandController.clearLive() }
        }
        HomeTestGroup(R.string.test_group_combo) {
            BouncyChip(stringResource(R.string.test_three_messages), Icons.AutoMirrored.Rounded.Chat) {
                for (i in 0..2) homeTestMessage(i)
            }
            BouncyChip(stringResource(R.string.test_nav_music), Icons.Rounded.MusicNote) {
                IslandController.setLive(
                    IslandMode.Live(LiveKind.Navigation, "Maps", "Turn left in 200 m", "Sector 21 Road"),
                    "islandify_test_nav", null, null
                )
                IslandController.setMedia(
                    IslandMode.Media(
                        "Blinding Lights", "The Weeknd", true,
                        durationMs = 200_000L, positionMs = 30_000L,
                        updatedAt = SystemClock.elapsedRealtime()
                    )
                )
            }
            BouncyChip(stringResource(R.string.test_clear_music), Icons.Rounded.Close) { IslandController.setMedia(null) }
        }
        Text(
            stringResource(R.string.home_test_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeTestGroup(@StringRes title: Int, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    }
}

private val HOME_TEST_MSGS = listOf(
    Triple("WhatsApp", "Rahul", "Is tomorrow's plan final? Let's leave at 10 AM."),
    Triple("Telegram", "Priya", "Sent you the notes, check once."),
    Triple("Messages", "Mummy", "Dinner is ready, come down."),
)

/** Ek test message: bubble bhi banta hai aur island par banner bhi aata hai (asli notification jaisa). */
private fun homeTestMessage(i: Int) {
    val (app, who, text) = HOME_TEST_MSGS[i % HOME_TEST_MSGS.size]
    val key = "islandify_test_$i"
    val direct = IslandSettings.directCardAll.value   // DIRECTCARD2 (test msg me Reply nahi hota, isliye sirf pehla toggle)
    BubbleStore.add(
        BubbleItem(
            key = key, pkg = "com.islandify.test.$i", app = app,
            title = who, text = text,
            icon = null, accent = null, open = null, reply = null, markRead = null,
            keep = direct,
        )
    )
    if (direct) { BubbleStore.openDirect(key); return }   // DIRECTCARD2: toggle ON = seedha card
    IslandController.flash(IslandMode.Notification(app, who, text), durationMs = 5500L, key = key)
}
