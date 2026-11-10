package com.islandify.app.island

import com.islandify.app.core.*
import com.islandify.app.ui.components.*
import com.islandify.app.ui.onboarding.*
import com.islandify.app.ui.screens.*
import com.islandify.app.ui.theme.*

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import android.os.Build
import android.os.SystemClock
import android.telecom.TelecomManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay   // CANCELFIX
import com.islandify.app.island.IslandListener.Companion.NON_LETTER
import com.islandify.app.island.IslandListener.Companion.CALL_ACTION_WORDS
import com.islandify.app.island.IslandListener.Companion.CLOCK_APPS
import com.islandify.app.island.IslandListener.Companion.TIME_RE
import com.islandify.app.island.IslandListener.Companion.NAV_APPS
import com.islandify.app.island.IslandListener.Companion.TRACK_APPS
import com.islandify.app.island.IslandListener.Companion.DIALERS

// SPLITLO  (Notification posted: appLabel, bodyOf, handlePosted.)

/**
 * App name for a notification.
 * Android 11+ hides apps that are not in <queries>, so getApplicationInfo can fail.
 * The notification itself carries the sender's ApplicationInfo ("android.appInfo"): use that as a fallback.
 */
@Suppress("DEPRECATION")
internal fun IslandListener.appLabel(sbn: StatusBarNotification): String {
    labelCache[sbn.packageName]?.let { return it }
    val pm = packageManager
    val label = runCatching {
        pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
    }.getOrNull()
        ?: runCatching {
            sbn.notification.extras.getParcelable<ApplicationInfo>("android.appInfo")
                ?.loadLabel(pm)?.toString()
        }.getOrNull()
    if (label.isNullOrBlank()) return sbn.packageName
    labelCache[sbn.packageName] = label
    return label
}

