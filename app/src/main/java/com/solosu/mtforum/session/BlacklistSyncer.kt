package com.solosu.mtforum.session

import android.content.Context

import com.solosu.mtforum.network.HttpClient

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

import java.util.ArrayList






object BlacklistSyncer {
    const val LIST_URL = "home.php?mod=space&do=friend&view=blacklist&mobile=2"

    
    interface Callback {
        fun onSynced(count: Int, error: String?)
    }

    
    @JvmStatic
    @JvmOverloads
    fun syncIfNeeded(c: Context, cb: Callback?, force: Boolean = false) {
        if (!force && !BlacklistManager.needsServerSync(c) && !hasBadNames(c)) {
            if (cb != null) cb.onSynced(BlacklistManager.getServerList(c).size, null)
            return
        }
        java.lang.Thread {
            var err: String? = null
            var entries: MutableList<BlacklistManager.Entry> = ArrayList()
            try {
                val html = HttpClient.getInstance().get("https://bbs.binmt.cc/" + LIST_URL)
                entries = parseBlacklistHtml(html)
                BlacklistManager.saveServerList(c, entries)
            } catch (e: Exception) {
                err = e.message ?: "网络错误"
            }
            val fe_ = entries
            val fe = err
            if (cb != null) {
                val h = android.os.Handler(android.os.Looper.getMainLooper())
                h.post { cb.onSynced(fe_.size, fe) }
            }
        }.start()
    }

    


    @JvmStatic
    fun parseBlacklistHtml(html: String?): MutableList<BlacklistManager.Entry> {
        val out = ArrayList<BlacklistManager.Entry>()
        if (html == null || html.isEmpty()) return out
        val doc: Document = Jsoup.parse(html)
        for (li in doc.select("li")) {
            val cls: String? = li.className()
            if (cls == null || !cls.contains("b_t")) continue
            var uid: String? = null
            var user = ""
            
            for (p in li.select("p")) {
                val pcls: String? = p.className()
                if (pcls == null || !pcls.contains("tit")) continue
                for (a in p.select("a")) {
                    val href = a.attr("href")
                    if (href.contains("do=profile")) {
                        val m = java.util.regex.Pattern.compile("uid=([0-9]+)").matcher(href)
                        if (m.find()) {
                            uid = m.group(1)
                            user = a.text().trim()
                            break
                        }
                    }
                }
                if (uid != null) break
            }
            
            if (uid == null) {
                for (a in li.select("a")) {
                    val href = a.attr("href")
                    if (href.contains("do=profile")) {
                        val m = java.util.regex.Pattern.compile("uid=([0-9]+)").matcher(href)
                        if (m.find()) {
                            uid = m.group(1)
                            user = a.text().trim()
                            break
                        }
                    }
                }
            }
            
            if (uid == null) {
                for (a in li.select("a")) {
                    val href = a.attr("href")
                    val m = java.util.regex.Pattern.compile("uid=([0-9]+)").matcher(href)
                    if (m.find()) {
                        uid = m.group(1)
                        break
                    }
                }
            }
            if (uid != null && !containsUid(out, uid)) out.add(BlacklistManager.Entry(uid, user, 0, "server"))
        }
        
        if (out.isEmpty()) {
            val p = java.util.regex.Pattern.compile("href=..[^>]*uid=([0-9]+)&amp;do=profile[^>]*>([^<]*)<")
            val m = p.matcher(html)
            while (m.find()) {
                val u = m.group(1)
                val n = m.group(2).trim()
                if (!containsUid(out, u)) out.add(BlacklistManager.Entry(u, n, 0, "server"))
            }
        }
        return out
    }

    
    @JvmStatic
    fun hasBadNames(c: Context): Boolean {
        val list = BlacklistManager.getServerList(c)
        for (e in list) {
            val n: String? = e.user
            if (n == null || n.trim().isEmpty()) return true
            val t = n.trim()
            if (t.contains("加好友") || t.contains("关注") || t.contains("打招呼")
                    || t.contains("发消息") || t.contains("解除黑名单") || t.contains("移出")) return true
        }
        return false
    }

    private fun containsUid(list: MutableList<BlacklistManager.Entry>, uid: String): Boolean {
        for (e in list) if (e.uid.equals(uid)) return true
        return false
    }

    
    
    

    




    private var syncLastRunAt = 0L
    private const val SYNC_MIN_INTERVAL = 5 * 60 * 1000L
    private const val SYNC_MAX_RETRY = 3
    private const val SYNC_BATCH_LIMIT = 5

    



