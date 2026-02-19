package com.musa.t9keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.musa.t9keyboard.databinding.KeyboardViewBinding
import com.musa.t9keyboard.databinding.PopupMultitapBinding

class KeyboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: KeyboardViewBinding = KeyboardViewBinding.inflate(LayoutInflater.from(context), this, true)

    var onKeyClickListener: ((String) -> Unit)? = null
    var onActionClickListener: ((KeyboardAction) -> Unit)? = null
    var onMultiTapListener: ((Char, Int, Boolean) -> Unit)? = null // char, tapCount, isFinished

    private val handler = Handler(Looper.getMainLooper())
    private var currentKeyId: Int = -1
    private var tapCount: Int = 0
    private var multiTapTimeout: Long = 800L
    private var lastShiftTapTime: Long = 0
    private var isNumMode = false

    private var popupWindow: PopupWindow? = null
    private var popupBinding: PopupMultitapBinding? = null

    private val keyMap = mapOf(
        binding.keyAbc.id to "abc",
        binding.keyDef.id to "def",
        binding.keyGhi.id to "ghi",
        binding.keyJkl.id to "jkl",
        binding.keyMno.id to "mno",
        binding.keyPqrs.id to "pqrs",
        binding.keyTuv.id to "tuv",
        binding.keyWxyz.id to "wxyz",
        binding.keyPunct.id to ".,?!:;"
    )

    /**
     * Enum defining standard keyboard actions.
     */
    enum class KeyboardAction {
        SHIFT, CAPS_LOCK, DEL, ENTER, SPACE, SYM, NUM, EMOJI
    }

    init {
        setupKeys()
    }

    private fun setupKeys() {
        val letterKeys = listOf(
            binding.keyAbc, binding.keyDef, binding.keyGhi, binding.keyJkl,
            binding.keyMno, binding.keyPqrs, binding.keyTuv, binding.keyWxyz,
            binding.keyPunct
        )

        letterKeys.forEach { button ->
            button.setOnClickListener { handleLetterKey(button) }
            button.setOnLongClickListener {
                showPopupPicker(button)
                true
            }
        }

        binding.keyShift.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastShiftTapTime < 300) {
                onActionClickListener?.invoke(KeyboardAction.CAPS_LOCK)
            } else {
                onActionClickListener?.invoke(KeyboardAction.SHIFT)
            }
            lastShiftTapTime = currentTime
        }
        binding.keyDel.setOnClickListener { onActionClickListener?.invoke(KeyboardAction.DEL) }
        binding.keyDel.setOnLongClickListener {
            startContinuousDelete()
            true
        }
        binding.keyEnter.setOnClickListener { onActionClickListener?.invoke(KeyboardAction.ENTER) }
        binding.keySpace.setOnClickListener {
            if (isNumMode) {
                onMultiTapListener?.invoke('0', 0, true)
            } else {
                onActionClickListener?.invoke(KeyboardAction.SPACE)
            }
        }
        binding.keySym.setOnClickListener { onActionClickListener?.invoke(KeyboardAction.SYM) }
        binding.key123.setOnClickListener { onActionClickListener?.invoke(KeyboardAction.NUM) }
        binding.keyEmoji.setOnClickListener { onActionClickListener?.invoke(KeyboardAction.EMOJI) }
    }

    private fun handleLetterKey(button: Button) {
        if (isNumMode) {
            val text = when(button.id) {
                binding.keyAbc.id -> "1"
                binding.keyDef.id -> "2"
                binding.keyGhi.id -> "3"
                binding.keyJkl.id -> "4"
                binding.keyMno.id -> "5"
                binding.keyPqrs.id -> "6"
                binding.keyTuv.id -> "7"
                binding.keyWxyz.id -> "8"
                binding.keyPunct.id -> "9"
                else -> ""
            }
            if (text.isNotEmpty()) {
                onMultiTapListener?.invoke(text[0], 0, true)
            }
            return
        }
        val chars = keyMap[button.id] ?: return

        if (currentKeyId == button.id) {
            handler.removeCallbacksAndMessages(null)
            tapCount = (tapCount + 1) % chars.length
        } else {
            commitCurrentTap()
            currentKeyId = button.id
            tapCount = 0
        }

        val currentChar = chars[tapCount]
        onMultiTapListener?.invoke(currentChar, tapCount, false)
        showMultiTapPopup(button, chars, tapCount)

        handler.postDelayed({
            commitCurrentTap()
        }, multiTapTimeout)
    }

    private fun commitCurrentTap() {
        if (currentKeyId != -1) {
            val chars = keyMap[currentKeyId] ?: return
            val currentChar = chars[tapCount % chars.length]
            onMultiTapListener?.invoke(currentChar, tapCount, true)
            currentKeyId = -1
            tapCount = 0
            hideMultiTapPopup()
        }
    }

    private fun showMultiTapPopup(anchor: View, chars: String, selectedIndex: Int) {
        if (popupWindow == null) {
            val inflater = LayoutInflater.from(context)
            popupBinding = PopupMultitapBinding.inflate(inflater)
            popupWindow = PopupWindow(popupBinding?.root, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }

        val spannable = android.text.SpannableString(chars)
        spannable.setSpan(android.text.style.UnderlineSpan(), selectedIndex, selectedIndex + 1, 0)
        spannable.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), selectedIndex, selectedIndex + 1, 0)
        popupBinding?.popupText?.text = spannable

        popupWindow?.showAsDropDown(anchor, 0, -anchor.height - 50)
    }

    private fun hideMultiTapPopup() {
        popupWindow?.dismiss()
    }

    /**
     * Displays a popup picker for the long-pressed key.
     * Includes swipe-to-select logic: user can either tap a letter or drag and release.
     */
    private fun showPopupPicker(anchor: View) {
        val chars = keyMap[anchor.id] ?: return
        val popupView = LinearLayout(context).apply {
            orientation = HORIZONTAL
            background = context.getDrawable(R.drawable.key_background_normal)
            setPadding(16, 16, 16, 16)
        }
        val pickerPopup = PopupWindow(popupView, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, true)

        val buttons = mutableListOf<Button>()
        chars.forEach { char ->
            val btn = Button(context).apply {
                text = char.toString()
                setTextColor(android.graphics.Color.WHITE)
                background = null
                textSize = 24f
                setOnClickListener {
                    onMultiTapListener?.invoke(char, 0, true)
                    pickerPopup.dismiss()
                }
            }
            popupView.addView(btn)
            buttons.add(btn)
        }

        // Swipe-to-select logic implementation
        popupView.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_MOVE) {
                // Find which button is under the touch
                buttons.forEach { btn ->
                    val rect = android.graphics.Rect()
                    btn.getGlobalVisibleRect(rect)
                    if (rect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                        btn.isPressed = true
                    } else {
                        btn.isPressed = false
                    }
                }
            } else if (event.action == android.view.MotionEvent.ACTION_UP) {
                buttons.forEach { btn ->
                    val rect = android.graphics.Rect()
                    btn.getGlobalVisibleRect(rect)
                    if (rect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                        onMultiTapListener?.invoke(btn.text[0], 0, true)
                        pickerPopup.dismiss()
                        return@setOnTouchListener true
                    }
                }
                pickerPopup.dismiss()
            }
            true
        }

        pickerPopup.showAsDropDown(anchor, 0, -anchor.height - 100)
    }

    private fun startContinuousDelete() {
        val deleteRunnable = object : Runnable {
            override fun run() {
                if (binding.keyDel.isPressed) {
                    onActionClickListener?.invoke(KeyboardAction.DEL)
                    handler.postDelayed(this, 100)
                }
            }
        }
        handler.post(deleteRunnable)
    }

    fun setMultiTapTimeout(timeout: Long) {
        this.multiTapTimeout = timeout
    }

    fun setFontSize(size: Float) {
        binding.suggestionBar.setFontSize(size)
    }

    fun setSuggestions(suggestions: List<String>) {
        binding.suggestionBar.setSuggestions(suggestions)
    }

    fun setOnSuggestionClickListener(listener: (String) -> Unit) {
        binding.suggestionBar.onSuggestionClickListener = listener
    }

    fun setKeyFontSize(sizeSp: Float) {
        val buttons = listOf(
            binding.keyAbc, binding.keyDef, binding.keyGhi, binding.keyJkl,
            binding.keyMno, binding.keyPqrs, binding.keyTuv, binding.keyWxyz,
            binding.keyPunct, binding.keyShift, binding.keyDel, binding.keyEnter,
            binding.keySpace, binding.keySym, binding.key123, binding.keyEmoji
        )
        buttons.forEach { it.textSize = sizeSp }
    }

    fun updateShiftState(state: ShiftState) {
        binding.keyShift.isActivated = (state != ShiftState.OFF)
        binding.keyShift.text = when(state) {
            ShiftState.OFF -> "shift"
            ShiftState.ON -> "SHIFT"
            ShiftState.CAPS_LOCK -> "CAPS"
        }
    }

    fun toggleNumMode() {
        isNumMode = !isNumMode
        if (isNumMode) {
            binding.keyAbc.text = "1"
            binding.keyDef.text = "2"
            binding.keyGhi.text = "3"
            binding.keyJkl.text = "4"
            binding.keyMno.text = "5"
            binding.keyPqrs.text = "6"
            binding.keyTuv.text = "7"
            binding.keyWxyz.text = "8"
            binding.keyPunct.text = "9"
            binding.keySpace.text = "0"
            binding.key123.text = "ABC"
        } else {
            binding.keyAbc.text = "abc"
            binding.keyDef.text = "def"
            binding.keyGhi.text = "ghi"
            binding.keyJkl.text = "jkl"
            binding.keyMno.text = "mno"
            binding.keyPqrs.text = "pqrs"
            binding.keyTuv.text = "tuv"
            binding.keyWxyz.text = "wxyz"
            binding.keyPunct.text = ".,?"
            binding.keySpace.text = "SPACE"
            binding.key123.text = "123"
        }
    }
}
