package com.musa.t9keyboard

import android.os.SystemClock
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

class InputConnectionManager(private val service: T9InputMethodService) {

    private val ic: InputConnection?
        get() = service.currentInputConnection

    fun commitText(text: String, cursorPosition: Int = 1) {
        ic?.commitText(text, cursorPosition)
    }

    fun setComposingText(text: CharSequence, cursorPosition: Int = 1) {
        ic?.setComposingText(text, cursorPosition)
    }

    fun finishComposingText() {
        ic?.finishComposingText()
    }

    fun deleteSurroundingText(before: Int, after: Int) {
        ic?.deleteSurroundingText(before, after)
    }

    fun sendKeyEvent(event: KeyEvent) {
        ic?.sendKeyEvent(event)
    }

    fun performContextMenuAction(id: Int) {
        ic?.performContextMenuAction(id)
    }

    fun getExtractedText(request: ExtractedTextRequest = ExtractedTextRequest(), flags: Int = 0) =
        ic?.getExtractedText(request, flags)

    fun getSelectedText(flags: Int = 0): CharSequence? =
        ic?.getSelectedText(flags)

    fun setSelection(start: Int, end: Int) {
        ic?.setSelection(start, end)
    }

    fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? =
        ic?.getTextBeforeCursor(n, flags)

    fun getTextAfterCursor(n: Int, flags: Int): CharSequence? =
        ic?.getTextAfterCursor(n, flags)

    fun beginBatchEdit() {
        ic?.beginBatchEdit()
    }

    fun endBatchEdit() {
        ic?.endBatchEdit()
    }

    fun sendDownUpKeyEvents(keyCode: Int, meta: Int = 0) {
        val now = SystemClock.uptimeMillis()
        ic?.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta, -1, 0, KeyEvent.FLAG_SOFT_KEYBOARD))
        ic?.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta, -1, 0, KeyEvent.FLAG_SOFT_KEYBOARD))
    }

    fun selectWord() {
        val before = getTextBeforeCursor(100, 0) ?: ""
        val after = getTextAfterCursor(100, 0) ?: ""

        var start = before.length
        while (start > 0 && before[start - 1].isLetterOrDigit()) {
            start--
        }

        var end = 0
        while (end < after.length && after[end].isLetterOrDigit()) {
            end++
        }

        val et = getExtractedText()
        if (et != null) {
            val cursor = et.selectionStart
            setSelection(cursor - (before.length - start), cursor + end)
        }
    }
}
