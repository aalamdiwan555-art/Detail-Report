package com.ultra.autodetector.detector

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
import android.os.Handler
import android.util.DisplayMetrics
import java.nio.ByteBuffer

class ScreenCapture(
    context: Context,
    private val handler: Handler,
) {
    private val appContext = context.applicationContext
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            handler.post { stop() }
        }
    }

    var capturedWidth: Int = 0
        private set
    var capturedHeight: Int = 0
        private set
    var realScreenWidth: Int = 0
        private set
    var realScreenHeight: Int = 0
        private set

    fun start(
        resultCode: Int,
        data: Intent,
        onBeforeDisplay: () -> Unit,
        onFrame: (Bitmap) -> Unit,
    ): Boolean {
        stop()
        val manager = appContext.getSystemService(MediaProjectionManager::class.java)
        projection = runCatching { manager.getMediaProjection(resultCode, data) }.getOrNull()
            ?: return false
        projection?.registerCallback(projectionCallback, handler)

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        appContext.getSystemService(android.view.WindowManager::class.java)
            .defaultDisplay.getRealMetrics(metrics)
        realScreenWidth = metrics.widthPixels
        realScreenHeight = metrics.heightPixels
        capturedWidth = minOf(CAPTURE_WIDTH, realScreenWidth)
        capturedHeight = (capturedWidth.toFloat() * realScreenHeight / realScreenWidth)
            .toInt()
            .coerceAtLeast(1)

        onBeforeDisplay()
        reader = ImageReader.newInstance(
            capturedWidth,
            capturedHeight,
            PixelFormat.RGBA_8888,
            2,
        )
        reader?.setOnImageAvailableListener({ source ->
            source.acquireLatestImage()?.use { image ->
                imageToBitmap(image)?.let(onFrame)
            }
        }, handler)
        display = projection?.createVirtualDisplay(
            "UltraAutoDetectorCapture",
            capturedWidth,
            capturedHeight,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader?.surface,
            null,
            handler,
        )
        return display != null
    }

    fun stop() {
        display?.release()
        display = null
        reader?.setOnImageAvailableListener(null, null)
        reader?.close()
        reader = null
        projection?.unregisterCallback(projectionCallback)
        projection?.stop()
        projection = null
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        if (pixelStride <= 0 || rowStride <= 0) return null
        val rowPadding = (rowStride - pixelStride * image.width).coerceAtLeast(0)
        val paddedWidth = image.width + rowPadding / pixelStride
        val bitmap = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        val buffer: ByteBuffer = plane.buffer
        buffer.rewind()
        bitmap.copyPixelsFromBuffer(buffer)
        return if (paddedWidth == image.width) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
                .also { bitmap.recycle() }
        }
    }

    companion object {
        private const val CAPTURE_WIDTH = 720
    }
}