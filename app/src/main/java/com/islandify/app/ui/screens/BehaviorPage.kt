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
import androidx.compose.material.icons.rounded.PushPin   // CLEANKIT
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
import com.islandify.app.island.unhide   // REVIVEKIT
import com.islandify.app.island.IslandController   // REVIVEKIT
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
import androidx.compose.material.icons.rounded.Cloud

/* ============================== PAGE FRAME ============================== */

// SPLITUI  (Behavior page.)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BehaviorPage() {
    val ctx = LocalContext.current
    val showWhen by IslandSettings.showWhen.collectAsState()
    val idleInfo by IslandSettings.idleInfo.collectAsState()

    /* ----- swipe gestures ----- */
    SectionCard(title = stringResource(R.string.cust_swipe_title), icon = Icons.Rounded.Swipe) {
        Text(
            stringResource(R.string.cust_swipe_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SwipeRow(Icons.Rounded.KeyboardArrowUp, R.string.cust_swipe_up, IslandSettings.swipeFlow(SwipeDir.Up))
        SwipeRow(Icons.Rounded.KeyboardArrowDown, R.string.cust_swipe_down, IslandSettings.swipeFlow(SwipeDir.Down))
        SwipeRow(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, R.string.cust_swipe_left, IslandSettings.swipeFlow(SwipeDir.Left))
        SwipeRow(Icons.AutoMirrored.Rounded.KeyboardArrowRight, R.string.cust_swipe_right, IslandSettings.swipeFlow(SwipeDir.Right))
    }

    /* ----- visibility rules ----- */
    SectionCard(title = stringResource(R.string.cust_rules_title), icon = Icons.Rounded.Visibility) {
        Text(stringResource(R.string.cust_show_when), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.searchAnchor(R.string.cust_show_when))   // SEARCHKIT
        val options = listOf(R.string.show_when_unlocked, R.string.show_when_screen_on, R.string.show_when_always)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = showWhen == i,
                    onClick = { IslandSettings.showWhen.value = i; IslandSettings.save() },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                    icon = {},
                    label = { Text(stringResource(label), maxLines = 1) }
                )
            }
        }
        Text(
            stringResource(R.string.cust_show_when_sub),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // ADVANCEDKIT: home-screen only, hide in own app, landscape aur tap-outside ab Advanced me hain
    }

    QuietHoursCard()   // QUIETKIT

    /* ----- idle pill & extras ----- */
    SectionCard(title = stringResource(R.string.cust_extras_title), icon = Icons.Rounded.Widgets) {
        val idlePillOn by IslandSettings.idlePill.collectAsState()
        ToggleRow(
            icon = Icons.Rounded.Schedule,
            title = stringResource(R.string.cust_idle_info),
            modifier = Modifier.searchAnchor(R.string.cust_idle_info),   // SEARCHKIT
            checked = idlePillOn && idleInfo,
            onChange = {
                IslandSettings.idlePill.value = it
                IslandSettings.idleInfo.value = it
                IslandSettings.save()
            },
            subtitle = stringResource(R.string.cust_idle_info_sub)
        )
        if (idleInfo) AppearIn {
            FlagRow(Icons.Rounded.Lightbulb, R.string.cust_idle_low, IslandSettings.idleBatteryOnlyLow)
        }
        IntSetting(R.string.cust_idle_alarm, Icons.Rounded.Timer, IslandSettings.idleAlarmHours, 0..12) {
            if (it == 0) stringResource(R.string.common_off) else stringResource(R.string.cust_hours, it)
        }

        // WEATHERKIT: mausam ke liye approximate location chahiye
        val weatherOn by IslandSettings.briefWeather.collectAsState()
        val weatherLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            IslandSettings.briefWeather.value = ok
            IslandSettings.save()
        }
        ToggleRow(
            icon = Icons.Rounded.Cloud,
            title = stringResource(R.string.cust_brief_weather),
            checked = weatherOn,
            onChange = { on ->
                val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
                if (!on) {
                    IslandSettings.briefWeather.value = false; IslandSettings.save()
                } else if (granted) {
                    IslandSettings.briefWeather.value = true; IslandSettings.save()
                } else {
                    weatherLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            },
            subtitle = stringResource(R.string.cust_brief_weather_sub)
        )
        if (weatherOn) FlagRow(Icons.Rounded.Cloud, R.string.cust_weather_f, IslandSettings.weatherF)

        // Calendar needs a runtime permission before it can be turned on
        val calendarOn by IslandSettings.briefCalendar.collectAsState()
        val calLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            IslandSettings.briefCalendar.value = ok
            IslandSettings.save()
        }
        ToggleRow(
            icon = Icons.AutoMirrored.Rounded.EventNote,
            title = stringResource(R.string.cust_brief_calendar),
            modifier = Modifier.searchAnchor(R.string.cust_brief_calendar),   // SEARCHKIT
            checked = calendarOn,
            onChange = { on ->
                val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) ==
                    PackageManager.PERMISSION_GRANTED
                if (!on) {
                    IslandSettings.briefCalendar.value = false; IslandSettings.save()
                } else if (granted) {
                    IslandSettings.briefCalendar.value = true; IslandSettings.save()
                } else {
                    calLauncher.launch(Manifest.permission.READ_CALENDAR)
                }
            },
            subtitle = stringResource(R.string.cust_brief_calendar_sub)
        )
        // CALKIT: sirf agle N ghante + account chuno
        if (calendarOn) {
            IntSetting(R.string.cust_cal_hours, Icons.Rounded.Timer, IslandSettings.calHours, 1..24) {
                stringResource(R.string.cust_hours, it)
            }
            val calAccs by IslandSettings.calAccounts.collectAsState()
            val calList by androidx.compose.runtime.produceState(emptyList<String>()) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.islandify.app.island.calendarAccountNames(ctx)
                }
            }
            if (calList.isNotEmpty()) {
                androidx.compose.material3.Text(
                    stringResource(R.string.cust_cal_accounts),
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
                calList.forEach { acc ->
                    ToggleRow(
                        icon = Icons.AutoMirrored.Rounded.EventNote,
                        title = acc,
                        checked = calAccs.isEmpty() || acc in calAccs,
                        onChange = { on ->
                            val cur = if (calAccs.isEmpty()) calList.toSet() else calAccs
                            val next = if (on) cur + acc else cur - acc
                            if (next.isNotEmpty()) {   // kam se kam ek account on rahe
                                IslandSettings.calAccounts.value = if (next.containsAll(calList)) emptySet() else next
                                IslandSettings.save()
                            }
                        }
                    )
                }
            }
        }
        // ADVANCEDKIT: flashlight pill aur camera / mic banners ab Advanced me hain
        FlagRow(Icons.Rounded.Vibration, R.string.cust_haptics, IslandSettings.haptics, R.string.cust_haptics_sub)
        FlagRow(Icons.Rounded.MusicNote, R.string.cust_media_peek, IslandSettings.mediaPeek, R.string.cust_media_peek_sub)   // MERGEKIT: 'Notifications & music' card se aaya
        FlagRow(Icons.Rounded.PushPin, R.string.cust_sticky, IslandSettings.stickyNotif, R.string.cust_sticky_sub)   // CLEANKIT
        FilledTonalButton(   // REVIVEKIT
            onClick = { IslandController.unhide() },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) { Text(stringResource(R.string.cust_unhide)) }
    }

    /* ----- lock screen privacy ----- */
    SectionCard(title = stringResource(R.string.cust_privacy_title), icon = Icons.Rounded.Lock) {
        FlagRow(Icons.Rounded.Lock, R.string.cust_conceal, IslandSettings.concealLocked, R.string.cust_conceal_sub)
        FlagRow(Icons.Rounded.ViewAgenda, R.string.cust_conceal_pics, IslandSettings.concealChatPics, R.string.cust_conceal_pics_sub)
    }

    MotionCard()   // MOTIONKIT (PATCH_10_11_12)

    /* ----- ADVANCEDKIT: collapsible Advanced (kam-use options + activity priority) ----- */
    AdvancedBehavior()
    // (language ab Customize ke main page par niche hai)
}

/** App language (System / English / Hindi). Customize ke main page par niche dikhta hai. */
