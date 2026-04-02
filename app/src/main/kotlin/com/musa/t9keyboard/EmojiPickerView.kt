package com.musa.t9keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.musa.t9keyboard.utils.FontUtils

class EmojiPickerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    companion object {
        const val COLS = 8; const val VIEW_TYPE_HEADER = 0; const val VIEW_TYPE_EMOJI = 1; const val VIEW_TYPE_EMPTY_STATE = 2; const val MAX_RECENT = 24
    }

    var onEmojiClickListener: ((String) -> Unit)? = null
    var onBackspaceClick: (() -> Unit)? = null
    var onBackClickListener: (() -> Unit)? = null
    var onFeedbackRequested: (() -> Unit)? = null

    private var deletionSpeed: Int = 100
    private var emojiRecycler: RecyclerView? = null
    private var accentColor = Color.parseColor("#00BFA5")
    private var currentRipple: android.graphics.drawable.Drawable? = null
    private var emojiAdapter: EmojiAdapter? = null
    private val preferences = PreferencesManager(context)
    private var cachedEmojiSize: Float = 32f
    sealed class ListItem { data class Header(val name: String) : ListItem(); data class Emoji(val code: String) : ListItem() }
    private val flatList = mutableListOf<ListItem>()

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#1A1A1A"))
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(304))
        setupViews()
    }

    private fun buildFlatList() {
        flatList.clear()
        val recent = preferences.recentEmojis.let { if (it.isEmpty()) emptyList() else it.split(",") }
        flatList.add(ListItem.Header("Recent Emoji"))
        if (recent.isEmpty()) flatList.add(ListItem.Emoji("No recent emojis"))
        else recent.take(MAX_RECENT).forEach { flatList.add(ListItem.Emoji(it)) }

        EmojiData.categories.forEach { cat ->
            flatList.add(ListItem.Header(cat.name))
            EmojiData.emojiMap[cat.name]?.values?.flatten()?.distinct()?.forEach { flatList.add(ListItem.Emoji(it)) }
        }
    }

    private fun setupViews() {
        removeAllViews()
        val topBar = LinearLayout(context).apply {
            orientation = HORIZONTAL; layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(48)); setBackgroundColor(Color.parseColor("#111111")); gravity = Gravity.CENTER_VERTICAL; setPadding(dpToPx(8), 0, dpToPx(8), 0)
        }
        val flexSpace = View(context).apply { layoutParams = LayoutParams(0, 1, 1f) }
        val searchBtn = createTopButton(R.drawable.ic_search_heart)
        val backBtn = createTopButton(R.drawable.ic_arrow_small_left).apply { setOnClickListener { onFeedbackRequested?.invoke(); onBackClickListener?.invoke() } }

        topBar.addView(flexSpace); topBar.addView(searchBtn); topBar.addView(backBtn); addView(topBar)

        buildFlatList()
        emojiRecycler = RecyclerView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f); overScrollMode = View.OVER_SCROLL_NEVER; setBackgroundColor(Color.parseColor("#2B2B2B"))
        }

        cachedEmojiSize = preferences.emojiSize.toFloat()
        val glm = GridLayoutManager(context, COLS).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    val vt = emojiAdapter?.getItemViewType(position)
                    return if (vt == VIEW_TYPE_HEADER || vt == VIEW_TYPE_EMPTY_STATE) COLS else 1
                }
            }
        }
        emojiRecycler?.layoutManager = glm
        emojiAdapter = EmojiAdapter(context, flatList, cachedEmojiSize, { currentRipple }) { emoji ->
            onEmojiClickListener?.invoke(emoji)
            addToRecent(emoji)
        }
        emojiRecycler?.adapter = emojiAdapter
        addView(emojiRecycler)
        setAccentColor(accentColor)
    }

    private fun createTopButton(resId: Int) = ImageView(context).apply {
        layoutParams = LayoutParams(dpToPx(48), dpToPx(48)); setImageResource(resId); scaleType = ImageView.ScaleType.CENTER; setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12)); isClickable = true; isFocusable = true
    }

    fun resetScroll() {
        buildFlatList()
        emojiAdapter?.notifyDataSetChanged()
        emojiRecycler?.scrollToPosition(0)
    }

    fun setDeletionSpeed(speed: Int) { this.deletionSpeed = speed }

    fun setAccentColor(color: Int) {
        this.accentColor = color
        currentRipple = android.graphics.drawable.RippleDrawable(ColorStateList.valueOf((color and 0x00FFFFFF) or (0x66 shl 24)), null, android.graphics.drawable.ColorDrawable(Color.WHITE))

        val topBar = getChildAt(0) as? LinearLayout
        if (topBar != null) {
            val tint = ColorStateList.valueOf(color)
            for (i in 0 until topBar.childCount) {
                val v = topBar.getChildAt(i)
                if (v is ImageView) { v.background = currentRipple?.constantState?.newDrawable()?.mutate(); ImageViewCompat.setImageTintList(v, tint) }
            }
        }
        emojiAdapter?.notifyDataSetChanged()
    }

    private fun addToRecent(emoji: String) {
        val recent = preferences.recentEmojis.let { if (it.isEmpty()) mutableListOf() else it.split(",").toMutableList() }
        recent.remove(emoji); recent.add(0, emoji)
        if (recent.size > MAX_RECENT) recent.removeAt(recent.size - 1)
        preferences.recentEmojis = recent.joinToString(",")
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density + 0.5f).toInt()
}
