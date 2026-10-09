package com.solosu.mtforum.session

import android.content.Context
import android.text.TextUtils

import com.solosu.mtforum.network.ForumParser
import com.solosu.mtforum.network.HttpClient

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

import java.util.HashMap
import java.util.Locale





object DiscuzUserActionManager {

    @JvmStatic
    fun addFriend(context: Context, uid: String?, note: String?): Boolean {
        if (TextUtils.isEmpty(uid)) return false
        try {
            val client = prepareClient()
            val actionUrl = HttpClient.BASE_URL +
                    "home.php?mod=spacecp&ac=friend&op=add&uid=" + uid +
                    "&handlekey=friendhk&inajax=1"
            val page = client.get(actionUrl)
            val formhash = getFormhash(client, page, context)
            if (TextUtils.isEmpty(formhash)) return false

            val params = HashMap<String, String>()
            params["formhash"] = formhash!!
            params["addsubmit"] = "true"
            params["uid"] = uid!!
            params["note"] = if (TextUtils.isEmpty(note)) "来自手机端" else note!!
            val result = client.post(actionUrl, params)
            return accepted(
                result, "好友申请已发送", "好友添加成功", "已添加为好友",
                "已是好友", "申请成功", "请求已发送"
            )
        } catch (ignored: Exception) {
            return false
        }
    }

    @JvmStatic
    fun sendPoke(context: Context, uid: String?, message: String?): Boolean {
        if (TextUtils.isEmpty(uid)) return false
        try {
            val client = prepareClient()
            val actionUrl = HttpClient.BASE_URL +
                    "home.php?mod=spacecp&ac=poke&op=send&uid=" + uid +
                    "&handlekey=propoke&inajax=1"
            val page = client.get(actionUrl)
            val formhash = getFormhash(client, page, context)
            if (TextUtils.isEmpty(formhash)) return false

            val text = if (TextUtils.isEmpty(message)) "你好！" else message!!.trim()
            val params = HashMap<String, String>()
            params["formhash"] = formhash!!
            params["pokesubmit"] = "true"
            params["uid"] = uid!!
            params["message"] = text
            val result = client.post(actionUrl, params)
            return accepted(result, "打招呼成功", "已向", "招呼已发送", "发送成功")
        } catch (ignored: Exception) {
            return false
        }
    }

    @JvmStatic
    fun sendPrivateMessage(
        context: Context, uid: String?,
        subject: String?, message: String?
    ): Boolean {
        if (TextUtils.isEmpty(uid) || TextUtils.isEmpty(message)) return false
        try {
            val client = prepareClient()
            val chatUrl = HttpClient.BASE_URL +
                    "home.php?mod=space&do=pm&subop=view&touid=" + uid + "&mobile=2&_ts=" +
                    System.currentTimeMillis()
            val page = client.get(chatUrl)
            if (TextUtils.isEmpty(page) || ForumParser.isLoginPage(page)) return false

            val doc = Jsoup.parse(page, HttpClient.BASE_URL)
            val form = doc.select("form#pmform, form[name=pmform]").first() ?: return false

            var action = form.attr("abs:action")
            if (TextUtils.isEmpty(action)) action = form.attr("action")
            if (TextUtils.isEmpty(action)) {
                action = HttpClient.BASE_URL +
                        "home.php?mod=spacecp&ac=pm&op=send&mobile=2"
            }

            var formhash: String? = form.select("input[name=formhash]").attr("value")
            if (TextUtils.isEmpty(formhash)) formhash = ForumParser.parseFormhash(page)
            if (TextUtils.isEmpty(formhash)) return false

            var targetUid = form.select("input[name=touid]").attr("value")
            if (TextUtils.isEmpty(targetUid)) targetUid = uid!!

            val params = HashMap<String, String>()
            params["formhash"] = formhash!!
            params["touid"] = targetUid
            params["message"] = message!!.trim()
            
            params["pmsubmit"] = "yes"
            val result = client.post(action, params)

            
            if (isExplicitActionFailure(result)) return false
            if (containsAnyIgnoreCase(result, "发送成功", "消息已发送", "succeed", "success")) {
                return true
            }

            
            val verify = client.get(chatUrl + "&verify=" + System.currentTimeMillis())
            return containsMessage(verify, message.trim())
        } catch (ignored: Exception) {
            return false
        }
    }

    
    @JvmStatic
    fun deletePrivateMessage(
        context: Context, uid: String?, formhash: String?,
        pageDeleteUrl: String?
    ): Boolean {
        if (TextUtils.isEmpty(uid)) return false
        try {
            val client = prepareClient()
            var deleteUrl = pageDeleteUrl
            var fh = formhash
            if (TextUtils.isEmpty(deleteUrl)) {
                if (TextUtils.isEmpty(fh)) {
                    val page = client.get(
                        HttpClient.BASE_URL +
                                "home.php?mod=space&do=pm&mobile=2&_ts=" +
                                System.currentTimeMillis()
                    )
                    fh = ForumParser.parseFormhash(page)
                }
                deleteUrl = HttpClient.BASE_URL +
                        "home.php?mod=spacecp&ac=pm&op=delete" +
                        "&deletepm_deluid%5B%5D=" + uid +
                        "&uid=" + uid +
                        "&handlekey=pmdeletehk_0" +
                        (if (TextUtils.isEmpty(fh)) "" else "&formhash=" + fh) +
                        "&deletesubmit=1&mobile=2"
            }
            if (!deleteUrl!!.contains("inajax=")) {
                deleteUrl += (if (deleteUrl.contains("?")) "&" else "?") + "inajax=1"
            }

            
            client.get(deleteUrl)

            val verifyHtml = client.get(
                HttpClient.BASE_URL +
                        "home.php?mod=space&do=pm&mobile=2&_delete_verify=" +
                        System.currentTimeMillis()
            )
            if (ForumParser.isLoginPage(verifyHtml)) return false
            for (item in ForumParser.parsePmList(verifyHtml)) {
                if (uid == item.authorUid) return false
            }
            return true
        } catch (ignored: Exception) {
            return false
        }
    }

    
    @JvmStatic
    fun deletePrivateMessage(context: Context, uid: String?, formhash: String?): Boolean {
        return deletePrivateMessage(context, uid, formhash, null)
    }

