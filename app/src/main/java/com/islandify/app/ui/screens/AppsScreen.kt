package com.islandify.app.ui.screens
import com.islandify.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.saveable.rememberSaveable

import androidx.compose.ui.semantics.Role

import androidx.compose.foundation.selection.toggleable

import com.islandify.app.core.*
import com.islandify.app.island.*
import com.islandify.app.ui.components.*
import com.islandify.app.ui.onboarding.*
import com.islandify.app.ui.theme.*

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [system] = system app without a launcher icon: hidden unless "System apps" is switched on. */
private data class AppItem(val pkg: String, val label: String, val system: Boolean = false)

// Shown instantly when the tab opens; it is re-checked on every open / resume / install / uninstall
private var appsCache: List<AppItem>? = null
private val iconCache = android.util.LruCache<String, ImageBitmap>(160)

private tailrec fun Context.findLifecycleOwner(): LifecycleOwner? = when (this) {
    is LifecycleOwner -> this
    is ContextWrapper -> baseContext.findLifecycleOwner()
    else -> null
}

/**
 * Launcher apps + user apps without a launcher icon (+ system apps without one, flagged) +
 * blocked packages that are no longer installed, so they can still be unblocked.
 * Labels of apps already in [prev] are reused: loading a label is the slow part.
 */
@Suppress("DEPRECATION")
private fun scanApps(ctx: Context, prev: List<AppItem>?, blocked: Set<String>): List<AppItem> {
    val pm = ctx.packageManager
    val known = prev.orEmpty().associate { it.pkg to it.label }
    val launcher = pm.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
    ).map { it.activityInfo.packageName }.toSet()

    val out = LinkedHashMap<String, AppItem>()
    for (ai in pm.getInstalledApplications(0)) {
        val pkg = ai.packageName
        if (pkg == ctx.packageName) continue
        val isSystem = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0
        val hiddenByDefault = isSystem && pkg !in launcher
        val label = known[pkg] ?: runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(pkg)
        out[pkg] = AppItem(pkg, label, hiddenByDefault)
    }
    for (p in blocked) if (p !in out && p != ctx.packageName) out[p] = AppItem(p, p)
    return out.values.sortedBy { it.label.lowercase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen() {
    val ctx = LocalContext.current
    val blocked by IslandSettings.blockedApps.collectAsState()
    var apps by remember { mutableStateOf<List<AppItem>?>(appsCache) }
    var query by rememberSaveable { mutableStateOf("") }     // survive tab switches
    var showSystem by rememberSaveable { mutableStateOf(false) }
    var refreshTick by remember { mutableIntStateOf(0) }

    // Install / uninstall / update while the tab is open, or coming back to the app: scan again
    DisposableEffect(ctx) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                i.data?.schemeSpecificPart?.let { iconCache.remove(it) }
                refreshTick++
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val owner = ctx.findLifecycleOwner()
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refreshTick++ }
        owner?.lifecycle?.addObserver(observer)
        onDispose {
            runCatching { ctx.unregisterReceiver(receiver) }
            owner?.lifecycle?.removeObserver(observer)
        }
    }

    LaunchedEffect(refreshTick) {
        val fresh = withContext(Dispatchers.Default) {
            scanApps(ctx, appsCache, IslandSettings.blockedApps.value)
        }
        appsCache = fresh
        if (fresh != apps) apps = fresh   // same list = no redraw
    }
    val base = remember(apps, showSystem) { apps?.filter { showSystem || !it.system } }
    val shown = remember(base, query) {
        base?.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }

    val listState = rememberLazyListState()
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 6 }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // Same compact header as the other tabs (no big empty area on top)
        ScreenHeader(
            title = stringResource(R.string.tab_apps),
            scrolled = scrolled,
            shrink = {
                if (listState.firstVisibleItemIndex > 0) 1f
                else (listState.firstVisibleItemScrollOffset / 240f).coerceIn(0f, 1f)
            }
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.apps_search)) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).reveal()
        )
        FilterChip(
            selected = showSystem,
            onClick = { showSystem = !showSystem },
            label = { Text(stringResource(R.string.apps_system)) },
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The blocked counter rolls up / down when it changes
            AnimatedContent(
                targetState = blocked.size,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                transitionSpec = {
                    val up = targetState > initialState
                    (fadeIn(tween(180)) + slideInVertically(tween(220)) { if (up) it else -it })
                        .togetherWith(
                            fadeOut(tween(120)) + slideOutVertically(tween(180)) { if (up) -it else it }
                        )
                },
                label = "blockedCount"
            ) { n ->
                Text(
                    stringResource(R.string.apps_blocked, n),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = {
                IslandSettings.blockedApps.value = emptySet(); IslandSettings.save()
            }) { Text(stringResource(R.string.apps_allow_all)) }
            TextButton(onClick = {
                IslandSettings.blockedApps.value = IslandSettings.blockedApps.value + base.orEmpty().map { it.pkg }; IslandSettings.save()
            }) { Text(stringResource(R.string.apps_block_all)) }
        }

        Crossfade(targetState = shown == null, label = "appsList") { loading ->
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    items(shown.orEmpty(), key = { it.pkg }) { app ->
                        val on = app.pkg !in blocked
                        // Blocked apps fade out a little, rows slide when the search filters the list
                        val rowAlpha = if (on) 1f else 0.5f
                        ListItem(
                            headlineContent = { Text(app.label) },
                            leadingContent = { AppIcon(app.pkg) },
                            trailingContent = {
                                Switch(checked = on, onCheckedChange = null)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .animateItem()
                                .graphicsLayer { this.alpha = rowAlpha }
                                .toggleable(
                                    value = on,
                                    role = Role.Switch,
                                    onValueChange = { v -> IslandSettings.setBlocked(app.pkg, !v) }
                                )
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(pkg: String) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(initialValue = iconCache.get(pkg), pkg) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
            }?.also { iconCache.put(pkg, it) }
        }
    }
    Box(Modifier.size(42.dp).clip(RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b, null, Modifier.fillMaxSize()) else Icon(Icons.Rounded.Android, null)
    }
}
