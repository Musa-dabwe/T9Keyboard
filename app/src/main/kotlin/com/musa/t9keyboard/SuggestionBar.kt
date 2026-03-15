package com.musa.t9keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.musa.t9keyboard.databinding.SuggestionBarBinding
import com.musa.t9keyboard.utils.FontUtils

class SuggestionBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: SuggestionBarBinding = SuggestionBarBinding.inflate(LayoutInflater.from(context), this, true)

    var onSuggestionClickListener: ((String) -> Unit)? = null
    var onToolbarActionClickListener: ((ToolbarAction) -> Unit)? = null

    private var accentColor: Int = Color.parseColor("#BB86FC")
    private var suggestionFontSize: Float = 18f
    private var isXt9Mode: Boolean = false

    enum class ToolbarAction {
        SETTINGS, EDIT, TOGGLE_XT9
    }

    init {
        setupToolbar()
        applyUbuntuFont()
    }

    private fun setupToolbar() {
        binding.toolbarSettings.setOnClickListener { onToolbarActionClickListener?.invoke(ToolbarAction.SETTINGS) }
        binding.toolbarEdit.setOnClickListener { onToolbarActionClickListener?.invoke(ToolbarAction.EDIT) }
        binding.toolbarXt9.setOnClickListener { onToolbarActionClickListener?.invoke(ToolbarAction.TOGGLE_XT9) }

        applyRipple(binding.toolbarSettings)
        applyRipple(binding.toolbarEdit)
    }

    private fun applyRipple(view: View) {
        val rippleColor = ColorStateList.valueOf((Color.WHITE and 0x00FFFFFF) or (0x33 shl 24))
        val ripple = RippleDrawable(rippleColor, null, GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        })
        view.background = ripple
    }

    private fun applyUbuntuFont() {
        val ubuntu = FontUtils.getUbuntu(context)
        binding.toolbarXt9.typeface = ubuntu
        binding.anchoredSuggestion.typeface = ubuntu
    }

    fun showToolbar() {
        binding.toolbarEdit.visibility = View.VISIBLE
    }

    fun showSuggestions() {
        binding.toolbarEdit.visibility = View.GONE
    }

    fun setSuggestions(suggestions: List<String>, anchoredWord: String? = null) {
        binding.suggestionContainer.removeAllViews()
        val ubuntu = FontUtils.getUbuntu(context)

        suggestions.forEach { suggestion ->
            val tv = TextView(context).apply {
                text = suggestion
                textSize = suggestionFontSize
                setTextColor(Color.WHITE)
                typeface = ubuntu
                gravity = Gravity.CENTER
                setPadding(dpToPx(12), 0, dpToPx(12), 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                isClickable = true
                isFocusable = true
                val outValue = android.util.TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
                setOnClickListener { onSuggestionClickListener?.invoke(suggestion) }
            }
            binding.suggestionContainer.addView(tv)
        }

        if (anchoredWord != null) {
            binding.anchoredSuggestion.visibility = View.VISIBLE
            binding.anchoredSuggestion.text = anchoredWord
            binding.anchoredSuggestion.setBackgroundColor(accentColor)
            binding.anchoredSuggestion.setOnClickListener { onSuggestionClickListener?.invoke(anchoredWord) }
        } else {
            binding.anchoredSuggestion.visibility = View.GONE
        }
    }

    fun setXt9Mode(enabled: Boolean) {
        this.isXt9Mode = enabled
        updateXt9ButtonVisuals()
    }

    private fun updateXt9ButtonVisuals() {
        binding.toolbarXt9.setTextColor(if (isXt9Mode) accentColor else Color.WHITE)
        // Optionally add a subtle background when active
        if (isXt9Mode) {
             val bg = GradientDrawable().apply {
                 setColor((accentColor and 0x00FFFFFF) or (0x33 shl 24))
                 cornerRadius = dpToPx(4).toFloat()
             }
             binding.toolbarXt9.background = bg
        } else {
             binding.toolbarXt9.background = null
        }
    }

    fun setAccentColor(color: Int) {
        this.accentColor = color
        updateXt9ButtonVisuals()
        if (binding.anchoredSuggestion.visibility == View.VISIBLE) {
            binding.anchoredSuggestion.setBackgroundColor(color)
        }
        // Update toolbar icons tint
        binding.toolbarSettings.setColorFilter(Color.WHITE)
        binding.toolbarEdit.setColorFilter(Color.WHITE)
    }

    fun setFontSize(sizeSp: Float) {
        this.suggestionFontSize = sizeSp
        binding.anchoredSuggestion.textSize = sizeSp + 4 // Make anchored word slightly larger
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density + 0.5f).toInt()
}
