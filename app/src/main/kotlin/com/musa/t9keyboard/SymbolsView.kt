package com.musa.t9keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.ImageViewCompat
import com.musa.t9keyboard.databinding.SymbolsViewBinding
import com.musa.t9keyboard.utils.FontUtils

class SymbolsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: SymbolsViewBinding = SymbolsViewBinding.inflate(LayoutInflater.from(context), this, true)
    var onSymbolClickListener: ((String) -> Unit)? = null
    var onDeleteClickListener: (() -> Unit)? = null
    var onBackClickListener: (() -> Unit)? = null
    var onFeedbackRequested: (() -> Unit)? = null

    private val delHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var delRunnable: Runnable? = null
    private var deletionSpeed: Int = 100
    private val repeatInterval: Long
        get() = maxOf(30L, 200L - (deletionSpeed * 1.5).toLong())
    private var accentColor: Int = Color.parseColor("#00BFA5")

    private val symbols = listOf(
        ".", ",", "!", "?", ":", ";", "'", "\"",
        "*", "(", ")", "#", "@", "&", "-", "_",
        "$", "€", "<", ">", "{", "}", "[", "]",
        "=", "/", "\\", "%", "|", "~", "©", "®",
        "™", "`", "•", "°", "✓", "×", "÷", "§"
    )

    init {
        orientation = VERTICAL
        setupTopBar()
        setupSymbolGrid()
    }

    private fun setupTopBar() {
        binding.btnBack.setOnClickListener {
            onFeedbackRequested?.invoke()
            onBackClickListener?.invoke()
        }
    }

    private fun setupSymbolGrid() {
        binding.symbolGrid.removeAllViews()
        val ubuntu = FontUtils.getUbuntu(context)

        symbols.forEach { symbol ->
            val tv = TextView(context).apply {
                text = symbol
                textSize = 18f
                setTextColor(Color.WHITE)
                typeface = ubuntu
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true

                val params = GridLayout.LayoutParams().apply {
                    width = 0
                    height = 0
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setGravity(Gravity.FILL)
                    setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
                }
                layoutParams = params

                setOnClickListener {
                    onFeedbackRequested?.invoke()
                    onSymbolClickListener?.invoke(symbol)
                }
            }
            binding.symbolGrid.addView(tv)
        }
    }

    private fun startRepeatingDel() {
        stopRepeatingDel()
        val interval = repeatInterval
        delRunnable = object : Runnable {
            override fun run() {
                onDeleteClickListener?.invoke()
                delHandler.postDelayed(this, interval)
            }
        }
        delHandler.postDelayed(delRunnable!!, 150L)
    }

    private fun stopRepeatingDel() {
        delRunnable?.let { delHandler.removeCallbacks(it) }
        delRunnable = null
    }

    fun setDeletionSpeed(speed: Int) {
        this.deletionSpeed = speed
    }

    private fun createKeyRipple(isIcon: Boolean = false): RippleDrawable {
        val pressedColor = (accentColor and 0x00FFFFFF) or (0x44 shl 24)
        val content = if (isIcon) null else GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(12).toFloat()
            setColor(Color.BLACK)
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(12).toFloat()
            setColor(Color.WHITE)
        }
        return RippleDrawable(ColorStateList.valueOf(pressedColor), content, mask)
    }

    fun setAccentColor(color: Int) {
        this.accentColor = color

        binding.btnBack.background = createKeyRipple(isIcon = true)

        ImageViewCompat.setImageTintList(binding.btnBack, ColorStateList.valueOf(color))

        for (i in 0 until binding.symbolGrid.childCount) {
            val child = binding.symbolGrid.getChildAt(i)
            child.background = createKeyRipple(isIcon = false)
        }
    }

    fun resetScroll() {
        // No scrolling in new design
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density + 0.5f).toInt()
}
