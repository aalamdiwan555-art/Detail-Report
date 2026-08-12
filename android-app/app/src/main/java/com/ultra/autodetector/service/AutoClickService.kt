package com.ultra.autodetector.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.ultra.autodetector.util.Constants
import com.ultra.autodetector.util.HumanizationEngine

class AutoClickService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        instance = this
        val filter = IntentFilter(Constants.ACTION_PERFORM_CLICK)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(clickReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(clickReceiver, filter)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    fun performUserRequestedClick(x: Float, y: Float) {
        val delay = HumanizationEngine.getMicroDelay()
        handler.postDelayed({
            val (jx, jy) = HumanizationEngine.applyJitter(x, y)
            val path = Path().apply { moveTo(jx, jy) }
            val stroke = GestureDescription.StrokeDescription(path, 0L, 100L)
            dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, handler)
        }, delay)
    }

    private val clickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Constants.ACTION_PERFORM_CLICK) {
                performUserRequestedClick(
                    intent.getFloatExtra(Constants.EXTRA_CLICK_X, 0f),
                    intent.getFloatExtra(Constants.EXTRA_CLICK_Y, 0f),
                )
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(clickReceiver) }
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile var instance: AutoClickService? = null
            private set
    }
}