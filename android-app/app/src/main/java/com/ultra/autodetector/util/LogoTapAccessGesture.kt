package com.ultra.autodetector.util

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View

object LogoTapAccessGesture {
    private const val HOLD_DURATION_MS = 6_000L

    fun attach(
        view: View?,
        onHoldStart: () -> Unit,
        onTrigger: () -> Unit,
    ) {
        view ?: return
        val handler = Handler(Looper.getMainLooper())
        var triggered = false
        val trigger = Runnable {
            triggered = true
            onTrigger()
        }

        view.isClickable = true
        view.isFocusable = true
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    triggered = false
                    onHoldStart()
                    handler.postDelayed(trigger, HOLD_DURATION_MS)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(trigger)
                    true
                }
                MotionEvent.ACTION_MOVE -> true
                else -> true
            }
        }
    }

    // Compatibility overload for any older caller in the imported project.
    fun attach(view: View?, onTriggered: () -> Unit) =
        attach(view, onHoldStart = {}, onTrigger = onTriggered)

    fun attachToHierarchy(view: View?, onTriggered: () -> Unit) =
        attach(view, onHoldStart = {}, onTrigger = onTriggered)
}