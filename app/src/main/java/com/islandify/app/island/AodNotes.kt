package com.islandify.app.island

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One notification line on the Always-On screen. */
data class AodNote(
    val key: String,
    val app: String,
    val title: String,
    val text: String,
)

/**
 * The latest notifications, newest first, for the AOD screen.
 *
 * The island banner disappears after a few seconds, but an Always-On screen must keep showing
 * what is waiting for you until the phone is unlocked. The listener adds every notification here
 * and a dismiss removes it again; unlocking clears the list (they were seen by then).
 *
 * Titles and texts are stored as they came in and are masked by [AodScreen] when the user did
 * not allow reading notifications on the lock screen. Secret notifications never get here.
 */
object AodNotes {
    private const val MAX = 6

    private val map = LinkedHashMap<String, AodNote>()
    private val _notes = MutableStateFlow<List<AodNote>>(emptyList())
    val notes = _notes.asStateFlow()

    @Synchronized
    fun put(n: AodNote) {
        if (n.title.isBlank() && n.text.isBlank()) return
        map.remove(n.key)
        map[n.key] = n
        while (map.size > MAX) map.remove(map.keys.first())
        publish()
    }

    @Synchronized
    fun remove(key: String) {
        if (map.remove(key) != null) publish()
    }

    @Synchronized
    fun clear() {
        if (map.isEmpty()) return
        map.clear()
        publish()
    }

    private fun publish() {
        _notes.value = map.values.reversed()   // newest first
    }
}
