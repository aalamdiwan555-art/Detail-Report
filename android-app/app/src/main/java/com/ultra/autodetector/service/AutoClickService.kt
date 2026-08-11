package com.ultra.autodetector.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.Job
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Receives only explicit click requests from the detection pipeline. This
 * service intentionally has no target-app discovery or anti-bot behavior.
 */
class AutoClickService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var pendingJob: Job? = null

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    fun performUserRequestedClick(x: Float, y: Float) {
        if (pendingJob != null) return
        val safeX = max(0f, x)
        val safeY = max(0f, y)
        val delayMs = Random.nextLong(20L, 90L)
        handler.postDelayed({
            val path = Path().apply { moveTo(safeX, safeY) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, min(160L, 70L + delayMs)))
                .build()
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    pendingJob = null
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    pendingJob = null
                }
            }, handler)
        }, delayMs)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        pendingJob = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile var instance: AutoClickService? = null
            private set
    }
}