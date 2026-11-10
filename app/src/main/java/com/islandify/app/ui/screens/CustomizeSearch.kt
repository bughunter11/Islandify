package com.islandify.app.ui.screens

// SEARCHKIT  (Customize ka search bar + "seedha is setting par le jao" anchors.)

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.islandify.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/** Search se khuli setting ka title (R.string id). Row dikhte hi wahan scroll karke thodi der chamakti hai. */
internal object CustSearch {
    val target = MutableStateFlow<Int?>(null)
    fun consume(title: Int) { if (target.value == title) target.value = null }
}

/**
 * Setting row par lagao. Agar search se isi title par aaye hain to row screen me scroll hoke aati hai
 * aur thodi der halka highlight hoti hai.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.searchAnchor(@StringRes title: Int): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val target by CustSearch.target.collectAsState()
    val hit = target == title
    var glow by remember { mutableStateOf(false) }
    LaunchedEffect(hit) {
        if (hit) {
            delay(500)   // page ki animation / card khulne tak ruko
            runCatching { requester.bringIntoView() }
            glow = true
            delay(1800)
            glow = false
            CustSearch.consume(title)
        }
    }
    val bg by animateColorAsState(
        if (glow) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
        tween(350),
        label = "searchGlow"
    )
    return this
        .bringIntoViewRequester(requester)
        .background(bg, RoundedCornerShape(14.dp))
}

/** Ek searchable setting: title, kis page par hai, aur extra (English / Hinglish) shabd. */
internal class SearchEntry(@StringRes val title: Int, val page: CustPage, val keys: String = "")

