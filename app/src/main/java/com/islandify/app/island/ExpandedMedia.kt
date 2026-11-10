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
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
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

// SPLITKIT  Expanded / Large card: Media. (Kaun sa card dikhana hai ye IslandExpanded.kt me chunta hai.)

// MUSICLAYOUT: layout constants
private const val LAYOUT_CLASSIC    = 0
private const val LAYOUT_EXPRESSIVE = 1
private const val LAYOUT_GLOW       = 2
private const val LAYOUT_SPECTRUM   = 3

@Composable
internal fun ExpandedMedia(m: IslandMode.Media, large: Boolean, a: IslandActions) {
    val musicLayout by IslandSettings.musicPlayerLayout.collectAsState()
    when (musicLayout) {
        LAYOUT_EXPRESSIVE -> ExpandedMediaExpressive(m, large, a)
        LAYOUT_GLOW       -> ExpandedMediaGlow(m, large, a)
        LAYOUT_SPECTRUM   -> ExpandedMediaSpectrum(m, large, a)
        else              -> ExpandedMediaClassic(m, large, a)
    }
}

@Composable
private fun ExpandedMediaClassic(m: IslandMode.Media, large: Boolean, a: IslandActions) {
    val primary = MaterialTheme.colorScheme.primary
    val accent = m.accentColor(primary)
    val apple by IslandSettings.appleMusic.collectAsState()   // APPLEMUSIC
    val artMul by IslandSettings.musicArtSize.collectAsState()   // MUSICKIT
    val artCorner by IslandSettings.musicArtCorner.collectAsState()
    val waveOn by IslandSettings.musicWave.collectAsState()
    val look = LocalShellLook.current   // M3DESIGN: dynamic design me icon / waveform ke M3 rang
    val artScale by animateFloatAsState(
        if (apple && !m.playing) 0.86f else 1f,
        spring(dampingRatio = 0.6f, stiffness = 400f), label = "artScale"
    )
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Song photo: tap = open the music app. Tap on the black part = back to the small pill.
            Box(
                Modifier
                    .clip(RoundedCornerShape(artCorner.dp))   // MUSICKIT
                    .clickable { a.onOpenMedia() }
            ) {
                Box(Modifier.graphicsLayer { scaleX = artScale; scaleY = artScale }) {   // APPLEMUSIC
                    Art(m, (if (large) 60.dp else 52.dp) * artMul, artCorner.dp)   // MUSICKIT
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Label(m.title.ifBlank { Isl.unknown }, size = if (large) 18 else 16, weight = FontWeight.SemiBold)
                Label(m.artist, color = Muted, size = 13, weight = FontWeight.Normal)
            }
            Spacer(Modifier.width(10.dp))
            if (waveOn) Waveform(m.playing, if (look.dynamic) look.accent else accent, Modifier.height(22.dp), speed = rememberWaveSpeed(m.speed))   // MUSICKIT
        }
        AppleSeekBar(m, a.onSeek)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIcon(
                if (apple) Icons.Rounded.FastRewind else Icons.Rounded.SkipPrevious,
                if (apple) 60.dp else 52.dp, Color.Transparent, look.on, a.onPrev
            )
            RoundIcon(
                if (m.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (apple) 68.dp else 60.dp, Color.Transparent, look.on, a.onPlayPause
            )
            RoundIcon(
                if (apple) Icons.Rounded.FastForward else Icons.Rounded.SkipNext,
                if (apple) 60.dp else 52.dp, Color.Transparent, look.on, a.onNext
            )
        }
        // MUSICLAYOUT: secondary controls row — shuffle, volume down, volume up, repeat
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val shuffleColor = if (m.shuffleOn) accent else look.on.copy(alpha = 0.45f)
            RoundIconD(com.islandify.app.R.string.a11y_shuffle, Icons.Rounded.Shuffle, 44.dp, Color.Transparent, shuffleColor, a.onShuffle)
            RoundIconD(com.islandify.app.R.string.a11y_volume_down, Icons.AutoMirrored.Rounded.VolumeDown, 44.dp, Color.Transparent, look.on.copy(alpha = 0.7f), a.onVolumeDown)
            RoundIconD(com.islandify.app.R.string.a11y_volume_up, Icons.AutoMirrored.Rounded.VolumeUp, 44.dp, Color.Transparent, look.on.copy(alpha = 0.7f), a.onVolumeUp)
            val repeatColor = if (m.repeatMode > 0) accent else look.on.copy(alpha = 0.45f)
            RoundIconD(if (m.repeatMode == 2) com.islandify.app.R.string.a11y_repeat_one else com.islandify.app.R.string.a11y_repeat, if (m.repeatMode == 2) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, 44.dp, Color.Transparent, repeatColor, a.onRepeat)
        }
    }
}

