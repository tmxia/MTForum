package com.solosu.mtforum.util

import android.content.Context
import android.content.Intent
import android.os.SystemClock

import com.solosu.mtforum.model.Thread
import com.solosu.mtforum.ui.detail.ThreadDetailActivity





object NavigationHelper {

    private var lastClickTime: Long = 0

    


    @JvmStatic
    fun openThread(context: Context, thread: Thread?) {
        if (thread == null) return
        openThread(context, thread.tid, thread.title, thread.author, thread)
    }

    


    @JvmStatic
    fun openThread(context: Context, tid: String?) {
        openThread(context, tid, null, null, null)
    }

    private fun openThread(context: Context, tid: String?,
                           title: String?, author: String?, thread: Thread?) {
        if (context == null || tid == null || tid.isEmpty()) return

        
        val now = SystemClock.elapsedRealtime()
        if (now - lastClickTime < 500) return
        lastClickTime = now

        val intent = Intent(context, ThreadDetailActivity::class.java)
        intent.putExtra("tid", tid)
        if (title != null) intent.putExtra("title", title)
        if (author != null) intent.putExtra("author", author)
        if (thread != null) {
            val imgs = ArrayList<String>()
            if (thread.imageUrls.isNotEmpty()) {
                imgs.addAll(thread.imageUrls)
            } else if (!thread.thumbnailUrl.isNullOrEmpty()) {
                imgs.add(thread.thumbnailUrl!!)
            }
            if (imgs.isNotEmpty()) {
                intent.putStringArrayListExtra("extra_image_urls", imgs)
            }
        }
        context.startActivity(intent)
    }
}
