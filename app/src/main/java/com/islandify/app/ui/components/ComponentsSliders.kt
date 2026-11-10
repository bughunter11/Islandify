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

// SPLITBIG  SettingSlider, CenteredSettingSlider

@Composable
internal fun SettingSlider(
    title: String,
    icon: ImageVector,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onFinish: () -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            modifier = Modifier.semantics { stateDescription = valueText },
            valueRange = range,
            onValueChangeFinished = onFinish
        )
    }
}

/**
 * Slider whose middle is the "normal" value ([center]).
 * The thumb starts in the middle, the coloured track grows from the middle towards
 * the thumb, and it snaps to [center] when you get close. Left half = [range].start..center,
 * right half = center..[range].endInclusive, so the original ranges are unchanged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CenteredSettingSlider(
    title: String,
    icon: ImageVector,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    center: Float,
    onChange: (Float) -> Unit,
    onFinish: () -> Unit,
) {
    val lo = range.start
    val hi = range.endInclusive

    // value -> slider position (-1 .. 1, 0 = middle)
    fun toPos(v: Float): Float = when {
        v >= center -> if (hi > center) (v - center) / (hi - center) else 0f
        else -> if (center > lo) -(center - v) / (center - lo) else 0f
    }
    // slider position -> value (snaps to the middle)
    fun fromPos(p: Float): Float {
        val q = if (abs(p) < 0.04f) 0f else p
        return if (q >= 0f) center + q * (hi - center) else center + q * (center - lo)
    }

    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.surfaceVariant
    val tick = MaterialTheme.colorScheme.onSurfaceVariant

    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = toPos(value.coerceIn(lo, hi)),
            onValueChange = { onChange(fromPos(it)) },
            modifier = Modifier.semantics { stateDescription = valueText },
            valueRange = -1f..1f,
            onValueChangeFinished = onFinish,
            track = { state ->
                Canvas(Modifier.fillMaxWidth().height(20.dp)) {
                    val w = size.width
                    val h = 4.dp.toPx()
                    val y = (size.height - h) / 2f
                    val r = 10.dp.toPx()                       // half the thumb
                    val frac = ((state.value + 1f) / 2f).coerceIn(0f, 1f)
                    val cx = w / 2f
                    val tx = r + frac * (w - 2 * r)
                    // base track
                    drawRoundRect(inactive, Offset(0f, y), Size(w, h), CornerRadius(h / 2f))
                    // coloured part: from the middle to the thumb
                    drawRect(active, Offset(min(cx, tx), y), Size(abs(tx - cx), h))
                    // little mark in the middle
                    drawRoundRect(
                        tick, Offset(cx - 1.dp.toPx(), y - 4.dp.toPx()),
                        Size(2.dp.toPx(), h + 8.dp.toPx()), CornerRadius(1.dp.toPx())
                    )
                }
            }
        )
    }
}

