package com.musa.t9keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
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
    var onFeedbackRequested: (() -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private val commitRunnable = Runnable { commitCurrentTap() }
    private var isWaitingToCommit = false
    private val delHandler = Handler(Looper.getMainLooper())
    private var delRunnable: Runnable? = null
    private var currentKeyId: Int = -1
    private var tapCount: Int = 0
    private var multiTapTimeout: Long = 800L
    private var lastShiftTapTime: Long = 0
    private var isNumMode = false
    var isXt9Mode = false
        set(value) {
            field = value
            binding.suggestionBar.setXt9Mode(value)
        }

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
        SHIFT, CAPS_LOCK, DEL, ENTER, SPACE, SYM, NUM, EMOJI, SETTINGS
    }

    init {
        setupKeys()
        updateKeyLabels()
    }

    private fun setupKeys() {
        val letterKeys = listOf(
            binding.keyAbc, binding.keyDef, binding.keyGhi, binding.keyJkl,
            binding.keyMno, binding.keyPqrs, binding.keyTuv, binding.keyWxyz,
            binding.keyPunct
        )

        letterKeys.forEach { view ->
            view.setOnTouchListener { v, event ->
                if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                    onFeedbackRequested?.invoke()
                    handleLetterKey(v)
                    true
                } else {
                    false
                }
            }
        }

        binding.keyPunct.setOnLongClickListener {
            onFeedbackRequested?.invoke()
            onActionClickListener?.invoke(KeyboardAction.SETTINGS)
            true
        }

        binding.keyShift.setOnClickListener {
            onFeedbackRequested?.invoke()
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastShiftTapTime < 300) {
                onActionClickListener?.invoke(KeyboardAction.CAPS_LOCK)
            } else {
                onActionClickListener?.invoke(KeyboardAction.SHIFT)
            }
            lastShiftTapTime = currentTime
        }
        binding.keyDel.setOnClickListener {
            onFeedbackRequested?.invoke()
            onActionClickListener?.invoke(KeyboardAction.DEL)
        }

        binding.keyDel.setOnLongClickListener {
            onFeedbackRequested?.invoke()
            startRepeatingDel()
            true
        }

        binding.keyDel.setOnTouchListener { v, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP || event.action == android.view.MotionEvent.ACTION_CANCEL) {
                stopRepeatingDel()
            }
            false
        }

        binding.keyEnter.setOnClickListener {
            onFeedbackRequested?.invoke()
            onActionClickListener?.invoke(KeyboardAction.ENTER)
        }
        binding.keySpace.setOnClickListener {
            onFeedbackRequested?.invoke()
            if (isNumMode) {
                onMultiTapListener?.invoke('0', 0, true)
            } else {
                onActionClickListener?.invoke(KeyboardAction.SPACE)
            }
        }
        binding.keySym.setOnClickListener {
            onFeedbackRequested?.invoke()
            onActionClickListener?.invoke(KeyboardAction.SYM)
        }
        binding.key123.setOnClickListener {
            onFeedbackRequested?.invoke()
            onActionClickListener?.invoke(KeyboardAction.NUM)
        }
        binding.keyEmoji.setOnClickListener {
            onFeedbackRequested?.invoke()
            onActionClickListener?.invoke(KeyboardAction.EMOJI)
        }
    }

    private fun startRepeatingDel() {
        stopRepeatingDel()
        delRunnable = object : Runnable {
            override fun run() {
                onActionClickListener?.invoke(KeyboardAction.DEL)
                delHandler.postDelayed(this, 100)
            }
        }
        delHandler.postDelayed(delRunnable!!, 500) // Initial delay
    }

    private fun stopRepeatingDel() {
        delRunnable?.let { delHandler.removeCallbacks(it) }
        delRunnable = null
    }

    private fun handleLetterKey(view: View) {
        if (isNumMode) {
            val text = when(view.id) {
                binding.keyPunct.id -> "1"
                binding.keyAbc.id -> "2"
                binding.keyDef.id -> "3"
                binding.keyGhi.id -> "4"
                binding.keyJkl.id -> "5"
                binding.keyMno.id -> "6"
                binding.keyPqrs.id -> "7"
                binding.keyTuv.id -> "8"
                binding.keyWxyz.id -> "9"
                else -> ""
            }
            if (text.isNotEmpty()) {
                if (isWaitingToCommit) {
                    handler.removeCallbacks(commitRunnable)
                    commitCurrentTap()
                }
                onMultiTapListener?.invoke(text[0], 0, true)
            }
            return
        }

        val chars = keyMap[view.id] ?: return

        if (isXt9Mode && view.id != binding.keyPunct.id) {
            if (isWaitingToCommit) {
                handler.removeCallbacks(commitRunnable)
                commitCurrentTap()
            }
            onMultiTapListener?.invoke(chars[0], 0, true)
            return
        }

        if (currentKeyId == view.id) {
            if (isWaitingToCommit) {
                handler.removeCallbacks(commitRunnable)
                isWaitingToCommit = false
            }
            tapCount = (tapCount + 1) % chars.length
        } else {
            if (isWaitingToCommit) {
                handler.removeCallbacks(commitRunnable)
                commitCurrentTap()
            }
            currentKeyId = view.id
            tapCount = 0
        }

        val currentChar = chars[tapCount]
        onMultiTapListener?.invoke(currentChar, tapCount, false)

        isWaitingToCommit = true
        handler.postDelayed(commitRunnable, multiTapTimeout)
    }

    private fun commitCurrentTap() {
        if (currentKeyId != -1) {
            val chars = keyMap[currentKeyId] ?: return
            val currentChar = chars[tapCount % chars.length]
            onMultiTapListener?.invoke(currentChar, tapCount, true)
            currentKeyId = -1
            tapCount = 0
            isWaitingToCommit = false
        }
    }

    fun resetState() {
        handler.removeCallbacks(commitRunnable)
        isWaitingToCommit = false
        currentKeyId = -1
        tapCount = 0
        stopRepeatingDel()
    }

    fun setMultiTapTimeout(timeout: Long) {
        this.multiTapTimeout = timeout
    }

    fun setFontSize(size: Float) {
        binding.suggestionBar.setFontSize(size)
    }

    fun setSuggestions(suggestions: List<String>, rawSequence: String? = null) {
        binding.suggestionBar.setSuggestions(suggestions, rawSequence)
    }

    fun setAccentColor(color: Int) {
        binding.suggestionBar.setAccentColor(color)
    }

    fun setOnSuggestionClickListener(listener: (String) -> Unit) {
        binding.suggestionBar.onSuggestionClickListener = listener
    }

    fun setKeyFontSize(sizeSp: Float) {
        val primaryLabels = listOf(
            binding.labelAbc, binding.labelDef, binding.labelGhi, binding.labelJkl,
            binding.labelMno, binding.labelPqrs, binding.labelTuv, binding.labelWxyz,
            binding.labelPunct, binding.labelShift, binding.labelDel, binding.labelEnter,
            binding.labelSpace, binding.labelSym, binding.label123, binding.labelEmoji
        )
        primaryLabels.forEach { it.textSize = sizeSp }
    }

    fun updateShiftState(state: ShiftState) {
        binding.keyShift.isActivated = (state != ShiftState.OFF)
        binding.labelShift.text = when(state) {
            ShiftState.OFF -> "SHIFT"
            ShiftState.ONE_SHOT -> "SHIFT"
            ShiftState.CAPS_LOCK -> "CAPS"
        }
    }

    fun toggleNumMode() {
        isNumMode = !isNumMode
        updateKeyLabels()
    }

    private fun updateKeyLabels() {
        val digitKeys = listOf(
            Triple(binding.labelPunct, binding.secondaryLabelPunct, "1"),
            Triple(binding.labelAbc, binding.secondaryLabelAbc, "2"),
            Triple(binding.labelDef, binding.secondaryLabelDef, "3"),
            Triple(binding.labelGhi, binding.secondaryLabelGhi, "4"),
            Triple(binding.labelJkl, binding.secondaryLabelJkl, "5"),
            Triple(binding.labelMno, binding.secondaryLabelMno, "6"),
            Triple(binding.labelPqrs, binding.secondaryLabelPqrs, "7"),
            Triple(binding.labelTuv, binding.secondaryLabelTuv, "8"),
            Triple(binding.labelWxyz, binding.secondaryLabelWxyz, "9"),
            Triple(binding.labelSpace, binding.secondaryLabelSpace, "0")
        )

        val letterLabels = listOf(
            ".,?!", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ", "SPACE"
        )

        digitKeys.forEachIndexed { index, (primary, secondary, digit) ->
            if (isNumMode) {
                primary.text = digit
                primary.textSize = 22f
                secondary.visibility = View.GONE
            } else {
                primary.text = letterLabels[index]
                primary.textSize = 18f
                secondary.visibility = View.VISIBLE
            }
        }
        binding.label123.text = if (isNumMode) "ABC" else "123"
    }
}
