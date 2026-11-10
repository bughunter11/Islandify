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

// SPLITBIG  ScreenHeader, ScreenScaffold

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
