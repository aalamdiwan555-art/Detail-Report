package com.ultra.autodetector.opencv

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.Image
import android.media.ImageReader
import java.nio.ByteBuffer

/**
 * Capture/image adapter matching the blueprint's OpenCV manager contract.
 *
 * The project intentionally does not vendor an OpenCV native binary until a
 * release ABI/version is selected. Bitmap conversion and matching are
 * dependency-free today; a future native adapter can replace this class
 * without changing the service or permission flow.
 */
object OpenCvManager {
    fun imageToBitmap(image: Image): Bitmap? {
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
            Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height).also { bitmap.recycle() }
        }
    }

    fun bitmapToBitmap(bitmap: Bitmap): Bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)

    fun createImageReader(width: Int, height: Int): ImageReader =
        ImageReader.newInstance(width, height, ImageFormat.RGBA_8888, 2)

    fun releaseBitmap(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) bitmap.recycle()
    }
}