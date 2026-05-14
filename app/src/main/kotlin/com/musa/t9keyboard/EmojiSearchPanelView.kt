package com.musa.t9keyboard

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.*
import android.widget.*
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.musa.t9keyboard.utils.FontUtils
import androidx.emoji2.widget.EmojiTextView

class EmojiSearchPanelView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    interface Listener {
        fun onEmojiSelected(emoji: String)
        fun onCloseRequested()
        fun onFeedbackRequested()
        fun onSearchTriggered()
    }

    var listener: Listener? = null
    private var accentColorInternal: Int = Color.parseColor("#E91E63")

    private lateinit var queryText: TextView
    private lateinit var underline: View
    private lateinit var resultsRecycler: RecyclerView
    private lateinit var deleteBtn: ImageButton
    private lateinit var topBar: LinearLayout

    private val queryBuilder = StringBuilder()
    private val gestureDetector: GestureDetector
    private var resultsAdapter: EmojiResultAdapter? = null

    init {
        orientation = VERTICAL
        gestureDetector = GestureDetector(context, SwipeDownListener())
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        bindViews()
        setupLetterKeys()
    }

    fun setAccentColor(color: Int) {
        accentColorInternal = color
        if (::underline.isInitialized) {
            underline.setBackgroundColor(color)
        }
        if (::deleteBtn.isInitialized) {
            val tint = android.content.res.ColorStateList.valueOf(color)
            ImageViewCompat.setImageTintList(deleteBtn, tint)
        }
    }

    fun resetQuery() {
        queryBuilder.setLength(0)
        queryText.text = ""
        updateResults(emptyList())
        resultsRecycler.visibility = GONE
    }

    private fun bindViews() {
        queryText = findViewById(R.id.emoji_search_query_text)
        underline = findViewById(R.id.emoji_search_underline)
        resultsRecycler = findViewById(R.id.emoji_search_results)
        deleteBtn = findViewById(R.id.emoji_search_delete)
        topBar = findViewById(R.id.emoji_search_top_bar)

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
        val alphabet = "abcdefghijklmnopqrstuvwxyz"
        for (char in alphabet) {
            val id = resources.getIdentifier("key_$char", "id", context.packageName)
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
            resultsRecycler.visibility = GONE
            updateResults(emptyList())
        } else {
            val results = EmojiSearchEngine.search(query, maxResults = 20)
            updateResults(results)
            resultsRecycler.visibility = if (results.isNotEmpty()) VISIBLE else GONE
        }
    }

    private fun updateResults(emojis: List<String>) {
        resultsAdapter?.submitList(emojis)
        resultsRecycler.scrollToPosition(0)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return true
    }

    inner class SwipeDownListener : GestureDetector.SimpleOnGestureListener() {
        private val SWIPE_THRESHOLD = 100
        private val SWIPE_VELOCITY_THRESHOLD = 100

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vX: Float, vY: Float): Boolean {
            val dy = (e2.y) - (e1?.y ?: 0f)
            if (dy > SWIPE_THRESHOLD && Math.abs(vY) > SWIPE_VELOCITY_THRESHOLD) {
                listener?.onCloseRequested()
                return true
            }
            return false
        }
    }

    private inner class EmojiResultAdapter(val onClick: (String) -> Unit) : RecyclerView.Adapter<EmojiResultAdapter.ViewHolder>() {
        private var items: List<String> = emptyList()

        fun submitList(newList: List<String>) {
            items = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.emoji_search_item, parent, false) as EmojiTextView
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val emoji = items[position]
            holder.textView.text = emoji
            holder.textView.setOnClickListener { onClick(emoji) }
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(val textView: EmojiTextView) : RecyclerView.ViewHolder(textView)
    }
}
