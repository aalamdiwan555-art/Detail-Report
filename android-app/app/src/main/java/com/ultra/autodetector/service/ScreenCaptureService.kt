package com.ultra.autodetector.service

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.ultra.autodetector.R
import com.ultra.autodetector.UltraAutoDetectorApp
import java.nio.ByteBuffer

class ScreenCaptureService : Service() {
    companion object {
        const val ACTION_START = "com.ultra.autodetector.action.CAPTURE_START"
        const val ACTION_STOP = "com.ultra.autodetector.action.CAPTURE_STOP"
        const val EXTRA_RESULT_CODE = "capture_result_code"
        const val EXTRA_RESULT_DATA = "capture_result_data"
        const val EXTRA_INTERVAL_MS = "capture_interval_ms"
        private const val NOTIFICATION_ID = 102

        private val frameLock = Any()
        private var latestFrame: Bitmap? = null

        fun currentFrame(): Bitmap? = synchronized(frameLock) {
            latestFrame?.takeUnless { it.isRecycled }?.copy(Bitmap.Config.ARGB_8888, false)
        }

        private fun replaceFrame(frame: Bitmap) = synchronized(frameLock) {
            latestFrame?.takeUnless { it.isRecycled }?.recycle()
            latestFrame = frame
        }

        private fun clearFrame() = synchronized(frameLock) {
            latestFrame?.takeUnless { it.isRecycled }?.recycle()
            latestFrame = null
        }
    }

    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var lastCaptureAt = 0L
    private var intervalMs = 500L

    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("UltraCapture").also { it.start() }
        handler = Handler(thread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCapture()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_START) {
            intervalMs = intent.getLongExtra(EXTRA_INTERVAL_MS, 500L).coerceIn(100L, 2000L)
            startForegroundCapture(intent)
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCapture(intent: Intent) {
        val notification = NotificationCompat.Builder(this, UltraAutoDetectorApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("ULTRA Active")
            .setContentText("Screen capture is running for image detection")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val resultData = intent.parcelableExtra<Intent>(EXTRA_RESULT_DATA) ?: return
        stopCapture()
        val manager = getSystemService(MediaProjectionManager::class.java)
        projection = runCatching { manager.getMediaProjection(resultCode, resultData) }.getOrNull()
        val currentProjection = projection ?: return
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
            .defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader?.setOnImageAvailableListener({ source ->
            val now = System.currentTimeMillis()
            if (now - lastCaptureAt < intervalMs) {
                source.acquireLatestImage()?.close()
                return@setOnImageAvailableListener
            }
            lastCaptureAt = now
            source.acquireLatestImage()?.use { imageToBitmap(it)?.let(::replaceFrame) }
        }, handler)
        display = currentProjection.createVirtualDisplay(
            "UltraAutoDetectorCapture",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader?.surface,
            null,
            handler,
        )
    }

    private fun stopCapture() {
        display?.release()
        display = null
        reader?.setOnImageAvailableListener(null, null)
        reader?.close()
        reader = null
        projection?.stop()
        projection = null
        clearFrame()
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val pixelStride = plane.pixelStride
        val rowPadding = (plane.rowStride - pixelStride * image.width).coerceAtLeast(0)
        val paddedWidth = image.width + rowPadding / pixelStride
        val bitmap = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        return try {
            val buffer: ByteBuffer = plane.buffer
            buffer.rewind()
            bitmap.copyPixelsFromBuffer(buffer)
            if (paddedWidth == image.width) bitmap
            else Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
                .also { bitmap.recycle() }
        } catch (_: Throwable) {
            bitmap.recycle()
            null
        }
    }

    override fun onDestroy() {
        stopCapture()
        thread.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@Suppress("DEPRECATION")
private inline fun <reified T : android.os.Parcelable> Intent.parcelableExtra(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, T::class.java)
    } else {
        getParcelableExtra(key)
    }