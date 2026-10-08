package com.islandify.app.ui.screens
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.vector.ImageVector
import com.islandify.app.R
import androidx.compose.ui.res.stringResource

import androidx.compose.ui.semantics.Role

import androidx.compose.foundation.selection.toggleable

import com.islandify.app.core.*
import com.islandify.app.island.*
import com.islandify.app.ui.components.*
import com.islandify.app.ui.onboarding.*
import com.islandify.app.ui.theme.*

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(tick: Int) {
    val ctx = LocalContext.current
    val running by IslandService.active.collectAsState(initial = IslandService.isActive())
    val scale by IslandSettings.scale.collectAsState()
    val w by IslandSettings.widthScale.collectAsState()
    val h by IslandSettings.heightScale.collectAsState()

    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val btOk = remember(tick) { Perms.bluetooth(ctx) }
    val batteryOk = remember(tick) { Perms.battery(ctx) }
    val a11yOk = remember(tick) { Perms.accessibility(ctx) }

    // On/off card: colors and icon animate when the island is switched
    val cardColor by animateColorAsState(
        targetValue = if (running) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = tween(450),
        label = "powerCard"
    )
    val cardContent by animateColorAsState(
        targetValue = if (running) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(450),
        label = "powerCardContent"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (running) 1f else 0.82f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "powerIcon"
    )

    ScreenScaffold(stringResource(R.string.app_name), trailing = { LiveBadge(running) }) {
        /* ---------- On / Off ---------- */
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = cardColor,
            contentColor = cardContent,
            modifier = Modifier.fillMaxWidth().reveal()
        ) {
            Row(
                Modifier
                    .toggleable(
                        value = running,
                        role = Role.Switch,
                        onValueChange = { on -> if (on) IslandControl.start(ctx) else IslandControl.stop(ctx) }
                    )
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.PowerSettingsNew, null,
                    Modifier.size(26.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                )
                Spacer(Modifier.width(14.dp))
                AnimatedContent(
                    targetState = running,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 3 })
                            .togetherWith(fadeOut(tween(120)) + slideOutVertically(tween(160)) { -it / 3 })
                    },
                    label = "powerText"
                ) { isOn ->
                    Column {
                        Text(
                            stringResource(if (isOn) R.string.home_island_on else R.string.home_island_off),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            stringResource(if (isOn) R.string.home_island_on_sub else R.string.home_island_off_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(checked = running, onCheckedChange = null)
            }
        }

        /* ---------- Taps need the accessibility service ---------- */
        if (!a11yOk) {
            SectionCard(
                title = stringResource(R.string.home_taps_title),
                icon = Icons.Rounded.TouchApp,
                container = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    stringResource(R.string.home_taps_body),
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { Perms.openAccessibilitySettings(ctx) }) { Text(stringResource(R.string.home_open_settings)) }
                    FilledTonalButton(onClick = { Perms.openAppInfo(ctx) }) { Text(stringResource(R.string.common_app_info)) }
                }
                Text(
                    stringResource(R.string.home_taps_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        PreviewCard(scale, w, h)

        /* ---------- Test ---------- */
        SectionCard(title = stringResource(R.string.home_test_title), icon = Icons.Rounded.AutoAwesome) {
            // Grouped, two equal-width columns: tidy rows instead of chips scattered by their own width
            TestGroup(R.string.test_group_basic, listOf(
                TestAction(R.string.demo_message, Icons.AutoMirrored.Rounded.Chat) {
                    IslandController.flash(
                        IslandMode.Notification("WhatsApp", "Rahul", "Is tomorrow's plan final? Let's leave at 10 AM.")
                    )
                },
                TestAction(R.string.demo_charging, Icons.Rounded.Bolt) {
                    IslandController.flash(IslandMode.Charging(82))
                },
                TestAction(R.string.test_timer_60, Icons.Rounded.Timer) {
                    IslandController.startTimer(60)
                },
                TestAction(R.string.demo_music, Icons.Rounded.MusicNote) {
                    IslandController.setMedia(IslandMode.Media("Blinding Lights", "The Weeknd", true))
                },
                TestAction(R.string.demo_call, Icons.Rounded.Call) {
                    IslandController.setCall("Mummy", true, null, null, canAnswer = true)
                },
                TestAction(R.string.test_end_call, Icons.Rounded.CallEnd) {
                    IslandController.clearCall()
                },
                TestAction(R.string.demo_earbuds, Icons.Rounded.Headphones) {
                    IslandController.flash(IslandMode.Device("AirPods Pro", true), 3500)
                },
                TestAction(R.string.trigger_battery, Icons.Rounded.BatteryAlert) {
                    IslandController.flash(IslandMode.LowBattery(15), 5000)
                },
                TestAction(R.string.test_silent, Icons.AutoMirrored.Rounded.VolumeOff) {
                    IslandController.flash(IslandMode.Toggle(ToggleKind.Silent, true), 2500)
                },
                TestAction(R.string.test_flashlight, Icons.Rounded.FlashOn) {
                    IslandController.flash(IslandMode.Toggle(ToggleKind.Flashlight, true), 2500)
                },
            ))
            TestGroup(R.string.test_group_live, listOf(
                TestAction(R.string.trigger_navigation, Icons.Rounded.Navigation) {
                    IslandController.setLive(
                        IslandMode.Live(LiveKind.Navigation, "Maps", "Turn left onto MG Road", "12 min, 4.2 km"),
                        "demo", null, null
                    )
                },
                TestAction(R.string.test_food_order, Icons.Rounded.DeliveryDining) {
                    IslandController.setLive(
                        IslandMode.Live(LiveKind.Tracking, "Zomato", "Order on the way", "Arriving in 12 min"),
                        "demo", null, null
                    )
                },
                TestAction(R.string.test_clear_live, Icons.Rounded.Close) {
                    IslandController.clearLive()
                },
            ))
            TestGroup(R.string.test_group_combo, listOf(
                TestAction(R.string.test_three_messages, Icons.AutoMirrored.Rounded.Chat) {
                    IslandController.flash(IslandMode.Notification("WhatsApp", "Rahul", "Is tomorrow's plan final?"))
                    IslandController.flash(IslandMode.Notification("WhatsApp", "Priya", "Call me when you are free"))
                    IslandController.flash(IslandMode.Notification("Messages", "Mummy", "Dinner at 8"))
                },
                TestAction(R.string.test_nav_music, Icons.Rounded.Navigation) {
                    IslandController.setMedia(IslandMode.Media("Blinding Lights", "The Weeknd", true))
                    IslandController.setLive(
                        IslandMode.Live(LiveKind.Navigation, "Maps", "Turn left onto MG Road", "12 min, 4.2 km"),
                        "demo", null, null
                    )
                },
                TestAction(R.string.test_clear_music, Icons.Rounded.Close) {
                    IslandController.setMedia(null)
                },
            ))
            Text(
                stringResource(R.string.home_test_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        /* ---------- Optional permissions still missing ---------- */
        if (!btOk || !batteryOk) {
            SectionCard(
                title = stringResource(R.string.home_best_title),
                icon = Icons.Rounded.ErrorOutline,
                container = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                if (!btOk) {
                    PermRow(Icons.Rounded.Bluetooth, stringResource(R.string.perm_bluetooth), false) {
                        btLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                }
                if (!batteryOk) {
                    PermRow(Icons.Rounded.BatteryChargingFull, stringResource(R.string.perm_battery), false) {
                        Perms.openBatterySettings(ctx)
                    }
                }
            }
        }
    }
}

private class TestAction(@StringRes val label: Int, val icon: ImageVector, val run: () -> Unit)

/** A small caption and the actions in rows of two equal-width chips. */
@Composable
private fun TestGroup(@StringRes title: Int, actions: List<TestAction>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        actions.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { a ->
                    BouncyChip(stringResource(a.label), a.icon, Modifier.weight(1f), a.run)
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
