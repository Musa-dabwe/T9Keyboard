package com.musa.t9keyboard

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.musa.t9keyboard.databinding.SymbolsViewBinding

class SymbolsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: SymbolsViewBinding = SymbolsViewBinding.inflate(LayoutInflater.from(context), this, true)
    var onSymbolClickListener: ((String) -> Unit)? = null
    var onBackClickListener: (() -> Unit)? = null

    private val categories = mapOf(
        "Punct" to listOf(".", ",", "!", "?", ";", ":", "'", "\"", "-", "_"),
        "Brackets" to listOf("(", ")", "[", "]", "{", "}", "<", ">"),
        "Math" to listOf("+", "-", "*", "/", "=", "%", "^", "~", "√", "π"),
        "Currency" to listOf("$", "€", "£", "¥", "₹", "¢", "¤"),
        "Misc" to listOf("@", "#", "&", "|", "\\", "/", "`", "°", "©", "®", "™", "•")
    )

    init {
        setupCategories()
        displayCategory("Punct")
        binding.btnBackToAbc.setOnClickListener { onBackClickListener?.invoke() }
    }

    private fun setupCategories() {
        categories.keys.forEach { category ->
            val btn = Button(context).apply {
                text = category
                setOnClickListener { displayCategory(category) }
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
            }
            binding.symbolCategories.addView(btn)
        }
    }

    private fun displayCategory(category: String) {
        binding.symbolGrid.removeAllViews()
        categories[category]?.forEach { symbol ->
            val btn = Button(context, null, 0, R.style.KeyboardKey).apply {
                text = symbol
                layoutParams = LayoutParams(0, 160).apply {
                    // In a GridLayout, we set column weight if possible, but here we use GridLayout.LayoutParams
                }
                setOnClickListener { onSymbolClickListener?.invoke(symbol) }
            }
            // Adding to GridLayout
            val params = android.widget.GridLayout.LayoutParams()
            params.width = 0
            params.height = 160
            params.columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
            btn.layoutParams = params
            binding.symbolGrid.addView(btn)
        }
    }
}
