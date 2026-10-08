package com.islandify.app
import android.content.Context
import com.islandify.app.core.AppLanguage
import com.islandify.app.R
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem

import androidx.compose.runtime.saveable.rememberSaveableStateHolder

import androidx.activity.compose.BackHandler

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.islandify.app.core.IslandControl
import com.islandify.app.core.IslandSettings
import com.islandify.app.core.Perms
import com.islandify.app.island.IslandService
import com.islandify.app.ui.onboarding.OnboardingScreen
import com.islandify.app.ui.screens.AboutScreen
import com.islandify.app.ui.screens.ActivitiesScreen
import com.islandify.app.ui.screens.AppsScreen
import com.islandify.app.ui.screens.CustomizeScreen
import com.islandify.app.ui.screens.HomeScreen
import com.islandify.app.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    // Incremented in onResume so permission status refreshes when returning from Settings
    private var tick by mutableIntStateOf(0)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()   // must be called before super.onCreate
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        IslandSettings.init(this)
        setContent { AppTheme { AppRoot(tick) } }
    }

    override fun onResume() {
        super.onResume()
        tick++
    }
}

private enum class Tab(@StringRes val label: Int, val icon: ImageVector) {
    Home(R.string.tab_home, Icons.Rounded.Home),
    Activities(R.string.tab_activities, Icons.Rounded.Sensors),
    Customize(R.string.tab_customize, Icons.Rounded.Palette),
    Apps(R.string.tab_apps, Icons.Rounded.Widgets),
    About(R.string.tab_about, Icons.Rounded.Info),
}

/** Shows onboarding until setup is finished, then the main tabbed app. */
@Composable
private fun AppRoot(tick: Int) {
    val ctx = LocalContext.current
    val onboarded by IslandSettings.onboarded.collectAsState()
    val enabled by IslandSettings.enabled.collectAsState()
    val running by IslandService.running.collectAsState()
    val coreOk = remember(tick) { Perms.coreOk(ctx) }
    val ready = onboarded && coreOk

    // Setup is done and the island is switched on: start the service automatically
    LaunchedEffect(ready, enabled, running) {
        if (ready && enabled && !running) IslandControl.start(ctx)
    }

    if (!ready) {
        OnboardingScreen(tick)
    } else {
        MainTabs(tick)
    }
}

/** Screens at least this wide (tablets, unfolded foldables, many landscape phones) get a side rail. */
private const val WIDE_MIN_DP = 600   // same breakpoint as WindowWidthSizeClass Compact -> Medium

@Composable
private fun MainTabs(tick: Int) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    val haptic = LocalHapticFeedback.current
    val stateHolder = rememberSaveableStateHolder()
    val wide = LocalConfiguration.current.screenWidthDp >= WIDE_MIN_DP

    // Back from any other tab goes to Home first (works with predictive back too)
    BackHandler(enabled = tab != Tab.Home) { tab = Tab.Home }

    val select: (Tab) -> Unit = { t ->
        if (tab != t) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            tab = t
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!wide) {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { select(t) },
                            icon = { TabIcon(t, tab == t) },
                            label = { Text(stringResource(t.label)) }
                        )
                    }
                }
            }
        }
    ) { pad ->
        Row(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
            if (wide) {
                NavigationRail {
                    Tab.entries.forEach { t ->
                        NavigationRailItem(
                            selected = tab == t,
                            onClick = { select(t) },
                            icon = { TabIcon(t, tab == t) },
                            label = { Text(stringResource(t.label)) }
                        )
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                // Tabs slide in the direction you are moving, with a soft fade
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (fadeIn(tween(150)) +
                            slideInHorizontally(tween(190, easing = FastOutSlowInEasing)) { dir * it / 10 })
                            .togetherWith(
                                fadeOut(tween(80)) +
                                    slideOutHorizontally(tween(160, easing = FastOutSlowInEasing)) { -dir * it / 10 }
                            )
                    },
                    label = "tabs"
                ) { t ->
                    // Each tab keeps its scroll position and state when you come back to it
                    stateHolder.SaveableStateProvider(t.name) {
                        when (t) {
                            Tab.Home -> HomeScreen(tick)
                            Tab.Activities -> ActivitiesScreen()
                            Tab.Customize -> CustomizeScreen()
                            Tab.Apps -> AppsScreen()
                            Tab.About -> AboutScreen(tick)
                        }
                    }
                }
            }
        }
    }
}

/** Tab icon that pops a little when selected. Label next to it already names the tab, so no description. */
@Composable
private fun TabIcon(t: Tab, selected: Boolean) {
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.18f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "navIcon"
    )
    Icon(
        t.icon,
        contentDescription = null,
        modifier = Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale }
    )
}
