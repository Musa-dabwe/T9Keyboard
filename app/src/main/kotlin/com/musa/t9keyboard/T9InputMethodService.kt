package com.musa.t9keyboard

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.Toast
import android.text.InputType
import android.Manifest
import android.content.pm.PackageManager
import android.os.Vibrator
import android.view.LayoutInflater
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.emoji2.text.EmojiCompat
import androidx.emoji2.text.DefaultEmojiCompatConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class T9InputMethodService : InputMethodService(), MainKeyActionListener, EditActionListener, EmojiActionListener {

    private var container: FrameLayout? = null
    private lateinit var orchestrator: ViewOrchestrator
    private lateinit var editorState: EditorState
    private lateinit var icManager: InputConnectionManager
    private lateinit var suggestionEngine: SuggestionEngine

    private lateinit var preferences: PreferencesManager
    private val shiftManager = ShiftStateManager()

    private var isSelectionMode = false
    private var selectionAnchor = -1
    private var movingPosition = -1
    private var isWindowVisible = false
    private var contactPermissionGranted = false
    private var contactSuggestionsEnabled = false
    private var xt9Enabled = false
    private var isInputSensitive = false
    private var currentPackageName: String = ""
    private var isEmojiSearchActive = false
    private var clipboardUsed = false
    private val pasteManager: PasteClipboardManager by lazy { PasteClipboardManager(this) { clipboardUsed = false } }
    private var pasteBubble: android.widget.TextView? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val accentColorResIds = listOf(
        R.color.accent_blue, R.color.accent_teal, R.color.accent_green,
        R.color.accent_yellow, R.color.accent_magenta, R.color.accent_red,
        R.color.accent_orange, R.color.accent_purple
    )

    override fun onDestroy() {
        pasteManager.unregister()
        super.onDestroy()
        (serviceScope.coroutineContext[Job])?.cancel()
    }

    override fun onCreate() {
        pasteManager.register()
        super.onCreate()
        preferences = PreferencesManager(this)
        serviceScope.launch {
            AospDictionary.loadFromAssets(this@T9InputMethodService)
            AospBigrams.loadFromAssets(this@T9InputMethodService)
        }
        LearnedDictionary.load(this)

        editorState = EditorState()
        icManager = InputConnectionManager(this)
        orchestrator = ViewOrchestrator(this)
        suggestionEngine = SuggestionEngine(serviceScope, { contactSuggestionsEnabled && contactPermissionGranted }) { suggestions, anchored ->
            try {
                if (xt9Enabled && editorState.xt9DigitSequence.isEmpty()) {
                    orchestrator.keyboardView?.setSuggestions(suggestions, null)
                    return@SuggestionEngine
                }

                if (xt9Enabled) {
                    editorState.currentXt9Predictions = if (anchored != null) listOf(anchored) + suggestions else suggestions
                    val displayPredictions = editorState.currentXt9Predictions.toMutableList()
                    val rawFallback = editorState.xt9RawSequence.toString()
                    if (displayPredictions.isEmpty()) displayPredictions.add(rawFallback)

                    val capitalizedPredictions = displayPredictions.map { applyShiftState(it) }
                    val displayAnchored = if (capitalizedPredictions.isNotEmpty()) capitalizedPredictions[0] else applyShiftState(rawFallback)
                    val others = if (capitalizedPredictions.size > 1) capitalizedPredictions.drop(1).take(20) else emptyList()

                    orchestrator.keyboardView?.setSuggestions(others, displayAnchored)
                    icManager.setComposingText(displayAnchored, 1)
                } else {
                    orchestrator.keyboardView?.setSuggestions(suggestions, anchored)
                }
            } catch (e: Exception) {
                // Silent
            }
        }
        xt9Enabled = preferences.xt9Enabled
        contactSuggestionsEnabled = preferences.contactSuggestionsEnabled

        try {
            val config = DefaultEmojiCompatConfig.create(this)
            if (config != null) {
                EmojiCompat.init(config)
            }
        } catch (e: Exception) {
            // Silent
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentPackageName = info?.packageName ?: ""
        isInputSensitive = T9Utils.isInputTypeSensitive(info)
        resetImeState(info, resetShift = !restarting)
        updatePasteBubble(info)
    }

    override fun onCreateInputView(): View {
        return try {
            val themedContext = android.view.ContextThemeWrapper(this, R.style.AppTheme)
            val container = FrameLayout(themedContext)
            this.container = container
            orchestrator.setContainer(container)

            orchestrator.keyboardView = KeyboardView(themedContext).apply {
                onActionClickListener = { a -> onActionClick(a) }
                onMultiTapListener = { c, tc, f -> onMultiTap(c, tc, f) }
                onFeedbackRequested = { this@T9InputMethodService.onFeedbackRequested() }
                setOnSuggestionClickListener { s -> onSuggestionClick(s) }
                setOnToolbarActionClickListener { a -> onToolbarActionClick(a) }
                pasteBubble = findViewById(R.id.paste_bubble)
                pasteBubble?.setOnClickListener { onPasteBubbleTapped() }
            }
            orchestrator.symbolsView = SymbolsView(themedContext).apply {
                onSymbolClickListener = { s -> commitTextWithFinalization(s) }
                onBackClickListener = { onBackClick() }
                onDeleteClickListener = { onActionClick(KeyboardView.KeyboardAction.DEL) }
                onFeedbackRequested = { this@T9InputMethodService.onFeedbackRequested() }
                onSwipeDownListener = { onBackClick() }
            }
            orchestrator.emojiPickerView = EmojiPickerView(themedContext).apply {
                onEmojiClickListener = { e -> onEmojiClick(e) }
                onBackspaceClick = { this@T9InputMethodService.onBackspaceClick() }
                onBackClickListener = { onBackClick() }
                onFeedbackRequested = { this@T9InputMethodService.onFeedbackRequested() }
                onSwipeDownListener = { onBackClick() }
                onSearchTriggered = { onEmojiSearchTriggered() }
            }

            val searchPanel = LayoutInflater.from(themedContext).inflate(R.layout.emoji_search_panel, container, false) as EmojiSearchPanelView
            orchestrator.emojiSearchPanelView = searchPanel.apply {
                listener = object : EmojiSearchPanelView.Listener {
                    override fun onEmojiSelected(emoji: String) {
                        onEmojiClick(emoji)
                    }
                    override fun onCloseRequested() {
                        onBackClick()
                    }
                    override fun onFeedbackRequested() {
                        this@T9InputMethodService.onFeedbackRequested()
                    }
                    override fun onSearchTriggered() {
                        onEmojiSearchTriggered()
                    }
                }
            }

            orchestrator.textEditingView = TextEditingView(themedContext).apply {
                onAction = { a -> onEditAction(a) }
                onAbcClick = { this@T9InputMethodService.onAbcClick() }
                on123Click = { this@T9InputMethodService.on123Click() }
                onSymClick = { this@T9InputMethodService.onSymClick() }
                onEmojiClick = { this@T9InputMethodService.onEmojiClick() }
                onFeedbackRequested = { this@T9InputMethodService.onFeedbackRequested() }
                onSwipeDownListener = { onBackClick() }
            }

            orchestrator.markViewReady()
            orchestrator.showView(orchestrator.keyboardView!!, force = true)
            container
        } catch (e: Exception) {
            View(this)
        }
    }

    override fun onMultiTap(char: Char, tapCount: Int, isFinished: Boolean) {
        editorState.lastTapTime = System.currentTimeMillis()
        val digit = T9Utils.getDigitForChar(char)
        val isPunctuation = (digit == '1')

        if (char.isDigit()) {
            commitTextWithFinalization(char.toString())
            editorState.lastDigit = ' '
            return
        }

        if (tapCount == 0 && !isFinished) {
            if (isPunctuation && editorState.lastDigit != '1') {
                finalizeCurrentComposing()
            }
            if (editorState.composingText.isEmpty() && editorState.xt9DigitSequence.isEmpty()) {
                editorState.lastDigit = digit
            }
        }

        if (xt9Enabled && !isPunctuation) {
            if (tapCount == 0 && !isFinished) {
                editorState.xt9DigitSequence.append(digit)
                editorState.xt9RawSequence.append(char)
                suggestionEngine.requestSuggestions(editorState, xt9Enabled, isInputSensitive)
            }
        } else {
            if (isFinished) {
                editorState.composingText.append(applyShiftState(char.toString()))
                icManager.finishComposingText()
                editorState.composingText.clear()
                checkAutoCap()
            } else {
                icManager.setComposingText(applyShiftState(char.toString()), 1)
            }
        }
    }

    override fun onActionClick(action: KeyboardView.KeyboardAction) {
        when (action) {
            KeyboardView.KeyboardAction.DEL -> {
                if (xt9Enabled && editorState.xt9DigitSequence.isNotEmpty()) {
                    editorState.xt9DigitSequence.deleteCharAt(editorState.xt9DigitSequence.length - 1)
                    editorState.xt9RawSequence.deleteCharAt(editorState.xt9RawSequence.length - 1)
                    if (editorState.xt9DigitSequence.isEmpty()) {
                        icManager.finishComposingText()
                        orchestrator.keyboardView?.setSuggestions(emptyList(), null)
                    } else {
                        suggestionEngine.requestSuggestions(editorState, xt9Enabled, isInputSensitive)
                    }
                } else if (editorState.composingText.isNotEmpty()) {
                    editorState.composingText.deleteCharAt(editorState.composingText.length - 1)
                    if (editorState.composingText.isEmpty()) {
                        icManager.finishComposingText()
                    } else {
                        icManager.setComposingText(editorState.composingText, 1)
                    }
                } else {
                    icManager.deleteSurroundingText(1, 0)
                }
                checkAutoCap()
            }
            KeyboardView.KeyboardAction.SPACE -> commitTextWithFinalization(" ", true)
            KeyboardView.KeyboardAction.ENTER -> {
                finalizeCurrentComposing()
                icManager.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                icManager.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
            KeyboardView.KeyboardAction.ABC -> {
                finalizeCurrentComposing()
                orchestrator.keyboardView?.let { orchestrator.showView(it) }
            }
            KeyboardView.KeyboardAction.NUM, KeyboardView.KeyboardAction.SYM -> {
                finalizeCurrentComposing()
                orchestrator.symbolsView?.let { orchestrator.showView(it) }
            }
            KeyboardView.KeyboardAction.EMOJI -> {
                finalizeCurrentComposing()
                orchestrator.emojiPickerView?.let { orchestrator.showView(it) }
            }
            KeyboardView.KeyboardAction.SHIFT -> {
                shiftManager.toggle()
                orchestrator.keyboardView?.updateShiftState(shiftManager.currentState)
            }
            KeyboardView.KeyboardAction.CAPS_LOCK -> {
                shiftManager.onDoubleTap()
                orchestrator.keyboardView?.updateShiftState(shiftManager.currentState)
            }
            KeyboardView.KeyboardAction.TOGGLE_XT9 -> toggleXt9()
            KeyboardView.KeyboardAction.SETTINGS -> {
                val intent = android.content.Intent(this, SettingsActivity::class.java)
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
            KeyboardView.KeyboardAction.SHOW_TEXT_EDITING -> showTextEditingPanel()
            KeyboardView.KeyboardAction.COMMA -> commitTextWithFinalization(",", false)
            KeyboardView.KeyboardAction.PERIOD -> commitTextWithFinalization(".", false)
            KeyboardView.KeyboardAction.SWITCH_KEYBOARD -> {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showInputMethodPicker()
            }
        }
    }

    override fun onToolbarActionClick(action: SuggestionBar.ToolbarAction) {
        when (action) {
            SuggestionBar.ToolbarAction.SETTINGS -> {
                val intent = android.content.Intent(this, SettingsActivity::class.java)
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
            SuggestionBar.ToolbarAction.EDIT -> showTextEditingPanel()
            SuggestionBar.ToolbarAction.TOGGLE_XT9 -> toggleXt9()
        }
    }

    override fun onSuggestionClick(suggestion: String) {
        val finalSuggestion = applyShiftState(suggestion)
        icManager.commitText(finalSuggestion, 1)

        val shouldLearn = !isInputSensitive && !preferences.isAppBlacklisted(currentPackageName)
        if (shouldLearn && xt9Enabled && editorState.xt9DigitSequence.isNotEmpty()) {
            LearnedDictionary.learnWord(suggestion, editorState.lastCommittedWord)
        }

        editorState.lastCommittedWord = suggestion
        editorState.xt9DigitSequence.setLength(0)
        editorState.xt9RawSequence.setLength(0)
        editorState.composingText.clear()
        orchestrator.keyboardView?.setSuggestions(emptyList(), null)
        shiftManager.consumeShift()
        orchestrator.keyboardView?.updateShiftState(shiftManager.currentState)
    }

    override fun onEmojiClick(emoji: String) {
        icManager.commitText(emoji, 1)
    }

    override fun onBackspaceClick() {
        icManager.deleteSurroundingText(1, 0)
    }

    override fun onBackClick() {
        if (isEmojiSearchActive) {
            isEmojiSearchActive = false
            orchestrator.emojiPickerView?.let { orchestrator.showView(it) }
            return
        }
        orchestrator.keyboardView?.let { orchestrator.showView(it) }
    }

    override fun onSearchTriggered() {
        onEmojiSearchTriggered()
    }

    private fun onEmojiSearchTriggered() {
        isEmojiSearchActive = true
        orchestrator.emojiSearchPanelView?.let {
            val colorRes = accentColorResIds[preferences.accentColorIndex]
            val color = ContextCompat.getColor(this, colorRes)
            it.setAccentColor(color)
            it.resetQuery()
            orchestrator.showView(it)
        }
    }

    override fun onEditAction(action: TextEditingView.EditAction) {
        when (action) {
            TextEditingView.EditAction.UP -> icManager.sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP)
            TextEditingView.EditAction.DOWN -> icManager.sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN)
            TextEditingView.EditAction.LEFT -> icManager.sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT)
            TextEditingView.EditAction.RIGHT -> icManager.sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT)
            TextEditingView.EditAction.SELECT_ALL -> {
                val et = icManager.getExtractedText()
                if (et != null) {
                    icManager.setSelection(0, et.text.length)
                }
            }
            TextEditingView.EditAction.COPY -> {
                icManager.performContextMenuAction(android.R.id.copy)
                isSelectionMode = false
                orchestrator.textEditingView?.setSelectionMode(false)
            }
            TextEditingView.EditAction.CUT -> {
                icManager.performContextMenuAction(android.R.id.cut)
                isSelectionMode = false
                orchestrator.textEditingView?.setSelectionMode(false)
            }
            TextEditingView.EditAction.PASTE -> icManager.performContextMenuAction(android.R.id.paste)
            TextEditingView.EditAction.ENTER -> onActionClick(KeyboardView.KeyboardAction.ENTER)
            TextEditingView.EditAction.DELETE -> onActionClick(KeyboardView.KeyboardAction.DEL)
            TextEditingView.EditAction.SELECT -> {
                isSelectionMode = !isSelectionMode
                orchestrator.textEditingView?.setSelectionMode(isSelectionMode)
            }
            else -> {}
        }
    }

    override fun onAbcClick() {
        onActionClick(KeyboardView.KeyboardAction.ABC)
    }

    override fun on123Click() {
        onActionClick(KeyboardView.KeyboardAction.NUM)
    }

    override fun onSymClick() {
        onActionClick(KeyboardView.KeyboardAction.SYM)
    }

    override fun onEmojiClick() {
        onActionClick(KeyboardView.KeyboardAction.EMOJI)
    }

    override fun onFeedbackRequested() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (preferences.hapticEnabled) {
            vibrator.vibrate(preferences.hapticDuration.toLong())
        }
    }

    private fun finalizeCurrentComposing(moveCursorToEnd: Boolean = true) {
        if (editorState.composingText.isEmpty() && editorState.xt9DigitSequence.isEmpty()) return

        val currentPackage = currentPackageName
        val shouldLearn = !isInputSensitive && !preferences.isAppBlacklisted(currentPackage)

        var committedWord: String? = null
        if (xt9Enabled && editorState.xt9DigitSequence.isNotEmpty()) {
            val wordToCommit = if (editorState.currentXt9Predictions.isNotEmpty()) editorState.currentXt9Predictions[0] else editorState.xt9RawSequence.toString()
            val finalWord = applyShiftState(wordToCommit)
            if (moveCursorToEnd) icManager.commitText(finalWord, 1) else icManager.finishComposingText()
            committedWord = finalWord
            if (shouldLearn) {
                try {
                    LearnedDictionary.learnWord(wordToCommit, editorState.lastCommittedWord)
                } catch (e: Exception) {
                    // Silent
                }
            }
            editorState.lastCommittedWord = wordToCommit
            editorState.xt9DigitSequence.setLength(0)
            editorState.xt9RawSequence.setLength(0)
            editorState.currentXt9Predictions = emptyList()
        } else if (editorState.composingText.isNotEmpty()) {
            val word = editorState.composingText.toString()
            if (moveCursorToEnd) icManager.setComposingText(word, 1)
            icManager.finishComposingText()
            committedWord = word
            if (shouldLearn) {
                try {
                    LearnedDictionary.learnWord(word, editorState.lastCommittedWord)
                } catch (e: Exception) {
                    // Silent
                }
            }
            editorState.lastCommittedWord = word
            editorState.composingText.clear()
        }
        shiftManager.consumeShift()
        orchestrator.keyboardView?.updateShiftState(shiftManager.currentState)
        orchestrator.keyboardView?.setSuggestions(emptyList(), null)
        editorState.lastDigit = ' '
    }

    private fun commitTextWithFinalization(text: String, addSpaceAfter: Boolean = false) {
        finalizeCurrentComposing(moveCursorToEnd = true)
        if (text.isNotEmpty()) {
            icManager.commitText(text, 1)
        }
        if (addSpaceAfter) {
            icManager.commitText(" ", 1)
        }
        shiftManager.consumeShift()
        orchestrator.keyboardView?.updateShiftState(shiftManager.currentState)
    }

    private fun showTextEditingPanel() {
        updateEditingSelectionState()
        orchestrator.textEditingView?.let { orchestrator.showView(it) }
    }

    private fun updateEditingSelectionState() {
        val selectedText = icManager.getSelectedText()
        orchestrator.textEditingView?.updateSelectionState(!selectedText.isNullOrEmpty())
    }

    private fun resetImeState(info: EditorInfo?, resetShift: Boolean = true) {
        isSelectionMode = false
        selectionAnchor = -1
        movingPosition = -1
        if (orchestrator.isViewReady) {
            orchestrator.textEditingView?.setSelectionMode(false)
        }
        editorState.reset()
        icManager.finishComposingText()
        if (orchestrator.isViewReady) {
            orchestrator.keyboardView?.let {
                it.resetState()
                it.setSuggestions(emptyList(), null)
                orchestrator.showView(it, force = true)
            }
        }
        if (resetShift) {
            shiftManager.reset()
        }
        if (orchestrator.isViewReady) {
            orchestrator.keyboardView?.updateShiftState(shiftManager.currentState)
        }
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
        val textBefore = icManager.getTextBeforeCursor(3, 0)
        if (textBefore != null && textBefore.length >= 2) {
            val lastTwo = textBefore.substring(textBefore.length - 2)
            if (lastTwo == ". " || lastTwo == "! " || lastTwo == "? ") {
                shiftManager.setAutoShift(ShiftState.ONE_SHOT)
                orchestrator.keyboardView?.updateShiftState(shiftManager.currentState)
            }
        }
    }

    private fun toggleXt9() {
        val newState = !xt9Enabled
        if (xt9Enabled && !newState) finalizeCurrentComposing()
        xt9Enabled = newState
        preferences.xt9Enabled = newState
        orchestrator.keyboardView?.isXt9Mode = newState
        Toast.makeText(this, if (newState) "XT9 mode on" else "Multi-tap mode on", Toast.LENGTH_SHORT).show()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        val isManualMove = candidatesStart != -1 && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)
        val isRecentTap = System.currentTimeMillis() - editorState.lastTapTime < 200
        if (isManualMove || (candidatesStart == -1 && !isRecentTap)) {
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
        if (orchestrator.isViewReady && orchestrator.textEditingView?.parent != null) {
            updateEditingSelectionState()
        }
    }

    override fun onWindowShown() {
        super.onWindowShown()
        isWindowVisible = true
        orchestrator.setWindowVisible(true)
        if (contactSuggestionsEnabled && !ContactsDictionary.isLoaded()) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                Thread { ContactsDictionary.load(this) }.start()
            }
        }
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        isEmojiSearchActive = false
        isWindowVisible = false
        orchestrator.setWindowVisible(false)
    }

    private fun isCurrentFieldSensitive(info: EditorInfo?): Boolean {
        if (info == null) return true
        val type = info.inputType
        val variation = type and android.text.InputType.TYPE_MASK_VARIATION
        val cls = type and android.text.InputType.TYPE_MASK_CLASS

        return when {
            variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD -> true
            variation == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD -> true
            variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> true
            cls == android.text.InputType.TYPE_CLASS_NUMBER &&
                variation == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD -> true
            type == android.text.InputType.TYPE_NULL -> true
            else -> false
        }
    }

    private fun updatePasteBubble(info: EditorInfo?) {
        val bubble = pasteBubble ?: return
        if (isCurrentFieldSensitive(info) || preferences.isAppBlacklisted(currentPackageName) || clipboardUsed) {
            bubble.visibility = View.GONE
            return
        }
        val preview = pasteManager.getClipPreview()
        if (preview != null) {
            bubble.text = preview
            bubble.visibility = View.VISIBLE
        } else {
            bubble.visibility = View.GONE
        }
    }

    private fun onPasteBubbleTapped() {
        val text = pasteManager.getFullClipText() ?: return
        icManager.commitText(text, 1)
        pasteBubble?.visibility = View.GONE
        clipboardUsed = true
    }
}
