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

// SPLITBIG  live / system bubbles -> BubbleStoreLive.kt, card actions -> BubbleStoreCard.kt
// SPLITKIT  (BubbleStore: saare bubbles, selection, card open/close, preview state.)

/** Every notification that was shown on the island lives on as a bubble until swiped away. */
object BubbleStore {
    const val MAX = 12
    val enabled: Boolean get() = IslandSettings.bubbleEnabled.value   // REVIVEKIT: "Show bubbles" switch se judta hai (pehle hamesha true)

    /** BUBBLESHIFT: true jab bubble pill ke saath dikh raha ho aur pill Compact / Banner ho. */
    val shiftOn = MutableStateFlow(false)

    /** CARDSCROLL: card ka text scroll hota hai to auto-collapse timer dobara shuru (user padh raha hai). */
    val readTick = MutableStateFlow(0)
    fun bumpRead() { readTick.value = readTick.value + 1 }

    /**
     * Pill ko right me kitna khisakna hai (px) taaki bubble + pill milakar screen par barabar dikhein.
     * Chhoti pill (jagah kaafi) = 0, chauda banner = (bubble + gap) ka aadha.
     */
    fun shiftPx(screenW: Int, density: Float): Int {
        if (!shiftOn.value || bubbleIsFree()) return 0   // BUBBLEMOVE
        val need = (BUBBLE_DP + 2 * PAD_DP + GAP_DP + bubbleOverlapExtraDp(shownItems.value.size)) * density   // OVERLAPKIT
        val c = screenW / 2f + IslandSettings.offsetX.value * density
        val half = maxOf(IslandController.pillWidthPx.value / 2f, CAM_HALF_DP * density)
        val left = c - half - need                 // bubble ke left me bachi jagah (khisakne se pehle)
        val soft = 48f * density
        val t = ((soft - left) / soft).coerceIn(0f, 1f)
        return (t * need / 2f).toInt()
    }

    internal val _items = MutableStateFlow<List<BubbleItem>>(emptyList())
    val items = _items.asStateFlow()

    /**
     * true  = chat padh liya / app ne notification hata di -> bubble bhi (2.5 s baad) hat jaye.
     * false = bubble tab tak rahe jab tak tum swipe na karo (ya shade se notification na hatao).
     */
    const val REMOVE_WHEN_READ = true

    /** CANCELFIX: bubble swipe karke clear karne par uske asli notifications bhi shade se hatein (Behavior > "Swipe also clears" ON ho to). */
    const val CLEAR_SHADE_ON_BUBBLE_SWIPE = true

    internal val bg = CoroutineScope(
        SupervisorJob() + Dispatchers.Main +
            kotlinx.coroutines.CoroutineExceptionHandler { _, e ->   // SMOOTHKIT
                android.util.Log.w("Islandify", "bubble coroutine failed", e)
            }
    )
    internal val pending = HashMap<String, Job>()

    /* ---- PREVIEWKIT ---- */
    /** Customize page preview: 0 = off, 1 = bubble, 2 = banner, 3 = message card. */
    val preview = MutableStateFlow(0)
    fun setPreview(p: Int) { preview.value = p }

    /** KEEPBUBBLE: messages swiped away from the card. Bubble stays, the card shows the next message. */
    internal val _dismissed = MutableStateFlow<Set<String>>(emptySet())
    val dismissed = _dismissed.asStateFlow()
    fun isDismissed(key: String?): Boolean = key != null && key in _dismissed.value

    /** CANCELFIX: user ne jo message swipe karke hataya, uski signature (app wapas post kare to dobara na dikhe). */
    private val dismissedSig = HashMap<String, Pair<Int, Long>>()
    private const val DISMISS_MEMORY_MS = 10 * 60_000L   // DISMISSFIX: hataya hua message 10 min tak wapas nahi

    /** Message user ne hataya: bubble dim rehta hai, removeLater ruk jaata hai, signature yaad rehti hai. */
    fun markUserDismissed(key: String) {
        pending.remove(key)?.cancel()
        _dismissed.update { it + key }
        val b = _items.value.firstOrNull { it.key == key }
        val nowMs = System.currentTimeMillis()
        dismissedSig.entries.removeAll { nowMs - it.value.second > DISMISS_MEMORY_MS }   // POLISHKIT + DISMISSFIX
        if (b != null && b.sig != 0) dismissedSig[key] = b.sig to nowMs
    }

    /** Same key + same signature, hatane ke 60 s ke andar = app ne wahi notification wapas laga di. */
    fun wasUserDismissed(key: String, sig: Int): Boolean {
        val d = dismissedSig[key] ?: return false
        if (System.currentTimeMillis() - d.second > DISMISS_MEMORY_MS) {   // DISMISSFIX
            dismissedSig.remove(key)
            return false
        }
        return d.first == sig
    }

