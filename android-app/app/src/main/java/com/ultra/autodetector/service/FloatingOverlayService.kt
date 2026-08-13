package com.ultra.autodetector.service

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo
import com.ultra.autodetector.UltraAutoDetectorApp

class FloatingOverlayService : android.app.Service() {

    private var windowManager: WindowManager? = null
    private var overlay: DetectionOverlay? = null

    override fun onCreate() {
        super.onCreate()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                stopSelf()
                return
            }
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            overlay = DetectionOverlay(this).apply {
                visibility = View.GONE
                setBackgroundColor(Color.TRANSPARENT)
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            windowManager?.addView(overlay, params)

            val notification = NotificationCompat.Builder(this, UltraAutoDetectorApp.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("Detection overlay")
                .setContentText("Showing the last matched target")
                .setOngoing(true)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(this, 102, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(102, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RESULT) {
            val left = intent.getIntExtra(EXTRA_LEFT, 0)
            val top = intent.getIntExtra(EXTRA_TOP, 0)
            val width = intent.getIntExtra(EXTRA_WIDTH, 0)
            val height = intent.getIntExtra(EXTRA_HEIGHT, 0)
            if(width > 0 && height > 0) overlay?.show(Rect(left, top, left + width, top + height))
        } else if (intent?.action == ACTION_STOP) {
            overlay?.hide()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        try { overlay?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
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
        private val hideRunnable = Runnable { hide() }

        fun show(value: Rect) {
            rectangle = value
            visibility = View.VISIBLE
            removeCallbacks(hideRunnable)
            postDelayed(hideRunnable, 700L)
            invalidate()
        }

        fun hide() {
            rectangle = null
            visibility = View.GONE
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            rectangle?.let { canvas.drawRect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat(), paint) }
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
