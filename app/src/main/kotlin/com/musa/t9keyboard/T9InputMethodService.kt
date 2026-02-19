package com.musa.t9keyboard

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout

class T9InputMethodService : InputMethodService() {

    private lateinit var container: FrameLayout
    private lateinit var keyboardView: KeyboardView
    private lateinit var symbolsView: SymbolsView
    private lateinit var emojiPickerView: EmojiPickerView
    private lateinit var dictionary: T9Dictionary
    private lateinit var preferences: PreferencesManager
    private val shiftManager = ShiftStateManager()

    private var currentWordConstraints = mutableListOf<String>()
    private var composingText = StringBuilder()
    private var lastCommittedWord: String? = null

    override fun onCreate() {
        super.onCreate()
        dictionary = T9Dictionary(this)
        preferences = PreferencesManager(this)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboardView.setMultiTapTimeout(preferences.multiTapTimeout)
        keyboardView.setKeyFontSize(preferences.keyFontSize.toFloat())
        keyboardView.setFontSize(preferences.suggestionFontSize.toFloat())
    }

    override fun onCreateInputView(): View {
        container = FrameLayout(this)

        keyboardView = KeyboardView(this)
        symbolsView = SymbolsView(this)
        emojiPickerView = EmojiPickerView(this)

        setupListeners()

        container.addView(keyboardView)
        return container
    }

    private fun setupListeners() {
        keyboardView.onMultiTapListener = { char, tapCount, isFinished ->
            performFeedback()
            handleMultiTap(char, tapCount, isFinished)
        }

        keyboardView.onActionClickListener = { action ->
            performFeedback()
            handleAction(action)
        }

        keyboardView.setOnSuggestionClickListener { suggestion ->
            commitSuggestion(suggestion)
        }

        symbolsView.onSymbolClickListener = { symbol ->
            currentInputConnection.commitText(symbol, 1)
        }

        symbolsView.onBackClickListener = {
            showView(keyboardView)
        }

        emojiPickerView.onEmojiClickListener = { emoji ->
            currentInputConnection.commitText(emoji, 1)
            showView(keyboardView)
        }

        emojiPickerView.onBackClickListener = {
            showView(keyboardView)
        }
    }

    /**
     * Handles T9 multi-tap input logic.
     *
     * Multi-tap Timeout Logic:
     * 1. When a key is tapped, a timer starts (multiTapTimeout).
     * 2. If the same key is tapped again before the timer expires:
     *    - The timer is reset.
     *    - The character cycles to the next one in the group (e.g., 'a' -> 'b').
     *    - The current composing character is updated in the input field.
     * 3. If a different key is tapped, or the timer expires:
     *    - The current character is considered "committed" for the purpose of the current word.
     *    - The cycle resets.
     * 4. Each tap (whether new or cycling) updates the T9Dictionary constraints,
     *    triggering a refresh of the Suggestion Bar.
     */
    private fun handleMultiTap(char: Char, tapCount: Int, isFinished: Boolean) {
        val ic = currentInputConnection ?: return

        val displayChar = if (shiftManager.currentState != ShiftState.OFF) char.uppercaseChar() else char

        if (isFinished) {
            // Last char of the cycle is committed
            if (composingText.isNotEmpty()) {
                composingText.setCharAt(composingText.length - 1, displayChar)
                currentWordConstraints[currentWordConstraints.size - 1] = char.toString()
            }
            ic.setComposingText(composingText, 1)
            updateSuggestions()
        } else {
            // Still cycling or first tap of a new key
            if (tapCount == 0) {
                // First tap of a new key
                composingText.append(displayChar)
                currentWordConstraints.add(getDigitForChar(char).toString())
            } else {
                // Subsequent tap of same key
                composingText.setCharAt(composingText.length - 1, displayChar)
                currentWordConstraints[currentWordConstraints.size - 1] = char.toString()
            }
            ic.setComposingText(composingText, 1)
            updateSuggestions()
        }
    }

