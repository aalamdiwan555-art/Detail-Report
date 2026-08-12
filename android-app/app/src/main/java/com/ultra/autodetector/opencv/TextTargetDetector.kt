package com.ultra.autodetector.opencv

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * Offline text-target detector for apps that expose their visible labels through
 * Android's accessibility tree. It deliberately matches only the configured
 * approval phrases and does not inspect or transmit arbitrary user content.
 */
object TextTargetDetector {
    data class Match(
        val text: String,
        val centerX: Float,
        val centerY: Float,
    )

    private val acceptedPhrases = listOf(
        // English
        "accept", "Accept", "ACCEPT", "AcCePt", "aCcEpT", "ACcept", "acCEPT",
        "Accepted", "ACCEPTED", "accepted",
        // Hindi
        "स्वीकार करें", "स्वीकार करे", "स्वीकार करो", "स्वीकार", "स्वीकार करें।",
        // Gujarati
        "સ્વીકારો", "સ્વીકાર", "સ્વીકારો.",
        // Marathi
        "स्वीकारा", "स्वीकार करा",
        // Bengali
        "গ্রহণ করুন", "গ্রহণ করো",
        // Tamil
        "ஏற்கவும்", "ஏற்க",
        // Telugu
        "అంగీకరించు", "అంగీకరించండి",
        // Kannada
        "ಸ್ವೀಕರಿಸಿ", "ಸ್ವೀಕಾರ",
        // Malayalam
        "സ്വീകരിക്കുക", "സ്വീകരിക്കൂ",
        // Punjabi
        "ਸਵੀਕਾਰ ਕਰੋ", "ਸਵੀਕਾਰੋ",
        // Urdu
        "قبول کریں", "قبول",
        // Odia
        "ଗ୍ରହଣ କରନ୍ତୁ", "ଗ୍ରହଣ",
    ).map(::normalize).toSet()

    fun find(root: AccessibilityNodeInfo?): Match? {
        root ?: return null
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        var visited = 0
        while (pending.isNotEmpty() && visited++ < MAX_NODES) {
            val node = pending.removeFirst()
            val values = listOfNotNull(
                node.text?.toString(),
                node.contentDescription?.toString(),
            )
            val matchingText = values.firstOrNull(::isAccepted)
            if (matchingText != null) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (!bounds.isEmpty) {
                    return Match(
                        text = matchingText,
                        centerX = bounds.exactCenterX(),
                        centerY = bounds.exactCenterY(),
                    )
                }
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(pending::addLast)
            }
        }
        return null
    }

    private fun isAccepted(value: String): Boolean {
        val normalized = normalize(value)
        return normalizedTargetsMatch(normalized)
    }

    private fun normalizedTargetsMatch(value: String): Boolean =
        acceptedPhrases.any { value == it || value.contains(it) }

    private fun normalize(value: String): String =
        value
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd('.', '。')

    private const val MAX_NODES = 300
}