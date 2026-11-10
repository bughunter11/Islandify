package com.islandify.app.ui.theme

// FONTNEON: "Neon Wide" = titles Unbounded, baaki text Sora (dono variable fonts, res/font/)

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.islandify.app.R

@OptIn(ExperimentalTextApi::class)
private fun vf(res: Int, w: Int, fw: FontWeight): Font =
    Font(res, fw, variationSettings = FontVariation.Settings(FontVariation.weight(w)))

internal object AppFonts {
    /** Titles: chauda, rounded (Unbounded). */
    val heading: FontFamily = FontFamily(
        vf(R.font.unbounded, 400, FontWeight.Normal),
        vf(R.font.unbounded, 500, FontWeight.Medium),
        vf(R.font.unbounded, 600, FontWeight.SemiBold),
        vf(R.font.unbounded, 700, FontWeight.Bold),
    )

    /** Baaki text: saaf, modern (Sora). */
    val body: FontFamily = FontFamily(
        vf(R.font.sora, 400, FontWeight.Normal),
        vf(R.font.sora, 500, FontWeight.Medium),
        vf(R.font.sora, 600, FontWeight.SemiBold),
        vf(R.font.sora, 700, FontWeight.Bold),
    )
}

/** Material 3 Typography: display / headline / titleLarge-Medium = Unbounded, baaki = Sora. */
internal fun appTypography(): Typography {
    val d = Typography()
    val h = AppFonts.heading
    val b = AppFonts.body
    return d.copy(
        displayLarge = d.displayLarge.copy(fontFamily = h, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        displayMedium = d.displayMedium.copy(fontFamily = h, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        displaySmall = d.displaySmall.copy(fontFamily = h, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        headlineLarge = d.headlineLarge.copy(fontFamily = h, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        headlineMedium = d.headlineMedium.copy(fontFamily = h, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        headlineSmall = d.headlineSmall.copy(fontFamily = h, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleLarge = d.titleLarge.copy(fontFamily = h, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-0.2).sp),
        titleMedium = d.titleMedium.copy(fontFamily = h, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleSmall = d.titleSmall.copy(fontFamily = b, fontWeight = FontWeight.SemiBold),
        bodyLarge = d.bodyLarge.copy(fontFamily = b),
        bodyMedium = d.bodyMedium.copy(fontFamily = b),
        bodySmall = d.bodySmall.copy(fontFamily = b),
        labelLarge = d.labelLarge.copy(fontFamily = b),
        labelMedium = d.labelMedium.copy(fontFamily = b),
        labelSmall = d.labelSmall.copy(fontFamily = b),
    )
}
