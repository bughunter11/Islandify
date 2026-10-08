package com.islandify.app.ui.screens
import com.islandify.app.R
import androidx.compose.ui.res.stringResource

import com.islandify.app.core.*
import com.islandify.app.island.*
import com.islandify.app.ui.components.*
import com.islandify.app.ui.onboarding.*
import com.islandify.app.ui.theme.*

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

@Composable
fun ActivitiesScreen() {
    val triggers by IslandSettings.triggers.collectAsState()

    ScreenScaffold(stringResource(R.string.tab_activities)) {
        SectionCard(title = stringResource(R.string.act_title), icon = Icons.Rounded.Sensors) {
            Trigger.entries.forEach { t ->
                // Rows enter one after another; the icon lights up with the switch
                Box(Modifier.reveal()) {
                    ToggleRow(
                        icon = t.icon(),
                        title = stringResource(t.label),
                        subtitle = stringResource(t.hint()),
                        checked = t.key in triggers,
                        onChange = { IslandSettings.setTrigger(t, it) }
                    )
                }
            }
        }
        Text(
            stringResource(R.string.act_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.reveal()
        )
    }
}
