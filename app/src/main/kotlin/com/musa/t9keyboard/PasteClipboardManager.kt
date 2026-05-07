package com.musa.t9keyboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build

class PasteClipboardManager(private val context: Context) {

    companion object {
        const val PREVIEW_MIN_LENGTH = 5
        const val PREVIEW_MAX_LENGTH = 30
    }

    private val clipboard: ClipboardManager by lazy {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    /**
     * Cheaply checks if a pasteable, non-sensitive text clip exists.
     * Uses getPrimaryClipDescription() — does NOT trigger Android 12 toast.
     * Returns a preview string if eligible, null otherwise.
     */
    fun getClipPreview(): String? {
        val description = clipboard.primaryClipDescription ?: return null

        // Check sensitive flag (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true) {
                return null
            }
        } else {
            // Fallback key for API < 33
            if (description.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true) {
                return null
            }
        }

        // Only proceed if it contains plain text
        if (!description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) return null

        // Now do the actual read — this triggers the toast on API 31+ if from another app
        val text = clipboard.primaryClip
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            ?.trim()
            ?: return null

        // Enforce minimum length
        if (text.length < PREVIEW_MIN_LENGTH) return null

        return if (text.length > PREVIEW_MAX_LENGTH) {
            text.take(PREVIEW_MAX_LENGTH) + "…"
        } else {
            text
        }
    }

    /**
     * Reads and returns the full clipboard text for pasting.
     * Call only when user taps the paste bubble.
     */
    fun getFullClipText(): String? {
        return clipboard.primaryClip
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
    }
}
