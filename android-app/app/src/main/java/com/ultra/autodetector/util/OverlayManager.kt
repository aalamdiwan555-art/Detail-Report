package com.ultra.autodetector.util

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Toast
import com.ultra.autodetector.R
import com.ultra.autodetector.service.DetectionService
import kotlin.math.roundToInt

object OverlayManager {
    private var windowManager: WindowManager? = null
    private var overlayView: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private const val PREFS_NAME = "overlay_pos"
    private const val KEY_X = "x"
    private const val KEY_Y = "y"

    fun showOverlay(context: Context, showClose: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) return
        if (overlayView != null) return

        val appContext = context.applicationContext
        val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = preferences.getInt(KEY_X, 100)
            y = preferences.getInt(KEY_Y, 300)
        }

        val content = View.inflate(context, R.layout.overlay_push, null) as FrameLayout
        val pushRoot = content.findViewById<FrameLayout>(R.id.pushRoot)
        val closeButton = content.findViewById<ImageButton>(R.id.btnCloseOverlay)
        closeButton.visibility = if (showClose) View.VISIBLE else View.GONE

        windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        params = layoutParams
        overlayView = content
        val touchState = TouchState()

        pushRoot.setOnTouchListener { view, event ->
            handlePushTouch(appContext, view, event, layoutParams, preferences, touchState)
        }
        closeButton.setOnClickListener {
            hideOverlay()
            context.sendBroadcast(
                Intent(DetectionService.ACTION_STOP_DETECTION)
                    .setPackage(context.packageName),
            )
        }
        runCatching { windowManager?.addView(content, layoutParams) }
            .onFailure {
                overlayView = null
                params = null
                windowManager = null
            }
    }

    fun hideOverlay() {
        overlayView?.let { view -> runCatching { windowManager?.removeViewImmediate(view) } }
        overlayView = null
        params = null
        windowManager = null
    }

    private fun handlePushTouch(
        context: Context,
        view: View,
        event: MotionEvent,
        layoutParams: WindowManager.LayoutParams,
        preferences: android.content.SharedPreferences,
        state: TouchState,
    ): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                state.initialX = layoutParams.x
                state.initialY = layoutParams.y
                state.initialTouchX = event.rawX
                state.initialTouchY = event.rawY
                state.isDragging = false
                view.alpha = 0.7f
                view.scaleX = 0.95f
                view.scaleY = 0.95f
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - state.initialTouchX
                val dy = event.rawY - state.initialTouchY
                if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                    state.isDragging = true
                }
                if (state.isDragging) {
                    layoutParams.x = state.initialX + dx.roundToInt()
                    layoutParams.y = state.initialY + dy.roundToInt()
                    runCatching {
                        overlayView?.let { view ->
                            windowManager?.updateViewLayout(view, layoutParams)
                        }
                    }
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                view.alpha = 0.9f
                view.scaleX = 1.0f
                view.scaleY = 1.0f
                if (!state.isDragging) {
                    view.animate()
                        .scaleX(0.9f)
                        .scaleY(0.9f)
                        .setDuration(70L)
                        .withEndAction {
                            view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(90L).start()
                        }
                        .start()
                    vibrate(context, 30L)
                    Toast.makeText(context, "PUSH Clicked", Toast.LENGTH_SHORT).show()
                    context.sendBroadcast(
                        Intent(DetectionService.ACTION_PUSH_CLICKED)
                            .setPackage(context.packageName),
                    )
                } else {
                    preferences.edit()
                        .putInt(KEY_X, layoutParams.x)
                        .putInt(KEY_Y, layoutParams.y)
                        .apply()
                }
                state.isDragging = false
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                view.alpha = 0.9f
                view.scaleX = 1.0f
                view.scaleY = 1.0f
                state.isDragging = false
                true
            }
            else -> true
        }
    }

    private fun vibrate(context: Context, durationMs: Long) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE),
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    private data class TouchState(
        var initialX: Int = 0,
        var initialY: Int = 0,
        var initialTouchX: Float = 0f,
        var initialTouchY: Float = 0f,
        var isDragging: Boolean = false,
    )

    @Suppress("DEPRECATION")
    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun dp(value: Int, density: Float): Int = (value * density).roundToInt()
}