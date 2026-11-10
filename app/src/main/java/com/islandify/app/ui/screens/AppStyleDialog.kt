package com.islandify.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.islandify.app.R
import com.islandify.app.core.AppStyle
import com.islandify.app.core.AppStyles
import com.islandify.app.core.IslandSettings
import com.islandify.app.island.IslandController
import com.islandify.app.island.IslandMode
import com.islandify.app.island.flash

// PATCH_10_11_12 / APPSTYLEKIT: Apps tab > tune icon. Is app ke liye accent rang + show time.

private val APP_COLORS: List<Pair<Int, Int>> = listOf(
    0xFF25D366.toInt() to R.string.app_style_c_green,
    0xFF0A84FF.toInt() to R.string.app_style_c_blue,
    0xFFFF453A.toInt() to R.string.app_style_c_red,
    0xFFFF9F0A.toInt() to R.string.app_style_c_orange,
    0xFFFFD60A.toInt() to R.string.app_style_c_yellow,
    0xFFBF5AF2.toInt() to R.string.app_style_c_purple,
    0xFFFF375F.toInt() to R.string.app_style_c_pink,
    0xFF64D2FF.toInt() to R.string.app_style_c_cyan,
)

@Composable
internal fun AppStyleDialog(pkg: String, label: String, onDismiss: () -> Unit) {
    val styles by AppStyles.map.collectAsState()
    val cur = styles[pkg] ?: AppStyle()
    val defaultName = stringResource(R.string.app_style_default)
    val previewText = stringResource(R.string.app_style_preview_text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.app_style_title, label), maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.app_style_accent), style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Swatch(null, cur.accent == null, defaultName) { AppStyles.set(pkg, cur.copy(accent = null)) }
                    APP_COLORS.forEach { (c, name) ->
                        Swatch(c, cur.accent == c, stringResource(name)) { AppStyles.set(pkg, cur.copy(accent = c)) }
                    }
                }

                val secText = if (cur.durationSec <= 0) defaultName
                    else stringResource(R.string.cust_seconds, cur.durationSec)
                Text(
                    stringResource(R.string.app_style_duration) + ": " + secText,
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = cur.durationSec.toFloat(),
                    onValueChange = { AppStyles.set(pkg, cur.copy(durationSec = it.toInt())) },
                    valueRange = 0f..30f,
                    steps = 29,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        // asli island par dikhao: sirf is app ka rang + time (kuch bubble me nahi jata)
                        IslandController.flash(
                            IslandMode.Notification(label, label, previewText, accent = cur.accent),
                            durationMs = AppStyles.durationMs(pkg) ?: 5500L,
                            key = "appstyle-preview",
                        )
                    }) { Text(stringResource(R.string.app_style_preview)) }
                    TextButton(onClick = { AppStyles.clear(pkg) }, enabled = !cur.isDefault) {
                        Text(stringResource(R.string.app_style_reset))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.app_style_done)) }
        },
    )
}

@Composable
private fun Swatch(color: Int?, selected: Boolean, desc: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (color != null) Color(color) else cs.surfaceContainerHighest)
            .border(if (selected) 3.dp else 1.dp, if (selected) cs.primary else cs.outlineVariant, CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center
    ) {
        if (color == null) Icon(Icons.Rounded.AutoAwesome, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
        else if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}
