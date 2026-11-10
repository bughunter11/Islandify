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

// SPLITKIT  (BubbleAction + BubbleItem: bubble / card ka data.)

/** CARDACTIONS: notification ka ek asli button (Reply, Mark as read, No thanks, Turn on, Archive ...). */
class BubbleAction(
    val label: String,
    val intent: PendingIntent?,
    val reply: Notification.Action? = null,   // != null: inline reply (text box khulta hai)
)

class BubbleItem(
    val key: String,
    val pkg: String,
    val app: String,
    val title: String,
    val text: String,
    val icon: Bitmap?,
    val accent: Int?,
    val open: PendingIntent?,
    val reply: Notification.Action?,
    val markRead: PendingIntent?,
    val time: Long = System.currentTimeMillis(),
    val markReadLabel: String = "Mark as read",
    val kind: Int = 0,   // 0 = message, 1 = live (map / ride / order), 2 = system (charging, bluetooth...)
    val actions: List<BubbleAction> = emptyList(),   // CARDACTIONS: card ke buttons (notification ke asli actions)
    val keep: Boolean = false,                       // DIRECTCARD2: card se aaya message = akela ho tab bhi bubble dikhe
    val sig: Int = 0,                                // CANCELFIX: notification ki signature (re-post pehchanne ke liye)
    val unlock: ((Bitmap?) -> BubbleItem)? = null,   // UNLOCKFIX: lock screen par chhupaya item, unlock par asli roop
) {
    /** How many messages this bubble got (same chat = same bubble). 1 = first message. */
    var count: Int = 1

    val color: Int
        get() = accent ?: PALETTE[abs(pkg.hashCode()) % PALETTE.size]
    val letter: String
        get() = app.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    companion object {
        private val PALETTE = intArrayOf(
            0xFF2AABEE.toInt(), 0xFF25D366.toInt(), 0xFFE53935.toInt(),
            0xFF8E24AA.toInt(), 0xFFFB8C00.toInt(), 0xFF1E88E5.toInt(),
        )
    }
}
