package com.islandify.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BorderOuter
import androidx.compose.material.icons.rounded.CameraFront
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.LowPriority
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.islandify.app.R
import com.islandify.app.core.AppLanguage
import com.islandify.app.core.IslandSettings
import com.islandify.app.core.SwipeAction
import com.islandify.app.core.SwipeDir
import com.islandify.app.island.BubbleStore
import com.islandify.app.core.IslandKind
import com.islandify.app.core.KindStyle
import com.islandify.app.ui.components.AppearIn
import com.islandify.app.ui.components.CenteredSettingSlider
import com.islandify.app.ui.components.PreviewCard
import com.islandify.app.ui.components.SectionCard
import com.islandify.app.ui.components.SettingSlider
import com.islandify.app.ui.components.ToggleRow
import com.islandify.app.ui.theme.AccentPresets
import com.islandify.app.ui.theme.ThemeMode
import com.islandify.app.ui.theme.hueOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

/* ============================== PAGE FRAME ============================== */

// SPLITUI  (AppColorsCards.)

@Composable
internal fun AppColorsCards() {
    val theme by IslandSettings.theme.collectAsState()
    val source by IslandSettings.accentSource.collectAsState()
    val seed by IslandSettings.accentSeed.collectAsState()
    val bgStyle by IslandSettings.bgStyle.collectAsState()
    val bgColor by IslandSettings.bgColor.collectAsState()
    val dynamicOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /* accent color */
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

    /* background */
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

    /* theme */
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
}