    private fun containsMessage(html: String?, message: String?): Boolean {
        if (TextUtils.isEmpty(html) || TextUtils.isEmpty(message)) return false
        return Jsoup.parse(html!!).text().contains(message!!)
    }

    private fun containsAnyIgnoreCase(text: String?, vararg words: String): Boolean {
        if (TextUtils.isEmpty(text)) return false
        val lower = text!!.lowercase(Locale.ROOT)
        for (word in words) {
            if (word != null && lower.contains(word.lowercase(Locale.ROOT))) return true
        }
        return false
    }

    private fun isExplicitActionFailure(result: String?): Boolean {
        
        if (TextUtils.isEmpty(result)) return false
        if (ForumParser.isLoginPage(result)) return true
        return containsAnyIgnoreCase(
            result, "请先登录", "formhash错误", "没有权限",
            "非法操作", "操作失败", "发送失败", "删除失败", "ajaxerror"
        )
    }

    








    @JvmStatic
    fun blockUser(context: Context, uid: String?): Boolean {
        if (TextUtils.isEmpty(uid)) return false
        try {
            val client = prepareClient()
            val blacklistUrl = HttpClient.BASE_URL +
                    "home.php?mod=space&do=friend&view=blacklist"
            val blacklistPage = client.get(blacklistUrl)
            
            if (pageContainsUid(blacklistPage, uid)) {
                mirrorBlacklist(context, client, blacklistPage)
                return true
            }
            
            var formhash = ForumParser.parseFormhash(blacklistPage)
            if (TextUtils.isEmpty(formhash)) {
                formhash = getFormhash(client, blacklistPage, context)
            }
            if (TextUtils.isEmpty(formhash)) return false
            val username = fetchUsername(client, uid)
            android.util.Log.d(
                "MTForum_Block", "uid=" + uid +
                        " username=" + username + " formhash=" + formhash
            )
            if (TextUtils.isEmpty(username)) return false
            val params = HashMap<String, String>()
            params["username"] = username!!
            params["blacklistsubmit"] = "true"
            params["formhash"] = formhash!!
            val result = client.postWithReferer(
                HttpClient.BASE_URL +
                        "home.php?mod=spacecp&ac=friend&op=blacklist" +
                        "&start=&inajax=1",
                params, blacklistUrl
            )
            android.util.Log.d("MTForum_Block", "blacklist post=" + snippet(result))
            if (accepted(result, "操作成功", "已添加", "succeedhandle_")) {
                mirrorBlacklist(context, client)
                return true
            }
            
            if (inBlacklist(client, uid)) {
                mirrorBlacklist(context, client)
                return true
            }
            return false
        } catch (ignored: Exception) {
            return false
        }
    }

    



    @JvmStatic
    fun unblockUser(context: Context, uid: String?): Boolean {
        if (TextUtils.isEmpty(uid)) return false
        try {
            val client = prepareClient()
            val blacklistUrl = HttpClient.BASE_URL +
                    "home.php?mod=space&do=friend&view=blacklist"
            val before = client.get(blacklistUrl)
            
            if (!pageContainsUid(before, uid)) {
                mirrorBlacklist(context, client, before)
                return true
            }
            val result = client.get(
                HttpClient.BASE_URL +
                        "home.php?mod=spacecp&ac=friend&op=blacklist&subop=delete&uid=" +
                        uid + "&start="
            )
            android.util.Log.d("MTForum_Block", "blacklist delete=" + snippet(result))
            
            val after = client.get(blacklistUrl)
            val gone = !pageContainsUid(after, uid)
            if (gone) {
                
                mirrorBlacklist(context, client)
            }
            return gone
        } catch (ignored: Exception) {
            return false
        }
    }

