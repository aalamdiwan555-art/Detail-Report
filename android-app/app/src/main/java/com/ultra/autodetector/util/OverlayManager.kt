package com.ultra.autodetector.util

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.ultra.autodetector.R
import com.ultra.autodetector.service.DetectionService
import kotlin.math.roundToInt

object OverlayManager {
    private var windowManager: WindowManager? = null
    private var root: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var appContext: Context? = null

    fun showOverlay(context: Context, isDetecting: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) return
        if (root != null) {
            updateState(isDetecting)
            return
        }
        appContext = context.applicationContext
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val content = (View.inflate(context, R.layout.overlay_push, null) as FrameLayout).apply {
            clipChildren = false
            clipToPadding = false
        }
        val density = context.resources.displayMetrics.density
        val layoutParams = WindowManager.LayoutParams(
            dp(120, density),
            dp(120, density),
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
        }
        params = layoutParams
        root = content
        content.findViewById<TextView>(R.id.tvPushLogo).setOnClickListener {
            Toast.makeText(context, "PUSH Clicked", Toast.LENGTH_SHORT).show()
            context.sendBroadcast(
                Intent(DetectionService.ACTION_PUSH_CLICKED)
                    .setPackage(context.packageName),
            )
        }
        content.findViewById<ImageButton>(R.id.btnCloseOverlay).setOnClickListener {
            context.sendBroadcast(
                Intent(DetectionService.ACTION_STOP_DETECTION)
                    .setPackage(context.packageName),
            )
        }
        attachDrag(content, layoutParams)
        updateState(isDetecting)
        runCatching { windowManager?.addView(content, layoutParams) }
            .onFailure {
                root = null
                params = null
                windowManager = null
            }
    }

    fun hideOverlay() {
        root?.let { view -> runCatching { windowManager?.removeViewImmediate(view) } }
        root = null
        params = null
        windowManager = null
        appContext = null
    }

    fun updateState(isDetecting: Boolean) {
        if (root == null) return
        root?.findViewById<TextView>(R.id.tvPushLogo)?.apply {
            text = if (isDetecting) "PUSH" else "START"
            setBackgroundResource(
                if (isDetecting) R.drawable.bg_push_logo else R.drawable.bg_start_logo,
            )
        }
    }

    private fun attachDrag(view: View, layoutParams: WindowManager.LayoutParams) {
        var downX = 0
        var downY = 0
        var startX = 0
        var startY = 0
        var moved = false
        view.setOnTouchListener { container, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX.roundToInt()
                    downY = event.rawY.roundToInt()
                    startX = layoutParams.x
                    startY = layoutParams.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    moved = moved ||
                        kotlin.math.abs(event.rawX - downX) > dp(6, view.resources.displayMetrics.density) ||
                        kotlin.math.abs(event.rawY - downY) > dp(6, view.resources.displayMetrics.density)
                    layoutParams.x = startX + event.rawX.roundToInt() - downX
                    layoutParams.y = startY + event.rawY.roundToInt() - downY
                    windowManager?.updateViewLayout(view, layoutParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        val close = view.findViewById<ImageButton>(R.id.btnCloseOverlay)
                        val logo = view.findViewById<TextView>(R.id.tvPushLogo)
                        val localX = event.x
                        val localY = event.y
                        if (localX >= close.left && localY <= close.bottom) {
                            close.performClick()
                        } else {
                            logo.performClick()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun dp(value: Int, density: Float): Int = (value * density).roundToInt()
}