package com.ultra.autodetector.util

import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Toast

object LogoTapAccessGesture {
    private const val HOLD_DURATION_MS = 6_000L

    fun attach(target: View?, onTriggered: () -> Unit) {
        target ?: return
        val handler = Handler(Looper.getMainLooper())
        val holdRunnable = Runnable {
            target.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            Toast.makeText(target.context, "Admin opening...", Toast.LENGTH_SHORT).show()
            onTriggered()
        }
        val touchSlop = ViewConfiguration.get(target.context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var moved = false

        target.isClickable = true
        target.isFocusable = true
        target.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    moved = false
                    handler.postDelayed(holdRunnable, HOLD_DURATION_MS)
                    Toast.makeText(view.context, "Hold 6 sec for admin", Toast.LENGTH_SHORT).show()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    moved = moved ||
                        kotlin.math.abs(event.x - downX) > touchSlop ||
                        kotlin.math.abs(event.y - downY) > touchSlop
                    if (moved) handler.removeCallbacks(holdRunnable)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(holdRunnable)
                    true
                }
                else -> true
            }
        }
    }

    // Kept for the imported authentication screen so the base app remains source-compatible.
    fun attachToHierarchy(target: View?, onTriggered: () -> Unit) =
        attach(target, onTriggered)
}