    /**
     * 从目标用户空间页 <title> 提取用户名（黑名单按用户名提交）。
     * 实测 title 形如 "成哥的广播 - MT论坛" / "XXX的个人空间 - MT论坛";
     * 贪婪匹配取最后一个"的+版块名", 防止用户名本身含"的"被截断。
     */
    @Throws(Exception::class)
    private fun fetchUsername(client: HttpClient, uid: String?): String? {
        val page = client.get(
            HttpClient.BASE_URL +
                    "home.php?mod=space&uid=" + uid
        )
        if (TextUtils.isEmpty(page)) return null
        val m = java.util.regex.Pattern
            .compile("<title>(.{1,40})的(广播|个人空间|主页|空间|资料|日志|相册|分享)")
            .matcher(page)
        if (m.find()) return unescapeHtml(m.group(1).trim())
        return null
    }

    private fun unescapeHtml(s: String?): String? {
        if (s == null) return null
        val dq = 34.toChar().toString()
        val sq = 39.toChar().toString()
        return s.replace("&#34;", dq)
            .replace("&#39;", sq)
            .replace("&" + "quot;", dq)
            .replace("&" + "apos;", sq)
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
    }

    





    private fun mirrorBlacklist(context: Context, client: HttpClient) {
        try {
            mirrorBlacklist(
                context, client,
                client.get(
                    HttpClient.BASE_URL +
                            "home.php?mod=space&do=friend&view=blacklist"
                )
            )
        } catch (ignored: Exception) {
        }
    }

    private fun mirrorBlacklist(context: Context?, client: HttpClient, blacklistPage: String?) {
        if (context == null || TextUtils.isEmpty(blacklistPage)) return
        try {
            val entries = BlacklistSyncer.parseBlacklistHtml(blacklistPage)
            BlacklistManager.saveServerList(context, entries)
            android.util.Log.d(
                "MTForum_Block", "mirror saved, count=" +
                        entries.size
            )
        } catch (ignored: Exception) {
        }
    }

    
    private fun inBlacklist(client: HttpClient, uid: String?): Boolean {
        try {
            val listPage = client.get(
                HttpClient.BASE_URL +
                        "home.php?mod=space&do=friend&view=blacklist"
            )
            if (pageContainsUid(listPage, uid)) {
                android.util.Log.d("MTForum_Block", "inBlacklist hit")
                return true
            }
        } catch (ignored: Exception) {
        }
        return false
    }

    
    private fun snippet(s: String?): String {
        if (s == null) return "null"
        return if (s.length > 300) s.substring(0, 300) else s
    }

    
    private fun pageContainsUid(page: String?, uid: String?): Boolean {
        if (TextUtils.isEmpty(page) || TextUtils.isEmpty(uid)) return false
        return java.util.regex.Pattern
            .compile("uid=" + java.util.regex.Pattern.quote(uid) + "([^0-9]|$)")
            .matcher(page!!).find()
    }

    private fun prepareClient(): HttpClient {
        val client = HttpClient.getInstance()
        if (!client.isLoggedIn()) client.syncFromCookieManager()
        return client
    }

    @Throws(Exception::class)
    private fun getFormhash(client: HttpClient, page: String?, context: Context?): String? {
        var formhash = ForumParser.parseFormhash(page)
        if (!TextUtils.isEmpty(formhash)) return formhash

        val currentUid = UserSessionManager.getInstance().getUid(
            if (context != null) context.applicationContext else null!!
        )
        var profileUrl = HttpClient.BASE_URL + "home.php?mod=space&do=profile&mobile=2"
        if (!TextUtils.isEmpty(currentUid)) profileUrl += "&uid=" + currentUid
        formhash = ForumParser.parseFormhash(client.get(profileUrl))
        if (!TextUtils.isEmpty(formhash)) return formhash

        formhash = ForumParser.parseFormhash(client.get(HttpClient.BASE_URL + "forum.php?mobile=2"))
        if (!TextUtils.isEmpty(formhash)) return formhash
        return ForumParser.parseFormhash(client.getDesktop(HttpClient.BASE_URL + "forum.php"))
    }

    private fun accepted(result: String?, vararg successWords: String): Boolean {
        if (TextUtils.isEmpty(result) || ForumParser.isLoginPage(result)) return false
        if (containsFailure(result)) return false
        val lower = result!!.lowercase(Locale.ROOT)
        for (word in successWords) {
            if (lower.contains(word.lowercase(Locale.ROOT))) return true
        }
        
        return lower.contains("showmessage") && !lower.contains("error") &&
                (lower.contains("succeed") || lower.contains("success"))
    }

    private fun containsFailure(result: String?): Boolean {
        if (TextUtils.isEmpty(result)) return true
        val lower = result!!.lowercase(Locale.ROOT)
        return lower.contains("请先登录") || lower.contains("formhash错误")
                || lower.contains("没有权限") || lower.contains("非法操作")
                || lower.contains("操作失败") || lower.contains("发生错误")
                || lower.contains("失败") || lower.contains("error")
    }
}
