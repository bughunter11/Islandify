package com.islandify.app.ui.screens

// QUIETUI-FILE: Customize > Behavior: "Quiet hours" + "Hide in games & video apps"

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.islandify.app.R
import com.islandify.app.core.IslandSettings
import com.islandify.app.ui.components.SectionCard
import com.islandify.app.ui.components.ToggleRow

@Composable
internal fun QuietHoursCard() {
    val on by IslandSettings.quietOn.collectAsState()
    val from by IslandSettings.quietFrom.collectAsState()
    val to by IslandSettings.quietTo.collectAsState()
    val fs by IslandSettings.hideFullscreen.collectAsState()
    SectionCard(title = stringResource(R.string.cust_quiet_title), icon = Icons.Rounded.Schedule) {
        ToggleRow(
            icon = Icons.Rounded.Schedule,
            title = stringResource(R.string.cust_quiet_on),
            modifier = Modifier.searchAnchor(R.string.cust_quiet_on),   // SEARCHKIT
            checked = on,
            onChange = { IslandSettings.quietOn.value = it; IslandSettings.save() },
            subtitle = stringResource(R.string.cust_quiet_on_sub),
        )
        if (on) {
            QuietTimeRow(R.string.cust_quiet_from, from) { IslandSettings.quietFrom.value = it; IslandSettings.save() }
            QuietTimeRow(R.string.cust_quiet_to, to) { IslandSettings.quietTo.value = it; IslandSettings.save() }
        }
        ToggleRow(
            icon = Icons.Rounded.Visibility,
            title = stringResource(R.string.cust_hide_fullscreen),
            modifier = Modifier.searchAnchor(R.string.cust_hide_fullscreen),   // SEARCHKIT
            checked = fs,
            onChange = { IslandSettings.hideFullscreen.value = it; IslandSettings.save() },
            subtitle = stringResource(R.string.cust_hide_fullscreen_sub),
        )
    }
}

@Composable
private fun QuietTimeRow(@StringRes label: Int, minutes: Int, onPick: (Int) -> Unit) {
    val ctx = LocalContext.current
    val text = String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes / 60, minutes % 60)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                runCatching {
                    android.app.TimePickerDialog(
                        ctx,
                        { _, h, m -> onPick(h * 60 + m) },
                        minutes / 60, minutes % 60,
                        android.text.format.DateFormat.is24HourFormat(ctx)
                    ).show()
                }
            }
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}
