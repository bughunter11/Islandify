package com.islandify.app.island

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

// BUBBLEMOVE  (GitHub issue #7): bubble window ka wrapper.
// Long-press ke baad ungli ke saath window khiskane ke liye. Tap aur sideways swipe (Compose ke) pehle jaise hi
// chalte hain; sirf long-press + drag par ye wrapper touch apne haath me le leta hai (child ko CANCEL bhejta hai).
// rawX / rawY (screen ke coordinates) use hote hain, isliye window khiskne par bhi drag smooth rehta hai.
internal class BubbleDragLayout(ctx: Context) : FrameLayout(ctx) {
    var canDrag: () -> Boolean = { false }
    var onStart: () -> Unit = {}
    var onMove: (Float, Float) -> Unit = { _, _ -> }
    var onEnd: () -> Unit = {}

    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val main = Handler(Looper.getMainLooper())
    private var downX = 0f
    private var downY = 0f
    private var dragging = false

    private val startDrag = Runnable {
        if (canDrag()) {
            dragging = true
            onStart()
        }
    }

    private fun track(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                dragging = false
                main.removeCallbacks(startDrag)
                if (canDrag()) main.postDelayed(startDrag, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) onMove(ev.rawX - downX, ev.rawY - downY)
                else if (abs(ev.rawX - downX) > slop || abs(ev.rawY - downY) > slop) main.removeCallbacks(startDrag)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                main.removeCallbacks(startDrag)
                if (dragging) {
                    dragging = false
                    onEnd()
                }
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        track(ev)
        return super.dispatchTouchEvent(ev)
    }

    // Drag shuru hote hi child (Compose) ko CANCEL jata hai aur baaki events yahan aate hain
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = dragging

    override fun onTouchEvent(ev: MotionEvent): Boolean = canDrag() || dragging

    // Child ko intercept band karne mat do, warna long-press drag kabhi shuru na ho
    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}

    override fun onDetachedFromWindow() {
        main.removeCallbacks(startDrag)
        super.onDetachedFromWindow()
    }
}
