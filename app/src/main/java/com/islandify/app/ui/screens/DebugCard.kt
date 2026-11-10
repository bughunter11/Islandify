package com.islandify.app.ui.screens

// PATCHKIT15B (DEBUGKIT): About tab me "Debug info" card.

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.islandify.app.R
import com.islandify.app.core.AppLog
import com.islandify.app.ui.components.SectionCard

@Composable
internal fun DebugInfoCard() {
    val ctx = LocalContext.current
    SectionCard(title = stringResource(R.string.debug_title), icon = Icons.Rounded.BugReport) {
        Text(
            stringResource(R.string.debug_sub),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FilledTonalButton(onClick = {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Islandify debug", AppLog.report(ctx)))
            Toast.makeText(ctx, ctx.getString(R.string.debug_copied), Toast.LENGTH_SHORT).show()
        }) { Text(stringResource(R.string.debug_copy)) }
    }
}
