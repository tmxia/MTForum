package com.solosu.mtforum.util

import android.content.Context
import org.json.JSONArray







object SearchHistory {

    private const val PREF_NAME = "mtforum_search_history"
    private const val KEY_LIST = "history"
    private const val MAX_SIZE = 20

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    
    fun getAll(ctx: Context): List<String> {
        val json = prefs(ctx).getString(KEY_LIST, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) list.add(arr.getString(i))
            list
        } catch (e: Exception) { emptyList() }
    }

    
    fun add(ctx: Context, keyword: String) {
        val kw = keyword.trim()
        if (kw.isEmpty()) return
        val current = getAll(ctx).toMutableList()
        current.removeAll { it.equals(kw, ignoreCase = true) }
        current.add(0, kw)
        while (current.size > MAX_SIZE) current.removeAt(current.size - 1)
        save(ctx, current)
    }

    
    fun remove(ctx: Context, keyword: String) {
        val current = getAll(ctx).toMutableList()
        current.removeAll { it == keyword }
        save(ctx, current)
    }

    
    fun clear(ctx: Context) {
        prefs(ctx).edit().remove(KEY_LIST).apply()
    }

    private fun save(ctx: Context, list: List<String>) {
        val arr = JSONArray()
        for (s in list) arr.put(s)
        prefs(ctx).edit().putString(KEY_LIST, arr.toString()).apply()
    }
}
