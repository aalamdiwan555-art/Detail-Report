package com.ultra.autodetector.detector

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Dispatches a short accessibility gesture and falls back to a clickable
 * accessibility node when the target app exposes one.
 */
class FastClicker(
    private val service: AccessibilityService,
    private val handler: Handler,
) : AutoCloseable {
    fun click(x: Float, y: Float): Boolean {
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
                    if (clickAccessibilityNode(x, y)) {
                        Log.i(TAG, "CLICKED via node at ${x.toInt()},${y.toInt()}")
                    }
                }
            },
            handler,
        )
        if (!accepted) {
            clickAccessibilityNode(x, y)
        }
        return accepted
    }

    fun click(rect: Rect): Boolean =
        if (rect.isEmpty) false else click(rect.exactCenterX(), rect.exactCenterY())

    private fun clickAccessibilityNode(targetX: Float, targetY: Float): Boolean {
        val root = service.rootInActiveWindow ?: return false
        return findClickableNode(root, targetX.toInt(), targetY.toInt())?.performAction(
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

    override fun close() = Unit

    companion object {
        private const val TAG = "FastClicker"
    }
}