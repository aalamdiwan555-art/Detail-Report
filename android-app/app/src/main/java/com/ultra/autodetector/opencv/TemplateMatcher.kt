package com.ultra.autodetector.opencv

import android.graphics.Bitmap
import kotlin.math.sqrt

/**
 * Bounded template matcher used by the capture pipeline.
 *
 * The blueprint requested OpenCV, but the imported project does not select or
 * vendor an OpenCV Android distribution. This implementation keeps the same
 * normalized-correlation contract using Android bitmaps, so the app can build
 * without unverified native binaries. A future OpenCV adapter can implement the
 * same result shape without changing the service or UI boundary.
 */
class TemplateMatcher {
    data class MatchResult(
        val found: Boolean,
        val confidence: Double,
        val centerX: Float,
        val centerY: Float,
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
    )

    fun match(
        screen: Bitmap,
        template: Bitmap,
        threshold: Double = DEFAULT_THRESHOLD,
        maxCandidates: Int = MAX_CANDIDATES,
    ): MatchResult {
        if (
            template.width <= 0 ||
            template.height <= 0 ||
            screen.width <= 0 ||
            screen.height <= 0 ||
            template.width > screen.width ||
            template.height > screen.height ||
            maxCandidates <= 0
        ) {
            return emptyResult()
        }

        val templatePixels = IntArray(template.width * template.height)
        template.getPixels(
            templatePixels,
            0,
            template.width,
            0,
            0,
            template.width,
            template.height,
        )
        val templateLuma = templatePixels.map(::luma)
        val templateMean = templateLuma.average()
        val templateEnergy = templateLuma.sumOf { value ->
            val delta = value - templateMean
            delta * delta
        }
        if (templateEnergy <= 0.0) return emptyResult()

        val screenPixels = IntArray(screen.width * screen.height)
        screen.getPixels(screenPixels, 0, screen.width, 0, 0, screen.width, screen.height)
        var best = Double.NEGATIVE_INFINITY
        var bestX = 0
        var bestY = 0
        var candidates = 0
        var evaluatedCandidate = false

        val stepX = maxOf(1, template.width / 12)
        val stepY = maxOf(1, template.height / 12)
        for (y in 0..screen.height - template.height step stepY) {
            for (x in 0..screen.width - template.width step stepX) {
                if (++candidates > maxCandidates) break
                evaluatedCandidate = true
                val windowMean = windowMean(
                    pixels = screenPixels,
                    screenWidth = screen.width,
                    x = x,
                    y = y,
                    width = template.width,
                    height = template.height,
                )
                var numerator = 0.0
                var windowEnergy = 0.0
                for (templateY in 0 until template.height) {
                    val screenOffset = (y + templateY) * screen.width + x
                    val templateOffset = templateY * template.width
                    for (templateX in 0 until template.width) {
                        val screenValue = luma(screenPixels[screenOffset + templateX]) - windowMean
                        val templateValue = templateLuma[templateOffset + templateX] - templateMean
                        numerator += screenValue * templateValue
                        windowEnergy += screenValue * screenValue
                    }
                }
                val score = if (windowEnergy > 0.0) {
                    numerator / sqrt(templateEnergy * windowEnergy)
                } else {
                    -1.0
                }
                if (score > best) {
                    best = score
                    bestX = x
                    bestY = y
                }
            }
            if (candidates > maxCandidates) break
        }

        return MatchResult(
            found = evaluatedCandidate && best >= threshold,
            confidence = if (evaluatedCandidate) best.coerceIn(-1.0, 1.0) else 0.0,
            centerX = bestX + template.width / 2f,
            centerY = bestY + template.height / 2f,
            left = bestX,
            top = bestY,
            width = template.width,
            height = template.height,
        )
    }

    private fun windowMean(
        pixels: IntArray,
        screenWidth: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ): Double {
        var total = 0.0
        for (row in 0 until height) {
            val offset = (y + row) * screenWidth + x
            for (column in 0 until width) total += luma(pixels[offset + column])
        }
        return total / (width * height)
    }

    private fun luma(color: Int): Double =
        (0.2126 * ((color shr 16) and 0xff)) +
            (0.7152 * ((color shr 8) and 0xff)) +
            (0.0722 * (color and 0xff))

    private fun emptyResult() = MatchResult(false, 0.0, 0f, 0f, 0, 0, 0, 0)

    companion object {
        const val DEFAULT_THRESHOLD = 0.85
        const val MAX_CANDIDATES = 2_500
        const val MAX_CANDIDATES_PER_FRAME = 1_000
    }
}