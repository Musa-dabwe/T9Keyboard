package com.musa.t9keyboard

import com.musa.t9keyboard.SuggestionBar.ToolbarAction

interface MainKeyActionListener {
    fun onMultiTap(char: Char, tapCount: Int, isFinished: Boolean)
    fun onActionClick(action: KeyboardView.KeyboardAction)
    fun onToolbarActionClick(action: ToolbarAction)
    fun onSuggestionClick(suggestion: String)
    fun onFeedbackRequested()
}

interface EditActionListener {
    fun onEditAction(action: TextEditingView.EditAction)
    fun onAbcClick()
    fun on123Click()
    fun onSymClick()
    fun onEmojiClick()
    fun onFeedbackRequested()
}

interface EmojiActionListener {
    fun onEmojiClick(emoji: String)
    fun onBackspaceClick()
    fun onBackClick()
    fun onFeedbackRequested()
}
