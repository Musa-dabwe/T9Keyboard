// ============================================================
// EMOJI PANEL INTEGRATION — T9InputMethodService.kt changes
// ============================================================
//
// The new EmojiPickerView is self-contained and programmatic.
// Replace the old emoji panel setup with the following:
//
// 1. DECLARATION (class-level field):

private var emojiPickerView: EmojiPickerView? = null

// 2. IN showEmojiPanel() or wherever you inflate the emoji view:

private fun showEmojiPanel() {
    try {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.emoji_picker, null)
        emojiPickerView = view.findViewById(R.id.emojiPickerView)

        emojiPickerView?.onEmojiSelected = { emoji ->
            commitTextWithFinalization(emoji)
            // Keep emoji panel open (GBoard behavior)
        }

        emojiPickerView?.onBackspaceClick = {
            performHapticFeedback()
            currentInputConnection?.deleteSurroundingText(1, 0)
        }

        // Reset scroll to top each time panel opens
        emojiPickerView?.resetScroll()

        // Show the view in your keyboard container
        setInputView(view)
        // OR if using a container layout:
        // keyboardContainer.removeAllViews()
        // keyboardContainer.addView(view)

    } catch (e: Exception) {
        Log.e("T9IME", "Failed to show emoji panel: ${e.message}")
    }
}

// 3. KEYBOARD HEIGHT NOTE:
// The EmojiPickerView needs the same height as your regular keyboard.
// Ensure the keyboard window height is set before showing the emoji panel.
// In onStartInputView(), set:
//   window?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, YOUR_KB_HEIGHT_PX)
//
// The panel height = tab row (44dp) + divider (1dp) + emoji grid (fills rest).
// Recommended minimum panel height: 240dp total.

// ============================================================
// FILES TO COPY TO PROJECT:
// ============================================================
//
// app/src/main/kotlin/com/musa/t9keyboard/EmojiData.kt       <- 1914 emojis
// app/src/main/kotlin/com/musa/t9keyboard/EmojiPickerView.kt  <- panel UI
// app/src/main/res/layout/emoji_picker.xml                    <- layout root
//
// NO external libraries required. All standard Android views.
// Minimum SDK: API 26+ (already your target).
// ============================================================
