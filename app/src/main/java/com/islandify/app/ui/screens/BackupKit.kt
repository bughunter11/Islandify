package com.islandify.app.ui.screens

// BACKUPKIT-FILE: Customize ke main page par "Backup and restore" card

import com.islandify.app.ui.components.ToggleRow
import com.islandify.app.core.BackupNudge
import androidx.compose.runtime.collectAsState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.islandify.app.R
import com.islandify.app.core.IslandSettings
import com.islandify.app.core.exportTo
import com.islandify.app.core.importFrom
import com.islandify.app.ui.components.SectionCard

@Composable
internal fun BackupCard() {
    val ctx = LocalContext.current
    var msg by remember { mutableStateOf<Int?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) msg = if (IslandSettings.exportTo(ctx, uri)) {
            BackupNudge.markBackedUp()   // NUDGEKIT (PATCH_10_11_12)
            R.string.backup_done
        } else R.string.backup_failed
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) msg = if (IslandSettings.importFrom(ctx, uri)) {
            BackupNudge.reset()   // NUDGEKIT (PATCH_10_11_12)
            R.string.restore_done
        } else R.string.restore_failed
    }
    SectionCard(title = stringResource(R.string.backup_title), icon = Icons.Rounded.Backup) {
        Text(
            stringResource(R.string.backup_sub),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { exportLauncher.launch("islandify-backup.json") }) {
                Text(stringResource(R.string.backup_export))
            }
            FilledTonalButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                Text(stringResource(R.string.backup_import))
            }
        }
        // NUDGEKIT (PATCH_10_11_12): reminder on / off
        val nudgeOn by BackupNudge.enabled.collectAsState()
        ToggleRow(
            icon = Icons.Rounded.Backup,
            title = stringResource(R.string.nudge_switch),
            checked = nudgeOn,
            onChange = { BackupNudge.setEnabled(it) },
            subtitle = stringResource(R.string.nudge_switch_sub)
        )
        msg?.let {
            Text(
                stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
