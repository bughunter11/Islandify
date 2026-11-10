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
import androidx.compose.material.icons.rounded.GraphicEq   // PILLWAVE
import androidx.compose.material.icons.rounded.Sync   // PILLWAVE
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

// SPLITUI  (Visuals page + ColorDot.)

@Composable
internal fun VisualsPage(showPreview: Boolean = true) {   // STYLEKIT3
    val scale by IslandSettings.scale.collectAsState()
    val w by IslandSettings.widthScale.collectAsState()
    val h by IslandSettings.heightScale.collectAsState()
    val outlineOn by IslandSettings.outlineOn.collectAsState()

    if (showPreview) PreviewCard(scale, w, h)   // STYLEKIT3

    /* ----- island look ----- */
    SectionCard(title = stringResource(R.string.cust_look_title), icon = Icons.Rounded.AutoAwesome) {
        // STYLEKIT2: pill design ab Pill > Design page me
        FloatSetting(R.string.cust_roundness, Icons.Rounded.RoundedCorner, IslandSettings.corner, 0.2f..1f, null, ::pct)
        FloatSetting(R.string.cust_expanded_corner, Icons.Rounded.RoundedCorner, IslandSettings.expandedCorner, 0f..48f) {
            if (it < 1f) "Auto" else "${it.roundToInt()} dp"
        }
        FloatSetting(R.string.cust_font_scale, Icons.Rounded.TextFields, IslandSettings.fontScale, 0.8f..1.4f, 1f, ::pct)
        FlagRow(Icons.Rounded.AutoAwesome, R.string.cust_glow, IslandSettings.glow, R.string.cust_glow_sub)
        FlagRow(Icons.Rounded.ColorLens, R.string.cust_app_accent, IslandSettings.islandAppAccent, R.string.cust_app_accent_sub)
    }

    /* ----- outline ----- */
    SectionCard(title = stringResource(R.string.cust_outline), icon = Icons.Rounded.BorderOuter) {
        FlagRow(Icons.Rounded.BorderOuter, R.string.cust_outline, IslandSettings.outlineOn, R.string.cust_outline_sub)
        if (outlineOn) AppearIn {
            FloatSetting(R.string.cust_outline_thickness, Icons.Rounded.BorderOuter, IslandSettings.outlineThickness, 0.3f..4f) {
                "%.1f dp".format(it)
            }
            FlagRow(Icons.Rounded.ColorLens, R.string.cust_outline_dynamic, IslandSettings.outlineDynamic)
            FlagRow(Icons.Rounded.Visibility, R.string.cust_outline_hide_expanded, IslandSettings.outlineHideExpanded)
        }
        FlagRow(Icons.Rounded.Lightbulb, R.string.cust_pulse, IslandSettings.pulseShadow, R.string.cust_pulse_sub)
    }

    /* ----- PILLWAVE: music waveform speed (Music card page wali hi settings) ----- */
    SectionCard(title = stringResource(R.string.cust_pill_wave_title), icon = Icons.Rounded.GraphicEq) {
        FloatSetting(R.string.cust_wave_speed, Icons.Rounded.Speed, IslandSettings.waveSpeed, 0.25f..3f, 1f) {
            String.format("%.2fx", it)
        }
        FlagRow(Icons.Rounded.Sync, R.string.cust_wave_follow, IslandSettings.waveFollow, R.string.cust_wave_follow_sub)
    }

    /* ----- MOVEKIT: pill activities ka text size + corner (banner wale ab Banner page me) ----- */
    KindSection(R.string.cust_kind_look, Icons.Rounded.Widgets, kinds = PILL_KINDS) { st, edit ->
        KindSlider(R.string.cust_banner_text, Icons.Rounded.TextFields, st.text, 0.7f..1.5f, 1f, ::pct) { edit(st.copy(text = it)) }
        ToggleRow(
            icon = Icons.Rounded.RoundedCorner,
            title = stringResource(R.string.cust_corner_follow),
            checked = st.corner < 0f,
            onChange = { follow ->
                edit(st.copy(corner = if (follow) -1f else IslandSettings.corner.value))
                IslandSettings.save()
            }
        )
        if (st.corner >= 0f) AppearIn {
            KindSlider(R.string.cust_roundness, Icons.Rounded.RoundedCorner, st.corner, 0.2f..1f, null, ::pct) {
                edit(st.copy(corner = it))
            }
        }
    }

    // (accent / background / theme ab Customize ke main page par niche hain)
}

/** Color swatch: the selected one pops up a little and shows a ring that fades in. */
@Composable
internal fun ColorDot(argb: Int, selected: Boolean, onClick: () -> Unit) {
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
