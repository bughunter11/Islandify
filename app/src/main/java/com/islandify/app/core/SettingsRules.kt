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

// SPLITSET  Pill > Behavior: kab dikhe (visibility, quiet hours) + lock screen privacy.
// Flows IslandSettings object me hi hain (poore app me IslandSettings.x se chalte hain). Yahan sirf load / save.

internal fun IslandSettings.loadRules(p: SharedPreferences) {
    showWhen.value = p.getInt("showWhen", 2)
    launcherOnly.value = p.getBoolean("launcherOnly", false)
    hideInOwnerApp.value = p.getBoolean("hideOwner", false)
    dismissOnOutside.value = p.getBoolean("outsideDismiss", false)
    hideLandscape.value = p.getBoolean("hideLand", false)
    quietOn.value = p.getBoolean("quietOn", false)
    quietFrom.value = p.getInt("quietFrom", 23 * 60)
    quietTo.value = p.getInt("quietTo", 7 * 60)
    hideFullscreen.value = p.getBoolean("hideFs", false)
    concealLocked.value = p.getBoolean("concealLock2", true)    // MUSICKIT: default ON (nayi key)
    concealChatPics.value = p.getBoolean("concealPics2", true)
}

internal fun IslandSettings.saveRules(e: SharedPreferences.Editor) {
    e.putInt("showWhen", showWhen.value)
    e.putBoolean("launcherOnly", launcherOnly.value)
    e.putBoolean("hideOwner", hideInOwnerApp.value)
    e.putBoolean("outsideDismiss", dismissOnOutside.value)
    e.putBoolean("hideLand", hideLandscape.value)
    e.putBoolean("quietOn", quietOn.value)
    e.putInt("quietFrom", quietFrom.value)
    e.putInt("quietTo", quietTo.value)
    e.putBoolean("hideFs", hideFullscreen.value)
    e.putBoolean("concealLock2", concealLocked.value)
    e.putBoolean("concealPics2", concealChatPics.value)
}
