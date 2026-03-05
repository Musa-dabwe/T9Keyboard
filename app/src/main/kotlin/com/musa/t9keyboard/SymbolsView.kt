package com.musa.t9keyboard

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.musa.t9keyboard.databinding.SymbolsViewBinding
import com.musa.t9keyboard.utils.FontUtils

class SymbolsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: SymbolsViewBinding = SymbolsViewBinding.inflate(LayoutInflater.from(context), this, true)
    var onSymbolClickListener: ((String) -> Unit)? = null
    var onDeleteClickListener: (() -> Unit)? = null
    var onBackClickListener: (() -> Unit)? = null

    init {
        binding.symbolRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.symbolRecyclerView.adapter = SymbolAdapter { symbol ->
            onSymbolClickListener?.invoke(symbol)
        }
        binding.btnBackToAbc.setOnClickListener { onBackClickListener?.invoke() }
        binding.btnSymbolDelete.setOnClickListener { onDeleteClickListener?.invoke() }
        applyUbuntuFont()
    }

    private fun applyUbuntuFont() {
        val ubuntu = FontUtils.getUbuntu(context)
        binding.btnBackToAbc.typeface = ubuntu
        binding.btnSymbolDelete.typeface = ubuntu
    }

    fun setAccentColor(color: Int) {
        val pressedColor = (color and 0x00FFFFFF) or (0x66 shl 24)
        val ripple = android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(pressedColor),
            null,
            android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE)
        )
        binding.btnBackToAbc.background = ripple
        binding.btnSymbolDelete.background = ripple.constantState?.newDrawable()?.mutate()

        (binding.symbolRecyclerView.adapter as? SymbolAdapter)?.setAccentColor(color)
    }

    fun resetScroll() {
        binding.symbolRecyclerView.scrollToPosition(0)
    }
}
