package com.solosu.mtforum.ui.detail

import com.solosu.mtforum.network.HttpClient

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

import java.util.ArrayList
import java.util.regex.Matcher
import java.util.regex.Pattern














object LikeUserFetcher {

    class Item(
        @JvmField val uid: String?,
        @JvmField val name: String?,
        @JvmField val avatar: String?
    )

    private const val BASE_DOMAIN = "https://bbs.binmt.cc/"
    private val UID_PATTERN = Pattern.compile("uid=(\\d+)")
    
    private val ICON_FONT_PATTERN = Pattern.compile("[\\uE000-\\uF8FF]")
    
    private val ZERO_WIDTH_PATTERN = Pattern.compile("[\\u200B-\\u200F\\uFEFF]")

    
    @JvmStatic
    fun fetch(tid: String?): List<Item> {
        val out: MutableList<Item> = ArrayList()
        if (tid == null || tid.isEmpty()) {
            return out
        }
        try {
            val url = BASE_DOMAIN + "misc.php?op=recommend&tid=" + tid + "&mod=faq&mobile=2"
            val html = HttpClient.getInstance().get(url)
            if (html == null || html.isEmpty()) {
                return out
            }
            val doc: Document = Jsoup.parse(html)
            val box: Element? = doc.selectFirst("div.comiis_userlist")
            if (box == null) {
                return out
            }
            val links: Elements = box.select("li a[href*=uid=]")
            for (a in links) {
                val m: Matcher = UID_PATTERN.matcher(a.attr("href"))
                if (!m.find()) {
                    continue
                }
                val uid = m.group(1)
                val img: Element? = a.selectFirst("img")
                var avatar = ""
                if (img != null) {
                    avatar = img.absUrl("src")
                    if (avatar.isEmpty()) {
                        avatar = img.attr("src")
                    }
                }
                if (!avatar.isEmpty() && avatar.startsWith("/") && !avatar.startsWith("//")) {
                    avatar = BASE_DOMAIN + avatar
                }
                var name = cleanName(a.text())
                if (name.isEmpty()) {
                    name = "用户" + uid
                }
                out.add(Item(uid, name, avatar))
            }
        } catch (e: Exception) {
            
        }
        return out
    }

    private fun cleanName(s: String?): String {
        if (s == null) {
            return ""
        }
        var cleaned = ICON_FONT_PATTERN.matcher(s).replaceAll("")
        cleaned = ZERO_WIDTH_PATTERN.matcher(cleaned).replaceAll("")
        return cleaned.trim()
    }
}
