package com.islandify.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb   // SEEKDESIGN
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.islandify.app.R
import com.islandify.app.core.IslandKind
import com.islandify.app.core.IslandSettings
import com.islandify.app.core.KindStyle
import com.islandify.app.ui.components.SectionCard
import kotlin.math.roundToInt

// MUSICKIT  Music card page (Customize > Music card). Gaana chalte waqt pill pe click karne par jo card khulta hai uski settings.
// MUSICLAYOUT: Classic / Expressive / Glow / Spectrum selector

private data class MusicLayoutOption(
    val id: Int,
    val name: String,
    val subtitle: String,
)

private val MUSIC_LAYOUTS = listOf(
    MusicLayoutOption(0, "Classic",    "Slim bar, plain buttons"),
    MusicLayoutOption(1, "Expressive", "Wavy bar, big buttons"),
    MusicLayoutOption(2, "Glow",       "Album colors glow up"),
    MusicLayoutOption(3, "Spectrum",   "Bars move to the song"),
)

@Composable
private fun MusicLayoutCard(opt: MusicLayoutOption, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val bg = MaterialTheme.colorScheme.surfaceVariant
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .clip(shape)
            .background(bg)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) accent else Color.Transparent,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            // Mini preview illustration
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(70.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                when (opt.id) {
                    0 -> ClassicPreview(accent)
                    1 -> ExpressivePreview(accent)
                    2 -> GlowPreview(accent)
                    3 -> SpectrumPreview(accent)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(opt.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface)
            Text(opt.subtitle, fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun ClassicPreview(accent: Color) {
    // Thin seekbar + 3 small round buttons
    Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(accent.copy(alpha = 0.3f))) {
            Box(Modifier.fillMaxWidth(0.4f).fillMaxHeight().background(accent))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            repeat(3) { Box(Modifier.size(18.dp).clip(RoundedCornerShape(50)).background(accent.copy(alpha = 0.5f))) }
        }
    }
}

@Composable private fun ExpressivePreview(accent: Color) {
    // Wavy line + 3 large buttons
    Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            val w = size.width; val h = size.height / 2f
            val path = androidx.compose.ui.graphics.Path()
            path.moveTo(0f, h)
            for (i in 0..10) {
                val x = w * i / 10f
                val y = h + h * 0.7f * kotlin.math.sin(i * kotlin.math.PI.toFloat() / 1.5f)
                path.lineTo(x, y)
            }
            drawPath(path, accent.copy(alpha = 0.7f), style = androidx.compose.ui.graphics.drawscope.Stroke(3f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            repeat(3) { Box(Modifier.size(22.dp).clip(RoundedCornerShape(50)).background(accent.copy(alpha = 0.6f))) }
        }
    }
}

@Composable private fun GlowPreview(accent: Color) {
    // Bottom glow gradient + thin bar
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxWidth().height(30.dp).align(Alignment.BottomCenter)
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color.Transparent, accent.copy(alpha = 0.45f))
                ))
        )
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.7f))) }
        }
    }
}

@Composable private fun SpectrumPreview(accent: Color) {
    // Animated-style equalizer bars
    Row(
        Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) {
        val heights = listOf(0.4f, 0.7f, 0.55f, 0.9f, 0.65f, 0.5f, 0.8f, 0.45f, 0.75f, 0.6f)
        heights.forEach { h ->
            Box(
                Modifier.width(4.dp).fillMaxHeight(h)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent.copy(alpha = 0.75f))
            )
        }
    }
}

