package com.islandify.app.update

import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.islandify.app.R
import com.islandify.app.core.IslandSettings
import com.islandify.app.ui.components.SectionCard

// UPDATEUI3 (UPDATEUI2 + skip / background / resume hint / welcome): premium update flow (dialog -> download -> install) + About card.
// Look: the app's own "island" pill is the hero. It morphs: version -> live % -> ready.
// All colors come from MaterialTheme, so it follows Material You / custom accent / AMOLED.
// UpdateManager (logic) is untouched; this file only draws the states.

private const val PH_AVAILABLE = 0
private const val PH_DOWNLOADING = 1
private const val PH_READY = 2
private const val PH_FAILED = 3
private const val PH_WELCOME = 4   // after an update: "you are on the new version"

private val IslandBlack = Color(0xFF0B0B0C)
private val IslandGreen = Color(0xFF30D158)
private val IslandRed = Color(0xFFFF453A)

/** Put this once next to the app root. [tick] is the same counter MainActivity already passes around. */
@Composable
fun UpdateHost(tick: Int) {
    val ctx = LocalContext.current
    val state by UpdateManager.state.collectAsState()
    val hidden by UpdateManager.hidden.collectAsState()
    val welcome by UpdateManager.welcome.collectAsState()

    LaunchedEffect(Unit) { UpdateManager.onAppOpened(ctx) }
    LaunchedEffect(tick) { if (tick > 0) UpdateManager.onResume(ctx) }

    // Android only lets an app open the installer while it is in front: tell the manager
    DisposableEffect(ctx) {
        val lifecycle = ctx.findLifecycleOwner()?.lifecycle
        UpdateManager.setForeground(lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) UpdateManager.setForeground(true)
            if (event == Lifecycle.Event.ON_STOP) UpdateManager.setForeground(false)
        }
        lifecycle?.addObserver(observer)
        onDispose {
            lifecycle?.removeObserver(observer)
            UpdateManager.setForeground(false)
        }
    }

    val s = state
    val info = when (s) {
        is UpdateState.Available -> s.info
        is UpdateState.Downloading -> s.info
        is UpdateState.Ready -> s.info
        is UpdateState.Failed -> s.info
        else -> null
    }
    val w = welcome
    if (info != null && !hidden) {
        UpdateDialog(info, s, tick)
    } else if (w != null) {
        WelcomeDialog(w)   // only when no update dialog is on screen
    }
}

private tailrec fun Context.findLifecycleOwner(): LifecycleOwner? = when (this) {
    is LifecycleOwner -> this
    is ContextWrapper -> baseContext.findLifecycleOwner()
    else -> null
}

// ====================================================================== dialog

