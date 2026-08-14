package com.ultra.autodetector

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.ultra.autodetector.data.repository.TemplateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.opencv.android.OpenCVLoader

class UltraAutoDetectorApp : Application() {
    companion object {
        private const val TAG = "UltraAutoDetectorApp"
        const val CHANNEL_ID = "ultra_active"
    }

    @Volatile
    private var openCvReady = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { TemplateStore.ensureBuiltIns(this@UltraAutoDetectorApp) }
                .onFailure { Log.w(TAG, "Built-in template seed skipped", it) }
        }

        // CRITICAL FIX: OpenCV init on background thread with proper error handling
        Thread({
            val loaded = ensureOpenCvLoaded()
            Log.i(TAG, "OpenCV initialization result: $loaded")
        }, "OpenCVInit").apply {
            isDaemon = true
            start()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val channel = NotificationChannel(
                CHANNEL_ID,
                 "ULTRA Active",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows when user-controlled screen detection is active"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)  // FIX: No sound for service notification
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create notification channel", e)
        }
    }

    /**
     * CRITICAL FIX: Thread-safe OpenCV initialization with retry
     */
    fun ensureOpenCvLoaded(): Boolean {
        if (openCvReady) return true
        return synchronized(this) {
            if (openCvReady) {
                true
            } else {
                val loaded = try {
                    OpenCVLoader.initDebug() || try {
                        OpenCVLoader.initLocal()
                    } catch (e: Exception) {
                        Log.w(TAG, "initLocal failed", e)
                        false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "OpenCV initDebug failed", e)
                    false
                }
                openCvReady = loaded
                loaded
            }
        }
    }
}
