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
import androidx.compose.ui.graphics.toArgb   // SEEKDESIGN
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

// SPLITUI  (AppleSeekBar (wavy) + SeekBar.)

// IMAGE3SEEK: seek bar ka design Image 3 jaisa (waves played hisse par, thumb par halo, bold time)
private val SeekWaveLen = floatArrayOf(0.30f, 0.23f, 0.37f, 0.27f)     // wavelength / track ki lambai
private val SeekWaveOff = floatArrayOf(0.0f, 2.1f, 4.0f, 5.3f)          // phase offset
private val SeekWaveSpd = floatArrayOf(0.5f, -0.5f, 0.25f, -0.75f)      // wavePhase ka multiplier (8*PI par wrap-safe)
private val SeekWaveAmp = floatArrayOf(0.85f, 1.0f, 0.7f, 0.95f)        // unchai ka hissa
private val SeekWaveAlpha = floatArrayOf(0.85f, 0.75f, 0.5f, 0.35f)     // transparency
private val SeekWaveShade = floatArrayOf(0.30f, 0.0f, -0.30f, -0.45f)   // + = halka (safed ki taraf), - = gehra

private fun seekShade(c: Color, k: Float): Color =
    if (k >= 0f) androidx.compose.ui.graphics.lerp(c, Color.White, k)
    else androidx.compose.ui.graphics.lerp(c, Color.Black, -k)

// SEEKDESIGN: album ke rang se thoda alag (hue +35, thoda chamakdar) taaki seekbar gaane ke visuals me ghul na jaye
internal fun seekContrast(c: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(c.toArgb(), hsv)
    if (hsv[1] < 0.15f) return Color(0xFF22D3EE)   // album grey / safed hai to cyan
    return Color.hsv((hsv[0] + 35f) % 360f, hsv[1].coerceIn(0.55f, 0.95f), hsv[2].coerceIn(0.85f, 1f))
}

