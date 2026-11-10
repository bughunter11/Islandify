package com.islandify.app.core

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

// PATCH_10_11_12 / APPSTYLEKIT: har app ka apna accent rang + show time (Apps tab > tune icon).
// Ek JSON string ("appStyles") me save hoti hai, isliye Backup / Restore ke saath apne aap chalti hai.

data class AppStyle(
    val accent: Int? = null,     // ARGB, null = notification ka apna rang
    val durationSec: Int = 0,    // 0 = default time
) {
    val isDefault: Boolean get() = accent == null && durationSec <= 0
}

object AppStyles {
    val map = MutableStateFlow<Map<String, AppStyle>>(emptyMap())

    fun load(p: SharedPreferences) {
        map.value = parse(p.getString("appStyles", null))
    }

    fun save(e: SharedPreferences.Editor) {
        e.putString("appStyles", encode(map.value))
    }

    fun get(pkg: String): AppStyle? = map.value[pkg]

    /** Default style = entry hata do. */
    fun set(pkg: String, style: AppStyle) {
        map.value = if (style.isDefault) map.value - pkg else map.value + (pkg to style)
        IslandSettings.save()
    }

    fun clear(pkg: String) = set(pkg, AppStyle())

    /** Per-app accent, ya [fallback] (notification ka apna rang). */
    fun accentFor(pkg: String, fallback: Int?): Int? = map.value[pkg]?.accent ?: fallback

    /** Per-app show time in ms, ya null = default. */
    fun durationMs(pkg: String): Long? =
        map.value[pkg]?.durationSec?.takeIf { it > 0 }?.let { it * 1000L }

    internal fun encode(m: Map<String, AppStyle>): String {
        val root = JSONObject()
        for ((k, v) in m) {
            if (v.isDefault) continue
            val o = JSONObject()
            v.accent?.let { o.put("a", it) }
            if (v.durationSec > 0) o.put("d", v.durationSec)
            root.put(k, o)
        }
        return root.toString()
    }

    internal fun parse(raw: String?): Map<String, AppStyle> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            val out = LinkedHashMap<String, AppStyle>()
            val keys = root.keys()
            while (keys.hasNext() && out.size < 500) {
                val k = keys.next()
                if (k.length > 200) continue
                val o = root.optJSONObject(k) ?: continue
                val accent: Int? = if (o.has("a")) o.optInt("a") else null
                val st = AppStyle(accent, o.optInt("d", 0).coerceIn(0, 60))
                if (!st.isDefault) out[k] = st
            }
            out
        }.getOrDefault(emptyMap())
    }
}
