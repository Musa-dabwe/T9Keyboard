package com.musa.t9keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.musa.t9keyboard.utils.FontUtils
import androidx.core.content.res.ResourcesCompat

/**
 * Redesigned Emoji panel:
 * - Top bar: Back, [Flex Space], Search (visual only), Delete
 * - Emoji body: Flat scrollable list with section headers (8 columns)
 * - Noto Color Emoji font
 * - Custom emoji size from settings
 * - Recent Emojis persistence
 */
class EmojiPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    companion object {
        const val COLS = 8
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_EMOJI = 1
        const val VIEW_TYPE_EMPTY_STATE = 2
        const val MAX_RECENT = 24
    }

    var onEmojiClickListener: ((String) -> Unit)? = null
    var onBackspaceClick: (() -> Unit)? = null
    var onBackClickListener: (() -> Unit)? = null
    var onFeedbackRequested: (() -> Unit)? = null

    private val delHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var delRunnable: Runnable? = null
    private var deletionSpeed: Int = 100

    var isInitialized = false
        private set

    private lateinit var emojiRecycler: RecyclerView
    private var accentColor = Color.parseColor("#00BFA5")
    private var currentRipple: android.graphics.drawable.Drawable? = null
    private lateinit var emojiAdapter: EmojiAdapter
    private val preferences = PreferencesManager(context)
    private var cachedEmojiSize: Float = 32f

    private val emojiFont: Typeface? by lazy {
        ResourcesCompat.getFont(context, R.font.noto_color_emoji)
    }

    // Flat list of items for the recycler
    sealed class ListItem {
        data class Header(val name: String) : ListItem()
        data class Emoji(val code: String) : ListItem()
    }

    private val flatList = mutableListOf<ListItem>()

    init {
        try {
            orientation = VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A1A"))

            // Lock to keyboard height
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(304)
            )

            setupViews()
            isInitialized = true
        } catch (e: Exception) {
            Log.e("EmojiPickerView", "Failed to initialize emoji picker: ${e.message}")
            isInitialized = false
        }
    }

    private fun buildFlatList() {
        flatList.clear()

        // 1. Recent Emoji
        val recent = getRecentEmojis()
        flatList.add(ListItem.Header("Recent Emoji"))
        if (recent.isEmpty()) {
            flatList.add(ListItem.Emoji("No recent emojis"))
        } else {
            recent.take(MAX_RECENT).forEach {
                flatList.add(ListItem.Emoji(it))
            }
        }

        // 2. All other categories
        EmojiData.categories.forEach { cat ->
            flatList.add(ListItem.Header(cat.name))
            EmojiData.emojiMap[cat.name]?.values?.flatten()?.distinct()?.forEach {
                flatList.add(ListItem.Emoji(it))
            }
        }
    }

    private fun setupViews() {
        removeAllViews()

        // --- Top bar ---
        val topBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(48))
            setBackgroundColor(Color.parseColor("#111111"))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
        }

        val backBtn = ImageView(context).apply {
            layoutParams = LayoutParams(dpToPx(48), dpToPx(48))
            setImageResource(R.drawable.ic_arrow_small_left)
            scaleType = ImageView.ScaleType.CENTER
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                onFeedbackRequested?.invoke()
                onBackClickListener?.invoke()
            }
        }

        val flexSpace = View(context).apply {
            layoutParams = LayoutParams(0, 1, 1f)
        }

        val searchBtn = ImageView(context).apply {
            layoutParams = LayoutParams(dpToPx(48), dpToPx(48))
            setImageResource(R.drawable.ic_search_heart)
            scaleType = ImageView.ScaleType.CENTER
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                onFeedbackRequested?.invoke()
                // No functionality for now
            }
        }

        val delBtn = ImageView(context).apply {
            layoutParams = LayoutParams(dpToPx(48), dpToPx(48))
            setImageResource(R.drawable.ic_delete)
            scaleType = ImageView.ScaleType.CENTER
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                onFeedbackRequested?.invoke()
                onBackspaceClick?.invoke()
            }
            setOnLongClickListener {
                onFeedbackRequested?.invoke()
                startRepeatingDel()
                true
            }
            setOnTouchListener { _, event ->
                if (event.action == android.view.MotionEvent.ACTION_UP || event.action == android.view.MotionEvent.ACTION_CANCEL) {
                    stopRepeatingDel()
                }
                false
            }
        }

        topBar.addView(backBtn)
        topBar.addView(flexSpace)
        topBar.addView(searchBtn)
        topBar.addView(delBtn)
        addView(topBar)

        // --- Main emoji grid recycler ---
        buildFlatList()
        emojiRecycler = RecyclerView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(Color.parseColor("#2B2B2B"))
        }

        cachedEmojiSize = preferences.emojiSize.toFloat()
        val glm = GridLayoutManager(context, COLS)
        glm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val vt = emojiAdapter.getItemViewType(position)
                return if (vt == VIEW_TYPE_HEADER || vt == VIEW_TYPE_EMPTY_STATE) COLS else 1
            }
        }
        emojiRecycler.layoutManager = glm

        emojiAdapter = EmojiAdapter(flatList) { emoji ->
            onEmojiClickListener?.invoke(emoji)
            addToRecent(emoji)
        }
        emojiRecycler.adapter = emojiAdapter

        addView(emojiRecycler)

        updateButtonRipples(backBtn, searchBtn, delBtn)
    }

    private fun updateButtonRipples(vararg views: View) {
        views.forEach { it.background = currentRipple?.constantState?.newDrawable()?.mutate() }
    }

    fun resetScroll() {
        buildFlatList()
        emojiAdapter.notifyDataSetChanged()
        emojiRecycler.scrollToPosition(0)
    }

    private fun startRepeatingDel() {
        stopRepeatingDel()
        delRunnable = object : Runnable {
            override fun run() {
                onBackspaceClick?.invoke()
                delHandler.postDelayed(this, deletionSpeed.toLong())
            }
        }
        delHandler.postDelayed(delRunnable!!, 500)
    }

    private fun stopRepeatingDel() {
        delRunnable?.let { delHandler.removeCallbacks(it) }
        delRunnable = null
    }

    fun setDeletionSpeed(speed: Int) {
        this.deletionSpeed = speed
    }

    fun setAccentColor(color: Int) {
        this.accentColor = color
        val pressedColor = (color and 0x00FFFFFF) or (0x66 shl 24)
        currentRipple = android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(pressedColor),
            null,
            android.graphics.drawable.ColorDrawable(Color.WHITE)
        )

        // Refresh ripples in top bar
        val topBar = getChildAt(0) as? LinearLayout
        if (topBar != null) {
            for (i in 0 until topBar.childCount) {
                val v = topBar.getChildAt(i)
                if (v is ImageView) {
                    v.background = currentRipple?.constantState?.newDrawable()?.mutate()
                }
            }
        }

        if (::emojiAdapter.isInitialized) {
            emojiAdapter.notifyDataSetChanged()
        }
    }

    private fun getRecentEmojis(): List<String> {
        val s = preferences.recentEmojis
        if (s.isEmpty()) return emptyList()
        return s.split(",")
    }

    private fun addToRecent(emoji: String) {
        val recent = getRecentEmojis().toMutableList()
        recent.remove(emoji)
        recent.add(0, emoji)
        if (recent.size > MAX_RECENT) {
            recent.removeAt(recent.size - 1)
        }
        preferences.recentEmojis = recent.joinToString(",")
    }

    // ---- EMOJI ADAPTER ----
    inner class EmojiAdapter(
        private val items: List<ListItem>,
        private val onEmojiClick: (String) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int {
            val item = items[position]
            return when {
                item is ListItem.Header -> VIEW_TYPE_HEADER
                item is ListItem.Emoji && item.code == "No recent emojis" -> VIEW_TYPE_EMPTY_STATE
                else -> VIEW_TYPE_EMOJI
            }
        }

        inner class HeaderVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tv: TextView = itemView as TextView
        }

        inner class EmojiVH(val tv: TextView) : RecyclerView.ViewHolder(tv)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                VIEW_TYPE_HEADER -> {
                    val tv = TextView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dpToPx(32)
                        )
                        textSize = 11f
                        setTextColor(Color.parseColor("#888888"))
                        typeface = FontUtils.getUbuntu(context)
                        setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(0))
                        gravity = Gravity.CENTER_VERTICAL
                        isAllCaps = true
                        letterSpacing = 0.05f
                    }
                    HeaderVH(tv)
                }
                VIEW_TYPE_EMPTY_STATE -> {
                    val tv = TextView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dpToPx(40)
                        )
                        textSize = 14f
                        setTextColor(Color.GRAY)
                        typeface = FontUtils.getUbuntu(context)
                        setPadding(dpToPx(16), 0, 0, 0)
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    }
                    EmojiVH(tv)
                }
                else -> {
                    val tv = TextView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dpToPx(48)
                        )
                        gravity = Gravity.CENTER
                        isClickable = true
                        isFocusable = true
                    }
                    EmojiVH(tv)
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            when (holder) {
                is HeaderVH -> {
                    holder.tv.text = (item as ListItem.Header).name
                }
                is EmojiVH -> {
                    val vt = getItemViewType(position)
                    if (vt == VIEW_TYPE_EMPTY_STATE) {
                        holder.tv.text = (item as ListItem.Emoji).code
                    } else {
                        val code = (item as ListItem.Emoji).code
                        holder.tv.text = code
                        holder.tv.typeface = emojiFont
                        holder.tv.textSize = cachedEmojiSize
                        holder.tv.setTextColor(Color.WHITE)
                        holder.tv.background = currentRipple?.constantState?.newDrawable()?.mutate()
                        holder.tv.setOnClickListener { onEmojiClick(code) }
                    }
                }
            }
        }

        override fun getItemCount() = items.size
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density + 0.5f).toInt()
}
