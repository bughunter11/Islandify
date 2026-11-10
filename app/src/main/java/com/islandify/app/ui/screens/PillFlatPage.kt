package com.islandify.app.ui.screens

// STYLEKIT3: Pill ka apna ek hi page (Bubble / Card / Banner jaisa).

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.islandify.app.core.IslandSettings
import com.islandify.app.ui.components.PreviewCard

@Composable
internal fun PillFlatPage() {
    val scale by IslandSettings.scale.collectAsState()
    val w by IslandSettings.widthScale.collectAsState()
    val h by IslandSettings.heightScale.collectAsState()

    PreviewCard(scale, w, h)
    DesignPage(showPreview = false)
    PlacementPage(showPreview = false)
    VisualsPage(showPreview = false)
    DurationPage()
    BehaviorPage()
}