/** Sirf wahi settings jo hamesha dikhti hain aur jinka row searchAnchor se juda hai. */
internal val SEARCH_INDEX: List<SearchEntry> = listOf(
    // Pill > Placement
    SearchEntry(R.string.cust_size, CustPage.Placement, "scale zoom bigger smaller bada chhota"),
    SearchEntry(R.string.cust_width, CustPage.Placement, "wide chauda"),
    SearchEntry(R.string.cust_height, CustPage.Placement, "tall lamba"),
    SearchEntry(R.string.cust_left_right, CustPage.Placement, "position horizontal move x"),
    SearchEntry(R.string.cust_up_down, CustPage.Placement, "position vertical move y"),
    // Pill > Visuals
    SearchEntry(R.string.cust_roundness, CustPage.Visuals, "corner radius round gol"),
    SearchEntry(R.string.cust_expanded_corner, CustPage.Visuals, "corner radius card"),
    SearchEntry(R.string.cust_font_scale, CustPage.Visuals, "text size font"),
    SearchEntry(R.string.cust_glow, CustPage.Visuals, "light chamak"),
    SearchEntry(R.string.cust_app_accent, CustPage.Visuals, "color accent"),
    SearchEntry(R.string.cust_outline, CustPage.Visuals, "border stroke"),
    SearchEntry(R.string.cust_pulse, CustPage.Visuals, "flash shadow"),
    SearchEntry(R.string.cust_style_pill, CustPage.Design, "design style theme look glass neon gradient"),   // STYLEKIT2
    SearchEntry(R.string.cust_notch, CustPage.Design, "notch edge top attached camera cutout punch hole"),   // NOTCHMODE
    SearchEntry(R.string.cust_pill_alpha, CustPage.Design, "opacity transparent transparency alpha"),   // STYLEKIT2
    SearchEntry(R.string.cust_pill_accent, CustPage.Design, "accent glow color"),   // STYLEKIT2
    // Pill > Duration
    SearchEntry(R.string.cust_auto_collapse, CustPage.Duration, "timeout close hide seconds"),
    SearchEntry(R.string.cust_anim_speed, CustPage.Duration, "animation faster slower"),
    // Pill > Behavior
    SearchEntry(R.string.cust_swipe_up, CustPage.Behavior, "gesture swipe"),
    SearchEntry(R.string.cust_swipe_down, CustPage.Behavior, "gesture swipe"),
    SearchEntry(R.string.cust_swipe_left, CustPage.Behavior, "gesture swipe"),
    SearchEntry(R.string.cust_swipe_right, CustPage.Behavior, "gesture swipe"),
    SearchEntry(R.string.cust_show_when, CustPage.Behavior, "visibility locked screen on always"),
    SearchEntry(R.string.cust_launcher_only, CustPage.Behavior, "home screen launcher"),
    SearchEntry(R.string.cust_hide_owner, CustPage.Behavior, "own app"),
    SearchEntry(R.string.cust_hide_landscape, CustPage.Behavior, "rotate"),
    SearchEntry(R.string.cust_outside, CustPage.Behavior, "tap close dismiss"),
    SearchEntry(R.string.cust_quiet_on, CustPage.Behavior, "do not disturb dnd night silent"),
    SearchEntry(R.string.cust_hide_fullscreen, CustPage.Behavior, "games video"),
    SearchEntry(R.string.cust_idle_info, CustPage.Behavior, "clock time battery"),
    SearchEntry(R.string.cust_idle_alarm, CustPage.Behavior, "alarm"),
    SearchEntry(R.string.cust_brief_calendar, CustPage.Behavior, "events"),
    SearchEntry(R.string.cust_cal_hours, CustPage.Behavior, "calendar event hours window account outlook google"),   // CALKIT
    SearchEntry(R.string.cust_flash_pill, CustPage.Behavior, "torch flashlight"),
    SearchEntry(R.string.cust_privacy_banners, CustPage.Banner, "camera mic recording"),
    SearchEntry(R.string.cust_haptics, CustPage.Behavior, "vibration vibrate"),
    SearchEntry(R.string.cust_unhide, CustPage.Behavior, "show dismissed hidden activity timer live again"),   // REVIVEKIT
    SearchEntry(R.string.cust_sticky, CustPage.Behavior, "keep notification sticky stay until tap"),   // CLEANKIT
    SearchEntry(R.string.cust_clear_on_dismiss, CustPage.Card, "notification"),
    SearchEntry(R.string.cust_direct_all, CustPage.Card, "message card open"),
    SearchEntry(R.string.cust_direct_reply, CustPage.Card, "message card open"),
    SearchEntry(R.string.cust_media_peek, CustPage.Behavior, "music song"),
    SearchEntry(R.string.cust_media_keep_paused, CustPage.Behavior, "music pause"),
    SearchEntry(R.string.cust_media_apple, CustPage.Behavior, "music apple card"),
    SearchEntry(R.string.cust_bb_enable, CustPage.Bubble, "show hide bubble toggle off on"),   // BUBBLETOGGLE
    SearchEntry(R.string.cust_bb_extras, CustPage.Bubble, "music timer downloads bubble"),
    SearchEntry(R.string.cust_bb_overlap, CustPage.Bubble, "overlap stack circles icons bubbles"),   // REVIVEKIT
    SearchEntry(R.string.cust_bb_move, CustPage.Bubble, "move drag bubble position reset"),   // BUBBLEMOVE
    SearchEntry(R.string.cust_conceal, CustPage.Behavior, "lock screen hide message privacy"),
    SearchEntry(R.string.cust_conceal_pics, CustPage.Behavior, "lock screen photo privacy"),
    SearchEntry(R.string.cust_motion, CustPage.Behavior, "reduce motion animation remove animations accessibility talkback calm"),   // MOTIONKIT (PATCH_10_11_12)
    // Bubble   (MOVEKIT: search entries naye pages par)
    SearchEntry(R.string.cust_bb_size, CustPage.Bubble, "bada chhota"),
    SearchEntry(R.string.cust_bb_gap, CustPage.Bubble, "distance space"),
    SearchEntry(R.string.cust_bb_y, CustPage.Bubble, "up down vertical position"),
    SearchEntry(R.string.cust_bb_alpha, CustPage.Bubble, "opacity transparent transparency alpha"),
    SearchEntry(R.string.cust_style_bubble, CustPage.Bubble, "design style theme look circle ring glass"),   // STYLEKIT
    SearchEntry(R.string.cust_bb_swipe, CustPage.Bubble, "clear dismiss"),
    SearchEntry(R.string.cust_bb_swipe_dist, CustPage.Bubble, "clear dismiss distance"),
    // Message card
    SearchEntry(R.string.cust_mc_width, CustPage.Card, "wide chauda"),
    SearchEntry(R.string.cust_mc_y, CustPage.Card, "up down vertical position"),
    SearchEntry(R.string.cust_mc_corner, CustPage.Card, "radius round"),
    SearchEntry(R.string.cust_mc_text, CustPage.Card, "font size"),
    SearchEntry(R.string.cust_mc_alpha, CustPage.Card, "opacity transparent transparency alpha"),
    SearchEntry(R.string.cust_mc_accent, CustPage.Card, "color tint"),
    SearchEntry(R.string.cust_mc_lines, CustPage.Card, "message lines max height"),
    SearchEntry(R.string.cust_mc_scroll_speed, CustPage.Card, "scroll auto-scroll speed reading"),
    SearchEntry(R.string.cust_mc_buttons, CustPage.Card, "reply action"),
    SearchEntry(R.string.cust_mc_row, CustPage.Card, "bubbles row"),
    SearchEntry(R.string.cust_style_card, CustPage.Card, "design style theme look glass material neon minimal bold"),   // STYLEKIT
    SearchEntry(R.string.cust_mc_up_dist, CustPage.Card, "swipe distance"),
    SearchEntry(R.string.cust_mc_side_dist, CustPage.Card, "swipe distance"),
    // Banner
    SearchEntry(R.string.cust_bn_width, CustPage.Banner, "wide chauda"),
    SearchEntry(R.string.cust_bn_height, CustPage.Banner, "tall lamba"),
    SearchEntry(R.string.cust_bn_y, CustPage.Banner, "up down vertical position"),
    SearchEntry(R.string.cust_bn_corner, CustPage.Banner, "radius round"),
    SearchEntry(R.string.cust_bn_text, CustPage.Banner, "font size"),
    SearchEntry(R.string.cust_bn_alpha, CustPage.Banner, "opacity transparent transparency alpha"),
    SearchEntry(R.string.cust_bn_accent, CustPage.Banner, "glow color"),
    SearchEntry(R.string.cust_bn_sub, CustPage.Banner, "second line"),
    SearchEntry(R.string.cust_bn_badge, CustPage.Banner, "count"),
    SearchEntry(R.string.cust_style_banner, CustPage.Banner, "design style theme look glass neon gradient"),   // STYLEKIT
    // MUSICKIT
    SearchEntry(R.string.cust_style_music, CustPage.Music, "music card player song design style theme look glass neon material"),
    SearchEntry(R.string.cust_music_corner, CustPage.Music, "music radius round corner"),
    SearchEntry(R.string.cust_music_art, CustPage.Music, "album art cover photo size"),
    SearchEntry(R.string.cust_music_art_corner, CustPage.Music, "album art cover round corner"),
    SearchEntry(R.string.cust_music_wave, CustPage.Music, "waveform equalizer bars animation"),
    SearchEntry(R.string.cust_wave_speed, CustPage.Music, "waveform speed slow fast calm animation"),   // WAVESYNC
    SearchEntry(R.string.cust_wave_follow, CustPage.Music, "waveform follow playback speed podcast"),   // WAVESYNC
    SearchEntry(R.string.cust_music_auto, CustPage.Music, "auto color colour song album art dynamic"),
    SearchEntry(R.string.cust_music_x, CustPage.Music, "music card position horizontal move x left right camera"),   // MUSICPOS
    SearchEntry(R.string.cust_music_y, CustPage.Music, "music card position vertical move y up down camera"),   // MUSICPOS
    SearchEntry(R.string.cust_music_pill_auto, CustPage.Music, "music pill auto color colour song album art"),
)