// ─── EXPRESSIVE layout: wavy seekbar + large buttons ───────────────────────
@Composable
private fun ExpandedMediaExpressive(m: IslandMode.Media, large: Boolean, a: IslandActions) {
    val primary = MaterialTheme.colorScheme.primary
    val accent = m.accentColor(primary)
    val look = LocalShellLook.current
    val artCorner by IslandSettings.musicArtCorner.collectAsState()
    val artMul by IslandSettings.musicArtSize.collectAsState()
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Art(m, (if (large) 60.dp else 52.dp) * artMul, artCorner.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Label(m.title.ifBlank { Isl.unknown }, size = if (large) 19 else 17, weight = FontWeight.Bold)
                Label(m.artist, color = Muted, size = 14, weight = FontWeight.Normal)
            }
        }
        // Wavy seek bar (AppleSeekBar already kaam karta hai)
        AppleSeekBar(m, a.onSeek)
        // Larger buttons
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.Rounded.SkipPrevious, 64.dp, Color.Transparent, look.on, a.onPrev)
            RoundIcon(if (m.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, 72.dp, accent, look.on, a.onPlayPause)
            RoundIcon(Icons.Rounded.SkipNext, 64.dp, Color.Transparent, look.on, a.onNext)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            val shuffleColor = if (m.shuffleOn) accent else look.on.copy(alpha = 0.45f)
            RoundIconD(com.islandify.app.R.string.a11y_shuffle, Icons.Rounded.Shuffle, 44.dp, Color.Transparent, shuffleColor, a.onShuffle)
            RoundIconD(com.islandify.app.R.string.a11y_volume_down, Icons.AutoMirrored.Rounded.VolumeDown, 44.dp, Color.Transparent, look.on.copy(0.7f), a.onVolumeDown)
            RoundIconD(com.islandify.app.R.string.a11y_volume_up, Icons.AutoMirrored.Rounded.VolumeUp, 44.dp, Color.Transparent, look.on.copy(0.7f), a.onVolumeUp)
            val repeatColor = if (m.repeatMode > 0) accent else look.on.copy(alpha = 0.45f)
            RoundIconD(if (m.repeatMode == 2) com.islandify.app.R.string.a11y_repeat_one else com.islandify.app.R.string.a11y_repeat, if (m.repeatMode == 2) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, 44.dp, Color.Transparent, repeatColor, a.onRepeat)
        }
    }
}

