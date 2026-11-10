package com.islandify.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import com.islandify.app.core.IslandSettings

/**
 * Finds the top camera cut-out and moves the island onto it (Left/Right + Up/Down offsets).
 * Returns false when the phone has no top cut-out or Android does not report one.
 */
internal fun autoAlignToCamera(ctx: Context): Boolean {
    var c: Context? = ctx
    while (c is ContextWrapper && c !is Activity) c = c.baseContext
    val act = c as? Activity ?: return false
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
    val cutout = act.window.decorView.rootWindowInsets?.displayCutout ?: return false
    val rect = cutout.boundingRects.firstOrNull { it.top <= 2 && it.width() > 0 } ?: return false

    val dm = act.resources.displayMetrics
    val d = dm.density
    val islandH = 38f * IslandSettings.scale.value *
        IslandSettings.heightScale.value.coerceIn(0.8f, 1.2f)   // dp of the small pill

    IslandSettings.offsetX.value = ((rect.exactCenterX() - dm.widthPixels / 2f) / d).coerceIn(-180f, 180f)
    IslandSettings.offsetY.value = (rect.exactCenterY() / d - islandH / 2f).coerceIn(0f, 300f)
    IslandSettings.save()
    return true
}

