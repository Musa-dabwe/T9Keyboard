package com.musa.t9keyboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SymbolAdapter(
    private val onSymbolClick: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<SymbolItem>()

    sealed class SymbolItem {
        data class Header(val title: String) : SymbolItem()
        data class Row(val symbols: List<String?>) : SymbolItem()
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ROW = 1
    }

    init {
        setupItems()
    }

    private fun setupItems() {
        addCategory("PUNCT", listOf(".", ",", "!", "?", ";", ":", "'", "\"", "-", "_", "…", "‘", "’", "“", "”", "‹", "›", "«", "»"))
        addCategory("BRACKETS", listOf("(", ")", "[", "]", "{", "}", "<", ">", "⌊", "⌋", "⌈", "⌉"))
        addCategory("MATH", listOf("+", "-", "*", "/", "=", "%", "^", "~", "≠", "≈", "±", "∞", "√", "∑", "∏", "∫", "∂", "∆", "∇", "∈", "∉", "⊂", "⊃", "∪", "∩", "∧", "∨", "¬", "∀", "∃", "⊕", "⊗", "≤", "≥", "≪", "≫", "∝", "∥"))
        addCategory("CURRENCY", listOf("$", "€", "£", "¥", "₹", "¢", "₩", "₪", "₺", "₽", "₿", "₴", "₦", "₫", "₱", "₭", "₮", "₯", "₸", "₻"))
        addCategory("MISC", listOf("@", "#", "&", "|", "\\", "/", "©", "®", "™", "°", "•", "·", "←", "→", "↑", "↓", "↔", "↕", "↗", "↘", "↙", "↖", "★", "☆", "♠", "♣", "♥", "♦", "☐", "☑", "☒", "♪", "♫", "✓", "✗", "✦", "✧", "❖", "◆", "◇", "○", "●", "□", "■", "△", "▽", "✉", "✂", "✏", "✒"))
    }

    private fun addCategory(title: String, symbols: List<String>) {
        items.add(SymbolItem.Header(title))
        for (i in symbols.indices step 6) {
            val rowSymbols = mutableListOf<String?>()
            for (j in 0 until 6) {
                if (i + j < symbols.size) {
                    rowSymbols.add(symbols[i + j])
                } else {
                    rowSymbols.add(null)
                }
            }
            items.add(SymbolItem.Row(rowSymbols))
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is SymbolItem.Header -> TYPE_HEADER
            is SymbolItem.Row -> TYPE_ROW
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(inflater.inflate(R.layout.symbol_section_header, parent, false))
            else -> RowViewHolder(inflater.inflate(R.layout.symbol_item_row, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is HeaderViewHolder && item is SymbolItem.Header) {
            holder.bind(item)
        } else if (holder is RowViewHolder && item is SymbolItem.Row) {
            holder.bind(item, onSymbolClick)
        }
    }

    override fun getItemCount(): Int = items.size

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleView: TextView = view.findViewById(R.id.section_title)
        fun bind(header: SymbolItem.Header) {
            titleView.text = header.title
        }
    }

    class RowViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cells = listOf<TextView>(
            view.findViewById(R.id.symbol_1),
            view.findViewById(R.id.symbol_2),
            view.findViewById(R.id.symbol_3),
            view.findViewById(R.id.symbol_4),
            view.findViewById(R.id.symbol_5),
            view.findViewById(R.id.symbol_6)
        )

        fun bind(row: SymbolItem.Row, onClick: (String) -> Unit) {
            row.symbols.forEachIndexed { index, symbol ->
                val cell = cells[index]
                if (symbol != null) {
                    cell.text = symbol
                    cell.visibility = View.VISIBLE
                    cell.setOnClickListener { onClick(symbol) }
                } else {
                    cell.text = ""
                    cell.visibility = View.INVISIBLE
                    cell.setOnClickListener(null)
                }
            }
        }
    }
}
