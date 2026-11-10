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

// SPLITUI  (Settings rows: FlagRow, FloatSetting, IntSetting, ChoiceRow, pct, x1.)

/** Switch row bound to a Boolean setting. Saves on every change. */
@Composable
internal fun FlagRow(
    icon: ImageVector,
    @StringRes title: Int,
    flag: MutableStateFlow<Boolean>,
    @StringRes sub: Int? = null,
) {
    val v by flag.collectAsState()
    ToggleRow(
        icon = icon,
        title = stringResource(title),
        checked = v,
        onChange = { flag.value = it; IslandSettings.save() },
        subtitle = sub?.let { stringResource(it) },
        modifier = Modifier.searchAnchor(title),   // SEARCHKIT
    )
}

/** Slider bound to a Float setting. With [center] the slider starts in the middle. */
@Composable
internal fun FloatSetting(
    @StringRes title: Int,
    icon: ImageVector,
    flow: MutableStateFlow<Float>,
    range: ClosedFloatingPointRange<Float>,
    center: Float? = null,
    format: (Float) -> String,
) {
    val v by flow.collectAsState()
    Box(Modifier.searchAnchor(title)) {   // SEARCHKIT
        if (center != null) {
            CenteredSettingSlider(
                stringResource(title), icon, format(v), v, range, center,
                { flow.value = it }, { IslandSettings.save() }
            )
        } else {
            SettingSlider(
                stringResource(title), icon, format(v), v, range,
                { flow.value = it }, { IslandSettings.save() }
            )
        }
    }
}

/** Slider bound to an Int setting. [snap] rounds the value to steps (for example 50 ms). */
@Composable
internal fun IntSetting(
    @StringRes title: Int,
    icon: ImageVector,
    flow: MutableStateFlow<Int>,
    range: IntRange,
    snap: Int = 1,
    format: @Composable (Int) -> String,
) {
    val v by flow.collectAsState()
    Box(Modifier.searchAnchor(title)) {   // SEARCHKIT
        SettingSlider(
            stringResource(title), icon, format(v), v.toFloat(),
            range.first.toFloat()..range.last.toFloat(),
            { flow.value = (it / snap).roundToInt() * snap }, { IslandSettings.save() }
        )
    }
}

internal fun pct(v: Float) = "${(v * 100).roundToInt()}%"

internal fun x1(v: Float) = "%.1f×".format(v)

/* ---------- per-activity (IslandKind) editing ---------- */

/** Title + chips to pick one of a few int choices (saved as the chip index). */
@Composable
internal fun ChoiceRow(@StringRes title: Int, flow: MutableStateFlow<Int>, options: List<Int>) {
    val v by flow.collectAsState()
    Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { i, o ->
            FilterChip(
                selected = v == i,
                onClick = { flow.value = i; IslandSettings.save() },
                label = { Text(stringResource(o)) }
            )
        }
    }
}
