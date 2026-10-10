package com.solosu.mtforum.session

import android.content.Context
import android.content.SharedPreferences

import org.json.JSONArray
import org.json.JSONObject

import java.util.ArrayList
import java.util.HashSet
import java.util.Set








object BlacklistManager {
    const val PREF = "sqapp_blacklist"
    private const val KEY_LOCAL = "local_list"      
    private const val KEY_SERVER = "server_list"    
    private const val KEY_SERVER_TS = "server_ts"   
    private const val SERVER_SYNC_INTERVAL = 7 * 24 * 3600 * 1000L 

    
    class Entry(uid: String?, user: String?, time: Long, source: String?) {
        @JvmField
        var uid: String = if (uid == null) "" else uid
        @JvmField
        var user: String = if (user == null) "" else user
        @JvmField
        var time: Long = time
        @JvmField
        var source: String = if (source == null) "local" else source
        
        @JvmField
        var syncState: String = "synced"
        @JvmField
        var retryCount: Int = 0
        @JvmField
        var lastRetryAt: Long = 0L

    }

    private fun prefs(c: Context): SharedPreferences {
        return c.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    }

    

    @JvmStatic
    fun getLocalList(c: Context): MutableList<Entry> {
        return parseEntries(prefs(c).getString(KEY_LOCAL, "[]"), "local")
    }

    @JvmStatic
    fun addLocal(c: Context, uid: String?, user: String?): Boolean {
        if (uid == null || uid.isEmpty()) return false
        val list = getLocalList(c)
        for (e in list) {
            if (uid == e.uid) return true 
        }
        list.add(Entry(uid, user, System.currentTimeMillis(), "local"))
        return prefs(c).edit().putString(KEY_LOCAL, toJSON(list)).commit()
    }

    @JvmStatic
    fun removeLocal(c: Context, uid: String?): Boolean {
        val list = getLocalList(c)
        var removed = false
        for (i in list.size - 1 downTo 0) {
            if (uid != null && uid == list[i].uid) {
                list.removeAt(i)
                removed = true
            }
        }
        if (removed) prefs(c).edit().putString(KEY_LOCAL, toJSON(list)).commit()
        return removed
    }

    @JvmStatic
    fun clearLocal(c: Context): Boolean {
        return prefs(c).edit().putString(KEY_LOCAL, "[]").commit()
    }

    

    
    @JvmStatic
    fun needsServerSync(c: Context): Boolean {
        val ts = prefs(c).getLong(KEY_SERVER_TS, 0)
        return ts == 0L || (System.currentTimeMillis() - ts) > SERVER_SYNC_INTERVAL
    }

    @JvmStatic
    fun getServerList(c: Context): MutableList<Entry> {
        val out = ArrayList<Entry>()
        val a = parseArray(prefs(c).getString(KEY_SERVER, "[]"))
        for (i in 0 until a.length()) {
            var uid: String?
            val user: String
            val o: Any? = a.opt(i)
            if (o is JSONObject) {
                uid = o.optString("uid", "")
                user = o.optString("user", "")
            } else {
                uid = a.optString(i, "")
                user = ""
            }
            if (uid != null && !uid.isEmpty()) out.add(Entry(uid, user, 0, "server"))
        }
        return out
    }

    
    @JvmStatic
    fun removeServer(c: Context, uid: String?): Boolean {
        val list = getServerList(c)
        var removed = false
        for (i in list.size - 1 downTo 0) {
            if (uid != null && uid == list[i].uid) {
                list.removeAt(i)
                removed = true
            }
        }
        if (removed) {
            val a = JSONArray()
            for (e in list) {
                try {
                    a.put(JSONObject().put("uid", e.uid).put("user", e.user))
                } catch (ignore: Exception) {
                }
            }
            prefs(c).edit().putString(KEY_SERVER, a.toString()).commit()
        }
        return removed
    }

    @JvmStatic
    fun saveServerList(c: Context, entries: MutableList<Entry>) {
        val a = JSONArray()
        for (e in entries) {
            if (e.uid.isEmpty()) continue
            try {
                a.put(JSONObject().put("uid", e.uid).put("user", e.user))
            } catch (ignore: Exception) {
            }
        }
        prefs(c).edit().putString(KEY_SERVER, a.toString()).putLong(KEY_SERVER_TS, System.currentTimeMillis()).commit()
    }

    

    
    @JvmStatic
    fun nameSet(c: Context): MutableSet<String> {
        val set = HashSet<String>()
        for (e in getLocalList(c)) if (e.user.isNotEmpty()) set.add(e.user)
        for (e in getServerList(c)) if (e.user.isNotEmpty()) set.add(e.user)
        return set
    }

