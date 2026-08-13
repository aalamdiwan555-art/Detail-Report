package com.ultra.autodetector

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import org.opencv.android.OpenCVLoader

class UltraAutoDetectorApp : Application() {
    @Volatile
    private var openCvReady = false

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        // Native library loading is expensive and must not delay the first activity.
        Thread({ ensureOpenCvLoaded() }, "OpenCVInit").apply {
            isDaemon = true
            start()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Detection Service",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows when user-controlled screen detection is active"
            setShowBadge(false)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /**
     * May be called by a detector worker when the background preload has not
     * completed yet. The synchronized initialization is never called from the
     * first activity, so cold-start work remains small.
     */
    fun ensureOpenCvLoaded(): Boolean {
        if (openCvReady) return true
        return synchronized(this) {
            if (openCvReady) {
                true
            } else {
                val loaded = runCatching {
                    OpenCVLoader.initDebug() || runCatching { OpenCVLoader.initLocal() }.getOrDefault(false)
                }.getOrDefault(false)
                openCvReady = loaded
                loaded
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "ultra_detection"
    }
}