@Composable
private fun UpdateDialog(info: UpdateInfo, s: UpdateState, tick: Int) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val cs = MaterialTheme.colorScheme
    val current = remember { UpdateManager.installedVersion(ctx) }
    val force = UpdateConfig.FORCE_UPDATE
    val downloading = s as? UpdateState.Downloading
    val failed = s as? UpdateState.Failed
    val phase = when (s) {
        is UpdateState.Downloading -> PH_DOWNLOADING
        is UpdateState.Ready -> PH_READY
        is UpdateState.Failed -> PH_FAILED
        else -> PH_AVAILABLE
    }
    // tick changes when the app comes back from the "install unknown apps" screen
    val needsPermission = remember(tick, phase) {
        phase == PH_READY && !ctx.packageManager.canRequestPackageInstalls()
    }

    // The old content keeps its last values while it fades out
    val lastDownload = remember { arrayOfNulls<UpdateState.Downloading>(1) }
    if (downloading != null) lastDownload[0] = downloading
    val lastReason = remember { intArrayOf(0) }
    if (failed != null) lastReason[0] = failed.reason
    val fraction = (downloading ?: lastDownload[0])?.let { fractionOf(it) } ?: 0f

    LaunchedEffect(phase) {
        if (IslandSettings.haptics.value) {
            if (phase == PH_READY) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (phase == PH_FAILED) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val sections = remember(info.notes) { parseNotes(info.notes) }

    // Back / outside: only "hides" the dialog. A running download keeps going in the background.
    DialogShell(canDismiss = !force, onDismiss = { UpdateManager.later() }) {
        UpdateStage(phase, info.version, fraction)
        Column(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title + version change + size
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Crossfade(targetState = phase, label = "updTitle") { p ->
                    Text(
                        stringResource(
                            when (p) {
                                PH_DOWNLOADING -> R.string.update_downloading_label
                                PH_READY -> R.string.update_ready_title
                                PH_FAILED -> R.string.update_failed_title
                                else -> R.string.update_title
                            }
                        ),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InfoChip("v$current", cs.surfaceContainerHighest, cs.onSurfaceVariant)
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward, null,
                        Modifier.size(16.dp), tint = cs.onSurfaceVariant
                    )
                    InfoChip("v${info.version}", cs.primaryContainer, cs.onPrimaryContainer)
                    if (info.sizeBytes > 0) {
                        InfoChip(formatSize(info.sizeBytes), Color.Transparent, cs.onSurfaceVariant, Icons.Rounded.Download)
                    }
                }
            }

            // Body changes with the phase
            AnimatedContent(
                targetState = phase,
                transitionSpec = { fadeIn(tween(220, delayMillis = 90)) togetherWith fadeOut(tween(90)) },
                modifier = Modifier.fillMaxWidth(),
                label = "updBody"
            ) { p ->
                when (p) {
                    PH_DOWNLOADING -> DownloadBlock(lastDownload[0])
                    PH_READY -> ReadyBlock(needsPermission)
                    PH_FAILED -> Callout(
                        icon = Icons.Rounded.ErrorOutline,
                        container = cs.errorContainer,
                        content = cs.onErrorContainer,
                        text = stringResource(lastReason[0].takeIf { it != 0 } ?: R.string.update_err_download)
                    )
                    else -> NotesBlock(sections, stringResource(R.string.update_body, info.version, current))
                }
            }

            // Actions
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PrimaryAction(phase) {
                    when (phase) {
                        PH_READY -> UpdateManager.install(ctx)
                        PH_DOWNLOADING -> {}
                        else -> UpdateManager.download(ctx)
                    }
                }
                if (!force) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        when (phase) {
                            PH_DOWNLOADING -> {
                                SecondaryAction(R.string.update_btn_cancel, Modifier.weight(1f)) { UpdateManager.cancel() }
                                SecondaryAction(R.string.update_btn_background, Modifier.weight(1f)) { UpdateManager.later() }
                            }
                            PH_AVAILABLE -> {
                                SecondaryAction(R.string.update_btn_later, Modifier.weight(1f)) { UpdateManager.later() }
                                SecondaryAction(R.string.update_btn_skip, Modifier.weight(1f), muted = true) {
                                    UpdateManager.skipVersion(ctx)
                                }
                            }
                            else -> SecondaryAction(R.string.update_btn_later, Modifier.weight(1f)) { UpdateManager.later() }
                        }
                    }
                } else if (phase == PH_DOWNLOADING) {
                    SecondaryAction(R.string.update_btn_cancel, Modifier.fillMaxWidth()) { UpdateManager.cancel() }
                }
            }
        }
    }
}