/** Seek bar (Image 3 design): drag or tap. Left = played, right = time left. */
@Composable
internal fun AppleSeekBar(m: IslandMode.Media, onSeek: (Long) -> Unit) {
    var pos by remember { mutableLongStateOf(m.currentPosition()) }
    LaunchedEffect(m.playing, m.positionMs, m.updatedAt, m.title) {
        pos = m.currentPosition()
        while (m.playing) {
            delay(250)
            pos = m.currentPosition()
        }
    }
    val dur = m.durationMs
    var pressed by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    val colorMode by IslandSettings.seekBarColorMode.collectAsState()   // 0=Solid, 1=Gradient, 2=Album
    val primary = MaterialTheme.colorScheme.primary
    val albumAccent = m.accentColor(primary)
    val design by IslandSettings.seekDesign.collectAsState()   // SEEKDESIGN
    val customInt by IslandSettings.seekCustomColor.collectAsState()
    val timeTint by IslandSettings.seekTimeTint.collectAsState()
    val barBase: Color = when (colorMode) {
        2 -> albumAccent
        3 -> Color(customInt)
        4 -> seekContrast(albumAccent)
        else -> Color.White
    }
    val timeColor: Color = if (timeTint) barBase else Color.White
    val barH by animateDpAsState(
        if (pressed) 8.dp else 5.dp,
        spring(stiffness = 600f), label = "seekH"
    )
    val thumbR by animateDpAsState(
        if (pressed) 10.dp else 8.dp,
        spring(dampingRatio = 0.5f, stiffness = 500f), label = "seekThumb"
    )
    // lehrein gaana chalte waqt uthti hain, pause par bar par baith jaati hain
    val waveOn by animateFloatAsState(
        if (m.playing) 1f else 0f,
        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow), label = "seekAmp"
    )
    var wavePhase by remember { mutableStateOf(0f) }
    val reduceMotion = com.islandify.app.core.rememberReduceMotion()   // MOTIONKIT (PATCH_10_11_12): lehrein behti nahi
    LaunchedEffect(m.playing, reduceMotion) {
        // frames sirf tab jab gaana chal raha ho (pause = zero battery)
        if (!m.playing || reduceMotion) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            wavePhase = (wavePhase + (now - last) / 1_000_000_000f * 3f) % (8f * PI.toFloat())
            last = now
        }
    }
    val wavePath = remember { androidx.compose.ui.graphics.Path() }
    val frac = dragFrac ?: if (dur > 0) (pos / dur.toFloat()).coerceIn(0f, 1f) else 0f
    val shownPos = if (dur > 0) (frac * dur).toLong() else pos

    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .pointerInput(dur) {
                    val pad = 8.dp.toPx()
                    fun fracAt(px: Float): Float =
                        ((px - pad) / (size.width - 2f * pad).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        pressed = true
                        dragFrac = fracAt(down.position.x)
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            dragFrac = fracAt(ch.position.x)
                            ch.consume()
                            if (!ch.pressed) break
                        }
                        val f = dragFrac
                        if (dur > 0 && f != null) {
                            val target = (f * dur).toLong().coerceIn(0L, dur)
                            pos = target
                            onSeek(target)
                        }
                        pressed = false
                        dragFrac = null
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxWidth().height(30.dp)) {
                val x0 = 8.dp.toPx()
                val x1 = size.width - x0
                val tw = (x1 - x0).coerceAtLeast(1f)
                val cy = 18.dp.toPx()
                val bar = barH.toPx()
                val x = x0 + tw * frac
                val playedLen = x - x0

                // rang: (left, right). Solid = safed, Gradient = cyan->purple, Album = album ka rang (halka -> gehra)
                val (c0, c1) = when (colorMode) {
                    1 -> Color(0xFF00C8FF) to Color(0xFF9B6DFF)
                    2 -> androidx.compose.ui.graphics.lerp(albumAccent, Color.White, 0.30f) to albumAccent
                    3 -> androidx.compose.ui.graphics.lerp(Color(customInt), Color.White, 0.25f) to Color(customInt)   // SEEKDESIGN
                    4 -> androidx.compose.ui.graphics.lerp(seekContrast(albumAccent), Color.White, 0.25f) to seekContrast(albumAccent)   // SEEKDESIGN
                    else -> Color.White to Color.White
                }
                // Gradient mode: poore track par (gaane ke progress ke saath rang badle). Baaki: played hisse par.
                val gEnd = if (colorMode == 1) x1 else x
                fun brushFor(k0: Float, k1: Float): Brush = Brush.horizontalGradient(
                    listOf(seekShade(c0, k0), seekShade(c1, k1)),
                    startX = x0, endX = maxOf(gEnd, x0 + 1f)
                )

                // 1) poora track (baaki hissa) halka grey
                drawLine(
                    Color.White.copy(alpha = 0.26f),
                    Offset(x0, cy), Offset(x1, cy), strokeWidth = bar, cap = StrokeCap.Round
                )

                // 2) played hisse ke upar 4 patli lehrati lines: dono sire flat, beech me unchi
                if (design == 0 && waveOn > 0.01f && playedLen > 12.dp.toPx()) {   // SEEKDESIGN
                    val amp = 14.dp.toPx() * waveOn * minOf(1f, playedLen / 80.dp.toPx())
                    val base = cy - bar * 0.3f
                    val lineW = 1.6f.dp.toPx()
                    for (i in 0 until 4) {
                        val wl = tw * SeekWaveLen[i]
                        val path = wavePath
                        path.rewind()
                        var px = x0
                        var first = true
                        while (px < x) {
                            val t = (px - x0) / playedLen
                            val env = kotlin.math.sqrt(sin(PI.toFloat() * t).coerceAtLeast(0f))   // dono sire flat, beech tak jaldi unchi
                            val ph = 2f * PI.toFloat() * (px - x0) / wl + SeekWaveOff[i] + wavePhase * SeekWaveSpd[i]
                            val hump = 0.5f - 0.5f * kotlin.math.cos(ph)   // 0..1, minimum bar par baitha
                            val y = base - amp * SeekWaveAmp[i] * env * hump
                            if (first) { path.moveTo(px, y); first = false } else path.lineTo(px, y)
                            px += 2f
                        }
                        path.lineTo(x, base)
                        drawPath(
                            path,
                            brushFor(SeekWaveShade[i], SeekWaveShade[i]),
                            alpha = SeekWaveAlpha[i],
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = lineW, cap = StrokeCap.Round,
                                join = androidx.compose.ui.graphics.StrokeJoin.Round
                            )
                        )
                    }
                }

                // SEEKDESIGN: 1 = Neon Pulse, 2 = Aurora
                if (design != 0) {
                    val en = maxOf(0.2f + 0.55f * waveOn, if (pressed) 1f else 0f)
                    val pal: List<Color> = if (colorMode == 0 || colorMode == 1) {
                        if (design == 1) listOf(Color(0xFFFF4FD8), Color(0xFF8B5CF6), Color(0xFF22D3EE))
                        else listOf(Color(0xFF34D399), Color(0xFF22D3EE), Color(0xFFA78BFA))
                    } else listOf(seekShade(c0, 0.15f), c1, seekShade(c1, -0.25f))
                    val full = Brush.horizontalGradient(pal, startX = x0, endX = x1)
                    val pi = PI.toFloat()
                    if (design == 1) {
                        // Neon Pulse: thumb ke paas tez hoti hui do waves + glow
                        if (playedLen > 0.5f) {
                            drawLine(brush = full, start = Offset(x0, cy), end = Offset(x, cy),
                                strokeWidth = bar * 2.6f, cap = StrokeCap.Round, alpha = 0.18f)
                        }
                        val k = 0.06f / density
                        for (j in 0..1) {
                            val path = wavePath
                            path.rewind()
                            var px = x0
                            var first = true
                            while (px <= x1) {
                                val dxv = (px - x) / 75.dp.toPx()
                                val gs = kotlin.math.exp(-(dxv * dxv))
                                val amp = (2.5.dp.toPx() + 10.5.dp.toPx() * gs * (0.45f + 0.55f * en)) * (0.3f + 0.7f * waveOn)
                                val y = cy - amp * sin(px * k - wavePhase * 2f + j * 1.7f)
                                if (first) { path.moveTo(px, y); first = false } else path.lineTo(px, y)
                                px += 3f
                            }
                            drawPath(
                                path, brush = full, alpha = if (j == 1) 0.55f else 1f,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = (if (j == 1) 1.5f else 2.5f).dp.toPx(), cap = StrokeCap.Round,
                                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                                )
                            )
                        }
                    } else {
                        // Aurora: played hisse par teen rang ki naram lehrein
                        if (playedLen > 0.5f) {
                            val ks = floatArrayOf(2.0f, 3.0f, 1.4f)
                            val sps = floatArrayOf(1f, -1f, 0.5f)
                            val phs = floatArrayOf(0f, 1.5f, 3f)
                            val amps = floatArrayOf(14f, 12f, 10f)
                            for (i in 0..2) {
                                val col = pal[i]
                                val path = wavePath
                                path.rewind()
                                path.moveTo(x0, cy)
                                var px = x0
                                while (px < x) {
                                    val u = (px - x0) / playedLen
                                    val env = sin(pi * u).coerceAtLeast(0f)
                                    val h = 3.dp.toPx() + env * amps[i].dp.toPx() * (0.4f + 0.6f * en) *
                                        (0.6f + 0.4f * sin(u * ks[i] * 2f * pi + wavePhase * sps[i] + phs[i]))
                                    path.lineTo(px, cy - h)
                                    px += 3f
                                }
                                path.lineTo(x, cy)
                                path.close()
                                drawPath(
                                    path,
                                    brush = Brush.verticalGradient(
                                        listOf(col.copy(alpha = 0.55f), col.copy(alpha = 0f)),
                                        startY = cy - 20.dp.toPx(), endY = cy
                                    )
                                )
                            }
                        }
                    }
                    // dono designs: played line (palette gradient)
                    if (playedLen > 0.5f) {
                        drawLine(brush = full, start = Offset(x0, cy), end = Offset(x, cy),
                            strokeWidth = bar * 0.8f, cap = StrokeCap.Round)
                    }
                }
                // 3) played bar: seedhi, mota, gradient (waves ke upar, taaki unke sire bar me ghul jayein)
                if (design == 0 && playedLen > 0.5f) {   // SEEKDESIGN
                    drawLine(
                        brush = brushFor(0f, 0f),
                        start = Offset(x0, cy), end = Offset(x, cy),
                        strokeWidth = bar, cap = StrokeCap.Round
                    )
                }

                // 4) thumb: hamesha dikhta hai, charon taraf halo ring
                val tr = thumbR.toPx()
                val thumbColor = if (design != 0) Color.White else if (colorMode == 1) androidx.compose.ui.graphics.lerp(c0, c1, frac) else c1
                drawCircle(thumbColor.copy(alpha = 0.35f), radius = tr + 3.dp.toPx(), center = Offset(x, cy))
                drawCircle(thumbColor, radius = tr, center = Offset(x, cy))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Label(formatClock(shownPos), color = timeColor, size = 13, weight = FontWeight.Bold, tabular = true)   // SEEKDESIGN
            Label(
                if (dur > 0) "\u2212" + formatClock((dur - shownPos).coerceAtLeast(0L)) else "--:--",
                color = timeColor,   // SEEKDESIGN
                size = 13, weight = FontWeight.Bold, tabular = true
            )
        }
    }
}

/** Tap to seek. The position advances by itself every 250 ms. */
@Composable
internal fun SeekBar(m: IslandMode.Media, accent: Color, onSeek: (Long) -> Unit) {
    var pos by remember { mutableLongStateOf(m.currentPosition()) }
    LaunchedEffect(m.playing, m.positionMs, m.updatedAt, m.title) {
        pos = m.currentPosition()
        while (m.playing) {
            delay(250)
            pos = m.currentPosition()
        }
    }
    val dur = m.durationMs
    val frac = if (dur > 0) (pos / dur.toFloat()).coerceIn(0f, 1f) else 0f

    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(18.dp)
                .pointerInput(dur) {
                    detectTapGestures { off ->
                        if (dur > 0) onSeek((off.x / size.width * dur).toLong().coerceIn(0L, dur))
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Track))
            Box(Modifier.fillMaxWidth(frac).height(4.dp).clip(CircleShape).background(accent))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label(formatClock(pos), color = Muted, size = 11, tabular = true)
            Label(if (dur > 0) formatClock(dur) else "--:--", color = Muted, size = 11, tabular = true)
        }
    }
}
