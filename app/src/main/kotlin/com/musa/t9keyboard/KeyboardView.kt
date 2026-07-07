package com.musa.t9keyboard

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.musa.t9keyboard.databinding.KeyboardViewBinding

class KeyboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: KeyboardViewBinding = KeyboardViewBinding.inflate(LayoutInflater.from(context), this, true)
    private val touchHandler: KeyTouchHandler
    private val labelRenderer: KeyLabelRenderer

    var onActionClickListener: ((KeyboardAction) -> Unit)? = null
    var onMultiTapListener: ((Char, Int, Boolean) -> Unit)? = null
    var onFeedbackRequested: (() -> Unit)? = null
    /** Fired when the user swipes down on the suggestion bar while the number pad is open. */
    var onNumPadSwipeDown: (() -> Unit)? = null

    private var isNumMode = false
    private var lastShiftState: ShiftState = ShiftState.OFF
    var isXt9Mode = false
        set(value) {
            field = value
            binding.suggestionBar.setXt9Mode(value)
            touchHandler.setupKeys(isNumMode, value)
        }

    enum class KeyboardAction {
        SHIFT, CAPS_LOCK, DEL, ENTER, SPACE, SYM, NUM, COMMA, EMOJI, PERIOD, SETTINGS, SWITCH_KEYBOARD, TOGGLE_XT9, SHOW_TEXT_EDITING
    }

    init {
        touchHandler = KeyTouchHandler(this, binding, { c, tc, f -> onMultiTapListener?.invoke(c, tc, f) }, { a -> onActionClickListener?.invoke(a) }, { onFeedbackRequested?.invoke() })
        labelRenderer = KeyLabelRenderer(this, binding)

        labelRenderer.applyUbuntuFont()
        touchHandler.setupKeys(isNumMode, isXt9Mode)
        labelRenderer.updateKeyLabels(isNumMode, lastShiftState)
        binding.suggestionBar.onSwipeDownListener = { onNumPadSwipeDown?.invoke() }
    }

    fun resetState() {
        touchHandler.resetState()
        isNumMode = false
        binding.suggestionBar.swipeDownEnabled = false
        labelRenderer.updateKeyAccent(binding.keyShift, false)
        labelRenderer.updateKeyLabels(isNumMode, lastShiftState)
        touchHandler.setupKeys(isNumMode, isXt9Mode)
    }

    fun setMultiTapTimeout(timeout: Long) = touchHandler.setMultiTapTimeout(timeout)
    fun setDeletionSpeed(speed: Int) = touchHandler.setDeletionSpeed(speed)
    fun setFontSize(size: Float) = binding.suggestionBar.setFontSize(size)
    fun setSuggestions(suggestions: List<String>, anchoredWord: String? = null) = binding.suggestionBar.setSuggestions(suggestions, anchoredWord)
    fun showAutocorrectIndicator(correctedWord: String) = binding.suggestionBar.showAutocorrectIndicator(correctedWord)
    fun showToolbar() = binding.suggestionBar.showToolbar()
    fun showSuggestions() = binding.suggestionBar.showSuggestions()
    fun setOnToolbarActionClickListener(listener: (SuggestionBar.ToolbarAction) -> Unit) { binding.suggestionBar.onToolbarActionClickListener = listener }
    fun setOnSuggestionClickListener(listener: (String) -> Unit) { binding.suggestionBar.onSuggestionClickListener = listener }
    fun setKeyFontSize(sizeSp: Float) = labelRenderer.setKeyFontSize(sizeSp)

    fun setAccentColor(color: Int) {
        labelRenderer.setAccentColor(color, isNumMode, lastShiftState)
        touchHandler.setAccentColor(color)
    }

    fun setTheme(theme: KeyboardTheme) {
        setBackgroundColor(theme.background)
        binding.root.setBackgroundColor(theme.background)
        labelRenderer.setTheme(theme, isNumMode, lastShiftState)
        binding.suggestionBar.setTheme(theme)
    }

    fun updateShiftState(state: ShiftState) {
        lastShiftState = state
        labelRenderer.updateShiftState(state, isNumMode)
    }

    fun toggleNumMode() {
        isNumMode = !isNumMode
        binding.suggestionBar.swipeDownEnabled = isNumMode
        labelRenderer.updateKeyLabels(isNumMode, lastShiftState)
        labelRenderer.updateKeyAccent(binding.keyShift, isNumMode)
        touchHandler.setupKeys(isNumMode, isXt9Mode)
        if (!isNumMode) updateShiftState(lastShiftState)
    }
}
