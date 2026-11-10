package com.islandify.app.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

// PATCH_10_11_12 / NUDGEKIT: bade badlav (theme, rang, design, bubble ki jagah, app list...) ke baad
// Customize page par "Backup le lo?" card. Apni alag SharedPreferences file me rehta hai
// ("islandify_nudge"), isliye Backup / Restore / Reset isse nahi chhute.

object BackupNudge {
    private const val NEED_CHANGES = 4                    // itne alag badlav ke baad card aata hai
    private const val SNOOZE_MS = 3L * 24 * 60 * 60 * 1000  // "Later" = 3 din
    private const val MIN_GAP_MS = 12L * 60 * 60 * 1000     // backup ke baad kam se kam 12 ghante shaanti

    /** Card dikhana hai ya nahi. */
    val show = MutableStateFlow(false)
    /** Settings me switch: "Remind me to back up". */
    val enabled = MutableStateFlow(true)

    private var prefs: SharedPreferences? = null
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var ignoreUntil = 0L

    @OptIn(FlowPreview::class)
    fun start(ctx: Context) {
        if (job != null) return
        val p = ctx.applicationContext.getSharedPreferences("islandify_nudge", Context.MODE_PRIVATE)
        prefs = p
        enabled.value = p.getBoolean("on", true)
        refresh()
        job = scope.launch {
            val watched: List<Flow<Any?>> = listOf(
                IslandSettings.theme, IslandSettings.accentSource, IslandSettings.accentSeed,
                IslandSettings.bgStyle, IslandSettings.bgColor,
                IslandSettings.pillStyle, IslandSettings.bannerStyle, IslandSettings.bubbleStyle,
                IslandSettings.cardStyle, IslandSettings.musicStyle, IslandSettings.notchMode,
                IslandSettings.bubbleFreeOn, IslandSettings.bubbleFreeX, IslandSettings.bubbleFreeY,
                IslandSettings.offsetX, IslandSettings.offsetY, IslandSettings.scale,
                IslandSettings.blockedApps, AppStyles.map,
            )
            combine(watched) { it.toList() }
                .distinctUntilChanged()
                .drop(1)               // pehli value = abhi load hui settings
                .debounce(2000)        // slider / drag = ek badlav
                .collect { onBigChange() }
        }
    }

    private fun onBigChange() {
        val p = prefs ?: return
        if (System.currentTimeMillis() < ignoreUntil) return   // restore / reset ke turant baad ki values
        p.edit().putInt("changes", p.getInt("changes", 0) + 1).apply()
        refresh()
    }

    private fun refresh() {
        val p = prefs ?: return
        val now = System.currentTimeMillis()
        show.value = enabled.value &&
            p.getInt("changes", 0) >= NEED_CHANGES &&
            now >= p.getLong("snooze", 0L) &&
            now - p.getLong("last", 0L) >= MIN_GAP_MS
    }

    /** Backup file save ho gayi. */
    fun markBackedUp() {
        prefs?.edit()?.putLong("last", System.currentTimeMillis())?.putInt("changes", 0)?.apply()
        refresh()
    }

    /** "Later" = kuch din chup. */
    fun later() {
        prefs?.edit()?.putLong("snooze", System.currentTimeMillis() + SNOOZE_MS)?.putInt("changes", 0)?.apply()
        refresh()
    }

    /** Restore ke baad: nayi values ko "badlav" mat ginna. */
    fun reset() {
        ignoreUntil = System.currentTimeMillis() + 5000L
        prefs?.edit()?.putInt("changes", 0)?.apply()
        refresh()
    }

    fun setEnabled(on: Boolean) {
        enabled.value = on
        prefs?.edit()?.putBoolean("on", on)?.apply()
        refresh()
    }
}
