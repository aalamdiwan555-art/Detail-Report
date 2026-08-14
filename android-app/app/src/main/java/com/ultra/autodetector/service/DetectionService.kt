package com.ultra.autodetector.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.ultra.autodetector.R
import com.ultra.autodetector.UltraAutoDetectorApp
import com.ultra.autodetector.data.local.ActionEntity
import com.ultra.autodetector.data.local.AppDatabase
import com.ultra.autodetector.data.local.LogEntity
import com.ultra.autodetector.detector.ImageDetector
import com.ultra.autodetector.ui.main.MainActivity
import com.ultra.autodetector.util.OverlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

class DetectionService : Service() {
    companion object {
        const val ACTION_START = "com.ultra.autodetector.action.START"
        const val ACTION_STOP = "com.ultra.autodetector.action.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_INTERVAL_MS = "interval_ms"
        const val ACTION_STOP_DETECTION = "com.ultra.autodetector.action.STOP_DETECTION"
        const val ACTION_PUSH_CLICKED = "com.ultra.autodetector.action.PUSH_CLICKED"
        private const val NOTIFICATION_ID = 101
        private const val DEFAULT_INTERVAL_MS = 500L
        private const val COOLDOWN_MS = 900L

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var scanJob: Job? = null
    private var lastActionAt = 0L
    private var intervalMs = DEFAULT_INTERVAL_MS
    private lateinit var database: AppDatabase
    private val settings by lazy {
        getSharedPreferences("detector_settings", Context.MODE_PRIVATE)
    }

    private val overlayReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_STOP_DETECTION -> stopDetection()
                ACTION_PUSH_CLICKED -> log("Overlay PUSH clicked")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        val filter = IntentFilter().apply {
            addAction(ACTION_STOP_DETECTION)
            addAction(ACTION_PUSH_CLICKED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(overlayReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(overlayReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP, ACTION_STOP_DETECTION -> stopDetection()
            ACTION_START -> startDetection(intent)
        }
        return START_NOT_STICKY
    }

    private fun startDetection(intent: Intent) {
        if (isRunning) return
        intervalMs = intent.getLongExtra(EXTRA_INTERVAL_MS, DEFAULT_INTERVAL_MS)
            .coerceIn(100L, 2000L)
        startForegroundNotification()
        isRunning = true
        OverlayManager.showOverlay(this, true)
        val captureIntent = Intent(this, ScreenCaptureService::class.java)
            .setAction(ScreenCaptureService.ACTION_START)
            .putExtra(
                ScreenCaptureService.EXTRA_RESULT_CODE,
                intent.getIntExtra(EXTRA_RESULT_CODE, 0),
            )
            .putExtra(
                ScreenCaptureService.EXTRA_RESULT_DATA,
                intent.getParcelableExtraCompat<Intent>(EXTRA_RESULT_DATA),
            )
            .putExtra(ScreenCaptureService.EXTRA_INTERVAL_MS, intervalMs)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(captureIntent)
            } else {
                startService(captureIntent)
            }
        }.onFailure { error ->
            log("Screen capture failed to start: ${error.message}", "ERROR")
            stopDetection()
        }
        scanJob?.cancel()
        scanJob = scope.launch {
            while (isActive && isRunning) {
                scanOnce()
                delay(intervalMs)
            }
        }
        log("Detection started with ${intervalMs}ms capture interval")
    }

    private suspend fun scanOnce() {
        val screen = ScreenCaptureService.currentFrame() ?: return
        try {
            if (!settings.getBoolean("global_enabled", true)) return
            val templates = withContext(Dispatchers.IO) { database.templateDao().getEnabled() }
            for (template in templates) {
                val bitmap = BitmapFactory.decodeFile(template.imagePath) ?: continue
                try {
                    val match = ImageDetector.findImageResult(bitmap, screen, template.threshold)
                        ?: continue
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastActionAt < COOLDOWN_MS) continue
                    lastActionAt = now
                    val centerX = match.point.x.roundToInt() + match.width / 2
                    val centerY = match.point.y.roundToInt() + match.height / 2
                    executeAction(template.id, template.name, centerX, centerY, match.confidence)
                } finally {
                    bitmap.recycle()
                }
            }
        } catch (error: Throwable) {
            log("Detection scan failed: ${error.message}", "ERROR")
        } finally {
            screen.recycle()
        }
    }

    private suspend fun executeAction(
        templateId: String,
        templateName: String,
        centerX: Int,
        centerY: Int,
        confidence: Float,
    ) {
        val action = withContext(Dispatchers.IO) { database.actionDao().getForTemplate(templateId) }
            ?: ActionEntity(templateId = templateId)
        val performed = when (action.actionType.uppercase(Locale.US)) {
            ActionEntity.TYPE_SWIPE -> performSwipeAction(action.parameters, centerX, centerY)
            else -> AutoDetectorService.performClick(centerX, centerY)
        }
        log(
            message = if (performed) {
                "Matched $templateName and executed ${action.actionType}"
            } else {
                "Matched $templateName but accessibility gesture was unavailable"
            },
            templateName = templateName,
            confidence = confidence,
            x = centerX,
            y = centerY,
            level = if (performed) "INFO" else "WARN",
        )
    }

    private fun performSwipeAction(parameters: String, x: Int, y: Int): Boolean {
        val values = parameters.split(',').mapNotNull { it.trim().toIntOrNull() }
        return if (values.size == 4) {
            AutoDetectorService.performSwipe(values[0], values[1], values[2], values[3])
        } else {
            AutoDetectorService.performSwipe(x, y, x, (y - 400).coerceAtLeast(0))
        }
    }

    private fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(this, UltraAutoDetectorApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("ULTRA Active")
            .setContentText("Image detection is running")
            .setOngoing(true)
            .setContentIntent(
                android.app.PendingIntent.getActivity(
                    this,
                    101,
                    Intent(this, MainActivity::class.java),
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                        android.app.PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopDetection() {
        scanJob?.cancel()
        scanJob = null
        if (isRunning) log("Detection stopped")
        isRunning = false
        OverlayManager.hideOverlay()
        stopService(Intent(this, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_STOP))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun log(
        message: String,
        level: String = "INFO",
        templateName: String? = null,
        confidence: Float? = null,
        x: Int? = null,
        y: Int? = null,
    ) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                database.logDao().insert(
                    LogEntity(
                        level = level,
                        message = message,
                        templateName = templateName,
                        confidence = confidence,
                        x = x,
                        y = y,
                    ),
                )
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(overlayReceiver) }
        isRunning = false
        scanJob?.cancel()
        OverlayManager.hideOverlay()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@Suppress("DEPRECATION")
private inline fun <reified T : android.os.Parcelable> Intent.getParcelableExtraCompat(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, T::class.java)
    } else {
        getParcelableExtra(key)
    }