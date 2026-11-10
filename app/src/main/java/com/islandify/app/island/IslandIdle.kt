package com.islandify.app.island

import com.islandify.app.core.*
import com.islandify.app.ui.components.*
import com.islandify.app.ui.onboarding.*
import com.islandify.app.ui.screens.*
import com.islandify.app.ui.theme.*

import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

// SPLITUI  (Idle card + idle pill info + unke helpers.)

/** Shown when nothing is active and the island is tapped: time, date, battery. */
@Composable
internal fun IdleCard(large: Boolean = false) {   // CALLIST
    if (!large) {
        IdleCardMain(true)
        return
    }
    // CALLIST: long-press = upar time / date / battery, neeche agle events
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(92.dp)) { IdleCardMain(false) }
        IdleEventList()
    }
}

/** CALLIST: bade idle card ki event list (max 3). */
@Composable
internal fun IdleEventList() {
    val ctx = LocalContext.current
    val briefCal by IslandSettings.briefCalendar.collectAsState()
    val calHrs by IslandSettings.calHours.collectAsState()
    val calAcc by IslandSettings.calAccounts.collectAsState()
    val events by produceState<List<CalEvent>?>(null, briefCal, calHrs, calAcc) {
        value = if (briefCal) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { upcomingEvents(ctx, 3) }
        } else emptyList()
    }
    val pattern = if (android.text.format.DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm a"
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 26.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val list = events
        when {
            !briefCal -> Label(androidx.compose.ui.res.stringResource(com.islandify.app.R.string.idle_cal_off), color = Muted, size = 12, weight = FontWeight.Normal)
            list == null -> {}
            list.isEmpty() -> Label(androidx.compose.ui.res.stringResource(com.islandify.app.R.string.idle_cal_empty), color = Muted, size = 12, weight = FontWeight.Normal)
            else -> list.forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val tm = if (e.begin <= System.currentTimeMillis()) "\u25CF"
                    else java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault()).format(java.util.Date(e.begin))
                    Label(tm, color = Orange, size = 12, weight = FontWeight.SemiBold, tabular = true)
                    Spacer(Modifier.width(10.dp))
                    Label(e.title, modifier = Modifier.weight(1f), size = 13, weight = FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
internal fun IdleCardMain(showNext: Boolean) {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(10_000)
        }
    }
    // (level %, charging) from the sticky battery broadcast: one read, no receiver to leak
    val (battery, charging) = remember(now) {
        runCatching {
            val i = ctx.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val level = i?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = i?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
            val status = i?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) level * 100 / scale else 0
            pct to (status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                status == android.os.BatteryManager.BATTERY_STATUS_FULL)
        }.getOrDefault(0 to false)
    }
    val batteryColor = when {
        charging -> Green
        battery <= 15 -> Red
        battery <= 30 -> Orange
        else -> Green
    }
    val batteryIcon = when {
        charging -> Icons.Rounded.BatteryChargingFull
        battery <= 15 -> Icons.Rounded.BatteryAlert
        battery <= 50 -> Icons.Rounded.Battery3Bar
        else -> Icons.Rounded.BatteryFull
    }
    val time = remember(now) {
        // Follow the phone's 12 / 24-hour setting
        val pattern = if (android.text.format.DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm"
        java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault()).format(java.util.Date(now))
    }
    val date = remember(now) {
        java.text.SimpleDateFormat("EEE, d MMM", java.util.Locale.getDefault()).format(java.util.Date(now))
    }
    // WEATHERKIT: mausam IO par, 30 min cache; value key badalne par bhi tika rehta hai (flicker nahi)
    val weatherOn by IslandSettings.briefWeather.collectAsState()
    val weatherF by IslandSettings.weatherF.collectAsState()
    val weather by produceState<String?>(null, weatherOn, weatherF, now / 600_000L) {
        value = if (weatherOn) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { WeatherKit.text(ctx, weatherF) }
        } else null
    }
    Row(
        Modifier.fillMaxSize().padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Label(time, size = 34, weight = FontWeight.Light, tabular = true)
            Label(weather?.let { "$date  \u00B7  $it" } ?: date, color = Muted, size = 14, weight = FontWeight.Normal)   // WEATHERKIT
            val briefCal by IslandSettings.briefCalendar.collectAsState()
            val calHrs by IslandSettings.calHours.collectAsState()   // CALKIT
            val calAcc by IslandSettings.calAccounts.collectAsState()   // CALKIT
            val event by produceState<String?>(null, now, briefCal, calHrs, calAcc) {
                value = if (briefCal) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { nextEventText(ctx) }
                } else null
            }
            if (showNext) event?.let { Label(it, color = Muted, size = 12, weight = FontWeight.Normal) }   // CALLIST
            // BUTTERIO: next alarm ki query IO par (main thread par binder call nahi)
            val alarmState by produceState<String?>(null, now) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { nextAlarmText(ctx) }
            }
            val alarm: String? = alarmState
            if (alarm != null) {
                Label(
                    androidx.compose.ui.res.stringResource(com.islandify.app.R.string.idle_alarm, alarm),
                    color = Muted, size = 12, weight = FontWeight.Normal
                )
            }
        }
        Icon(batteryIcon, null, tint = batteryColor, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        Label("$battery%", size = 16, weight = FontWeight.SemiBold)
    }
}

/** Small idle pill: time (or the next alarm) on the left, battery on the right, camera in the middle. */
@Composable
internal fun IdlePillInfo() {
    val on by IslandSettings.idleInfo.collectAsState()
    if (on) IdlePillInfoContent()
}

