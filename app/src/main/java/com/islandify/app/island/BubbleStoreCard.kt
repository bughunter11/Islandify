package com.islandify.app.island

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize   // SWIPEANIM
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import android.view.MotionEvent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll   // CARDSCROLL
import androidx.compose.foundation.layout.heightIn   // CARDSCROLL
import androidx.compose.ui.draw.drawWithContent   // CARDSCROLL
import androidx.compose.ui.graphics.BlendMode   // CARDSCROLL
import androidx.compose.ui.graphics.CompositingStrategy   // CARDSCROLL
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults   // M3KIT
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.islandify.app.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

// SPLITBIG  BubbleStore ke card ke actions (extension fun, state BubbleStore.kt me)

/** Tap on a message banner: open the card straight on that message. */
fun BubbleStore.openFor(key: String?) {
    val l = _items.value
    val b = l.firstOrNull { it.key == key } ?: l.lastOrNull() ?: return
    _selected.value = b.key
    _replying.value = false
    _cardOpen.value = true
}

/** Swipe left / right on the card: this message (and its bubble) goes away. */
fun BubbleStore.dismissSelected() {
    val b = current() ?: return
    // DISMISSFIX: card se swipe = ye message + uska bubble hamesha ke liye gaya (dim bubble wapas nahi aayega).
    // Signature yaad rehti hai, to app / system wahi notification dobara post kare tab bhi wapas nahi aayega.
    markUserDismissed(b.key)   // CANCELFIX: signature yaad
    if (b.kind == 0 && IslandSettings.clearOnDismiss.value) IslandListener.cancel(b.key)
    _replying.value = false
    val next = _items.value.lastOrNull { it.key != b.key }
    remove(b.key)   // DISMISSFIX
    if (next != null) _selected.value = next.key else closeCard()
}

/** SWIPEDISMISS: banner / pop-up se swipe: ye message + uska bubble hamesha ke liye gaya (signature yaad rehti hai). */
fun BubbleStore.dismissKey(key: String) {
    if (_items.value.none { it.key == key }) return
    markUserDismissed(key)
    remove(key)
}

/** Tap on the status-bar bubble: opens the card with the newest message. */
fun BubbleStore.onTap() {
    if (IslandController.level.value != IslandLevel.Compact) IslandController.collapseForBubble()
    openCard()
}

fun BubbleStore.openCard() {
    val l = _items.value
    val last = l.lastOrNull { it.key !in _dismissed.value } ?: l.lastOrNull() ?: return
    _dismissed.update { it - last.key }   // KEEPBUBBLE: bubble par tap = ye message dobara dikhao
    _selected.value = last.key
    _replying.value = false
    _cardOpen.value = true
}

/** Swipe up / timeout: the card folds back into the bubble (bubble stays). */
fun BubbleStore.closeCard() {
    _replying.value = false
    _cardOpen.value = false
}

fun BubbleStore.select(key: String) {
    if (_items.value.none { it.key == key }) return
    _dismissed.update { it - key }   // KEEPBUBBLE
    _replying.value = false
    _selected.value = key
}

fun BubbleStore.current(): BubbleItem? {
    val l = _items.value
    return l.firstOrNull { it.key == _selected.value } ?: l.lastOrNull()
}

/** Tap on the card: jump into that app's chat (contentIntent). */
fun BubbleStore.openSelected() {
    val b = current() ?: return
    closeCard()
    if (b.kind != 2) IslandController.openFromBubble(b.open, b.pkg)
    if (b.kind != 1) remove(b.key)   // chat khul gaya -> bubble hat jaye (live bubble rehta hai)
}

fun BubbleStore.archiveSelected() {
    val b = current() ?: return
    if (b.kind == 0) markUserDismissed(b.key)   // DISMISSFIX: app wahi notification wapas laga de to bubble dobara na aaye
    if (b.markRead != null) IslandController.fireAction(b.markRead)
    else IslandListener.cancel(b.key)
    remove(b.key)
}

fun BubbleStore.startReply() { if (current()?.reply != null) _replying.value = true }

/** CARDACTIONS: card ka koi bhi button. Reply = text box, baaki = notification ka action fire + bubble hatao. */
fun BubbleStore.runAction(a: BubbleAction) {
    val b = current() ?: return
    if (a.reply != null) { startReply(); return }
    if (b.kind == 0) markUserDismissed(b.key)   // DISMISSFIX
    IslandController.fireAction(a.intent)
    remove(b.key)
}

/** CARDACTIONS: notification me koi action nahi hai: "Dismiss" = notification shade se bhi hatao + bubble hatao. */
fun BubbleStore.dismissButton() {
    val b = current() ?: return
    IslandListener.cancel(b.key)
    remove(b.key)
}
fun BubbleStore.cancelReply() { _replying.value = false }

fun BubbleStore.sendReply(text: String) {
    val b = current() ?: return
    val a = b.reply ?: return
    if (IslandController.sendReplyTo(a, text)) {
        _replying.value = false
        remove(b.key)
    } else _replying.value = false
}

/**
 * DIRECTCARD2: message seedha card me (banner nahi).
 * Card pehle se khula = naya message bubbles row me intezaar karta hai (user ko card se hataya nahi jata).
 * Banner / bada pill khula ho to pehle wo band hota hai: BubbleCardHost card ko tab block kar deta hai
 * jab island Compact na ho (aur card turant band ho jata tha, isi liye "card nahi khul raha" tha).
 */
fun BubbleStore.openDirect(key: String?) {
    // DIRECTCARD3: user reply likh raha hai (card ya banner) = naya message sirf bubble me, card nahi badlega.
    // Reply nahi chal rahi + card khula hai = naya message card me aata hai, purana card bubble me chala jata hai.
    if (_cardOpen.value && _replying.value) return
    IslandController.queuedMessagesToBubbles()
    IslandController.collapseForBubble()
    openFor(key)
}
