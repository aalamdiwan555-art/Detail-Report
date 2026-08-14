package com.ultra.autodetector.util

import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

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
        var holdRunnable: Runnable? = null
        var isHolding = false

        target.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isHolding = true
                    // Prevent ScrollView from stealing touch
                    v.parent?.requestDisallowInterceptTouchEvent(true)

                    holdRunnable = Runnable {
                        if (isHolding) {
                            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onTriggered()
                        }
                    }
                    handler.postDelayed(holdRunnable!!, HOLD_DURATION_MS)

                    // Visual feedback - shrink
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(150).start()
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isHolding = false
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    holdRunnable?.let { handler.removeCallbacks(it) }
                    holdRunnable = null

                    // Restore
                    v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    // If finger moves too far, cancel (optional)
                    true
                }

                else -> true
            }
        }
    }
}
