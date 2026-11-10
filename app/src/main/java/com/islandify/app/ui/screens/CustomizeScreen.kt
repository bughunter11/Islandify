package com.islandify.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusWeak
import androidx.compose.material.icons.rounded.PhoneAndroid   // PILLPAGE
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.AutoAwesome   // STYLEKIT2
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.MusicNote   // MUSICKIT
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.islandify.app.R
import com.islandify.app.core.IslandSettings
import com.islandify.app.core.resetAll
import com.islandify.app.ui.components.ScreenScaffold
import com.islandify.app.ui.components.reveal
import com.islandify.app.ui.theme.hueOf
import kotlin.math.roundToInt

/**
 * The four groups shown on the Customize tab.
 * [hueShift] rotates the app's accent hue, so every badge is a different color
 * but they all follow the wallpaper / accent (Material You) and the light / dark theme.
 */
internal enum class CustPage(
    @StringRes val title: Int,
    val icon: ImageVector,
    val hueShift: Float,
) {
    Pill(R.string.cust_page_pill, Icons.Rounded.PhoneAndroid, 195f),   // PILLPAGE
    Placement(R.string.cust_page_placement, Icons.Rounded.CenterFocusWeak, 195f),
    Design(R.string.cust_page_design, Icons.Rounded.AutoAwesome, 290f),   // STYLEKIT2
    Visuals(R.string.cust_page_visuals, Icons.Rounded.Palette, 0f),
    Duration(R.string.cust_page_duration, Icons.Rounded.Timer, 65f),
    Behavior(R.string.cust_page_behavior, Icons.Rounded.Settings, 330f),
    Bubble(R.string.cust_page_bubble, Icons.Rounded.ChatBubble, 130f),
    Card(R.string.cust_page_card, Icons.Rounded.ViewAgenda, 260f),
    Music(R.string.cust_page_music, Icons.Rounded.MusicNote, 95f),   // MUSICKIT
    Banner(R.string.cust_page_banner, Icons.Rounded.Notifications, 30f),
}

/** Customize tab: a list of 4 groups. Tap one to open its settings page. */
@Composable
fun CustomizeScreen() {
    var pageName by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = pageName != null) {   // PILLPAGE
        CustSearch.target.value = null
        pageName = pageName?.let { CustPage.valueOf(it).parent?.name }
    }

    AnimatedContent(
        targetState = pageName,
        transitionSpec = {
            val ease = FastOutSlowInEasing
            if (custDepth(targetState) > custDepth(initialState)) {   // PILLPAGE
                (slideInHorizontally(tween(300, easing = ease)) { it / 4 } + fadeIn(tween(220)))
                    .togetherWith(slideOutHorizontally(tween(300, easing = ease)) { -it / 6 } + fadeOut(tween(120)))
            } else {
                (slideInHorizontally(tween(300, easing = ease)) { -it / 6 } + fadeIn(tween(220)))
                    .togetherWith(slideOutHorizontally(tween(300, easing = ease)) { it / 4 } + fadeOut(tween(120)))
            }
        },
        label = "customizeNav"
    ) { name ->
        if (name == null) {
            CustomizeHub(   // SEARCHKIT
                onOpen = { CustSearch.target.value = null; pageName = it.name },
                onSearch = { p, t -> CustSearch.target.value = t; pageName = (if (p.parent == CustPage.Pill) CustPage.Pill else p).name }   // STYLEKIT3
            )
        } else {
            val cur = CustPage.valueOf(name)   // PILLPAGE
            CustomizePage(
                cur,
                onBack = { CustSearch.target.value = null; pageName = cur.parent?.name },
                onOpen = { pageName = it.name }
            )
        }
    }
}

@Composable
private fun CustomizeHub(onOpen: (CustPage) -> Unit, onSearch: (CustPage, Int) -> Unit) {   // SEARCHKIT
    val summaries = pageSummaries()
    var confirmReset by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }   // SEARCHKIT
    val searching = query.isNotBlank()
    val pages = hubPages   // PILLPAGE: sirf 4 group

    ScreenScaffold(stringResource(R.string.tab_customize)) {
        CustSearchBar(query) { query = it }   // SEARCHKIT
        if (!searching) BackupNudgeCard()   // NUDGEKIT (PATCH_10_11_12)
        if (searching) CustSearchResults(query, onSearch)
        // One grouped list: big corners outside, small corners between the rows
        if (!searching) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            pages.forEachIndexed { i, p ->
                CategoryRow(
                    page = p,
                    summary = summaries[p].orEmpty(),
                    first = i == 0,
                    last = i == pages.lastIndex,
                    modifier = Modifier.reveal(),
                    onClick = { onOpen(p) }
                )
            }
        }

        // Accent color, app background, theme, language: main page par, 4 groups ke niche
        if (!searching) {   // SEARCHKIT
            AppColorsCards()
            LanguageCard()
            BackupCard()   // BACKUPKIT
        }

        if (!searching) FilledTonalButton(   // SEARCHKIT
            onClick = { confirmReset = true },
            modifier = Modifier.fillMaxWidth().reveal()
        ) {
            Icon(Icons.Rounded.RestartAlt, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.cust_reset))
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.cust_reset)) },
            text = { Text(stringResource(R.string.cust_reset_msg)) },
            confirmButton = {
                TextButton(onClick = { IslandSettings.resetAll(); confirmReset = false }) {
                    Text(stringResource(R.string.cust_reset_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cust_cancel)) }
            }
        )
    }
}

