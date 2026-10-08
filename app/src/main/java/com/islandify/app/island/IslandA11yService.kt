package com.islandify.app.island

import com.islandify.app.core.*

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Hosts the island window above the status bar.
 *
 * Why this exists: a normal "draw over other apps" window sits BELOW the status bar window,
 * so Android hands every touch inside the status bar strip to SystemUI and the island never
 * sees a tap. An accessibility overlay sits ABOVE the status bar and receives touches normally.
 *
 * This service does not read or react to screen content. [onAccessibilityEvent] is empty.
 */
class IslandA11yService : AccessibilityService() {

    private var scope: CoroutineScope? = null
    private var overlay: IslandOverlay? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        IslandSettings.init(this)
        IslandController.attach(this)

        val o = IslandOverlay(this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        overlay = o
        scope = s
        connected.value = true

        // The in-app switch (IslandSettings.enabled) shows / hides the island
        s.launch {
            IslandSettings.enabled.collect { on -> if (on) o.show() else o.hide() }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        connected.value = false
        scope?.cancel()
        scope = null
        overlay?.hide()
        overlay = null
    }

    companion object {
        /** True while the accessibility service is bound. IslandService watches this to decide who shows the island. */
        val connected = MutableStateFlow(false)
    }
}
