package com.musa.t9keyboard

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class ViewOrchestrator(
    private val service: T9InputMethodService
) {
    private var container: FrameLayout? = null
    var keyboardView: KeyboardView? = null
    var symbolsView: SymbolsView? = null
    var emojiPickerView: EmojiPickerView? = null
    var textEditingView: TextEditingView? = null
    var emojiSearchPanelView: EmojiSearchPanelView? = null

    var isViewReady = false
        private set
    private var isWindowVisible = false
    private var lastAppliedHeight = -1

    fun setContainer(container: FrameLayout) {
        this.container = container
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

    fun markViewReady() {
        isViewReady = true
    }

    private fun updateKeyboardHeight(height: Int) {
        val views = listOfNotNull(keyboardView, symbolsView, emojiPickerView, textEditingView, emojiSearchPanelView)
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
        val c = container ?: return

        if (c.childCount == 1 && c.getChildAt(0) === view) {
            return
        }

        if (!force && !isWindowVisible && c.childCount > 0) {
            return
        }

        c.removeAllViews()

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

        c.addView(view)
        if (view is SymbolsView) {
            view.resetScroll()
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * service.resources.displayMetrics.density + 0.5f).toInt()
}
