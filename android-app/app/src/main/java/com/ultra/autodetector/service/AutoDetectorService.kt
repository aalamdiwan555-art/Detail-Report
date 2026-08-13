package com.ultra.autodetector.service

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.ultra.autodetector.R
import com.ultra.autodetector.UltraAutoDetectorApp
import com.ultra.autodetector.detector.BuiltInTemplateManager
import com.ultra.autodetector.detector.FastClicker
import com.ultra.autodetector.detector.ScreenCapture
import com.ultra.autodetector.ui.main.MainActivity
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import java.util.concurrent.TimeUnit

class AutoDetectorService : AccessibilityService() {
    companion object {
        private const val TAG = "AutoDetectorService"
        private const val NOTIFICATION_ID = 101
        private const val SCAN_INTERVAL_MS = 180L
        private const val CLICK_COOLDOWN_MS = 800L
        private val SCALES = doubleArrayOf(0.75, 0.90, 1.0, 1.10)
        const val ACTION_START = "com.ultra.autodetector.action.START"
        const val ACTION_STOP = "com.ultra.autodetector.action.STOP"
        const val ACTION_RESTART = "com.ultra.autodetector.action.RESTART"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        @Volatile
        var isRunning = false
            private set
    }

    private lateinit var templates: BuiltInTemplateManager
    private lateinit var capture: ScreenCapture
    private lateinit var clicker: FastClicker
    private lateinit var detectorThread: HandlerThread
    private lateinit var detectorHandler: Handler
    private val analyzing = AtomicBoolean(false)
    private val frameLock = Any()
    private var latestFrame: Bitmap? = null
    private var frameInUse: Bitmap? = null
    private var pendingResultCode = Activity.RESULT_CANCELED
    private var pendingResultData: Intent? = null
    private var connected = false
    private var stopping = false
    private var lastClickAt = 0L