    private val demoItems: List<BubbleItem> = listOf(
        BubbleItem(
            key = "preview:2", pkg = "preview.b", app = "Chat", title = "Sam",
            text = "Sent you the photos", icon = null, accent = 0xFF25D366.toInt(),
            open = null, reply = null, markRead = null,
            actions = listOf(BubbleAction("Mark as read", null)),   // CARDACTIONS
        ),
        BubbleItem(
            key = "preview:1", pkg = "preview.a", app = "Messages", title = "Alex",
            text = "Hey! Are you free tonight? We could grab dinner and talk about the trip.",
            icon = null, accent = 0xFF2AABEE.toInt(), open = null, reply = null, markRead = null,
            actions = listOf(BubbleAction("Mute", null), BubbleAction("Mark as read", null)),   // CARDACTIONS
        ),
    )

    /** Real bubbles. While Customize > Bubble / Message card is open and nothing is waiting: two demo ones. */
    val shownItems = combine(_items, preview) { l, p -> if (l.isEmpty() && (p == 1 || p == 3)) demoItems else l }
        .stateIn(bg, SharingStarted.Eagerly, emptyList())

    /** Same notification key = same bubble entry (updated, moved to the newest position). */
    fun add(i: BubbleItem) {
        pending.remove(i.key)?.cancel()          // app ne cancel + re-post kiya: bubble bachao
        _dismissed.update { it - i.key }         // KEEPBUBBLE: naya message aaya
        _items.update { l ->
            val old = l.firstOrNull { it.key == i.key }
            i.count = (old?.count ?: 0) + 1
            (l.filterNot { it.key == i.key } + i).takeLast(MAX)
        }
    }

    /** UNLOCKFIX: phone unlock hote hi lock screen par chhupaye bubbles asli text / buttons ke saath dikhao. */
    fun revealHidden() {
        _items.update { l ->
            if (l.none { it.unlock != null }) l
            else l.map { b ->
                val u = b.unlock
                if (u == null) b else u(b.icon).also { it.count = b.count }
            }
        }
    }

    fun has(key: String?): Boolean = key != null && _items.value.any { it.key == key }

    /** SINGLEHIDE: akela ek message = bubble nahi (banner / card kaafi). 2+ items ya live / system bubble ho to dikhe. */
    fun worthShowing(l: List<BubbleItem>): Boolean = IslandSettings.bubbleEnabled.value &&   // BUBBLETOGGLE
        (l.size > 1 || (l.size == 1 && (l[0].kind != 0 || l[0].keep)))   // DIRECTCARD2

    const val LIVE_PREFIX = "live:"
    internal val expiry = HashMap<String, Job>()

    /* ---- BUBBLELIVE: music / Clock timer / download: ek hi slot, activity khatam hote hi bubble gayab ---- */
    internal val statusLive = HashSet<String>()   // jo ids hum ne lagaye
    internal val statusOff = HashSet<String>()    // user ne swipe karke hataye: activity dobara shuru hone tak wapas nahi

    /** Delayed removal: if the app re-posts the same notification in the meantime, the bubble stays. */
    fun removeLater(key: String) {
        pending.remove(key)?.cancel()
        pending[key] = bg.launch {
            delay(2500)
            pending.remove(key)
            remove(key)
        }
    }

    fun remove(key: String) {
        _dismissed.update { it - key }   // KEEPBUBBLE
        _items.update { l -> l.filterNot { it.key == key } }
        if (key.startsWith(LIVE_PREFIX)) IslandController.dropParked(key.removePrefix(LIVE_PREFIX))
        fixSelection()
    }

    fun clear() {
        // CANCELFIX: bubble swipe = saare message-notifications shade se bhi hatao
        if (CLEAR_SHADE_ON_BUBBLE_SWIPE && IslandSettings.clearOnDismiss.value) {
            _items.value.filter { it.kind == 0 }.forEach { IslandListener.cancel(it.key) }
        }
        IslandController.dropAllParked()
        _dismissed.value = emptySet()   // KEEPBUBBLE
        _items.value = emptyList()
        _selected.value = null
        closeCard()
    }

    /* ---------- Part 2: card ---------- */
    internal val _cardOpen = MutableStateFlow(false)
    val cardOpen = _cardOpen.asStateFlow()
    internal val _selected = MutableStateFlow<String?>(null)
    val selected = _selected.asStateFlow()
    internal val _replying = MutableStateFlow(false)
    val replying = _replying.asStateFlow()

    private fun fixSelection() {
        val l = _items.value
        if (l.isEmpty()) { _selected.value = null; closeCard(); return }
        if (l.none { it.key == _selected.value }) _selected.value = l.last().key
    }
}
