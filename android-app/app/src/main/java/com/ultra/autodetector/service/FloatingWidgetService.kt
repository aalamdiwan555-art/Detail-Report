package com.ultra.autodetector.service

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.ultra.autodetector.util.Constants

class FloatingWidgetService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubble: TextView? = null
    private var root: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var expanded = false
    private var moved = false
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE) {
            removeWidget()
            stopSelf()
        } else if (root == null && canDrawOverlays()) {
            createWidget()
        }
        return START_NOT_STICKY
    }

    private fun createWidget() {
        val frame = FrameLayout(this)
        val marker = TextView(this).apply {
            text = "U"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(8, 18, 27))
            setBackgroundColor(Color.rgb(86, 214, 200))
            contentDescription = "Ultra AutoDetector controls"
            setOnClickListener { if (!moved) toggleMenu() }
        }
        val size = (56 * resources.displayMetrics.density).toInt()
        frame.addView(marker, FrameLayout.LayoutParams(size, size))
        bubble = marker
        root = frame

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else WindowManager.LayoutParams.TYPE_PHONE
        params = WindowManager.LayoutParams(
            size,
            size,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16
            y = 180
        }
        marker.setOnTouchListener { view, event ->
            val current = params ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    moved = false
                    downX = event.rawX
                    downY = event.rawY
                    startX = current.x
                    startY = current.y
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (kotlin.math.abs(dx) > 6 || kotlin.math.abs(dy) > 6) moved = true
                    current.x = startX + dx.toInt()
                    current.y = startY + dy.toInt()
                    windowManager.updateViewLayout(frame, current)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) snapToEdge(frame, current, size)
                    else view.performClick()
                    true
                }
                else -> true
            }
        }
        windowManager.addView(frame, params)
    }

    private fun toggleMenu() {
        val frame = root ?: return
        if (expanded) {
            if (frame.childCount > 1) frame.removeViewAt(1)
            expanded = false
            return
        }
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.rgb(16, 37, 50))
            setPadding(6, 2, 6, 2)
        }
        val play = Button(this).apply {
            text = "Play"
            setOnClickListener {
                sendBroadcast(Intent(Constants.ACTION_START_DETECTION).setPackage(packageName))
                toggleMenu()
            }
        }
        val pause = Button(this).apply {
            text = if (DetectionService.isPaused) "Resume" else "Pause"
            setOnClickListener {
                sendBroadcast(
                    Intent(DetectionService.ACTION_PAUSE).setPackage(packageName)
                        .putExtra(DetectionService.EXTRA_PAUSED, !DetectionService.isPaused),
                )
                text = if (DetectionService.isPaused) "Resume" else "Pause"
            }
        }
        val close = Button(this).apply {
            text = "Close"
            setOnClickListener {
                sendBroadcast(Intent(DetectionService.ACTION_STOP).setPackage(packageName))
                removeWidget()
                stopSelf()
            }
        }
        menu.addView(play)
        menu.addView(pause)
        menu.addView(close)
        frame.addView(menu)
        expanded = true
    }

    private fun snapToEdge(frame: View, current: WindowManager.LayoutParams, size: Int) {
        val width = resources.displayMetrics.widthPixels
        current.x = if (current.x + size / 2 < width / 2) 8 else width - size - 8
        windowManager.updateViewLayout(frame, current)
    }

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || android.provider.Settings.canDrawOverlays(this)

    private fun removeWidget() {
        root?.let { runCatching { windowManager.removeView(it) } }
        root = null
        bubble = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeWidget()
        super.onDestroy()
    }

    companion object {
        const val ACTION_HIDE = Constants.ACTION_HIDE_FLOATING_WIDGET
    }
}