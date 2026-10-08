package com.islandify.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.islandify.app.R
import com.islandify.app.core.*
import com.islandify.app.island.*
import com.islandify.app.ui.components.*

/* Customize > Pop-ups & activities
 * One card with every pop-up and live activity of the island (same list as the test buttons on Home).
 * Tap one and a sheet opens with its own live preview and settings.
 */

private fun IslandKind.icon(): ImageVector = when (this) {
    IslandKind.Notification -> Icons.Rounded.Notifications
    IslandKind.Charging -> Icons.Rounded.Bolt
    IslandKind.Unplugged -> Icons.Rounded.BatteryStd
    IslandKind.LowBattery -> Icons.Rounded.BatteryAlert
    IslandKind.Device -> Icons.Rounded.Headphones
    IslandKind.Silent -> Icons.AutoMirrored.Rounded.VolumeOff
    IslandKind.Vibrate -> Icons.Rounded.Vibration
    IslandKind.Dnd -> Icons.Rounded.DoNotDisturbOn
    IslandKind.Flashlight -> Icons.Rounded.FlashOn
    IslandKind.Call -> Icons.Rounded.Call
    IslandKind.Media -> Icons.Rounded.MusicNote
    IslandKind.Timer -> Icons.Rounded.Timer
    IslandKind.Navigation -> Icons.Rounded.Navigation
    IslandKind.Tracking -> Icons.Rounded.DeliveryDining
    IslandKind.Download -> Icons.Rounded.Download
}

/** Sample content for the live preview inside the sheet. */
private fun IslandKind.demo(): IslandMode = when (this) {
    IslandKind.Notification -> IslandMode.Notification("WhatsApp", "Rahul", "Is tomorrow's plan final?")
    IslandKind.Charging -> IslandMode.Charging(82, true)
    IslandKind.Unplugged -> IslandMode.Charging(82, false)
    IslandKind.LowBattery -> IslandMode.LowBattery(15)
    IslandKind.Device -> IslandMode.Device("AirPods Pro", true)
    IslandKind.Silent -> IslandMode.Toggle(ToggleKind.Silent, true)
    IslandKind.Vibrate -> IslandMode.Toggle(ToggleKind.Vibrate, true)
    IslandKind.Dnd -> IslandMode.Toggle(ToggleKind.Dnd, true)
    IslandKind.Flashlight -> IslandMode.Toggle(ToggleKind.Flashlight, true)
    IslandKind.Call -> IslandMode.Call("Mummy", true, 0L)
    IslandKind.Media -> IslandMode.Media("Blinding Lights", "The Weeknd", true)
    IslandKind.Timer -> IslandMode.Timer(42, 60)
    IslandKind.Navigation -> IslandMode.Live(LiveKind.Navigation, "Maps", "Turn left onto MG Road", "12 min, 4.2 km")
    IslandKind.Tracking -> IslandMode.Live(LiveKind.Tracking, "Zomato", "Order on the way", "Arriving in 12 min")
    IslandKind.Download -> IslandMode.Live(LiveKind.Download, "Chrome", "movie.mp4", "45 MB of 120 MB", 38)
}

/** The list on screen: (group title, kinds). */
private val GROUPS: List<Pair<Int, List<IslandKind>>> = listOf(
    R.string.test_group_basic to listOf(
        IslandKind.Notification, IslandKind.Charging, IslandKind.Unplugged, IslandKind.LowBattery,
        IslandKind.Device, IslandKind.Silent, IslandKind.Vibrate, IslandKind.Dnd, IslandKind.Flashlight,
    ),
    R.string.kind_group_island to listOf(
        IslandKind.Call, IslandKind.Media, IslandKind.Timer,
    ),
    R.string.test_group_live to listOf(
        IslandKind.Navigation, IslandKind.Tracking, IslandKind.Download,
    ),
)

@Composable
fun KindCustomizeCard() {
    val styles by IslandSettings.kindStyles.collectAsState()
    var open by remember { mutableStateOf<IslandKind?>(null) }
    var openAll by remember { mutableStateOf(false) }

    SectionCard(title = stringResource(R.string.cust_banner), icon = Icons.Rounded.Notifications) {
        Text(
            stringResource(R.string.cust_banner_sub),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PickRow(
            icon = Icons.Rounded.Tune,
            title = stringResource(R.string.banner_all),
            summary = stringResource(R.string.banner_all_sub),
        ) { openAll = true }

        GROUPS.forEach { (groupTitle, kinds) ->
            Text(
                stringResource(groupTitle),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp)
            )
            kinds.forEach { t ->
                val s = styles[t.key] ?: KindStyle()
                PickRow(
                    icon = t.icon(),
                    title = stringResource(t.label),
                    summary = if (s == KindStyle()) stringResource(R.string.preset_normal)
                    else "${(s.w * 100).toInt()}% × ${(s.h * 100).toInt()}%  ·  ${(s.text * 100).toInt()}%",
                ) { open = t }
            }
        }
    }

    open?.let { t ->
        KindEditorSheet(
            title = stringResource(t.label), icon = t.icon(),
            kinds = listOf(t), previewKind = t, onClose = { open = null }
        )
    }
    if (openAll) {
        KindEditorSheet(
            title = stringResource(R.string.banner_all), icon = Icons.Rounded.Tune,
            kinds = IslandKind.entries, previewKind = IslandKind.Notification, onClose = { openAll = false }
        )
    }
}

