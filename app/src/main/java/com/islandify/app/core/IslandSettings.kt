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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow

// SPLITSET  load / save per page: Settings{Pill,Rules,Banner,Bubble,Card,App}.kt
// SPLITBIG  core: IslandSettings.kt. Types: SettingsTypes.kt, backup: SettingsBackup.kt, resets: SettingsReset.kt

object IslandSettings {
    internal var prefs: SharedPreferences? = null   // SPLITBIG: SettingsBackup.kt bhi padhta hai

    val scale = MutableStateFlow(1f)        // 0.5x .. 1.8x
    val widthScale = MutableStateFlow(1f)   // 0.6x .. 1.6x
    val heightScale = MutableStateFlow(1f)  // 0.7x .. 1.6x
    val offsetX = MutableStateFlow(0f)      // dp, left (-) / right (+) of center
    val offsetY = MutableStateFlow(10f)     // dp, down from the top of the screen

    // Every quick pop-up banner type has its own size / text / roundness / show time
    val kindStyles = MutableStateFlow<Map<String, KindStyle>>(emptyMap())

    val corner = MutableStateFlow(1f)       // 0.2 .. 1
    val animSpeed = MutableStateFlow(1f)    // 0.5 .. 2
    val glow = MutableStateFlow(true)       // album art / accent glow
    val haptics = MutableStateFlow(true)

    val triggers = MutableStateFlow(Trigger.entries.map { it.key }.toSet())
    val blockedApps = MutableStateFlow<Set<String>>(emptySet())

    val theme = MutableStateFlow(0)          // 0 system, 1 light, 2 dark, 3 amoled
    // Colors (whole app) + island behaviour
    val accentSource = MutableStateFlow(0)                       // 0 wallpaper (Material You), 1 custom seed color
    val accentSeed = MutableStateFlow(0xFF7B3DF5.toInt())        // seed used when accentSource == 1
    val bgStyle = MutableStateFlow(0)                            // 0 default, 1 tinted by accent, 2 custom color
    val bgColor = MutableStateFlow(0xFF14121C.toInt())           // used when bgStyle == 2
    val islandAppAccent = MutableStateFlow(false)                // island glow uses the app accent instead of album art
    val autoCollapseSec = MutableStateFlow(6)                    // expanded card collapses by itself (0 = never)
    val idlePill = MutableStateFlow(false)                        // small pill stays on screen when idle (tap = time / date / battery)
    // ---- Essentials-style island options ----
    val outlineOn = MutableStateFlow(true)             // thin border around the island
    val outlineThickness = MutableStateFlow(0.6f)      // dp, 0.3 .. 4
    val outlineDynamic = MutableStateFlow(true)        // border follows the activity color
    val outlineHideExpanded = MutableStateFlow(false)  // outline only the small pill / banner
    val pulseShadow = MutableStateFlow(false)          // border pulses when a pop-up arrives
    val pulseDurationMs = MutableStateFlow(1450)       // 400 .. 3000
    val expandedCorner = MutableStateFlow(0f)          // dp for cards, 0 = follow roundness
    val fontScale = MutableStateFlow(1f)               // text size of the whole island
    val launcherOnly = MutableStateFlow(false)         // island only on the home screen (calls always show)
    val hideInOwnerApp = MutableStateFlow(false)       // hide while the app that owns the activity is open
    val briefCalendar = MutableStateFlow(false)        // next calendar event on the idle card
    val briefWeather = MutableStateFlow(false)         // WEATHERKIT: idle card par mausam
    val weatherF = MutableStateFlow(false)             // WEATHERKIT: Fahrenheit
    val calHours = MutableStateFlow(24)                // CALKIT: sirf agle N ghante ka event dikhao (1..24)
    val calAccounts = MutableStateFlow<Set<String>>(emptySet())   // CALKIT: chune hue accounts; khali = sab
    // ---- Added from the Essentials island ----
    val idleInfo = MutableStateFlow(false)             // time (left) + battery (right) on the small idle pill
    val idleBatteryOnlyLow = MutableStateFlow(false)   // battery only when it is 30% or less, or charging
    val idleAlarmHours = MutableStateFlow(0)           // next alarm shown on the idle pill when it rings within N hours (0 = off)
    val flashlightPill = MutableStateFlow(false)       // pill stays on while the flashlight is on (card has a brightness slider)
    val dismissOnOutside = MutableStateFlow(false)     // tap outside an open card closes it
    val quietOn = MutableStateFlow(false)               // STATUSKIT: quiet hours
    val quietFrom = MutableStateFlow(23 * 60)           // minutes after midnight
    val quietTo = MutableStateFlow(7 * 60)
    val hideFullscreen = MutableStateFlow(false)        // hide in games and video apps
    val hideLandscape = MutableStateFlow(false)        // no island in landscape (calls still show)
    val concealLocked = MutableStateFlow(true)         // lock screen: always hide sender and text   // MUSICKIT: default ON
    val concealChatPics = MutableStateFlow(true)       // lock screen: app icon instead of the sender photo   // MUSICKIT: default ON
    val baseOrder = MutableStateFlow(listOf("timer", "live", "download", "media"))   // top = shown first

