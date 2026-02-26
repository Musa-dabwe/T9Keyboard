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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * GBoard-style emoji panel:
 * - Category tabs row at top (scrollable horizontal strip)
 * - Vertical RecyclerView with section headers + emoji grid rows (8 per row)
 */
class EmojiPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    companion object {
        const val COLS = 8
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_EMOJI_ROW = 1
    }

    var onEmojiClickListener: ((String) -> Unit)? = null
    var onBackspaceClick: (() -> Unit)? = null
    var onBackClickListener: (() -> Unit)? = null

    var isInitialized = false
        private set

    private lateinit var tabsRecycler: RecyclerView
    private lateinit var emojiRecycler: RecyclerView
    private var accentColor = Color.parseColor("#00BFA5")
    private lateinit var tabsAdapter: TabsAdapter
    private lateinit var emojiListAdapter: EmojiListAdapter

    // Flat list of items for the main recycler
    sealed class ListItem {
        data class Header(val categoryName: String, val subcategoryName: String) : ListItem()
        data class EmojiRow(val emojis: List<String>) : ListItem()
    }

    // Map from category index to first item position in flat list
    private val categoryPositions = mutableListOf<Int>()
    private val flatList = mutableListOf<ListItem>()

    init {
        try {
            orientation = VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A1A"))

            // Lock to keyboard height — prevents full-screen takeover
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(304)
            )

            buildFlatList()
            setupViews()
            isInitialized = true
        } catch (e: Exception) {
            Log.e("EmojiPickerView", "Failed to initialize emoji picker: ${e.message}")
            isInitialized = false
        }
    }

    private fun buildFlatList() {
        flatList.clear()
        categoryPositions.clear()

        EmojiData.categories.forEachIndexed { catIndex, cat ->
            categoryPositions.add(flatList.size)
            val subsMap = EmojiData.emojiMap[cat.name] ?: return@forEachIndexed

            subsMap.forEach { (subName, emojis) ->
                flatList.add(ListItem.Header(cat.name, subName))
                // Chunk emojis into rows of COLS
                emojis.chunked(COLS).forEach { rowEmojis ->
                    flatList.add(ListItem.EmojiRow(rowEmojis))
                }
            }
        }
    }

    private fun setupViews() {
        // --- Top bar: Category tabs ---
        val topBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(44))
            setBackgroundColor(Color.parseColor("#111111"))
            gravity = Gravity.CENTER_VERTICAL
        }

        tabsRecycler = RecyclerView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        tabsAdapter = TabsAdapter(EmojiData.categories) { catIndex ->
            scrollToCategory(catIndex)
            tabsAdapter.setSelected(catIndex)
        }
        tabsAdapter.setSelected(0)
        tabsRecycler.adapter = tabsAdapter

        topBar.addView(tabsRecycler)
        addView(topBar)

        // --- Divider ---
        addView(View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(Color.parseColor("#333333"))
        })

        // --- Main emoji grid recycler ---
        emojiRecycler = RecyclerView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(Color.parseColor("#2B2B2B"))
        }

        emojiRecycler.layoutManager = LinearLayoutManager(context)
        emojiListAdapter = EmojiListAdapter(flatList) { emoji ->
            onEmojiClickListener?.invoke(emoji)
        }
        emojiRecycler.adapter = emojiListAdapter

        // Sync tab highlight when scrolling
        emojiRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val lm = recyclerView.layoutManager as LinearLayoutManager
                val firstVisible = lm.findFirstVisibleItemPosition()
                if (firstVisible == RecyclerView.NO_POSITION) return

                // Find which category we're in
                var activeCat = 0
                for (i in categoryPositions.indices) {
                    if (firstVisible >= categoryPositions[i]) activeCat = i
                    else break
                }
                tabsAdapter.setSelected(activeCat)
            }
        })

        addView(emojiRecycler)

        // --- Bottom bar: ABC and DEL ---
        val bottomBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(48))
            setBackgroundColor(Color.parseColor("#1A1A1A"))
        }

        val abcBtn = TextView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 3f)
            text = "ABC"
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener { onBackClickListener?.invoke() }
        }

        val delBtn = TextView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            text = "⌫"
            setTextColor(Color.parseColor("#FF5252"))
            textSize = 22f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener { onBackspaceClick?.invoke() }
        }

        bottomBar.addView(abcBtn)
        bottomBar.addView(delBtn)
        addView(bottomBar)
    }

    fun scrollToCategory(catIndex: Int) {
        if (catIndex < categoryPositions.size) {
            (emojiRecycler.layoutManager as LinearLayoutManager)
                .scrollToPositionWithOffset(categoryPositions[catIndex], 0)
        }
    }

    fun resetScroll() {
        emojiRecycler.scrollToPosition(0)
        tabsAdapter.setSelected(0)
    }

    fun setAccentColor(color: Int) {
        this.accentColor = color
        if (::tabsAdapter.isInitialized) {
            tabsAdapter.notifyDataSetChanged()
        }
    }

    // ---- TABS ADAPTER ----
    inner class TabsAdapter(
        private val categories: List<EmojiData.EmojiCategory>,
        private val onTabClick: (Int) -> Unit
    ) : RecyclerView.Adapter<TabsAdapter.TabVH>() {

        private var selectedIndex = 0

        fun setSelected(index: Int) {
            if (selectedIndex == index) return
            val old = selectedIndex
            selectedIndex = index
            notifyItemChanged(old)
            notifyItemChanged(index)
            tabsRecycler.scrollToPosition(index)
        }

        inner class TabVH(val tv: TextView) : RecyclerView.ViewHolder(tv)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabVH {
            val tv = TextView(context).apply {
                layoutParams = ViewGroup.LayoutParams(dpToPx(48), dpToPx(44))
                textSize = 22f
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
            }
            return TabVH(tv)
        }

        override fun onBindViewHolder(holder: TabVH, position: Int) {
            holder.tv.text = categories[position].icon
            val isSelected = position == selectedIndex
            holder.tv.setBackgroundColor(
                if (isSelected) accentColor else Color.TRANSPARENT
            )
            holder.tv.setOnClickListener { onTabClick(position) }
        }

        override fun getItemCount() = categories.size
    }

    // ---- EMOJI LIST ADAPTER ----
    inner class EmojiListAdapter(
        private val items: List<ListItem>,
        private val onEmojiClick: (String) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int) = when (items[position]) {
            is ListItem.Header -> VIEW_TYPE_HEADER
            is ListItem.EmojiRow -> VIEW_TYPE_EMOJI_ROW
        }

        inner class HeaderVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tv: TextView = itemView as TextView
        }

        inner class EmojiRowVH(val row: LinearLayout) : RecyclerView.ViewHolder(row)

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
                        setTypeface(null, Typeface.BOLD)
                        setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(4))
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    HeaderVH(tv)
                }
                else -> {
                    val row = LinearLayout(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dpToPx(48)
                        )
                        orientation = HORIZONTAL
                    }
                    repeat(COLS) {
                        val cell = TextView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                            textSize = 24f
                            gravity = Gravity.CENTER
                            isClickable = true
                            isFocusable = true
                        }
                        row.addView(cell)
                    }
                    EmojiRowVH(row)
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is ListItem.Header -> {
                    val hVH = holder as HeaderVH
                    hVH.tv.text = item.subcategoryName
                        .replace('-', ' ')
                        .uppercase()
                }
                is ListItem.EmojiRow -> {
                    val rVH = holder as EmojiRowVH
                    for (i in 0 until COLS) {
                        val cell = rVH.row.getChildAt(i) as TextView
                        if (i < item.emojis.size) {
                            cell.text = item.emojis[i]
                            cell.visibility = View.VISIBLE
                            cell.setOnClickListener { onEmojiClick(item.emojis[i]) }
                        } else {
                            cell.text = ""
                            cell.visibility = View.INVISIBLE
                            cell.setOnClickListener(null)
                        }
                    }
                }
            }
        }

        override fun getItemCount() = items.size
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density + 0.5f).toInt()
}
