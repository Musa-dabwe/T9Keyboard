package com.musa.t9keyboard

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class ViewOrchestrator(
    private val service: T9InputMethodService,
    private val container: FrameLayout
) {

    var keyboardView: KeyboardView? = null
    var symbolsView: SymbolsView? = null
    var emojiPickerView: EmojiPickerView? = null
    var textEditingView: TextEditingView? = null

    private var isWindowVisible = false
    private var lastAppliedHeight = -1

    init {
        ViewCompat.setOnApplyWindowInsetsListener(container) { _, insets ->
            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val screenHeight = service.resources.displayMetrics.heightPixels
            val usableHeight = screenHeight - navBarHeight

            val isLandscape = service.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val heightPercent = if (isLandscape) 0.50f else 0.35f
            val calculatedHeight = (usableHeight * heightPercent).toInt()

            if (calculatedHeight != lastAppliedHeight && calculatedHeight > 0) {
                lastAppliedHeight = calculatedHeight
                updateKeyboardHeight(calculatedHeight)
            }
            insets
        }
    }

    private fun updateKeyboardHeight(height: Int) {
        val views = listOfNotNull(keyboardView, symbolsView, emojiPickerView, textEditingView)
        views.forEach { view ->
            val params = view.layoutParams ?: FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
            )
            params.height = height
            view.layoutParams = params
            if (view is SymbolsView) {
                view.refreshSymbolGrid()
            }
        }
    }

    fun setWindowVisible(visible: Boolean) {
        isWindowVisible = visible
    }

    fun showView(view: View, force: Boolean = false) {
        // Skip adding if it's already the only child
        if (container.childCount == 1 && container.getChildAt(0) === view) {
            return
        }

        // Suppress redundant view switches when window is not visible,
        // but allow the first view to be added even if hidden.
        if (!force && !isWindowVisible && container.childCount > 0) {
            return
        }

        container.removeAllViews()

        // Ensure height is applied before adding
        if (lastAppliedHeight > 0) {
            val params = view.layoutParams ?: FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                lastAppliedHeight
            )
            params.height = lastAppliedHeight
            view.layoutParams = params
        } else {
            view.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(304)
            )
        }

        container.addView(view)
        if (view is SymbolsView) {
            view.resetScroll()
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * service.resources.displayMetrics.density + 0.5f).toInt()
}
