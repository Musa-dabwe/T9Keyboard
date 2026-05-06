package com.musa.t9keyboard

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.style.UnderlineSpan
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.musa.t9keyboard.utils.FontUtils

class TextEditingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
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
        object ENTER : EditAction()
    }

    var onAction: ((EditAction) -> Unit)? = null
    var onAbcClick: (() -> Unit)? = null
    var on123Click: (() -> Unit)? = null
    var onSymClick: (() -> Unit)? = null
    var onEmojiClick: (() -> Unit)? = null
    var onFeedbackRequested: (() -> Unit)? = null
    var onSwipeDownListener: (() -> Unit)? = null

    private var isSelectionMode = false
    private var accentColor = Color.parseColor("#00BFA5")
    private val keyHandler: EditKeyHandler
    private lateinit var selectKey: TextView
    private lateinit var abcKey: TextView
    private lateinit var copyKey: TextView
    private lateinit var cutKey: TextView
    private var allKeys = mutableListOf<TextView>()

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#1A1A1A"))
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(304))
        keyHandler = EditKeyHandler(this, { onAction?.invoke(it) }, { onFeedbackRequested?.invoke() })
        setupUI()
    }

    private fun setupUI() {
        val ubuntu = FontUtils.getUbuntu(context)
        val topBar = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(36))
            text = "TEXT EDITING"
            setTextColor(Color.parseColor("#888888"))
            textSize = 13f
            typeface = ubuntu
            gravity = Gravity.CENTER
            setAllCaps(true)
            letterSpacing = 0.05f
            setOnTouchListener(SwipeDownListener(context) { onSwipeDownListener?.invoke() })
        }
        addView(topBar)

        val gridLayout = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            setPadding(dpToPx(6), 0, dpToPx(6), dpToPx(6))
        }

        val rows = listOf(
            listOf(
                KeyConfig("Undo ↩", 15f, EditAction.UNDO, repeatInterval = 300),
                KeyConfig("∧", 22f, EditAction.UP, repeatInterval = 100, iconResId = R.drawable.ic_caret_up),
                KeyConfig("Redo ↪", 15f, EditAction.REDO, repeatInterval = 300),
                KeyConfig("⌫", 22f, EditAction.DELETE, textColor = Color.parseColor("#FF5252"), iconResId = R.drawable.ic_delete)
            ),
            listOf(
                KeyConfig("<", 22f, EditAction.LEFT, repeatInterval = 100, iconResId = R.drawable.ic_caret_left),
                KeyConfig("Select", 15f, EditAction.SELECT, longAction = EditAction.SELECT_WORD),
                KeyConfig(">", 22f, EditAction.RIGHT, repeatInterval = 100, iconResId = R.drawable.ic_caret_right),
                KeyConfig("ABC", 18f, onClick = { onAbcClick?.invoke() })
            ),
            listOf(
                KeyConfig("Cut", 16f, EditAction.CUT, longAction = EditAction.CUT_LONG),
                KeyConfig("∨", 22f, EditAction.DOWN, repeatInterval = 100, iconResId = R.drawable.ic_caret_down),
                KeyConfig("Copy", 16f, EditAction.COPY, longAction = EditAction.COPY_LONG),
                KeyConfig("Paste", 16f, EditAction.PASTE, longAction = EditAction.PASTE_LONG)
            ),
            listOf(
                KeyConfig("123", 18f, onClick = { on123Click?.invoke() }),
                KeyConfig("Select all", 14f, EditAction.SELECT_ALL, weight = 2f),
                KeyConfig("↵", 22f, EditAction.ENTER)
            )
        )

        rows.forEachIndexed { i, configs ->
            val row = createRow(configs)
            if (i == 1) {
                selectKey = row.getChildAt(1) as TextView
                abcKey = row.getChildAt(3) as TextView
            }
            if (i == 2) {
                cutKey = row.getChildAt(0) as TextView
                copyKey = row.getChildAt(2) as TextView
            }
            gridLayout.addView(row)
        }
        addView(gridLayout)
        updateSelectKeyVisuals()
        updateSelectionState(false)
    }

    private data class KeyConfig(
        val label: String,
        val textSize: Float,
        val action: EditAction? = null,
        val longAction: EditAction? = null,
        val repeatInterval: Long? = null,
        val textColor: Int = Color.WHITE,
        val iconResId: Int? = null,
        val onClick: (() -> Unit)? = null,
        val weight: Float = 1f
    )

    private fun createRow(configs: List<KeyConfig>): LinearLayout {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            configs.forEach { addView(createKey(it)) }
        }
    }

    private fun createKey(config: KeyConfig): TextView {
        val key = TextView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, config.weight).apply {
                setMargins(dpToPx(3), dpToPx(3), dpToPx(3), dpToPx(3))
            }
            if (config.iconResId != null) {
                androidx.appcompat.content.res.AppCompatResources.getDrawable(context, config.iconResId)?.let {
                    it.setTint(config.textColor)
                    it.setBounds(0, 0, dpToPx(24), dpToPx(24))
                    val spannable = SpannableString(" ")
                    spannable.setSpan(android.text.style.ImageSpan(it, android.text.style.ImageSpan.ALIGN_BOTTOM), 0, 1, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    text = spannable
                }
            } else {
                text = config.label
            }
            setTextColor(config.textColor)
            textSize = config.textSize
            typeface = FontUtils.getUbuntu(context)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
        }
        allKeys.add(key)
        updateKeyBackground(key)
        keyHandler.setupTouchListener(key, config.action, config.longAction, config.repeatInterval, config.onClick)
        return key
    }

    private fun updateKeyBackground(key: TextView) {
        if (::selectKey.isInitialized && key == selectKey) return
        if (::abcKey.isInitialized && key == abcKey) {
            key.background = SelectKeyDrawable(true)
            return
        }

        val pressedColor = (accentColor and 0x00FFFFFF) or (0x66 shl 24)
        val pressedColorList = android.content.res.ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
            intArrayOf(pressedColor, Color.parseColor("#2D2D2D"))
        )
        key.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(4).toFloat()
            setColor(Color.WHITE)
        }
        key.backgroundTintList = pressedColorList
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
    }

    fun setDeletionSpeed(speed: Int) = keyHandler.setDeletionSpeed(speed)

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
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        override fun draw(canvas: Canvas) {
            paint.color = if (isSelected) accentColor else Color.parseColor("#2D2D2D")
            canvas.drawRoundRect(RectF(bounds), dpToPx(4).toFloat(), dpToPx(4).toFloat(), paint)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density + 0.5f).toInt()
}
