package com.islandify.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.islandify.app.R
import com.islandify.app.core.BackupNudge
import com.islandify.app.core.IslandSettings
import com.islandify.app.core.exportTo
import com.islandify.app.ui.components.SectionCard

// PATCH_10_11_12 / NUDGEKIT: bade badlav ke baad "Backup le lo?" card (Customize ke main page par, sabse upar)
@Composable
internal fun BackupNudgeCard() {
    val show by BackupNudge.show.collectAsState()
    val ctx = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && IslandSettings.exportTo(ctx, uri)) BackupNudge.markBackedUp()
    }
    AnimatedVisibility(visible = show) {
        SectionCard(title = stringResource(R.string.nudge_title), icon = Icons.Rounded.Backup) {
            Text(
                stringResource(R.string.nudge_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalButton(onClick = { exportLauncher.launch("islandify-backup.json") }) {
                    Text(stringResource(R.string.backup_export))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { BackupNudge.later() }) { Text(stringResource(R.string.nudge_later)) }
                    TextButton(onClick = { BackupNudge.setEnabled(false) }) { Text(stringResource(R.string.nudge_never)) }
                }
            }
        }
    }
}
