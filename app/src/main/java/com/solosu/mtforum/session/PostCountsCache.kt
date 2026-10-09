package com.solosu.mtforum.session

import android.text.TextUtils







object PostCountsCache {

    private val LIKES: MutableMap<String, Int> =
        java.util.concurrent.ConcurrentHashMap()

    
    @JvmStatic
    fun setLikes(tid: String?, likes: Int) {
        if (TextUtils.isEmpty(tid) || likes < 0) return
        val safe = tid!!
        LIKES[safe] = likes
    }

    
    @JvmStatic
    fun getLikes(tid: String?): Int? {
        if (TextUtils.isEmpty(tid)) return null
        val safe = tid!!
        return LIKES[safe]
    }
}
