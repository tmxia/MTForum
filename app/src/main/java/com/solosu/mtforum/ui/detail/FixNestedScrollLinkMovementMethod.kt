package com.solosu.mtforum.ui.detail

import android.text.method.LinkMovementMethod
import android.text.Spannable
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import java.util.regex.Matcher
import java.util.regex.Pattern








class FixNestedScrollLinkMovementMethod : LinkMovementMethod() {

    private var mDownX = 0f
    private var mDownY = 0f
    private var mDownTime = 0L
    private var mIsLongPress = false
    private val LONG_PRESS_THRESHOLD = 500L 

    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        val action = event.action

        if (action == MotionEvent.ACTION_DOWN) {
            mDownX = event.x
            mDownY = event.y
            mDownTime = System.currentTimeMillis()
            mIsLongPress = false

            
            if (hasLinkAtPosition(widget, buffer, mDownX, mDownY)) {
                widget.parent?.requestDisallowInterceptTouchEvent(true)
                findNestedScrollView(widget)?.requestDisallowInterceptTouchEvent(true)
            }
        }

        
        if (action == MotionEvent.ACTION_MOVE) {
            val pressDuration = System.currentTimeMillis() - mDownTime
            if (pressDuration > LONG_PRESS_THRESHOLD) {
                mIsLongPress = true
            }

            
            if (mIsLongPress) {
                val parent = findNestedScrollView(widget)
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(false)
                }
            } else {
                
                val parent = findNestedScrollView(widget)
                if (parent != null && !hasLinkAtPosition(widget, buffer, mDownX, mDownY)) {
                    parent.requestDisallowInterceptTouchEvent(false)
                }
            }
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            val pressDuration = System.currentTimeMillis() - mDownTime
            val parent = findNestedScrollView(widget)
            if (parent != null) {
                
                
                if (mIsLongPress || pressDuration > LONG_PRESS_THRESHOLD) {
                    parent.requestDisallowInterceptTouchEvent(false)
                } else {
                    parent.requestDisallowInterceptTouchEvent(!hasLinkAtPosition(widget, buffer, mDownX, mDownY))
                }
            }
        }

        return super.onTouchEvent(widget, buffer, event)
    }

    


    private fun hasLinkAtPosition(widget: TextView, buffer: Spannable, x: Float, y: Float): Boolean {
        try {
            val layout = widget.layout
            if (layout == null) return false

            val line = layout.getLineForVertical(y.toInt())
            val offset = layout.getOffsetForHorizontal(line, x)

            if (offset < 0) return false

            val spans = buffer.getSpans(offset, offset, ClickableSpan::class.java)
            return spans.size > 0
        } catch (e: Exception) {
            return false
        }
    }

    


    private fun findNestedScrollView(view: TextView): NestedScrollView? {
        var parent: android.view.ViewParent? = view.parent
        while (parent != null) {
            if (parent is NestedScrollView) {
                return parent
            }
            parent = parent.parent
        }
        return null
    }

    companion object {

        






        @JvmStatic
        @JvmOverloads
        fun matcherLinkify(
            spannable: Spannable?,
            pattern: Pattern?,
            urlProcessor: java.util.function.Function<String, String>?,
            onClickListener: java.util.function.Consumer<String>?,
            linkColor: Int = 0xFF2563EB.toInt()
        ) {
            if (spannable == null || pattern == null) return

            val text = spannable.toString()
            val matcher: Matcher = pattern.matcher(text)

            while (matcher.find()) {
                val start = matcher.start()
                val end = matcher.end()

                
                var alreadySpanned = false
                for (span in spannable.getSpans(start, end, ClickableSpan::class.java)) {
                    if (spannable.getSpanStart(span) >= start && spannable.getSpanEnd(span) <= end) {
                        alreadySpanned = true
                        break
                    }
                }
                if (alreadySpanned) continue

                val rawUrl = text.substring(start, end)
                val processedUrl: String = if (urlProcessor != null) urlProcessor.apply(rawUrl) else rawUrl

                spannable.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        if (onClickListener != null) {
                            onClickListener.accept(processedUrl)
                        }
                    }

                    override fun updateDrawState(ds: TextPaint) {
                        ds.color = linkColor
                        ds.isUnderlineText = true
                    }
                }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }
}
