package com.musa.t9keyboard

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.core.widget.ImageViewCompat
import com.musa.t9keyboard.TextEditingView.EditAction
import com.musa.t9keyboard.utils.FontUtils

class EditKeyHandler(
    private val view: TextEditingView,
    private val onAction: (EditAction) -> Unit,
    private val onFeedback: () -> Unit
) {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null
    private var deletionSpeed: Int = 100
    private val deleteRepeatInterval: Long
        get() = maxOf(30L, 200L - (deletionSpeed * 1.5).toLong())

    fun setupTouchListener(key: TextView, action: EditAction?, longAction: EditAction?, repeatInterval: Long?, onClick: (() -> Unit)?) {
        key.setOnTouchListener(object : View.OnTouchListener {
            private var longPressedSent = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        onFeedback()
                        v.isPressed = true
                        longPressedSent = false

                        if (action != null) {
                            if (action == EditAction.DELETE || repeatInterval != null) {
                                val interval = if (action == EditAction.DELETE) 100L else repeatInterval!!
                                onAction(action)
                                startRepeating(action, interval)
                            } else if (longAction != null) {
                                startLongPressCheck(longAction) { longPressedSent = true }
                            }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        v.isPressed = false
                        stopRepeating()
                        if (!longPressedSent) {
                            if (action != null) {
                                if (action != EditAction.DELETE && repeatInterval == null) {
                                    onAction(action)
                                }
                            } else if (onClick != null) {
                                onFeedback()
                                onClick.invoke()
                            }
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        stopRepeating()
                    }
                }
                return true
            }
        })
    }

    private fun startRepeating(action: EditAction, interval: Long) {
        stopRepeating()
        val effectiveInterval = if (action == EditAction.DELETE) deleteRepeatInterval else interval
        val initialDelay = if (action == EditAction.DELETE) 150L else 500L

        repeatRunnable = object : Runnable {
            override fun run() {
                onAction(action)
                handler.postDelayed(this, effectiveInterval)
            }
        }
        handler.postDelayed(repeatRunnable!!, initialDelay)
    }

    private fun startLongPressCheck(longAction: EditAction, onLongPressed: () -> Unit) {
        stopRepeating()
        repeatRunnable = Runnable {
            onLongPressed()
            onAction(longAction)
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        }
        handler.postDelayed(repeatRunnable!!, 500)
    }

    fun stopRepeating() {
        repeatRunnable?.let { handler.removeCallbacks(it) }
        repeatRunnable = null
    }

    fun setDeletionSpeed(speed: Int) {
        this.deletionSpeed = speed
    }
}