/** After an update: the island says "v1.6", with what changed. Shown once per version. */
@Composable
private fun WelcomeDialog(w: WhatsNew) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val cs = MaterialTheme.colorScheme
    val sections = remember(w.notes) { parseNotes(w.notes) }
    LaunchedEffect(Unit) {
        if (IslandSettings.haptics.value) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    DialogShell(canDismiss = true, onDismiss = { UpdateManager.dismissWelcome(ctx) }) {
        UpdateStage(PH_WELCOME, w.version, 1f)
        Column(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.update_welcome_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.update_welcome_sub, w.version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }
            if (sections.isNotEmpty()) NotesBlock(sections, "")
            Button(
                onClick = { UpdateManager.dismissWelcome(ctx) },
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Icon(Icons.Rounded.Check, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.update_btn_gotit), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Shared dialog window: spring-in card, no outside-tap dismiss, scrolls on small screens. */
@Composable
private fun DialogShell(canDismiss: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }
    Dialog(
        onDismissRequest = { if (canDismiss) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = canDismiss,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        AnimatedVisibility(
            visibleState = appear,
            enter = fadeIn(tween(220)) +
                scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = 0.75f, stiffness = 420f)) +
                slideInVertically(animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)) { it / 14 },
            exit = fadeOut(tween(120))
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 16.dp,
                modifier = Modifier.padding(horizontal = 20.dp).widthIn(max = 400.dp).fillMaxWidth()
            ) {
                Column(Modifier.verticalScroll(rememberScrollState())) { content() }
            }
        }
    }
}

@Composable
private fun SecondaryAction(@androidx.annotation.StringRes label: Int, modifier: Modifier, muted: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    TextButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = if (muted) cs.onSurfaceVariant else cs.primary)
    ) {
        Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ====================================================================== hero: the island pill

/** Dark "phone screen" stage with the island pill in the middle. Same look as the Home preview card. */
@Composable
private fun UpdateStage(phase: Int, version: String, fraction: Float) {
    val primary = MaterialTheme.colorScheme.primary
    val inf = rememberInfiniteTransition(label = "stageGlow")
    val glow = inf.animateFloat(
        0.25f, 0.55f, infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow"
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(128.dp)
            .background(Brush.verticalGradient(listOf(primary.copy(alpha = 0.55f), IslandBlack)))
            .drawBehind {
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = size.width * 0.45f
                drawCircle(
                    brush = Brush.radialGradient(listOf(primary.copy(alpha = glow.value), Color.Transparent), center = c, radius = r),
                    radius = r,
                    center = c
                )
            },
        contentAlignment = Alignment.Center
    ) {
        UpdateIsland(phase, version, fraction)
    }
}

@Composable
private fun UpdateIsland(phase: Int, version: String, fraction: Float) {
    val primary = MaterialTheme.colorScheme.primary
    val width by animateDpAsState(
        if (phase == PH_AVAILABLE) 176.dp else 252.dp,
        spring(dampingRatio = 0.78f, stiffness = 380f),
        label = "islandWidth"
    )
    val fill by animateFloatAsState(
        when (phase) {
            PH_DOWNLOADING -> fraction
            PH_READY, PH_WELCOME -> 1f
            else -> 0f
        },
        tween(280, easing = LinearOutSlowInEasing),
        label = "islandFill"
    )
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .width(width)
            .height(54.dp)
            .clip(shape)
            .background(IslandBlack)
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape),
        contentAlignment = Alignment.Center
    ) {
        // live fill behind the content
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(fill.coerceIn(0f, 1f))
                .background(Brush.horizontalGradient(listOf(primary.copy(alpha = 0.30f), primary.copy(alpha = 0.62f))))
        )
        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                (fadeIn(tween(220, delayMillis = 60)) + scaleIn(initialScale = 0.9f)) togetherWith fadeOut(tween(110))
            },
            label = "islandContent"
        ) { p ->
            when (p) {
                PH_DOWNLOADING -> IslandDownloading(fraction)
                PH_READY -> IslandReady()
                PH_FAILED -> IslandFailed()
                PH_WELCOME -> IslandWelcome(version)
                else -> IslandIdle(version)
            }
        }
    }
}

