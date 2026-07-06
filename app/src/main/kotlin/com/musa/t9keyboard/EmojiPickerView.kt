package com.musa.t9keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.emoji2.widget.EmojiTextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.PaintCompat
import androidx.core.widget.ImageViewCompat
import androidx.emoji2.text.EmojiCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.musa.t9keyboard.utils.FontUtils

class EmojiPickerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
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
    var onSwipeDownListener: (() -> Unit)? = null
    var onSearchModeListener: ((Boolean) -> Unit)? = null

    private var deletionSpeed: Int = 100
    private var emojiRecycler: RecyclerView? = null
    private var accentColor = Color.parseColor("#00BFA5")
    private var currentRipple: android.graphics.drawable.Drawable? = null
    private var emojiAdapter: EmojiAdapter? = null
    private val preferences = PreferencesManager(context)
    private var cachedEmojiSize: Float = 32f
    private val paint = Paint()

    private var isSearchMode = false
    private val searchQuery = StringBuilder()
    private var searchTextView: TextView? = null
    private var searchResultsRow: LinearLayout? = null
    private var searchResultsContainer: View? = null
    private var searchKeyboard: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var cursorVisible = true
    private val cursorRunnable = object : Runnable {
        override fun run() {
            cursorVisible = !cursorVisible
            updateSearchDisplayText()
            handler.postDelayed(this, 500)
        }
    }

    private val emojiCompatCallback = object : EmojiCompat.InitCallback() {
        override fun onInitialized() {
            post {
                if (isAttachedToWindow) {
                    refreshEmojiList()
                }
            }
        }
    }

    sealed class ListItem {
        data class Header(val name: String) : ListItem()
        data class Emoji(val code: String) : ListItem()
        data class EmptyState(val message: String) : ListItem()
    }

    private val flatList = mutableListOf<ListItem>()

    init {
        orientation = VERTICAL
        setBackgroundColor(ContextCompat.getColor(context, R.color.keyboard_background))
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(304))
        setupViews()
    }

    private fun isEmojiSupported(emoji: String): Boolean {
        if (emoji.isEmpty()) return false
        if (!PaintCompat.hasGlyph(paint, emoji)) return false
        return try {
            val emojiCompat = EmojiCompat.get()
            if (emojiCompat.loadState != EmojiCompat.LOAD_STATE_SUCCEEDED) return true
            val match = emojiCompat.getEmojiMatch(emoji, Int.MAX_VALUE)
            match != EmojiCompat.EMOJI_UNSUPPORTED
        } catch (e: IllegalStateException) {
            true
        }
    }

    private fun buildFlatList() {
        flatList.clear()
        if (isSearchMode) {
            // In search mode, we don't show the grid.
            // The grid is replaced by the search keyboard.
            return
        }

        val recentEmojisStr = preferences.recentEmojis
        val recent = if (recentEmojisStr.isEmpty()) emptyList() else recentEmojisStr.split(",")
            .filter { isEmojiSupported(it) }

        flatList.add(ListItem.Header("Recent Emoji"))
        if (recent.isEmpty()) {
            flatList.add(ListItem.EmptyState("No recent emojis"))
        } else {
            recent.take(MAX_RECENT).forEach {
                flatList.add(ListItem.Emoji(it))
            }
        }

        EmojiData.categories.forEach { cat ->
            val categoryEmojis = EmojiData.emojiMap[cat.name]?.values?.flatten()?.distinct()
                ?.filter { isEmojiSupported(it) } ?: emptyList()

            if (categoryEmojis.isNotEmpty()) {
                flatList.add(ListItem.Header(cat.name))
                categoryEmojis.forEach {
                    flatList.add(ListItem.Emoji(it))
                }
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        try {
            EmojiCompat.get().registerInitCallback(emojiCompatCallback)
        } catch (e: IllegalStateException) {
            // EmojiCompat not initialized
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try {
            EmojiCompat.get().unregisterInitCallback(emojiCompatCallback)
        } catch (e: IllegalStateException) {
            // EmojiCompat not initialized
        }
        if (isSearchMode) {
            exitSearchMode()
        }
    }

    private fun setupViews() {
        removeAllViews()
        val topBar = LinearLayout(context).apply {
            setOnTouchListener(SwipeDownListener(context) { onSwipeDownListener?.invoke() })
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(48))
            setBackgroundColor(ContextCompat.getColor(context, R.color.keyboard_background))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
        }

        if (isSearchMode) {
            setupSearchView(topBar)
            addView(topBar)

            // Results Row
            val resultsLayout = inflate(context, R.layout.emoji_search_results, null)
            searchResultsContainer = resultsLayout
            searchResultsRow = resultsLayout.findViewById(R.id.emoji_search_results_row)
            // searchResultsContainer is always visible
            addView(searchResultsContainer)
            showSearchHint()

            // QWERTY Keyboard
            searchKeyboard = inflate(context, R.layout.emoji_search_keyboard, null).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            }
            setupSearchKeyboard(searchKeyboard!!)
            addView(searchKeyboard)
        } else {
            setupNormalTopBar(topBar)
            addView(topBar)

            buildFlatList()
            emojiRecycler = RecyclerView(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
                overScrollMode = View.OVER_SCROLL_NEVER
                setBackgroundColor(ContextCompat.getColor(context, R.color.keyboard_background))
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
        }
        setAccentColor(accentColor)
    }

    private fun setupSearchKeyboard(keyboard: View) {
        val alphabet = "QWERTYUIOPASDFGHJKLZXCVBNM"
        for (char in alphabet) {
            val id = resources.getIdentifier("key_${char.lowercaseChar()}", "id", context.packageName)
            keyboard.findViewById<TextView>(id)?.apply {
                typeface = FontUtils.getUbuntu(context)
                setOnClickListener {
                    onFeedbackRequested?.invoke()
                    searchQuery.append(char.lowercaseChar())
                    updateSearchDisplayText()
                    performFuzzySearch(searchQuery.toString())
                }
            }
        }

        keyboard.findViewById<ImageView>(R.id.key_backspace)?.apply {
            setOnClickListener {
                onFeedbackRequested?.invoke()
                deleteSearchChar()
            }
        }
    }

    private fun setupNormalTopBar(topBar: LinearLayout) {
        val flexSpace = View(context).apply {
            layoutParams = LayoutParams(0, 1, 1f)
        }

        val searchBtn = createTopButton(R.drawable.ic_search_heart).apply {
            setOnClickListener {
                onFeedbackRequested?.invoke()
                enterSearchMode()
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

    private fun setupSearchView(topBar: LinearLayout) {
        val closeBtn = createTopButton(R.drawable.ic_arrow_small_left).apply {
            setOnClickListener {
                onFeedbackRequested?.invoke()
                exitSearchMode()
            }
        }

        searchTextView = TextView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            hint = "Search emoji..."
            setHintTextColor(Color.parseColor("#888888"))
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = FontUtils.getUbuntu(context)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
        }
        updateSearchDisplayText()

        topBar.addView(closeBtn)
        topBar.addView(searchTextView)
    }

    fun enterSearchMode() {
        if (isSearchMode) return
        isSearchMode = true
        searchQuery.setLength(0)
        onSearchModeListener?.invoke(true)
        setupViews()
        handler.post(cursorRunnable)
    }

    fun exitSearchMode() {
        if (!isSearchMode) return
        isSearchMode = false
        onSearchModeListener?.invoke(false)
        handler.removeCallbacks(cursorRunnable)
        setupViews()
    }

    fun updateSearchChar(c: Char, isNewChar: Boolean) {
        if (!isSearchMode) return
        if (isNewChar) {
            searchQuery.append(c.lowercaseChar())
        } else if (searchQuery.isNotEmpty()) {
            searchQuery.setCharAt(searchQuery.length - 1, c.lowercaseChar())
        }
        updateSearchDisplayText()
        performFuzzySearch(searchQuery.toString())
    }

    fun appendSearchChar(c: Char) {
        updateSearchChar(c, true)
    }

    fun deleteSearchChar() {
        if (!isSearchMode || searchQuery.isEmpty()) {
            return
        }
        searchQuery.deleteCharAt(searchQuery.length - 1)
        updateSearchDisplayText()
        if (searchQuery.isEmpty()) {
            showSearchHint()
        } else {
            performFuzzySearch(searchQuery.toString())
        }
    }

    private fun performFuzzySearch(query: String) {
        if (query.isEmpty()) {
            showSearchHint()
            return
        }
        val results = EmojiSearchEngine.search(query, maxResults = 5).filter { isEmojiSupported(it) }
        if (results.isEmpty()) {
            showSearchHint()
        } else {
            // searchResultsContainer is always visible
            updateSearchResultsRow(results)
        }
    }

    private fun showSearchHint() {
        val row = searchResultsRow ?: return
        row.removeAllViews()
        val hint = TextView(context).apply {
            text = "Type to search emoji"
            textSize = 14f
            setTextColor(0x99FFFFFF.toInt())
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(16), 0, 0, 0)
            typeface = FontUtils.getUbuntu(context)
        }
        row.addView(hint)
    }

    private fun updateSearchResultsRow(emojis: List<String>) {
        val row = searchResultsRow ?: return
        row.removeAllViews()
        emojis.forEach { emoji ->
            val textView = inflate(context, R.layout.emoji_search_item, null) as EmojiTextView
            textView.apply {
                text = emoji
                setOnClickListener {
                    onFeedbackRequested?.invoke()
                    onEmojiClickListener?.invoke(emoji)
                    addToRecent(emoji)
                }
            }
            row.addView(textView)
        }
    }

    private fun updateSearchDisplayText() {
        val tv = searchTextView ?: return
        val cursor = if (cursorVisible) "|" else " "
        val fullText = searchQuery.toString() + cursor

        val spannable = SpannableString(fullText)
        val cursorIndex = searchQuery.length
        spannable.setSpan(
            ForegroundColorSpan(accentColor),
            cursorIndex,
            cursorIndex + 1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        if (searchQuery.isNotEmpty()) {
            spannable.setSpan(
                UnderlineSpan(),
                0,
                searchQuery.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        tv.text = spannable
    }

    private fun createTopButton(resId: Int) = ImageView(context).apply {
        layoutParams = LayoutParams(dpToPx(48), dpToPx(48))
        setImageResource(resId)
        scaleType = ImageView.ScaleType.CENTER
        setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
        isClickable = true
        isFocusable = true
    }

    fun resetScroll() {
        refreshEmojiList()
        emojiRecycler?.scrollToPosition(0)
    }

    private fun refreshEmojiList() {
        buildFlatList()
        emojiAdapter?.notifyDataSetChanged()
    }

    fun setDeletionSpeed(speed: Int) {
        this.deletionSpeed = speed
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
        searchKeyboard?.findViewById<ImageView>(R.id.key_backspace)?.let {
            ImageViewCompat.setImageTintList(it, ColorStateList.valueOf(color))
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
