package com.musa.t9keyboard

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import com.musa.t9keyboard.databinding.SuggestionBarBinding
import com.musa.t9keyboard.utils.FontUtils

import android.graphics.Color
import android.view.View
import android.widget.FrameLayout

class SuggestionBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: SuggestionBarBinding = SuggestionBarBinding.inflate(LayoutInflater.from(context), this, true)

    var onSuggestionClickListener: ((String) -> Unit)? = null
    private var accentColor: Int = Color.parseColor("#BB86FC") // Default purple

    init {
        binding.suggestion1.setOnClickListener { onSuggestionClickListener?.invoke(binding.suggestion1.text.toString()) }
        binding.suggestion2.setOnClickListener { onSuggestionClickListener?.invoke(binding.suggestion2.text.toString()) }
        binding.suggestion3.setOnClickListener { onSuggestionClickListener?.invoke(binding.suggestion3.text.toString()) }
        applyUbuntuFont()
    }

    private fun applyUbuntuFont() {
        val ubuntu = FontUtils.getUbuntu(context)
        binding.suggestion1.typeface = ubuntu
        binding.suggestion2.typeface = ubuntu
        binding.suggestion3.typeface = ubuntu
        binding.suggestionRaw.typeface = ubuntu
        binding.xt9Indicator.typeface = ubuntu
    }

    fun setSuggestions(suggestions: List<String>, rawSequence: String? = null) {
        if (rawSequence != null) {
            binding.suggestionRaw.visibility = View.VISIBLE
            binding.dividerRaw.visibility = View.VISIBLE
            binding.suggestionRaw.text = rawSequence
        } else {
            binding.suggestionRaw.visibility = View.GONE
            binding.dividerRaw.visibility = View.GONE
        }

        binding.suggestion1.text = suggestions.getOrNull(0) ?: ""
        binding.suggestion2.text = suggestions.getOrNull(1) ?: ""
        binding.suggestion3.text = suggestions.getOrNull(2) ?: ""

        // Highlight the first suggestion if it's not empty and we are in XT9 mode (implied by rawSequence)
        if (rawSequence != null && (suggestions.getOrNull(0) ?: "").isNotEmpty()) {
            binding.suggestion1.setTextColor(accentColor)
        } else {
            binding.suggestion1.setTextColor(Color.WHITE)
        }
    }

    fun setXt9Mode(enabled: Boolean) {
        binding.xt9Indicator.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    fun setAccentColor(color: Int) {
        this.accentColor = color
        binding.xt9Indicator.setTextColor(color)
    }

    fun setFontSize(sizeSp: Float) {
        binding.suggestion1.textSize = sizeSp
        binding.suggestion2.textSize = sizeSp
        binding.suggestion3.textSize = sizeSp
    }
}
