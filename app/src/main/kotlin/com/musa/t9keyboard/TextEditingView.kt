package com.musa.t9keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.style.UnderlineSpan
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.musa.t9keyboard.utils.FontUtils

class TextEditingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    sealed class EditAction {
        object HOME : EditAction()
        object HOME_LONG : EditAction()
        object UP : EditAction()
        object END : EditAction()
        object END_LONG : EditAction()
        object SELECT_ALL : EditAction()
        object LEFT : EditAction()
        object SELECT : EditAction()
        object SELECT_WORD : EditAction()
        object RIGHT : EditAction()
        object COPY : EditAction()
        object COPY_LONG : EditAction()
        object SELECT_LEFT_WORD : EditAction()
        object SELECT_LEFT_WORD_LONG : EditAction()
        object DOWN : EditAction()
        object SELECT_RIGHT_WORD : EditAction()
        object SELECT_RIGHT_WORD_LONG : EditAction()
        object PASTE : EditAction()
        object PASTE_LONG : EditAction()
        object CUT : EditAction()
        object CUT_LONG : EditAction()
        object UNDO : EditAction()
        object REDO : EditAction()
        object DELETE : EditAction()
    }

    var onAction: ((EditAction) -> Unit)? = null
    var onAbcClick: (() -> Unit)? = null
    var onFeedbackRequested: (() -> Unit)? = null

    private var isSelectionMode = false
    private var accentColor = Color.parseColor("#00BFA5")

    private val handler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null

    private lateinit var selectKey: TextView
    private lateinit var copyKey: TextView
    private lateinit var cutKey: TextView
    private lateinit var abcBtn: TextView
    private var allKeys = mutableListOf<TextView>()

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#1A1A1A"))
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dpToPx(304)
        )
        setupUI()
    }

    private fun setupUI() {
        val ubuntu = FontUtils.getUbuntu(context)
        // --- Header ---
        addView(TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(36))
            text = "TEXT EDITING"
            setTextColor(Color.parseColor("#AAAAAA"))
            textSize = 13f
            typeface = ubuntu
            gravity = Gravity.CENTER
            setAllCaps(true)
        })

        val gridLayout = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            setPadding(dpToPx(6), 0, dpToPx(6), 0)
        }

        // Row 1: Home, Up, End, Select All
        gridLayout.addView(createRow(listOf(
            KeyConfig("|<", 20f, EditAction.HOME, EditAction.HOME_LONG),
            KeyConfig("∧", 22f, EditAction.UP, repeatInterval = 100),
            KeyConfig(">|", 20f, EditAction.END, EditAction.END_LONG),
            KeyConfig("Select all", 14f, EditAction.SELECT_ALL)
        )))

        // Row 2: Left, Select, Right, Copy
        val row2Configs = listOf(
            KeyConfig("<", 22f, EditAction.LEFT, repeatInterval = 100),
            KeyConfig("Select", 15f, EditAction.SELECT, longAction = EditAction.SELECT_WORD),
            KeyConfig(">", 22f, EditAction.RIGHT, repeatInterval = 100),
            KeyConfig("Copy", 16f, EditAction.COPY, longAction = EditAction.COPY_LONG)
        )
        val row2 = createRow(row2Configs)
        selectKey = row2.getChildAt(1) as TextView
        copyKey = row2.getChildAt(3) as TextView
        gridLayout.addView(row2)

        // Row 3: Select Left Word, Down, Select Right Word, Paste
        gridLayout.addView(createRow(listOf(
            KeyConfig("⇐", 22f, EditAction.SELECT_LEFT_WORD, longAction = EditAction.SELECT_LEFT_WORD_LONG),
            KeyConfig("∨", 22f, EditAction.DOWN, repeatInterval = 100),
            KeyConfig("⇒", 22f, EditAction.SELECT_RIGHT_WORD, longAction = EditAction.SELECT_RIGHT_WORD_LONG),
            KeyConfig("Paste", 16f, EditAction.PASTE, longAction = EditAction.PASTE_LONG)
        )))

        // Row 4: Cut, Undo, Redo, DEL
        val row4Configs = listOf(
            KeyConfig("Cut", 16f, EditAction.CUT, longAction = EditAction.CUT_LONG),
            KeyConfig("Undo ↩", 15f, EditAction.UNDO, repeatInterval = 300),
            KeyConfig("Redo ↪", 15f, EditAction.REDO, repeatInterval = 300),
            KeyConfig("⌫", 22f, EditAction.DELETE, repeatInterval = 100, textColor = Color.parseColor("#FF5252"))
        )
        val row4 = createRow(row4Configs)
        cutKey = row4.getChildAt(0) as TextView
        gridLayout.addView(row4)

        addView(gridLayout)

        // Bottom Bar: ABC
        addView(LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(48))
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            abcBtn = TextView(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                text = "ABC"
                setTextColor(Color.WHITE)
                textSize = 18f
                typeface = ubuntu
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    onFeedbackRequested?.invoke()
                    onAbcClick?.invoke()
                }
            }
            addView(abcBtn)
        })

        updateSelectKeyVisuals()
        updateSelectionState(false)
    }

    private data class KeyConfig(
        val label: String,
        val textSize: Float,
        val action: EditAction,
        val longAction: EditAction? = null,
        val repeatInterval: Long? = null,
        val textColor: Int = Color.WHITE
    )

    private fun createRow(configs: List<KeyConfig>): LinearLayout {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            configs.forEach { config ->
                addView(createKey(config))
            }
        }
    }

    private fun createKey(config: KeyConfig): TextView {
        val key = TextView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(dpToPx(3), dpToPx(3), dpToPx(3), dpToPx(3))
            }
            text = config.label
            setTextColor(config.textColor)
            textSize = config.textSize
            typeface = FontUtils.getUbuntu(context)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
        }

        allKeys.add(key)
        updateKeyBackground(key)

        key.setOnTouchListener(object : OnTouchListener {
            private var longPressedSent = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        onFeedbackRequested?.invoke()
                        v.isPressed = true
                        longPressedSent = false

                        if (config.repeatInterval != null) {
                            onAction?.invoke(config.action)
                            startRepeating(config.action, config.repeatInterval)
                        } else if (config.longAction != null) {
                            startLongPressCheck(config.longAction) { longPressedSent = true }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        v.isPressed = false
                        stopRepeating()
                        if (!longPressedSent) {
                            if (config.repeatInterval == null) {
                                onAction?.invoke(config.action)
                            }
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        stopRepeating()
                    }
                }
                return true
            }
        })
        return key
    }

    private fun updateKeyBackground(key: TextView) {
        if (::selectKey.isInitialized && key == selectKey) return

        val pressedColor = (accentColor and 0x00FFFFFF) or (0x66 shl 24)
        val pressedColorList = android.content.res.ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_pressed),
                intArrayOf()
            ),
            intArrayOf(
                pressedColor,
                Color.parseColor("#2D2D2D") // Match the background color for TextEditingView
            )
        )
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(4).toFloat()
            setColor(Color.WHITE)
        }
        key.background = shape
        key.backgroundTintList = pressedColorList
    }

    private fun updateAbcBtnBackground() {
        if (!::abcBtn.isInitialized) return
        val pressedColor = (accentColor and 0x00FFFFFF) or (0x66 shl 24)
        val ripple = android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(pressedColor),
            null,
            android.graphics.drawable.ColorDrawable(Color.WHITE)
        )
        abcBtn.background = ripple
    }

    private fun startRepeating(action: EditAction, interval: Long) {
        stopRepeating()
        repeatRunnable = object : Runnable {
            override fun run() {
                onAction?.invoke(action)
                handler.postDelayed(this, interval)
            }
        }
        handler.postDelayed(repeatRunnable!!, 500)
    }

    private fun startLongPressCheck(longAction: EditAction, onLongPressed: () -> Unit) {
        stopRepeating()
        repeatRunnable = Runnable {
            onLongPressed()
            onAction?.invoke(longAction)
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        }
        handler.postDelayed(repeatRunnable!!, 500)
    }

    private fun stopRepeating() {
        repeatRunnable?.let { handler.removeCallbacks(it) }
        repeatRunnable = null
    }

    fun setSelectionMode(enabled: Boolean) {
        this.isSelectionMode = enabled
        updateSelectKeyVisuals()
    }

    fun updateSelectionState(hasSelection: Boolean) {
        val color = if (hasSelection) Color.WHITE else Color.parseColor("#666666")
        copyKey.setTextColor(color)
        cutKey.setTextColor(color)
    }

    fun setAccentColor(color: Int) {
        this.accentColor = color
        updateSelectKeyVisuals()
        allKeys.forEach { updateKeyBackground(it) }
        updateAbcBtnBackground()
    }

    private fun updateSelectKeyVisuals() {
        selectKey.background = SelectKeyDrawable(isSelectionMode)
        val ubuntu = FontUtils.getUbuntu(context)
        if (isSelectionMode) {
            val content = SpannableString("Select")
            content.setSpan(UnderlineSpan(), 0, content.length, 0)
            selectKey.text = content
            selectKey.typeface = ubuntu
        } else {
            selectKey.text = "Select"
            selectKey.typeface = ubuntu
        }
    }

    private inner class SelectKeyDrawable(val isSelected: Boolean) : Drawable() {
        private val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

        override fun draw(canvas: android.graphics.Canvas) {
            bgPaint.color = if (isSelected) accentColor else Color.parseColor("#2D2D2D")

            val rect = android.graphics.RectF(bounds)
            val radius = dpToPx(4).toFloat()
            canvas.drawRoundRect(rect, radius, radius, bgPaint)
        }

        override fun setAlpha(alpha: Int) { bgPaint.alpha = alpha }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { bgPaint.colorFilter = colorFilter }
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density + 0.5f).toInt()
}
