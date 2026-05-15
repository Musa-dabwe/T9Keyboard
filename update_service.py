import re

with open('app/src/main/kotlin/com/musa/t9keyboard/T9InputMethodService.kt', 'r') as f:
    content = f.read()

# 1. Update emojiPickerView setup and add emojiSearchPanelView setup
picker_pattern = r'(orchestrator\.emojiPickerView = EmojiPickerView\(themedContext\)\.apply \{.*?\n\s+onSearchModeListener = \{ active -> isEmojiSearchActive = active \}\n\s+\})'
replacement = r"""orchestrator.emojiPickerView = EmojiPickerView(themedContext).apply {
                onEmojiClickListener = { e -> onEmojiClick(e) }
                onBackspaceClick = { this@T9InputMethodService.onBackspaceClick() }
                onBackClickListener = { onBackClick() }
                pasteBubble?.visibility = View.GONE
                onFeedbackRequested = { this@T9InputMethodService.onFeedbackRequested() }
                onSwipeDownListener = { onBackClick() }
                onSearchModeListener = { active ->
                    isEmojiSearchActive = active
                    if (active) onEmojiSearchTriggered()
                }
            }
            orchestrator.emojiSearchPanelView = EmojiSearchPanelView(themedContext).apply {
                listener = object : EmojiSearchPanelView.Listener {
                    override fun onEmojiSelected(emoji: String) {
                        onEmojiClick(emoji)
                    }
                    override fun onCloseRequested() {
                        isEmojiSearchActive = false
                        orchestrator.emojiPickerView?.let { orchestrator.showView(it) }
                    }
                    override fun onFeedbackRequested() {
                        this@T9InputMethodService.onFeedbackRequested()
                    }
                }
            }"""

content = re.sub(picker_pattern, replacement, content, flags=re.DOTALL)

# 2. Update onBackClick
back_click_pattern = r'override fun onBackClick\(\) \{.*?\n\s+\}'
back_click_replacement = r"""override fun onBackClick() {
        if (isEmojiSearchActive) {
            isEmojiSearchActive = false
            orchestrator.emojiPickerView?.let { orchestrator.showView(it) }
            return
        }
        orchestrator.keyboardView?.let { orchestrator.showView(it) }
    }"""
content = re.sub(back_click_pattern, back_click_replacement, content, flags=re.DOTALL)

# 3. Add onEmojiSearchTriggered
if 'private fun onEmojiSearchTriggered()' not in content:
    content = content.replace('    override fun onBackClick()', r"""    private fun onEmojiSearchTriggered() {
        isEmojiSearchActive = true
        orchestrator.emojiSearchPanelView?.let {
            val colorRes = accentColorResIds[preferences.accentColorIndex]
            val color = ContextCompat.getColor(this, colorRes)
            it.setAccentColor(color)
            it.resetQuery()
            orchestrator.showView(it)
        }
    }

    override fun onBackClick()""")

with open('app/src/main/kotlin/com/musa/t9keyboard/T9InputMethodService.kt', 'w') as f:
    f.write(content)
