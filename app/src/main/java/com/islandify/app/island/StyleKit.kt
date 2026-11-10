package com.islandify.app.island

// STYLEKIT: pill / banner / bubble / message card ke 12-12 design (Customize > Design).
// Pill + Banner paint: 0 Classic, 1 Glass, 2 Gradient, 3 Neon, 4 Tinted, 5 Graphite,
//                      6 Tonal, 7 Surface, 8 Aurora, 9 Duo, 10 Outline, 11 Spotlight.
// Bubble: 0 Circle, 1 Squircle, 2 Ring, 3 Glass, 4 Tinted, 5 Gradient,
//         6 Tonal, 7 Surface, 8 Aurora, 9 Duo, 10 Outline, 11 Spotlight.
// Card: 0 Classic, 1 Glass, 2 Material, 3 Minimal, 4 Neon, 5 Bold,
//       6 Tonal, 7 Surface, 8 Aurora, 9 Duo, 10 Outline, 11 Spotlight.
//
// M3DESIGN
// 0..5  = purane design, ab zyada saaf: halki chamak (sheen), patli border, andar ki glow, gehrai.
//         Inka rang item / accent se aata hai, text safed.
// 6..11 = MATERIAL 3 DYNAMIC. Rang, text, button, badge, reply box sab MaterialTheme.colorScheme se
//         (StyleM3.kt ka ShellLook), isliye wallpaper badalte hi poora design naye rang me aa jata hai.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

internal const val STYLE_COUNT = 12

/** Pehle 6 design ke baad ke M3 dynamic design ki shuruaat. */
internal const val DYNAMIC_STYLE_FIRST = 6

/** Bubble ke "Ring" ka apna paint (shellStyle ke andar). */
private const val PAINT_RING = 20

/**
 * M3 dynamic paint (style 6..11). Pill, banner, bubble aur message card sab yahi use karte hain.
 * Rang ShellLook se (MaterialTheme.colorScheme): overlay me IslandTheme dynamic (wallpaper) scheme deti hai,
 * app ke andar app ki theme.
 */
internal fun Modifier.dynamicPaint(style: Int, alpha: Float, shape: Shape): Modifier = composed {
    val look = shellLookOf(style, MaterialTheme.colorScheme)
    when (style) {
        // 6 Tonal: primaryContainer ka saaf rang + patli primary border
        6 -> Modifier
            .background(look.base.copy(alpha = alpha))
            .border(1.dp, look.accent.copy(alpha = 0.22f), shape)
        // 7 Surface: surfaceContainerHigh + outlineVariant border
        7 -> Modifier
            .background(look.base.copy(alpha = alpha))
            .border(1.dp, look.outline.copy(alpha = 0.70f), shape)
        // 8 Aurora: primary -> tertiary -> secondary container gradient + upar ki halki roshni
        8 -> Modifier
            .background(
                Brush.linearGradient(
                    listOf(
                        look.base.copy(alpha = alpha),
                        look.base2.copy(alpha = alpha),
                        look.base3.copy(alpha = alpha),
                    )
                )
            )
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(look.on.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(size.width * 0.15f, 0f),
                        radius = size.width * 0.70f,
                    )
                )
            }
        // 9 Duo: secondaryContainer | tertiaryContainer, tirchhi seam (pill, banner, bubble, card sab me ek jaisa)
        9 -> Modifier
            .background(
                Brush.linearGradient(
                    0f to look.base.copy(alpha = alpha),
                    0.47f to look.base.copy(alpha = alpha),
                    0.53f to look.base2.copy(alpha = alpha),
                    1f to look.base2.copy(alpha = alpha),
                )
            )
        // 10 Outline: gehra surface, halka primary tint, primary -> tertiary patli outline
        10 -> Modifier
            .background(look.base.copy(alpha = alpha))
            .background(look.accent.copy(alpha = 0.07f))
            .border(
                1.5.dp,
                Brush.linearGradient(listOf(look.accent.copy(alpha = 0.90f), look.outline.copy(alpha = 0.70f))),
                shape,
            )
        // 11 Spotlight: kone se primary ki roshni, doosre kone se tertiary ki halki
        11 -> Modifier
            .background(look.base.copy(alpha = alpha))
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(look.accent.copy(alpha = 0.50f), Color.Transparent),
                        center = Offset(size.width * 0.12f, 0f),
                        radius = size.width * 0.85f,
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(look.base2.copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(size.width * 0.95f, size.height),
                        radius = size.width * 0.55f,
                    )
                )
            }
            .border(1.dp, look.accent.copy(alpha = 0.18f), shape)
        else -> Modifier.background(Color.Black.copy(alpha = alpha))
    }
}

