package com.ultra.autodetector.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo
import com.ultra.autodetector.UltraAutoDetectorApp
import com.ultra.autodetector.R
import com.ultra.autodetector.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Native capture lifecycle. Matching is deliberately bounded and paused by
 * explicit user state; this service never starts on its own.
 *
 * The OpenCV matcher is kept behind [FrameAnalyzer] so the project can build
 * without native binaries while Firebase/OpenCV are being configured.
 */
class DetectionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var detectionJob: Job? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var paused = false
    private var stopping = false
    private var frameAnalyzer: FrameAnalyzer = FrameAnalyzer()

    override fun onCreate() {
        super.onCreate()
        registerControlReceiver()
        val power = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "UltraAutoDetector::Capture",
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopDetection()
            ACTION_PAUSE -> paused = intent.getBooleanExtra(EXTRA_PAUSED, true)
            ACTION_START, null -> {
                val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                    ?: Activity.RESULT_CANCELED
                val data = intent?.parcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (resultCode == Activity.RESULT_CANCELED || data == null) {
                    stopDetection()
                } else {
                    startDetection(resultCode, data)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startDetection(resultCode: Int, data: Intent) {
        if (mediaProjection != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification())
        }
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = manager.getMediaProjection(resultCode, data)
        val metrics = resources.displayMetrics
        imageReader = ImageReader.newInstance(
            metrics.widthPixels,
            metrics.heightPixels,
            android.graphics.PixelFormat.RGBA_8888,
            2,
        )
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "UltraAutoDetectorCapture",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null,
        )
        wakeLock?.takeUnless { it.isHeld }?.acquire(10 * 60 * 1000L)
        detectionJob = scope.launch {
            while (isActive) {
                if (!paused) {
                    imageReader?.acquireLatestImage()?.use { image ->
                        frameAnalyzer.analyze(image)
                    }
                }
                delay(FRAME_INTERVAL_MS)
            }
        }
        isRunning = true
    }

    private fun stopDetection() {
        if (stopping) return
        stopping = true
        detectionJob?.cancel()
        detectionJob = null
        imageReader?.close()
        imageReader = null
        virtualDisplay?.release()
        virtualDisplay = null
        mediaProjection?.stop()
        mediaProjection = null
        wakeLock?.takeIf { it.isHeld }?.release()
        isRunning = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
        stopping = false
    }

    private fun notification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, UltraAutoDetectorApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.detection_notification_title))
            .setContentText(getString(R.string.detection_notification_text))
            .setOngoing(true)
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun registerControlReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_STOP)
            addAction(ACTION_PAUSE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(controlReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(controlReceiver, filter)
        }
    }

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_STOP -> stopDetection()
                ACTION_PAUSE -> paused = intent.getBooleanExtra(EXTRA_PAUSED, true)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (!stopping) stopDetection()
        runCatching { unregisterReceiver(controlReceiver) }
        scope.cancel()
        super.onDestroy()
    }

    class FrameAnalyzer {
        fun analyze(image: android.media.Image) {
            // The production OpenCV adapter belongs here. Keeping frame
            // ownership in this class ensures Image.close() is always called.
            // A future adapter can emit a click request to AutoClickService.
            image.width
        }
    }

    companion object {
        const val ACTION_START = "com.ultra.autodetector.action.START"
        const val ACTION_STOP = "com.ultra.autodetector.action.STOP"
        const val ACTION_PAUSE = "com.ultra.autodetector.action.PAUSE"
        const val EXTRA_RESULT_CODE = "com.ultra.autodetector.extra.RESULT_CODE"
        const val EXTRA_RESULT_DATA = "com.ultra.autodetector.extra.RESULT_DATA"
        const val EXTRA_PAUSED = "com.ultra.autodetector.extra.PAUSED"
        private const val NOTIFICATION_ID = 101
        private const val FRAME_INTERVAL_MS = 100L
        @Volatile var isRunning: Boolean = false
            private set
    }
}

private inline fun <reified T : android.os.Parcelable> Intent.parcelableExtra(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableExtra(key, T::class.java)
    else @Suppress("DEPRECATION") getParcelableExtra(key)