package com.musa.t9keyboard

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.musa.t9keyboard.databinding.EmojiPickerBinding

class EmojiPickerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: EmojiPickerBinding = EmojiPickerBinding.inflate(LayoutInflater.from(context), this, true)
    var onEmojiClickListener: ((String) -> Unit)? = null
    var onBackClickListener: (() -> Unit)? = null

    private var emojiTypeface: Typeface? = null

    private val emojiCategories = mapOf(
        "Smileys" to listOf("😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇"),
        "People" to listOf("👶", "👧", "🧒", "👦", "👩", "🧑", "👨", "👩‍🦱", "🧑‍🦱", "👨‍🦱"),
        "Animals" to listOf("🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯"),
        "Food" to listOf("🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🍈"),
        "Travel" to listOf("🚗", "🚕", "🚙", "🚌", "🚎", "🏎", "🚓", "🚑", "🚒", "🚐"),
        "Objects" to listOf("⌚", "📱", "📲", "💻", "⌨", "🖥", "🖨", "🖱", "🖲", "🕹"),
        "Symbols" to listOf("💘", "💝", "💖", "💗", "💓", "💞", "💕", "💟", "❣", "💔"),
        "Flags" to listOf("🏁", "🚩", "🎌", "🏴", "🏳", "🏳‍🌈", "🏳‍⚧", "🏴‍☠", "🇦🇫", "🇦🇽"),
        "Recent" to listOf("😀", "🚗", "⌚")
    )

    init {
        try {
            emojiTypeface = Typeface.createFromAsset(context.assets, "emojifont.ttf")
        } catch (e: Exception) {
            // Fallback to system font
        }

        binding.emojiRecycler.layoutManager = GridLayoutManager(context, 7)
        setupCategories()
        displayCategory("Smileys")

        setupSearch()

        binding.btnBackFromEmoji.setOnClickListener { onBackClickListener?.invoke() }
    }

    private fun setupSearch() {
        binding.emojiSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().lowercase()
                if (query.isEmpty()) {
                    displayCategory("Smileys")
                } else {
                    val filtered = emojiCategories.values.flatten().distinct().filter { it.contains(query) } // Note: Emojis don't really 'contain' text, but this is a placeholder for search logic
                    binding.emojiRecycler.adapter = EmojiAdapter(filtered)
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun setupCategories() {
        emojiCategories.keys.forEach { category ->
            val btn = Button(context).apply {
                text = category
                setOnClickListener { displayCategory(category) }
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
            }
            binding.emojiCategories.addView(btn)
        }
    }

    private fun displayCategory(category: String) {
        val emojis = emojiCategories[category] ?: emptyList()
        binding.emojiRecycler.adapter = EmojiAdapter(emojis)
    }

    inner class EmojiAdapter(private val emojis: List<String>) : RecyclerView.Adapter<EmojiViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
            val tv = TextView(context).apply {
                textSize = 32f
                gravity = android.view.Gravity.CENTER
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 150)
                emojiTypeface?.let { typeface = it }
            }
            return EmojiViewHolder(tv)
        }

        override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
            val emoji = emojis[position]
            (holder.itemView as TextView).text = emoji
            holder.itemView.setOnClickListener { onEmojiClickListener?.invoke(emoji) }
        }

        override fun getItemCount(): Int = emojis.size
    }

    class EmojiViewHolder(view: View) : RecyclerView.ViewHolder(view)
}