/** If EXTRA_TEXT is empty, use the last chat message (MessagingStyle) or the big text. */
@Suppress("DEPRECATION")
internal fun IslandListener.bodyOf(n: Notification): String {
    val ex = n.extras
    ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
    val arr = ex.getParcelableArray(Notification.EXTRA_MESSAGES)
    if (arr != null) {
        Notification.MessagingStyle.Message.getMessagesFromBundleArray(arr)
            .lastOrNull()?.text?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
}

internal fun IslandListener.handlePosted(sbn: StatusBarNotification) {
    if (sbn.packageName == packageName) return
    val n = sbn.notification

    // Calls first: even if the dialer is in the blocked list, a call must show
    if (isCall(sbn)) {
        handleCall(sbn)
        return
    }

    if (sbn.packageName in IslandSettings.blockedApps.value) return

    // Clock app ka timer
    if (handleClockTimer(sbn)) return

    // Navigation / delivery / ride tracking: their ongoing notification becomes a live activity
    if (handleLive(sbn)) return

    // Download / upload / any progress-bar notification: live progress on the island
    if (handleDownload(sbn)) return

    if (!IslandSettings.isOn(Trigger.Notifications)) return
    if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
    if (n.category == Notification.CATEGORY_TRANSPORT) return   // music has its own UI

    // "Quiet" switch ON = silent, ongoing, service and progress notifications show too
    val all = IslandSettings.isOn(Trigger.Quiet)
    val ongoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0
    if (!all) {
        if (ongoing) return
        if (n.category == Notification.CATEGORY_PROGRESS ||
            n.category == Notification.CATEGORY_SERVICE
        ) return
    }

    // Silent (LOW / MIN importance) channels
    var quiet = false
    val rank = NotificationListenerService.Ranking()
    if (currentRanking.getRanking(sbn.key, rank) &&
        rank.importance < NotificationManager.IMPORTANCE_DEFAULT
    ) {
        if (!all) return
        quiet = true
    }

    val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
    val text = bodyOf(n)
    if (title.isBlank() && text.isBlank()) return

    // SILENTFILTER: smartwatch / accessiblity service notifications skip karo
    // FLAG_LOCAL_ONLY = sirf is device ke liye, watch ko sync nahi (ye usually watch ki notifications hoti hain)
    if (n.flags and Notification.FLAG_LOCAL_ONLY != 0 && ongoing) return
    // Accessibility overlays (TalkBack, switch access etc.) ke status notifications
    if (n.category == Notification.CATEGORY_STATUS && ongoing) return
    // SILENTFILTER: same pkg ki same content ongoing notif agar 10 sec mein dobara aayi = skip (watch/health apps)
    val nowMs = System.currentTimeMillis()
    val spamKey = "${sbn.packageName}|${n.flags and Notification.FLAG_ONGOING_EVENT}"
    val lastSpam = spamTimestamps[spamKey]
    if (ongoing && lastSpam != null && nowMs - lastSpam < 10_000L) return
    if (ongoing) spamTimestamps[spamKey] = nowMs

    // Ongoing notifications must not pop up on every update: only the first time
    if (ongoing && shown.containsKey(sbn.key)) return

    // The same notification posted again (count update etc.): do not show it twice
    val sig = "$title|$text|${n.`when`}".hashCode()
    // CANCELFIX: user ne yahi message abhi swipe karke hataya tha aur app ne wapas post kar diya:
    // dobara mat dikhao (bubble / pop-up), shade se phir hata do
    if (BubbleStore.wasUserDismissed(sbn.key, sig)) {
        cancelVerified(sbn.key, 1)
        return
    }
    if (shown[sbn.key] == sig) {   // AVATARFIX-repost
        refreshAvatar(sbn)
        return
    }
    shown[sbn.key] = sig

    val app = appLabel(sbn)

    // Lock screen: never show private content, skip secret notifications completely
    val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
    // MUSICKIT: toggle ON = lock screen par chhupao (secret notification bilkul nahi). OFF = dikhao.
    // (Pehle OFF hone par bhi app notification ki "private" setting dekh ke chhupa deta tha, isliye toggle ka asar nahi dikhta tha.)
    if (locked && IslandSettings.concealLocked.value && n.visibility == Notification.VISIBILITY_SECRET) return
    val hide = locked && IslandSettings.concealLocked.value

    // Inline reply (RemoteInput) and "Mark as read" actions, e.g. Messages / WhatsApp / Telegram
    val acts: List<Notification.Action> =
        if (hide) emptyList() else n.actions.orEmpty().toList()
    val reply = acts.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }
    val markReadAct = acts.firstOrNull { a ->
        val t = a.title?.toString()?.lowercase().orEmpty()
        (Build.VERSION.SDK_INT >= 28 &&
            a.semanticAction == Notification.Action.SEMANTIC_ACTION_MARK_AS_READ) ||
            (t.contains("read") && !t.contains("unread")) || t.contains("\u092a\u0922\u093c\u093e") ||
            t == "archive"
    }
    val markRead = markReadAct?.actionIntent

    val iconAppOnly = hide || (locked && IslandSettings.concealChatPics.value)   // BUTTERICON
    // BUTTERICON: bubble + pop-up ek local function me; sender ki photo (content URI) background me load hoti hai
    // DIRECTCARD2 flag: Customize > Behavior toggles (calls pehle hi upar alag handle ho chuki hain)
    val directCard = (IslandSettings.directCardAll.value ||
        (IslandSettings.directCardReply.value && reply != null)) &&
        IslandController.callMode == null
    // UNLOCKFIX: lock screen par chhupaye message ka asli roop. Phone unlock hote hi bubble / card me aa jata hai.
    val realActs: List<Notification.Action> = if (hide) n.actions.orEmpty().toList() else emptyList()
    val realReply = realActs.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }
    val realMarkAct = realActs.firstOrNull { a ->
        val t = a.title?.toString()?.lowercase().orEmpty()
        (Build.VERSION.SDK_INT >= 28 &&
            a.semanticAction == Notification.Action.SEMANTIC_ACTION_MARK_AS_READ) ||
            (t.contains("read") && !t.contains("unread")) || t.contains("\u092a\u0922\u093c\u093e") ||
            t == "archive"
    }
    val unlockFn: ((Bitmap?) -> BubbleItem)? = if (!hide) null else fun(ic: Bitmap?): BubbleItem =
        BubbleItem(
            key = sbn.key, pkg = sbn.packageName, app = app,
            title = title, text = text, icon = ic,
            accent = AppStyles.accentFor(sbn.packageName, n.color.takeIf { it != 0 }?.let { it or (0xFF shl 24) }),
            open = n.contentIntent, reply = realReply, markRead = realMarkAct?.actionIntent,
            markReadLabel = realMarkAct?.title?.toString()?.takeIf { it.isNotBlank() } ?: Isl.markRead,
            actions = buildCardActions(realActs, realReply),
            sig = sig,
            keep = directCard,
        )
    fun deliver(bIcon: Bitmap?) {
    BubbleStore.add(
        BubbleItem(
            key = sbn.key, pkg = sbn.packageName, app = app,
            title = if (hide) app else title,
            text = if (hide) Isl.newNotification else text,   // ISLANDI18N
            icon = bIcon,
            accent = AppStyles.accentFor(sbn.packageName, n.color.takeIf { it != 0 }?.let { it or (0xFF shl 24) }),   // APPSTYLEKIT (PATCH_10_11_12)
            open = n.contentIntent, reply = reply, markRead = markRead,
            markReadLabel = markReadAct?.title?.toString()?.takeIf { it.isNotBlank() } ?: Isl.markRead,   // ISLANDI18N
            actions = buildCardActions(acts, reply),   // CARDACTIONS
            sig = sig,
            keep = directCard,   // DIRECTCARD2
            unlock = unlockFn,   // UNLOCKFIX
        )
    )
    // DIRECTCARD: toggles in Customize > Behavior. Bubble is already added above.
    // Silent / ongoing notifications and active calls keep the normal banner behaviour.
    if (directCard) {   // DIRECTCARD2
        BubbleStore.openDirect(sbn.key)
        return
    }
    IslandController.flash(
        IslandMode.Notification(
            app,
            if (hide) app else title,
            if (hide) Isl.newNotification else text,
            canReply = reply != null,
            canMarkRead = markRead != null,
            markReadLabel = markReadAct?.title?.toString()?.takeIf { it.isNotBlank() } ?: Isl.markRead,   // CARDACTIONS
            accent = AppStyles.accentFor(sbn.packageName, n.color.takeIf { it != 0 }?.let { it or (0xFF shl 24) }),   // APPSTYLEKIT (PATCH_10_11_12)
            icon = bIcon,
        ),
        // Cards with buttons stay a bit longer so there is time to tap them
        durationMs = when {
            reply != null || markRead != null -> 8000L
            quiet || ongoing -> 3000L     // silent / ongoing: shorter pop-up
            else -> 5500L
        },
        open = n.contentIntent,
        pkg = sbn.packageName,
        reply = reply,
        markRead = markRead,
        key = sbn.key,
    )
    }   // BUTTERICON: end of deliver()
    if (!iconAppOnly && hasSlowLargeIcon(sbn)) {
        // BUTTERICON2: pehle turant app icon ke saath dikhao (banner late nahi), sender ki photo aate hi icon badal do
        deliver(appIconCached(sbn.packageName))
        scope.launch {
            val big = kotlinx.coroutines.withTimeoutOrNull(2000L) {
                kotlinx.coroutines.withContext(Dispatchers.IO) { loadLargeIcon(sbn) }
            } ?: return@launch
            if (shown[sbn.key] != sig) return@launch      // notification hat gayi / naya message aa gaya
            BubbleStore.updateIcon(sbn.key, big)
            IslandController.updateFlashIcon(sbn.key, big)
        }
    } else if (appIconCache.get(sbn.packageName) == null) {
        // SMOOTHPATCH: app icon cache me nahi hai: decode main thread par bahut atakta hai.
        // Pop-up turant icon ke bina dikhao, icon IO thread par ban ke aate hi badal do.
        deliver(null)
        scope.launch {
            val ic = kotlinx.coroutines.withTimeoutOrNull(2000L) {
                kotlinx.coroutines.withContext(Dispatchers.IO) { iconFor(sbn, iconAppOnly) }
            } ?: return@launch
            if (shown[sbn.key] != sig) return@launch
            BubbleStore.updateIcon(sbn.key, ic)
            IslandController.updateFlashIcon(sbn.key, ic)
        }
    } else deliver(iconFor(sbn, iconAppOnly))
}

/** AVATARFIX: same message dobara post hua (app ne photo baad me jodi): bubble / card ka icon badal do, message dobara nahi dikhta. */
internal fun IslandListener.refreshAvatar(sbn: StatusBarNotification) {
    // AVATARFIX-once: bubble me pehle se sender ki photo hai to dobara kaam nahi (bekaar recompose / lag nahi)
    val cur = BubbleStore.items.value.firstOrNull { it.key == sbn.key } ?: return
    if (cur.icon != null && cur.icon !== appIconCached(sbn.packageName)) return
    val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
    if (locked && (IslandSettings.concealLocked.value || IslandSettings.concealChatPics.value)) return   // MUSICKIT
    val sig0 = shown[sbn.key]
    scope.launch {
        val big = kotlinx.coroutines.withTimeoutOrNull(2000L) {
            kotlinx.coroutines.withContext(Dispatchers.IO) { loadLargeIcon(sbn) }
        } ?: return@launch
        if (shown[sbn.key] != sig0) return@launch
        BubbleStore.updateIcon(sbn.key, big)
        IslandController.updateFlashIcon(sbn.key, big)
    }
}