    override fun onCreate() {
        super.onCreate()
        detectorThread = HandlerThread("DetectorThread", android.os.Process.THREAD_PRIORITY_DISPLAY)
        detectorThread.start()
        detectorHandler = Handler(detectorThread.looper)
        capture = ScreenCapture(this, detectorHandler)
        clicker = FastClicker(this, detectorHandler)
        detectorHandler.post {
            if (!application.ensureOpenCvLoaded()) {
                Log.e(TAG, "OpenCV failed to initialize")
                return@post
            }
            templates = BuiltInTemplateManager(this).also { it.onCreate() }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = true
        startCaptureIfReady()
        Log.i(TAG, "Accessibility detector connected")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                stopping = false
                pendingResultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                pendingResultData = intent.parcelableExtra(EXTRA_RESULT_DATA)
                startCaptureIfReady()
            }
            ACTION_STOP -> {
                stopping = true
                stopDetection()
                stopSelf()
            }
            ACTION_RESTART -> {
                stopping = false
                startCaptureIfReady()
            }
        }
        return START_STICKY
    }

    private fun startCaptureIfReady() {
        if (!connected || pendingResultData == null || pendingResultCode != Activity.RESULT_OK) return
        if (!::templates.isInitialized) {
            detectorHandler.removeCallbacks(templateReadyRetry)
            detectorHandler.postDelayed(templateReadyRetry, 40L)
            return
        }
        val started = runCatching {
            capture.start(
                pendingResultCode,
                pendingResultData!!,
                onBeforeDisplay = ::startForegroundServiceNotification,
            ) { frame ->
                synchronized(frameLock) {
                    val old = latestFrame
                    latestFrame = frame
                    if (old != null && old !== frame && old !== frameInUse && !old.isRecycled) {
                        old.recycle()
                    }
                }
            }
        }.getOrElse {
            Log.e(TAG, "MediaProjection setup failed", it)
            false
        }
        if (started) {
            detectorHandler.removeCallbacks(scanRunnable)
            detectorHandler.post(scanRunnable)
            isRunning = true
        } else {
            Log.e(TAG, "MediaProjection failed; accessibility fallback remains available")
            isRunning = false
        }
    }

    private val templateReadyRetry = Runnable {
        if (!stopping) startCaptureIfReady()
    }

    private val scanRunnable = object : Runnable {
        override fun run() {
            analyzeLatestFrame()
            if (isRunning) detectorHandler.postDelayed(this, SCAN_INTERVAL_MS)
        }
    }

    private fun analyzeLatestFrame() {
        if (!analyzing.compareAndSet(false, true)) return
        val frame = synchronized(frameLock) {
            latestFrame?.also { frameInUse = it }
        }
        if (frame == null) {
            analyzing.set(false)
            return
        }
        val startedAt = SystemClock.uptimeMillis()
        val screenRgba = Mat()
        val screenGray = Mat()
        try {
            Utils.bitmapToMat(frame, screenRgba)
            Imgproc.cvtColor(screenRgba, screenGray, Imgproc.COLOR_RGBA2GRAY)
            var best: Detection? = null

            templates.getAllTemplates().filter { it.isActive }.forEach { template ->
                SCALES.forEach { scale ->
                    val scaledWidth = (template.matGray.cols() * scale).roundToInt()
                    val scaledHeight = (template.matGray.rows() * scale).roundToInt()
                    if (scaledWidth < 2 || scaledHeight < 2 ||
                        scaledWidth > screenGray.cols() || scaledHeight > screenGray.rows()
                    ) return@forEach

                    val scaled = Mat()
                    val result = Mat()
                    try {
                        Imgproc.resize(
                            template.matGray,
                            scaled,
                            Size(scaledWidth.toDouble(), scaledHeight.toDouble()),
                        )
                        Imgproc.matchTemplate(screenGray, scaled, result, Imgproc.TM_CCOEFF_NORMED)
                        val match = Core.minMaxLoc(result)
                        if (match.maxVal >= templates.thresholdFor(template.id) &&
                            (best == null || match.maxVal > best!!.confidence)
                        ) {
                            best = Detection(
                                template = template,
                                confidence = match.maxVal,
                                left = match.maxLoc.x.roundToInt(),
                                top = match.maxLoc.y.roundToInt(),
                                width = scaledWidth,
                                height = scaledHeight,
                            )
                        }
                    } finally {
                        scaled.release()
                        result.release()
                    }
                }
            }

            best?.let { found ->
                val now = SystemClock.elapsedRealtime()
                if (now - lastClickAt >= CLICK_COOLDOWN_MS) {
                    val x = (found.left + found.width / 2f) *
                        capture.realScreenWidth / capture.capturedWidth
                    val y = (found.top + found.height / 2f) *
                        capture.realScreenHeight / capture.capturedHeight
                    lastClickAt = now
                    performFastClick(x, y)
                    publishOverlay(found)
                    Log.i(
                        TAG,
                        "FOUND ${found.template.name} at ${x.roundToInt()},${y.roundToInt()} " +
                        "Confidence: ${"%.2f".format(found.confidence)} in " +
                        "${SystemClock.uptimeMillis() - startedAt}ms -> CLICKED",
                    )
                }
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Detection frame failed", error)
        } finally {
            screenRgba.release()
            screenGray.release()
            analyzing.set(false)
            synchronized(frameLock) {
                if (frameInUse === frame) frameInUse = null
                if (latestFrame !== frame && !frame.isRecycled) frame.recycle()
            }
        }
    }

    private fun performFastClick(x: Float, y: Float) {
        clicker.doubleClick(x, y)
    }

    private fun publishOverlay(found: Detection) {
        startService(
            Intent(FloatingOverlayService.ACTION_RESULT)
                .setClass(this, FloatingOverlayService::class.java)
                .setPackage(packageName)
                .putExtra(FloatingOverlayService.EXTRA_LEFT, found.left * capture.realScreenWidth / capture.capturedWidth)
                .putExtra(FloatingOverlayService.EXTRA_TOP, found.top * capture.realScreenHeight / capture.capturedHeight)
                .putExtra(FloatingOverlayService.EXTRA_WIDTH, found.width * capture.realScreenWidth / capture.capturedWidth)
                .putExtra(FloatingOverlayService.EXTRA_HEIGHT, found.height * capture.realScreenHeight / capture.capturedHeight),
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (isRunning) return
        if (!::templates.isInitialized) return
        val root = rootInActiveWindow ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastClickAt < CLICK_COOLDOWN_MS) return
        templates.getAllTemplates().filter { it.isActive }.forEach { template ->
            val nodes = root.findAccessibilityNodeInfosByText(template.name)
            val node = nodes.firstOrNull() ?: return@forEach
            if (clicker.doubleClick(node)) {
                lastClickAt = now
                Log.i(TAG, "Accessibility fallback double-clicked ${template.name}")
                return
            }
        }
    }

    private fun stopDetection() {
        isRunning = false
        detectorHandler.removeCallbacks(scanRunnable)
        capture.stop()
        synchronized(frameLock) {
            val old = latestFrame
            latestFrame = null
            if (old != null && old !== frameInUse && !old.isRecycled) old.recycle()
        }
    }

    private fun startForegroundServiceNotification() {
        val notification = NotificationCompat.Builder(this, UltraAutoDetectorApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("AutoDetector Running")
            .setContentText("${templates.getAllTemplates().count { it.isActive }} Templates Active")
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    11,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        stopDetection()
        clicker.close()
        if (::templates.isInitialized) templates.close()
        detectorThread.quitSafely()
        if (!stopping) {
            WorkManager.getInstance(this).enqueue(
                OneTimeWorkRequestBuilder<AutoDetectorRestartWorker>()
                    .setInitialDelay(5, TimeUnit.SECONDS)
                    .build(),
            )
        }
        super.onDestroy()
    }

    private val application: UltraAutoDetectorApp
        get() = getApplication() as UltraAutoDetectorApp

    private data class Detection(
        val template: BuiltInTemplateManager.Template,
        val confidence: Double,
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
    )
}

class AutoDetectorRestartWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        val intent = Intent(applicationContext, AutoDetectorService::class.java)
            .setAction(AutoDetectorService.ACTION_RESTART)
        runCatching { applicationContext.startService(intent) }
        return Result.success()
    }
}

@Suppress("DEPRECATION")
private inline fun <reified T : android.os.Parcelable> Intent.parcelableExtra(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableExtra(key, T::class.java)
    else getParcelableExtra(key)
