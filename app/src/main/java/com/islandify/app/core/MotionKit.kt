package com.islandify.app.core

import android.content.Context
import android.content.SharedPreferences
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow

// PATCH_10_11_12 / MOTIONKIT: "Reduce motion".
//   mode 0 = system ki "Remove animations" setting maano (default)
//   mode 1 = hamesha kam motion
// Island ke springs, enter / exit, pulse, waveform, seek wave, bubble pop, card appear isse band hote hain.

object Motion {
    val mode = MutableStateFlow(0)
    /** true = phone ki Developer / Accessibility setting me animation scale 0 hai. */
    val systemReduced = MutableStateFlow(false)

    @Volatile private var started = false

    fun load(p: SharedPreferences) {
        mode.value = p.getInt("motionMode", 0).coerceIn(0, 1)
    }

    fun save(e: SharedPreferences.Editor) {
        e.putInt("motionMode", mode.value)
    }

    private fun readSystem(ctx: Context): Boolean = runCatching {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

    /** Ek baar (IslandSettings.init se): system setting padho aur badalne par update karo. */
    fun start(ctx: Context) {
        if (started) return
        started = true
        val app = ctx.applicationContext
        systemReduced.value = readSystem(app)
        runCatching {
            app.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        systemReduced.value = readSystem(app)
                    }
                }
            )
        }
    }

    fun isReduced(): Boolean = mode.value == 1 || systemReduced.value
}

/** true = animations kam / band karo. Compose me recompose hota hai jab setting badle. */
@Composable
fun rememberReduceMotion(): Boolean {
    val m by Motion.mode.collectAsState()
    val sys by Motion.systemReduced.collectAsState()
    return m == 1 || sys
}