/** Upar se halki safed chamak (glass / sheen). */
private fun Modifier.topSheen(strength: Float, fraction: Float = 0.55f): Modifier = drawBehind {
    drawRect(
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = strength), Color.Transparent),
            endY = size.height * fraction,
        )
    )
}

/** Pill / banner / bubble ka rang-roop. Shape bahar se clip ho chuka hota hai, [shape] sirf border ke liye. */
internal fun Modifier.shellStyle(style: Int, accent: Color, alpha: Float, shape: Shape): Modifier = when (style) {
    // Classic: kaala (camera ke saath ghul jata hai) + bahut halki chamak + patli rim
    0 -> this
        .background(Color.Black.copy(alpha = alpha))
        .topSheen(0.035f)
        .border(0.7.dp, Color.White.copy(alpha = 0.07f), shape)
    // Glass: gehra shisha, upar chamak, tirchhi glint, gradient border
    1 -> this
        .background(
            Brush.verticalGradient(
                listOf(
                    Color(0xFF3A3A42).copy(alpha = alpha * 0.80f),
                    Color(0xFF0C0C0E).copy(alpha = alpha * 0.86f),
                )
            )
        )
        .topSheen(0.20f, 0.60f)
        .drawBehind {
            drawRect(
                Brush.linearGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.07f), Color.Transparent),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
            )
        }
        .border(
            1.dp,
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.42f),
                    Color.White.copy(alpha = 0.06f),
                    Color.White.copy(alpha = 0.20f),
                )
            ),
            shape,
        )
    // Gradient: kaala -> accent, 3 stop, upar halki chamak
    2 -> this
        .background(
            Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = alpha),
                0.45f to lerp(Color.Black, accent, 0.30f).copy(alpha = alpha),
                1f to lerp(Color.Black, accent, 0.62f).copy(alpha = alpha),
            )
        )
        .topSheen(0.07f)
    // Neon: andar ki glow (3 patli parat) + tez patli border
    3 -> this
        .background(Color(0xFF050507).copy(alpha = alpha))
        .drawBehind {
            drawRect(Brush.verticalGradient(listOf(accent.copy(alpha = 0.20f), Color.Transparent)))
        }
        .border(5.dp, accent.copy(alpha = 0.07f), shape)
        .border(3.dp, accent.copy(alpha = 0.14f), shape)
        .border(1.5.dp, accent.copy(alpha = 0.95f), shape)
    // Tinted: accent ka gehra rang, upar se neeche halka, accent border
    4 -> this
        .background(
            Brush.verticalGradient(
                listOf(
                    lerp(Color.Black, accent, 0.40f).copy(alpha = alpha),
                    lerp(Color.Black, accent, 0.22f).copy(alpha = alpha),
                )
            )
        )
        .border(1.dp, accent.copy(alpha = 0.38f), shape)
    // Graphite: grey dhatu jaisa, gehrai wala gradient + upar ki dhar
    5 -> this
        .background(
            Brush.verticalGradient(
                listOf(
                    Color(0xFF2A2A2D).copy(alpha = alpha),
                    Color(0xFF161618).copy(alpha = alpha),
                )
            )
        )
        .border(
            1.dp,
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.05f))),
            shape,
        )
    // Bubble ring: kaala andar, mota accent ring + andar halki glow
    PAINT_RING -> this
        .background(Color(0xFF050507).copy(alpha = alpha))
        .border(4.dp, accent.copy(alpha = 0.10f), shape)
        .border(2.dp, accent.copy(alpha = 0.95f), shape)
    in DYNAMIC_STYLE_FIRST until STYLE_COUNT -> this.dynamicPaint(style, alpha, shape)   // M3 DYNAMIC
    else -> this.background(Color.Black.copy(alpha = alpha))
}

