package com.islandify.app.core

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// PATCHKIT15B (DEBUGKIT): chhota local error log. Crash aur update / restore errors yahan likhe jaate hain.
// Kuch bhi network par nahi jaata. Message ya notification ka text kabhi log nahi hota.
// About > Debug info se user ise copy karke bug report me paste kar sakta hai.
object AppLog {
    private const val FILE_NAME = "islandify-log.txt"
    private const val MAX_BYTES = 96 * 1024

    @Volatile private var dir: File? = null
    private var installed = false

    /** Call early (IslandSettings.init does). Also writes uncaught crashes to the log. */
    fun install(ctx: Context) {
        synchronized(this) {
            if (installed) return
            installed = true
            dir = ctx.applicationContext.filesDir
        }
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { write("CRASH", "thread=" + t.name, e) }
            prev?.uncaughtException(t, e)
        }
    }

    fun e(tag: String, msg: String, t: Throwable? = null) = write("E/$tag", msg, t)

    fun w(tag: String, msg: String, t: Throwable? = null) = write("W/$tag", msg, t)

    @Synchronized
    private fun write(level: String, msg: String, t: Throwable?) {
        val d = dir ?: return
        runCatching {
            val f = File(d, FILE_NAME)
            if (f.length() > MAX_BYTES) {
                val keep = f.readText().takeLast(MAX_BYTES / 2)
                f.writeText(keep.substringAfter('\n', keep))
            }
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            f.appendText("$ts $level $msg\n")
            if (t != null) f.appendText(android.util.Log.getStackTraceString(t) + "\n")
        }
    }

    /** Text to paste in a bug report: app version, device, and the end of the error log. */
    fun report(ctx: Context): String {
        val pi = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull()
        val code = pi?.let {
            if (Build.VERSION.SDK_INT >= 28) it.longVersionCode
            else @Suppress("DEPRECATION") it.versionCode.toLong()
        } ?: 0L
        val head = "Islandify ${pi?.versionName ?: "?"} ($code) | ${Build.MANUFACTURER} ${Build.MODEL} | " +
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val f = File(ctx.applicationContext.filesDir, FILE_NAME)
        val tail = runCatching { if (f.exists()) f.readText().takeLast(20_000) else "" }.getOrDefault("")
        return head + "\n\n" + tail.ifBlank { "(no errors logged)" }
    }
}
