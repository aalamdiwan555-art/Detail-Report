package com.ultra.autodetector.util

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View

/**
 * Keeps administrator entry out of the normal auth surface while still
 * providing a deliberate, discoverable-to-authorized-operators gesture.
 */
object LongPressAccessGesture {
    private const val HOLD_DURATION_MS = 6_000L

    fun attach(target: View, onTriggered: () -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        var triggered = false
        val action = Runnable {
            triggered = true
            target.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            onTriggered()
        }

        target.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    triggered = false
                    view.isPressed = true
                    handler.postDelayed(action, HOLD_DURATION_MS)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(action)
                    view.isPressed = false
                    if (!triggered) view.performClick()
                    true
                }
                else -> true
            }
        }
    }
}