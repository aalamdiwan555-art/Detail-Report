package com.ultra.autodetector.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo
import com.ultra.autodetector.R
import com.ultra.autodetector.UltraAutoDetectorApp

class FloatingOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var overlay: DetectionOverlay? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        overlay = DetectionOverlay(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        runCatching { windowManager.addView(overlay, params) }
        val notification = NotificationCompat.Builder(this, UltraAutoDetectorApp.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("Detection overlay")
                .setContentText("Showing the last matched target")
                .setOngoing(true)
                .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                102,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(102, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RESULT) {
            overlay?.show(
                Rect(
                    intent.getIntExtra(EXTRA_LEFT, 0),
                    intent.getIntExtra(EXTRA_TOP, 0),
                    intent.getIntExtra(EXTRA_LEFT, 0) + intent.getIntExtra(EXTRA_WIDTH, 0),
                    intent.getIntExtra(EXTRA_TOP, 0) + intent.getIntExtra(EXTRA_HEIGHT, 0),
                ),
            )
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        overlay?.let { runCatching { windowManager.removeView(it) } }
        overlay = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private class DetectionOverlay(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        private var rectangle: Rect? = null
        private val hideRunnable = Runnable {
            rectangle = null
            invalidate()
        }

        fun show(value: Rect) {
            rectangle = value
            removeCallbacks(hideRunnable)
            postDelayed(hideRunnable, 700L)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            rectangle?.let(canvas::drawRect)
        }
    }

    companion object {
        const val ACTION_RESULT = "com.ultra.autodetector.action.OVERLAY_RESULT"
        const val ACTION_STOP = "com.ultra.autodetector.action.OVERLAY_STOP"
        const val EXTRA_LEFT = "left"
        const val EXTRA_TOP = "top"
        const val EXTRA_WIDTH = "width"
        const val EXTRA_HEIGHT = "height"
    }
}