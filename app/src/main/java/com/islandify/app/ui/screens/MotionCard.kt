package com.islandify.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.islandify.app.R
import com.islandify.app.core.Motion
import com.islandify.app.ui.components.SectionCard

// PATCH_10_11_12 / MOTIONKIT: Customize > Behavior > Motion
@Composable
internal fun MotionCard() {
    SectionCard(title = stringResource(R.string.cust_motion_title), icon = Icons.Rounded.Speed) {
        Text(
            stringResource(R.string.cust_motion_sub),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(
            androidx.compose.ui.Modifier.searchAnchor(R.string.cust_motion),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChoiceRow(
                R.string.cust_motion, Motion.mode,
                listOf(R.string.cust_motion_system, R.string.cust_motion_on)
            )
        }
    }
}
