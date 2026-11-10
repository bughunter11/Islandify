package com.islandify.app.island

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow

/** Lock / screen state, read by the island window for the "Show island when" setting. */
object IslandVisibility {
    val locked = MutableStateFlow(false)
    val screenOn = MutableStateFlow(true)

    /** Package of the app that last opened a window (keyboard, system UI and Islandify itself are ignored). */
    val foreground = MutableStateFlow<String?>(null)

    fun refresh(ctx: Context) {
        locked.value = runCatching {
            ctx.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        }.getOrDefault(false)
        screenOn.value = runCatching {
            ctx.getSystemService(PowerManager::class.java)?.isInteractive != false
        }.getOrDefault(true)
    }
}
