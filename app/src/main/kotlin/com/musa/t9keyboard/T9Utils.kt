package com.musa.t9keyboard

import android.view.inputmethod.EditorInfo

object T9Utils {
    fun getDigitForChar(c: Char): Char {
        return when (c.lowercaseChar()) {
            'a', 'b', 'c' -> '2'
            'd', 'e', 'f' -> '3'
            'g', 'h', 'i' -> '4'
            'j', 'k', 'l' -> '5'
            'm', 'n', 'o' -> '6'
            'p', 'q', 'r', 's' -> '7'
            't', 'u', 'v' -> '8'
            'w', 'x', 'y', 'z' -> '9'
            '.', ',', '?', '!' -> '1'
            else -> ' '
        }
    }

    fun getFirstCharForDigit(digit: Char): Char {
        return when (digit) {
            '2' -> 'a'
            '3' -> 'd'
            '4' -> 'g'
            '5' -> 'j'
            '6' -> 'm'
            '7' -> 'p'
            '8' -> 't'
            '9' -> 'w'
            '1' -> '.'
            else -> ' '
        }
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

        return false
    }
}