@Composable
private fun IslandIdle(version: String) {
    val inf = rememberInfiniteTransition(label = "idle")
    val dot = inf.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "dot")
    val b1 = inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "bar1")
    val b2 = inf.animateFloat(0.4f, 1f, infiniteRepeatable(tween(680), RepeatMode.Reverse), label = "bar2")
    val b3 = inf.animateFloat(0.25f, 0.9f, infiniteRepeatable(tween(440), RepeatMode.Reverse), label = "bar3")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .size(10.dp)
                .graphicsLayer { alpha = dot.value }
                .clip(CircleShape)
                .background(IslandGreen)
        )
        Text(
            "v$version",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Row(
            Modifier.height(18.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(b1, b2, b3).forEach { v ->
                Box(
                    Modifier
                        .width(3.dp)
                        .height(18.dp)
                        .graphicsLayer { scaleY = v.value }
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White)
                )
            }
        }
    }
}

@Composable
private fun IslandDownloading(fraction: Float) {
    val inf = rememberInfiniteTransition(label = "dl")
    val bounce = inf.animateFloat(-2.5f, 2.5f, infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bounce")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            Icons.Rounded.Download, null, tint = Color.White,
            modifier = Modifier.size(20.dp).graphicsLayer { translationY = bounce.value.dp.toPx() }
        )
        Text(
            "${(fraction * 100).toInt()}%",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun IslandReady() {
    val pop = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f)) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            Icons.Rounded.CheckCircle, null, tint = IslandGreen,
            modifier = Modifier.size(22.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }
        )
        Text(
            stringResource(R.string.update_ready_title),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun IslandWelcome(version: String) {
    val pop = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f)) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            Icons.Rounded.CheckCircle, null, tint = IslandGreen,
            modifier = Modifier.size(22.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }
        )
        Text(
            "v$version",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun IslandFailed() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Rounded.ErrorOutline, null, tint = IslandRed, modifier = Modifier.size(22.dp))
        Text(
            stringResource(R.string.update_failed_title),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

// ====================================================================== body blocks

@Composable
private fun InfoChip(text: String, container: Color, content: Color, icon: ImageVector? = null) {
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Row(
            Modifier.padding(horizontal = if (container == Color.Transparent) 4.dp else 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Callout(
    icon: ImageVector,
    container: Color,
    content: Color,
    text: String,
    title: String? = null,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = container,
        contentColor = content,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, null, Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (title != null) Text(title, style = MaterialTheme.typography.titleSmall)
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** "What's new": release notes split into sections (New / Improved / Fixed ...) with a soft fade at the bottom. */
@Composable
private fun NotesBlock(sections: List<NoteSection>, fallback: String) {
    val cs = MaterialTheme.colorScheme
    if (sections.isEmpty()) {
        Text(fallback, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        return
    }
    val scroll = rememberScrollState()
    val bg = cs.surfaceContainerLow
    val fade by animateFloatAsState(if (scroll.canScrollForward) 1f else 0f, label = "notesFade")
    Surface(shape = RoundedCornerShape(22.dp), color = bg, modifier = Modifier.fillMaxWidth()) {
        Box {
            Column(
                Modifier.heightIn(max = 230.dp).verticalScroll(scroll).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    stringResource(R.string.update_whats_new),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                sections.forEach { sec -> NoteSectionView(sec) }
                Spacer(Modifier.height(6.dp))
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(36.dp)
                    .graphicsLayer { alpha = fade }
                    .background(Brush.verticalGradient(listOf(Color.Transparent, bg)))
            )
        }
    }
}

@Composable
private fun NoteSectionView(sec: NoteSection) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val title = sec.title
        if (title != null) {
            val (icon, container, content) = when (sec.kind) {
                NoteKind.Improved -> Triple(Icons.Rounded.Bolt, cs.tertiaryContainer, cs.onTertiaryContainer)
                NoteKind.Fixed -> Triple(Icons.Rounded.BugReport, cs.secondaryContainer, cs.onSecondaryContainer)
                NoteKind.New -> Triple(Icons.Rounded.AutoAwesome, cs.primaryContainer, cs.onPrimaryContainer)
                NoteKind.Other -> Triple(Icons.Rounded.NewReleases, cs.primaryContainer, cs.onPrimaryContainer)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(26.dp).clip(RoundedCornerShape(9.dp)).background(container),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, null, Modifier.size(16.dp), tint = content) }
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
        }
        sec.lines.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                if (line.bullet) {
                    Box(
                        Modifier
                            .padding(top = 8.dp, start = if (title != null) 8.dp else 0.dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(cs.primary.copy(alpha = 0.85f))
                    )
                }
                Text(
                    line.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurface.copy(alpha = 0.88f),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DownloadBlock(d: UpdateState.Downloading?) {
    val cs = MaterialTheme.colorScheme
    val done = d?.done ?: 0L
    val total = d?.total ?: 0L
    val frac: Float? = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null
    val speed = rememberSpeed(done)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Stepper(active = 0)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (frac != null) "${(frac * 100).toInt()}" else "\u2013",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                "%",
                style = MaterialTheme.typography.titleMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp, bottom = 5.dp)
            )
            Spacer(Modifier.weight(1f))
            if (total > 0) {
                Text(
                    stringResource(R.string.update_size_of, formatSize(done), formatSize(total)),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
        GradientProgress(frac)
        val resumedPct = if (d != null && d.resumedFrom > 0 && total > 0) ((d.resumedFrom * 100) / total).toInt() else 0
        if (resumedPct > 0) {
            Text(
                stringResource(R.string.update_resumed, resumedPct),
                style = MaterialTheme.typography.labelMedium,
                color = cs.primary
            )
        }
        if (speed > 0f) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.update_speed, formatSpeed(speed)),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                if (total > 0) {
                    val left = (((total - done).coerceAtLeast(0L)) / speed).toInt()
                    Text(
                        if (left >= 90) stringResource(R.string.update_eta_min, (left + 30) / 60)
                        else stringResource(R.string.update_eta_sec, left.coerceAtLeast(1)),
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadyBlock(needsPermission: Boolean) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Stepper(active = 2)
        Callout(
            icon = Icons.Rounded.Verified,
            container = cs.primaryContainer,
            content = cs.onPrimaryContainer,
            text = stringResource(R.string.update_verified)
        )
        if (needsPermission) {
            Callout(
                icon = Icons.Rounded.Shield,
                container = cs.secondaryContainer,
                content = cs.onSecondaryContainer,
                title = stringResource(R.string.update_permission_title),
                text = stringResource(R.string.update_permission_hint)
            )
        }
    }
}

/** Download -> Check -> Install. Steps before [active] are done, [active] pulses. */
@Composable
private fun Stepper(active: Int) {
    val cs = MaterialTheme.colorScheme
    val labels = listOf(R.string.update_step_download, R.string.update_step_verify, R.string.update_step_install)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        labels.forEachIndexed { i, res ->
            if (i > 0) {
                val lineFill by animateFloatAsState(if (i <= active) 1f else 0f, tween(450), label = "stepLine")
                Box(
                    Modifier
                        .weight(1f)
                        .padding(start = 6.dp, end = 6.dp, top = 10.dp)
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(cs.surfaceContainerHighest)
                ) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(lineFill).background(cs.primary))
                }
            }
            val state = when {
                i < active -> 2
                i == active -> 1
                else -> 0
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val dotBg by animateColorAsState(
                    if (state >= 1) cs.primary else cs.surfaceContainerHighest, label = "stepDot"
                )
                Box(Modifier.size(22.dp).clip(CircleShape).background(dotBg), contentAlignment = Alignment.Center) {
                    if (state == 2) {
                        Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = cs.onPrimary)
                    } else if (state == 1) {
                        val inf = rememberInfiniteTransition(label = "stepPulse")
                        val p = inf.animateFloat(0.55f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "p")
                        Box(
                            Modifier
                                .size(8.dp)
                                .graphicsLayer { scaleX = p.value; scaleY = p.value }
                                .clip(CircleShape)
                                .background(cs.onPrimary)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(res),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (state == 1) FontWeight.Bold else FontWeight.Medium,
                    color = if (state >= 1) cs.onSurface else cs.onSurfaceVariant
                )
            }
        }
    }
}

/** Rounded gradient bar with a moving shine. [fraction] null = size unknown (sliding bar). */
@Composable
private fun GradientProgress(fraction: Float?, height: androidx.compose.ui.unit.Dp = 10.dp) {
    val cs = MaterialTheme.colorScheme
    val inf = rememberInfiniteTransition(label = "shine")
    val shift = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "shift")
    val target by animateFloatAsState(fraction ?: 0f, tween(300, easing = LinearOutSlowInEasing), label = "frac")
    val track = cs.surfaceContainerHighest
    val c1 = cs.primary
    val c2 = cs.tertiary
    Canvas(Modifier.fillMaxWidth().height(height)) {
        val w = size.width
        val h = size.height
        val r = CornerRadius(h / 2f, h / 2f)
        drawRoundRect(color = track, cornerRadius = r)
        if (fraction != null) {
            val fw = w * target
            if (fw > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(c1, c2), startX = 0f, endX = w.coerceAtLeast(1f)),
                    size = Size(fw, h),
                    cornerRadius = r
                )
                val sw = w * 0.28f
                val sx = (fw + sw) * shift.value - sw
                clipRect(left = 0f, top = 0f, right = fw, bottom = h) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                            startX = sx, endX = sx + sw
                        ),
                        topLeft = Offset(sx, 0f),
                        size = Size(sw, h)
                    )
                }
            }
        } else {
            val sw = w * 0.35f
            val sx = (w + sw) * shift.value - sw
            clipRect(left = 0f, top = 0f, right = w, bottom = h) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(c1, c2), startX = sx, endX = sx + sw),
                    topLeft = Offset(sx, 0f),
                    size = Size(sw, h),
                    cornerRadius = r
                )
            }
        }
    }
}

