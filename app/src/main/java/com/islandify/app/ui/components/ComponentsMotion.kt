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

// SPLITBIG  reveal, BouncyChip, LiveBadge, AppearIn

/* ============================== MOTION ============================== */

internal class RevealCounter {
    var next = 0
    var settled = false
}

// Outside ScreenScaffold the default is "settled": no stagger delay, just a quick fade-in
internal val LocalReveal = compositionLocalOf { RevealCounter().apply { settled = true } }

/** Card enters with a soft fade + rise + scale. Cards on one screen enter one after another. */
@Composable
internal fun Modifier.reveal(): Modifier {
    val rc = LocalReveal.current
    val order = remember { if (rc.settled) 0 else rc.next++ }
    // Tabs are disposed when you leave them, so remember (saveable) that this card already entered:
    // coming back to a tab shows it instantly instead of replaying the animation.
    var entered by rememberSaveable { mutableStateOf(false) }
    val p = remember { Animatable(if (entered) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!entered) {
            delay(order.coerceAtMost(5) * 30L)
            p.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 700f))
            entered = true
        }
    }
    return this.graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * 18.dp.toPx()
        val sc = 0.94f + 0.06f * v
        scaleX = sc
        scaleY = sc
    }
}

/** Assist chip that squishes a little while pressed. */
@Composable
internal fun BouncyChip(label: String, icon: ImageVector? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "chipPress"
    )
    val leading: (@Composable () -> Unit)? =
        if (icon == null) null else ({ Icon(icon, null, Modifier.size(18.dp)) })
    ElevatedAssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = leading,
        interactionSource = src,
        modifier = modifier.graphicsLayer { scaleX = sc; scaleY = sc }
    )
}

/** Small "Live / Off" badge with a breathing dot, shown in the Home header. */
@Composable
internal fun LiveBadge(on: Boolean) {
    val pulse by rememberInfiniteTransition(label = "live").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "livePulse"
    )
    val dot by animateColorAsState(
        targetValue = if (on) Color(0xFF30D158) else MaterialTheme.colorScheme.outline,
        animationSpec = tween(300),
        label = "liveDot"
    )
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .graphicsLayer { alpha = if (on) pulse else 1f }
                    .background(dot, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (on) R.string.live_on else R.string.common_off), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Content that grows + fades in the moment it shows up (for options that appear after a choice). */
@Composable
internal fun ColumnScope.AppearIn(content: @Composable ColumnScope.() -> Unit) {
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(tween(250)) + expandVertically(tween(300, easing = FastOutSlowInEasing))
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
    }
}

