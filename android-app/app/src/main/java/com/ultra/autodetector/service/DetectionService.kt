package com.ultra.autodetector.service

import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.util.LruCache
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo
import com.ultra.autodetector.R
import com.ultra.autodetector.UltraAutoDetectorApp
import com.ultra.autodetector.data.repository.TemplateRepository
import com.ultra.autodetector.data.repository.TemplateSyncManager
import com.ultra.autodetector.data.local.EncryptedPrefsManager
import com.ultra.autodetector.detector.ScreenCaptureEngine
import com.ultra.autodetector.detector.TemplateMatcher
import com.ultra.autodetector.ui.main.MainActivity
import com.ultra.autodetector.util.Constants
import com.ultra.autodetector.util.HumanizationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class DetectionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var captureEngine: ScreenCaptureEngine? = null
    private var paused = false
    private lateinit var prefs: EncryptedPrefsManager
    private lateinit var matcher: TemplateMatcher
    private val templates = mutableListOf<TemplateMatcher.LoadedTemplate>()
    private val templateLock = Any()
    private val analyzing = AtomicBoolean(false)
    private var frameNumber = 0
    private val bitmapCache = object : LruCache<String, Bitmap>(MAX_TEMPLATES) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (oldValue !== newValue && !oldValue.isRecycled) oldValue.recycle()
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = EncryptedPrefsManager(this)
        matcher = TemplateMatcher(this)
        val filter = IntentFilter().apply {
            addAction(ACTION_STOP)
            addAction(ACTION_PAUSE)
            addAction(Constants.ACTION_TEMPLATE_UPDATED)
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
            Constants.ACTION_TEMPLATE_UPDATED -> scope.launch { loadTemplates() }
        }
        return START_STICKY
    }

    private fun startDetection(resultCode: Int, data: Intent) {
        if (isRunning) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else startForeground(NOTIFICATION_ID, notification())
        paused = false
        isPaused = false
        isRunning = true
        prefs.setDetectorWasRunning(true)
        frameNumber = 0
        job = scope.launch { loadTemplates() }
        captureEngine = ScreenCaptureEngine(this).also { engine ->
            if (!engine.start(resultCode, data) { frame ->
                    if (paused || !isRunning) {
                        frame.bitmap.recycle()
                        return@start
                    }
                    val shouldAnalyze = frame.changed && analyzing.compareAndSet(false, true)
                    if (!shouldAnalyze) {
                        frame.bitmap.recycle()
                        return@start
                    }
                    frameNumber++
                    scope.launch {
                        try {
                            analyze(frame, runText = frameNumber % TEXT_EVERY_N_FRAMES == 0)
                        } finally {
                            if (!frame.bitmap.isRecycled) frame.bitmap.recycle()
                            analyzing.set(false)
                        }
                    }
                }) {
                stopDetection()
            }
        }
    }

    private suspend fun loadTemplates() {
        val active = TemplateRepository(this).listActive().take(MAX_TEMPLATES)
        synchronized(templateLock) {
            templates.clear()
            bitmapCache.evictAll()
            active.forEach { template ->
                val sharedFile = TemplateSyncManager.sharedFile(this, template.templateId)
                val sourcePath = sharedFile.takeIf { it.exists() }?.absolutePath ?: template.filePath
                val bitmap = bitmapCache.get(template.templateId)
                    ?: BitmapFactory.decodeFile(sourcePath)?.also {
                        bitmapCache.put(template.templateId, it)
                    }
                bitmap?.let {
                    templates += TemplateMatcher.LoadedTemplate(template, it)
                }
            }
        }
    }

    private fun analyze(frame: ScreenCaptureEngine.Frame, runText: Boolean) {
        val best = synchronized(templateLock) {
            val activeTemplates = templates.toList()
            if (activeTemplates.isEmpty() || !HumanizationEngine.isCooldownPassed()) return
            matcher.findBest(frame.bitmap, activeTemplates, runText)
        } ?: return
        val screenRect = Rect(
            (best.rect.left * frame.scaleX).roundToInt(),
            (best.rect.top * frame.scaleY).roundToInt(),
            (best.rect.right * frame.scaleX).roundToInt(),
            (best.rect.bottom * frame.scaleY).roundToInt(),
        )
        sendBroadcast(
            Intent(Constants.ACTION_PERFORM_CLICK).setPackage(packageName)
                .putExtra(Constants.EXTRA_CLICK_X, screenRect.exactCenterX())
                .putExtra(Constants.EXTRA_CLICK_Y, screenRect.exactCenterY())
                .putExtra(Constants.EXTRA_CLICK_LEFT, screenRect.left)
                .putExtra(Constants.EXTRA_CLICK_TOP, screenRect.top)
                .putExtra(Constants.EXTRA_CLICK_WIDTH, screenRect.width())
                .putExtra(Constants.EXTRA_CLICK_HEIGHT, screenRect.height()),
        )
        Log.i(TAG, "Template ${best.template.name} matched by ${best.source} at ${screenRect}")
        HumanizationEngine.recordClick()
    }

    private fun stopDetection() {
        sendBroadcast(Intent(AutoClickService.ACTION_STOP_CLICKING).setPackage(packageName))
        job?.cancel()
        job = null
        captureEngine?.close()
        captureEngine = null
        synchronized(templateLock) {
            templates.clear()
            bitmapCache.evictAll()
        }
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
        matcher.close()
        runCatching { unregisterReceiver(controlReceiver) }
        scope.cancel()
        super.onDestroy()
    }

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_STOP -> stopDetection()
                ACTION_PAUSE -> { paused = intent.getBooleanExtra(EXTRA_PAUSED, true); isPaused = paused }
                Constants.ACTION_TEMPLATE_UPDATED -> scope.launch { loadTemplates() }
            }
        }
    }

    companion object {
        private const val TAG = "DetectionService"
        const val ACTION_START = "com.ultra.autodetector.action.START"
        const val ACTION_STOP = "com.ultra.autodetector.action.STOP"
        const val ACTION_PAUSE = "com.ultra.autodetector.action.PAUSE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_PAUSED = "paused"
        private const val NOTIFICATION_ID = 101
        private const val MAX_TEMPLATES = 20
        private const val TEXT_EVERY_N_FRAMES = 3
        @Volatile var isRunning = false
            private set
        @Volatile var isPaused = false
            private set
    }
}

private inline fun <reified T : android.os.Parcelable> Intent.parcelableExtra(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableExtra(key, T::class.java)
    else @Suppress("DEPRECATION") getParcelableExtra(key)