package com.ultra.autodetector.util

import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.Toast

/**
 * Provides a deliberate six-second local administrator entry gesture on the
 * visible ULTRA logo. There is intentionally no visible hint for this access
 * path.
 */
object LogoTapAccessGesture {
    private const val HOLD_DURATION_MS = 6_000L

    fun attach(target: View, onTriggered: () -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        var triggered = false
        var holding = false
        var adminRunnable: Runnable? = null

        target.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    triggered = false
                    holding = true
                    adminRunnable?.let(handler::removeCallbacks)
                    val runnable = Runnable {
                        if (!holding || triggered || !view.isShown || !view.isEnabled) return@Runnable
                        triggered = true
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        Toast.makeText(view.context, "Admin access", Toast.LENGTH_SHORT).show()
                        onTriggered()
                    }
                    adminRunnable = runnable
                    handler.postDelayed(runnable, HOLD_DURATION_MS)
                    view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(150L).start()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    holding = false
                    adminRunnable?.let(handler::removeCallbacks)
                    adminRunnable = null
                    view.animate().scaleX(1f).scaleY(1f).setDuration(150L).start()
                    true
                }
                else -> {
                    // Keep consuming the gesture so ScrollView and other
                    // parents cannot intercept it during the six-second hold.
                    holding
                }
            }
        }
    }
}