package com.ultra.autodetector.detector

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc

object ImageDetector {
    data class Match(
        val point: Point,
        val confidence: Float,
        val width: Int,
        val height: Int,
    )

    fun findImage(template: Bitmap, screen: Bitmap, threshold: Float): Point? =
        findImageResult(template, screen, threshold)?.point

    fun findImageResult(template: Bitmap, screen: Bitmap, threshold: Float): Match? {
        if (template.isRecycled || screen.isRecycled) return null
        val templateRgba = Mat()
        val templateGray = Mat()
        val screenRgba = Mat()
        val screenGray = Mat()
        val result = Mat()
        return try {
            Utils.bitmapToMat(template, templateRgba)
            Utils.bitmapToMat(screen, screenRgba)
            Imgproc.cvtColor(templateRgba, templateGray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.cvtColor(screenRgba, screenGray, Imgproc.COLOR_RGBA2GRAY)
            if (templateGray.cols() > screenGray.cols() ||
                templateGray.rows() > screenGray.rows()
            ) return null

            Imgproc.matchTemplate(
                screenGray,
                templateGray,
                result,
                Imgproc.TM_CCOEFF_NORMED,
            )
            val best = Core.minMaxLoc(result)
            if (best.maxVal < threshold.coerceIn(0.5f, 0.95f).toDouble()) {
                null
            } else {
                Match(
                    point = best.maxLoc,
                    confidence = best.maxVal.toFloat(),
                    width = template.width,
                    height = template.height,
                )
            }
        } finally {
            templateRgba.release()
            templateGray.release()
            screenRgba.release()
            screenGray.release()
            result.release()
        }
    }
}