    /** Keeps only known keys, drops duplicates, appends anything missing. */
    fun normalizeBaseOrder(saved: List<String>?): List<String> {
        val all = listOf("timer", "live", "download", "media")
        return (saved.orEmpty().filter { it in all } + all).distinct()
    }

    val directCardAll = MutableStateFlow(false)        // every message opens straight as the message card
    val directCardReply = MutableStateFlow(false)      // only messages with a Reply option open straight as the card
    val clearOnDismiss = MutableStateFlow(true)        // swiping a notification away also clears it from the shade
    val mediaPeek = MutableStateFlow(false)            // music card opens when the next song starts
    val mediaKeepPaused = MutableStateFlow(false)      // music pill stays after pause (no 10 s hide)
    val showWhen = MutableStateFlow(2)                 // 0 unlocked, 1 screen on, 2 always
    val swipeSkip = MutableStateFlow(true)                       // swipe left/right on the island to skip tracks
    val stickyNotif = MutableStateFlow(false)                    // notifications stay on the island until tapped / swiped away

    // ---- BUBBLEKIT: bubble (status-bar circle) ----
    val bubbleSize = MutableStateFlow(32f)        // dp, 24..56
    val bubbleGap = MutableStateFlow(2f)          // dp gap from the pill
    val bubbleY = MutableStateFlow(0f)            // dp up / down nudge
    val bubbleAlpha = MutableStateFlow(1f)        // background opacity
    val bubbleSwipeOn = MutableStateFlow(true)    // swipe the bubble sideways to clear
    val bubbleSwipeDist = MutableStateFlow(56f)   // dp needed to count as a swipe
    // ---- BUBBLEKIT: message card (opens when the bubble is tapped) ----
    val cardWidth = MutableStateFlow(0.92f)       // share of the screen width
    val cardCorner = MutableStateFlow(30f)        // dp
    val cardTextScale = MutableStateFlow(1f)
    val cardAlpha = MutableStateFlow(1f)
    val cardAccent = MutableStateFlow(0.45f)      // strength of the accent gradient at the bottom (0 = none)
    val cardMaxLines = MutableStateFlow(10)   // CARDGROW
    val cardScrollSpeed = MutableStateFlow(1f)    // CARDSPEED: text auto-scroll speed, 1 = normal
    val cardButtons = MutableStateFlow(true)      // Mark as read / Open / Reply row
    val cardRow = MutableStateFlow(true)          // row of bubbles under the card
    // DEADCLEAN: cardUpAct / cardSideAct hata diye (swipe rules fixed: SWIPERULE)
    val cardUpDist = MutableStateFlow(40f)        // dp
    val cardSideDist = MutableStateFlow(64f)      // dp
    // ---- BANNERKIT: pop-up banner (Customize > Banner) ----
    val bannerW = MutableStateFlow(1f)            // width multiplier
    val bannerH = MutableStateFlow(1f)            // height multiplier
    val bannerCorner = MutableStateFlow(0f)       // < 0.2 = follow the island roundness
    val bannerText = MutableStateFlow(1f)
    val bannerAlpha = MutableStateFlow(1f)        // background opacity
    val bannerAccent = MutableStateFlow(1f)       // strength of the accent glow (0 = none)
    val bannerY = MutableStateFlow(0f)            // BANNERY: banner up / down (dp, Customize > Banner)
    val pillStyle = MutableStateFlow(0)           // STYLEKIT: pill / island design (0..5)
    val notchMode = MutableStateFlow(false)       // NOTCHMODE: island upar ke edge se juda (notch jaisa)
    val pillAlpha = MutableStateFlow(1f)          // STYLEKIT2: pill opacity
    val pillAccent = MutableStateFlow(1f)         // STYLEKIT2: pill accent glow strength
    val bannerStyle = MutableStateFlow(0)         // STYLEKIT
    val bubbleStyle = MutableStateFlow(0)         // STYLEKIT
    val cardStyle = MutableStateFlow(0)           // STYLEKIT
    val cardY = MutableStateFlow(0f)              // CARDY: message card up / down (dp, Customize > Message card)
    // ---- MUSICKIT: music card (pill pe click karne par khulta hai) ----
    val musicStyle = MutableStateFlow(0)          // 0..11 (naam Message card jaise)
    val musicPlayerLayout = MutableStateFlow(0)   // MUSICLAYOUT: 0=Classic, 1=Expressive, 2=Glow, 3=Spectrum
    val seekDesign = MutableStateFlow(0)          // SEEKDESIGN: 0=Waves, 1=Neon Pulse, 2=Aurora
    val seekCustomColor = MutableStateFlow(0xFF22D3EE.toInt())   // SEEKDESIGN: user ka rang (mode 3)
    val seekTimeTint = MutableStateFlow(false)    // SEEKDESIGN: time ka rang bar jaisa
    val seekBarColorMode = MutableStateFlow(0)    // WAVYCOLOR: 0=Solid, 1=Gradient, 2=Album
    val musicAlpha = MutableStateFlow(1f)         // background opacity
    val musicAccent = MutableStateFlow(1f)        // album art ke rang ki glow
    val musicCorner = MutableStateFlow(0f)        // < 8 = island ki roundness jaisa
    val musicArtSize = MutableStateFlow(1f)       // album art ka size
    val musicArtCorner = MutableStateFlow(14f)    // album art ke kone (dp)
    val musicWave = MutableStateFlow(true)        // gaane ke naam ke paas waveform
    val waveSpeed = MutableStateFlow(1f)          // WAVESYNC: waveform speed multiplier (0.25 .. 3)
    val waveFollow = MutableStateFlow(true)       // WAVESYNC: waveform follows the player's playback speed
    val musicAuto = MutableStateFlow(true)        // MUSICAUTO: music card = gaane ke rang ka
    val musicPillAuto = MutableStateFlow(true)    // MUSICAUTO: music pill = gaane ke rang ka
    val musicX = MutableStateFlow(0f)             // MUSICPOS: music card left (-) / right (+) (dp, main island position ke upar)
    val musicY = MutableStateFlow(0f)             // MUSICPOS: music card up (-) / down (+) (dp, main island position ke upar)
    val bubbleEnabled = MutableStateFlow(true)    // BUBBLETOGGLE: master switch, off = saare bubbles hide
    val bubbleOverlap = MutableStateFlow(true)   // OVERLAPKIT / REVIVEKIT: ab switch se on / off (Customize > Bubble)
    val bubbleExtras = MutableStateFlow(true)     // BUBBLELIVE: music / Clock timer / downloads bubble me bhi
    val bubbleMove = MutableStateFlow(true)       // BUBBLEMOVE: long-press karke bubble khiskao (GitHub issue #7)
    val bubbleFreeOn = MutableStateFlow(false)    // BUBBLEMOVE: true = bubble apni khud ki jagah par (pill ke saath nahi)
    val bubbleFreeX = MutableStateFlow(0f)        // BUBBLEMOVE: bubble window ka left (dp, screen ke left se)
    val bubbleFreeY = MutableStateFlow(0f)        // BUBBLEMOVE: bubble window ka top (dp, screen ke top se)
    val privacyBanners = MutableStateFlow(true)   // EXTRAKIT: camera / mic / screen-record banners
    val appleMusic = MutableStateFlow(true)       // APPLEMUSIC: Apple jaisa music card
    val bannerSub = MutableStateFlow(true)        // second line (message text / detail)
    val bannerBadge = MutableStateFlow(true)      // "+N" / dot at the end of a message banner
    val swipeUp = MutableStateFlow(SwipeAction.Dismiss)
    val swipeDown = MutableStateFlow(SwipeAction.None)
    val swipeLeft = MutableStateFlow(SwipeAction.NextTrack)
    val swipeRight = MutableStateFlow(SwipeAction.PrevTrack)

