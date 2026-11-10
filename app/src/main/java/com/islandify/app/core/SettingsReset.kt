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

fun IslandSettings.resetKinds() {
    kindStyles.value = emptyMap()
    save()
}

fun IslandSettings.resetLook() {
    corner.value = 1f
    animSpeed.value = 1f
    glow.value = true
    haptics.value = true
    save()
}

fun IslandSettings.reset() {
    scale.value = 1f
    widthScale.value = 1f
    heightScale.value = 1f
    offsetX.value = 0f
    offsetY.value = 10f
    save()
}

fun IslandSettings.resetBubbleCard() {
    bubbleSize.value = 32f; bubbleGap.value = 2f; bubbleY.value = 0f; bubbleAlpha.value = 1f
    bubbleSwipeOn.value = true; bubbleSwipeDist.value = 56f
    bubbleMove.value = true; bubbleFreeOn.value = false   // BUBBLEMOVE
    cardWidth.value = 0.92f; cardCorner.value = 30f; cardTextScale.value = 1f; cardAlpha.value = 1f
    cardAccent.value = 0.45f; cardMaxLines.value = 10;   /* CARDGROW */  cardButtons.value = true; cardRow.value = true
    cardScrollSpeed.value = 1f   // CARDSPEED
    cardUpDist.value = 40f; cardSideDist.value = 64f   // DEADCLEAN
    bannerW.value = 1f; bannerH.value = 1f; bannerCorner.value = 0f; bannerText.value = 1f
    bannerAlpha.value = 1f; bannerAccent.value = 1f; bannerSub.value = true; bannerBadge.value = true   // BANNERKIT
    bannerStyle.value = 0; bubbleStyle.value = 0; cardStyle.value = 0   // STYLEKIT
    bannerY.value = 0f; cardY.value = 0f   // BANNERY / CARDY reset
    bubbleEnabled.value = true; bubbleOverlap.value = true; bubbleExtras.value = true; privacyBanners.value = true; appleMusic.value = true   // BUBBLELIVE / EXTRAKIT / APPLEMUSIC reset
    musicStyle.value = 0; musicAlpha.value = 1f; musicAccent.value = 1f; musicCorner.value = 0f   // MUSICKIT
    musicArtSize.value = 1f; musicArtCorner.value = 14f; musicWave.value = true; waveSpeed.value = 1f; waveFollow.value = true   // MUSICKIT / WAVESYNC
    musicAuto.value = true; musicPillAuto.value = true   // MUSICAUTO
    musicX.value = 0f; musicY.value = 0f   // MUSICPOS
}

/** Island + look + behaviour + colors + theme. (Triggers and blocked apps are kept.) */
fun IslandSettings.resetAll() {
    resetBubbleCard()
    reset()
    resetKinds()
    resetLook()
    pillStyle.value = 0   // STYLEKIT
    notchMode.value = false   // NOTCHMODE
    pillAlpha.value = 1f; pillAccent.value = 1f   // STYLEKIT2
    autoCollapseSec.value = 6
    swipeSkip.value = true
    stickyNotif.value = false
    swipeUp.value = SwipeAction.Dismiss
    swipeDown.value = SwipeAction.None
    swipeLeft.value = SwipeAction.NextTrack
    outlineOn.value = true
    outlineThickness.value = 0.6f
    outlineDynamic.value = true
    outlineHideExpanded.value = false
    pulseShadow.value = false
    pulseDurationMs.value = 1450
    expandedCorner.value = 0f
    fontScale.value = 1f
    showWhen.value = 2
    clearOnDismiss.value = true
    directCardAll.value = false
    directCardReply.value = false
    mediaPeek.value = false
    mediaKeepPaused.value = false
    launcherOnly.value = false
    hideInOwnerApp.value = false
    briefCalendar.value = false
    briefWeather.value = false; weatherF.value = false   // WEATHERKIT
    calHours.value = 24; calAccounts.value = emptySet()   // CALKIT
    idleInfo.value = false
    idleBatteryOnlyLow.value = false
    idleAlarmHours.value = 0
    flashlightPill.value = false
    dismissOnOutside.value = false
    hideLandscape.value = false
    quietOn.value = false
    quietFrom.value = 23 * 60
    quietTo.value = 7 * 60
    hideFullscreen.value = false
    concealLocked.value = true    // MUSICKIT: default ON
    concealChatPics.value = true
    baseOrder.value = listOf("timer", "live", "download", "media")
    swipeRight.value = SwipeAction.PrevTrack
    idlePill.value = false
    islandAppAccent.value = false
    theme.value = 0
    accentSource.value = 0
    accentSeed.value = 0xFF7B3DF5.toInt()
    bgStyle.value = 0
    bgColor.value = 0xFF14121C.toInt()
    Motion.mode.value = 0   // MOTIONKIT (PATCH_10_11_12): per-app styles aur blocked apps pehle ki tarah bache rehte hain
    save()
}
