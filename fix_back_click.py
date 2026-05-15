import re

with open('app/src/main/kotlin/com/musa/t9keyboard/T9InputMethodService.kt', 'r') as f:
    content = f.read()

# Fix the broken onBackClick resulting from regex replacement
content = re.sub(r'override fun onBackClick\(\) \{.*?\n\s+\} else \{.*?\n\s+\}\n\s+\}',
                 r"""override fun onBackClick() {
        if (isEmojiSearchActive) {
            isEmojiSearchActive = false
            orchestrator.emojiPickerView?.let { orchestrator.showView(it) }
            return
        }
        orchestrator.keyboardView?.let { orchestrator.showView(it) }
    }""",
                 content, flags=re.DOTALL)

with open('app/src/main/kotlin/com/musa/t9keyboard/T9InputMethodService.kt', 'w') as f:
    f.write(content)
