package com.ultra.autodetector.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo
import com.ultra.autodetector.UltraAutoDetectorApp
import com.ultra.autodetector.R

class FloatingWidgetService : Service() {
    private lateinit var windowManager: WindowManager
    private var root: View? = null
    private var params: WindowManager.LayoutParams? = null
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
        } else if (root == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    NotificationCompat.Builder(this, UltraAutoDetectorApp.CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_menu_view)
                        .setContentTitle(getString(R.string.detection_notification_title))
                        .setContentText("Floating controls are available while detection is active.")
                        .setOngoing(true)
                        .build(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(
                    NOTIFICATION_ID,
                    NotificationCompat.Builder(this, UltraAutoDetectorApp.CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_menu_view)
                        .setContentTitle(getString(R.string.detection_notification_title))
                        .setContentText("Floating controls are available while detection is active.")
                        .setOngoing(true)
                        .build(),
                )
            }
            createWidget()
        }
        return START_NOT_STICKY
    }

    private fun createWidget() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 8, 12, 8)
            setBackgroundColor(0xEE102532.toInt())
        }
        val status = TextView(this).apply {
            text = "  DETECTOR  "
            setTextColor(0xFF56D6C8.toInt())
            contentDescription = "Detection status"
        }
        val pause = Button(this).apply {
            text = "Pause"
            contentDescription = "Pause detection"
            setOnClickListener {
                sendBroadcast(Intent(DetectionService.ACTION_PAUSE).setPackage(packageName).putExtra("paused", true))
                text = "Resume"
                setOnClickListener {
                    sendBroadcast(Intent(DetectionService.ACTION_PAUSE).setPackage(packageName).putExtra("paused", false))
                    text = "Pause"
                }
            }
        }
        val stop = Button(this).apply {
            text = "Stop"
            contentDescription = "Stop detection"
            setOnClickListener {
                sendBroadcast(Intent(DetectionService.ACTION_STOP).setPackage(packageName))
                removeWidget()
                stopSelf()
            }
        }
        container.addView(status)
        container.addView(pause)
        container.addView(stop)
        root = container

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else WindowManager.LayoutParams.TYPE_PHONE
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16
            y = 180
        }
        container.setOnTouchListener { _, event ->
            val currentParams = params ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = currentParams.x
                    startY = currentParams.y
                    downX = event.rawX
                    downY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    currentParams.x = startX + (event.rawX - downX).toInt()
                    currentParams.y = startY + (event.rawY - downY).toInt()
                    windowManager.updateViewLayout(container, currentParams)
                    true
                }
                else -> true
            }
        }
        windowManager.addView(container, params)
    }

    private fun removeWidget() {
        root?.let { view -> runCatching { windowManager.removeView(view) } }
        root = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeWidget()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
        super.onDestroy()
    }

    companion object {
        const val ACTION_HIDE = "com.ultra.autodetector.action.HIDE_WIDGET"
        private const val NOTIFICATION_ID = 102
    }
}