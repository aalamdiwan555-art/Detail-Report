package com.ultra.autodetector.detector

import android.app.Activity
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
import android.os.HandlerThread
import kotlin.math.roundToInt

/**
 * MediaProjection capture optimized for matching rather than presentation.
 *
 * Frames are captured at a maximum width of 540 px (540x960 on a 16:9
 * portrait display) and carry the scale needed to map a match back to screen
 * coordinates. The image-difference flag lets callers skip expensive matching
 * when the display is unchanged.
 */
class ScreenCaptureEngine(private val context: Context) {
    data class Frame(
        val bitmap: Bitmap,
        val scaleX: Float,
        val scaleY: Float,
        val changed: Boolean,
    )

    private val metrics = context.resources.displayMetrics
    private val captureWidth = minOf(metrics.widthPixels, MAX_CAPTURE_WIDTH)
    private val captureHeight = (metrics.heightPixels * (captureWidth.toFloat() / metrics.widthPixels))
        .roundToInt()
        .coerceAtLeast(1)
    private val thread = HandlerThread("UltraCapture").apply { start() }
    private val handler = Handler(thread.looper)
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var previousSignature: IntArray? = null

    fun start(
        resultCode: Int,
        resultData: Intent,
        onFrame: (Frame) -> Unit,
    ): Boolean {
        stop()
        if (resultCode != Activity.RESULT_OK) return false
        val manager = context.getSystemService(MediaProjectionManager::class.java)
        projection = manager.getMediaProjection(resultCode, resultData) ?: return false
        reader = ImageReader.newInstance(
            captureWidth,
            captureHeight,
            PixelFormat.RGBA_8888,
            2,
        )
        reader?.setOnImageAvailableListener({ source ->
            source.acquireLatestImage()?.use { image ->
                imageToBitmap(image)?.let { bitmap ->
                    onFrame(
                        Frame(
                            bitmap = bitmap,
                            scaleX = metrics.widthPixels.toFloat() / captureWidth,
                            scaleY = metrics.heightPixels.toFloat() / captureHeight,
                            changed = hasChanged(bitmap),
                        ),
                    )
                }
            }
        }, handler)
        display = projection?.createVirtualDisplay(
            "UltraAutoDetectorCapture",
            captureWidth,
            captureHeight,
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
        projection?.stop()
        projection = null
        previousSignature = null
    }

    fun close() {
        stop()
        thread.quitSafely()
    }

    private fun hasChanged(bitmap: Bitmap): Boolean {
        val pixels = IntArray(SIGNATURE_COLUMNS * SIGNATURE_ROWS)
        val sample = Bitmap.createScaledBitmap(bitmap, SIGNATURE_COLUMNS, SIGNATURE_ROWS, true)
        sample.getPixels(pixels, 0, SIGNATURE_COLUMNS, 0, 0, SIGNATURE_COLUMNS, SIGNATURE_ROWS)
        sample.recycle()
        val previous = previousSignature
        previousSignature = pixels
        if (previous == null) return true
        var difference = 0L
        for (index in pixels.indices) {
            difference += kotlin.math.abs((pixels[index] and 0xff) - (previous[index] and 0xff))
        }
        return difference > STATIC_DIFFERENCE_THRESHOLD
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = (rowStride - pixelStride * image.width).coerceAtLeast(0)
        val paddedWidth = image.width + rowPadding / pixelStride
        val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(plane.buffer)
        return if (paddedWidth == image.width) padded
        else Bitmap.createBitmap(padded, 0, 0, image.width, image.height).also { padded.recycle() }
    }

    companion object {
        private const val MAX_CAPTURE_WIDTH = 540
        private const val SIGNATURE_COLUMNS = 18
        private const val SIGNATURE_ROWS = 32
        private const val STATIC_DIFFERENCE_THRESHOLD = 18_000L
    }
}