package com.islandify.app.island

import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import java.net.HttpURLConnection
import java.net.URL

// WEATHERKIT + CALLIST  (idle card: mausam + agle calendar events.)

/** Ek calendar event (idle card ki list ke liye). */
internal data class CalEvent(val title: String, val begin: Long, val end: Long)

/** Open-Meteo se current temperature. Key nahi chahiye. 30 min cache. IO thread par chalao. */
internal object WeatherKit {
    private var cacheAt = 0L
    private var cacheF = false
    private var cacheText: String? = null

    fun text(ctx: Context, fahrenheit: Boolean): String? = runCatching {
        val now = System.currentTimeMillis()
        if (cacheText != null && cacheF == fahrenheit && now - cacheAt < 30 * 60_000L) return@runCatching cacheText
        val granted = ContextCompat.checkSelfPermission(ctx, "android.permission.ACCESS_COARSE_LOCATION") ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return@runCatching null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return@runCatching cacheText
        val loc = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { pr -> runCatching { lm.getLastKnownLocation(pr) }.getOrNull() }
            .maxByOrNull { it.time } ?: return@runCatching cacheText   // koi location nahi: purana dikhao
        val unit = if (fahrenheit) "fahrenheit" else "celsius"
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=${"%.2f".format(java.util.Locale.US, loc.latitude)}" +
                "&longitude=${"%.2f".format(java.util.Locale.US, loc.longitude)}" +
                "&current=temperature_2m,weather_code&temperature_unit=$unit"
        )
        val con = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000; readTimeout = 5000
        }
        try {
            val body = con.inputStream.bufferedReader().use { it.readText() }
            val cur = org.json.JSONObject(body).getJSONObject("current")
            val temp = Math.round(cur.getDouble("temperature_2m")).toInt()
            val icon = codeIcon(cur.getInt("weather_code"))
            val out = "$icon $temp\u00B0" + if (fahrenheit) "F" else "C"
            cacheText = out; cacheAt = now; cacheF = fahrenheit
            out
        } finally {
            con.disconnect()
        }
    }.getOrDefault(cacheText)

    private fun codeIcon(c: Int): String = when (c) {
        0 -> "\u2600\uFE0F"                       // clear
        1, 2 -> "\u26C5"                           // partly cloudy
        3 -> "\u2601\uFE0F"                       // overcast
        45, 48 -> "\uD83C\uDF2B\uFE0F"           // fog
        in 51..57 -> "\uD83C\uDF26\uFE0F"        // drizzle
        in 61..67, in 80..82 -> "\uD83C\uDF27\uFE0F"   // rain
        in 71..77, 85, 86 -> "\u2744\uFE0F"       // snow
        in 95..99 -> "\u26C8\uFE0F"               // thunderstorm
        else -> "\uD83C\uDF21\uFE0F"
    }
}

/** Agle [max] calendar events (CALKIT ke hours + accounts ke hisaab se). Needs READ_CALENDAR. IO thread. */
internal fun upcomingEvents(ctx: Context, max: Int): List<CalEvent> = runCatching {
    val granted = ContextCompat.checkSelfPermission(ctx, "android.permission.READ_CALENDAR") ==
        PackageManager.PERMISSION_GRANTED
    if (!granted) return@runCatching emptyList<CalEvent>()
    val now = System.currentTimeMillis()
    val hrs = com.islandify.app.core.IslandSettings.calHours.value.coerceIn(1, 24)
    val uri = android.provider.CalendarContract.Instances.CONTENT_URI.buildUpon().also {
        android.content.ContentUris.appendId(it, now)
        android.content.ContentUris.appendId(it, now + hrs * 3600_000L)
    }.build()
    val cols = arrayOf(
        android.provider.CalendarContract.Instances.TITLE,
        android.provider.CalendarContract.Instances.BEGIN,
        android.provider.CalendarContract.Instances.END,
        android.provider.CalendarContract.Instances.ALL_DAY,
        android.provider.CalendarContract.Calendars.ACCOUNT_NAME,
    )
    val accFilter = com.islandify.app.core.IslandSettings.calAccounts.value
    val out = ArrayList<CalEvent>()
    ctx.contentResolver.query(uri, cols, null, null, android.provider.CalendarContract.Instances.BEGIN + " ASC")?.use { c ->
        while (c.moveToNext() && out.size < max) {
            if (c.getInt(3) == 1) continue
            val end = c.getLong(2)
            if (end < now) continue
            if (accFilter.isNotEmpty() && (c.getString(4) ?: "") !in accFilter) continue
            val title = c.getString(0)?.takeIf { it.isNotBlank() } ?: continue
            out += CalEvent(title, c.getLong(1), end)
        }
    }
    out
}.getOrDefault(emptyList())
