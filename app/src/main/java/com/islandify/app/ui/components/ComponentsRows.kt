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

// SPLITBIG  ToggleRow, Trigger.icon, Trigger.hint

@Composable
internal fun ToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null,
    modifier: Modifier = Modifier,   // SEARCHKIT
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tint by animateColorAsState(
            targetValue = if (checked) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            animationSpec = tween(250),
            label = "toggleTint"
        )
        val pop by animateFloatAsState(
            targetValue = if (checked) 1.1f else 1f,
            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
            label = "togglePop"
        )
        Icon(
            icon, null,
            Modifier.size(22.dp).graphicsLayer { scaleX = pop; scaleY = pop },
            tint = tint
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(end = 12.dp)) {   // UIPOLISH
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

internal fun Trigger.icon(): ImageVector = when (this) {
    Trigger.Notifications -> Icons.Rounded.Notifications
    Trigger.Media -> Icons.Rounded.MusicNote
    Trigger.Calls -> Icons.Rounded.Call
    Trigger.Charging -> Icons.Rounded.Bolt
    Trigger.Timer -> Icons.Rounded.Timer
    Trigger.Devices -> Icons.Rounded.Headphones
    Trigger.Battery -> Icons.Rounded.BatteryAlert
    Trigger.Toggles -> Icons.AutoMirrored.Rounded.VolumeOff
    Trigger.Navigation -> Icons.Rounded.Navigation
    Trigger.Tracking -> Icons.Rounded.DeliveryDining
    Trigger.Downloads -> Icons.Rounded.Download
    Trigger.Quiet -> Icons.Rounded.NotificationsNone
    Trigger.Network -> Icons.Rounded.Wifi
}

@StringRes
internal fun Trigger.hint(): Int = when (this) {
    Trigger.Notifications -> R.string.hint_notifications
    Trigger.Media -> R.string.hint_media
    Trigger.Calls -> R.string.hint_calls
    Trigger.Charging -> R.string.hint_charging
    Trigger.Timer -> R.string.hint_timer
    Trigger.Devices -> R.string.hint_devices
    Trigger.Battery -> R.string.hint_battery
    Trigger.Toggles -> R.string.hint_toggles
    Trigger.Navigation -> R.string.hint_navigation
    Trigger.Tracking -> R.string.hint_tracking
    Trigger.Downloads -> R.string.hint_downloads
    Trigger.Quiet -> R.string.hint_quiet
    Trigger.Network -> R.string.hint_network
}

