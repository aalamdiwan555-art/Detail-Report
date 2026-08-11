package com.ultra.autodetector.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.google.firebase.FirebaseApp
import com.ultra.autodetector.UltraAutoDetectorApp
import com.ultra.autodetector.R
import com.ultra.autodetector.data.firebase.FirestoreManager
import com.ultra.autodetector.data.firebase.StorageManager
import com.ultra.autodetector.data.model.Template
import com.ultra.autodetector.opencv.OpenCvManager
import com.ultra.autodetector.opencv.TemplateMatcher
import com.ultra.autodetector.ui.MainActivity
import com.ultra.autodetector.util.HumanizationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Native capture lifecycle. Matching is deliberately bounded and paused by
 * explicit user state; this service never starts on its own.
 *
 * The matcher is kept behind dependency-free bitmap adapters so the project
 * can build without an unselected native OpenCV binary.
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
    private val templateMatcher = TemplateMatcher()
    private val firestoreManager by lazy { FirestoreManager() }
    private val storageManager by lazy { StorageManager() }
    private val loadedTemplates = mutableListOf<LoadedTemplate>()
    private var templatesLoaded = false

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
            ACTION_REFRESH_TEMPLATES -> {
                templatesLoaded = false
                scope.launch { loadTemplates() }
            }
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
            loadTemplates()
            while (isActive) {
                if (!paused) {
                    imageReader?.acquireLatestImage()?.use { image ->
                        OpenCvManager.imageToBitmap(image)?.let { bitmap ->
                            analyzeFrame(bitmap)
                            OpenCvManager.releaseBitmap(bitmap)
                        }
                    }
                }
                delay(FRAME_INTERVAL_MS)
            }
        }
        isRunning = true
    }

    private suspend fun loadTemplates() {
        if (templatesLoaded) return
        if (FirebaseApp.getApps(this).isEmpty()) {
            templatesLoaded = true
            return
        }
        loadedTemplates.forEach { releaseTemplate(it) }
        loadedTemplates.clear()
        runCatching {
            firestoreManager.listTemplates()
                .asSequence()
                .filter { it.isActive && it.downloadUrl.isNotBlank() }
                .take(MAX_TEMPLATES)
                .mapNotNull { template ->
                    val localFile = storageManager
                        .downloadTemplateImage(template.downloadUrl, template.templateId, this)
                        .getOrNull()
                        ?: return@mapNotNull null
                    val bitmap = BitmapFactory.decodeFile(localFile.absolutePath)
                    if (bitmap == null) {
                        runCatching { localFile.delete() }
                        null
                    } else {
                        LoadedTemplate(template, bitmap, localFile)
                    }
                }
                .toList()
                .also { loadedTemplates.addAll(it) }
        }
        templatesLoaded = true
    }

    private fun analyzeFrame(screen: Bitmap) {
        if (loadedTemplates.isEmpty() || !HumanizationEngine.isCooldownPassed()) return
        val bestMatch = loadedTemplates.asSequence()
            .map { loaded ->
                loaded.template to templateMatcher.match(
                    screen = screen,
                    template = loaded.bitmap,
                    threshold = loaded.template.confidenceThreshold.toDouble(),
                    maxCandidates = TemplateMatcher.MAX_CANDIDATES_PER_FRAME,
                )
            }
            .filter { (_, result) -> result.found }
            .maxByOrNull { (_, result) -> result.confidence }
            ?: return

        val (jitteredX, jitteredY) = HumanizationEngine.applyJitter(
            bestMatch.second.centerX,
            bestMatch.second.centerY,
        )
        AutoClickService.instance?.performUserRequestedClick(
            HumanizationEngine.clampCoordinate(jitteredX, screen.width - 1f),
            HumanizationEngine.clampCoordinate(jitteredY, screen.height - 1f),
        )
        HumanizationEngine.recordClick()
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
        loadedTemplates.forEach { releaseTemplate(it) }
        loadedTemplates.clear()
        templatesLoaded = false
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
            addAction(ACTION_REFRESH_TEMPLATES)
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
                ACTION_REFRESH_TEMPLATES -> {
                    templatesLoaded = false
                    scope.launch { loadTemplates() }
                }
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

    private fun releaseTemplate(template: LoadedTemplate) {
        OpenCvManager.releaseBitmap(template.bitmap)
        runCatching { template.file.delete() }
    }

    private data class LoadedTemplate(
        val template: Template,
        val bitmap: Bitmap,
        val file: File,
    )

    companion object {
        const val ACTION_START = "com.ultra.autodetector.action.START"
        const val ACTION_STOP = "com.ultra.autodetector.action.STOP"
        const val ACTION_PAUSE = "com.ultra.autodetector.action.PAUSE"
        const val ACTION_REFRESH_TEMPLATES = "com.ultra.autodetector.action.REFRESH_TEMPLATES"
        const val EXTRA_RESULT_CODE = "com.ultra.autodetector.extra.RESULT_CODE"
        const val EXTRA_RESULT_DATA = "com.ultra.autodetector.extra.RESULT_DATA"
        const val EXTRA_PAUSED = "com.ultra.autodetector.extra.PAUSED"
        private const val NOTIFICATION_ID = 101
        private const val FRAME_INTERVAL_MS = 100L
        private const val MAX_TEMPLATES = 20
        @Volatile var isRunning: Boolean = false
            private set
    }
}

private inline fun <reified T : android.os.Parcelable> Intent.parcelableExtra(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableExtra(key, T::class.java)
    else @Suppress("DEPRECATION") getParcelableExtra(key)