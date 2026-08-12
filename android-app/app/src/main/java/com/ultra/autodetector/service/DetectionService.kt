package com.ultra.autodetector.service

import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.media.ImageReader
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.graphics.PixelFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo
import com.ultra.autodetector.R
import com.ultra.autodetector.UltraAutoDetectorApp
import com.ultra.autodetector.data.repository.TemplateRepository
import com.ultra.autodetector.data.local.EncryptedPrefsManager
import com.ultra.autodetector.opencv.OpenCvManager
import com.ultra.autodetector.opencv.TemplateMatcher
import com.ultra.autodetector.ui.main.MainActivity
import com.ultra.autodetector.util.Constants
import com.ultra.autodetector.util.HumanizationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DetectionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var projection: android.media.projection.MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var paused = false
    private lateinit var prefs: EncryptedPrefsManager
    private val matcher = TemplateMatcher()
    private val templates = mutableListOf<LoadedTemplate>()

    override fun onCreate() {
        super.onCreate()
        prefs = EncryptedPrefsManager(this)
        val filter = IntentFilter().apply {
            addAction(ACTION_STOP)
            addAction(ACTION_PAUSE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(controlReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(controlReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopDetection()
            ACTION_PAUSE -> {
                paused = intent.getBooleanExtra(EXTRA_PAUSED, true)
                isPaused = paused
            }
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = intent.parcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (data == null || resultCode != Activity.RESULT_OK) stopDetection()
                else startDetection(resultCode, data)
            }
        }
        return START_STICKY
    }

    private fun startDetection(resultCode: Int, data: Intent) {
        if (projection != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else startForeground(NOTIFICATION_ID, notification())
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, data)
        val metrics = resources.displayMetrics
        reader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
        display = projection?.createVirtualDisplay(
            "UltraAutoDetectorCapture",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader?.surface,
            null,
            null,
        )
        paused = false
        isPaused = false
        isRunning = true
        prefs.setDetectorWasRunning(true)
        job = scope.launch {
            loadTemplates()
            while (isActive) {
                if (!paused) {
                    reader?.acquireLatestImage()?.use { image ->
                        OpenCvManager.imageToBitmap(image)?.let { bitmap ->
                            analyze(bitmap)
                            OpenCvManager.releaseBitmap(bitmap)
                        }
                    }
                }
                delay(33L)
            }
        }
    }

    private suspend fun loadTemplates() {
        templates.forEach { OpenCvManager.releaseBitmap(it.bitmap) }
        templates.clear()
        TemplateRepository(this).listActive().take(MAX_TEMPLATES).forEach { template ->
            BitmapFactory.decodeFile(template.filePath)?.let { templates += LoadedTemplate(template, it) }
        }
    }

    private fun analyze(screen: android.graphics.Bitmap) {
        if (templates.isEmpty() || !HumanizationEngine.isCooldownPassed()) return
        val best = templates.asSequence()
            .map { it to matcher.match(screen, it.bitmap, it.template.confidenceThreshold, TemplateMatcher.MAX_CANDIDATES_PER_FRAME) }
            .filter { it.second.found }
            .maxByOrNull { it.second.confidence } ?: return
        sendBroadcast(
            Intent(Constants.ACTION_PERFORM_CLICK).setPackage(packageName)
                .putExtra(Constants.EXTRA_CLICK_X, best.second.centerX)
                .putExtra(Constants.EXTRA_CLICK_Y, best.second.centerY),
        )
        HumanizationEngine.recordClick()
    }

    private fun stopDetection() {
        sendBroadcast(Intent(AutoClickService.ACTION_STOP_CLICKING).setPackage(packageName))
        job?.cancel()
        job = null
        reader?.close()
        reader = null
        display?.release()
        display = null
        projection?.stop()
        projection = null
        templates.forEach { OpenCvManager.releaseBitmap(it.bitmap) }
        templates.clear()
        isRunning = false
        isPaused = false
        if (::prefs.isInitialized) prefs.setDetectorWasRunning(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    private fun notification(): Notification =
        NotificationCompat.Builder(this, UltraAutoDetectorApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.detection_notification_title))
            .setContentText(getString(R.string.detection_notification_text))
            .setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopDetection()
        runCatching { unregisterReceiver(controlReceiver) }
        scope.cancel()
        super.onDestroy()
    }

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_STOP -> stopDetection()
                ACTION_PAUSE -> { paused = intent.getBooleanExtra(EXTRA_PAUSED, true); isPaused = paused }
            }
        }
    }

    private data class LoadedTemplate(
        val template: com.ultra.autodetector.data.model.Template,
        val bitmap: android.graphics.Bitmap,
    )

    companion object {
        const val ACTION_START = "com.ultra.autodetector.action.START"
        const val ACTION_STOP = "com.ultra.autodetector.action.STOP"
        const val ACTION_PAUSE = "com.ultra.autodetector.action.PAUSE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_PAUSED = "paused"
        private const val NOTIFICATION_ID = 101
        private const val MAX_TEMPLATES = 20
        @Volatile var isRunning = false
            private set
        @Volatile var isPaused = false
            private set
    }
}

private inline fun <reified T : android.os.Parcelable> Intent.parcelableExtra(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableExtra(key, T::class.java)
    else @Suppress("DEPRECATION") getParcelableExtra(key)