    fun swipeFlow(d: SwipeDir) = when (d) {
        SwipeDir.Up -> swipeUp
        SwipeDir.Down -> swipeDown
        SwipeDir.Left -> swipeLeft
        SwipeDir.Right -> swipeRight
    }

    val onboarded = MutableStateFlow(false)  // has the setup screen been completed once?
    val enabled = MutableStateFlow(true)     // has the user left the island switched on?

    fun init(ctx: Context) {
        AppLog.install(ctx)   // DEBUGKIT
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("islandify", Context.MODE_PRIVATE)
        prefs = p
        // SPLITSET: har page ki settings apni file me (SettingsPill / Rules / Banner / Bubble / Card / App .kt)
        loadPill(p)
        loadRules(p)
        loadBanner(p)
        loadBubble(p)
        loadCard(p)
        loadApp(p)
        Motion.start(ctx)         // MOTIONKIT (PATCH_10_11_12)
        BackupNudge.start(ctx)    // NUDGEKIT (PATCH_10_11_12)
    }

    // SMOOTHPATCH: save() ab turant main thread par ~150 keys nahi likhta. Kai calls (slider, switch, chips)
    // ek hi write me mil jaati hain, aur write background thread par hota hai.
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var saveJob: Job? = null
    private val saveLock = Any()

