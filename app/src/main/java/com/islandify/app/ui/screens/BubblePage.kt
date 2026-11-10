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

// SPLITUI  (Bubble page.)

@Composable
internal fun BubblePage() {
    SectionCard(title = stringResource(R.string.cust_bb_look), icon = Icons.Rounded.AspectRatio) {
        FlagRow(Icons.Rounded.Visibility, R.string.cust_bb_enable, IslandSettings.bubbleEnabled, R.string.cust_bb_enable_sub)   // BUBBLETOGGLE
        StylePickerRow(R.string.cust_style_bubble, IslandSettings.bubbleStyle, BUBBLE_STYLE_LABELS, 1)   // STYLEKIT
        FloatSetting(R.string.cust_bb_size, Icons.Rounded.CropFree, IslandSettings.bubbleSize, 24f..56f, 32f) { "${it.roundToInt()} dp" }
        FloatSetting(R.string.cust_bb_gap, Icons.Rounded.SwapHoriz, IslandSettings.bubbleGap, 0f..24f, 2f) { "${it.roundToInt()} dp" }
        FloatSetting(R.string.cust_bb_y, Icons.Rounded.SwapVert, IslandSettings.bubbleY, -60f..60f, 0f) { "${it.roundToInt()} dp" }
        FloatSetting(R.string.cust_bb_alpha, Icons.Rounded.Visibility, IslandSettings.bubbleAlpha, 0.3f..1f, null, ::pct)
        FlagRow(Icons.Rounded.PlayCircle, R.string.cust_bb_extras, IslandSettings.bubbleExtras, R.string.cust_bb_extras_sub)   // MOVEKIT (Pill > Advanced se aaya)
        FlagRow(Icons.Rounded.ViewAgenda, R.string.cust_bb_overlap, IslandSettings.bubbleOverlap, R.string.cust_bb_overlap_sub)   // REVIVEKIT
        FlagRow(Icons.Rounded.OpenWith, R.string.cust_bb_move, IslandSettings.bubbleMove, R.string.cust_bb_move_sub)   // BUBBLEMOVE (GitHub issue #7)
        val bbFreeOn by IslandSettings.bubbleFreeOn.collectAsState()   // BUBBLEMOVE
        if (bbFreeOn) {
            FilledTonalButton(
                onClick = { IslandSettings.bubbleFreeOn.value = false; IslandSettings.save() },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) { Text(stringResource(R.string.cust_bb_move_reset)) }
        }
    }
    SectionCard(title = stringResource(R.string.cust_bb_swipe_title), icon = Icons.Rounded.Swipe) {
        FlagRow(Icons.Rounded.Swipe, R.string.cust_bb_swipe, IslandSettings.bubbleSwipeOn, R.string.cust_bb_swipe_sub)
        FloatSetting(R.string.cust_bb_swipe_dist, Icons.Rounded.TouchApp, IslandSettings.bubbleSwipeDist, 30f..120f, 56f) { "${it.roundToInt()} dp" }
    }
}
