package com.ultra.autodetector.detector

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Clicks a detection rectangle without blocking the accessibility service.
 *
 * Gesture dispatch is the supported fast path. If the target is exposed in the
 * accessibility tree, ACTION_CLICK is attempted when a gesture is rejected.
 * The shell fallback is best-effort only: Android normally denies `input` to an
 * application UID unless the device is rooted, so failure is logged rather
 * than treated as a successful click.
 */
class FastClicker(
    private val service: AccessibilityService,
    private val handler: Handler,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var lastClickAt = 0L

    fun click(rect: Rect): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastClickAt < COOLDOWN_MS || rect.isEmpty) return false
        lastClickAt = now
        val x = rect.exactCenterX()
        val y = rect.exactCenterY()
        val startedAt = SystemClock.uptimeMillis()
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 1L))
            .build()
        val accepted = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.i(TAG, "CLICKED at ${x.toInt()},${y.toInt()} in ${SystemClock.uptimeMillis() - startedAt}ms")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (clickAccessibilityNode(rect)) {
                        Log.i(TAG, "CLICKED via node at ${x.toInt()},${y.toInt()}")
                    } else {
                        clickShell(x, y)
                    }
                }
            },
            handler,
        )
        if (!accepted) {
            if (clickAccessibilityNode(rect)) return true
            clickShell(x, y)
        }
        return accepted
    }

    private fun clickAccessibilityNode(rect: Rect): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val targetX = rect.exactCenterX().toInt()
        val targetY = rect.exactCenterY().toInt()
        return findClickableNode(root, targetX, targetY)?.performAction(
            AccessibilityNodeInfo.ACTION_CLICK,
        ) == true
    }

    private fun findClickableNode(
        node: AccessibilityNodeInfo,
        x: Int,
        y: Int,
    ): AccessibilityNodeInfo? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.contains(x, y) && node.isClickable) return node
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { child ->
                findClickableNode(child, x, y)?.let { return it }
            }
        }
        return null
    }

    private fun clickShell(x: Float, y: Float) {
        scope.launch {
            runCatching {
                Runtime.getRuntime().exec(arrayOf("input", "tap", x.toInt().toString(), y.toInt().toString()))
            }.onSuccess {
                Log.i(TAG, "Shell click fallback requested at ${x.toInt()},${y.toInt()}")
            }.onFailure {
                Log.w(TAG, "Shell click fallback unavailable; root is required", it)
            }
        }
    }

    override fun close() {
        scope.coroutineContext.cancel()
    }

    companion object {
        private const val TAG = "FastClicker"
        private const val COOLDOWN_MS = 100L
    }
}