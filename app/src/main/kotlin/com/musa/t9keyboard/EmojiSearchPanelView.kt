package com.musa.t9keyboard

import android.content.Context
import android.util.AttributeSet
import android.view.*
import android.widget.*
import android.graphics.Color
import android.content.res.ColorStateList
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.emoji2.widget.EmojiTextView
import com.musa.t9keyboard.utils.FontUtils

class EmojiSearchPanelView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    interface Listener {
        fun onEmojiSelected(emoji: String)
        fun onCloseRequested()
        fun onFeedbackRequested()
    }

    var listener: Listener? = null
    private var accentColorInternal: Int = Color.parseColor("#E91E63")

    private lateinit var queryText: TextView
    private lateinit var underline: View
    private lateinit var resultsRecycler: RecyclerView
    private lateinit var deleteBtn: ImageButton
    private lateinit var gestureDetector: GestureDetector

    private val queryBuilder = StringBuilder()
    private var resultsAdapter: EmojiResultAdapter? = null

    init {
        orientation = VERTICAL
        isFocusable = true
        isFocusableInTouchMode = true
        inflate(context, R.layout.emoji_search_panel, this)
        bindViews()
        setupLetterKeys()
        setupSwipeToClose()
    }

    fun setAccentColor(color: Int) {
        accentColorInternal = color
        if (::underline.isInitialized) {
            underline.setBackgroundColor(color)
        }
        if (::deleteBtn.isInitialized) {
            ImageViewCompat.setImageTintList(deleteBtn, ColorStateList.valueOf(color))
        }
    }

    fun resetQuery() {
        queryBuilder.setLength(0)
        queryText.text = ""
        resultsAdapter?.submitList(emptyList())
        resultsRecycler.visibility = GONE
    }

    private fun bindViews() {
        queryText = findViewById(R.id.emoji_search_query_text)
        underline = findViewById(R.id.emoji_search_underline)
        resultsRecycler = findViewById(R.id.emoji_search_results)
        deleteBtn = findViewById(R.id.emoji_search_delete)

        resultsAdapter = EmojiResultAdapter { emoji ->
            listener?.onFeedbackRequested()
            listener?.onEmojiSelected(emoji)
        }
        resultsRecycler.adapter = resultsAdapter

        deleteBtn.setOnClickListener {
            listener?.onFeedbackRequested()
            handleDelete()
        }

        queryText.typeface = FontUtils.getUbuntu(context)
        setAccentColor(accentColorInternal)
    }

    private fun setupLetterKeys() {
        for (char in 'a'..'z') {
            val id = resources.getIdentifier("key_$char", "id", context.packageName)
            if (id == 0) continue
            findViewById<TextView>(id)?.apply {
                typeface = FontUtils.getUbuntu(context)
                setOnClickListener {
                    listener?.onFeedbackRequested()
                    appendChar(char)
                }
            }
        }
    }

    private fun appendChar(c: Char) {
        queryBuilder.append(c)
        queryText.text = queryBuilder.toString()
        performSearch()
    }

    private fun handleDelete() {
        if (queryBuilder.isNotEmpty()) {
            queryBuilder.deleteCharAt(queryBuilder.lastIndex)
            queryText.text = queryBuilder.toString()
            performSearch()
        }
    }

    private fun performSearch() {
        val query = queryBuilder.toString()
        if (query.isEmpty()) {
            resultsAdapter?.submitList(emptyList())
            resultsRecycler.visibility = GONE
        } else {
            val results = EmojiSearchEngine.search(query, maxResults = 20)
            resultsAdapter?.submitList(results)
            resultsRecycler.visibility = if (results.isNotEmpty()) VISIBLE else GONE
        }
    }

    private fun setupSwipeToClose() {
        val swipeListener = SwipeDownListener(context) { listener?.onCloseRequested() }
        gestureDetector = GestureDetector(context, swipeListener)

        setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            // Return false so child views still receive touch events
            false
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Forward to gesture detector so swipe fires even over child views
        gestureDetector.onTouchEvent(ev)
        return false
    }

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            listener?.onCloseRequested()
            return true
        }
        return super.onKeyPreIme(keyCode, event)
    }

    private inner class EmojiResultAdapter(
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<EmojiResultAdapter.ViewHolder>() {

        private var items: List<String> = emptyList()

        fun submitList(newList: List<String>) {
            items = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.emoji_search_item, parent, false) as EmojiTextView
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val emoji = items[position]
            holder.textView.text = emoji
            holder.textView.setOnClickListener { onClick(emoji) }
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(val textView: EmojiTextView) : RecyclerView.ViewHolder(textView)
    }
}
