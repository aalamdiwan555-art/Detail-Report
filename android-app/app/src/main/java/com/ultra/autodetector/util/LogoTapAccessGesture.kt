package com.ultra.autodetector.util

import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Provides a deliberate ten-tap local administrator entry gesture on the
 * visible ULTRA logo.
 */
object LogoTapAccessGesture {
    private const val REQUIRED_TAPS = 10
    private const val TAP_SEQUENCE_TIMEOUT_MS = 6_000L

    fun attach(target: View, onTriggered: () -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        var tapCount = 0
        val reset = Runnable { tapCount = 0 }

        target.setOnClickListener {
            tapCount += 1
            handler.removeCallbacks(reset)

            val completed = tapCount == REQUIRED_TAPS
            target.performHapticFeedback(
                if (completed) {
                    HapticFeedbackConstants.LONG_PRESS
                } else {
                    HapticFeedbackConstants.KEYBOARD_TAP
                },
            )

            if (completed) {
                tapCount = 0
                onTriggered()
            } else {
                handler.postDelayed(reset, TAP_SEQUENCE_TIMEOUT_MS)
            }
        }
    }
}