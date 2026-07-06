package com.musa.t9keyboard

import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.PopupWindow
import android.widget.TextView
import com.musa.t9keyboard.databinding.KeyboardViewBinding
import com.musa.t9keyboard.utils.FontUtils

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
    private var accentColor: Int = android.graphics.Color.parseColor("#558DFF")

    // Views in the same order as KeyboardLayout.ALL_KEYS (row-major)
    private val keyViews: List<View> = listOf(
        binding.keyPunct, binding.keyAb, binding.keyCd, binding.keyEf, binding.keyDel,
        binding.keyGh, binding.keyIj, binding.keyKl, binding.keyMn, binding.keySym,
        binding.keyOp, binding.keyQr, binding.keySt, binding.keyUv, binding.keyShift,
        binding.keyWx, binding.keySpace, binding.keyYz, binding.keyEnter
    )

    private val defByViewId: Map<Int, KeyDef> =
        keyViews.zip(KeyboardLayout.ALL_KEYS).associate { (view, def) -> view.id to def }

    // --- Key preview popup ---

    private val previewText: TextView by lazy {
        TextView(keyboardView.context).apply {
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
            textSize = 22f
            typeface = FontUtils.getUbuntu(keyboardView.context)
        }
    }

    private val previewPopup: PopupWindow by lazy {
        val size = dpToPx(56)
        PopupWindow(previewText, size, size).apply {
            isTouchable = false
            isFocusable = false
            isClippingEnabled = false
        }
    }

    private fun showPreview(keyView: View, label: String) {
        if (label.isEmpty() || label.length > 5) return
        try {
            previewText.text = label
            previewText.background = GradientDrawable().apply {
                setColor(accentColor)
                cornerRadius = dpToPx(16).toFloat()
            }
            val location = IntArray(2)
            keyView.getLocationInWindow(location)
            val size = dpToPx(56)
            val x = location[0] + keyView.width / 2 - size / 2
            val y = location[1] - size - dpToPx(8)
            if (previewPopup.isShowing) {
                previewPopup.update(x, y, size, size)
            } else {
                previewPopup.showAtLocation(keyboardView, Gravity.NO_GRAVITY, x, y)
            }
        } catch (e: Exception) {
            // Preview is cosmetic; never let it break input
        }
    }

    private fun hidePreview() {
        try {
            if (previewPopup.isShowing) previewPopup.dismiss()
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun setAccentColor(color: Int) {
        accentColor = color
    }

    // --- Key wiring ---

    fun setupKeys(isNumMode: Boolean, isXt9Mode: Boolean) {
        keyViews.forEach { view ->
            val def = defByViewId[view.id] ?: return@forEach
            when (def.type) {
                KeyType.LETTER, KeyType.SYMBOL_CYCLE -> setupLetterKey(view, def, isNumMode, isXt9Mode)
                KeyType.BACKSPACE -> setupBackspaceKey(view)
                KeyType.SYM_PAGE -> setupSymKey(view)
                KeyType.SHIFT -> setupShiftKey(view, isNumMode)
                KeyType.SPACE -> setupSpaceKey(view, def, isNumMode)
                KeyType.ENTER -> setupEnterKey(view)
                KeyType.NUM_PAGE -> Unit
            }
        }
    }

    private fun setupLetterKey(view: View, def: KeyDef, isNumMode: Boolean, isXt9Mode: Boolean) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    onFeedback()
                    showPreview(v, if (isNumMode) def.numLabel ?: def.label else def.label)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> hidePreview()
            }
            false
        }
        view.setOnClickListener { handleLetterKey(view, def, isNumMode, isXt9Mode) }
        view.setOnLongClickListener {
            onFeedback()
            hidePreview()
            when {
                def.longPressOpensNumPage -> onAction(KeyboardView.KeyboardAction.NUM)
                def.longPressChar != null -> onMultiTap(def.longPressChar, 0, true)
                else -> return@setOnLongClickListener false
            }
            true
        }
    }

    private fun setupBackspaceKey(view: View) {
        view.setOnClickListener {
            onFeedback()
            onAction(KeyboardView.KeyboardAction.DEL)
        }
        view.setOnLongClickListener {
            onFeedback()
            startRepeatingDel()
            true
        }
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                stopRepeatingDel()
            }
            false
        }
    }

    private fun setupSymKey(view: View) {
        view.setOnClickListener {
            onFeedback()
            onAction(KeyboardView.KeyboardAction.SYM)
        }
        view.setOnLongClickListener {
            onFeedback()
            onAction(KeyboardView.KeyboardAction.SWITCH_KEYBOARD)
            true
        }
    }

    private fun setupShiftKey(view: View, isNumMode: Boolean) {
        view.setOnClickListener {
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
        view.setOnLongClickListener(null)
    }

    private fun setupSpaceKey(view: View, def: KeyDef, isNumMode: Boolean) {
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) onFeedback()
            false
        }
        view.setOnClickListener { onAction(KeyboardView.KeyboardAction.SPACE) }
        view.setOnLongClickListener {
            onFeedback()
            def.longPressChar?.let { onMultiTap(it, 0, true) }
            true
        }
    }

    private fun setupEnterKey(view: View) {
        view.setOnClickListener {
            onFeedback()
            onAction(KeyboardView.KeyboardAction.ENTER)
        }
        view.setOnLongClickListener {
            onFeedback()
            onAction(KeyboardView.KeyboardAction.EMOJI)
            true
        }
    }

    private fun handleLetterKey(view: View, def: KeyDef, isNumMode: Boolean, isXt9Mode: Boolean) {
        if (isNumMode) {
            if (isWaitingToCommit) {
                handler.removeCallbacks(commitRunnable)
                commitCurrentTap()
            }
            when (def.numLabel) {
                null -> Unit
                "," -> onAction(KeyboardView.KeyboardAction.COMMA)
                "." -> onAction(KeyboardView.KeyboardAction.PERIOD)
                else -> onMultiTap(def.numLabel[0], 0, true)
            }
            return
        }

        val chars = def.chars
        if (chars.isEmpty()) return

        if (isXt9Mode && def.type != KeyType.SYMBOL_CYCLE) {
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

        val currentChar = chars[tapCount % chars.size]
        onMultiTap(currentChar, tapCount, false)
        isWaitingToCommit = true
        handler.postDelayed(commitRunnable, multiTapTimeout)
    }

    private fun commitCurrentTap() {
        if (currentKeyId != -1) {
            val chars = defByViewId[currentKeyId]?.chars ?: return
            if (chars.isEmpty()) return
            val currentChar = chars[tapCount % chars.size]
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
        hidePreview()
    }

    fun setMultiTapTimeout(timeout: Long) { this.multiTapTimeout = timeout }
    fun setDeletionSpeed(speed: Int) { this.deletionSpeed = speed }

    private fun dpToPx(dp: Int): Int =
        (dp * keyboardView.resources.displayMetrics.density + 0.5f).toInt()
}
