package com.islandify.app.core

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * In-app language choice: "" = follow the phone, "en" = English, "hi" = Hindi.
 * Stored on its own so it can be read in attachBaseContext, before IslandSettings is initialised.
 */
object AppLanguage {
    const val SYSTEM = ""
    const val ENGLISH = "en"
    const val HINDI = "hi"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("islandify", Context.MODE_PRIVATE)

    fun saved(ctx: Context): String = prefs(ctx).getString("lang", SYSTEM) ?: SYSTEM

    fun set(ctx: Context, code: String) {
        prefs(ctx).edit().putString("lang", code).apply()
    }

    /** Wraps [base] so every string resource resolves in the chosen language. */
    fun wrap(base: Context): Context {
        val code = prefs(base).getString("lang", SYSTEM) ?: SYSTEM
        if (code.isEmpty()) return base
        val locale = Locale.forLanguageTag(code)
        Locale.setDefault(locale)
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(locale)
        return base.createConfigurationContext(cfg)
    }
}
