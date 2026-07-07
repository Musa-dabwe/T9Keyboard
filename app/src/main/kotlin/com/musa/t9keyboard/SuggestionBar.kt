package com.musa.t9keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.musa.t9keyboard.databinding.SuggestionBarBinding
import com.musa.t9keyboard.utils.FontUtils

class SuggestionBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: SuggestionBarBinding = SuggestionBarBinding.inflate(LayoutInflater.from(context), this, true)

    var onSuggestionClickListener: ((String) -> Unit)? = null
    var onToolbarActionClickListener: ((ToolbarAction) -> Unit)? = null
    var onSwipeDownListener: (() -> Unit)? = null

    /** When true (number pad), a downward fling anywhere on the bar dismisses the panel. */
    var swipeDownEnabled: Boolean = false

    private val swipeDownDetector = SwipeDownListener(context) { onSwipeDownListener?.invoke() }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        // Observe the full event stream (even over child suggestion chips) without
        // stealing it, so taps keep working while flings dismiss the number pad.
        if (swipeDownEnabled) swipeDownDetector.onTouch(this, ev)
        val handled = super.dispatchTouchEvent(ev)
        return handled || swipeDownEnabled
    }

    private var accentColor: Int = Color.parseColor("#BB86FC")
    private var suggestionFontSize: Float = 13f
    private var isXt9Mode: Boolean = false
    private val ubuntuTypeface = FontUtils.getUbuntu(context)
    private val suggestionAdapter = SuggestionAdapter()
    private val handler = Handler(Looper.getMainLooper())
    private var revertRunnable: Runnable? = null

    enum class ToolbarAction {
        SETTINGS, EDIT, TOGGLE_XT9
    }

    init {
        setupToolbar()
        setupRecyclerView()
        applyUbuntuFont()
    }

    private fun setupRecyclerView() {
        binding.suggestionRecyclerView.layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        binding.suggestionRecyclerView.adapter = suggestionAdapter
    }

    private fun setupToolbar() {
        binding.toolbarSettings.setOnClickListener { onToolbarActionClickListener?.invoke(ToolbarAction.SETTINGS) }
        binding.toolbarEdit.setOnClickListener { onToolbarActionClickListener?.invoke(ToolbarAction.EDIT) }
        binding.toolbarXt9.setOnClickListener { onToolbarActionClickListener?.invoke(ToolbarAction.TOGGLE_XT9) }

        applyRipple(binding.toolbarSettings)
        applyRipple(binding.toolbarEdit)

        // Settings/Edit are plain navigation actions, not toggles - stay muted regardless
        // of the user's accent color (only xt9, an actual toggle, uses the accent when on).
        val muted = ColorStateList.valueOf(Color.parseColor("#8C90A0"))
        ImageViewCompat.setImageTintList(binding.toolbarSettings, muted)
        ImageViewCompat.setImageTintList(binding.toolbarEdit, muted)
    }

    private fun applyRipple(view: View) {
        val rippleColor = ColorStateList.valueOf((Color.WHITE and 0x00FFFFFF) or (0x33 shl 24))
        val ripple = RippleDrawable(rippleColor, null, GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        })
        view.background = ripple
    }

    private fun applyUbuntuFont() {
        val ubuntu = FontUtils.getUbuntu(context)
        binding.toolbarXt9.typeface = ubuntu
        binding.anchoredSuggestion.typeface = ubuntu
    }

    fun showToolbar() {
        // Edit button is now permanently visible
    }

    fun showSuggestions() {
        // Edit button is now permanently visible
    }

    fun setSuggestions(suggestions: List<String>, anchoredWord: String? = null) {
        revertRunnable?.let { handler.removeCallbacks(it) }
        revertRunnable = null

        suggestionAdapter.updateSuggestions(suggestions)

        if (anchoredWord != null) {
            binding.anchoredSuggestion.visibility = View.VISIBLE
            binding.anchoredSuggestion.text = anchoredWord
            binding.anchoredSuggestion.setBackgroundColor(accentColor)
            binding.anchoredSuggestion.setOnClickListener { onSuggestionClickListener?.invoke(anchoredWord) }
            binding.anchoredSuggestion.paintFlags = binding.anchoredSuggestion.paintFlags and android.graphics.Paint.UNDERLINE_TEXT_FLAG.inv()
        } else {
            binding.anchoredSuggestion.visibility = View.GONE
        }
    }

    fun showAutocorrectIndicator(correctedWord: String) {
        revertRunnable?.let { handler.removeCallbacks(it) }

        binding.anchoredSuggestion.visibility = View.VISIBLE
        binding.anchoredSuggestion.text = correctedWord
        val alphaAccent = (accentColor and 0x00FFFFFF) or (0xB3 shl 24) // ~70% opacity
        binding.anchoredSuggestion.setBackgroundColor(alphaAccent)
        binding.anchoredSuggestion.paintFlags = binding.anchoredSuggestion.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
        binding.anchoredSuggestion.setOnClickListener { onSuggestionClickListener?.invoke(correctedWord) }

        revertRunnable = Runnable {
            binding.anchoredSuggestion.visibility = View.GONE
            revertRunnable = null
        }
        handler.postDelayed(revertRunnable!!, 1500)
    }

    fun setXt9Mode(enabled: Boolean) {
        this.isXt9Mode = enabled
        updateXt9ButtonVisuals()
    }

    private fun updateXt9ButtonVisuals() {
        binding.toolbarXt9.setTextColor(if (isXt9Mode) accentColor else Color.WHITE)
        // Optionally add a subtle background when active
        if (isXt9Mode) {
             val bg = GradientDrawable().apply {
                 setColor((accentColor and 0x00FFFFFF) or (0x33 shl 24))
                 cornerRadius = dpToPx(4).toFloat()
             }
             binding.toolbarXt9.background = bg
        } else {
             binding.toolbarXt9.background = null
        }
    }

    fun setAccentColor(color: Int) {
        this.accentColor = color
        updateXt9ButtonVisuals()
        if (binding.anchoredSuggestion.visibility == View.VISIBLE) {
            binding.anchoredSuggestion.setBackgroundColor(color)
        }
    }

    fun setFontSize(sizeSp: Float) {
        this.suggestionFontSize = sizeSp
        binding.anchoredSuggestion.textSize = sizeSp
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density + 0.5f).toInt()

    private inner class SuggestionAdapter : RecyclerView.Adapter<SuggestionAdapter.ViewHolder>() {
        private var items = listOf<String>()

        fun updateSuggestions(newItems: List<String>) {
            val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize(): Int = items.size
                override fun getNewListSize(): Int = newItems.size
                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                    items[oldItemPosition] == newItems[newItemPosition]
                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                    items[oldItemPosition] == newItems[newItemPosition]
            })
            items = newItems
            diffResult.dispatchUpdatesTo(this)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val tv = TextView(context).apply {
                textSize = suggestionFontSize
                setTextColor(Color.WHITE)
                typeface = ubuntuTypeface
                gravity = Gravity.CENTER
                setPadding(dpToPx(12), 0, dpToPx(12), 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                isClickable = true
                isFocusable = true
                val outValue = android.util.TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
            }
            return ViewHolder(tv)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val suggestion = items[position]
            (holder.itemView as TextView).apply {
                text = suggestion
                setOnClickListener { onSuggestionClickListener?.invoke(suggestion) }
            }
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view)
    }
}
