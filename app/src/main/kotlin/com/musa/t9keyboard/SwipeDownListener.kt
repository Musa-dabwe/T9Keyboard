package com.musa.t9keyboard

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

class SwipeDownListener(
    context: Context,
    private val onSwipeDown: () -> Unit
) : GestureDetector.SimpleOnGestureListener(), View.OnTouchListener {

    companion object {
        private const val SWIPE_THRESHOLD = 80        // px — minimum vertical travel
        private const val SWIPE_VELOCITY_THRESHOLD = 100  // px/s — minimum speed
    }

    private val gestureDetector = GestureDetector(context, this)

    override fun onDown(e: MotionEvent) = true

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        val e1 = e1 ?: return false
        val diffY = e2.y - e1.y
        val diffX = e2.x - e1.x
        // Must be more vertical than horizontal, downward, fast enough
        if (diffY > SWIPE_THRESHOLD &&
            Math.abs(diffY) > Math.abs(diffX) &&
            Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD
        ) {
            onSwipeDown()
            return true
        }
        return false
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val handled = gestureDetector.onTouchEvent(event)
        // Return false so taps on child views/nav bar still register normally
        return handled
    }
}