// ─── GLOW layout: album art colors glow up background ──────────────────────
@Composable
private fun ExpandedMediaGlow(m: IslandMode.Media, large: Boolean, a: IslandActions) {
    val primary = MaterialTheme.colorScheme.primary
    val accent = m.accentColor(primary)
    val look = LocalShellLook.current
    val artCorner by IslandSettings.musicArtCorner.collectAsState()
    val artMul by IslandSettings.musicArtSize.collectAsState()
    Box(Modifier.fillMaxSize()) {
        // Bottom glow from album art accent
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
                .align(Alignment.BottomCenter)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Transparent, accent.copy(alpha = 0.35f))
                    )
                )
        )
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Art(m, (if (large) 60.dp else 52.dp) * artMul, artCorner.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Label(m.title.ifBlank { Isl.unknown }, size = if (large) 18 else 16, weight = FontWeight.SemiBold)
                    Label(m.artist, color = Muted, size = 13, weight = FontWeight.Normal)
                }
            }
            AppleSeekBar(m, a.onSeek)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(Icons.Rounded.SkipPrevious, 52.dp, Color.Transparent, look.on, a.onPrev)
                RoundIcon(if (m.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, 60.dp, Color.Transparent, look.on, a.onPlayPause)
                RoundIcon(Icons.Rounded.SkipNext, 52.dp, Color.Transparent, look.on, a.onNext)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                val sc = if (m.shuffleOn) accent else look.on.copy(0.45f)
                RoundIconD(com.islandify.app.R.string.a11y_shuffle, Icons.Rounded.Shuffle, 40.dp, Color.Transparent, sc, a.onShuffle)
                RoundIconD(com.islandify.app.R.string.a11y_volume_down, Icons.AutoMirrored.Rounded.VolumeDown, 40.dp, Color.Transparent, look.on.copy(0.7f), a.onVolumeDown)
                RoundIconD(com.islandify.app.R.string.a11y_volume_up, Icons.AutoMirrored.Rounded.VolumeUp, 40.dp, Color.Transparent, look.on.copy(0.7f), a.onVolumeUp)
                val rc = if (m.repeatMode > 0) accent else look.on.copy(0.45f)
                RoundIconD(if (m.repeatMode == 2) com.islandify.app.R.string.a11y_repeat_one else com.islandify.app.R.string.a11y_repeat, if (m.repeatMode == 2) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, 40.dp, Color.Transparent, rc, a.onRepeat)
            }
        }
    }
}

// ─── SPECTRUM layout: equalizer bars move to the song ──────────────────────
@Composable
private fun ExpandedMediaSpectrum(m: IslandMode.Media, large: Boolean, a: IslandActions) {
    val primary = MaterialTheme.colorScheme.primary
    val accent = m.accentColor(primary)
    val look = LocalShellLook.current
    val artCorner by IslandSettings.musicArtCorner.collectAsState()
    val artMul by IslandSettings.musicArtSize.collectAsState()
    val waveOn by IslandSettings.musicWave.collectAsState()
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Art(m, (if (large) 60.dp else 52.dp) * artMul, artCorner.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Label(m.title.ifBlank { Isl.unknown }, size = if (large) 18 else 16, weight = FontWeight.SemiBold)
                Label(m.artist, color = Muted, size = 13, weight = FontWeight.Normal)
            }
        }
        // Full-width spectrum bars (Waveform composable, wider)
        if (waveOn) {
            Waveform(m.playing, accent, Modifier.fillMaxWidth().height(40.dp), speed = rememberWaveSpeed(m.speed))
        } else {
            AppleSeekBar(m, a.onSeek)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.Rounded.SkipPrevious, 52.dp, Color.Transparent, look.on, a.onPrev)
            RoundIcon(if (m.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, 60.dp, accent, look.on, a.onPlayPause)
            RoundIcon(Icons.Rounded.SkipNext, 52.dp, Color.Transparent, look.on, a.onNext)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            val sc = if (m.shuffleOn) accent else look.on.copy(0.45f)
            RoundIconD(com.islandify.app.R.string.a11y_shuffle, Icons.Rounded.Shuffle, 40.dp, Color.Transparent, sc, a.onShuffle)
            RoundIconD(com.islandify.app.R.string.a11y_volume_down, Icons.AutoMirrored.Rounded.VolumeDown, 40.dp, Color.Transparent, look.on.copy(0.7f), a.onVolumeDown)
            RoundIconD(com.islandify.app.R.string.a11y_volume_up, Icons.AutoMirrored.Rounded.VolumeUp, 40.dp, Color.Transparent, look.on.copy(0.7f), a.onVolumeUp)
            val rc = if (m.repeatMode > 0) accent else look.on.copy(0.45f)
            RoundIconD(if (m.repeatMode == 2) com.islandify.app.R.string.a11y_repeat_one else com.islandify.app.R.string.a11y_repeat, if (m.repeatMode == 2) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, 40.dp, Color.Transparent, rc, a.onRepeat)
        }
    }
}
