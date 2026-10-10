package com.solosu.mtforum.session

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import com.solosu.mtforum.model.Thread
import org.json.JSONArray
import org.json.JSONObject






object FavoriteEnrichCache {
    private const val PREFS = "favorite_enrich_cache"
    private const val KEY_JSON = "items"
    private const val TTL_MS = 24 * 60 * 60 * 1000L
    private const val MAX_ITEMS = 200

    private val MEM: MutableMap<String, Thread> = java.util.concurrent.ConcurrentHashMap()
    private var loadedFromDisk = false
    private var lastSaveAt = 0L

    @JvmStatic
    fun get(c: Context?, tid: String?): Thread? {
        if (TextUtils.isEmpty(tid)) return null
        val key = tid!!
        MEM[key]?.let { return it }
        if (!loadedFromDisk) loadFromDisk(c)
        return MEM[key]
    }

    @JvmStatic
    fun put(c: Context?, thread: Thread?) {
        if (c == null || thread == null || TextUtils.isEmpty(thread.tid)) return
        if (!loadedFromDisk) loadFromDisk(c)
        MEM[thread.tid!!] = thread
        
        val now = System.currentTimeMillis()
        if (now - lastSaveAt > 10_000) {
            lastSaveAt = now
            saveToDisk(c)
        }
    }

    @JvmStatic
    fun clear(c: Context?) {
        MEM.clear()
        loadedFromDisk = false
        if (c != null) {
            try {
                c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().remove(KEY_JSON).apply()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun prefs(c: Context?): SharedPreferences? =
        c?.applicationContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun loadFromDisk(c: Context?) {
        loadedFromDisk = true
        try {
            val json = prefs(c)?.getString(KEY_JSON, null) ?: return
            val obj = JSONObject(json)
            val now = System.currentTimeMillis()
            val arr = obj.optJSONArray("items") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ts = o.optLong("_ts", 0L)
                if (now - ts > TTL_MS) continue
                val t = fromJson(o.optJSONObject("t") ?: continue)
                if (!TextUtils.isEmpty(t.tid)) MEM[t.tid!!] = t
            }
        } catch (ignored: Exception) {
        }
    }

    private fun saveToDisk(c: Context?) {
        try {
            val now = System.currentTimeMillis()
            val arr = JSONArray()
            var count = 0
            for (t in MEM.values) {
                if (count >= MAX_ITEMS) break
                arr.put(JSONObject().apply {
                    put("_ts", now)
                    put("t", toJson(t))
                })
                count++
            }
            val obj = JSONObject().apply { put("items", arr) }
            prefs(c)?.edit()?.putString(KEY_JSON, obj.toString())?.apply()
        } catch (ignored: Exception) {
        }
    }

    private fun toJson(t: Thread): JSONObject = JSONObject().apply {
        put("tid", t.tid ?: "")
        put("title", t.title ?: "")
        put("author", t.author ?: "")
        put("authorUid", t.authorUid ?: "")
        put("authorLevel", t.authorLevel ?: "")
        put("avatarUrl", t.avatarUrl ?: "")
        put("forumName", t.forumName ?: "")
        put("forumFid", t.forumFid ?: "")
        put("summary", t.summary ?: "")
        put("publishTime", t.publishTime ?: "")
        put("thumbnailUrl", t.thumbnailUrl ?: "")
        put("favid", t.favid ?: "")
        put("views", t.views)
        put("replies", t.replies)
        put("likes", t.likes)
        put("favorites", t.favorites)
        put("hasImage", t.hasImage)
        put("isSticky", t.isSticky)
        put("hasHiddenContent", t.hasHiddenContent)
        val imgs = JSONArray()
        for (u in t.imageUrls) imgs.put(u)
        put("imageUrls", imgs)
    }

    private fun fromJson(o: JSONObject): Thread = Thread().apply {
        tid = o.optString("tid", "")
        title = o.optString("title", "")
        author = o.optString("author", "")
        authorUid = o.optString("authorUid", "")
        authorLevel = o.optString("authorLevel", "")
        avatarUrl = o.optString("avatarUrl", "")
        forumName = o.optString("forumName", "")
        forumFid = o.optString("forumFid", "")
        summary = o.optString("summary", "")
        publishTime = o.optString("publishTime", "")
        thumbnailUrl = o.optString("thumbnailUrl", "").takeIf { it.isNotEmpty() }
        favid = o.optString("favid", "")
        views = o.optInt("views", 0)
        replies = o.optInt("replies", 0)
        likes = o.optInt("likes", 0)
        favorites = o.optInt("favorites", 0)
        hasImage = o.optBoolean("hasImage", false)
        isSticky = o.optBoolean("isSticky", false)
        hasHiddenContent = o.optBoolean("hasHiddenContent", false)
        val arr = o.optJSONArray("imageUrls")
        if (arr != null) {
            val list = ArrayList<String>()
            for (i in 0 until arr.length()) list.add(arr.getString(i))
            imageUrls = list
        }
    }
}
