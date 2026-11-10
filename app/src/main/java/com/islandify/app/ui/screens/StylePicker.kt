package com.islandify.app.ui.screens

// STYLEKIT: Customize me "Design" chunne wali row (chhote preview tiles, tap = set).

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.islandify.app.R
import com.islandify.app.core.IslandSettings
import com.islandify.app.island.bubblePaint
import com.islandify.app.island.bubbleShape
import com.islandify.app.island.cardStyleBg
import com.islandify.app.island.rememberShellLook
import com.islandify.app.island.shellStyle
import kotlinx.coroutines.flow.MutableStateFlow

internal val SHELL_STYLE_LABELS = listOf(
    R.string.style_classic, R.string.style_glass, R.string.style_gradient,
    R.string.style_neon, R.string.style_tinted, R.string.style_graphite,
    R.string.style_tonal, R.string.style_surface, R.string.style_aurora,
    R.string.style_duo, R.string.style_outline, R.string.style_spotlight,
)
internal val BUBBLE_STYLE_LABELS = listOf(
    R.string.style_circle, R.string.style_squircle, R.string.style_ring,
    R.string.style_glass, R.string.style_tinted, R.string.style_gradient,
    R.string.style_tonal, R.string.style_surface, R.string.style_aurora,
    R.string.style_duo, R.string.style_outline, R.string.style_spotlight,
)
internal val CARD_STYLE_LABELS = listOf(
    R.string.style_classic, R.string.style_glass, R.string.style_material,
    R.string.style_minimal, R.string.style_neon, R.string.style_bold,
    R.string.style_tonal, R.string.style_surface, R.string.style_aurora,
    R.string.style_duo, R.string.style_outline, R.string.style_spotlight,
)

/** kind: 0 = pill / banner, 1 = bubble, 2 = message card. */
@Composable
internal fun StylePickerRow(@StringRes title: Int, flow: MutableStateFlow<Int>, labels: List<Int>, kind: Int) {
    val v by flow.collectAsState()
    val accent = MaterialTheme.colorScheme.primary
    Box(Modifier.searchAnchor(title)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                labels.forEachIndexed { i, label ->
                    val sel = v == i
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { flow.value = i; IslandSettings.save() }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        StyleSwatch(kind, i, accent, sel)
                        Text(
                            stringResource(label),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (sel) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StyleSwatch(kind: Int, i: Int, accent: Color, sel: Boolean) {
    val frame = RoundedCornerShape(14.dp)
    val look = rememberShellLook(i)   // M3DESIGN: 6..11 me wallpaper ke rang, 0..5 me purana
    val acc = if (look.dynamic) look.accent else accent
    Box(
        Modifier
            .size(width = 92.dp, height = 58.dp)
            .clip(frame)
            .background(Color(0xFF2B2740))
            .border(if (sel) 2.dp else 1.dp, if (sel) accent else Color.White.copy(alpha = 0.12f), frame),
        contentAlignment = Alignment.Center,
    ) {
        when (kind) {
            1 -> {
                // bubble: app icon + count badge
                val shape = bubbleShape(i)
                Box(Modifier.size(34.dp)) {
                    Box(
                        Modifier.fillMaxSize().clip(shape).shellStyle(bubblePaint(i), accent, 1f, shape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(15.dp).clip(CircleShape).background(Color(0xFF25D366)))
                    }
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 3.dp, y = (-2).dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(look.badge)
                    )
                }
            }
            2 -> {
                // message card: icon tile + title + text + button
                val shape = RoundedCornerShape(12.dp)
                Box(Modifier.width(76.dp).height(46.dp).clip(shape).cardStyleBg(i, accent, 1f, 0.45f, shape)) {
                    Column(
                        Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            if (i != 3) Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(acc))
                            Box(Modifier.width(30.dp).height(5.dp).clip(CircleShape).background(look.on.copy(alpha = 0.9f)))
                        }
                        Box(Modifier.width(52.dp).height(4.dp).clip(CircleShape).background(look.on.copy(alpha = 0.5f)))
                        Box(
                            Modifier
                                .width(30.dp)
                                .height(7.dp)
                                .clip(CircleShape)
                                .background(if (look.dynamic) look.btn else Color.White.copy(alpha = 0.22f))
                        )
                    }
                }
            }
            3 -> {
                // MUSICKIT: music card: album art + title / artist + progress + 3 buttons
                val shape = RoundedCornerShape(12.dp)
                Box(Modifier.width(76.dp).height(46.dp).clip(shape).cardStyleBg(i, accent, 1f, 0.45f, shape)) {
                    Column(
                        Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(acc))
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Box(Modifier.width(28.dp).height(4.dp).clip(CircleShape).background(look.on.copy(alpha = 0.9f)))
                                Box(Modifier.width(18.dp).height(3.dp).clip(CircleShape).background(look.on.copy(alpha = 0.5f)))
                            }
                        }
                        Box(Modifier.width(58.dp).height(3.dp).clip(CircleShape).background(look.on.copy(alpha = 0.35f)))
                        Row(
                            Modifier.width(58.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(look.on.copy(alpha = 0.7f)))
                            Box(Modifier.size(9.dp).clip(CircleShape).background(look.on))
                            Box(Modifier.size(6.dp).clip(CircleShape).background(look.on.copy(alpha = 0.7f)))
                        }
                    }
                }
            }
            else -> {
                // pill / banner: icon dot + text line
                val shape = if (i == 5) RoundedCornerShape(9.dp) else RoundedCornerShape(13.dp)
                Box(Modifier.width(66.dp).height(26.dp).clip(shape).shellStyle(i, accent, 1f, shape)) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(acc))
                        Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(look.on.copy(alpha = 0.7f)))
                    }
                }
            }
        }
    }
}
