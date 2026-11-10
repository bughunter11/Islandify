package com.islandify.app.ui.screens

// PILLPAGE + ADVANCEDKIT  (Pill page = Placement / Visuals / Duration / Behavior ka hub; Behavior ka Advanced section.)

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.islandify.app.R
import com.islandify.app.core.IslandSettings
import com.islandify.app.ui.components.SectionCard
import com.islandify.app.ui.components.reveal

/** Customize main page par sirf ye 4 group dikhte hain. */
internal val hubPages: List<CustPage> = listOf(CustPage.Pill, CustPage.Bubble, CustPage.Card, CustPage.Music, CustPage.Banner)   // MUSICKIT

/** Pill ke andar ke 4 pages ka parent Pill hai (back dabane par Pill page par wapas). */
internal val CustPage.parent: CustPage?
    get() = when (this) {
        CustPage.Placement, CustPage.Design, CustPage.Visuals, CustPage.Duration, CustPage.Behavior -> CustPage.Pill   // STYLEKIT2
        else -> null
    }

/** Page ki gehraai: 0 = hub, 1 = Pill / Bubble / Card / Banner, 2 = Pill ke andar ke pages. Slide ki disha ke liye. */
internal fun custDepth(name: String?): Int = when {
    name == null -> 0
    CustPage.valueOf(name).parent == null -> 1
    else -> 2
}

/** Pill page: pill se jude 4 pages ki list (pehle ye Customize ke main page par alag alag the). */
@Composable
internal fun PillPage(onOpen: (CustPage) -> Unit) {
    PillFlatPage()   // STYLEKIT3: ek hi page, sab settings ek jagah
}

/** Advanced section ki settings (search inhe dekh ke section khud khol deta hai). */
private val ADV_TITLES: Set<Int> = setOf(
    R.string.cust_launcher_only,
    R.string.cust_hide_owner,
    R.string.cust_hide_landscape,
    R.string.cust_outside,
    R.string.cust_flash_pill,
    R.string.cust_media_keep_paused,
    R.string.cust_media_apple,
)   // MOVEKIT: privacy banners / direct card / bubble extras ab Banner, Card, Bubble page me

/** Behavior page ka collapsible "Advanced": kam-use options + activity priority. Band rehta hai. */
@Composable
internal fun AdvancedBehavior() {
    var open by rememberSaveable { mutableStateOf(false) }
    val target by CustSearch.target.collectAsState()
    LaunchedEffect(target) {
        val t = target
        if (t != null && t in ADV_TITLES) open = true
    }
    val rot by animateFloatAsState(
        if (open) 180f else 0f,
        spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "advArrow"
    )

    Surface(
        onClick = { open = !open },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().reveal()
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Settings, null, Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.cust_adv_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.cust_adv_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.graphicsLayer { rotationZ = rot })
        }
    }

    if (open) {
        SectionCard(title = stringResource(R.string.cust_adv_visibility), icon = Icons.Rounded.Visibility) {
            FlagRow(Icons.Rounded.PhoneAndroid, R.string.cust_launcher_only, IslandSettings.launcherOnly, R.string.cust_launcher_only_sub)
            FlagRow(Icons.Rounded.Visibility, R.string.cust_hide_owner, IslandSettings.hideInOwnerApp, R.string.cust_hide_owner_sub)
            FlagRow(Icons.Rounded.AspectRatio, R.string.cust_hide_landscape, IslandSettings.hideLandscape, R.string.cust_hide_landscape_sub)
            FlagRow(Icons.Rounded.TouchApp, R.string.cust_outside, IslandSettings.dismissOnOutside, R.string.cust_outside_sub)
        }
        SectionCard(title = stringResource(R.string.cust_adv_notif), icon = Icons.Rounded.MusicNote) {
            FlagRow(Icons.Rounded.PlayCircle, R.string.cust_media_keep_paused, IslandSettings.mediaKeepPaused, R.string.cust_media_keep_paused_sub)
            FlagRow(Icons.Rounded.MusicNote, R.string.cust_media_apple, IslandSettings.appleMusic, R.string.cust_media_apple_sub)   // APPLEMUSIC
        }
        SectionCard(title = stringResource(R.string.cust_adv_island), icon = Icons.Rounded.Widgets) {
            FlagRow(Icons.Rounded.FlashlightOn, R.string.cust_flash_pill, IslandSettings.flashlightPill, R.string.cust_flash_pill_sub)
        }
        PrioritySection()
    }
}
