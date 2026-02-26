package com.musa.t9keyboard

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.Toast

class T9InputMethodService : InputMethodService() {

    private lateinit var container: FrameLayout
    private lateinit var keyboardView: KeyboardView
    private lateinit var symbolsView: SymbolsView
    private lateinit var emojiPickerView: EmojiPickerView
    private lateinit var textEditingView: TextEditingView
    private lateinit var preferences: PreferencesManager
    private val shiftManager = ShiftStateManager()

    private var currentWordConstraints = mutableListOf<String>()
    private var composingText = StringBuilder()
    private var lastCommittedWord: String? = null

    private var xt9DigitSequence = StringBuilder()
    private var xt9RawSequence = StringBuilder()
    private var currentXt9Predictions = listOf<String>()
    private var isSelectionMode = false
    private var selectionAnchor = -1
    private var movingPosition = -1

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
        AospDictionary.loadFromAssets(this)
        LearnedDictionary.load(this)
        AospBigrams.loadFromAssets(this)
        preferences = PreferencesManager(this)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        resetImeState(attribute, resetShift = true)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        resetImeState(info, resetShift = false)
        keyboardView.setMultiTapTimeout(preferences.multiTapTimeout)
        keyboardView.setKeyFontSize(preferences.keyFontSize.toFloat())
        keyboardView.setFontSize(preferences.suggestionFontSize.toFloat())
        keyboardView.isXt9Mode = preferences.xt9Enabled
        val accentColor = androidx.core.content.ContextCompat.getColor(this, accentColorResIds[preferences.accentColorIndex])
        keyboardView.setAccentColor(accentColor)
        emojiPickerView.setAccentColor(accentColor)
        textEditingView.setAccentColor(accentColor)
    }

    override fun onFinishInput() {
        super.onFinishInput()
        resetImeState(null, resetShift = true)
    }

    override fun onCreateInputView(): View {
        container = FrameLayout(this)

        keyboardView = KeyboardView(this)
        symbolsView = SymbolsView(this)
        emojiPickerView = EmojiPickerView(this)
        textEditingView = TextEditingView(this)

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
            commitTextWithFinalization(symbol)
        }

        symbolsView.onBackClickListener = {
            showView(keyboardView)
        }

        symbolsView.onDeleteClickListener = {
            handleAction(KeyboardView.KeyboardAction.DEL)
        }

        emojiPickerView.onEmojiClickListener = { emoji ->
            commitTextWithFinalization(emoji)
        }

        emojiPickerView.onBackspaceClick = {
            handleAction(KeyboardView.KeyboardAction.DEL)
        }

        emojiPickerView.onBackClickListener = {
            showView(keyboardView)
        }

        textEditingView.onAction = { action ->
            handleEditAction(action)
        }

        textEditingView.onAbcClick = {
            isSelectionMode = false
            textEditingView.setSelectionMode(false)
            showView(keyboardView)
        }

        textEditingView.onFeedbackRequested = {
            performFeedback()
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
        val digit = getDigitForChar(char)
        val isPunctuation = (digit == '1')

        // Handle digit mode or long press
        if (char.isDigit()) {
            commitTextWithFinalization(char.toString())
            return
        }

        // On the very first tap of a new multi-tap sequence
        if (tapCount == 0 && !isFinished) {
            // If it's a punctuation key, finalize any active word before starting the symbol cycle
            if (isPunctuation) {
                finalizeCurrentComposing()
            }

            // Auto-capitalization check for new words
            if (composingText.isEmpty() && xt9DigitSequence.isEmpty()) {
                checkAutoCap()
            }
        }

        val displayChar = if (shiftManager.currentState != ShiftState.OFF) char.uppercaseChar() else char

        if (isPunctuation) {
            handlePunctuationTap(displayChar, tapCount, isFinished)
        } else {
            if (preferences.xt9Enabled) {
                handleXt9Tap(char)
            } else {
                handleLetterMultiTap(displayChar, char, tapCount, isFinished)
            }
        }
    }

    private fun handlePunctuationTap(displayChar: Char, tapCount: Int, isFinished: Boolean) {
        val ic = currentInputConnection ?: return

        if (isFinished) {
            // Finalize the punctuation immediately as requested
            if (composingText.isNotEmpty()) {
                val textToCommit = composingText.toString()
                ic.commitText(textToCommit, 1)
                composingText.setLength(0)
                currentWordConstraints.clear()

                shiftManager.consumeShift()
                keyboardView.updateShiftState(shiftManager.currentState)
            }
            keyboardView.setSuggestions(emptyList())
        } else {
            // Cycling or first tap
            if (tapCount == 0) {
                composingText.setLength(0)
                composingText.append(displayChar)
                currentWordConstraints.clear()
                currentWordConstraints.add("1")
            } else {
                if (composingText.isNotEmpty()) {
                    composingText.setCharAt(composingText.length - 1, displayChar)
                } else {
                    composingText.append(displayChar)
                    currentWordConstraints.clear()
                    currentWordConstraints.add("1")
                }
            }
            ic.setComposingText(composingText, 1)
        }
    }

    private fun handleLetterMultiTap(displayChar: Char, rawChar: Char, tapCount: Int, isFinished: Boolean) {
        val ic = currentInputConnection ?: return

        if (isFinished) {
            // For letters, we stay in composing state after timeout (Option A)
            if (composingText.isNotEmpty()) {
                composingText.setCharAt(composingText.length - 1, displayChar)
                if (currentWordConstraints.isNotEmpty()) {
                    currentWordConstraints[currentWordConstraints.size - 1] = rawChar.toString()
                }
                ic.setComposingText(composingText, 1)
                updateSuggestions()
                shiftManager.consumeShift()
                keyboardView.updateShiftState(shiftManager.currentState)
            }
        } else {
            // cycling or new key tap
            if (tapCount == 0) {
                composingText.append(displayChar)
                currentWordConstraints.add(getDigitForChar(rawChar).toString())
            } else {
                if (composingText.isNotEmpty()) {
                    composingText.setCharAt(composingText.length - 1, displayChar)
                    if (currentWordConstraints.isNotEmpty()) {
                        currentWordConstraints[currentWordConstraints.size - 1] = rawChar.toString()
                    }
                }
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
                    if (currentWordConstraints.isNotEmpty()) {
                        currentWordConstraints.removeAt(currentWordConstraints.size - 1)
                    }
                    ic.setComposingText(composingText, 1)
                    if (composingText.isEmpty()) {
                        ic.finishComposingText()
                    }
                    updateSuggestions()
                } else {
                    val selectedText = ic.getSelectedText(0)
                    if (!selectedText.isNullOrEmpty()) {
                        ic.commitText("", 1)
                    } else {
                        ic.deleteSurroundingText(1, 0)
                    }
                }
            }
            KeyboardView.KeyboardAction.SPACE -> {
                commitTextWithFinalization(" ")
                updateNextWordSuggestions()
            }
            KeyboardView.KeyboardAction.ENTER -> {
                commitTextWithFinalization("")
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
                        emojiPickerView.resetScroll()
                        showView(emojiPickerView)
                    } else {
                        android.util.Log.e("T9InputMethodService", "Emoji picker not initialized correctly")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("T9InputMethodService", "Error showing emoji picker", e)
                }
            }
            KeyboardView.KeyboardAction.SHOW_TEXT_EDITING -> {
                showTextEditingPanel()
            }
            KeyboardView.KeyboardAction.SETTINGS -> {
                val intent = android.content.Intent(this, SettingsActivity::class.java)
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
            KeyboardView.KeyboardAction.SWITCH_KEYBOARD -> {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
            KeyboardView.KeyboardAction.TOGGLE_XT9 -> {
                val newState = !preferences.xt9Enabled
                if (preferences.xt9Enabled && !newState) {
                    commitCurrentComposing()
                }
                preferences.xt9Enabled = newState
                keyboardView.isXt9Mode = newState
                val status = if (newState) "On" else "Off"
                Toast.makeText(this, "XT9 $status", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun commitCurrentComposing() {
        finalizeCurrentComposing(moveCursorToEnd = true)
    }

    private fun finalizeCurrentComposing(moveCursorToEnd: Boolean = true) {
        val ic = currentInputConnection ?: return
        if (composingText.isEmpty() && xt9DigitSequence.isEmpty()) return

        var committedWord: String? = null

        if (preferences.xt9Enabled && xt9DigitSequence.isNotEmpty()) {
            val suggestions = currentXt9Predictions
            val wordToCommit = if (suggestions.isNotEmpty()) suggestions[0] else xt9RawSequence.toString()
            val finalWord = applyShiftState(wordToCommit)

            if (moveCursorToEnd) {
                ic.commitText(finalWord, 1)
            } else {
                ic.finishComposingText()
            }
            committedWord = finalWord
            LearnedDictionary.learnWord(wordToCommit, lastCommittedWord)
            lastCommittedWord = wordToCommit

            xt9DigitSequence.setLength(0)
            xt9RawSequence.setLength(0)
            currentXt9Predictions = emptyList()

            shiftManager.consumeShift()
            keyboardView.updateShiftState(shiftManager.currentState)
        } else if (composingText.isNotEmpty()) {
            val word = composingText.toString()
            if (moveCursorToEnd) {
                ic.setComposingText(word, 1)
            }
            ic.finishComposingText()
            committedWord = word
            LearnedDictionary.learnWord(word, lastCommittedWord)
            lastCommittedWord = word
            composingText.clear()
            currentWordConstraints.clear()
            shiftManager.consumeShift()
            keyboardView.updateShiftState(shiftManager.currentState)
        }

        if (committedWord == "i" && moveCursorToEnd) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText("I", 1)
            lastCommittedWord = "I"
        }
        keyboardView.setSuggestions(emptyList())
    }

    private fun commitTextWithFinalization(text: String, addSpaceAfter: Boolean = false) {
        val ic = currentInputConnection ?: return
        finalizeCurrentComposing(moveCursorToEnd = true)

        if (text.isNotEmpty()) {
            ic.commitText(text, 1)
        }
        if (addSpaceAfter) {
            ic.commitText(" ", 1)
        }
        shiftManager.consumeShift()
        keyboardView.updateShiftState(shiftManager.currentState)
    }

    private fun commitSuggestion(suggestion: String) {
        val ic = currentInputConnection ?: return
        if (preferences.xt9Enabled) {
            // Find which suggestion was clicked to get the un-capitalized version for learning
            val originalWord = currentXt9Predictions.find { applyShiftState(it) == suggestion }
                ?: if (applyShiftState(xt9RawSequence.toString()) == suggestion) xt9RawSequence.toString() else suggestion

            ic.commitText(suggestion, 1)
            ic.commitText(" ", 1)
            LearnedDictionary.learnWord(originalWord, lastCommittedWord)
            lastCommittedWord = originalWord
            xt9DigitSequence.setLength(0)
            xt9RawSequence.setLength(0)
            currentXt9Predictions = emptyList()
        } else {
            ic.setComposingText(suggestion, 1)
            ic.finishComposingText()
            ic.commitText(" ", 1)
            LearnedDictionary.learnWord(suggestion, lastCommittedWord)
            lastCommittedWord = suggestion
            composingText.clear()
            currentWordConstraints.clear()
        }
        shiftManager.consumeShift()
        keyboardView.updateShiftState(shiftManager.currentState)
        updateNextWordSuggestions()
    }

    private fun updateSuggestions() {
        if (composingText.isEmpty() || composingText.all { !it.isLetter() }) {
            keyboardView.setSuggestions(emptyList())
            return
        }
        val learned = LearnedDictionary.getSuggestions(currentWordConstraints)
        val aosp = AospDictionary.getSuggestions(currentWordConstraints)
        val combined = (learned + aosp).distinctBy { it.word }
            .sortedByDescending { it.frequency }
            .map { it.word }
            .take(3)
        keyboardView.setSuggestions(combined)
    }

    private fun updateNextWordSuggestions() {
        val word = lastCommittedWord ?: return
        val learned = LearnedDictionary.getNextWordSuggestions(word)
        val aosp = AospBigrams.getNextWordSuggestions(word)
        val combined = (learned + aosp).distinct().take(3)
        keyboardView.setSuggestions(combined)
    }

    private fun sendDownUpKeyEvents(keyCode: Int, meta: Int) {
        val ic = currentInputConnection ?: return
        val now = System.currentTimeMillis()
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta))
    }

    private fun showView(view: View) {
        container.removeAllViews()
        if (view is EmojiPickerView || view is TextEditingView) {
            view.layoutParams = FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(304)
            )
        }
        container.addView(view)
        if (view is SymbolsView) {
            view.resetScroll()
        }
        if (view is TextEditingView) {
            updateEditingSelectionState()
        }
    }

    private fun showTextEditingPanel() {
        updateEditingSelectionState()
        showView(textEditingView)
    }

    private fun handleEditAction(action: TextEditingView.EditAction) {
        val ic = currentInputConnection ?: return

        // Finalize any active composing text before performing edit actions,
        // unless it's a delete action which handles its own internal buffer.
        if (action != TextEditingView.EditAction.DELETE) {
            finalizeCurrentComposing(moveCursorToEnd = false)
        }

        val et = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return
        val textLength = et.text.length
        val selectionStart = et.selectionStart
        val selectionEnd = et.selectionEnd

        if (isSelectionMode && movingPosition == -1) {
            movingPosition = selectionEnd
        }

        when (action) {
            TextEditingView.EditAction.HOME -> {
                if (isSelectionMode) {
                    movingPosition = 0
                    ic.setSelection(selectionAnchor, movingPosition)
                } else {
                    ic.setSelection(0, 0)
                }
            }
            TextEditingView.EditAction.HOME_LONG -> {
                if (isSelectionMode) {
                    movingPosition = 0
                    ic.setSelection(selectionAnchor, movingPosition)
                } else {
                    ic.setSelection(0, 0)
                }
            }
            TextEditingView.EditAction.END -> {
                if (isSelectionMode) {
                    movingPosition = textLength
                    ic.setSelection(selectionAnchor, movingPosition)
                } else {
                    ic.setSelection(textLength, textLength)
                }
            }
            TextEditingView.EditAction.END_LONG -> {
                if (isSelectionMode) {
                    movingPosition = textLength
                    ic.setSelection(selectionAnchor, movingPosition)
                } else {
                    ic.setSelection(textLength, textLength)
                }
            }
            TextEditingView.EditAction.UP -> {
                if (selectionStart > 0) {
                    if (!isSelectionMode && selectionStart != selectionEnd) {
                        ic.setSelection(selectionStart, selectionStart)
                    }
                    val meta = if (isSelectionMode) KeyEvent.META_SHIFT_ON else 0
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP, meta)
                }
            }
            TextEditingView.EditAction.DOWN -> {
                if (selectionEnd < textLength) {
                    if (!isSelectionMode && selectionStart != selectionEnd) {
                        ic.setSelection(selectionEnd, selectionEnd)
                    }
                    val meta = if (isSelectionMode) KeyEvent.META_SHIFT_ON else 0
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN, meta)
                }
            }
            TextEditingView.EditAction.LEFT -> {
                val currentPos = if (isSelectionMode) movingPosition else selectionStart
                if (currentPos > 0) {
                    val newPos = currentPos - 1
                    if (isSelectionMode) {
                        movingPosition = newPos
                        ic.setSelection(selectionAnchor, movingPosition)
                    } else {
                        ic.setSelection(newPos, newPos)
                    }
                }
            }
            TextEditingView.EditAction.RIGHT -> {
                val currentPos = if (isSelectionMode) movingPosition else selectionEnd
                if (currentPos < textLength) {
                    val newPos = currentPos + 1
                    if (isSelectionMode) {
                        movingPosition = newPos
                        ic.setSelection(selectionAnchor, movingPosition)
                    } else {
                        ic.setSelection(newPos, newPos)
                    }
                }
            }
            TextEditingView.EditAction.SELECT_ALL -> {
                if (selectionStart == 0 && selectionEnd == textLength && textLength > 0) {
                    ic.setSelection(selectionEnd, selectionEnd)
                } else {
                    ic.performContextMenuAction(android.R.id.selectAll)
                }
            }
            TextEditingView.EditAction.SELECT -> {
                isSelectionMode = !isSelectionMode
                textEditingView.setSelectionMode(isSelectionMode)
                if (isSelectionMode) {
                    selectionAnchor = selectionStart
                    movingPosition = selectionEnd
                } else {
                    ic.setSelection(movingPosition, movingPosition)
                    selectionAnchor = -1
                    movingPosition = -1
                }
            }
            TextEditingView.EditAction.SELECT_WORD -> {
                selectWord()
            }
            TextEditingView.EditAction.COPY -> {
                ic.performContextMenuAction(android.R.id.copy)
            }
            TextEditingView.EditAction.COPY_LONG -> {
                ic.performContextMenuAction(android.R.id.copy)
                Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
            }
            TextEditingView.EditAction.CUT -> {
                ic.performContextMenuAction(android.R.id.cut)
            }
            TextEditingView.EditAction.CUT_LONG -> {
                ic.performContextMenuAction(android.R.id.cut)
                Toast.makeText(this, "Cut", Toast.LENGTH_SHORT).show()
            }
            TextEditingView.EditAction.PASTE -> {
                ic.performContextMenuAction(android.R.id.paste)
            }
            TextEditingView.EditAction.PASTE_LONG -> {
                val intent = android.content.Intent("android.intent.action.CLIPBOARD_MANAGER")
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    ic.performContextMenuAction(android.R.id.paste)
                }
            }
            TextEditingView.EditAction.SELECT_LEFT_WORD -> {
                if (selectionStart > 0) {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
                }
            }
            TextEditingView.EditAction.SELECT_LEFT_WORD_LONG -> {
                if (selectionStart > 0) {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.META_SHIFT_ON)
                }
            }
            TextEditingView.EditAction.SELECT_RIGHT_WORD -> {
                if (selectionEnd < textLength) {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
                }
            }
            TextEditingView.EditAction.SELECT_RIGHT_WORD_LONG -> {
                if (selectionEnd < textLength) {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_MOVE_END, KeyEvent.META_SHIFT_ON)
                }
            }
            TextEditingView.EditAction.UNDO -> {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON)
            }
            TextEditingView.EditAction.REDO -> {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
            }
            TextEditingView.EditAction.DELETE -> {
                handleAction(KeyboardView.KeyboardAction.DEL)
            }
        }
        updateEditingSelectionState()
    }

    private fun selectWord() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(100, 0) ?: ""
        val after = ic.getTextAfterCursor(100, 0) ?: ""

        var start = before.length
        while (start > 0 && before[start - 1].isLetterOrDigit()) {
            start--
        }

        var end = 0
        while (end < after.length && after[end].isLetterOrDigit()) {
            end++
        }

        val et = ic.getExtractedText(ExtractedTextRequest(), 0)
        if (et != null) {
            val cursor = et.selectionStart
            ic.setSelection(cursor - (before.length - start), cursor + end)
        }
    }

    private fun updateEditingSelectionState() {
        val ic = currentInputConnection ?: return
        val selectedText = ic.getSelectedText(0)
        val hasSelection = !selectedText.isNullOrEmpty()
        if (::textEditingView.isInitialized) {
            textEditingView.updateSelectionState(hasSelection)
        }
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)

        // Detect if the cursor moved outside of the current composing region (manual move)
        val isManualMove = candidatesStart != -1 && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)

        if (candidatesStart == -1 || isManualMove) {
            finalizeCurrentComposing(moveCursorToEnd = false)
        }

        if (isSelectionMode) {
            if (newSelStart == selectionAnchor) {
                movingPosition = newSelEnd
            } else if (newSelEnd == selectionAnchor) {
                movingPosition = newSelStart
            } else {
                selectionAnchor = newSelStart
                movingPosition = newSelEnd
            }
        }

        if (::textEditingView.isInitialized && textEditingView.parent != null) {
            updateEditingSelectionState()
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density + 0.5f).toInt()

    private fun handleXt9Tap(char: Char) {
        if (composingText.isNotEmpty()) {
            commitTextWithFinalization("")
        }
        if (xt9DigitSequence.isEmpty()) {
            checkAutoCap()
        }
        val digit = getDigitForChar(char)
        if (digit == ' ' || digit == '1') {
            // For now, if it's not a dictionary key, just commit current word and handle it normally
            // But prompt says "Tap each key once — keyboard predicts the word"
            // If it's the punctuation key, maybe we should handle it.
            // But let's stick to 2-9 for dictionary.
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

        val learned = LearnedDictionary.getSuggestionsForSequence(xt9DigitSequence.toString())
        val aosp = AospDictionary.getSuggestionsForSequence(xt9DigitSequence.toString())

        var combined = (learned + aosp).sortedByDescending { it.frequency }
            .distinctBy { it.word.lowercase() }

        if (combined.isEmpty() && xt9DigitSequence.length >= 3) {
            // Fallback to prefix completions
            combined = AospDictionary.getWordsStartingWith(xt9DigitSequence.toString())
                .sortedByDescending { it.frequency }
        }

        currentXt9Predictions = combined.map { it.word }.take(3)

        val displayPredictions = currentXt9Predictions.toMutableList()

        if (displayPredictions.isEmpty()) {
            displayPredictions.add(xt9RawSequence.toString())
        }

        val capitalizedPredictions = displayPredictions.map { applyShiftState(it) }
        keyboardView.setSuggestions(capitalizedPredictions, xt9RawSequence.toString())

        val activeCandidate = capitalizedPredictions[0]
        currentInputConnection?.setComposingText(activeCandidate, 1)
    }

    private fun applyShiftState(text: String): String {
        return when (shiftManager.currentState) {
            ShiftState.ONE_SHOT -> text.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            ShiftState.CAPS_LOCK -> text.uppercase()
            else -> text
        }
    }

    private fun checkAutoCap() {
        if (shiftManager.wasShiftSetManually || shiftManager.currentState != ShiftState.OFF) return

        val ic = currentInputConnection ?: return
        val textBefore = ic.getTextBeforeCursor(3, 0)
        if (textBefore != null && textBefore.length >= 2) {
            val lastTwo = textBefore.substring(textBefore.length - 2)
            if (lastTwo == ". " || lastTwo == "! " || lastTwo == "? ") {
                shiftManager.setAutoShift(ShiftState.ONE_SHOT)
                keyboardView.updateShiftState(shiftManager.currentState)
            }
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
            '.', ',', '?', '!' -> '1'
            else -> ' '
        }
    }

    private fun resetImeState(info: EditorInfo?, resetShift: Boolean = true) {
        isSelectionMode = false
        selectionAnchor = -1
        movingPosition = -1
        if (::textEditingView.isInitialized) {
            textEditingView.setSelectionMode(false)
        }
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
            if (::container.isInitialized) {
                showView(keyboardView)
            }
        }

        if (resetShift) {
            shiftManager.reset()
            info?.let {
                val inputType = it.inputType
                val capsFlags = inputType and android.view.inputmethod.EditorInfo.TYPE_MASK_FLAGS
                val variation = inputType and android.view.inputmethod.EditorInfo.TYPE_MASK_VARIATION

                if (capsFlags and android.view.inputmethod.EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0) {
                    shiftManager.onDoubleTap() // CAPS_LOCK
                } else {
                    // Rule 2: Field start capitalization
                    val ic = currentInputConnection
                    val isAtStart = ic?.getTextBeforeCursor(1, 0)?.isEmpty() ?: true

                    if (isAtStart) {
                        val isSentenceCap = (capsFlags and android.view.inputmethod.EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES != 0)
                        val isPlainText = (inputType and android.view.inputmethod.EditorInfo.TYPE_MASK_CLASS == android.view.inputmethod.EditorInfo.TYPE_CLASS_TEXT) &&
                                         (variation == android.view.inputmethod.EditorInfo.TYPE_TEXT_VARIATION_NORMAL || variation == android.view.inputmethod.EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)

                        if (isSentenceCap || isPlainText) {
                            shiftManager.setAutoShift(ShiftState.ONE_SHOT)
                        }
                    }
                }
            }
        }
        if (::keyboardView.isInitialized) {
            keyboardView.updateShiftState(shiftManager.currentState)
        }
    }
}