@Composable
private fun PrimaryAction(phase: Int, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "press"
    )
    Button(
        onClick = onClick,
        enabled = phase != PH_DOWNLOADING,
        interactionSource = source,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = cs.surfaceContainerHighest,
            disabledContentColor = cs.onSurfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        AnimatedContent(
            targetState = phase,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },
            label = "primaryLabel"
        ) { p ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (p) {
                    PH_DOWNLOADING -> CircularProgressIndicator(
                        Modifier.size(18.dp), color = LocalContentColor.current, strokeWidth = 2.dp
                    )
                    PH_READY -> Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(20.dp))
                    PH_FAILED -> Icon(Icons.Rounded.Refresh, null, Modifier.size(20.dp))
                    else -> Icon(Icons.Rounded.Download, null, Modifier.size(20.dp))
                }
                Text(
                    stringResource(
                        when (p) {
                            PH_DOWNLOADING -> R.string.update_downloading_label
                            PH_READY -> R.string.update_btn_install
                            PH_FAILED -> R.string.update_btn_retry
                            else -> R.string.update_btn_update
                        }
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ====================================================================== About tab card

/** "App update" card for the About tab: manual check + shortcut back to the dialog. */
@Composable
fun UpdateAboutCard() {
    val ctx = LocalContext.current
    val state by UpdateManager.state.collectAsState()
    val current = remember { UpdateManager.installedVersion(ctx) }
    val s = state
    val line = aboutLine(s, current)

    SectionCard(title = stringResource(R.string.about_update_title), icon = Icons.Rounded.SystemUpdate) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatusOrb(s)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.about_update_version, current),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                AnimatedContent(
                    targetState = line,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(100)) },
                    label = "aboutLine"
                ) { text ->
                    Text(
                        text,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (s is UpdateState.Failed) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        val dl = s as? UpdateState.Downloading
        val lastFrac = remember { floatArrayOf(0f) }   // keeps the bar steady while it collapses
        if (dl != null && dl.total > 0) lastFrac[0] = fractionOf(dl)
        AnimatedVisibility(
            visible = dl != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            GradientProgress(if (dl == null || dl.total > 0) lastFrac[0] else null, 8.dp)
        }

        val actionable = s is UpdateState.Available || s is UpdateState.Ready ||
            (s is UpdateState.Failed && s.info != null)
        if (actionable) {
            Button(
                onClick = { UpdateManager.showDialog() },
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.update_btn_update), fontWeight = FontWeight.SemiBold)
            }
        } else {
            FilledTonalButton(
                onClick = { UpdateManager.checkNow(ctx) },
                enabled = s !is UpdateState.Checking && s !is UpdateState.Downloading,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.about_update_check), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun aboutLine(s: UpdateState, current: String): String = when (s) {
    is UpdateState.Idle -> stringResource(R.string.about_update_idle)
    is UpdateState.Checking -> stringResource(R.string.about_update_checking)
    is UpdateState.UpToDate -> stringResource(R.string.about_update_latest, current)
    is UpdateState.Available -> stringResource(R.string.about_update_available, s.info.version)
    is UpdateState.Downloading -> stringResource(R.string.update_downloading_label)
    is UpdateState.Ready -> stringResource(R.string.update_ready_title)
    is UpdateState.Failed ->
        stringResource(if (s.info == null && s.reason != R.string.update_err_rate_limit) R.string.about_update_failed else s.reason)
}

private fun aboutKind(s: UpdateState): Int = when (s) {
    is UpdateState.Idle -> 0
    is UpdateState.Checking -> 1
    is UpdateState.UpToDate -> 2
    is UpdateState.Available -> 3
    is UpdateState.Downloading -> 4
    is UpdateState.Ready -> 5
    is UpdateState.Failed -> 6
}

/** Round status icon: spinner, check, pulsing "new", live percent ring or error. */
@Composable
private fun StatusOrb(s: UpdateState) {
    val cs = MaterialTheme.colorScheme
    val kind = aboutKind(s)
    val ring = rememberInfiniteTransition(label = "orbRing")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearOutSlowInEasing)), label = "orbP")
    Box(
        Modifier.size(56.dp).drawBehind {
            if (kind == 3) {
                val p = ring.value
                drawCircle(
                    color = cs.primary.copy(alpha = (1f - p) * 0.45f),
                    radius = size.minDimension / 2f * (1f + 0.35f * p),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        },
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = kind,
            transitionSpec = { (fadeIn(tween(200)) + scaleIn(initialScale = 0.8f)) togetherWith fadeOut(tween(100)) },
            label = "orb"
        ) { k ->
            val gradient = k == 3 || k == 5
            val solid = if (k == 6) cs.errorContainer else cs.primaryContainer
            val ink = when {
                gradient -> cs.onPrimary
                k == 6 -> cs.onErrorContainer
                else -> cs.onPrimaryContainer
            }
            val base = Modifier.size(48.dp).clip(CircleShape)
            Box(
                if (gradient) base.background(Brush.linearGradient(listOf(cs.primary, cs.tertiary))) else base.background(solid),
                contentAlignment = Alignment.Center
            ) {
                when (k) {
                    1 -> CircularProgressIndicator(Modifier.size(22.dp), color = ink, strokeWidth = 2.5.dp)
                    2 -> Icon(Icons.Rounded.Verified, null, Modifier.size(26.dp), tint = ink)
                    3 -> Icon(Icons.Rounded.NewReleases, null, Modifier.size(26.dp), tint = ink)
                    4 -> {
                        val f = (s as? UpdateState.Downloading)?.let { fractionOf(it) } ?: 1f
                        val shown by animateFloatAsState(f, tween(300), label = "orbFrac")
                        CircularProgressIndicator(
                            progress = { shown },
                            modifier = Modifier.size(38.dp),
                            color = cs.primary,
                            trackColor = cs.primary.copy(alpha = 0.18f),
                            strokeWidth = 3.dp
                        )
                        Text(
                            "${(shown * 100).toInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ink
                        )
                    }
                    5 -> Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(26.dp), tint = ink)
                    6 -> Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(26.dp), tint = ink)
                    else -> Icon(Icons.Rounded.Update, null, Modifier.size(26.dp), tint = ink)
                }
            }
        }
    }
}

