package com.solosu.mtforum.session

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import org.json.JSONArray
import org.json.JSONObject





object BrowseHistoryManager {
    private const val PREF = "browse_history"
    private const val KEY_LIST = "items"
    private const val MAX = 100

    class Item {
        @JvmField var tid: String = ""
        @JvmField var title: String = ""
        @JvmField var author: String = ""
        @JvmField var forumName: String = ""
        @JvmField var viewTime: Long = 0L
    }

    @Volatile
    private var memCache: List<Item>? = null

    private fun prefs(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    @JvmStatic
    fun add(c: Context, tid: String?, title: String?, author: String?, forumName: String?) {
        memCache = null
        if (TextUtils.isEmpty(tid)) return
        try {
            val all = list(c).toMutableList()
            
            all.removeAll { it.tid == tid }
            
            val item = Item().apply {
                this.tid = tid!!
                this.title = title ?: ""
                this.author = author ?: ""
                this.forumName = forumName ?: ""
                this.viewTime = System.currentTimeMillis()
            }
            all.add(0, item)
            
            while (all.size > MAX) all.removeAt(all.size - 1)
            save(c, all)
        } catch (ignored: Exception) {
        }
    }

    @JvmStatic
    fun list(c: Context): List<Item> {
        memCache?.let { return it }
        val out = ArrayList<Item>()
        try {
            val json = prefs(c).getString(KEY_LIST, null) ?: return out
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val item = Item().apply {
                    tid = o.optString("tid", "")
                    title = o.optString("title", "")
                    author = o.optString("author", "")
                    forumName = o.optString("forumName", "")
                    viewTime = o.optLong("viewTime", 0L)
                }
                if (!TextUtils.isEmpty(item.tid)) out.add(item)
            }
        } catch (ignored: Exception) {
        }
        memCache = out
        return out
    }

    @JvmStatic
    fun remove(c: Context, tid: String?) {
        memCache = null
        if (TextUtils.isEmpty(tid)) return
        try {
            val all = list(c).toMutableList()
            all.removeAll { it.tid == tid }
            save(c, all)
        } catch (ignored: Exception) {
        }
    }

    @JvmStatic
    fun clear(c: Context) {
        memCache = null
        try {
            prefs(c).edit().remove(KEY_LIST).apply()
        } catch (ignored: Exception) {
        }
    }

    private fun save(c: Context, items: List<Item>) {
        try {
            val arr = JSONArray()
            for (it in items) {
                arr.put(JSONObject().apply {
                    put("tid", it.tid)
                    put("title", it.title)
                    put("author", it.author)
                    put("forumName", it.forumName)
                    put("viewTime", it.viewTime)
                })
            }
            prefs(c).edit().putString(KEY_LIST, arr.toString()).apply()
        } catch (ignored: Exception) {
        }
    }
}
