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
        }
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