// ====================================================================== helpers

private fun fractionOf(d: UpdateState.Downloading): Float =
    if (d.total > 0) (d.done.toFloat() / d.total).coerceIn(0f, 1f) else 0f

private fun formatSize(bytes: Long): String = "%.1f MB".format(bytes / 1048576.0)

private fun formatSpeed(bytesPerSec: Float): String =
    if (bytesPerSec >= 1048576f) "%.1f MB".format(bytesPerSec / 1048576f)
    else "%d KB".format((bytesPerSec / 1024f).toInt().coerceAtLeast(1))

/** Smoothed download speed in bytes / second, measured from the progress the manager emits. */
@Composable
private fun rememberSpeed(done: Long): Float {
    var speed by remember { mutableFloatStateOf(0f) }
    val last = remember { longArrayOf(0L, 0L) }   // [time ms, bytes]
    LaunchedEffect(done) {
        val now = SystemClock.elapsedRealtime()
        if (last[0] != 0L && now > last[0] && done >= last[1]) {
            val inst = (done - last[1]) * 1000f / (now - last[0])
            speed = if (speed == 0f) inst else speed * 0.85f + inst * 0.15f
        }
        last[0] = now
        last[1] = done
    }
    return speed
}

// ---- release notes (GitHub Markdown) -> sections

