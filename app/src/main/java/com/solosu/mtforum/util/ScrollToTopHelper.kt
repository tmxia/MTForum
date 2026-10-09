package com.solosu.mtforum.util

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView




object ScrollToTopHelper {

    @JvmStatic
    fun attach(topBar: View?, onScrollToTop: () -> Unit) {
        if (topBar == null) return

        var lastClickTime = 0L
        val threshold = 400L

        
        val clickListener = View.OnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastClickTime <= threshold) {
                lastClickTime = 0L
                onScrollToTop()
            } else {
                lastClickTime = now
            }
        }
        topBar.setOnClickListener(clickListener)

        
        val gestureDetector = GestureDetector(topBar.context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                onScrollToTop()
                return true
            }
        })

        topBar.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    @JvmStatic
    fun attachRecyclerView(topBar: View?, recyclerView: RecyclerView?) {
        attach(topBar) {
            val lm = recyclerView?.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
            if (lm != null) {
                lm.scrollToPositionWithOffset(0, 0)
            } else {
                recyclerView?.scrollToPosition(0)
            }
        }
    }

    @JvmStatic
    fun attachNestedScrollView(topBar: View?, scrollView: NestedScrollView?) {
        attach(topBar) {
            scrollView?.scrollTo(0, 0)
        }
    }

    @JvmStatic
    fun attachScrollView(topBar: View?, scrollView: ScrollView?) {
        attach(topBar) {
            scrollView?.scrollTo(0, 0)
        }
    }
}