    fun save() {
        if (prefs == null) return
        saveJob?.cancel()
        saveJob = saveScope.launch {
            delay(150)
            saveNow()
        }
    }

    /** Pending save cancel karke abhi likh do (export, app band hone par). */
    fun flush() {
        saveJob?.cancel()
        saveJob = null
        saveNow()
    }

    fun cancelPendingSave() {
        saveJob?.cancel()
        saveJob = null
    }

    private fun saveNow() {
        synchronized(saveLock) {
            val e = prefs?.edit() ?: return
            savePill(e)
            saveRules(e)
            saveBanner(e)
            saveBubble(e)
            saveCard(e)
            saveApp(e)
            e.apply()
        }
    }

    fun kindStyle(t: IslandKind): KindStyle = kindStyles.value[t.key] ?: KindStyle()

    fun setKindStyle(t: IslandKind, style: KindStyle) {
        kindStyles.value = kindStyles.value + (t.key to style)
    }

    /** Custom "show time" for this pop-up, or null = use the app's default time. */
    fun kindDurationMs(m: IslandMode): Long? =
        kindOf(m)?.let { kindStyle(it).durationSec }?.takeIf { it > 0 }?.let { it * 1000L }

    fun isOn(t: Trigger) = t.key in triggers.value

    fun setTrigger(t: Trigger, on: Boolean) {
        triggers.value = if (on) triggers.value + t.key else triggers.value - t.key
        save()
    }

    fun setBlocked(pkg: String, blocked: Boolean) {
        blockedApps.value = if (blocked) blockedApps.value + pkg else blockedApps.value - pkg
        save()
    }

    fun preset(size: Float, w: Float, h: Float) {
        scale.value = size
        widthScale.value = w
        heightScale.value = h
        save()
    }

}
