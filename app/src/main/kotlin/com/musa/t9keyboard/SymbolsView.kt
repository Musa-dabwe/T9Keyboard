package com.musa.t9keyboard

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.musa.t9keyboard.databinding.SymbolsViewBinding

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
    }

    fun resetScroll() {
        binding.symbolRecyclerView.scrollToPosition(0)
    }
}