/** Live one-line summary under each title, so the list shows the current values. */
@Composable
internal fun pageSummaries(): Map<CustPage, String> {   // PILLPAGE
    val scale by IslandSettings.scale.collectAsState()
    val ox by IslandSettings.offsetX.collectAsState()
    val oy by IslandSettings.offsetY.collectAsState()
    val corner by IslandSettings.corner.collectAsState()
    val outline by IslandSettings.outlineOn.collectAsState()
    val auto by IslandSettings.autoCollapseSec.collectAsState()
    val showWhen by IslandSettings.showWhen.collectAsState()

    val placement = "${stringResource(R.string.cust_size)} ${(scale * 100).roundToInt()}%  ·  X ${ox.roundToInt()}  ·  Y ${oy.roundToInt()}"
    val visuals = "${stringResource(R.string.cust_roundness)} ${(corner * 100).roundToInt()}%" +
        if (outline) "  ·  ${stringResource(R.string.cust_outline)}" else ""
    val autoText = if (auto == 0) stringResource(R.string.cust_never) else stringResource(R.string.cust_seconds, auto)
    val duration = "${stringResource(R.string.cust_auto_collapse)}: $autoText"
    val whenLabel = stringResource(
        when (showWhen) {
            0 -> R.string.show_when_unlocked
            1 -> R.string.show_when_screen_on
            else -> R.string.show_when_always
        }
    )
    val behavior = "${stringResource(R.string.cust_show_when)}: $whenLabel"
    val bbSize by IslandSettings.bubbleSize.collectAsState()
    val mcWidth by IslandSettings.cardWidth.collectAsState()
    val bubbleSummary = "${stringResource(R.string.cust_bb_size)} ${bbSize.roundToInt()} dp"
    val cardSummary = "${stringResource(R.string.cust_mc_width)} ${(mcWidth * 100).roundToInt()}%"
    val bnWidth by IslandSettings.bannerW.collectAsState()
    val bannerSummary = "${stringResource(R.string.cust_bn_width)} ${(bnWidth * 100).roundToInt()}%"

    val pillStyleNow by IslandSettings.pillStyle.collectAsState()   // STYLEKIT2
    val designSummary = stringResource(SHELL_STYLE_LABELS[pillStyleNow.coerceIn(0, SHELL_STYLE_LABELS.lastIndex)])   // STYLEKIT2
    val musicStyleNow by IslandSettings.musicStyle.collectAsState()   // MUSICKIT
    val musicSummary = stringResource(CARD_STYLE_LABELS[musicStyleNow.coerceIn(0, CARD_STYLE_LABELS.lastIndex)])
    return mapOf(
        CustPage.Pill to "$designSummary  ·  $placement",   // STYLEKIT3
        CustPage.Design to designSummary,   // STYLEKIT2
        CustPage.Placement to placement,
        CustPage.Visuals to visuals,
        CustPage.Duration to duration,
        CustPage.Behavior to behavior,
        CustPage.Bubble to bubbleSummary,
        CustPage.Card to cardSummary,
        CustPage.Music to musicSummary,   // MUSICKIT
        CustPage.Banner to bannerSummary,
    )
}

@Composable
internal fun CategoryRow(   // PILLPAGE
    page: CustPage,
    summary: String,
    first: Boolean,
    last: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()

    // The corners soften while the row is pressed
    val outer = 28.dp
    val inner = 6.dp
    val pressedCorner = 20.dp
    val spec = spring<androidx.compose.ui.unit.Dp>(dampingRatio = 0.7f, stiffness = 600f)
    val top by animateDpAsState(if (pressed) pressedCorner else if (first) outer else inner, spec, label = "rowTop")
    val bottom by animateDpAsState(if (pressed) pressedCorner else if (last) outer else inner, spec, label = "rowBottom")

    val (badge, onBadge) = badgeColors(page)

    Surface(
        onClick = onClick,
        interactionSource = source,
        shape = RoundedCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(badge),
                contentAlignment = Alignment.Center
            ) {
                Icon(page.icon, null, Modifier.size(24.dp), tint = onBadge)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(page.title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                if (summary.isNotEmpty()) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Badge background + icon color. Comes from the app accent, so it changes with the wallpaper and theme. */
@Composable
private fun badgeColors(page: CustPage): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    val dark = cs.surface.luminance() < 0.5f
    val hue = (hueOf(cs.primary.toArgb()) + page.hueShift) % 360f
    return if (dark) {
        Color.hsv(hue, 0.42f, 0.34f) to Color.hsv(hue, 0.25f, 0.93f)
    } else {
        Color.hsv(hue, 0.20f, 0.96f) to Color.hsv(hue, 0.65f, 0.42f)
    }
}
