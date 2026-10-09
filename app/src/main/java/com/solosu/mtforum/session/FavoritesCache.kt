package com.solosu.mtforum.session

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils







object FavoritesCache {
    private val MEM: MutableMap<String, Int> = java.util.concurrent.ConcurrentHashMap()
    private const val PREFS = "thread_favorites_cache"
    private const val PREFIX = "fav_"

    
    @JvmStatic
    fun get(c: Context?, tid: String?): Int? {
        if (TextUtils.isEmpty(tid)) return null
        val safe = tid!!
        val mem = MEM[safe]
        if (mem != null) return mem
        if (c == null) return null
        try {
            val sp: SharedPreferences = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val v = sp.getInt(PREFIX + safe, -1)
            if (v >= 0) {
                MEM[safe] = v
                return v
            }
        } catch (ignore: Exception) {
        }
        return null
    }

    
    @JvmStatic
    fun put(c: Context?, tid: String?, count: Int) {
        if (TextUtils.isEmpty(tid) || c == null || count < 0) return
        val safe = tid!!
        MEM[safe] = count
        try {
            val sp: SharedPreferences = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            sp.edit().putInt(PREFIX + safe, count).apply()
        } catch (ignore: Exception) {
        }
    }
}
