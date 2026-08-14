package com.ultra.autodetector.util

import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup

object LogoTapAccessGesture {
    private const val REQUIRED_TAPS = 6
    private const val TAP_SEQUENCE_TIMEOUT_MS = 2500L

    /**
     * Attaches a six-tap gesture to the target view.
     * On the sixth tap -> triggers onTriggered callback (open admin).
     * Usage: LogoTapAccessGesture.attach(view) { openAdminPanel() }
     */
    fun attach(target: View?, onTriggered: () -> Unit) {
        if (target == null) return
        attachToTarget(target, TapState(onTriggered))
    }

    private class TapState(private val onTriggered: () -> Unit) {
        private val handler = Handler(Looper.getMainLooper())
        private var tapCount = 0
        private val resetRunnable = Runnable { tapCount = 0 }

        fun registerTap(v: View) {
            tapCount += 1
            handler.removeCallbacks(resetRunnable)

            if (tapCount == REQUIRED_TAPS) {
                tapCount = 0
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onTriggered()
                return
            }

            handler.postDelayed(resetRunnable, TAP_SEQUENCE_TIMEOUT_MS)
        }
    }

    private fun attachToTarget(target: View, tapState: TapState) {
        target.isClickable = true
        target.isLongClickable = true
        target.isFocusable = true

        val touchSlop = ViewConfiguration.get(target.context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var movedTooFar = false

        target.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    movedTooFar = false
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    if (!movedTooFar) tapState.registerTap(v)
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    movedTooFar = kotlin.math.abs(event.x - downX) > touchSlop ||
                        kotlin.math.abs(event.y - downY) > touchSlop
                    true
                }

                else -> true
            }
        }
    }

    /**
     * Handles taps that start on the logo icon, label, or surrounding target
     * with one shared counter.
     */
    fun attachToHierarchy(target: View?, onTriggered: () -> Unit) {
        if (target == null) return
        val tapState = TapState(onTriggered)
        attachToHierarchy(target, tapState)
    }

    private fun attachToHierarchy(target: View, tapState: TapState) {
        attachToTarget(target, tapState)
        if (target is ViewGroup) {
            for (index in 0 until target.childCount) {
                attachToHierarchy(target.getChildAt(index), tapState)
            }
        }
    }
}