    @JvmStatic
    fun uidSet(c: Context): MutableSet<String> {
        val set = HashSet<String>()
        for (e in getLocalList(c)) if (!e.uid.isEmpty()) set.add(e.uid)
        for (e in getServerList(c)) if (!e.uid.isEmpty()) set.add(e.uid)
        return set
    }

    @JvmStatic
    fun isBlack(c: Context, uid: String?): Boolean {
        if (uid == null || uid.isEmpty()) return false
        return uidSet(c).contains(uid)
    }

    @JvmStatic
    fun isEmpty(c: Context): Boolean {
        return getLocalList(c).isEmpty() && getServerList(c).isEmpty()
    }

    @JvmStatic
    fun size(c: Context): Int {
        return getLocalList(c).size + getServerList(c).size
    }

    @JvmStatic
    fun addPending(c: Context, uid: String?, user: String?): Boolean {
        if (uid.isNullOrEmpty()) return false
        val list = getLocalList(c)
        var found = false
        for (e in list) {
            if (uid == e.uid) {
                e.syncState = "pending"
                if (!user.isNullOrEmpty()) e.user = user
                found = true
                break
            }
        }
        if (!found) {
            val e = Entry(uid, user, System.currentTimeMillis(), "local")
            e.syncState = "pending"
            list.add(e)
        }
        return prefs(c).edit().putString(KEY_LOCAL, toJSON(list)).commit()
    }

    @JvmStatic
    fun markSynced(c: Context, uid: String?): Boolean {
        if (uid.isNullOrEmpty()) return false
        val list = getLocalList(c)
        for (e in list) {
            if (uid == e.uid) {
                e.syncState = "synced"
                e.retryCount = 0
                break
            }
        }
        return prefs(c).edit().putString(KEY_LOCAL, toJSON(list)).commit()
    }

    @JvmStatic
    fun markFailed(c: Context, uid: String?): Boolean {
        if (uid.isNullOrEmpty()) return false
        val list = getLocalList(c)
        for (e in list) {
            if (uid == e.uid) {
                e.syncState = "failed"
                break
            }
        }
        return prefs(c).edit().putString(KEY_LOCAL, toJSON(list)).commit()
    }

    @JvmStatic
    fun incrementRetry(c: Context, uid: String?): Boolean {
        if (uid.isNullOrEmpty()) return false
        val list = getLocalList(c)
        for (e in list) {
            if (uid == e.uid) {
                e.retryCount += 1
                e.lastRetryAt = System.currentTimeMillis()
                break
            }
        }
        return prefs(c).edit().putString(KEY_LOCAL, toJSON(list)).commit()
    }



    



    @JvmStatic
    fun resetAllPendingRetry(c: Context): Int {
        val list = getLocalList(c)
        var changed = 0
        for (e in list) {
            if (e.syncState == "pending" && e.retryCount > 0) {
                e.retryCount = 0
                changed++
            }
        }
        if (changed > 0) {
            prefs(c).edit().putString(KEY_LOCAL, toJSON(list)).commit()
        }
        return changed
    }


    @JvmStatic
    @JvmOverloads
    fun listPending(c: Context, limit: Int = 5): MutableList<Entry> {
        val out = ArrayList<Entry>()
        for (e in getLocalList(c)) {
            if (e.syncState == "pending" && e.retryCount < 3) {
                out.add(e)
                if (out.size >= limit) break
            }
        }
        return out
    }


    

    private fun toJSON(list: MutableList<Entry>): String {
        val a = JSONArray()
        for (e in list) {
            try {
                a.put(
                    JSONObject()
                        .put("uid", e.uid)
                        .put("user", e.user)
                        .put("time", e.time)
                        .put("syncState", e.syncState)
                        .put("retryCount", e.retryCount)
                        .put("lastRetryAt", e.lastRetryAt)
                )
            } catch (ignore: Exception) {
            }
        }
        return a.toString()

    }

    private fun parseArray(raw: String?): JSONArray {
        return try {
            JSONArray(if (raw == null) "[]" else raw)
        } catch (e: Exception) {
            JSONArray()
        }
    }

    private fun parseEntries(raw: String?, source: String): MutableList<Entry> {
        val out = ArrayList<Entry>()
        val a = parseArray(raw)
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val uid = o.optString("uid", "")
            val user = o.optString("user", "")
            val time = o.optLong("time", 0)
            
            val syncState = o.optString("syncState", "pending")
            val retryCount = o.optInt("retryCount", 0)
            val lastRetryAt = o.optLong("lastRetryAt", 0L)
            if (uid != null && !uid.isEmpty()) {
                val e = Entry(uid, user, time, source)
                e.syncState = syncState
                e.retryCount = retryCount
                e.lastRetryAt = lastRetryAt
                out.add(e)
            }
        }
        return out

    }
}
