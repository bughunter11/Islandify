package com.islandify.app.island

import android.content.Intent
import android.os.BatteryManager
import android.os.Bundle
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.islandify.app.core.IslandSettings
import com.islandify.app.R
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Islandify's own Always-On screen.
 *
 * Android has no API that lets an app draw on the system AOD: the AOD is a system window drawn by
 * SystemUI / the OEM while the display is in doze, and nothing of ours is composed there. Every
 * third-party "Always On AMOLED" app works around this the same way, and so does this screen: the
 * display is kept ON with a black, almost unlit window at the lowest brightness. On an OLED panel
 * black pixels are off, so it looks (and, at ~2% brightness, costs) like an ambient display.
 *
 * The window is started by [AodController] from the accessibility service, sits above the keyguard
 * and shows the clock, the date, the battery and the notifications that are waiting. Tap anywhere
 * (or swipe up) to leave it and see the normal lock screen.
 */
class AodActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Above the keyguard and able to turn the panel on by itself
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        // AOD: as dark as a window can be. The system applies this brightness to the display
        // while the window is visible (window brightness overrides the system level).
        setBrightness(DIM)
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            isAppearanceLightStatusBars = false
        }

        current = WeakReference(this)
        setContent {
            IslandTheme {
                AodScreen(onClose = { AodController.hide() })
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Display switched off while the AOD was up (power button): stay dark until the next wake.
        if (!isChangingConfigurations && !isFinishing) AodController.onScreenOffWhileShowing()
    }

    override fun onDestroy() {
        if (current.get() === this) current = WeakReference(null)
        AodController.onActivityDestroyed()
        super.onDestroy()
    }

    private fun setBrightness(v: Float) {
        window.attributes = window.attributes.apply { screenBrightness = v }
    }

    companion object {
        private var current = WeakReference<AodActivity>(null)

        /** Brightness while nothing happens: dark enough for a bedroom, light enough to read. */
        private const val DIM = 0.02f
        private const val PULSE = 0.45f
        private const val PULSE_MS = 5000L

        fun close() {
            current.get()?.finish()
        }

        /** A new notification arrived: lift the brightness for a few seconds so it is readable. */
        fun pulse() {
            val a = current.get() ?: return
            a.setBrightness(PULSE)
            a.window.decorView.removeCallbacks(a.dim)
            a.window.decorView.postDelayed(a.dim, PULSE_MS)
        }

        fun start(c: android.content.Context) {
            runCatching {
                c.startActivity(
                    Intent(c, AodActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                )
            }
        }
    }

    private val dim = Runnable { setBrightness(DIM) }
}

/* ------------------------------- the screen ------------------------------- */

@Composable
private fun AodScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val showOnLock by IslandSettings.showOnLock.collectAsState()
    val notes by AodNotes.notes.collectAsState()

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000)   // once a minute, on the minute
        }
    }
    val twentyFour = remember { DateFormat.is24HourFormat(ctx) }
    val time = remember(now / 60_000, twentyFour) {
        SimpleDateFormat(if (twentyFour) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(now))
    }
    val amPm = if (!twentyFour) {
        SimpleDateFormat("a", Locale.getDefault()).format(Date(now))
    } else ""
    val date = remember(now / 60_000) {
        SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now))
    }
    val battery = remember(now / 60_000) {
        runCatching {
            ctx.getSystemService(BatteryManager::class.java)
                ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
        }.getOrDefault(0)
    }

    // New notification on an AOD that is already up: make it readable for a few seconds.
    // (The first composition only shows whatever was already waiting, it does not pulse.)
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(notes.firstOrNull()?.key) {
        if (first) first = false else AodActivity.pulse()
    }

    val muted = Color(0xFF8E8E93)
    val dim = Color(0xFFB9B9BE)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.9f))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    time,
                    color = Color.White,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Light
                )
                if (amPm.isNotBlank()) {
                    Text(
                        amPm,
                        color = dim,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(start = 6.dp, bottom = 14.dp)
                    )
                }
            }
            Text(date, color = muted, fontSize = 15.sp)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = muted, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(6.dp))
                Text("$battery%", color = muted, fontSize = 13.sp)
            }

            Spacer(Modifier.weight(1f))

            if (notes.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    notes.take(3).forEach { n ->
                        AodNoteRow(n, showText = showOnLock, titleColor = Color.White, textColor = dim)
                    }
                }
                Spacer(Modifier.height(26.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ArrowUpward, null, tint = muted, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.aod_unlock), color = muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(34.dp))
        }
    }
}

@Composable
private fun AodNoteRow(
    n: AodNote,
    showText: Boolean,
    titleColor: Color,
    textColor: Color,
) {
    Row(verticalAlignment = Alignment.Top) {
        // Small app dot instead of an icon: no extra decoding, no bright chrome
        Box(
            Modifier
                .padding(top = 3.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color(0xFF2C2C2E)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                n.app.trim().take(1).uppercase(),
                color = Color(0xFFD1D1D6),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            if (showText) {
                if (n.title.isNotBlank()) {
                    Text(
                        n.title, color = titleColor, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (n.text.isNotBlank() && n.text != n.title) {
                    Text(
                        n.text, color = textColor, fontSize = 13.sp, maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Text(
                    stringResource(R.string.aod_new_notification, n.app),
                    color = textColor, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
