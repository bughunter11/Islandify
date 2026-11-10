package com.islandify.app.island

// M3DESIGN: pill / banner / bubble / message card ke 6..11 (Material 3 dynamic) designs ka "look".
//
// Pehle in 6 designs me sirf background ka rang badalta tha. Ab har design ke saare hisse
// (text, chhota text, icon ka tile, button, reply box, count badge, bubbles ki patti, swipe hint)
// MaterialTheme.colorScheme ke M3 roles se bante hain. Overlay me IslandTheme wallpaper (Material You)
// scheme deti hai, isliye wallpaper badalte hi poora design apne aap naye rang me aa jata hai.
//
//   6 Tonal      primaryContainer + onPrimaryContainer, filled (primary) button
//   7 Surface    surfaceContainerHigh + outlineVariant border, tonal (secondaryContainer) button
//   8 Aurora     primary / tertiary / secondary container ka gradient, glass jaisa button
//   9 Duo        secondary | tertiary container ka do-rang (diagonal seam), filled button
//   10 Outline   surface + primary -> tertiary outline, outlined button
//   11 Spotlight surfaceContainerLowest + primary / tertiary ki roshni, filled button
//
// 0..5 (Classic, Glass ...) ka look = ShellLook.Default, yani pehle jaisa safed text / safed button.

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Design ke saare rang ek jagah. `dynamic = false` ka matlab purane (0..5) design: kuch bhi nahi badalta. */
@Immutable
internal data class ShellLook(
    val dynamic: Boolean,
    val base: Color = Color.Black,                 // main container
    val base2: Color = Color.Black,                // gradient / do-rang ka doosra rang
    val base3: Color = Color.Black,                // Aurora ka teesra rang
    val outline: Color = Color.Transparent,        // border / doosra accent
    val on: Color = Color.White,                   // title, body, icon
    val muted: Color = Muted,                      // chhota text (app ka naam, subtitle)
    val accent: Color = Color.Unspecified,         // progress, badge, send button (Unspecified = caller ka apna rang)
    val onAccent: Color = Color.White,
    val tile: Color = Color.White.copy(alpha = 0.12f),   // icon ke peeche ka tonal tile
    val btn: Color = Color.White.copy(alpha = 0.22f),    // card ke buttons
    val onBtn: Color = Color.White,
    val btnBorder: Color = Color.Unspecified,      // Unspecified = border nahi (Outline me hota hai)
    val field: Color = Color(0xFF1C1C1E),          // reply box
    val rail: Color = Color.Black,                 // card ke neeche bubbles ki patti
    val ring: Color = Color(0xFFB9C8FF),           // patti me chuna hua bubble
    val badge: Color = Color(0xFFFF3B30),          // count badge, "Dismiss" hint
    val onBadge: Color = Color.White,
    val hint: Color = Color.White.copy(alpha = 0.22f),   // "Hide" swipe hint
) {
    /** Label ka purana safed / grey rang dynamic design me M3 role ban jata hai; baaki rang jaise hain waise. */
    fun map(c: Color): Color = if (!dynamic) c else when (c) {
        Color.White -> on
        Muted -> muted
        else -> c
    }

    companion object {
        val Default = ShellLook(dynamic = false)
    }
}

/** Pill / banner / card / reply bar apna look yahin se padhte hain. Default = purane designs. */
internal val LocalShellLook = compositionLocalOf { ShellLook.Default }

internal val StyleInk = Color(0xFF0B0B0D)

