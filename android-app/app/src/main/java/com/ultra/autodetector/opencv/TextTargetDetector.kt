package com.ultra.autodetector.opencv

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * All accepted approval labels. Matching is case-insensitive and whitespace/
 * punctuation tolerant, so callers only need to maintain this one list.
 */
val ACCEPT_ALL_VARIATIONS = listOf(
    // English
    "accept", "Accept", "ACCEPT", "AcCePt", "aCcEpT", "ACcept", "acCEPT",
    "Accepted", "ACCEPTED", "accepted",

    // Hindi
    "स्वीकार करें", "स्वीकार करे", "स्वीकार करो", "स्वीकार", "स्वीकार करें।",

    // Gujarati
    "સ્વીકારો", "સ્વીકાર", "સ્વીકારો.",

    // Marathi
    "स्वीकारा", "स्वीકાર करा",

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
)

/**
 * Case-insensitive substring matching with normalized whitespace and terminal
 * sentence punctuation. This is reusable by the accessibility detector and
 * keeps all accepted-language handling in one place.
 */
fun isSimilar(text: String, keyword: String): Boolean {
    val normalizedText = normalizeForMatch(text)
    val normalizedKeyword = normalizeForMatch(keyword)
    return normalizedKeyword.isNotEmpty() && normalizedText.contains(normalizedKeyword)
}

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

    private fun isAccepted(value: String): Boolean =
        ACCEPT_ALL_VARIATIONS.any { keyword -> isSimilar(value, keyword) }

    private const val MAX_NODES = 300
}

private fun normalizeForMatch(value: String): String =
    value
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
        .trim()
        .trimEnd('.', '。')