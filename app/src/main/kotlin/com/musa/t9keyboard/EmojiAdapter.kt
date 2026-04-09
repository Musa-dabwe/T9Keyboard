package com.musa.t9keyboard

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.musa.t9keyboard.utils.FontUtils
import androidx.emoji2.widget.EmojiTextView

class EmojiAdapter(
    private val context: Context,
    private val emojiSize: Float,
    private val rippleProvider: () -> android.graphics.drawable.Drawable?,
    private val onEmojiClick: (String) -> Unit
) : ListAdapter<EmojiPickerView.ListItem, RecyclerView.ViewHolder>(EmojiDiffCallback()) {

    private val headerLayoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        dpToPx(32)
    )
    private val emptyStateLayoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        dpToPx(40)
    )
    private val emojiLayoutParams = RecyclerView.LayoutParams(
        (emojiSize * 2.5f).toInt(),
        RecyclerView.LayoutParams.WRAP_CONTENT
    )

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is EmojiPickerView.ListItem.Header -> EmojiPickerView.VIEW_TYPE_HEADER
            is EmojiPickerView.ListItem.EmptyState -> EmojiPickerView.VIEW_TYPE_EMPTY_STATE
            is EmojiPickerView.ListItem.Emoji -> EmojiPickerView.VIEW_TYPE_EMOJI
        }
    }

    class HeaderVH(val tv: TextView) : RecyclerView.ViewHolder(tv)
    class EmojiVH(val tv: TextView) : RecyclerView.ViewHolder(tv)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            EmojiPickerView.VIEW_TYPE_HEADER -> {
                val tv = TextView(context).apply {
                    layoutParams = headerLayoutParams
                    textSize = 11f
                    setTextColor(Color.parseColor("#888888"))
                    typeface = FontUtils.getUbuntu(context)
                    setPadding(dpToPx(12), dpToPx(12), dpToPx(12), 0)
                    gravity = Gravity.CENTER_VERTICAL
                    isAllCaps = true
                    letterSpacing = 0.05f
                }
                HeaderVH(tv)
            }
            EmojiPickerView.VIEW_TYPE_EMPTY_STATE -> {
                val tv = TextView(context).apply {
                    layoutParams = emptyStateLayoutParams
                    textSize = 14f
                    setTextColor(Color.GRAY)
                    typeface = FontUtils.getUbuntu(context)
                    setPadding(dpToPx(16), 0, 0, 0)
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                }
                EmojiVH(tv)
            }
            else -> {
                val tv = EmojiTextView(context).apply {
                    textSize = emojiSize
                    gravity = Gravity.CENTER
                    setIncludeFontPadding(true)
                    setLineSpacing(0f, 1.2f)
                    layoutParams = emojiLayoutParams
                    minimumHeight = dpToPx(48)
                    isClickable = true
                    isFocusable = true
                }
                EmojiVH(tv)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is HeaderVH -> {
                holder.tv.text = (item as EmojiPickerView.ListItem.Header).name
            }
            is EmojiVH -> {
                val tv = holder.tv
                if (item is EmojiPickerView.ListItem.EmptyState) {
                    tv.text = item.message
                    tv.background = null
                    tv.setOnClickListener(null)
                } else if (item is EmojiPickerView.ListItem.Emoji) {
                    val code = item.code
                    tv.text = code
                    tv.textSize = emojiSize
                    tv.setTextColor(Color.WHITE)
                    tv.background = rippleProvider()?.constantState?.newDrawable()?.mutate()
                    tv.setOnClickListener { onEmojiClick(code) }
                }
            }
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * context.resources.displayMetrics.density + 0.5f).toInt()

    class EmojiDiffCallback : DiffUtil.ItemCallback<EmojiPickerView.ListItem>() {
        override fun areItemsTheSame(oldItem: EmojiPickerView.ListItem, newItem: EmojiPickerView.ListItem): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: EmojiPickerView.ListItem, newItem: EmojiPickerView.ListItem): Boolean {
            return oldItem == newItem
        }
    }
}
