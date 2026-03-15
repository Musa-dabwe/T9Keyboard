package com.musa.t9keyboard

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.musa.t9keyboard.databinding.SymbolsViewBinding
import com.musa.t9keyboard.utils.FontUtils

class SymbolsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: SymbolsViewBinding = SymbolsViewBinding.inflate(LayoutInflater.from(context), this, true)
    var onSymbolClickListener: ((String) -> Unit)? = null
    var onDeleteClickListener: (() -> Unit)? = null
    var onBackClickListener: (() -> Unit)? = null
    var onFeedbackRequested: (() -> Unit)? = null

    private val delHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var delRunnable: Runnable? = null
    private var deletionSpeed: Int = 100

    init {
        binding.symbolRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.symbolRecyclerView.adapter = SymbolAdapter { symbol ->
            onSymbolClickListener?.invoke(symbol)
        }
        binding.btnBackToAbc.setOnClickListener {
            onFeedbackRequested?.invoke()
            onBackClickListener?.invoke()
        }
        binding.btnSymbolDelete.setOnClickListener {
            onFeedbackRequested?.invoke()
            onDeleteClickListener?.invoke()
        }
        binding.btnSymbolDelete.setOnLongClickListener {
            onFeedbackRequested?.invoke()
            startRepeatingDel()
            true
        }
        binding.btnSymbolDelete.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP || event.action == android.view.MotionEvent.ACTION_CANCEL) {
                stopRepeatingDel()
            }
            false
        }
        applyUbuntuFont()
    }

    private fun startRepeatingDel() {
        stopRepeatingDel()
        delRunnable = object : Runnable {
            override fun run() {
                onDeleteClickListener?.invoke()
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

    private fun applyUbuntuFont() {
        val ubuntu = FontUtils.getUbuntu(context)
        binding.btnBackToAbc.typeface = ubuntu
    }

    fun setAccentColor(color: Int) {
        val pressedColor = (color and 0x00FFFFFF) or (0x66 shl 24)
        val ripple = android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(pressedColor),
            null,
            android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE)
        )
        binding.btnBackToAbc.background = ripple
        binding.btnSymbolDelete.background = ripple.constantState?.newDrawable()?.mutate()

        androidx.core.widget.ImageViewCompat.setImageTintList(binding.btnSymbolDeleteIcon, android.content.res.ColorStateList.valueOf(color))

        (binding.symbolRecyclerView.adapter as? SymbolAdapter)?.setAccentColor(color)
    }

    fun resetScroll() {
        binding.symbolRecyclerView.scrollToPosition(0)
    }
}