/** Bubble ka shape: Squircle (1), Surface (7), Spotlight (11) squircle; baaki hamesha gol. */
internal fun bubbleShape(style: Int): Shape =
    if (style == 1 || style == 7 || style == 11) RoundedCornerShape(32) else CircleShape

/** Bubble style -> shellStyle paint (Circle, Squircle = Classic; Ring = ring; Glass; Tinted; Gradient; 6..11 = M3 dynamic, seedhe wahi paint). */
internal fun bubblePaint(style: Int): Int = when (style) {
    2 -> PAINT_RING
    3 -> 1
    4 -> 4
    5 -> 2
    in DYNAMIC_STYLE_FIRST until STYLE_COUNT -> style
    else -> 0
}

/** Message card ke title ka size (sp, text scale se pehle). */
internal fun cardTitleSp(style: Int): Float = when (style) {
    3 -> 15f
    5 -> 19f
    else -> 17f
}

/** Message card ka background. [glow] sirf Classic ka neeche wala accent gradient hai (Customize > Accent). */
internal fun Modifier.cardStyleBg(style: Int, accent: Color, alpha: Float, glow: Float, shape: Shape): Modifier = when (style) {
    // Glass: shisha + badi chamak + gradient border
    1 -> this
        .background(
            Brush.verticalGradient(
                listOf(
                    Color(0xFF3A3A42).copy(alpha = alpha * 0.85f),
                    Color(0xFF111114).copy(alpha = alpha * 0.85f),
                )
            )
        )
        .topSheen(0.16f, 0.50f)
        .drawBehind {
            drawRect(
                Brush.linearGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.06f), Color.Transparent),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
            )
        }
        .border(
            1.dp,
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.40f),
                    Color.White.copy(alpha = 0.06f),
                    Color.White.copy(alpha = 0.22f),
                )
            ),
            shape,
        )
    // Material: accent se tonal surface + kone ki roshni + patli accent border
    2 -> this
        .background(lerp(Color(0xFF1B1A22), accent, 0.26f).copy(alpha = alpha))
        .drawBehind {
            drawRect(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(size.width * 0.10f, 0f),
                    radius = size.width * 0.80f,
                )
            )
        }
        .border(1.dp, accent.copy(alpha = 0.28f), shape)
    // Minimal: saaf gehra surface + gol sire wali accent patti (andar ki taraf) + patli rim
    3 -> this
        .background(Color(0xFF121216).copy(alpha = alpha))
        .drawBehind {
            val barW = 4.dp.toPx()
            val padV = 14.dp.toPx()
            drawRoundRect(
                accent,
                Offset(10.dp.toPx(), padV),
                Size(barW, (size.height - padV * 2f).coerceAtLeast(barW)),
                CornerRadius(barW / 2f),
            )
        }
        .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
    // Neon: andar ki glow + tez patli border
    4 -> this
        .background(Color(0xFF050508).copy(alpha = alpha))
        .drawBehind {
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.16f))))
        }
        .border(5.dp, accent.copy(alpha = 0.07f), shape)
        .border(3.dp, accent.copy(alpha = 0.14f), shape)
        .border(1.5.dp, accent.copy(alpha = 0.95f), shape)
    // Bold: gehra gradient + upar-baayen ki roshni
    5 -> this
        .background(
            Brush.linearGradient(
                listOf(
                    lerp(accent, Color.Black, 0.25f).copy(alpha = alpha),
                    lerp(accent, Color(0xFF1A0838), 0.6f).copy(alpha = alpha),
                )
            )
        )
        .drawBehind {
            drawRect(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(size.width * 0.05f, 0f),
                    radius = size.width * 0.75f,
                )
            )
        }
        .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
    in DYNAMIC_STYLE_FIRST until STYLE_COUNT -> this.dynamicPaint(style, alpha, shape)   // M3 DYNAMIC
    // Classic: kaala, upar halki gehrai, neeche accent glow, patli rim
    else -> this
        .background(
            Brush.verticalGradient(
                listOf(
                    Color(0xFF131316).copy(alpha = alpha),
                    Color.Black.copy(alpha = alpha),
                )
            )
        )
        .drawBehind {
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.35f to Color.Transparent,
                    1f to accent.copy(alpha = glow),
                )
            )
        }
        .border(1.dp, Color.White.copy(alpha = 0.07f), shape)
}
