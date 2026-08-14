package com.ultra.autodetector.util

import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup

object LogoTapAccessGesture {
    private const val HOLD_DURATION_MS = 6000L

    /**
     * Attaches 6-second hold gesture to target view.
     * On hold -> triggers onTriggered callback (open admin)
     * Usage: LogoTapAccessGesture.attach(view) { openAdminPanel() }
     */
    fun attach(target: View?, onTriggered: () -> Unit) {
        if (target == null) return

        target.isClickable = true
        target.isLongClickable = true
        target.isFocusable = true

        val handler = Handler(Looper.getMainLooper())
        val touchSlop = ViewConfiguration.get(target.context).scaledTouchSlop
        var holdRunnable: Runnable? = null
        var isHolding = false
        var hasTriggered = false
        var downX = 0f
        var downY = 0f

        fun cancelHold(v: View) {
            isHolding = false
            holdRunnable?.let(handler::removeCallbacks)
            holdRunnable = null
            v.parent?.requestDisallowInterceptTouchEvent(false)
        }

        target.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isHolding = true
                    hasTriggered = false
                    downX = event.x
                    downY = event.y
                    // Prevent ScrollView from stealing touch
                    v.parent?.requestDisallowInterceptTouchEvent(true)

                    val runnable = Runnable {
                        if (isHolding && !hasTriggered) {
                            hasTriggered = true
                            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onTriggered()
                        }
                    }
                    holdRunnable = runnable
                    handler.postDelayed(runnable, HOLD_DURATION_MS)

                    // Keep the logo and label visually normal while the hidden hold is active.
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    cancelHold(v)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val movedTooFar = kotlin.math.abs(event.x - downX) > touchSlop ||
                        kotlin.math.abs(event.y - downY) > touchSlop
                    if (movedTooFar) cancelHold(v)
                    true
                }

                else -> true
            }
        }
    }

    /**
     * Handles touches that start on the logo icon or label as well as the
     * surrounding touch target.
     */
    fun attachToHierarchy(target: View?, onTriggered: () -> Unit) {
        if (target == null) return
        attach(target, onTriggered)
        if (target is ViewGroup) {
            for (index in 0 until target.childCount) {
                attachToHierarchy(target.getChildAt(index), onTriggered)
            }
        }
    }
}
