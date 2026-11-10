package com.islandify.app.core
import com.islandify.app.R
import androidx.annotation.StringRes

import com.islandify.app.island.*
import com.islandify.app.ui.components.*
import com.islandify.app.ui.onboarding.*
import com.islandify.app.ui.screens.*
import com.islandify.app.ui.theme.*

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow

// SPLITBIG  core: IslandSettings.kt. Types: SettingsTypes.kt, backup: SettingsBackup.kt, resets: SettingsReset.kt

// BACKUPKIT: saari settings ek JSON file me (export) / file se wapas (import)
fun IslandSettings.exportTo(ctx: Context, uri: android.net.Uri): Boolean = runCatching {
    flush()   // SMOOTHPATCH: save() ab debounced hai, export ko latest values chahiye
    val data = org.json.JSONObject()
    prefs?.all?.forEach { (k, v) ->
        val o = org.json.JSONObject()
        when (v) {
            is Boolean -> o.put("t", "b").put("v", v)
            is Int -> o.put("t", "i").put("v", v)
            is Long -> o.put("t", "l").put("v", v)
            is Float -> o.put("t", "f").put("v", v.toDouble())
            is String -> o.put("t", "s").put("v", v)
            is Set<*> -> o.put("t", "ss").put("v", org.json.JSONArray(v.map { it.toString() }))
            else -> return@forEach
        }
        data.put(k, o)
    }
    val root = org.json.JSONObject().put("app", "islandify").put("version", BACKUP_FORMAT_VERSION).put("data", data)
    val out = ctx.contentResolver.openOutputStream(uri, "wt") ?: return@runCatching false
    out.use { it.write(root.toString(2).toByteArray(Charsets.UTF_8)) }
    true
}.getOrDefault(false)

// PATCHKIT15B: import ab file ko check karta hai (size, format version, key / value type, NaN) aur agar nayi
// settings load hi na ho paayein to purani settings automatically wapas rakh deta hai.
fun IslandSettings.importFrom(ctx: Context, uri: android.net.Uri): Boolean = runCatching {
    val bytes = ctx.contentResolver.openInputStream(uri)?.use { readCapped(it, BACKUP_MAX_BYTES) }
        ?: return@runCatching false
    val root = org.json.JSONObject(bytes.toString(Charsets.UTF_8))
    if (root.optString("app") != "islandify") return@runCatching false
    if (root.optInt("version", 1) > BACKUP_FORMAT_VERSION) return@runCatching false   // newer app's backup
    val data = root.getJSONObject("data")
    val p = prefs ?: return@runCatching false
    val before: Map<String, Any?> = HashMap(p.all)   // rollback ke liye snapshot
    cancelPendingSave()   // SMOOTHPATCH: purana pending save import ki values ko overwrite na kare
    val e = p.edit()
    var n = 0
    val keys = data.keys()
    while (keys.hasNext()) {
        val k = keys.next()
        if (k == "onboarded" || k.length > BACKUP_MAX_KEY) continue
        val o = data.optJSONObject(k) ?: continue
        val t = o.optString("t")
        if (!backupTypeMatches(before[k], t)) continue   // key pehle se doosre type ki hai -> chhod do
        when (t) {
            "b" -> e.putBoolean(k, o.getBoolean("v"))
            "i" -> e.putInt(k, o.getInt("v"))
            "l" -> e.putLong(k, o.getLong("v"))
            "f" -> {
                val d = o.getDouble("v")
                if (d.isNaN() || d.isInfinite()) continue
                e.putFloat(k, d.toFloat())
            }
            "s" -> {
                val s = o.getString("v")
                if (s.length > BACKUP_MAX_STRING) continue
                e.putString(k, s)
            }
            "ss" -> {
                val a = o.getJSONArray("v")
                if (a.length() > BACKUP_MAX_SET) continue
                e.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet())
            }
            else -> continue
        }
        n++
    }
    if (n == 0) return@runCatching false
    if (!e.commit()) return@runCatching false
    prefs = null      // init() dobara saari values padhe
    val loaded = runCatching { init(ctx) }
        .onFailure { AppLog.e("backup", "restore: settings could not be loaded, rolling back", it) }
        .isSuccess
    if (!loaded) {
        val r = p.edit().clear()
        for ((k, v) in before) {
            when (v) {
                is Boolean -> r.putBoolean(k, v)
                is Int -> r.putInt(k, v)
                is Long -> r.putLong(k, v)
                is Float -> r.putFloat(k, v)
                is String -> r.putString(k, v)
                is Set<*> -> r.putStringSet(k, v.map { it.toString() }.toSet())
            }
        }
        r.commit()
        prefs = null
        runCatching { init(ctx) }
        return@runCatching false
    }
    true
}.getOrDefault(false)

private fun readCapped(input: java.io.InputStream, max: Int): ByteArray? {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        total += n
        if (total > max) return null
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
