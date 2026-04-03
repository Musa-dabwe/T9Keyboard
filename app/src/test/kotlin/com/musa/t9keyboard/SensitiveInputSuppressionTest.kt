package com.musa.t9keyboard

import android.view.inputmethod.EditorInfo
import org.junit.Assert.*
import org.junit.Test

class SensitiveInputSuppressionTest {

    @Test
    fun testIsInputTypeSensitive() {
        val info = EditorInfo()

        // 1. Password fields
        info.inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
        assertTrue("TEXT_VARIATION_PASSWORD should be sensitive", T9Utils.isInputTypeSensitive(info))

        info.inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        assertTrue("TEXT_VARIATION_VISIBLE_PASSWORD should be sensitive", T9Utils.isInputTypeSensitive(info))

        info.inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD
        assertTrue("TEXT_VARIATION_WEB_PASSWORD should be sensitive", T9Utils.isInputTypeSensitive(info))

        info.inputType = EditorInfo.TYPE_CLASS_NUMBER or EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD
        assertTrue("NUMBER_VARIATION_PASSWORD should be sensitive", T9Utils.isInputTypeSensitive(info))

        // 2. Class fields
        info.inputType = EditorInfo.TYPE_CLASS_NUMBER
        assertTrue("TYPE_CLASS_NUMBER should be sensitive", T9Utils.isInputTypeSensitive(info))

        info.inputType = EditorInfo.TYPE_CLASS_PHONE
        assertTrue("TYPE_CLASS_PHONE should be sensitive", T9Utils.isInputTypeSensitive(info))

        // 3. Normal fields
        info.inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_NORMAL
        assertFalse("Normal text should NOT be sensitive", T9Utils.isInputTypeSensitive(info))

        info.inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        assertFalse("Email address should NOT be sensitive", T9Utils.isInputTypeSensitive(info))
    }
}
