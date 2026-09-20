package ir.ali0003.downloader.util

import android.content.Intent

/**
 * Utility for cleanly extracting URLs from incoming Android Intents (ACTION_SEND, ACTION_VIEW)
 * and arbitrary text strings (such as clipboard contents containing titles or sentences).
 */
object IntentUrlExtractor {

    // Matches http:// and https:// URLs up to whitespace, quotes, angle brackets, or newlines
    private val RAW_URL_REGEX = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    /**
     * Extracts a target URL from an incoming Intent, handling:
     * 1. ACTION_VIEW with intent.dataString (browser click, custom scheme redirection)
     * 2. ACTION_SEND with text/plain (Android Share Sheet from YouTube, Instagram, X/Twitter, etc.)
     * 3. ClipData payload fallback
     * 4. EXTRA_SUBJECT fallback
     */
    fun extractUrl(intent: Intent?): String? {
        if (intent == null) return null
        val action = intent.action

        // 1. Direct ACTION_VIEW Intent with data URI
        if (Intent.ACTION_VIEW == action) {
            val dataUri = intent.dataString
            if (!dataUri.isNullOrBlank() && (dataUri.startsWith("http://", ignoreCase = true) || dataUri.startsWith("https://", ignoreCase = true))) {
                return cleanTrailingPunctuation(dataUri)
            }
        }

        // 2. ACTION_SEND Intent (Share Sheet)
        if (Intent.ACTION_SEND == action) {
            // Check EXTRA_TEXT first (most common)
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            val urlFromText = findUrlInString(text)
            if (urlFromText != null) return urlFromText

            // Check ClipData
            val clipData = intent.clipData
            if (clipData != null && clipData.itemCount > 0) {
                for (i in 0 until clipData.itemCount) {
                    val item = clipData.getItemAt(i)
                    val itemText = item.text?.toString()
                    val found = findUrlInString(itemText)
                    if (found != null) return found

                    val itemUri = item.uri?.toString()
                    if (!itemUri.isNullOrBlank() && (itemUri.startsWith("http://", ignoreCase = true) || itemUri.startsWith("https://", ignoreCase = true))) {
                        return cleanTrailingPunctuation(itemUri)
                    }
                }
            }

            // Fallback: Check EXTRA_SUBJECT
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            val urlFromSubject = findUrlInString(subject)
            if (urlFromSubject != null) return urlFromSubject
        }

        // 3. Fallback check for intent.data or intent.dataString on any action
        val fallbackUri = intent.dataString
        if (!fallbackUri.isNullOrBlank() && (fallbackUri.startsWith("http://", ignoreCase = true) || fallbackUri.startsWith("https://", ignoreCase = true))) {
            return cleanTrailingPunctuation(fallbackUri)
        }

        return null
    }

    /**
     * Extracts the first valid HTTP/HTTPS URL within an arbitrary text string,
     * such as sentences copied from social media share buttons (e.g. "Check this out: https://instagram.com/reel/123/ on Instagram").
     */
    fun findUrlInString(input: String?): String? {
        if (input.isNullOrBlank()) return null
        val match = RAW_URL_REGEX.find(input)?.value ?: return null
        return cleanTrailingPunctuation(match)
    }

    /**
     * Checks if a string is a valid HTTP/HTTPS URL.
     */
    fun isValidUrl(input: String?): Boolean {
        if (input.isNullOrBlank()) return false
        val trimmed = input.trim()
        return (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) &&
                trimmed.contains('.') &&
                !trimmed.contains(' ')
    }

    private fun cleanTrailingPunctuation(rawUrl: String): String {
        return rawUrl.trim().trimEnd('.', ',', '!', '?', ')', ']', '}', ';', ':', '"', '\'')
    }
}
