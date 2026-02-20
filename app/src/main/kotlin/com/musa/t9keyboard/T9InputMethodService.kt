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

    private var xt9DigitSequence = StringBuilder()
    private var xt9RawSequence = StringBuilder()
    private var currentXt9Predictions = listOf<String>()

    private val accentColorResIds = listOf(
        R.color.accent_blue,
        R.color.accent_teal,
        R.color.accent_green,
        R.color.accent_yellow,
        R.color.accent_magenta,
        R.color.accent_red,
        R.color.accent_orange,
        R.color.accent_purple
    )

    override fun onCreate() {
        super.onCreate()
        dictionary = T9Dictionary(this)
        preferences = PreferencesManager(this)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        resetImeState(attribute)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        resetImeState(info)
        keyboardView.setMultiTapTimeout(preferences.multiTapTimeout)
        keyboardView.setKeyFontSize(preferences.keyFontSize.toFloat())
        keyboardView.setFontSize(preferences.suggestionFontSize.toFloat())
        keyboardView.isXt9Mode = preferences.xt9Enabled
        val accentColor = androidx.core.content.ContextCompat.getColor(this, accentColorResIds[preferences.accentColorIndex])
        keyboardView.setAccentColor(accentColor)
        emojiPickerView.setAccentColor(accentColor)
    }

    override fun onFinishInput() {
        super.onFinishInput()
        resetImeState(null)
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
            handleMultiTap(char, tapCount, isFinished)
        }

        keyboardView.onActionClickListener = { action ->
            handleAction(action)
        }

        keyboardView.onFeedbackRequested = {
            performFeedback()
        }

        keyboardView.setOnSuggestionClickListener { suggestion ->
            performFeedback()
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
        if (preferences.xt9Enabled) {
            handleXt9Tap(char)
            return
        }
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
                if (preferences.xt9Enabled && xt9DigitSequence.isNotEmpty()) {
                    xt9DigitSequence.deleteCharAt(xt9DigitSequence.length - 1)
                    xt9RawSequence.deleteCharAt(xt9RawSequence.length - 1)
                    if (xt9DigitSequence.isEmpty()) {
                        ic.finishComposingText()
                    }
                    updateXt9Suggestions()
                } else if (!preferences.xt9Enabled && composingText.isNotEmpty()) {
                    composingText.deleteCharAt(composingText.length - 1)
                    currentWordConstraints.removeAt(currentWordConstraints.size - 1)
                    ic.setComposingText(composingText, 1)
                    if (composingText.isEmpty()) {
                        ic.finishComposingText()
                    }
                    updateSuggestions()
                } else {
                    ic.deleteSurroundingText(1, 0)
                }
            }
            KeyboardView.KeyboardAction.SPACE -> {
                if (preferences.xt9Enabled) {
                    commitXt9Word()
                } else {
                    commitWord()
                }
                ic.commitText(" ", 1)
                updateNextWordSuggestions()
            }
            KeyboardView.KeyboardAction.ENTER -> {
                if (preferences.xt9Enabled) {
                    commitXt9Word()
                } else {
                    commitWord()
                }
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
                try {
                    if (emojiPickerView.isInitialized) {
                        showView(emojiPickerView)
                    } else {
                        android.util.Log.e("T9InputMethodService", "Emoji picker not initialized correctly")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("T9InputMethodService", "Error showing emoji picker", e)
                }
            }
            KeyboardView.KeyboardAction.SETTINGS -> {
                val intent = android.content.Intent(this, SettingsActivity::class.java)
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
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
        if (preferences.xt9Enabled) {
            // Find which suggestion was clicked to get the un-capitalized version for learning
            val originalWord = currentXt9Predictions.find { applyShiftState(it) == suggestion }
                ?: if (applyShiftState(xt9RawSequence.toString()) == suggestion) xt9RawSequence.toString() else suggestion

            ic.commitText(suggestion, 1)
            ic.commitText(" ", 1)
            dictionary.learnWord(originalWord, lastCommittedWord)
            lastCommittedWord = originalWord
            xt9DigitSequence.setLength(0)
            xt9RawSequence.setLength(0)
            currentXt9Predictions = emptyList()
        } else {
            ic.setComposingText(suggestion, 1)
            ic.finishComposingText()
            ic.commitText(" ", 1)
            dictionary.learnWord(suggestion, lastCommittedWord)
            lastCommittedWord = suggestion
            composingText.clear()
            currentWordConstraints.clear()
        }
        shiftManager.consumeShift()
        keyboardView.updateShiftState(shiftManager.currentState)
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

    private fun handleXt9Tap(char: Char) {
        val digit = getDigitForChar(char)
        if (digit == ' ' || digit == '1') {
            // For now, if it's not a dictionary key, just commit current word and handle it normally
            // But prompt says "Tap each key once — keyboard predicts the word"
            // If it's the punctuation key, maybe we should handle it.
            // But let's stick to 2-9 for dictionary.
            if (digit == '1') {
                // Punctuation key
                xt9DigitSequence.append('1')
                xt9RawSequence.append('.')
                updateXt9Suggestions()
            }
            return
        }

        xt9DigitSequence.append(digit)
        xt9RawSequence.append(getFirstCharForDigit(digit))
        updateXt9Suggestions()
    }

    private fun updateXt9Suggestions() {
        if (xt9DigitSequence.isEmpty()) {
            keyboardView.setSuggestions(emptyList())
            return
        }

        currentXt9Predictions = dictionary.xt9Predict(xt9DigitSequence.toString())
        val displayPredictions = currentXt9Predictions.toMutableList()

        if (displayPredictions.isEmpty()) {
            displayPredictions.add(xt9RawSequence.toString())
        }

        val capitalizedPredictions = displayPredictions.map { applyShiftState(it) }
        keyboardView.setSuggestions(capitalizedPredictions, xt9RawSequence.toString())

        val activeCandidate = capitalizedPredictions[0]
        currentInputConnection?.setComposingText(activeCandidate, 1)
    }

    private fun commitXt9Word() {
        val ic = currentInputConnection ?: return
        if (xt9DigitSequence.isNotEmpty()) {
            val suggestions = currentXt9Predictions
            val wordToCommit = if (suggestions.isNotEmpty()) suggestions[0] else xt9RawSequence.toString()
            val finalWord = applyShiftState(wordToCommit)

            ic.commitText(finalWord, 1)
            dictionary.learnWord(wordToCommit, lastCommittedWord)
            lastCommittedWord = wordToCommit

            xt9DigitSequence.setLength(0)
            xt9RawSequence.setLength(0)
            currentXt9Predictions = emptyList()

            shiftManager.consumeShift()
            keyboardView.updateShiftState(shiftManager.currentState)
        }
    }

    private fun applyShiftState(text: String): String {
        return when (shiftManager.currentState) {
            ShiftState.ON -> text.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            ShiftState.CAPS_LOCK -> text.uppercase()
            else -> text
        }
    }

    private fun getFirstCharForDigit(digit: Char): Char {
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
            '.', ',', '?', '!', ':', ';' -> '1'
            else -> ' '
        }
    }

    private fun resetImeState(info: EditorInfo?) {
        composingText.setLength(0)
        currentWordConstraints.clear()
        xt9DigitSequence.setLength(0)
        xt9RawSequence.setLength(0)
        currentXt9Predictions = emptyList()
        lastCommittedWord = null

        currentInputConnection?.finishComposingText()
        if (::keyboardView.isInitialized) {
            keyboardView.resetState()
            keyboardView.setSuggestions(emptyList())
        }

        shiftManager.reset()
        info?.let {
            val capsFlags = it.inputType and android.view.inputmethod.EditorInfo.TYPE_MASK_FLAGS
            if (capsFlags and android.view.inputmethod.EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0) {
                shiftManager.onDoubleTap() // CAPS_LOCK
            } else if (capsFlags and (android.view.inputmethod.EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS or android.view.inputmethod.EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0) {
                // For words or sentences, start with SHIFT ON
                // Actually, let's just use ON for simplicity, it will be consumed after first char
                if (shiftManager.currentState == ShiftState.OFF) {
                    shiftManager.toggle()
                }
            }
        }
        if (::keyboardView.isInitialized) {
            keyboardView.updateShiftState(shiftManager.currentState)
        }
    }
}