/** [style] (0..11) ka look. 0..5 ke liye ShellLook.Default. Composable me [rememberShellLook] use karo. */
internal fun shellLookOf(style: Int, cs: ColorScheme): ShellLook {
    if (style < DYNAMIC_STYLE_FIRST || style >= STYLE_COUNT) return ShellLook.Default

    // Overlay hamesha dark scheme hai: M3 roles seedhe use hote hain.
    // Light scheme sirf app ke andar (preview / swatch) aati hai: container gehre kiye, text halka, taaki safed icon padhe jaye.
    val light = cs.background.luminance() > 0.5f
    fun box(c: Color) = if (light) lerp(StyleInk, c, 0.40f) else c
    fun tone(c: Color) = if (light) lerp(Color.White, c, 0.14f) else c

    val accent = if (light) cs.inversePrimary else cs.primary
    val onAccent = if (light) StyleInk else cs.onPrimary
    val tertiary = if (light) lerp(cs.tertiary, Color.White, 0.40f) else cs.tertiary
    val onTertiary = if (light) StyleInk else cs.onTertiary

    fun mk(
        base: Color,
        on: Color,
        accent: Color,
        onAccent: Color,
        btn: Color,
        onBtn: Color,
        base2: Color = base,
        base3: Color = base,
        outline: Color = accent,
        btnBorder: Color = Color.Unspecified,
        field: Color = on.copy(alpha = 0.12f),
        muted: Color = on.copy(alpha = 0.74f),
        tile: Color = on.copy(alpha = 0.14f),
    ) = ShellLook(
        dynamic = true,
        base = base, base2 = base2, base3 = base3, outline = outline,
        on = on, muted = muted,
        accent = accent, onAccent = onAccent,
        tile = tile, btn = btn, onBtn = onBtn, btnBorder = btnBorder,
        field = field,
        rail = lerp(base, StyleInk, 0.35f),
        ring = accent,
        badge = cs.error, onBadge = cs.onError,   // M3 badge = error role
        hint = on.copy(alpha = 0.20f),
    )

    return when (style) {
        // 6 Tonal: primaryContainer, filled primary button
        6 -> mk(
            base = box(cs.primaryContainer),
            on = tone(cs.onPrimaryContainer),
            accent = accent, onAccent = onAccent,
            btn = accent, onBtn = onAccent,
        )
        // 7 Surface: surfaceContainerHigh + outlineVariant, tonal secondaryContainer button
        7 -> mk(
            base = box(cs.surfaceContainerHigh),
            on = tone(cs.onSurface),
            muted = tone(cs.onSurfaceVariant),
            accent = accent, onAccent = onAccent,
            outline = cs.outlineVariant,
            tile = box(cs.secondaryContainer),
            btn = box(cs.secondaryContainer), onBtn = tone(cs.onSecondaryContainer),
            field = box(cs.surfaceContainerHighest),
        )
        // 8 Aurora: primary -> tertiary -> secondary container gradient, glass button
        8 -> {
            val on = tone(lerp(cs.onPrimaryContainer, cs.onTertiaryContainer, 0.5f))
            mk(
                base = box(cs.primaryContainer),
                base2 = box(cs.tertiaryContainer),
                base3 = box(cs.secondaryContainer),
                on = on,
                accent = tertiary, onAccent = onTertiary,
                btn = on.copy(alpha = 0.20f), onBtn = on,
                field = Color.Black.copy(alpha = 0.22f),
            )
        }
        // 9 Duo: secondary | tertiary container, beech me tirchhi seam
        9 -> {
            val on = tone(lerp(cs.onSecondaryContainer, cs.onTertiaryContainer, 0.5f))
            mk(
                base = box(cs.secondaryContainer),
                base2 = box(cs.tertiaryContainer),
                on = on,
                accent = accent, onAccent = onAccent,
                btn = accent, onBtn = onAccent,
                field = Color.Black.copy(alpha = 0.20f),
            )
        }
        // 10 Outline: surface + primary -> tertiary outline, outlined button
        10 -> mk(
            base = box(cs.surface),
            on = tone(cs.onSurface),
            muted = tone(cs.onSurfaceVariant),
            accent = accent, onAccent = onAccent,
            outline = tertiary,
            tile = accent.copy(alpha = 0.16f),
            btn = Color.Transparent, onBtn = accent, btnBorder = accent.copy(alpha = 0.70f),
            field = box(cs.surfaceContainerHigh),
        )
        // 11 Spotlight: gehra surface + primary / tertiary ki roshni
        else -> mk(
            base = box(cs.surfaceContainerLowest),
            base2 = tertiary,
            on = tone(cs.onSurface),
            muted = tone(cs.onSurfaceVariant),
            accent = accent, onAccent = onAccent,
            btn = accent, onBtn = onAccent,
            field = Color.Black.copy(alpha = 0.28f),
        )
    }
}

/** Composable me look. Scheme ke rang yahin padhe jate hain, isliye wallpaper badalne par apne aap dobara banta hai. */
@Composable
internal fun rememberShellLook(style: Int): ShellLook = shellLookOf(style, MaterialTheme.colorScheme)
