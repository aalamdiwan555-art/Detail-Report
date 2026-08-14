package com.ultra.autodetector.util

import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.os.Handler
import android.os.Looper

object LogoTapAccessGesture {
    private const val HOLD_DURATION_MS = 6000L

    fun attach(target: View?, onTriggered: () -> Unit) {
        if (target == null) return
        target.isClickable = true
        target.isLongClickable = true
        target.isFocusable = true
        val handler = Handler(Looper.getMainLooper())
        var runnable: Runnable? = null
        target.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    runnable = Runnable {
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onTriggered()
                    }
                    handler.postDelayed(runnable!!, HOLD_DURATION_MS)
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(150).start()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    runnable?.let { handler.removeCallbacks(it) }
                    v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                    true
                }
                else -> true
            }
        }
    }
}