private enum class NoteKind { New, Improved, Fixed, Other }

private data class NoteLine(val text: String, val bullet: Boolean)

private data class NoteSection(val title: String?, val kind: NoteKind, val lines: List<NoteLine>)

private val MD_HEADING = Regex("^#{1,6}\\s*(.+)\$")
private val MD_NUMBERED = Regex("^\\d+[.)]\\s+(.*)\$")
private val MD_LINK = Regex("\\[([^\\]]+)]\\([^)]*\\)")

private fun cleanMd(s: String): String =
    s.replace(MD_LINK, "\$1").replace("**", "").replace("__", "").replace("`", "").trim()

private fun kindOf(title: String?): NoteKind {
    val t = title?.lowercase().orEmpty()
    return when {
        t.contains("fix") || t.contains("bug") || t.contains("ठीक") || t.contains("corre") -> NoteKind.Fixed
        t.contains("improv") || t.contains("better") || t.contains("सुधार") || t.contains("melhor") -> NoteKind.Improved
        t.contains("new") || t.contains("नया") || t.contains("novo") || t.contains("added") -> NoteKind.New
        else -> NoteKind.Other
    }
}

private fun parseNotes(md: String): List<NoteSection> {
    val out = ArrayList<NoteSection>()
    var title: String? = null
    var lines = ArrayList<NoteLine>()
    fun flush() {
        if (title != null || lines.isNotEmpty()) out.add(NoteSection(title, kindOf(title), lines.toList()))
        lines = ArrayList()
    }
    for (raw in md.replace("\r", "").lines()) {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("<!--") || line.matches(Regex("^[-*_]{3,}\$"))) continue
        val heading = MD_HEADING.find(line)
        val numbered = MD_NUMBERED.find(line)
        when {
            heading != null -> {
                flush()
                title = cleanMd(heading.groupValues[1])
            }
            line.startsWith("- ") || line.startsWith("* ") || line.startsWith("\u2022 ") ->
                lines.add(NoteLine(cleanMd(line.drop(2)), true))
            numbered != null -> lines.add(NoteLine(cleanMd(numbered.groupValues[1]), true))
            else -> lines.add(NoteLine(cleanMd(line), false))
        }
    }
    flush()
    return out.map { it.copy(lines = it.lines.filter { l -> l.text.isNotEmpty() }.take(12)) }
        .filter { it.title != null || it.lines.isNotEmpty() }
        .take(6)
}
