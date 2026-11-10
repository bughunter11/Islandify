package com.islandify.app.ui.components
import com.islandify.app.R
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.runtime.saveable.rememberSaveable

import androidx.compose.ui.semantics.stateDescription

import androidx.compose.ui.semantics.semantics

import androidx.compose.ui.semantics.heading

import androidx.compose.ui.semantics.Role

import androidx.compose.foundation.selection.toggleable

import com.islandify.app.core.*
import com.islandify.app.island.*
import com.islandify.app.ui.onboarding.*
import com.islandify.app.ui.screens.*
import com.islandify.app.ui.theme.*

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.abs
import kotlin.math.min
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import kotlinx.coroutines.delay

// SPLITBIG  Demo, PreviewCard, SectionCard, PermRow yahin. Baaki ComponentsMotion / Sliders / Rows / Scaffold.kt me

internal enum class Demo(@StringRes val label: Int, val icon: ImageVector) {
    Idle(R.string.demo_idle, Icons.Rounded.RadioButtonUnchecked),
    Notification(R.string.demo_message, Icons.AutoMirrored.Rounded.Chat),
    Media(R.string.demo_music, Icons.Rounded.MusicNote),
    Timer(R.string.demo_timer, Icons.Rounded.Timer),
    Charging(R.string.demo_charging, Icons.Rounded.Bolt),
    Call(R.string.demo_call, Icons.Rounded.Call),
    Device(R.string.demo_earbuds, Icons.Rounded.Headphones),
}

internal fun Demo.toMode(): IslandMode = when (this) {
    Demo.Idle -> IslandMode.Idle
    Demo.Notification -> IslandMode.Notification("WhatsApp", "Rahul", "Is tomorrow's plan final? Let's leave at 10 AM.")
    Demo.Media -> IslandMode.Media("Blinding Lights", "The Weeknd", true)
    Demo.Timer -> IslandMode.Timer(42, 60)
    Demo.Charging -> IslandMode.Charging(82)
    Demo.Call -> IslandMode.Call("Mummy", true, 0L)
    Demo.Device -> IslandMode.Device("AirPods Pro", true)
}


/* ============================== PIECES ============================== */

/** Hero: the real DynamicIslandUi, updates live with the sliders. Tap to expand / collapse. */
@OptIn(ExperimentalLayoutApi::class)   // PREVIEWTIDY
@Composable
internal fun PreviewCard(scale: Float, w: Float, h: Float) {
    var demo by remember { mutableStateOf(Demo.Notification) }
    var level by remember { mutableStateOf(IslandLevel.Expanded) }
    val corner by IslandSettings.corner.collectAsState()
    val speedUser by IslandSettings.animSpeed.collectAsState()
    val speed = if (com.islandify.app.core.rememberReduceMotion()) 0f else speedUser   // MOTIONKIT (PATCH_10_11_12)
    val glow by IslandSettings.glow.collectAsState()
    val haptics by IslandSettings.haptics.collectAsState()
    val useAppAccent by IslandSettings.islandAppAccent.collectAsState()
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().reveal()
    ) {
        Column(Modifier.padding(bottom = 14.dp)) {
            // Dark "phone screen" stage
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                Color(0xFF0B0B0C)
                            )
                        )
                    ),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(Modifier.padding(top = 14.dp)) {
                    DynamicIslandUi(
                        mode = demo.toMode(),
                        level = level,
                        scale = scale.coerceAtMost(1.25f),
                        widthScale = w,
                        heightScale = h,
                        cornerFactor = corner,
                        animSpeed = speed,
                        glow = glow,
                        haptics = haptics,
                        appAccent = if (useAppAccent) MaterialTheme.colorScheme.primary else null,
                        onClick = {
                            level = if (level == IslandLevel.Compact) IslandLevel.Expanded else IslandLevel.Compact
                        },
                        onLongPress = {
                            level = if (level == IslandLevel.Large) IslandLevel.Expanded else IslandLevel.Large
                        },
                        onDismiss = { level = IslandLevel.Compact },
                    )
                }
            }
            Text(
                stringResource(R.string.preview_hint),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // PREVIEWTIDY: pehli row = size (Pill / Banner / Card), doosri = kya dikhana hai. Idle hata diya.
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = level == IslandLevel.Compact,
                    onClick = { level = IslandLevel.Compact },
                    label = { Text(stringResource(R.string.preview_pill)) }
                )
                FilterChip(
                    selected = level == IslandLevel.Banner,
                    onClick = { level = IslandLevel.Banner },
                    label = { Text(stringResource(R.string.preview_banner)) }
                )
                FilterChip(
                    selected = level == IslandLevel.Expanded,
                    onClick = { level = IslandLevel.Expanded },
                    label = { Text(stringResource(R.string.preview_card)) }
                )
            }
            FlowRow(
                Modifier.padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Demo.entries.filter { it != Demo.Idle }.forEach { d ->
                    FilterChip(
                        selected = demo == d,
                        onClick = { demo = d; if (level == IslandLevel.Compact) level = IslandLevel.Expanded },
                        label = { Text(stringResource(d.label)) },
                        leadingIcon = { Icon(d.icon, null, Modifier.size(18.dp)) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun SectionCard(
    title: String,
    icon: ImageVector,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = container,
        modifier = Modifier.fillMaxWidth().animateContentSize().reveal()
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
internal fun PermRow(icon: ImageVector, title: String, done: Boolean, onGrant: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, Modifier.weight(1f).padding(end = 8.dp), style = MaterialTheme.typography.bodyLarge)   // UIPOLISH
        if (done) {
            Icon(Icons.Rounded.CheckCircle, stringResource(R.string.common_done), tint = MaterialTheme.colorScheme.primary)
        } else {
            FilledTonalButton(onClick = onGrant) { Text(stringResource(R.string.perm_allow)) }
        }
    }
}

