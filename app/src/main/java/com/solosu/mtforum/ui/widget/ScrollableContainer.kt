package com.solosu.mtforum.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout








class ScrollableContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var downY = 0f
    private var downX = 0f

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = ev.y
                downX = ev.x
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = Math.abs(ev.y - downY)
                val dx = Math.abs(ev.x - downX)
                
                if (dy > dx && dy > 10f) {
                    return false
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }
}
