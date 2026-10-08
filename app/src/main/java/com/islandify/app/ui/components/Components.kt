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
import androidx.compose.ui.input.nestedscroll.nestedScroll
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

/* ============================== PIECES ============================== */

/** Hero: the real DynamicIslandUi, updates live with the sliders. Tap to expand / collapse. */
@Composable
internal fun PreviewCard(scale: Float, w: Float, h: Float) {
    var demo by remember { mutableStateOf(Demo.Notification) }
    var level by remember { mutableStateOf(IslandLevel.Expanded) }
    val corner by IslandSettings.corner.collectAsState()
    val speed by IslandSettings.animSpeed.collectAsState()
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
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Demo.entries.forEach { d ->
                    FilterChip(
                        selected = demo == d,
                        onClick = { demo = d; level = IslandLevel.Expanded },
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
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (done) {
            Icon(Icons.Rounded.CheckCircle, stringResource(R.string.common_done), tint = MaterialTheme.colorScheme.primary)
        } else {
            FilledTonalButton(onClick = onGrant) { Text(stringResource(R.string.perm_allow)) }
        }
    }
}

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

@Composable
internal fun ToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Row(
        Modifier
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
        Column(Modifier.weight(1f)) {
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
}

/** Compact screen title bar. [shrink] returns 0..1 (how far the content has scrolled). */
@Composable
internal fun ScreenHeader(
    title: String,
    scrolled: Boolean,
    shrink: () -> Float,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val barColor by animateColorAsState(
        targetValue = if (scrolled) MaterialTheme.colorScheme.surfaceContainer
        else MaterialTheme.colorScheme.surface,
        animationSpec = tween(250),
        label = "headerColor"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(barColor)
            .statusBarsPadding()
            .heightIn(min = 64.dp)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
                .graphicsLayer {
                    val sc = lerp(1f, 0.76f, shrink())
                    scaleX = sc
                    scaleY = sc
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
        )
        trailing?.invoke(this)
    }
}

/**
 * Common frame for every tab screen: a compact header (no big empty area on top) whose title
 * shrinks smoothly while scrolling, then the scrolling content. Cards enter one after another.
 */
@Composable
internal fun ScreenScaffold(
    title: String,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    val counter = remember { RevealCounter() }
    LaunchedEffect(Unit) {
        delay(800)
        counter.settled = true
    }
    val scrolled by remember { derivedStateOf { scroll.value > 6 } }

    CompositionLocalProvider(LocalReveal provides counter) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            ScreenHeader(title, scrolled, { (scroll.value / 240f).coerceIn(0f, 1f) }, trailing)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                content()
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
