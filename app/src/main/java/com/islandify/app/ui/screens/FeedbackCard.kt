package com.islandify.app.ui.screens

import com.islandify.app.R
import com.islandify.app.ui.components.SectionCard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

// FEEDBACKKIT: "Suggest a feature" card (About screen). Koi INTERNET permission nahi chahiye:
// idea clipboard me copy hota hai aur developer ki Telegram chat khulti hai (ya system Share).
private const val FEEDBACK_TG = "https://t.me/zyronix01"
private const val MAX_IDEA = 600

private val TOPICS = listOf(
    R.string.fb_topic_design,
    R.string.fb_topic_activity,
    R.string.fb_topic_gesture,
    R.string.fb_topic_apps,
    R.string.fb_topic_battery,
    R.string.fb_topic_other,
)

// Developer ko hamesha English topic naam jaata hai (user ki language kuch bhi ho).
private val TOPICS_EN = listOf(
    "Look & design",
    "New pop-up type",
    "Gestures",
    "App support",
    "Battery & speed",
    "Something else",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FeedbackCard() {
    val ctx = LocalContext.current
    var topic by rememberSaveable { mutableIntStateOf(-1) }
    var idea by rememberSaveable { mutableStateOf("") }
    val ready = idea.isNotBlank()

    SectionCard(title = stringResource(R.string.fb_title), icon = Icons.Rounded.Lightbulb) {
        Text(stringResource(R.string.fb_hint), style = MaterialTheme.typography.bodyMedium)

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TOPICS.forEachIndexed { i, res ->
                FilterChip(
                    selected = topic == i,
                    onClick = { topic = if (topic == i) -1 else i },
                    label = { Text(stringResource(res)) },
                )
            }
        }

        OutlinedTextField(
            value = idea,
            onValueChange = { if (it.length <= MAX_IDEA) idea = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.fb_idea_label)) },
            minLines = 3,
            maxLines = 6,
            supportingText = { Text("${idea.length}/$MAX_IDEA") },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = ready,
                onClick = { sendViaTelegram(ctx, buildMessage(ctx, TOPICS_EN.getOrNull(topic), idea)) },
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, null)
                Text(stringResource(R.string.fb_send), Modifier.padding(start = 8.dp))
            }
            FilledTonalButton(
                enabled = ready,
                onClick = { shareIdea(ctx, buildMessage(ctx, TOPICS_EN.getOrNull(topic), idea)) },
            ) {
                Icon(Icons.Rounded.Share, null)
                Text(stringResource(R.string.fb_share), Modifier.padding(start = 8.dp))
            }
        }

        Text(
            stringResource(R.string.fb_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun buildMessage(ctx: Context, topicEn: String?, idea: String): String {
    val info = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull()
    val ver = info?.versionName ?: "?"
    val code = info?.let {
        if (Build.VERSION.SDK_INT >= 28) it.longVersionCode
        else @Suppress("DEPRECATION") it.versionCode.toLong()
    } ?: 0L
    val device = "${Build.MANUFACTURER.orEmpty().replaceFirstChar { c -> c.uppercase() }} ${Build.MODEL}".trim()
    return buildString {
        append("Islandify feature idea\n")
        if (topicEn != null) append("Topic: ").append(topicEn).append('\n')
        append('\n').append(idea.trim()).append("\n\n")
        append("Islandify $ver ($code) | $device | Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    }
}

/** Idea copy karke developer ki Telegram chat kholta hai; user bas paste + send karta hai. */
private fun sendViaTelegram(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Islandify idea", text))
    Toast.makeText(ctx, ctx.getString(R.string.fb_copied), Toast.LENGTH_LONG).show()
    runCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(FEEDBACK_TG)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun shareIdea(ctx: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching {
        ctx.startActivity(
            Intent.createChooser(send, ctx.getString(R.string.fb_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