    @JvmStatic
    fun syncPendingAsync(c: Context, force: Boolean, onProgress: (() -> Unit)? = null) {
        val now = System.currentTimeMillis()
        if (!force && now - syncLastRunAt < SYNC_MIN_INTERVAL) return
        syncLastRunAt = now
        java.lang.Thread {
            try {
                var round = 0
                while (round < 10) {
                    val pending = BlacklistManager.listPending(c, SYNC_BATCH_LIMIT)
                    if (pending.isEmpty()) break
                    var anySuccess = false
                    for (e in pending) {
                        if (e.retryCount >= SYNC_MAX_RETRY) {
                            BlacklistManager.markFailed(c, e.uid)
                            continue
                        }
                        val ok = addToServer(e.user)
                        if (ok) {
                            BlacklistManager.markSynced(c, e.uid)
                            anySuccess = true
                        } else {
                            BlacklistManager.incrementRetry(c, e.uid)
                        }
                        try { onProgress?.invoke() } catch (_: Exception) {}
                        try { Thread.sleep(1000) } catch (_: InterruptedException) {}
                    }
                    round++
                    if (!anySuccess) break
                }
                
                try {
                    val html = HttpClient.getInstance().get("https://bbs.binmt.cc/" + LIST_URL)
                    val entries = parseBlacklistHtml(html)
                    BlacklistManager.saveServerList(c, entries)
                } catch (_: Exception) {}
                try { onProgress?.invoke() } catch (_: Exception) {}
            } catch (ignored: Exception) {
            }
        }.start()
    }

    


    private fun fetchFormhashWithRetry(
        http: HttpClient,
        maxAttempts: Int,
    ): String? {
        val urls = listOf(
            "home.php?mod=space&do=friend&view=blacklist&mobile=2",
            "home.php?mod=spacecp&ac=friend&op=blacklist&mobile=2",
            "home.php?mod=spacecp",
        )
        for (attempt in 1..maxAttempts) {
            for (path in urls) {
                try {
                    val html = http.get(HttpClient.BASE_URL + path)
                    val fh = com.solosu.mtforum.network.ForumParser.parseFormhash(html)
                    if (!fh.isNullOrEmpty()) {
                        com.solosu.mtforum.util.AiLog.i("blacklist", "formhash 获取成功: " + fh)
                        return fh
                    }
                } catch (_: Exception) {
                }
            }
            if (attempt < maxAttempts) {
                try { Thread.sleep(2000) } catch (_: InterruptedException) {}
            }
        }
        return null
    }

    @JvmStatic
    fun addToServer(username: String?): Boolean {
        if (username.isNullOrEmpty()) return false
        return try {
            val http = HttpClient.getInstance()
            
            val formhash = fetchFormhashWithRetry(http, 3)
            if (formhash.isNullOrEmpty()) {
                com.solosu.mtforum.util.AiLog.e("blacklist", "formhash 获取失败（3 次重试后）")
                return false
            }
            val url = HttpClient.BASE_URL + "home.php?mod=spacecp&ac=friend&op=blacklist&start=&inajax=1"
            val params = HashMap<String, String>().apply {
                put("blacklistsubmit", "true")
                put("formhash", formhash)
                put("username", username)
            }
            val res = http.post(url, params)
            val preview = res?.take(300)?.replace("\n", " ") ?: "null"

            
            if (res != null && (
                    res.contains("成功") || res.contains("succeedhandle_")
                        || res.contains("已添加") || res.contains("操作成功")
                    )) {
                com.solosu.mtforum.util.AiLog.i("blacklist", "拉黑 $username → 成功")
                return true
            }

            
            if (res != null && (res.contains("已经屏蔽") || res.contains("已屏蔽"))) {
                com.solosu.mtforum.util.AiLog.i("blacklist", "拉黑 $username → 已屏蔽过，视为已处理")
                return true
            }

            
            if (res != null && res.contains("用户不存在") && res.contains("指定的用户")) {
                com.solosu.mtforum.util.AiLog.i("blacklist", "拉黑 $username → 用户已不存在，视为已处理")
                return true
            }

            
            com.solosu.mtforum.util.AiLog.i("blacklist", "拉黑 $username → 失败 | res=$preview")
            false
        } catch (e: Exception) {
            com.solosu.mtforum.util.AiLog.e("blacklist", "拉黑失败: " + e.message)
            false
        }
    }

    



    @JvmStatic
    fun removeFromServer(uid: String?): Boolean {
        if (uid.isNullOrEmpty()) return false
        return try {
            val http = HttpClient.getInstance()
            val url = HttpClient.BASE_URL +
                    "home.php?mod=spacecp&ac=friend&op=blacklist&subop=delete" +
                    "&uid=" + uid + "&mobile=2&inajax=1"
            val res = http.get(url)
            val ok = res != null && (res.contains("成功") || res.contains("succeed"))
            com.solosu.mtforum.util.AiLog.i("blacklist", "解除 $uid → $ok")
            ok
        } catch (e: Exception) {
            com.solosu.mtforum.util.AiLog.e("blacklist", "解除失败: " + e.message)
            false
        }
    }
}
