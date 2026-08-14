package com.ultra.autodetector.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.atomic.AtomicReference

class UltraAccessibilityService : AccessibilityService() {
    companion object {
        private val serviceRef = AtomicReference<UltraAccessibilityService?>()

        fun performClick(x: Int, y: Int): Boolean =
            serviceRef.get()?.click(x.toFloat(), y.toFloat()) == true

        fun performSwipe(x1: Int, y1: Int, x2: Int, y2: Int): Boolean =
            serviceRef.get()?.swipe(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat()) == true
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceRef.set(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    private fun click(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 500))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    override fun onDestroy() {
        serviceRef.compareAndSet(this, null)
        super.onDestroy()
    }
}