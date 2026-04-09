package com.musa.t9keyboard

import android.content.Context
import android.content.ComponentCallbacks2
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.emoji2.text.EmojiCompat
import androidx.emoji2.text.DefaultEmojiCompatConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private var isEmojiSearchActive = false

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val accentColorResIds = intArrayOf(
        R.color.accent_blue, R.color.accent_teal, R.color.accent_green,
        R.color.accent_yellow, R.color.accent_magenta, R.color.accent_red,
        R.color.accent_orange, R.color.accent_purple
    )

    override fun onDestroy() {
        super.onDestroy()
        (serviceScope.coroutineContext[Job])?.cancel()
        ContactsDictionary.clear()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            // Clear suggestion bar and keyboard state to free memory when UI is not visible
            if (orchestrator.isViewReady) {
                orchestrator.keyboardView?.setSuggestions(emptyList())
            }
            // SuggestionEngine is lightweight but we cancel pending jobs
            suggestionEngine.requestSuggestions(editorState, xt9Enabled, isInputSensitive = true)
        }
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            // Perform more aggressive cleanup if memory is low
            LearnedDictionary.load(this) // Reloading essentially clears in-memory state and re-syncs with disk
        }
    }

    override fun onCreate() {
        super.onCreate()
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
                    // Next-word suggestions only — show in bar, do not set composing text
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
                CrashLogger.log("onSuggestionsReady", e, this)
            }
        }
        preferences = PreferencesManager(this)

        if (preferences.contactSuggestionsEnabled) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                serviceScope.launch(Dispatchers.IO) { ContactsDictionary.load(this@T9InputMethodService) }
            } else {
                preferences.contactSuggestionsEnabled = false
            }
        }

        val fontRequest = androidx.core.provider.FontRequest(
            "com.google.android.gms.fonts",
            "com.google.android.gms",
            "Noto Color Emoji Compat",
            R.array.com_google_android_gms_fonts_certs
        )
        val config = androidx.emoji2.text.FontRequestEmojiCompatConfig(this, fontRequest)
            .setReplaceAll(false)
        EmojiCompat.init(config)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        isInputSensitive = T9Utils.isInputTypeSensitive(attribute)
        resetImeState(attribute, resetShift = true)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        isInputSensitive = T9Utils.isInputTypeSensitive(info)

        // Reset state
        editorState.xt9DigitSequence.setLength(0)
        editorState.xt9RawSequence.setLength(0)
        editorState.composingText.clear()
        editorState.currentXt9Predictions = emptyList()
        isSelectionMode = false

        resetImeState(info, resetShift = false)

        contactPermissionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        contactSuggestionsEnabled = preferences.contactSuggestionsEnabled
        xt9Enabled = preferences.xt9Enabled

        if (orchestrator.isViewReady) {
            val kv = orchestrator.keyboardView ?: return
            val sv = orchestrator.symbolsView ?: return
            val epv = orchestrator.emojiPickerView ?: return
            val tev = orchestrator.textEditingView ?: return

            kv.setMultiTapTimeout(preferences.multiTapTimeout)
            kv.setKeyFontSize(preferences.keyFontSize.toFloat())
            kv.setFontSize(preferences.suggestionFontSize.toFloat())
            kv.setDeletionSpeed(preferences.deletionSpeed)
            kv.isXt9Mode = xt9Enabled
            val accentColor = ContextCompat.getColor(this, accentColorResIds[preferences.accentColorIndex])
            kv.setAccentColor(accentColor)
            sv.setAccentColor(accentColor)
            sv.setDeletionSpeed(preferences.deletionSpeed)
            epv.setAccentColor(accentColor)
            epv.setDeletionSpeed(preferences.deletionSpeed)
            tev.setAccentColor(accentColor)
            tev.setDeletionSpeed(preferences.deletionSpeed)
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        resetImeState(null, resetShift = true)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        icManager.finishComposingText()
        suggestionEngine.cancelPendingJobs()
    }

    override fun onCreateInputView(): View {
        return try {
            val themedContext = android.view.ContextThemeWrapper(this, R.style.AppTheme)
            val container = FrameLayout(themedContext)
            this.container = container
            orchestrator.setContainer(container)

            orchestrator.keyboardView = KeyboardView(themedContext).apply {
                onMultiTapListener = { c, tc, f -> onMultiTap(c, tc, f) }
                onActionClickListener = { a -> onActionClick(a) }
                onFeedbackRequested = { this@T9InputMethodService.onFeedbackRequested() }
                setOnSuggestionClickListener { s -> onSuggestionClick(s) }
                setOnToolbarActionClickListener { a -> onToolbarActionClick(a) }
            }
            orchestrator.symbolsView = SymbolsView(themedContext).apply {
                onSymbolClickListener = { s -> commitTextWithFinalization(s) }
                onBackClickListener = { onBackClick() }
                onDeleteClickListener = { onActionClick(KeyboardView.KeyboardAction.DEL) }
                onFeedbackRequested = { this@T9InputMethodService.onFeedbackRequested() }
            }
            orchestrator.emojiPickerView = EmojiPickerView(themedContext).apply {
                onEmojiClickListener = { e -> onEmojiClick(e) }
                onBackspaceClick = { this@T9InputMethodService.onBackspaceClick() }
                onBackClickListener = { onBackClick() }
                onFeedbackRequested = { this@T9InputMethodService.onFeedbackRequested() }
                onSearchModeListener = { active -> isEmojiSearchActive = active }
            }
            orchestrator.textEditingView = TextEditingView(themedContext).apply {
                onAction = { a -> onEditAction(a) }
                onAbcClick = { this@T9InputMethodService.onAbcClick() }
                on123Click = { this@T9InputMethodService.on123Click() }
                onSymClick = { this@T9InputMethodService.onSymClick() }
                onEmojiClick = { this@T9InputMethodService.onEmojiClick() }
                onFeedbackRequested = { this@T9InputMethodService.onFeedbackRequested() }
            }

            orchestrator.markViewReady()
            orchestrator.showView(orchestrator.keyboardView!!, force = true)
            container
        } catch (e: Exception) {
            CrashLogger.log("onCreateInputView", e, this)
            View(this)
        }
    }

    // --- Action Listeners ---

    override fun onMultiTap(char: Char, tapCount: Int, isFinished: Boolean) {
        if (isEmojiSearchActive) {
            if (!isFinished) {
                orchestrator.emojiPickerView?.updateSearchChar(char, tapCount == 0)
            } else if (isFinished && tapCount == 0) {
                val isPunctuation = (T9Utils.getDigitForChar(char) == '1')
                if (char.isDigit() || (xt9Enabled && !isPunctuation)) {
                    orchestrator.emojiPickerView?.appendSearchChar(char)
                }
            }
            return
        }

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
                checkAutoCap()
            }
        }

        val displayChar = if (shiftManager.currentState != ShiftState.OFF) char.uppercaseChar() else char

        if (isPunctuation) {
            handlePunctuationTap(displayChar, tapCount, isFinished)
            editorState.lastDigit = '1'
        } else {
            editorState.lastDigit = digit
            if (xt9Enabled) {
                handleXt9Tap(char)
            } else {
                handleLetterMultiTap(displayChar, char, tapCount, isFinished)
            }
        }
    }

    override fun onActionClick(action: KeyboardView.KeyboardAction) {
        try {
            if (isEmojiSearchActive) {
                when (action) {
                    KeyboardView.KeyboardAction.DEL -> {
                        orchestrator.emojiPickerView?.deleteSearchChar()
                        return
                    }
                    KeyboardView.KeyboardAction.SPACE -> {
                        orchestrator.emojiPickerView?.appendSearchChar(' ')
                        return
                    }
                    else -> { /* Fall through to reset search */ }
                }
            }

            if (isEmojiSearchActive) {
                orchestrator.emojiPickerView?.exitSearchMode()
            }

            when (action) {
                KeyboardView.KeyboardAction.DEL -> handleDelete()
                KeyboardView.KeyboardAction.SPACE -> {
                    commitTextWithFinalization(" ")
                    suggestionEngine.requestNextWordSuggestions(editorState.lastCommittedWord)
                }
                KeyboardView.KeyboardAction.ENTER -> {
                    commitTextWithFinalization("")
                    icManager.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                }
                KeyboardView.KeyboardAction.SHIFT -> {
                    shiftManager.toggle()
                    orchestrator.keyboardView?.updateShiftState(shiftManager.currentState)
                }
                KeyboardView.KeyboardAction.CAPS_LOCK -> {
                    shiftManager.onDoubleTap()
                    orchestrator.keyboardView?.updateShiftState(shiftManager.currentState)
                }
                KeyboardView.KeyboardAction.SYM -> {
                    orchestrator.symbolsView?.let { orchestrator.showView(it) }
                }
                KeyboardView.KeyboardAction.NUM -> {
                    orchestrator.keyboardView?.toggleNumMode()
                }
                KeyboardView.KeyboardAction.COMMA -> {
                    icManager.commitText(",", 1)
                }
                KeyboardView.KeyboardAction.PERIOD -> {
                    icManager.commitText(".", 1)
                }
                KeyboardView.KeyboardAction.EMOJI -> {
                    orchestrator.emojiPickerView?.let {
                        it.resetScroll()
                        orchestrator.showView(it)
                    }
                }
                KeyboardView.KeyboardAction.SHOW_TEXT_EDITING -> showTextEditingPanel()
                KeyboardView.KeyboardAction.SETTINGS -> {
                    val intent = android.content.Intent(this, SettingsActivity::class.java).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }
                KeyboardView.KeyboardAction.SWITCH_KEYBOARD -> {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showInputMethodPicker()
                }
                KeyboardView.KeyboardAction.TOGGLE_XT9 -> toggleXt9()
            }
        } catch (e: Exception) {
            CrashLogger.log("onActionClick", e, this)
        }
    }

    override fun onToolbarActionClick(action: SuggestionBar.ToolbarAction) {
        onFeedbackRequested()
        if (isEmojiSearchActive) {
            orchestrator.emojiPickerView?.exitSearchMode()
        }
        when (action) {
            SuggestionBar.ToolbarAction.SETTINGS -> {
                val intent = android.content.Intent(this, SettingsActivity::class.java).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
            SuggestionBar.ToolbarAction.EDIT -> showTextEditingPanel()
            SuggestionBar.ToolbarAction.TOGGLE_XT9 -> toggleXt9()
        }
    }

    override fun onSuggestionClick(suggestion: String) {
        onFeedbackRequested()
        if (isEmojiSearchActive) {
            orchestrator.emojiPickerView?.exitSearchMode()
        }
        try {
            if (xt9Enabled) {
                val originalWord = editorState.currentXt9Predictions.find { applyShiftState(it) == suggestion }
                    ?: if (applyShiftState(editorState.xt9RawSequence.toString()) == suggestion) editorState.xt9RawSequence.toString() else suggestion

                icManager.commitText(suggestion, 1)
                icManager.commitText(" ", 1)
                if (!isInputSensitive) {
                    LearnedDictionary.learnWordStrong(originalWord, editorState.lastCommittedWord)
                }
                editorState.lastCommittedWord = originalWord
                editorState.xt9DigitSequence.setLength(0)
                editorState.xt9RawSequence.setLength(0)
                editorState.currentXt9Predictions = emptyList()
            } else {
                icManager.setComposingText(suggestion, 1)
                icManager.finishComposingText()
                icManager.commitText(" ", 1)
                if (!isInputSensitive) {
                    try {
                        LearnedDictionary.learnWordStrong(suggestion, editorState.lastCommittedWord)
                    } catch (e: Exception) {
                        CrashLogger.log("onSuggestionClick.multi", e, this)
                    }
                }
                editorState.lastCommittedWord = suggestion
                editorState.composingText.clear()
                editorState.currentWordConstraints.clear()
            }
            shiftManager.consumeShift()
            orchestrator.keyboardView?.updateShiftState(shiftManager.currentState)
            suggestionEngine.requestNextWordSuggestions(editorState.lastCommittedWord, isInputSensitive)
        } catch (e: Exception) {
            CrashLogger.log("onSuggestionClick", e, this)
        }
    }

    override fun onEditAction(action: TextEditingView.EditAction) {
        if (action != TextEditingView.EditAction.DELETE) {
            finalizeCurrentComposing(moveCursorToEnd = false)
        }

        val et = icManager.getExtractedText() ?: return
        val textLength = et.text.length
        val selectionStart = et.selectionStart
        val selectionEnd = et.selectionEnd

        if (isSelectionMode && (movingPosition == -1 || selectionAnchor == -1)) {
            selectionAnchor = selectionStart
            movingPosition = selectionEnd
        } else if (!isSelectionMode) {
            selectionAnchor = -1
            movingPosition = -1
        }

        when (action) {
            TextEditingView.EditAction.HOME, TextEditingView.EditAction.HOME_LONG -> {
                if (isSelectionMode) {
                    movingPosition = 0
                    icManager.setSelection(selectionAnchor, 0)
                } else {
                    icManager.setSelection(0, 0)
                }
            }
            TextEditingView.EditAction.END, TextEditingView.EditAction.END_LONG -> {
                if (isSelectionMode) {
                    movingPosition = textLength
                    icManager.setSelection(selectionAnchor, textLength)
                } else {
                    icManager.setSelection(textLength, textLength)
                }
            }
            TextEditingView.EditAction.UP -> {
                if (isSelectionMode) {
                    icManager.setSelection(selectionAnchor, maxOf(0, movingPosition - 26))
                } else {
                    icManager.sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP)
                }
            }
            TextEditingView.EditAction.DOWN -> {
                if (isSelectionMode) {
                    icManager.setSelection(selectionAnchor, minOf(textLength, movingPosition + 26))
                } else {
                    icManager.sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN)
                }
            }
            TextEditingView.EditAction.LEFT -> {
                val currentPos = if (isSelectionMode) movingPosition else selectionStart
                if (currentPos > 0) {
                    val newPos = currentPos - 1
                    if (isSelectionMode) {
                        movingPosition = newPos
                        icManager.setSelection(selectionAnchor, newPos)
                    } else {
                        icManager.setSelection(newPos, newPos)
                    }
                }
            }
            TextEditingView.EditAction.RIGHT -> {
                val currentPos = if (isSelectionMode) movingPosition else selectionEnd
                if (currentPos < textLength) {
                    val newPos = currentPos + 1
                    if (isSelectionMode) {
                        movingPosition = newPos
                        icManager.setSelection(selectionAnchor, newPos)
                    } else {
                        icManager.setSelection(newPos, newPos)
                    }
                }
            }
            TextEditingView.EditAction.SELECT_ALL -> {
                if (selectionStart == 0 && selectionEnd == textLength && textLength > 0) {
                    icManager.setSelection(textLength, textLength)
                } else {
                    icManager.performContextMenuAction(android.R.id.selectAll)
                }
            }
            TextEditingView.EditAction.SELECT -> {
                isSelectionMode = !isSelectionMode
                orchestrator.textEditingView?.setSelectionMode(isSelectionMode)
                if (isSelectionMode) {
                    selectionAnchor = selectionStart
                    movingPosition = selectionEnd
                } else {
                    icManager.setSelection(movingPosition, movingPosition)
                    selectionAnchor = -1
                    movingPosition = -1
                }
            }
            TextEditingView.EditAction.SELECT_WORD -> {
                icManager.selectWord()
            }
            TextEditingView.EditAction.COPY, TextEditingView.EditAction.COPY_LONG -> {
                icManager.performContextMenuAction(android.R.id.copy)
                if (action == TextEditingView.EditAction.COPY_LONG) {
                    Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
                }
                isSelectionMode = false
                orchestrator.textEditingView?.setSelectionMode(false)
            }
            TextEditingView.EditAction.CUT, TextEditingView.EditAction.CUT_LONG -> {
                icManager.performContextMenuAction(android.R.id.cut)
                if (action == TextEditingView.EditAction.CUT_LONG) {
                    Toast.makeText(this, "Cut", Toast.LENGTH_SHORT).show()
                }
                isSelectionMode = false
                orchestrator.textEditingView?.setSelectionMode(false)
            }
            TextEditingView.EditAction.PASTE -> {
                icManager.performContextMenuAction(android.R.id.paste)
            }
            TextEditingView.EditAction.PASTE_LONG -> {
                val intent = android.content.Intent("android.intent.action.CLIPBOARD_MANAGER").apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    icManager.performContextMenuAction(android.R.id.paste)
                }
            }
            TextEditingView.EditAction.SELECT_LEFT_WORD -> {
                if (selectionStart > 0) {
                    icManager.sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
                }
            }
            TextEditingView.EditAction.SELECT_LEFT_WORD_LONG -> {
                if (selectionStart > 0) {
                    icManager.sendDownUpKeyEvents(KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.META_SHIFT_ON)
                }
            }
            TextEditingView.EditAction.SELECT_RIGHT_WORD -> {
                if (selectionEnd < textLength) {
                    icManager.sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
                }
            }
            TextEditingView.EditAction.SELECT_RIGHT_WORD_LONG -> {
                if (selectionEnd < textLength) {
                    icManager.sendDownUpKeyEvents(KeyEvent.KEYCODE_MOVE_END, KeyEvent.META_SHIFT_ON)
                }
            }
            TextEditingView.EditAction.UNDO -> {
                icManager.sendDownUpKeyEvents(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON)
            }
            TextEditingView.EditAction.REDO -> {
                icManager.sendDownUpKeyEvents(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
            }
            TextEditingView.EditAction.DELETE -> onActionClick(KeyboardView.KeyboardAction.DEL)
            TextEditingView.EditAction.ENTER -> onActionClick(KeyboardView.KeyboardAction.ENTER)
        }
        updateEditingSelectionState()
    }

    override fun onAbcClick() {
        if (isEmojiSearchActive) {
            orchestrator.emojiPickerView?.exitSearchMode()
        }
        isSelectionMode = false
        orchestrator.textEditingView?.setSelectionMode(false)
        orchestrator.keyboardView?.let { orchestrator.showView(it) }
    }

    override fun on123Click() {
        if (isEmojiSearchActive) {
            orchestrator.emojiPickerView?.exitSearchMode()
        }
        onActionClick(KeyboardView.KeyboardAction.NUM)
        orchestrator.keyboardView?.let { orchestrator.showView(it) }
    }

    override fun onSymClick() {
        if (isEmojiSearchActive) {
            orchestrator.emojiPickerView?.exitSearchMode()
        }
        onActionClick(KeyboardView.KeyboardAction.SYM)
    }

    override fun onEmojiClick() {
        onActionClick(KeyboardView.KeyboardAction.EMOJI)
    }

    override fun onEmojiClick(emoji: String) {
        commitTextWithFinalization(emoji)
        // Note: isEmojiSearchActive remains true as per Step 5 requirements
    }

    override fun onBackspaceClick() {
        onActionClick(KeyboardView.KeyboardAction.DEL)
    }

    override fun onBackClick() {
        if (isEmojiSearchActive) {
            orchestrator.emojiPickerView?.exitSearchMode()
        } else {
            orchestrator.keyboardView?.let { orchestrator.showView(it) }
        }
    }

    override fun onFeedbackRequested() {
        if (preferences.hapticEnabled) {
            val vibrator = getSystemService(android.os.Vibrator::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(preferences.hapticDuration.toLong(), android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(preferences.hapticDuration.toLong())
            }
        }
        if (preferences.soundEnabled) {
            val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                am.playSoundEffect(android.media.AudioManager.FX_KEY_CLICK, preferences.soundVolume)
            } else {
                am.playSoundEffect(android.media.AudioManager.FX_KEY_CLICK)
            }
        }
    }

    // --- Private Helpers ---

    private fun handlePunctuationTap(displayChar: Char, tapCount: Int, isFinished: Boolean) {
        if (isFinished) {
            if (editorState.xt9DigitSequence.isNotEmpty()) {
                commitTextWithFinalization(displayChar.toString())
            } else if (editorState.composingText.isNotEmpty()) {
                icManager.commitText(editorState.composingText.toString(), 1)
                editorState.composingText.setLength(0)
                editorState.currentWordConstraints.clear()
                shiftManager.consumeShift()
                orchestrator.keyboardView?.updateShiftState(shiftManager.currentState)
            }
            orchestrator.keyboardView?.setSuggestions(emptyList())
        } else {
            if (tapCount == 0) {
                editorState.composingText.setLength(0)
                editorState.composingText.append(displayChar)
                editorState.currentWordConstraints.clear()
                editorState.currentWordConstraints.add("1")
            } else {
                if (editorState.composingText.isNotEmpty()) {
                    editorState.composingText.setCharAt(editorState.composingText.length - 1, displayChar)
                } else {
                    editorState.composingText.append(displayChar)
                    editorState.currentWordConstraints.clear()
                    editorState.currentWordConstraints.add("1")
                }
            }
            icManager.setComposingText(editorState.composingText, 1)
        }
    }

    private fun handleLetterMultiTap(displayChar: Char, rawChar: Char, tapCount: Int, isFinished: Boolean) {
        if (isFinished) {
            if (editorState.composingText.isNotEmpty()) {
                editorState.composingText.setCharAt(editorState.composingText.length - 1, displayChar)
                if (editorState.currentWordConstraints.isNotEmpty()) {
                    editorState.currentWordConstraints[editorState.currentWordConstraints.size - 1] = rawChar.toString()
                }
                icManager.setComposingText(editorState.composingText, 1)
                suggestionEngine.requestSuggestions(editorState, false, isInputSensitive)
                shiftManager.consumeShift()
                orchestrator.keyboardView?.updateShiftState(shiftManager.currentState)
            }
        } else {
            if (tapCount == 0) {
                editorState.composingText.append(displayChar)
                editorState.currentWordConstraints.add(T9Utils.getDigitForChar(rawChar).toString())
            } else {
                if (editorState.composingText.isNotEmpty()) {
                    editorState.composingText.setCharAt(editorState.composingText.length - 1, displayChar)
                    if (editorState.currentWordConstraints.isNotEmpty()) {
                        editorState.currentWordConstraints[editorState.currentWordConstraints.size - 1] = rawChar.toString()
                    }
                }
            }
            icManager.setComposingText(editorState.composingText, 1)
            suggestionEngine.requestSuggestions(editorState, false, isInputSensitive)
        }
    }

    private fun handleXt9Tap(char: Char) {
        if (editorState.composingText.isNotEmpty()) {
            commitTextWithFinalization("")
        }
        if (editorState.xt9DigitSequence.isEmpty()) {
            checkAutoCap()
        }
        val digit = T9Utils.getDigitForChar(char)
        if (digit == ' ' || digit == '1') return
        editorState.xt9DigitSequence.append(digit)
        editorState.xt9RawSequence.append(T9Utils.getFirstCharForDigit(digit))
        suggestionEngine.requestSuggestions(editorState, true)
    }

    private fun handleDelete() {
        if (xt9Enabled && editorState.xt9DigitSequence.isNotEmpty()) {
            editorState.xt9DigitSequence.deleteCharAt(editorState.xt9DigitSequence.length - 1)
            editorState.xt9RawSequence.deleteCharAt(editorState.xt9RawSequence.length - 1)
            if (editorState.xt9DigitSequence.isEmpty()) {
                icManager.setComposingText("", 1)
                icManager.finishComposingText()
                orchestrator.keyboardView?.setSuggestions(emptyList())
            } else {
                suggestionEngine.requestSuggestions(editorState, true)
            }
        } else if (!xt9Enabled && editorState.composingText.isNotEmpty()) {
            editorState.composingText.deleteCharAt(editorState.composingText.length - 1)
            if (editorState.currentWordConstraints.isNotEmpty()) {
                editorState.currentWordConstraints.removeAt(editorState.currentWordConstraints.size - 1)
            }
            icManager.setComposingText(editorState.composingText, 1)
            if (editorState.composingText.isEmpty()) {
                icManager.finishComposingText()
            }
            suggestionEngine.requestSuggestions(editorState, false, isInputSensitive)
        } else {
            if (!icManager.getSelectedText().isNullOrEmpty()) {
                icManager.commitText("", 1)
            } else {
                icManager.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            }
        }
    }

    private fun finalizeCurrentComposing(moveCursorToEnd: Boolean = true) {
        if (editorState.composingText.isEmpty() && editorState.xt9DigitSequence.isEmpty()) return

        var committedWord: String? = null
        if (xt9Enabled && editorState.xt9DigitSequence.isNotEmpty()) {
            val wordToCommit = if (editorState.currentXt9Predictions.isNotEmpty()) editorState.currentXt9Predictions[0] else editorState.xt9RawSequence.toString()
            val finalWord = applyShiftState(wordToCommit)
            if (moveCursorToEnd) icManager.commitText(finalWord, 1) else icManager.finishComposingText()
            committedWord = finalWord
            if (!isInputSensitive) {
                try {
                    LearnedDictionary.learnWord(wordToCommit, editorState.lastCommittedWord)
                } catch (e: Exception) {
                    CrashLogger.log("finalizeCurrentComposing.xt9", e, this)
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
            if (!isInputSensitive) {
                try {
                    LearnedDictionary.learnWord(word, editorState.lastCommittedWord)
                } catch (e: Exception) {
                    CrashLogger.log("finalizeCurrentComposing.multi", e, this)
                }
            }
            editorState.lastCommittedWord = word
            editorState.composingText.clear()
            editorState.currentWordConstraints.clear()
        }
        shiftManager.consumeShift()
        orchestrator.keyboardView?.updateShiftState(shiftManager.currentState)
        if (committedWord == "i" && moveCursorToEnd) {
            icManager.deleteSurroundingText(1, 0)
            icManager.commitText("I", 1)
            editorState.lastCommittedWord = "I"
        }
        orchestrator.keyboardView?.setSuggestions(emptyList())
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
            if (isEmojiSearchActive) {
                orchestrator.emojiPickerView?.exitSearchMode()
            }
        }
        editorState.reset()
        icManager.finishComposingText()
        if (orchestrator.isViewReady) {
            orchestrator.keyboardView?.let {
                it.resetState()
                it.setSuggestions(emptyList())
                orchestrator.showView(it, force = true)
            }
        }
        if (resetShift) {
            shiftManager.reset()
            info?.let {
                val inputType = it.inputType
                val capsFlags = inputType and EditorInfo.TYPE_MASK_FLAGS
                val variation = inputType and EditorInfo.TYPE_MASK_VARIATION
                if (capsFlags and EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0) {
                    shiftManager.onDoubleTap()
                } else if (icManager.getTextBeforeCursor(1, 0)?.isEmpty() ?: true) {
                    val isSentenceCap = (capsFlags and EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES != 0)
                    val isPlainText = (inputType and EditorInfo.TYPE_MASK_CLASS == EditorInfo.TYPE_CLASS_TEXT) &&
                                     (variation == EditorInfo.TYPE_TEXT_VARIATION_NORMAL || variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
                    if (isSentenceCap || isPlainText) {
                        shiftManager.setAutoShift(ShiftState.ONE_SHOT)
                    }
                }
            }
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
                serviceScope.launch(Dispatchers.IO) { ContactsDictionary.load(this@T9InputMethodService) }
            }
        }
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        if (isEmojiSearchActive) {
            orchestrator.emojiPickerView?.exitSearchMode()
        }
        isWindowVisible = false
        orchestrator.setWindowVisible(false)
    }
}
