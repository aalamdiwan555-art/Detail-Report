package com.ultra.autodetector.service

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.ultra.autodetector.UltraAutoDetectorApp

class FloatingOverlayService : Service() {
    companion object {
        const val ACTION_RESULT = "com.ultra.autodetector.action.OVERLAY_RESULT"
        const val ACTION_STOP = "com.ultra.autodetector.action.OVERLAY_STOP"
        const val EXTRA_LEFT = "left"
        const val EXTRA_TOP = "top"
        const val EXTRA_WIDTH = "width"
        const val EXTRA_HEIGHT = "height"
        private const val NOTIFICATION_ID = 102
    }

    private var overlayManager: OverlayManager? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        runCatching {
            startForegroundNotification()
            overlayManager = OverlayManager(this, ::stopDetectionFromOverlay)
            overlayManager?.show()
        }.onFailure {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RESULT -> {
                val left = intent.getIntExtra(EXTRA_LEFT, 0)
                val top = intent.getIntExtra(EXTRA_TOP, 0)
                val width = intent.getIntExtra(EXTRA_WIDTH, 0)
                val height = intent.getIntExtra(EXTRA_HEIGHT, 0)
                if (width > 0 && height > 0) {
                    overlayManager?.showMatch(
                        android.graphics.Rect(left, top, left + width, top + height),
                    )
                }
            }
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun stopDetectionFromOverlay() {
        startService(
            Intent(this, DetectionService::class.java)
                .setAction(DetectionService.ACTION_STOP)
        )
        stopSelf()
    }

    private fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(this, UltraAutoDetectorApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Detection overlay")
            .setContentText("Tap the X to stop detection")
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        overlayManager?.hide()
        overlayManager = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}