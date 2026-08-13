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
    fun doubleClick(x: Float, y: Float): Boolean {
        val startedAt = SystemClock.uptimeMillis()
        val accepted = dispatchTap(
            x = x,
            y = y,
            onCompleted = {
                val secondTap = Runnable {
                    val secondAccepted = dispatchTap(
                        x = x,
                        y = y,
                        onCompleted = {
                            Log.i(
                                TAG,
                                "DOUBLE CLICKED at ${x.toInt()},${y.toInt()} in " +
                                    "${SystemClock.uptimeMillis() - startedAt}ms",
                            )
                        },
                        // The first tap already reached the target, so only
                        // use one node fallback when the second gesture fails.
                        onCancelled = { clickAccessibilityNode(x, y) },
                    )
                    if (!secondAccepted) clickAccessibilityNode(x, y)
                }
                handler.postDelayed(secondTap, DOUBLE_TAP_DELAY_MS)
            },
            // If the first gesture is rejected/cancelled, neither tap reached
            // the target, so perform both actions through the node fallback.
            onCancelled = { doubleClickAccessibilityNodeAt(x, y) },
        )
        if (!accepted) doubleClickAccessibilityNodeAt(x, y)
        return accepted
    }

    fun doubleClick(node: AccessibilityNodeInfo): Boolean {
        val firstAccepted = runCatching {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }.getOrDefault(false)
        if (!firstAccepted) return false

        handler.postDelayed(
            {
                runCatching {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
            },
            DOUBLE_TAP_DELAY_MS,
        )
        return true
    }

    private fun dispatchTap(
        x: Float,
        y: Float,
        onCompleted: () -> Unit,
        onCancelled: () -> Unit,
    ): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 1L))
            .build()
        val accepted = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    onCompleted()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    onCancelled()
                }
            },
            handler,
        )
        return accepted
    }

    fun doubleClick(rect: Rect): Boolean =
        if (rect.isEmpty) false else doubleClick(rect.exactCenterX(), rect.exactCenterY())

    private fun doubleClickAccessibilityNodeAt(targetX: Float, targetY: Float): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val node = findClickableNode(root, targetX.toInt(), targetY.toInt()) ?: return false
        val first = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (first) {
            handler.postDelayed(
                { runCatching { node.performAction(AccessibilityNodeInfo.ACTION_CLICK) } },
                DOUBLE_TAP_DELAY_MS,
            )
        }
        return first
    }

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
        private const val DOUBLE_TAP_DELAY_MS = 90L
    }
}