package com.islandify.app.core
import com.islandify.app.R
import androidx.annotation.StringRes

import com.islandify.app.island.*
import com.islandify.app.ui.components.*
import com.islandify.app.ui.onboarding.*
import com.islandify.app.ui.screens.*
import com.islandify.app.ui.theme.*

import android.content.Context
import android.content.SharedPreferences

// SPLITSET  Banner page + per-activity (kind) styles.
// Flows IslandSettings object me hi hain (poore app me IslandSettings.x se chalte hain). Yahan sirf load / save.

internal fun IslandSettings.loadBanner(p: SharedPreferences) {
    kindStyles.value = IslandKind.entries.associate { t ->
        val k = "bn_${t.key}"
        t.key to KindStyle(
            w = p.getFloat("${k}_w", 1f),
            h = p.getFloat("${k}_h", 1f),
            text = p.getFloat("${k}_t", 1f),
            corner = p.getFloat("${k}_c", -1f),
            durationSec = p.getInt("${k}_d", 0),
            cardW = p.getFloat("${k}_cw", 1f),
            cardH = p.getFloat("${k}_ch", 1f),
            y = p.getFloat("${k}_y", 0f),   // KINDY
        )
    }
    bannerW.value = p.getFloat("bnW", 1f)
    bannerH.value = p.getFloat("bnH", 1f)
    bannerCorner.value = p.getFloat("bnCorner", 0f)
    bannerText.value = p.getFloat("bnText", 1f)
    bannerAlpha.value = p.getFloat("bnAlpha", 1f)
    bannerAccent.value = p.getFloat("bnAccent", 1f)
    bannerY.value = p.getFloat("bnY", 0f)   // BANNERY
    bannerStyle.value = p.getInt("stBanner", 0).coerceIn(0, 11)   // STYLEKIT
    privacyBanners.value = p.getBoolean("privBan", true)   // EXTRAKIT
    bannerSub.value = p.getBoolean("bnSub", true)
    bannerBadge.value = p.getBoolean("bnBadge", true)
}

internal fun IslandSettings.saveBanner(e: SharedPreferences.Editor) {
    e.putFloat("bnW", bannerW.value)
    e.putFloat("bnH", bannerH.value)
    e.putFloat("bnCorner", bannerCorner.value)
    e.putFloat("bnText", bannerText.value)
    e.putFloat("bnAlpha", bannerAlpha.value)
    e.putFloat("bnAccent", bannerAccent.value)
    e.putFloat("bnY", bannerY.value)   // BANNERY
    e.putInt("stBanner", bannerStyle.value)   // STYLEKIT
    e.putBoolean("privBan", privacyBanners.value)   // EXTRAKIT
    e.putBoolean("bnSub", bannerSub.value)
    e.putBoolean("bnBadge", bannerBadge.value)
    // Banner styles (one set of keys per banner type)
    IslandKind.entries.forEach { t ->
        val b = kindStyle(t)
        val k = "bn_${t.key}"
        e.putFloat("${k}_w", b.w).putFloat("${k}_h", b.h).putFloat("${k}_t", b.text)
            .putFloat("${k}_c", b.corner).putInt("${k}_d", b.durationSec)
            .putFloat("${k}_cw", b.cardW).putFloat("${k}_ch", b.cardH)
            .putFloat("${k}_y", b.y)   // KINDY
    }
}
