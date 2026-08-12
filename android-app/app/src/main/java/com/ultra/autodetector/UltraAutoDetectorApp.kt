package com.ultra.autodetector

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class UltraAutoDetectorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.detection_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows when user-controlled screen detection is active"
                setShowBadge(false)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "ultra_detection"
    }
}