@Composable
private fun PickRow(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Rounded.ChevronRight, null, Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SubTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

/** [kinds] = what this sheet changes (one, or all). [previewKind] = what the preview shows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KindEditorSheet(
    title: String,
    icon: ImageVector,
    kinds: List<IslandKind>,
    previewKind: IslandKind,
    onClose: () -> Unit,
) {
    val styles by IslandSettings.kindStyles.collectAsState()
    val s = styles[previewKind.key] ?: KindStyle()
    val globalCorner by IslandSettings.corner.collectAsState()
    val scale by IslandSettings.scale.collectAsState()
    val speed by IslandSettings.animSpeed.collectAsState()
    val glow by IslandSettings.glow.collectAsState()

    val popup = previewKind.popup
    // Preview shows either the small part (banner / pill) or the card that opens on tap
    var showCard by remember { mutableStateOf(false) }
    val smallLevel = if (popup) IslandLevel.Banner else IslandLevel.Compact

    fun update(n: KindStyle) = kinds.forEach { IslandSettings.setKindStyle(it, n) }
    fun commit() = IslandSettings.save()
    fun preset(w: Float, h: Float, t: Float) {
        update(s.copy(w = w, h = h, text = t))
        commit()
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }

            // Live preview of exactly this kind
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (showCard) 300.dp else 170.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), Color(0xFF0B0B0C))
                        )
                    ),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(Modifier.padding(top = 14.dp)) {
                    DynamicIslandUi(
                        mode = previewKind.demo(),
                        level = if (showCard) IslandLevel.Expanded else smallLevel,
                        scale = if (showCard) 0.85f else scale.coerceAtMost(1.0f),
                        cornerFactor = globalCorner,
                        animSpeed = speed,
                        glow = glow,
                        haptics = false,
                        onClick = {},
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !showCard,
                    onClick = { showCard = false },
                    label = { Text(stringResource(if (popup) R.string.preview_banner else R.string.preview_pill)) }
                )
                FilterChip(
                    selected = showCard,
                    onClick = { showCard = true },
                    label = { Text(stringResource(R.string.preview_card)) }
                )
            }

            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BouncyChip(stringResource(R.string.preset_normal)) { preset(1f, 1f, 1f) }
                BouncyChip(stringResource(R.string.preset_large)) { preset(1.3f, 1.3f, 1.2f) }
                BouncyChip(stringResource(R.string.preset_wide)) { preset(1.5f, 1.2f, 1.1f) }
                BouncyChip(stringResource(R.string.preset_tall)) { preset(1.2f, 1.6f, 1.3f) }
            }

            SubTitle(stringResource(if (popup) R.string.kind_sec_banner else R.string.kind_sec_pill))
            CenteredSettingSlider(
                stringResource(R.string.cust_banner_width), Icons.Rounded.Straighten, "${(s.w * 100).toInt()}%",
                s.w, 0.8f..1.8f, 1f, { update(s.copy(w = it)) }, { commit() }
            )
            CenteredSettingSlider(
                stringResource(R.string.cust_banner_height), Icons.Rounded.Height, "${(s.h * 100).toInt()}%",
                s.h, 0.8f..2f, 1f, { update(s.copy(h = it)) }, { commit() }
            )

            SubTitle(stringResource(R.string.kind_sec_card))
            CenteredSettingSlider(
                stringResource(R.string.cust_card_width), Icons.Rounded.Straighten, "${(s.cardW * 100).toInt()}%",
                s.cardW, 0.7f..1.3f, 1f, { update(s.copy(cardW = it)); showCard = true }, { commit() }
            )
            CenteredSettingSlider(
                stringResource(R.string.cust_card_height), Icons.Rounded.Height, "${(s.cardH * 100).toInt()}%",
                s.cardH, 0.7f..1.6f, 1f, { update(s.copy(cardH = it)); showCard = true }, { commit() }
            )

            SubTitle(stringResource(R.string.kind_sec_look))
            CenteredSettingSlider(
                stringResource(R.string.cust_banner_text), Icons.Rounded.TextFields, "${(s.text * 100).toInt()}%",
                s.text, 0.8f..1.8f, 1f, { update(s.copy(text = it)) }, { commit() }
            )
            val cornerShown = if (s.corner < 0f) globalCorner else s.corner
            SettingSlider(
                stringResource(R.string.cust_roundness), Icons.Rounded.RoundedCorner, "${(cornerShown * 100).toInt()}%",
                cornerShown, 0.2f..1f, { update(s.copy(corner = it)) }, { commit() }
            )
            if (popup) {
                SettingSlider(
                    stringResource(R.string.banner_duration), Icons.Rounded.Timer,
                    if (s.durationSec == 0) stringResource(R.string.banner_auto)
                    else stringResource(R.string.cust_seconds, s.durationSec),
                    s.durationSec.toFloat(), 0f..10f, { update(s.copy(durationSec = it.toInt())) }, { commit() }
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    update(KindStyle())
                    commit()
                }) {
                    Icon(Icons.Rounded.RestartAlt, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.banner_reset))
                }
                Button(onClick = onClose) { Text(stringResource(R.string.common_done)) }
            }
        }
    }
}
