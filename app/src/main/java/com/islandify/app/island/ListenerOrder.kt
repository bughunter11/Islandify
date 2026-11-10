package com.islandify.app.island

import android.app.Notification
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// MSGORDER: do alag chats ke message ~300 ms ke andar galat order me aa jaate the.
// Android alag apps ki notifications alag threads par process karta hai, isliye listener tak
// pahunchne ka order post hone ke order se mil sakta hai. Chat notifications ab ORDER_HOLD_MS tak
// ek chhote buffer me ruki rehti hain aur sbn.postTime ke hisaab se sort hoke deliver hoti hain
// (bubble aur banner dono isi order me bante hain). Calls / timer / music / downloads par koi
// delay nahi: unka raasta pehle jaisa hai.

/** Kitni der tak chat notifications ko sort ke liye rokte hain. Kam = tez banner, zyada = pakka order. */
internal const val ORDER_HOLD_MS = 150L

/** Sirf asli chat / message notifications buffer hoti hain (ongoing, group summary, calls nahi). */
internal fun IslandListener.isChatLike(sbn: StatusBarNotification): Boolean {
    if (sbn.packageName == packageName) return false
    val n = sbn.notification ?: return false
    if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
    if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return false
    if (isCall(sbn)) return false
    return n.category == Notification.CATEGORY_MESSAGE ||
        n.extras?.containsKey(Notification.EXTRA_MESSAGES) == true
}

internal fun IslandListener.bufferPosted(sbn: StatusBarNotification) {
    postBuf.removeAll { it.key == sbn.key }   // same notification dobara aayi: naya wala rakho
    postBuf.add(sbn)
    if (postFlushJob?.isActive == true) return
    postFlushJob = scope.launch {
        delay(ORDER_HOLD_MS)
        flushPosted()
    }
}

internal fun IslandListener.flushPosted() {
    if (postBuf.isEmpty()) return
    val batch = postBuf.sortedBy { it.postTime }   // stable sort: same postTime = aane ka order
    postBuf.clear()
    batch.forEach { sbn ->
        runCatching { handlePosted(sbn) }
            .onFailure { android.util.Log.w("Islandify", "notification skipped: ${sbn.packageName}", it) }
    }
}

/** Notification buffer me hi hata di gayi (deliver hone se pehle): use deliver mat karo. */
internal fun IslandListener.dropBuffered(key: String) {
    postBuf.removeAll { it.key == key }
}

internal fun IslandListener.clearBuffered() {
    postFlushJob?.cancel()
    postBuf.clear()
}
