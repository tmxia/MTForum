package com.solosu.mtforum.ui.detail

import android.text.Spannable
import android.text.method.ArrowKeyMovementMethod
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.widget.TextView








class SelectableLinkMovementMethod : LinkMovementMethod() {
    private val arrow = ArrowKeyMovementMethod.getInstance()

    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        
        return if (event.actionMasked == MotionEvent.ACTION_DOWN && hasLinkAt(widget, buffer, event.x, event.y)) {
            super.onTouchEvent(widget, buffer, event)
        } else if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            
            arrow.onTouchEvent(widget, buffer, event)
        } else {
            
            if (hasLinkAt(widget, buffer, event.x, event.y)) {
                super.onTouchEvent(widget, buffer, event)
            } else {
                arrow.onTouchEvent(widget, buffer, event)
            }
        }
    }

    private fun hasLinkAt(widget: TextView, buffer: Spannable, x: Float, y: Float): Boolean {
        return try {
            val layout = widget.layout ?: return false
            val tx = (x - widget.totalPaddingLeft + widget.scrollX).toInt()
            val ty = (y - widget.totalPaddingTop + widget.scrollY).toInt()
            if (ty < 0 || ty > layout.height) return false
            val line = layout.getLineForVertical(ty)
            val offset = layout.getOffsetForHorizontal(line, tx.toFloat())
            if (offset < 0) return false
            buffer.getSpans(offset, offset, ClickableSpan::class.java).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }
}
