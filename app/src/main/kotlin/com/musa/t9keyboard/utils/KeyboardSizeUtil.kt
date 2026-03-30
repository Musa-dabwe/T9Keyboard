package com.musa.t9keyboard.utils

import android.content.Context
import android.content.res.Configuration
import androidx.core.view.WindowInsetsCompat

/**
 * Calculates the target keyboard height based on screen size and system insets.
 */
fun calculateKeyboardHeight(context: Context, windowInsets: WindowInsetsCompat): Int {
    val displayMetrics = context.resources.displayMetrics
    val screenHeight = displayMetrics.heightPixels

    val navBarHeight = windowInsets.getInsets(
        WindowInsetsCompat.Type.navigationBars()
    ).bottom

    // As per user instruction: Subtract nav bar only, not status bar.
    val usableHeight = screenHeight - navBarHeight

    val isLandscape = context.resources.configuration.orientation ==
        Configuration.ORIENTATION_LANDSCAPE

    return if (isLandscape) (usableHeight * 0.50).toInt()
           else (usableHeight * 0.35).toInt()
}
