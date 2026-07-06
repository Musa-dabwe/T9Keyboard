package com.musa.t9keyboard

import android.view.inputmethod.EditorInfo

object T9Utils {
    // Key code → characters, derived from the single source of truth in KeyboardLayout
    private val DIGIT_MAP: Map<Char, String> = KeyboardLayout.codeToChars

    private val CHAR_TO_DIGIT: Map<Char, Char> = mutableMapOf<Char, Char>().apply {
        DIGIT_MAP.forEach { (digit, letters) ->
            letters.forEach { put(it, digit) }
        }
    }

    fun getDigitForChar(c: Char): Char {
        return CHAR_TO_DIGIT[c.lowercaseChar()] ?: ' '
    }

    fun getFirstCharForDigit(digit: Char): Char {
        return DIGIT_MAP[digit]?.firstOrNull() ?: ' '
    }

    /**
     * True if [c] is a key code (an ambiguous whole-key constraint in a T9 sequence),
     * as opposed to an exact letter constraint. Codes include non-digit symbols
     * ('*', '#', '+', '/') now that the layout has more than ten letter keys.
     */
    fun isKeyCode(c: Char): Boolean = DIGIT_MAP.containsKey(c)

    fun getT9Sequence(word: String): String {
        return word.lowercase().filter { it in 'a'..'z' }
            .map { CHAR_TO_DIGIT[it] ?: ' ' }.joinToString("").trim()
    }

    fun isInputTypeSensitive(info: EditorInfo?): Boolean {
        if (info == null) return false
        val inputType = info.inputType
        val classType = inputType and EditorInfo.TYPE_MASK_CLASS
        val variation = inputType and EditorInfo.TYPE_MASK_VARIATION

        if (classType == EditorInfo.TYPE_CLASS_TEXT) {
            if (variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD) {
                return true
            }
        }

        if (classType == EditorInfo.TYPE_CLASS_NUMBER) {
            return true
        }

        if (classType == EditorInfo.TYPE_CLASS_PHONE) {
            return true
        }

        // Check for sensitive field hints/labels
        val hint = info.hintText?.toString()?.lowercase() ?: ""
        val label = info.label?.toString()?.lowercase() ?: ""
        val combined = "$hint $label"
        val sensitiveKeywords = listOf("password", "pin", "credit", "card", "cvv", "ssn", "social", "secret")
        if (sensitiveKeywords.any { combined.contains(it) }) return true

        return false
    }
}
