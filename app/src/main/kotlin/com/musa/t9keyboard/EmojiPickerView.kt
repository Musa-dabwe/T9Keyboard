package com.musa.t9keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.emoji2.text.EmojiCompat

class EmojiPickerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_EMOJI = 1
        const val VIEW_TYPE_EMPTY_STATE = 2
        private const val COLS = 8
        private const val MAX_RECENT = 40
    }

    sealed class ListItem {
        data class Header(val name: String) : ListItem()
        data class Emoji(val code: String) : ListItem()
        data class EmptyState(val message: String) : ListItem()
    }

    var onEmojiClickListener: ((String) -> Unit)? = null
    var onBackspaceClick: (() -> Unit)? = null
    var onBackClickListener: (() -> Unit)? = null
    var onFeedbackRequested: (() -> Unit)? = null
    var onSwipeDownListener: (() -> Unit)? = null
    var onSearchTriggered: (() -> Unit)? = null

    private val preferences = PreferencesManager(context)
    private var emojiRecycler: RecyclerView? = null
    private var emojiAdapter: EmojiAdapter? = null
    private val flatList = mutableListOf<ListItem>()
    private var cachedEmojiSize = 24f
    private var currentRipple: android.graphics.drawable.Drawable? = null
    private var accentColor = Color.parseColor("#E91E63")

    init {
        orientation = VERTICAL
        setupViews()
    }

    private fun setupViews() {
        removeAllViews()

        val topBar = LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(48))
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#333333"))
        }

        setupNormalTopBar(topBar)
        addView(topBar)

        buildFlatList()
        emojiRecycler = RecyclerView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(Color.parseColor("#2B2B2B"))
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

    private fun setupNormalTopBar(topBar: LinearLayout) {
        val flexSpace = View(context).apply {
            layoutParams = LayoutParams(0, 1, 1f)
        }

        val searchBtn = createTopButton(R.drawable.ic_search_heart).apply {
            setOnClickListener {
                onFeedbackRequested?.invoke()
                onSearchTriggered?.invoke()
            }
        }
        val backBtn = createTopButton(R.drawable.ic_arrow_small_left).apply {
            setOnClickListener {
                onFeedbackRequested?.invoke()
                onBackClickListener?.invoke()
            }
        }

        topBar.addView(flexSpace)
        topBar.addView(searchBtn)
        topBar.addView(backBtn)
    }

    private fun createTopButton(resId: Int) = ImageView(context).apply {
        layoutParams = LayoutParams(dpToPx(48), dpToPx(48))
        setImageResource(resId)
        scaleType = ImageView.ScaleType.CENTER
        setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
        isClickable = true
        isFocusable = true
    }

    private fun buildFlatList() {
        flatList.clear()

        // Recents
        val recentsStr = preferences.recentEmojis
        if (recentsStr.isNotEmpty()) {
            flatList.add(ListItem.Header("Recent"))
            recentsStr.split(",").filter { isEmojiSupported(it) }.forEach { flatList.add(ListItem.Emoji(it)) }
        } else {
            flatList.add(ListItem.Header("Recent"))
            flatList.add(ListItem.EmptyState("No recent emojis"))
        }

        // Categories
        EmojiData.categories.forEach { category ->
            val allInCat = mutableListOf<String>()
            EmojiData.emojiMap[category.name]?.values?.forEach { allInCat.addAll(it) }
            val supported = allInCat.filter { isEmojiSupported(it) }
            if (supported.isNotEmpty()) {
                flatList.add(ListItem.Header(category.name))
                supported.forEach { flatList.add(ListItem.Emoji(it)) }
            }
        }
    }

    private fun isEmojiSupported(emoji: String): Boolean {
        if (!EmojiCompat.isConfigured()) return true
        return try {
            EmojiCompat.get().getEmojiMatch(emoji, Int.MAX_VALUE) != EmojiCompat.EMOJI_UNSUPPORTED
        } catch (e: Exception) {
            true
        }
    }

    fun resetScroll() {
        refreshEmojiList()
        emojiRecycler?.scrollToPosition(0)
    }

    private fun refreshEmojiList() {
        buildFlatList()
        emojiAdapter?.notifyDataSetChanged()
    }

    fun setAccentColor(color: Int) {
        this.accentColor = color
        val pressedColor = (color and 0x00FFFFFF) or (0x66 shl 24)
        currentRipple = android.graphics.drawable.RippleDrawable(
            ColorStateList.valueOf(pressedColor),
            null,
            android.graphics.drawable.ColorDrawable(Color.WHITE)
        )

        val topBar = getChildAt(0) as? LinearLayout
        if (topBar != null) {
            val tint = ColorStateList.valueOf(color)
            for (i in 0 until topBar.childCount) {
                val v = topBar.getChildAt(i)
                if (v is ImageView) {
                    v.background = currentRipple?.constantState?.newDrawable()?.mutate()
                    ImageViewCompat.setImageTintList(v, tint)
                }
            }
        }
        emojiAdapter?.notifyDataSetChanged()
    }

    private fun addToRecent(emoji: String) {
        val recentEmojisStr = preferences.recentEmojis
        val recent = if (recentEmojisStr.isEmpty()) mutableListOf() else recentEmojisStr.split(",").toMutableList()
        recent.remove(emoji)
        recent.add(0, emoji)
        if (recent.size > MAX_RECENT) {
            recent.removeAt(recent.size - 1)
        }
        preferences.recentEmojis = recent.joinToString(",")
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density + 0.5f).toInt()
}