/** Customize ke upar ka search box. */
@Composable
internal fun CustSearchBar(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.cust_search_hint), maxLines = 1) },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.cust_search_clear))
                }
            }
        },
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun pagePath(p: CustPage): String {
    val par = p.parent
    return if (par != null) "${stringResource(par.title)} › ${stringResource(p.title)}" else stringResource(p.title)
}

/** Match hone wali settings ki list. Tap = us page par khulta hai aur setting tak scroll hota hai. */
@Composable
internal fun CustSearchResults(query: String, onOpen: (CustPage, Int) -> Unit) {
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    // App Hindi me ho tab bhi English shabd se search chale
    val en = remember(ctx) {
        val cfg = android.content.res.Configuration(ctx.resources.configuration)
        cfg.setLocale(java.util.Locale.ENGLISH)
        ctx.createConfigurationContext(cfg)
    }
    val hits = remember(query, ctx) {
        val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        SEARCH_INDEX.filter { e ->
            val hay = (
                ctx.getString(e.title) + " " + en.getString(e.title) + " " + e.keys + " " +
                    ctx.getString(e.page.title) + " " + en.getString(e.page.title)
                ).lowercase()
            tokens.all { it in hay }
        }.take(40)
    }
    if (hits.isEmpty()) {
        Text(
            stringResource(R.string.cust_search_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            hits.forEachIndexed { i, e ->
                val first = i == 0
                val last = i == hits.lastIndex
                Surface(
                    onClick = { focus.clearFocus(); onOpen(e.page, e.title) },
                    shape = RoundedCornerShape(
                        topStart = if (first) 20.dp else 6.dp,
                        topEnd = if (first) 20.dp else 6.dp,
                        bottomEnd = if (last) 20.dp else 6.dp,
                        bottomStart = if (last) 20.dp else 6.dp,
                    ),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(e.title), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                pagePath(e.page),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