    private fun handleAction(action: KeyboardView.KeyboardAction) {
        val ic = currentInputConnection ?: return
        when (action) {
            KeyboardView.KeyboardAction.DEL -> {
                if (composingText.isNotEmpty()) {
                    composingText.deleteCharAt(composingText.length - 1)
                    currentWordConstraints.removeAt(currentWordConstraints.size - 1)
                    ic.setComposingText(composingText, 1)
                    if (composingText.isEmpty()) {
                        ic.finishComposingText()
                    }
                } else {
                    ic.deleteSurroundingText(1, 0)
                }
                updateSuggestions()
            }
            KeyboardView.KeyboardAction.SPACE -> {
                commitWord()
                ic.commitText(" ", 1)
                updateNextWordSuggestions()
            }
            KeyboardView.KeyboardAction.ENTER -> {
                commitWord()
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            }
            KeyboardView.KeyboardAction.SHIFT -> {
                shiftManager.toggle()
                keyboardView.updateShiftState(shiftManager.currentState)
            }
            KeyboardView.KeyboardAction.CAPS_LOCK -> {
                shiftManager.onDoubleTap()
                keyboardView.updateShiftState(shiftManager.currentState)
            }
            KeyboardView.KeyboardAction.SYM -> {
                showView(symbolsView)
            }
            KeyboardView.KeyboardAction.NUM -> {
                keyboardView.toggleNumMode()
            }
            KeyboardView.KeyboardAction.EMOJI -> {
                showView(emojiPickerView)
            }
        }
    }

    private fun commitWord() {
        val ic = currentInputConnection ?: return
        if (composingText.isNotEmpty()) {
            val word = composingText.toString()
            ic.finishComposingText()
            dictionary.learnWord(word, lastCommittedWord)
            lastCommittedWord = word
            composingText.clear()
            currentWordConstraints.clear()
            shiftManager.consumeShift()
            keyboardView.updateShiftState(shiftManager.currentState)
        }
    }

    private fun commitSuggestion(suggestion: String) {
        val ic = currentInputConnection ?: return
        ic.setComposingText(suggestion, 1)
        ic.finishComposingText()
        ic.commitText(" ", 1)
        dictionary.learnWord(suggestion, lastCommittedWord)
        lastCommittedWord = suggestion
        composingText.clear()
        currentWordConstraints.clear()
        updateNextWordSuggestions()
    }

    private fun updateSuggestions() {
        if (composingText.isEmpty()) {
            keyboardView.setSuggestions(emptyList())
            return
        }
        val suggestions = dictionary.getSuggestions(currentWordConstraints)
        keyboardView.setSuggestions(suggestions)
    }

    private fun updateNextWordSuggestions() {
        val word = lastCommittedWord ?: return
        val suggestions = dictionary.getNextWordSuggestions(word)
        keyboardView.setSuggestions(suggestions)
    }

    private fun showView(view: View) {
        container.removeAllViews()
        container.addView(view)
    }

    private fun performFeedback() {
        if (preferences.hapticEnabled) {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(preferences.hapticDuration.toLong(), android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(preferences.hapticDuration.toLong())
            }
        }
        if (preferences.soundEnabled) {
            val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
            am.playSoundEffect(android.view.SoundEffectConstants.CLICK, preferences.soundVolume)
        }
    }

    private fun getDigitForChar(c: Char): Char {
        return when (c.lowercaseChar()) {
            'a', 'b', 'c' -> '2'
            'd', 'e', 'f' -> '3'
            'g', 'h', 'i' -> '4'
            'j', 'k', 'l' -> '5'
            'm', 'n', 'o' -> '6'
            'p', 'q', 'r', 's' -> '7'
            't', 'u', 'v' -> '8'
            'w', 'x', 'y', 'z' -> '9'
            else -> ' '
        }
    }
}
