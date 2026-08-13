package com.ultra.autodetector

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import org.opencv.android.OpenCVLoader

class UltraAutoDetectorApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Safe OpenCV init - dono try karega
        try {
            if (!OpenCVLoader.initDebug()) {
                // fallback for newer opencv
                runCatching { OpenCVLoader.initLocal() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Detection Service", // getString() hataya - yahi crash kara raha tha
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows when user-controlled screen detection is active"
                setShowBadge(false)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        // Yahi ID sab jagah use karna hai - FloatingOverlayService me bhi
        const val CHANNEL_ID = "ultra_detection"
    }
}
