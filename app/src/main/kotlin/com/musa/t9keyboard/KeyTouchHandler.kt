package com.musa.t9keyboard

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import com.musa.t9keyboard.databinding.KeyboardViewBinding

class KeyTouchHandler(
    private val keyboardView: KeyboardView,
    private val binding: KeyboardViewBinding,
    private val onMultiTap: (Char, Int, Boolean) -> Unit,
    private val onAction: (KeyboardView.KeyboardAction) -> Unit,
    private val onFeedback: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val commitRunnable = Runnable { commitCurrentTap() }
    private var isWaitingToCommit = false
    private val delHandler = Handler(Looper.getMainLooper())
    private var delRunnable: Runnable? = null

    private var currentKeyId: Int = -1
    private var tapCount: Int = 0
    private var multiTapTimeout: Long = 800L
    private var deletionSpeed: Int = 100
    private val repeatInterval: Long
        get() = maxOf(30L, 200L - (deletionSpeed * 1.5).toLong())

    private var lastShiftTapTime: Long = 0

    private val keyMap = mapOf(
        binding.keyAbc.id to "abc",
        binding.keyDef.id to "def",
        binding.keyGhi.id to "ghi",
        binding.keyJkl.id to "jkl",
        binding.keyMno.id to "mno",
        binding.keyPqrs.id to "pqrs",
        binding.keyTuv.id to "tuv",
        binding.keyWxyz.id to "wxyz",
        binding.keyPunct.id to ".,!?"
    )

    fun setupKeys(isNumMode: Boolean, isXt9Mode: Boolean) {
        val numberKeys = listOf(
            binding.keyPunct to '1',
            binding.keyAbc to '2',
            binding.keyDef to '3',
            binding.keyGhi to '4',
            binding.keyJkl to '5',
            binding.keyMno to '6',
            binding.keyPqrs to '7',
            binding.keyTuv to '8',
            binding.keyWxyz to '9'
        )

        numberKeys.forEach { (view, digit) ->
            view.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) onFeedback()
                false
            }
            view.setOnClickListener { handleLetterKey(view, isNumMode, isXt9Mode) }
            view.setOnLongClickListener {
                onFeedback()
                onMultiTap(digit, 0, true)
                true
            }
        }

        binding.keyShift.setOnClickListener {
            onFeedback()
            if (isNumMode) {
                onAction(KeyboardView.KeyboardAction.NUM)
            } else {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastShiftTapTime < 300) {
                    onAction(KeyboardView.KeyboardAction.CAPS_LOCK)
                } else {
                    onAction(KeyboardView.KeyboardAction.SHIFT)
                }
                lastShiftTapTime = currentTime
            }
        }

        binding.keyDel.setOnClickListener {
            onFeedback()
            onAction(KeyboardView.KeyboardAction.DEL)
        }

        binding.keyDel.setOnLongClickListener {
            onFeedback()
            startRepeatingDel()
            true
        }

        binding.keyDel.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                stopRepeatingDel()
            }
            false
        }

        binding.keyEnter.setOnClickListener {
            onFeedback()
            onAction(KeyboardView.KeyboardAction.ENTER)
        }

        binding.keySpace.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) onFeedback()
            false
        }
        binding.keySpace.setOnClickListener {
            if (isNumMode) onMultiTap('0', 0, true)
            else onAction(KeyboardView.KeyboardAction.SPACE)
        }
        binding.keySpace.setOnLongClickListener {
            onFeedback()
            onMultiTap('0', 0, true)
            true
        }

        binding.keySym.setOnClickListener {
            onFeedback()
            onAction(KeyboardView.KeyboardAction.SYM)
        }

        binding.key123.setOnClickListener {
            onFeedback()
            if (isNumMode) onAction(KeyboardView.KeyboardAction.COMMA)
            else onAction(KeyboardView.KeyboardAction.NUM)
        }

        binding.keyEmoji.setOnClickListener {
            onFeedback()
            onAction(KeyboardView.KeyboardAction.EMOJI)
        }
    }

    private fun handleLetterKey(view: View, isNumMode: Boolean, isXt9Mode: Boolean) {
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
                onMultiTap(text[0], 0, true)
            }
            return
        }

        val chars = keyMap[view.id] ?: return
        if (isXt9Mode && view.id != binding.keyPunct.id) {
            if (isWaitingToCommit) {
                handler.removeCallbacks(commitRunnable)
                commitCurrentTap()
            }
            onMultiTap(chars[0], 0, true)
            return
        }

        if (currentKeyId == view.id) {
            if (isWaitingToCommit) {
                handler.removeCallbacks(commitRunnable)
                isWaitingToCommit = false
            }
            tapCount++
        } else {
            if (isWaitingToCommit) {
                handler.removeCallbacks(commitRunnable)
                commitCurrentTap()
            }
            currentKeyId = view.id
            tapCount = 0
        }

        val currentChar = chars[tapCount % chars.length]
        onMultiTap(currentChar, tapCount, false)
        isWaitingToCommit = true
        handler.postDelayed(commitRunnable, multiTapTimeout)
    }

    private fun commitCurrentTap() {
        if (currentKeyId != -1) {
            val chars = keyMap[currentKeyId] ?: return
            val currentChar = chars[tapCount % chars.length]
            onMultiTap(currentChar, tapCount, true)
            currentKeyId = -1
            tapCount = 0
            isWaitingToCommit = false
        }
    }

    fun startRepeatingDel() {
        stopRepeatingDel()
        val interval = repeatInterval
        delRunnable = object : Runnable {
            override fun run() {
                onAction(KeyboardView.KeyboardAction.DEL)
                delHandler.postDelayed(this, interval)
            }
        }
        delHandler.postDelayed(delRunnable!!, 150L)
    }

    fun stopRepeatingDel() {
        delRunnable?.let { delHandler.removeCallbacks(it) }
        delRunnable = null
    }

    fun resetState() {
        handler.removeCallbacks(commitRunnable)
        isWaitingToCommit = false
        currentKeyId = -1
        tapCount = 0
        stopRepeatingDel()
    }

    fun setMultiTapTimeout(timeout: Long) { this.multiTapTimeout = timeout }
    fun setDeletionSpeed(speed: Int) { this.deletionSpeed = speed }
}
