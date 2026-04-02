package com.musa.t9keyboard

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.musa.t9keyboard.utils.FontUtils
import androidx.emoji2.widget.EmojiTextView

class EmojiAdapter(
    private val context: Context,
    private val items: List<EmojiPickerView.ListItem>,
    private val emojiSize: Float,
    private val rippleProvider: () -> android.graphics.drawable.Drawable?,
    private val onEmojiClick: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemViewType(position: Int): Int {
        val item = items[position]
        return when {
            item is EmojiPickerView.ListItem.Header -> EmojiPickerView.VIEW_TYPE_HEADER
            item is EmojiPickerView.ListItem.Emoji && item.code == "No recent emojis" -> EmojiPickerView.VIEW_TYPE_EMPTY_STATE
            else -> EmojiPickerView.VIEW_TYPE_EMOJI
        }
    }

    class HeaderVH(val tv: TextView) : RecyclerView.ViewHolder(tv)
    class EmojiVH(val tv: TextView) : RecyclerView.ViewHolder(tv)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            EmojiPickerView.VIEW_TYPE_HEADER -> {
                val tv = TextView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(32))
                    textSize = 11f; setTextColor(Color.parseColor("#888888")); typeface = FontUtils.getUbuntu(context)
                    setPadding(dpToPx(12), dpToPx(12), dpToPx(12), 0); gravity = Gravity.CENTER_VERTICAL
                    isAllCaps = true; letterSpacing = 0.05f
                }
                HeaderVH(tv)
            }
            EmojiPickerView.VIEW_TYPE_EMPTY_STATE -> {
                val tv = TextView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(40))
                    textSize = 14f; setTextColor(Color.GRAY); typeface = FontUtils.getUbuntu(context)
                    setPadding(dpToPx(16), 0, 0, 0); gravity = Gravity.START or Gravity.CENTER_VERTICAL
                }
                EmojiVH(tv)
            }
            else -> {
                val tv = EmojiTextView(context).apply {
                    textSize = emojiSize; gravity = Gravity.CENTER; setIncludeFontPadding(true); setLineSpacing(0f, 1.2f)
                    layoutParams = RecyclerView.LayoutParams((emojiSize * 2.5f).toInt(), RecyclerView.LayoutParams.WRAP_CONTENT)
                    minimumHeight = dpToPx(48); isClickable = true; isFocusable = true
                }
                EmojiVH(tv)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is HeaderVH) holder.tv.text = (item as EmojiPickerView.ListItem.Header).name
        else if (holder is EmojiVH) {
            val vt = getItemViewType(position)
            val tv = holder.tv
            val code = (item as EmojiPickerView.ListItem.Emoji).code
            if (vt == EmojiPickerView.VIEW_TYPE_EMPTY_STATE) tv.text = code
            else {
                tv.text = code; tv.textSize = emojiSize; tv.setTextColor(Color.WHITE)
                tv.background = rippleProvider()?.constantState?.newDrawable()?.mutate()
                tv.setOnClickListener { onEmojiClick(code) }
            }
        }
    }

    override fun getItemCount() = items.size
    private fun dpToPx(dp: Int): Int = (dp * context.resources.displayMetrics.density + 0.5f).toInt()
}
