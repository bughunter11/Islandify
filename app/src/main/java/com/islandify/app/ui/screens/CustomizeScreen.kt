package com.islandify.app.ui.screens
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.rounded.Language
import com.islandify.app.core.AppLanguage
import com.islandify.app.R
import androidx.compose.ui.res.stringResource

import com.islandify.app.core.*
import com.islandify.app.island.*
import com.islandify.app.ui.components.*
import com.islandify.app.ui.onboarding.*
import com.islandify.app.ui.theme.*

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeScreen() {
    val scale by IslandSettings.scale.collectAsState()
    val w by IslandSettings.widthScale.collectAsState()
    val h by IslandSettings.heightScale.collectAsState()
    val x by IslandSettings.offsetX.collectAsState()
    val y by IslandSettings.offsetY.collectAsState()
    val corner by IslandSettings.corner.collectAsState()
    val speed by IslandSettings.animSpeed.collectAsState()
    val glow by IslandSettings.glow.collectAsState()
    val haptics by IslandSettings.haptics.collectAsState()
    val theme by IslandSettings.theme.collectAsState()
    val source by IslandSettings.accentSource.collectAsState()
    val seed by IslandSettings.accentSeed.collectAsState()
    val bgStyle by IslandSettings.bgStyle.collectAsState()
    val bgColor by IslandSettings.bgColor.collectAsState()
    val appAccent by IslandSettings.islandAppAccent.collectAsState()
    val collapse by IslandSettings.autoCollapseSec.collectAsState()
    val swipeSkip by IslandSettings.swipeSkip.collectAsState()
    val idlePill by IslandSettings.idlePill.collectAsState()
    val dynamicOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun nudge(dx: Float, dy: Float) {
        IslandSettings.offsetX.value = (IslandSettings.offsetX.value + dx).coerceIn(-180f, 180f)
        IslandSettings.offsetY.value = (IslandSettings.offsetY.value + dy).coerceIn(0f, 300f)
        IslandSettings.save()
    }

    ScreenScaffold(stringResource(R.string.tab_customize)) {
        PreviewCard(scale, w, h)

        /* ---------- Shape ---------- */
        SectionCard(title = stringResource(R.string.cust_shape), icon = Icons.Rounded.AspectRatio) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BouncyChip(stringResource(R.string.preset_small)) { IslandSettings.preset(0.8f, 0.85f, 1f) }
                BouncyChip(stringResource(R.string.preset_normal)) { IslandSettings.resetShape() }
                BouncyChip(stringResource(R.string.preset_large)) { IslandSettings.preset(1.2f, 1.1f, 1.1f) }
                BouncyChip(stringResource(R.string.preset_wide)) { IslandSettings.preset(1f, 1.45f, 1f) }
                BouncyChip(stringResource(R.string.preset_tall)) { IslandSettings.preset(1f, 1f, 1.5f) }
            }
            CenteredSettingSlider(
                stringResource(R.string.cust_size), Icons.Rounded.ZoomOutMap, "${(scale * 100).toInt()}%",
                scale, 0.5f..1.8f, 1f, { IslandSettings.scale.value = it }, { IslandSettings.save() }
            )
            CenteredSettingSlider(
                stringResource(R.string.cust_width), Icons.Rounded.Straighten, "${(w * 100).toInt()}%",
                w, 0.6f..1.6f, 1f, { IslandSettings.widthScale.value = it }, { IslandSettings.save() }
            )
            CenteredSettingSlider(
                stringResource(R.string.cust_height), Icons.Rounded.Height, "${(h * 100).toInt()}%",
                h, 0.7f..1.6f, 1f, { IslandSettings.heightScale.value = it }, { IslandSettings.save() }
            )
            SettingSlider(
                stringResource(R.string.cust_roundness), Icons.Rounded.RoundedCorner, "${(corner * 100).toInt()}%",
                corner, 0.2f..1f, { IslandSettings.corner.value = it }, { IslandSettings.save() }
            )
        }

        /* ---------- Banners: pick one, then customize it ---------- */
        KindCustomizeCard()

        /* ---------- Animation & feel ---------- */
        SectionCard(title = stringResource(R.string.cust_anim_title), icon = Icons.Rounded.Speed) {
            SettingSlider(
                stringResource(R.string.cust_anim_speed), Icons.Rounded.Speed, "${"%.1f".format(speed)}x",
                speed, 0.5f..2f, { IslandSettings.animSpeed.value = it }, { IslandSettings.save() }
            )
            ToggleRow(
                icon = Icons.Rounded.Lightbulb, title = stringResource(R.string.cust_glow),
                subtitle = stringResource(R.string.cust_glow_sub),
                checked = glow, onChange = { IslandSettings.glow.value = it; IslandSettings.save() }
            )
            ToggleRow(
                icon = Icons.Rounded.Vibration, title = stringResource(R.string.cust_haptics),
                subtitle = stringResource(R.string.cust_haptics_sub),
                checked = haptics, onChange = { IslandSettings.haptics.value = it; IslandSettings.save() }
            )
        }

        /* ---------- Position + calibration ---------- */
        SectionCard(title = stringResource(R.string.cust_position), icon = Icons.Rounded.OpenWith) {
            CenteredSettingSlider(
                stringResource(R.string.cust_left_right), Icons.Rounded.SwapHoriz, "${x.toInt()} dp",
                x, -180f..180f, 0f, { IslandSettings.offsetX.value = it }, { IslandSettings.save() }
            )
            CenteredSettingSlider(
                stringResource(R.string.cust_up_down), Icons.Rounded.SwapVert, "${y.toInt()} dp",
                y, 0f..300f, 10f, { IslandSettings.offsetY.value = it }, { IslandSettings.save() }
            )
            Text(
                stringResource(R.string.cust_nudge_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(onClick = { nudge(-1f, 0f) }) { Icon(Icons.Rounded.ChevronLeft, stringResource(R.string.dir_left)) }
                FilledTonalIconButton(onClick = { nudge(0f, -1f) }) { Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.dir_up)) }
                FilledTonalIconButton(onClick = { nudge(0f, 1f) }) { Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.dir_down)) }
                FilledTonalIconButton(onClick = { nudge(1f, 0f) }) { Icon(Icons.Rounded.ChevronRight, stringResource(R.string.dir_right)) }
                FilledTonalButton(onClick = {
                    IslandSettings.offsetX.value = 0f
                    IslandSettings.save()
                }) { Text(stringResource(R.string.cust_center)) }
            }
        }

        /* ---------- Island behavior ---------- */
        SectionCard(title = stringResource(R.string.cust_behavior), icon = Icons.Rounded.TouchApp) {
            SettingSlider(
                stringResource(R.string.cust_auto_collapse), Icons.Rounded.Timer,
                if (collapse == 0) stringResource(R.string.common_off) else stringResource(R.string.cust_seconds, collapse),
                collapse.toFloat(), 0f..15f,
                { IslandSettings.autoCollapseSec.value = it.toInt() }, { IslandSettings.save() }
            )
            ToggleRow(
                icon = Icons.Rounded.AccessTime, title = stringResource(R.string.cust_idle_pill),
                subtitle = stringResource(R.string.cust_idle_pill_sub),
                checked = idlePill, onChange = { IslandSettings.idlePill.value = it; IslandSettings.save() }
            )
            ToggleRow(
                icon = Icons.Rounded.SkipNext, title = stringResource(R.string.cust_swipe_skip),
                subtitle = stringResource(R.string.cust_swipe_skip_sub),
                checked = swipeSkip, onChange = { IslandSettings.swipeSkip.value = it; IslandSettings.save() }
            )
            ToggleRow(
                icon = Icons.Rounded.Palette, title = stringResource(R.string.cust_app_accent),
                subtitle = stringResource(R.string.cust_app_accent_sub),
                checked = appAccent, onChange = { IslandSettings.islandAppAccent.value = it; IslandSettings.save() }
            )
        }

        /* ---------- Accent color ---------- */
        SectionCard(title = stringResource(R.string.cust_accent_color), icon = Icons.Rounded.ColorLens) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (dynamicOk) FilterChip(
                    selected = source == 0,
                    onClick = { IslandSettings.accentSource.value = 0; IslandSettings.save() },
                    label = { Text(stringResource(R.string.cust_wallpaper)) }
                )
                FilterChip(
                    selected = source == 1 || !dynamicOk,
                    onClick = { IslandSettings.accentSource.value = 1; IslandSettings.save() },
                    label = { Text(stringResource(R.string.cust_custom)) }
                )
            }
            if (source == 1 || !dynamicOk) AppearIn {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AccentPresets.forEach { (_, argb) ->
                        ColorDot(argb, seed == argb) {
                            IslandSettings.accentSeed.value = argb; IslandSettings.save()
                        }
                    }
                }
                SettingSlider(
                    stringResource(R.string.cust_custom_hue), Icons.Rounded.ColorLens, "${hueOf(seed).toInt()}°",
                    hueOf(seed), 0f..360f,
                    {
                        IslandSettings.accentSeed.value =
                            android.graphics.Color.HSVToColor(floatArrayOf(it, 0.75f, 0.92f))
                    },
                    { IslandSettings.save() }
                )
            }
        }

        /* ---------- Background ---------- */
        SectionCard(title = stringResource(R.string.cust_app_background), icon = Icons.Rounded.Wallpaper) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(R.string.bg_default, R.string.bg_tinted, R.string.cust_custom).forEachIndexed { i, label ->
                    FilterChip(
                        selected = bgStyle == i,
                        onClick = { IslandSettings.bgStyle.value = i; IslandSettings.save() },
                        label = { Text(stringResource(label)) }
                    )
                }
            }
            if (bgStyle == 2) AppearIn {
                // Keep H, S, V separately: at S=0 or V=0 the color itself forgets the hue
                val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(bgColor, it) } }
                var bgH by remember { mutableFloatStateOf(start[0]) }
                var bgS by remember { mutableFloatStateOf(start[1]) }
                var bgV by remember { mutableFloatStateOf(start[2]) }
                fun push() {
                    IslandSettings.bgColor.value =
                        android.graphics.Color.HSVToColor(floatArrayOf(bgH, bgS, bgV))
                }
                SettingSlider(
                    stringResource(R.string.cust_bg_hue), Icons.Rounded.ColorLens, "${bgH.toInt()}°",
                    bgH, 0f..360f, { bgH = it; push() }, { IslandSettings.save() }
                )
                SettingSlider(
                    stringResource(R.string.cust_bg_brightness), Icons.Rounded.Lightbulb, "${(bgV * 100).toInt()}%",
                    bgV, 0f..1f, { bgV = it; push() }, { IslandSettings.save() }
                )
                SettingSlider(
                    stringResource(R.string.cust_bg_saturation), Icons.Rounded.Palette, "${(bgS * 100).toInt()}%",
                    bgS, 0f..1f, { bgS = it; push() }, { IslandSettings.save() }
                )
            }
        }

        /* ---------- Theme ---------- */
        SectionCard(title = stringResource(R.string.cust_app_theme), icon = Icons.Rounded.Palette) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = theme == i,
                        onClick = { IslandSettings.theme.value = i; IslandSettings.save() },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = ThemeMode.entries.size),
                        icon = {},
                        label = { Text(stringResource(m.label), maxLines = 1) }
                    )
                }
            }
        }

        /* ---------- Language ---------- */
        SectionCard(title = stringResource(R.string.lang_title), icon = Icons.Rounded.Language) {
            val ctx = LocalContext.current
            var lang by remember { mutableStateOf(AppLanguage.saved(ctx)) }
            val options = listOf(
                AppLanguage.SYSTEM to stringResource(R.string.theme_system),
                AppLanguage.ENGLISH to "English",
                AppLanguage.HINDI to "हिन्दी",
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { i, (code, name) ->
                    SegmentedButton(
                        selected = lang == code,
                        onClick = {
                            if (lang != code) {
                                lang = code
                                AppLanguage.set(ctx, code)
                                (ctx as? Activity)?.recreate()   // reload the screen in the new language
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                        icon = {},
                        label = { Text(name, maxLines = 1) }
                    )
                }
            }
        }

        TextButton(onClick = { IslandSettings.resetAll() }) {
            Icon(Icons.Rounded.RestartAlt, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.cust_reset))
        }
    }
}

/** Color swatch: the selected one pops up a little and shows a ring that fades in. */
@Composable
private fun ColorDot(argb: Int, selected: Boolean, onClick: () -> Unit) {
    val ring by animateFloatAsState(if (selected) 1f else 0f, tween(200), label = "dotRing")
    val sc by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "dotScale"
    )
    Box(
        Modifier
            .size(40.dp)
            .graphicsLayer { scaleX = sc; scaleY = sc }
            .clip(CircleShape)
            .background(Color(argb))
            .border(3.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = ring), CircleShape)
            .clickable(onClick = onClick)
    )
}
