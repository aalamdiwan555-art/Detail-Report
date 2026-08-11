package com.ultra.autodetector.opencv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import java.nio.ByteBuffer

/**
 * Owns the MediaProjection -> ImageReader lifecycle and converts frames to a
 * tightly packed bitmap while accounting for row padding.
 */
class ScreenCaptureManager(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null

    val width: Int get() = context.resources.displayMetrics.widthPixels
    val height: Int get() = context.resources.displayMetrics.heightPixels
    val densityDpi: Int get() = context.resources.displayMetrics.densityDpi

    fun start(
        resultCode: Int,
        resultData: Intent,
        onFrame: (Bitmap) -> Unit,
    ): Boolean {
        stop()
        if (resultCode != Activity.RESULT_OK) return false
        val manager = context.getSystemService(MediaProjectionManager::class.java)
        projection = manager.getMediaProjection(resultCode, resultData) ?: return false
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader?.setOnImageAvailableListener({ source ->
            source.acquireLatestImage()?.use { image ->
                imageToBitmap(image)?.let(onFrame)
            }
        }, handler)
        display = projection?.createVirtualDisplay(
            "UltraAutoDetectorCapture",
            width,
            height,
            densityDpi,
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
        projection?.stop()
        projection = null
    }

    private fun imageToBitmap(image: android.media.Image): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = (rowStride - pixelStride * image.width).coerceAtLeast(0)
        val paddedWidth = image.width + rowPadding / pixelStride
        val bitmap = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        val buffer: ByteBuffer = plane.buffer
        bitmap.copyPixelsFromBuffer(buffer)
        return if (paddedWidth == image.width) bitmap
        else Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height).also { bitmap.recycle() }
    }
}