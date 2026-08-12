package com.ultra.autodetector.detector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.ultra.autodetector.data.model.Template
import com.ultra.autodetector.opencv.TemplateMatcher as BitmapTemplateMatcher
import java.util.Locale

/**
 * Combines normalized bitmap correlation with optional on-device OCR.
 *
 * The current repository intentionally keeps the OpenCV adapter dependency-free
 * so the imported app remains buildable on a stock Android SDK. The bitmap
 * matcher exposes the same normalized-correlation result contract, and this
 * wrapper adds the required scale pyramid and ML Kit path.
 */
class TemplateMatcher(context: Context) : AutoCloseable {
    enum class Source { IMAGE, TEXT }

    data class Match(
        val template: Template,
        val rect: Rect,
        val confidence: Double,
        val source: Source,
    )

    private val imageMatcher = BitmapTemplateMatcher()
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun findBest(
        screen: Bitmap,
        templates: List<LoadedTemplate>,
        runText: Boolean,
    ): Match? {
        val imageMatch = templates.asSequence()
            .mapNotNull { candidate -> findImage(screen, candidate) }
            .maxByOrNull { it.confidence }
        if (imageMatch != null && imageMatch.confidence >= IMAGE_SHORT_CIRCUIT_CONFIDENCE) {
            return imageMatch
        }
        if (!runText || templates.isEmpty()) return imageMatch

        val text = runCatching {
            Tasks.await(recognizer.process(InputImage.fromBitmap(screen, 0)))
        }.onFailure { Log.w(TAG, "ML Kit text recognition failed", it) }.getOrNull()
            ?: return imageMatch
        val textMatch = text.textBlocks.asSequence()
            .mapNotNull { block ->
                val bounds = block.boundingBox ?: return@mapNotNull null
                templates.asSequence()
                    .mapNotNull { candidate ->
                        val score = candidate.textScore(block.text)
                        if (score < 2) Match(candidate.template, bounds, 1.0 - score / 2.0, Source.TEXT)
                        else null
                    }
                    .maxByOrNull { it.confidence }
            }
            .maxByOrNull { it.confidence }
        return listOfNotNull(imageMatch, textMatch).maxByOrNull { it.confidence }
    }

    override fun close() {
        recognizer.close()
    }

    private fun findImage(screen: Bitmap, candidate: LoadedTemplate): Match? {
        var best: Match? = null
        SCALE_FACTORS.forEach { scale ->
            val width = (candidate.bitmap.width * scale).toInt().coerceAtLeast(1)
            val height = (candidate.bitmap.height * scale).toInt().coerceAtLeast(1)
            if (width > screen.width || height > screen.height) return@forEach
            val scaled = if (width == candidate.bitmap.width && height == candidate.bitmap.height) {
                candidate.bitmap
            } else {
                Bitmap.createScaledBitmap(candidate.bitmap, width, height, true)
            }
            val result = imageMatcher.match(
                screen,
                scaled,
                candidate.template.confidenceThreshold,
                BitmapTemplateMatcher.MAX_CANDIDATES_PER_FRAME,
            )
            if (scaled !== candidate.bitmap) scaled.recycle()
            if (result.found && (best == null || result.confidence > best!!.confidence)) {
                best = Match(
                    candidate.template,
                    Rect(result.left, result.top, result.left + result.width, result.top + result.height),
                    result.confidence,
                    Source.IMAGE,
                )
            }
        }
        return best
    }

    class LoadedTemplate(
        val template: Template,
        val bitmap: Bitmap,
    ) {
        fun textScore(value: String): Int {
            val normalized = normalize(value)
            return listOf(template.name, template.description)
                .filter(String::isNotBlank)
                .map {
                    val target = normalize(it)
                    if (normalized.contains(target) || target.contains(normalized)) 0
                    else levenshtein(normalized, target)
                }
                .minOrNull() ?: Int.MAX_VALUE
        }
    }

    companion object {
        private const val TAG = "TemplateMatcher"
        private const val IMAGE_SHORT_CIRCUIT_CONFIDENCE = 0.95
        private val SCALE_FACTORS = doubleArrayOf(0.8, 0.9, 1.0, 1.1)

        private fun normalize(value: String): String =
            value.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()

        private fun levenshtein(left: String, right: String): Int {
            if (left == right) return 0
            if (left.isEmpty()) return right.length
            if (right.isEmpty()) return left.length
            val previous = IntArray(right.length + 1) { it }
            val current = IntArray(right.length + 1)
            left.forEachIndexed { row, leftChar ->
                current[0] = row + 1
                right.forEachIndexed { column, rightChar ->
                    current[column + 1] = minOf(
                        current[column] + 1,
                        previous[column + 1] + 1,
                        previous[column] + if (leftChar == rightChar) 0 else 1,
                    )
                }
                previous.indices.forEach { previous[it] = current[it] }
            }
            return previous[right.length]
        }
    }
}