package com.solosu.mtforum.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import kotlin.math.abs







class EdgeSwipeBackLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    
    var onBack: (() -> Unit)? = null

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val edgeWidth = resources.displayMetrics.widthPixels * 0.18f

    private var startX = 0f
    private var startY = 0f
    private var dragging = false
    private var velocityTracker: VelocityTracker? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.x
                startY = ev.y
                dragging = false
                animate().cancel()
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging && startX <= edgeWidth &&
                    ev.x - startX > slop && abs(ev.x - startX) > abs(ev.y - startY)
                ) {
                    dragging = true
                    obtainTracker().addMovement(ev)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!dragging) return super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                obtainTracker().addMovement(event)
                translationX = (event.x - startX).coerceAtLeast(0f)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (trackerVelocityX() > FLING_VELOCITY || translationX > width * 0.4f) {
                    onBack?.invoke()
                } else {
                    springBack()
                }
                releaseTracker()
                dragging = false
            }
        }
        return true
    }

    private fun springBack() {
        animate().cancel()
        animate()
            .translationX(0f)
            .setDuration(220L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun obtainTracker(): VelocityTracker =
        velocityTracker ?: VelocityTracker.obtain().also { velocityTracker = it }

    private fun trackerVelocityX(): Float {
        val tracker = velocityTracker ?: return 0f
        tracker.computeCurrentVelocity(1000)
        return tracker.xVelocity
    }

    private fun releaseTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    companion object {
        private const val FLING_VELOCITY = 400f
    }
}
