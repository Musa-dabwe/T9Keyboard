package com.musa.t9keyboard

import android.view.inputmethod.EditorInfo

object T9Utils {
    private val DIGIT_MAP: Map<Char, String> = mapOf(
        '2' to "abc",
        '3' to "def",
        '4' to "ghi",
        '5' to "jkl",
        '6' to "mno",
        '7' to "pqrs",
        '8' to "tuv",
        '9' to "wxyz",
        '1' to ".,?!"
    )

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