@Composable
internal fun MusicPage() {
    val kStyles by IslandSettings.kindStyles.collectAsState()
    val mk = kStyles[IslandKind.Media.key] ?: KindStyle()
    val auto = stringResource(R.string.cust_auto)
    // MUSICLAYOUT: Classic / Expressive / Glow / Spectrum selector
    val musicLayout by IslandSettings.musicPlayerLayout.collectAsState()
    SectionCard(title = "Music Player", icon = Icons.Rounded.MusicNote) {
        Text(
            "How the open island shows what's playing.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MUSIC_LAYOUTS.take(2).forEach { opt ->
                    Box(Modifier.weight(1f)) {
                        MusicLayoutCard(opt, musicLayout == opt.id) {
                            IslandSettings.musicPlayerLayout.value = opt.id
                            IslandSettings.save()
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MUSIC_LAYOUTS.drop(2).forEach { opt ->
                    Box(Modifier.weight(1f)) {
                        MusicLayoutCard(opt, musicLayout == opt.id) {
                            IslandSettings.musicPlayerLayout.value = opt.id
                            IslandSettings.save()
                        }
                    }
                }
            }
        }
    }
    SectionCard(title = stringResource(R.string.cust_music_look), icon = Icons.Rounded.MusicNote) {
        FlagRow(Icons.Rounded.ColorLens, R.string.cust_music_auto, IslandSettings.musicAuto, R.string.cust_music_auto_sub)   // MUSICAUTO
        StylePickerRow(R.string.cust_style_music, IslandSettings.musicStyle, CARD_STYLE_LABELS, 3)
        KindSlider(R.string.cust_mc_fine_w, Icons.Rounded.SwapHoriz, mk.cardW, 0.7f..1.4f, 1f, ::pct) {
            IslandSettings.setKindStyle(IslandKind.Media, mk.copy(cardW = it))
        }
        KindSlider(R.string.cust_card_height, Icons.Rounded.Height, mk.cardH, 0.7f..1.4f, 1f, ::pct) {
            IslandSettings.setKindStyle(IslandKind.Media, mk.copy(cardH = it))
        }
        KindSlider(R.string.cust_mc_text, Icons.Rounded.TextFields, mk.text, 0.7f..1.5f, 1f, ::pct) {
            IslandSettings.setKindStyle(IslandKind.Media, mk.copy(text = it))
        }
        FloatSetting(R.string.cust_music_corner, Icons.Rounded.RoundedCorner, IslandSettings.musicCorner, 0f..48f, null) {
            if (it < 8f) auto else "${it.roundToInt()} dp"
        }
        FloatSetting(R.string.cust_mc_alpha, Icons.Rounded.Visibility, IslandSettings.musicAlpha, 0.4f..1f, null, ::pct)
        FloatSetting(R.string.cust_mc_accent, Icons.Rounded.ColorLens, IslandSettings.musicAccent, 0f..1.5f, null, ::pct)
    }
    // MUSICPOS: music card ki jagah (Placement jaisa): left/right + up/down slider aur 1 dp nudge pad
    val mx by IslandSettings.musicX.collectAsState()
    val my by IslandSettings.musicY.collectAsState()
    SectionCard(title = stringResource(R.string.cust_music_pos_title), icon = Icons.Rounded.OpenWith) {
        FloatSetting(R.string.cust_music_x, Icons.Rounded.SwapHoriz, IslandSettings.musicX, -180f..180f, 0f) {
            "${it.roundToInt()} dp"
        }
        FloatSetting(R.string.cust_music_y, Icons.Rounded.SwapVert, IslandSettings.musicY, -40f..100f, 0f) {
            "${it.roundToInt()} dp"
        }
        Text(
            stringResource(R.string.cust_music_nudge_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        NudgePad(
            onLeft = { IslandSettings.musicX.value = (mx - 1f).coerceIn(-180f, 180f); IslandSettings.save() },
            onRight = { IslandSettings.musicX.value = (mx + 1f).coerceIn(-180f, 180f); IslandSettings.save() },
            onUp = { IslandSettings.musicY.value = (my - 1f).coerceIn(-40f, 100f); IslandSettings.save() },
            onDown = { IslandSettings.musicY.value = (my + 1f).coerceIn(-40f, 100f); IslandSettings.save() },
            onCenter = { IslandSettings.musicX.value = 0f; IslandSettings.save() },
        )
    }
    SectionCard(title = stringResource(R.string.cust_music_pill_title), icon = Icons.Rounded.MusicNote) {   // MUSICAUTO
        FlagRow(Icons.Rounded.ColorLens, R.string.cust_music_pill_auto, IslandSettings.musicPillAuto, R.string.cust_music_pill_auto_sub)
    }
    SectionCard(title = stringResource(R.string.cust_music_art_title), icon = Icons.Rounded.AspectRatio) {
        FloatSetting(R.string.cust_music_art, Icons.Rounded.AspectRatio, IslandSettings.musicArtSize, 0.7f..1.2f, 1f, ::pct)
        FloatSetting(R.string.cust_music_art_corner, Icons.Rounded.RoundedCorner, IslandSettings.musicArtCorner, 0f..30f, 14f) {
            "${it.roundToInt()} dp"
        }
        FlagRow(Icons.Rounded.GraphicEq, R.string.cust_music_wave, IslandSettings.musicWave, R.string.cust_music_wave_sub)
        FloatSetting(R.string.cust_wave_speed, Icons.Rounded.Speed, IslandSettings.waveSpeed, 0.25f..3f, 1f) {   // WAVESYNC
            String.format("%.2fx", it)
        }
        FlagRow(Icons.Rounded.Sync, R.string.cust_wave_follow, IslandSettings.waveFollow, R.string.cust_wave_follow_sub)   // WAVESYNC
    }
    // SEEKDESIGN: seekbar design (Neon Pulse / Aurora) + apna rang + gaane se alag rang
    val seekDesign by IslandSettings.seekDesign.collectAsState()
    val seekCustom by IslandSettings.seekCustomColor.collectAsState()
    val seekTimeTint by IslandSettings.seekTimeTint.collectAsState()
    val seekModeNow by IslandSettings.seekBarColorMode.collectAsState()
    val hueState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(190f) }
    SectionCard(title = "Seekbar Design", icon = Icons.Rounded.GraphicEq) {
        val accent = MaterialTheme.colorScheme.primary
        Text(
            "Style of the progress bar",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Waves", "Neon Pulse", "Aurora").forEachIndexed { i, label ->
                val selected = seekDesign == i
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
                        .border(if (selected) 1.5.dp else 0.dp, if (selected) accent else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickable { IslandSettings.seekDesign.value = i; IslandSettings.save() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) accent else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Colour",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("My colour" to 3, "Song contrast" to 4).forEach { (label, mode) ->
                val selected = seekModeNow == mode
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
                        .border(if (selected) 1.5.dp else 0.dp, if (selected) accent else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickable { IslandSettings.seekBarColorMode.value = mode; IslandSettings.save() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) accent else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Text(
            "Song contrast: a slightly shifted shade of the song's colour, so the bar stays visible on the card.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (seekModeNow == 3) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0xFFFF4FD8L, 0xFF8B5CF6L, 0xFF22D3EEL, 0xFF34D399L, 0xFFFBBF24L, 0xFFFB923CL, 0xFFFB7185L, 0xFFFFFFFFL).forEach { cl ->
                    val sel = seekCustom == cl.toInt()
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(cl))
                            .border(if (sel) 2.dp else 0.dp, if (sel) accent else Color.Transparent, RoundedCornerShape(50))
                            .clickable { IslandSettings.seekCustomColor.value = cl.toInt(); IslandSettings.seekBarColorMode.value = 3; IslandSettings.save() }
                    )
                }
            }
            androidx.compose.material3.Slider(
                value = hueState.floatValue,
                onValueChange = {
                    hueState.floatValue = it
                    IslandSettings.seekCustomColor.value = Color.hsv(it, 0.75f, 1f).toArgb()
                    IslandSettings.seekBarColorMode.value = 3
                },
                onValueChangeFinished = { IslandSettings.save() },
                valueRange = 0f..360f,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Time follows bar colour",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            androidx.compose.material3.Switch(
                checked = seekTimeTint,
                onCheckedChange = { IslandSettings.seekTimeTint.value = it; IslandSettings.save() },
            )
        }
    }
    // WAVYCOLOR: seekbar color mode selector
    val seekColorMode by IslandSettings.seekBarColorMode.collectAsState()
    SectionCard(title = "Seekbar Color", icon = Icons.Rounded.ColorLens) {
        Text(
            "Color of the wavy progress bar",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Solid", "Gradient", "Album").forEachIndexed { i, label ->
                val selected = seekColorMode == i
                val accent = MaterialTheme.colorScheme.primary
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
                        .border(if (selected) 1.5.dp else 0.dp, if (selected) accent else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickable { IslandSettings.seekBarColorMode.value = i; IslandSettings.save() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) accent else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // Preview strip
        val previewColors: List<Color> = when (seekColorMode) {
            1 -> listOf(Color(0xFF00C8FF), Color(0xFF9B6DFF))
            2 -> listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            else -> listOf(Color.White, Color.White)
        }
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                .background(androidx.compose.ui.graphics.Brush.horizontalGradient(previewColors))
        )
    }
}