@Composable
internal fun IdlePillInfoContent() {
    val onlyLow by IslandSettings.idleBatteryOnlyLow.collectAsState()
    val alarmHours by IslandSettings.idleAlarmHours.collectAsState()
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L + 50L)   // wake up right after the minute changes
        }
    }
    val (pct, charging) = remember(now) { batteryInfo(ctx) }
    // BUTTERIO: next alarm ki query IO par
    val alarmAtState by produceState<Long?>(null, now, alarmHours) {
        value = if (alarmHours > 0) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { nextAlarmMillis(ctx) }
        } else null
    }
    val alarmAt: Long? = alarmAtState
    val showAlarm = alarmAt != null && (alarmAt - now) in 0L..(alarmHours * 3_600_000L)
    val pattern = if (android.text.format.DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm"
    val shownMs = alarmAt?.takeIf { showAlarm } ?: now
    val text = remember(shownMs, pattern) {
        java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault()).format(java.util.Date(shownMs))
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showAlarm) {
            Icon(Icons.Rounded.Alarm, null, tint = Orange, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(3.dp))
        }
        Label(
            text, color = if (showAlarm) Orange else Color.White,
            size = 13, weight = FontWeight.SemiBold, tabular = true
        )
    }
    if (!onlyLow || charging || pct <= 30) {
        val c = when {
            charging -> Green
            pct <= 15 -> Red
            pct <= 30 -> Orange
            else -> Color.White
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
                null, tint = c, modifier = Modifier.size(14.dp)
            )
            Label("$pct%", color = c, size = 12, weight = FontWeight.SemiBold, tabular = true)
        }
    } else {
        Spacer(Modifier.width(1.dp))
    }
}

/** Next calendar event in the coming 24 h ("Title · 14:30"), or null. Needs READ_CALENDAR. */
internal fun nextEventText(ctx: android.content.Context): String? = runCatching {
    val granted = androidx.core.content.ContextCompat.checkSelfPermission(ctx, "android.permission.READ_CALENDAR") ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
    if (!granted) return@runCatching null
    val now = System.currentTimeMillis()
    val uri = android.provider.CalendarContract.Instances.CONTENT_URI.buildUpon().also {
        android.content.ContentUris.appendId(it, now)
        android.content.ContentUris.appendId(it, now + com.islandify.app.core.IslandSettings.calHours.value.coerceIn(1, 24) * 3600_000L)   // CALKIT
    }.build()
    val cols = arrayOf(
        android.provider.CalendarContract.Instances.TITLE,
        android.provider.CalendarContract.Instances.BEGIN,
        android.provider.CalendarContract.Instances.END,
        android.provider.CalendarContract.Instances.ALL_DAY,
        android.provider.CalendarContract.Calendars.ACCOUNT_NAME,   // CALKIT
    )
    val accFilter = com.islandify.app.core.IslandSettings.calAccounts.value   // CALKIT: khali = sab accounts
    var out: String? = null
    ctx.contentResolver.query(
        uri, cols, null, null, android.provider.CalendarContract.Instances.BEGIN + " ASC"
    )?.use { c ->
        while (c.moveToNext()) {
            val allDay = c.getInt(3) == 1
            val begin = c.getLong(1)
            val end = c.getLong(2)
            if (allDay || end < now) continue
            if (accFilter.isNotEmpty() && (c.getString(4) ?: "") !in accFilter) continue   // CALKIT
            val title = c.getString(0)?.takeIf { it.isNotBlank() } ?: continue
            val pattern = if (android.text.format.DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm a"
            val time = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault()).format(java.util.Date(begin))
            out = if (begin <= now) title else "$title - $time"
            break
        }
    }
    out
}.getOrNull()

/** CALKIT: phone me jitne calendar accounts hain unke naam (Google / Outlook ...). Needs READ_CALENDAR. */
internal fun calendarAccountNames(ctx: android.content.Context): List<String> = runCatching {
    val granted = androidx.core.content.ContextCompat.checkSelfPermission(ctx, "android.permission.READ_CALENDAR") ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
    if (!granted) return@runCatching emptyList<String>()
    val names = linkedSetOf<String>()
    ctx.contentResolver.query(
        android.provider.CalendarContract.Calendars.CONTENT_URI,
        arrayOf(android.provider.CalendarContract.Calendars.ACCOUNT_NAME),
        null, null, android.provider.CalendarContract.Calendars.ACCOUNT_NAME + " ASC"
    )?.use { c -> while (c.moveToNext()) c.getString(0)?.takeIf { it.isNotBlank() }?.let { names += it } }
    names.toList()
}.getOrDefault(emptyList())

/** "Mon 07:30" from the phone's next alarm clock, or null when none is set. No permission needed. */
internal fun nextAlarmText(ctx: android.content.Context): String? = runCatching {
    val am = ctx.getSystemService(android.app.AlarmManager::class.java)
    val t = am?.nextAlarmClock?.triggerTime ?: return@runCatching null
    val pattern = if (android.text.format.DateFormat.is24HourFormat(ctx)) "EEE HH:mm" else "EEE h:mm a"
    java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault()).format(java.util.Date(t))
}.getOrNull()

internal fun batteryInfo(ctx: android.content.Context): Pair<Int, Boolean> = runCatching {
    val i = ctx.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
    val level = i?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = i?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
    val status = i?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
    val pct = if (level >= 0 && scale > 0) level * 100 / scale else 0
    pct to (status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
        status == android.os.BatteryManager.BATTERY_STATUS_FULL)
}.getOrDefault(0 to false)

internal fun nextAlarmMillis(ctx: android.content.Context): Long? = runCatching {
    ctx.getSystemService(android.app.AlarmManager::class.java)?.nextAlarmClock?.triggerTime
}.getOrNull()
