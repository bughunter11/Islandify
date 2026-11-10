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

// SPLITUI  (Placement page + NudgePad.)

@Composable
internal fun PlacementPage(showPreview: Boolean = true) {   // STYLEKIT3
    val ctx = LocalContext.current
    val scale by IslandSettings.scale.collectAsState()
    val w by IslandSettings.widthScale.collectAsState()
    val h by IslandSettings.heightScale.collectAsState()
    val ox by IslandSettings.offsetX.collectAsState()
    val oy by IslandSettings.offsetY.collectAsState()

    if (showPreview) PreviewCard(scale, w, h)   // STYLEKIT3

    /* ----- size ----- */
    SectionCard(title = stringResource(R.string.cust_shape), icon = Icons.Rounded.AspectRatio) {
        val presets = listOf(
            Triple(R.string.preset_small, 0.85f, Pair(1f, 1f)),
            Triple(R.string.preset_normal, 1f, Pair(1f, 1f)),
            Triple(R.string.preset_large, 1.2f, Pair(1f, 1f)),
            Triple(R.string.preset_wide, 1f, Pair(1.3f, 1f)),
            Triple(R.string.preset_tall, 1f, Pair(1f, 1.3f)),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { (label, s, wh) ->
                val selected = abs(scale - s) < 0.01f && abs(w - wh.first) < 0.01f && abs(h - wh.second) < 0.01f
                FilterChip(
                    selected = selected,
                    onClick = { IslandSettings.preset(s, wh.first, wh.second) },
                    label = { Text(stringResource(label)) }
                )
            }
        }
        FloatSetting(R.string.cust_size, Icons.Rounded.CropFree, IslandSettings.scale, 0.5f..1.8f, 1f, ::pct)
        FloatSetting(R.string.cust_width, Icons.Rounded.SwapHoriz, IslandSettings.widthScale, 0.6f..1.6f, 1f, ::pct)
        FloatSetting(R.string.cust_height, Icons.Rounded.Height, IslandSettings.heightScale, 0.7f..1.6f, 1f, ::pct)
    }

    /* ----- position + nudge pad ----- */
    SectionCard(title = stringResource(R.string.cust_position), icon = Icons.Rounded.OpenWith) {
        FloatSetting(R.string.cust_left_right, Icons.Rounded.SwapHoriz, IslandSettings.offsetX, -180f..180f, 0f) {
            "${it.roundToInt()} dp"
        }
        FloatSetting(R.string.cust_up_down, Icons.Rounded.SwapVert, IslandSettings.offsetY, 0f..300f) {
            "${it.roundToInt()} dp"
        }
        Text(
            stringResource(R.string.cust_nudge_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        NudgePad(
            onLeft = { IslandSettings.offsetX.value = (ox - 1f).coerceIn(-180f, 180f); IslandSettings.save() },
            onRight = { IslandSettings.offsetX.value = (ox + 1f).coerceIn(-180f, 180f); IslandSettings.save() },
            onUp = { IslandSettings.offsetY.value = (oy - 1f).coerceIn(0f, 300f); IslandSettings.save() },
            onDown = { IslandSettings.offsetY.value = (oy + 1f).coerceIn(0f, 300f); IslandSettings.save() },
            onCenter = { IslandSettings.offsetX.value = 0f; IslandSettings.save() },
        )
    }

    /* ----- camera cut-out ----- */
    SectionCard(title = stringResource(R.string.cust_camera), icon = Icons.Rounded.CameraFront) {
        var result by remember { mutableIntStateOf(0) }   // 0 none yet, 1 aligned, 2 no cut-out
        Text(
            stringResource(R.string.cust_camera_sub),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FilledTonalButton(
            onClick = { result = if (autoAlignToCamera(ctx)) 1 else 2 },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.CenterFocusStrong, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.cust_camera_btn))
        }
        if (result != 0) AppearIn {
            Text(
                stringResource(if (result == 1) R.string.cust_camera_done else R.string.cust_camera_none),
                style = MaterialTheme.typography.bodySmall,
                color = if (result == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    }

    /* ----- MOVEKIT: pill size per activity (music, timer, call...). Banner size ab Banner page me ----- */
    KindSection(R.string.cust_pill_kinds, Icons.Rounded.Widgets, R.string.cust_pill_kinds_sub, PILL_KINDS) { st, edit ->
        KindSlider(R.string.cust_banner_width, Icons.Rounded.SwapHoriz, st.w, 0.6f..1.6f, 1f, ::pct) { edit(st.copy(w = it)) }
        KindSlider(R.string.cust_banner_height, Icons.Rounded.Height, st.h, 0.6f..1.6f, 1f, ::pct) { edit(st.copy(h = it)) }
    }

    /* ----- MOVEKIT: expanded island card size per activity. Message card size ab Message card page me ----- */
    KindSection(R.string.cust_card_kinds, Icons.Rounded.Widgets, R.string.cust_card_kinds_sub, CARD_KINDS) { st, edit ->
        KindSlider(R.string.cust_card_width, Icons.Rounded.SwapHoriz, st.cardW, 0.7f..1.4f, 1f, ::pct) { edit(st.copy(cardW = it)) }
        KindSlider(R.string.cust_card_height, Icons.Rounded.Height, st.cardH, 0.7f..1.4f, 1f, ::pct) { edit(st.copy(cardH = it)) }
    }
}

/** Plus-shaped pad: tap an arrow to move the island by 1 dp. The middle button re-centers it. */
@Composable
internal fun NudgePad(
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onCenter: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilledTonalIconButton(onClick = onUp) {
            Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.dir_up))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onLeft) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.dir_left))
            }
            FilledIconButton(onClick = onCenter) {
                Icon(Icons.Rounded.CenterFocusStrong, stringResource(R.string.cust_center))
            }
            FilledTonalIconButton(onClick = onRight) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.dir_right))
            }
        }
        FilledTonalIconButton(onClick = onDown) {
            Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.dir_down))
        }
    }
}
