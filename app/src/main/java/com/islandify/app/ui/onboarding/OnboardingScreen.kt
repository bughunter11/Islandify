package com.islandify.app.ui.onboarding
import com.islandify.app.R
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes

import com.islandify.app.core.*
import com.islandify.app.island.*
import com.islandify.app.ui.components.*
import com.islandify.app.ui.screens.*
import com.islandify.app.ui.theme.*

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import kotlinx.coroutines.delay

private class Step(
    val icon: ImageVector,
    @StringRes val title: Int,
    @StringRes val why: Int,
    val required: Boolean,
    val granted: Boolean,
    val ask: () -> Unit,
    @StringRes val extraLabel: Int? = null,
    val extra: (() -> Unit)? = null,
    val skippable: Boolean = !required,
)

/**
 * Shown the first time (or whenever a required permission is removed).
 * The "Open Islandify" button stays disabled until the required permissions are granted.
 */
@Composable
fun OnboardingScreen(tick: Int) {
    val ctx = LocalContext.current
    val postLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    val a11y = remember(tick) { Perms.accessibility(ctx) }
    val overlay = remember(tick) { Perms.overlay(ctx) }
    val listener = remember(tick) { Perms.listener(ctx) }
    val post = remember(tick) { Perms.post(ctx) }
    val bt = remember(tick) { Perms.bluetooth(ctx) }
    val battery = remember(tick) { Perms.battery(ctx) }
    val skipped = remember { mutableStateListOf<Int>() }

    val steps = buildList {
        add(Step(
            Icons.Rounded.Accessibility, R.string.perm_accessibility,
            R.string.onb_a11y_why,
            true, a11y, { Perms.openAccessibilitySettings(ctx) },
            R.string.common_app_info, { Perms.openAppInfo(ctx) },
            // Users who already granted "draw over other apps" can carry on without it
            skippable = overlay
        ))
        add(Step(
            Icons.Rounded.NotificationsActive, R.string.perm_notification_access,
            R.string.onb_listener_why,
            true, listener, { Perms.openListenerSettings(ctx) },
            R.string.common_app_info, { Perms.openAppInfo(ctx) }
        ))
        if (Build.VERSION.SDK_INT >= 33) add(Step(
            Icons.Rounded.Notifications, R.string.perm_show_notifications,
            R.string.onb_post_why,
            true, post, { postLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        ))
        if (Build.VERSION.SDK_INT >= 31) add(Step(
            Icons.Rounded.Bluetooth, R.string.perm_nearby_devices,
            R.string.onb_bt_why,
            false, bt, { btLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) }
        ))
        add(Step(
            Icons.Rounded.BatteryChargingFull, R.string.perm_battery,
            R.string.onb_battery_why,
            false, battery, { Perms.openBatterySettings(ctx) }
        ))
    }

    val coreOk = (a11y || overlay) && listener && post
    val doneCount = steps.count { it.granted }
    val current = steps.firstOrNull { !it.granted && it.title !in skipped }
    val progress by animateFloatAsState(doneCount / steps.size.toFloat(), spring(), label = "progress")

    val counter = remember { RevealCounter() }
    LaunchedEffect(Unit) {
        delay(900)
        counter.settled = true
    }

    CompositionLocalProvider(LocalReveal provides counter) {
    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { pad ->
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.reveal()) { HeroIsland() }
            Text(
                stringResource(R.string.onb_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.reveal()
            )
            Text(
                stringResource(R.string.onb_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.reveal()
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).reveal(),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )

            steps.forEach { st ->
                StepCard(
                    st,
                    isCurrent = st === current,
                    onSkip = { skipped.add(st.title) }
                )
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    IslandSettings.onboarded.value = true
                    IslandSettings.save()
                },
                enabled = coreOk,
                modifier = Modifier.fillMaxWidth().height(56.dp).reveal()
            ) {
                Icon(Icons.Rounded.CheckCircle, null)
                Spacer(Modifier.width(8.dp))
                Crossfade(targetState = coreOk, label = "cta") { ok ->
                    Text(stringResource(if (ok) R.string.onb_open else R.string.onb_grant_first))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    }
}

@Composable
private fun StepCard(st: Step, isCurrent: Boolean, onSkip: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = when {
            st.granted -> cs.primaryContainer
            isCurrent -> cs.surfaceContainerHighest
            else -> cs.surfaceContainer
        },
        animationSpec = tween(350),
        label = "stepColor"
    )
    val badge by animateColorAsState(
        targetValue = if (st.granted) cs.primary else cs.secondaryContainer,
        animationSpec = tween(350),
        label = "stepBadge"
    )
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = container,
        modifier = Modifier.fillMaxWidth().animateContentSize(spring(0.8f, 380f)).reveal()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(badge),
                    contentAlignment = Alignment.Center
                ) {
                    // The icon pops into a tick when the permission is granted
                    AnimatedContent(
                        targetState = st.granted,
                        contentAlignment = Alignment.Center,
                        transitionSpec = {
                            (scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), 0.4f) + fadeIn(tween(150)))
                                .togetherWith(scaleOut(targetScale = 0.4f) + fadeOut(tween(100)))
                        },
                        label = "stepIcon"
                    ) { granted ->
                        Icon(
                            if (granted) Icons.Rounded.Check else st.icon, null,
                            tint = if (granted) cs.onPrimary else cs.onSecondaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(st.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (!st.required && !isCurrent && !st.granted) {
                        Text(stringResource(R.string.onb_optional), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    }
                }
                AnimatedVisibility(visible = st.granted, enter = fadeIn(tween(250)), exit = fadeOut(tween(120))) {
                    Text(stringResource(R.string.common_done), style = MaterialTheme.typography.labelLarge, color = cs.primary)
                }
            }
            AnimatedVisibility(
                visible = isCurrent,
                enter = fadeIn(tween(250)) + expandVertically(),
                exit = fadeOut(tween(120)) + shrinkVertically()
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(st.why), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = st.ask) { Text(stringResource(R.string.perm_allow)) }
                    if (st.extra != null && st.extraLabel != null) {
                        FilledTonalButton(onClick = st.extra) { Text(stringResource(st.extraLabel)) }
                    }
                    if (st.skippable) TextButton(onClick = onSkip) { Text(stringResource(R.string.onb_later)) }
                }
              }
            }
        }
    }
}

/** Small animated island at the top that cycles through different states. */
@Composable
private fun HeroIsland() {
    val demos = remember {
        listOf(Demo.Media.toMode(), Demo.Notification.toMode(), Demo.Charging.toMode(), Demo.Call.toMode())
    }
    var i by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2600)
            i = (i + 1) % demos.size
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), Color(0xFF0B0B0C)))
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(Modifier.padding(top = 18.dp)) {
            DynamicIslandUi(
                mode = demos[i],
                level = IslandLevel.Expanded,
                scale = 0.92f,
                onClick = {},
            )
        }
    }
}
