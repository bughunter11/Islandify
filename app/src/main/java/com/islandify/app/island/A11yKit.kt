package com.islandify.app.island

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.islandify.app.R

// PATCH_10_11_12 / A11YKIT: TalkBack.
// Island, bubble aur message card ke gestures (tap / long-press / swipe) pointerInput se chalte hain, jo
// TalkBack ko dikhte nahi. Yahan unke liye semantic click + "Dismiss" action aur ek chhota description hai.

/** RoundIcon jaisa hi, bas button ka naam seedha diya jata hai (shuffle / repeat / volume ke liye). */
@Composable
internal fun RoundIconD(
    @StringRes desc: Int,
    icon: ImageVector,
    size: Dp,
    bg: Color,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, stringResource(desc), tint = tint, modifier = Modifier.size(size * 0.58f))
    }
}

private fun joinNonBlank(vararg s: String): String? =
    s.filter { it.isNotBlank() }.joinToString(", ").takeIf { it.isNotBlank() }

/** Island kya dikha raha hai, ek line me (lock screen par chhupa hua text pehle se mode me chhupa hota hai). */
private fun islandSummary(m: IslandMode): String? = when (m) {
    is IslandMode.Notification -> joinNonBlank(m.app, m.title, m.text)
    is IslandMode.Media -> joinNonBlank(m.title, m.artist)
    is IslandMode.Call -> joinNonBlank(m.name)
    is IslandMode.Device -> joinNonBlank(m.name)
    is IslandMode.Live -> joinNonBlank(m.app, m.title, m.text)
    else -> null
}

/** Island ke liye: description + tap (open) + long-press (large) + Dismiss action. */
@Composable
internal fun Modifier.islandSemantics(
    mode: IslandMode,
    tap: () -> Unit,
    hold: () -> Unit,
    dismiss: () -> Unit,
): Modifier {
    val openLbl = stringResource(R.string.a11y_island_open)
    val largeLbl = stringResource(R.string.a11y_island_large)
    val dismissLbl = stringResource(R.string.a11y_dismiss)
    val summary = islandSummary(mode) ?: stringResource(R.string.a11y_island)
    return this.semantics {
        contentDescription = summary
        role = Role.Button
        onClick(label = openLbl) { tap(); true }
        onLongClick(label = largeLbl) { hold(); true }
        customActions = listOf(CustomAccessibilityAction(dismissLbl) { dismiss(); true })
    }
}

/** Bubble ke liye: kitne message, tap = card kholo, custom action = sab hatao. */
@Composable
internal fun Modifier.bubbleSemantics(items: List<BubbleItem>): Modifier {
    val last = items.lastOrNull()
    val label = stringResource(R.string.a11y_bubble, last?.app.orEmpty(), items.size)
    val openLbl = stringResource(R.string.a11y_bubble_open)
    val clearLbl = stringResource(R.string.a11y_bubble_clear)
    return this.semantics {
        contentDescription = label
        role = Role.Button
        onClick(label = openLbl) { BubbleStore.onTap(); true }
        customActions = listOf(CustomAccessibilityAction(clearLbl) { BubbleStore.clear(); true })
    }
}

/** Message card ke liye: kaun sa app, tap = app kholo, custom action = message hatao. */
@Composable
internal fun Modifier.cardSemantics(item: BubbleItem): Modifier {
    val label = stringResource(R.string.a11y_card, item.app)
    val openLbl = stringResource(R.string.a11y_island_open)
    val dismissLbl = stringResource(R.string.a11y_dismiss)
    return this.semantics {
        contentDescription = label
        onClick(label = openLbl) { BubbleStore.openSelected(); true }
        customActions = listOf(CustomAccessibilityAction(dismissLbl) { BubbleStore.dismissSelected(); true })
    }
}
