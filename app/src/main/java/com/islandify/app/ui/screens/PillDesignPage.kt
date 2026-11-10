package com.islandify.app.ui.screens

// STYLEKIT2: Pill > Design page (live preview + design + opacity + accent glow).

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VerticalAlignTop   // NOTCHMODE
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.islandify.app.R
import com.islandify.app.core.IslandSettings
import com.islandify.app.ui.components.PreviewCard
import com.islandify.app.ui.components.SectionCard

@Composable
internal fun DesignPage(showPreview: Boolean = true) {   // STYLEKIT3
    val scale by IslandSettings.scale.collectAsState()
    val w by IslandSettings.widthScale.collectAsState()
    val h by IslandSettings.heightScale.collectAsState()

    if (showPreview) PreviewCard(scale, w, h)   // STYLEKIT3

    SectionCard(title = stringResource(R.string.cust_page_design), icon = Icons.Rounded.AutoAwesome) {
        StylePickerRow(R.string.cust_style_pill, IslandSettings.pillStyle, SHELL_STYLE_LABELS, 0)
        FlagRow(Icons.Rounded.VerticalAlignTop, R.string.cust_notch, IslandSettings.notchMode, R.string.cust_notch_sub)   // NOTCHMODE
        FloatSetting(R.string.cust_pill_alpha, Icons.Rounded.Visibility, IslandSettings.pillAlpha, 0.5f..1f, null, ::pct)
        FloatSetting(R.string.cust_pill_accent, Icons.Rounded.ColorLens, IslandSettings.pillAccent, 0f..1.5f, 1f, ::pct)
    }
}
