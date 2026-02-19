package com.musa.t9keyboard

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import com.musa.t9keyboard.databinding.SuggestionBarBinding

class SuggestionBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: SuggestionBarBinding = SuggestionBarBinding.inflate(LayoutInflater.from(context), this, true)

    var onSuggestionClickListener: ((String) -> Unit)? = null

    init {
        binding.suggestion1.setOnClickListener { onSuggestionClickListener?.invoke(binding.suggestion1.text.toString()) }
        binding.suggestion2.setOnClickListener { onSuggestionClickListener?.invoke(binding.suggestion2.text.toString()) }
        binding.suggestion3.setOnClickListener { onSuggestionClickListener?.invoke(binding.suggestion3.text.toString()) }
    }

    fun setSuggestions(suggestions: List<String>) {
        binding.suggestion1.text = suggestions.getOrNull(0) ?: ""
        binding.suggestion2.text = suggestions.getOrNull(1) ?: ""
        binding.suggestion3.text = suggestions.getOrNull(2) ?: ""
    }

    fun setFontSize(sizeSp: Float) {
        binding.suggestion1.textSize = sizeSp
        binding.suggestion2.textSize = sizeSp
        binding.suggestion3.textSize = sizeSp
    }
}
