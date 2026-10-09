package com.solosu.mtforum.network

import android.text.TextUtils

import com.solosu.mtforum.model.ChatMessage
import com.solosu.mtforum.model.ForumCategory
import com.solosu.mtforum.model.PostDetail
import com.solosu.mtforum.model.ReplyItem
import com.solosu.mtforum.model.Thread
import com.solosu.mtforum.model.Message
import com.solosu.mtforum.model.UserProfile
import com.solosu.mtforum.model.Friend

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.Elements

import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet
import java.util.Locale
import java.util.regex.Pattern





object ForumParser {

    private const val BASE_DOMAIN = "https://bbs.binmt.cc/"

    @JvmStatic
    fun getBaseDomain(): String = BASE_DOMAIN

    


    @JvmStatic
    fun parseThreadList(html: String?): MutableList<Thread> {
        val threads = ArrayList<Thread>()
        val doc = Jsoup.parse(html ?: "")

        
        var items = doc.select("li.forumlist_li")
        if (items.isEmpty()) {
            
            val links = doc.select("a[href*=thread-]")
            for (link in links) {
                val parent = link.closest("li")
                if (parent != null) {
                    items.add(parent)
                }
            }
        }

        
        val tidPattern = Pattern.compile("thread-(\\d+)-1-1\\.html")
        val fidPattern = Pattern.compile("forum-(\\d+)-1\\.html")

        for (item in items) {
            try {
                val t = Thread()

                
                var titleLink = item.select(".mmlist_li_box h2 a[href*=thread-]").first()
                if (titleLink == null) {
                    titleLink = item.select("a[href*=thread-]").first()
                }
                if (titleLink == null) continue

                val href = titleLink.attr("href")
                val tidMatcher = tidPattern.matcher(href)
                if (!tidMatcher.find()) continue
                t.tid = tidMatcher.group(1)

                
                var title = titleLink.ownText().trim()
                if (title.isEmpty()) title = titleLink.text().trim()
                t.title = title

                
                val authorEl = item.select(".forumlist_li_top .top_user").first()
                if (authorEl != null) {
                    t.author = authorEl.text().trim()
                    
                    val authorHref = authorEl.attr("href")
                    val m = Pattern.compile("uid=(\\d+)").matcher(authorHref)
                    if (m.find()) t.authorUid = m.group(1)
                }

                
                val avatarEl = item.select(".forumlist_li_top .top_tximg").first()
                if (avatarEl != null) {
                    var src = avatarEl.attr("src")
                    if (!src.startsWith("http")) src = BASE_DOMAIN + src
                    t.avatarUrl = src
                }

                
                val levelEl = item.select(".forumlist_li_top .top_lev").first()
                if (levelEl != null) {
                    t.authorLevel = levelEl.text().trim()
                }

                
                val timeEl = item.select(".forumlist_li_time .f_d").first()
                if (timeEl != null) {
                    t.publishTime = timeEl.text().trim()
                }

                
                val forumLink = item.select(".comiis_xznalist_bk a[href*=forum-]").first()
                if (forumLink != null) {
                    
                    var forumName = forumLink.ownText().trim()
                    if (forumName.isEmpty()) forumName = forumLink.text().trim()
                    t.forumName = forumName

                    val forumHref = forumLink.attr("href")
                    val fidMatcher = fidPattern.matcher(forumHref)
                    if (fidMatcher.find()) t.forumFid = fidMatcher.group(1)
                }

                
                val summaryEl = item.select(".list_body .f_b").first()
                if (summaryEl != null) {
                    t.summary = summaryEl.text().trim()
                }

                
                val stats = item.select(".comiis_xznalist_bottom ul li .comiis_tm")
                
                if (stats.size >= 3) {
                    t.likes = parseIntFromText(stats.get(0).text())
                    t.replies = parseIntFromText(stats.get(1).text())
                    t.views = parseIntFromText(stats.get(2).text())
                } else if (stats.size >= 2) {
                    t.likes = parseIntFromText(stats.get(0).text())
                    t.replies = parseIntFromText(stats.get(1).text())
                }

                
                
                t.hasImage = item.select(
                    ".mmlist_li_box .comiis_pyqlist_imgs, .mmlist_li_box .comiis_pyqlist_img"
                ).size > 0

                
                val imgEl = item.select(
                    ".mmlist_li_box .comiis_pyqlist_imgs img, .mmlist_li_box .comiis_pyqlist_img img, .mmlist_li_box img"
                ).first()
                if (imgEl != null) {
                    val imgSrc = firstNonEmptyAttr(
                        imgEl, "comiis_loadimages", "data-original", "data-src",
                        "data-file", "file", "src"
                    )
                    if (!TextUtils.isEmpty(imgSrc)) {
                        val fullUrl = resolveAttachmentUrl(imgSrc)
                        if (isPostImageUrl(fullUrl)) {
                            t.thumbnailUrl = fullUrl
                        }
                    }
                }

                
                val bodyText = item.select(".list_body .f_b").first()
                if (bodyText != null && bodyText.text().contains("本内容被作者隐藏")) {
                    t.hasHiddenContent = true
                }

                populateThreadImages(item, t)
                t.isSticky = isStickyThread(item)

                threads.add(t)
            } catch (ignored: Exception) {
            }
        }

        return threads
    }

    



    @JvmStatic
    fun parseSearchResults(html: String?): MutableList<Thread> {
        val threads = ArrayList<Thread>()
        val doc = Jsoup.parse(html ?: "")

        
        var items = doc.select("li.forumlist_li")
        if (items.isEmpty()) {
            val links = doc.select("a[href*=thread-]")
            for (link in links) {
                val parent = link.closest("li")
                if (parent != null) {
                    items.add(parent)
                }
            }
        }

        val tidPattern = Pattern.compile("thread-(\\d+)-1-1\\.html")
        val fidPattern = Pattern.compile("forum-(\\d+)-1\\.html")

        for (item in items) {
            try {
                val t = Thread()

                
                var titleLink = item.select(".mmlist_li_box h2 a[href*=thread-]").first()
                if (titleLink == null) {
                    titleLink = item.select("a[href*=thread-]").first()
                }
                if (titleLink == null) continue

                val href = titleLink.attr("href")
                val tidMatcher = tidPattern.matcher(href)
                if (!tidMatcher.find()) continue
                t.tid = tidMatcher.group(1)

                
                val title = titleLink.text().trim()
                if (title.isEmpty()) continue
                t.title = title

                
                val authorEl = item.select(".forumlist_li_top .top_user").first()
                if (authorEl != null) {
                    t.author = authorEl.text().trim()
                    val authorHref = authorEl.attr("href")
                    val m = Pattern.compile("uid=(\\d+)").matcher(authorHref)
                    if (m.find()) t.authorUid = m.group(1)
                }

                
                val avatarEl = item.select(".forumlist_li_top .top_tximg").first()
                if (avatarEl != null) {
                    var src = avatarEl.attr("src")
                    if (!src.startsWith("http")) src = BASE_DOMAIN + src
                    t.avatarUrl = src
                }

                
                val levelEl = item.select(".forumlist_li_top .top_lev").first()
                if (levelEl != null) {
                    t.authorLevel = levelEl.text().trim()
                }

                
                val timeEl = item.select(".forumlist_li_time .f_d").first()
                if (timeEl != null) {
                    t.publishTime = timeEl.text().trim()
                }

                
                val forumLink = item.select(".comiis_xznalist_bk a[href*=forum-]").first()
                if (forumLink != null) {
                    var forumName = forumLink.ownText().trim()
                    if (forumName.isEmpty()) forumName = forumLink.text().trim()
                    t.forumName = forumName

                    val forumHref = forumLink.attr("href")
                    val fidMatcher = fidPattern.matcher(forumHref)
                    if (fidMatcher.find()) t.forumFid = fidMatcher.group(1)
                }

                
                val summaryEl = item.select(".list_body .f_b").first()
                if (summaryEl != null) {
                    val summaryText = summaryEl.text().trim()
                    t.summary = summaryText
                    if (summaryText.contains("本内容被作者隐藏")) {
                        t.hasHiddenContent = true
                    }
                }

                
                val stats = item.select(".comiis_xznalist_bottom ul li .comiis_tm")
                if (stats.size >= 3) {
                    t.likes = parseIntFromText(stats.get(0).text())
                    t.replies = parseIntFromText(stats.get(1).text())
                    t.views = parseIntFromText(stats.get(2).text())
                } else if (stats.size >= 2) {
                    t.likes = parseIntFromText(stats.get(0).text())
                    t.replies = parseIntFromText(stats.get(1).text())
                }

                
                t.hasImage = item.select(
                    ".mmlist_li_box .comiis_pyqlist_imgs, .mmlist_li_box .comiis_pyqlist_img"
                ).size > 0

                
                val imgEl = item.select(
                    ".mmlist_li_box .comiis_pyqlist_imgs img, .mmlist_li_box .comiis_pyqlist_img img, .mmlist_li_box img"
                ).first()
                if (imgEl != null) {
                    val imgSrc = firstNonEmptyAttr(
                        imgEl, "comiis_loadimages", "data-original", "data-src",
                        "data-file", "file", "src"
                    )
                    if (!TextUtils.isEmpty(imgSrc)) {
                        val fullUrl = resolveAttachmentUrl(imgSrc)
                        if (isPostImageUrl(fullUrl)) {
                            t.thumbnailUrl = fullUrl
                        }
                    }
                }

                populateThreadImages(item, t)
                threads.add(t)
            } catch (ignored: Exception) {
            }
        }

        return threads
    }

    



    @JvmStatic
    fun parseForumThreadList(html: String?): MutableList<Thread> {
        val threads = ArrayList<Thread>()
        val doc = Jsoup.parse(html ?: "")

        
        var items = doc.select("li.forumlist_li")
        if (items.isEmpty()) {
            val links = doc.select("a[href*=thread-]")
            for (link in links) {
                val parent = link.closest("li")
                if (parent != null) {
                    items.add(parent)
                }
            }
        }

        val tidPattern = Pattern.compile("thread-(\\d+)-1-1\\.html")
        val fidPattern = Pattern.compile("forum-(\\d+)-1\\.html")

        for (item in items) {
            try {
                val t = Thread()

                
                var titleLink = item.select(".mmlist_li_box h2 a[href*=thread-]").first()
                if (titleLink == null) {
                    titleLink = item.select("a[href*=thread-]").first()
                }
                if (titleLink == null) continue

                val href = titleLink.attr("href")
                val tidMatcher = tidPattern.matcher(href)
                if (!tidMatcher.find()) continue
                t.tid = tidMatcher.group(1)

                
                var title = titleLink.ownText().trim()
                if (title.isEmpty()) title = titleLink.text().trim()
                t.title = title

                
                val authorEl = item.select(".forumlist_li_top .top_user").first()
                if (authorEl != null) {
                    t.author = authorEl.text().trim()
                    val authorHref = authorEl.attr("href")
                    val m = Pattern.compile("uid=(\\d+)").matcher(authorHref)
                    if (m.find()) t.authorUid = m.group(1)
                }

                
                val avatarEl = item.select(".forumlist_li_top .top_tximg").first()
                if (avatarEl != null) {
                    var src = avatarEl.attr("src")
                    if (!src.startsWith("http")) src = BASE_DOMAIN + src
                    t.avatarUrl = src
                }

                
                val levelEl = item.select(".forumlist_li_top .top_lev").first()
                if (levelEl != null) {
                    t.authorLevel = levelEl.text().trim()
                }

                
                val timeEl = item.select(".forumlist_li_time .f_d").first()
                if (timeEl != null) {
                    t.publishTime = timeEl.text().trim()
                }

                
                val forumLink = item.select(".comiis_xznalist_bk a[href*=forum-]").first()
                if (forumLink != null) {
                    var forumName = forumLink.ownText().trim()
                    if (forumName.isEmpty()) forumName = forumLink.text().trim()
                    t.forumName = forumName

                    val forumHref = forumLink.attr("href")
                    val fidMatcher = fidPattern.matcher(forumHref)
                    if (fidMatcher.find()) t.forumFid = fidMatcher.group(1)
                }

                
                val summaryEl = item.select(".list_body .f_b").first()
                if (summaryEl != null) {
                    t.summary = summaryEl.text().trim()
                }

                
                val stats = item.select(".comiis_xznalist_bottom ul li .comiis_tm")
                if (stats.size >= 3) {
                    t.likes = parseIntFromText(stats.get(0).text())
                    t.replies = parseIntFromText(stats.get(1).text())
                    t.views = parseIntFromText(stats.get(2).text())
                } else if (stats.size >= 2) {
                    t.likes = parseIntFromText(stats.get(0).text())
                    t.replies = parseIntFromText(stats.get(1).text())
                }

                
                
                t.hasImage = item.select(
                    ".mmlist_li_box .comiis_pyqlist_imgs, .mmlist_li_box .comiis_pyqlist_img"
                ).size > 0

                
                val imgEl = item.select(
                    ".mmlist_li_box .comiis_pyqlist_imgs img, .mmlist_li_box .comiis_pyqlist_img img, .mmlist_li_box img"
                ).first()
                if (imgEl != null) {
                    val imgSrc = firstNonEmptyAttr(
                        imgEl, "comiis_loadimages", "data-original", "data-src",
                        "data-file", "file", "src"
                    )
                    if (!TextUtils.isEmpty(imgSrc)) {
                        val fullUrl = resolveAttachmentUrl(imgSrc)
                        if (isPostImageUrl(fullUrl)) {
                            t.thumbnailUrl = fullUrl
                        }
                    }
                }

                
                val bodyText = item.select(".list_body .f_b").first()
                if (bodyText != null && bodyText.text().contains("本内容被作者隐藏")) {
                    t.hasHiddenContent = true
                }

                populateThreadImages(item, t)

                
                t.isSticky = isStickyThread(item)

                threads.add(t)
            } catch (ignored: Exception) {
            }
        }

        return threads
    }

    private fun isStickyThread(item: Element?): Boolean {
        if (item == null) return false
        if (!item.select(
                "[title*=\"置顶\"], [class*=\"sticky\"], [class*=\"thread_sticky\"], [class*=\"threadtop\"]"
            ).isEmpty()
        ) {
            return true
        }
        for (marker in item.select("span, em, i, label")) {
            val text = marker.text().trim()
            if (text == "置顶" || text.contains("置顶")) return true
        }
        return false
    }

    



    @JvmStatic
    fun parseForumCategories(html: String?): MutableList<ForumCategory> {
        val categories = ArrayList<ForumCategory>()
        val doc = Jsoup.parse(html ?: "")

        
        val categoryBlocks = doc.select("div.comiis_forumlist")
        if (categoryBlocks.isEmpty()) {
            
            return parseForumCategoriesFromPostDialog(doc)
        }

        val fidPattern = Pattern.compile("forum-(\\d+)-1\\.html")

        for (block in categoryBlocks) {
            try {
                
                val titleEl = block.select("div.comiis_bbs_show h2 a").first() ?: continue
                val categoryName = titleEl.text().trim()
                if (categoryName.isEmpty()) continue

                
                val forums = ArrayList<ForumCategory.Forum>()
                val forumLinks = block.select("div.comiis_forum_nbox ul li a[href*=forum-]")
                for (link in forumLinks) {
                    val href = link.attr("href")
                    val m = fidPattern.matcher(href)
                    if (!m.find()) continue
                    val fid = m.group(1)

                    
                    val img = link.select("img[alt]").first()
                    val fName = if (img != null && !img.attr("alt").isEmpty())
                        img.attr("alt").trim()
                    else link.select("p").text().trim()
                    if (fName.isEmpty()) continue

                    val forum = ForumCategory.Forum(fid, fName)

                    
                    val iconImg = link.select("em img").first()
                    if (iconImg != null) {
                        var src = iconImg.attr("src")
                        if (!src.isEmpty()) {
                            if (!src.startsWith("http")) src = BASE_DOMAIN + src
                            forum.iconUrl = src
                        }
                    }

                    
                    val todaySpan = link.select("em span.bg_a.f_f").first()
                    if (todaySpan != null) {
                        val today = parseIntFromText(todaySpan.text())
                        forum.todayPosts = today
                        forum.totalThreads = if (today > 0) today else 0
                    }

                    forums.add(forum)
                }

                if (!forums.isEmpty()) {
                    categories.add(ForumCategory(categoryName, forums))
                }
            } catch (ignored: Exception) {
            }
        }

        return categories
    }

    




    private fun parseForumCategoriesFromPostDialog(doc: Document): MutableList<ForumCategory> {
        val categories = ArrayList<ForumCategory>()

        val groupLis = doc.select("div.comiis_bbslists_gid ul > li.comiis_fxpostlistkey")
        for (groupLi in groupLis) {
            try {
                val gid = groupLi.attr("fid")
                val categoryName = groupLi.select("a").text().trim()
                if (categoryName.isEmpty() || gid.isEmpty()) continue

                val forums = ArrayList<ForumCategory.Forum>()
                val subLis = doc.select("ul.comiis_fxpostlistbox_$gid > li")
                for (subLi in subLis) {
                    val link = subLi.select("a.bbslist_ico[href*=fid=]").first() ?: continue
                    val href = link.attr("href")
                    val fid = extractParam(href, "fid") ?: continue

                    
                    val img = link.select("img[alt]").first()
                    var fName = if (img != null) img.attr("alt").trim() else ""
                    if (fName.isEmpty()) {
                        val nameEl = subLi.select("a.post_tit em").first()
                        if (nameEl != null) fName = nameEl.text().trim()
                    }
                    if (fName.isEmpty()) continue

                    val forum = ForumCategory.Forum(fid, fName)

                    if (img != null) {
                        var src = img.attr("src")
                        if (!src.isEmpty()) {
                            if (!src.startsWith("http")) src = BASE_DOMAIN + src
                            forum.iconUrl = src
                        }
                    }

                    
                    val todaySpan = subLi.select("em span.bg_a.f_f").first()
                    if (todaySpan != null) {
                        val today = parseIntFromText(todaySpan.text())
                        forum.todayPosts = today
                        forum.totalThreads = if (today > 0) today else 0
                    }

                    forums.add(forum)
                }

                if (!forums.isEmpty()) {
                    categories.add(ForumCategory(categoryName, forums))
                }
            } catch (ignored: Exception) {
            }
        }

        return categories
    }

    



    @JvmStatic
    fun isLoginPage(html: String?): Boolean {
        if (TextUtils.isEmpty(html)) return true
        val doc = Jsoup.parse(html!!)

        
        
        val loginForm = doc.select(
            "form[method=post][action*=login], form[method=post][action*=logging]"
        ).first()
        if (loginForm != null) return true
        val pageTitle = doc.title().lowercase()
        if (pageTitle.contains("登录") || pageTitle.contains("login")) return true
        if (doc.select("input[type=password]").size > 0) return true

        

        
        val uidLinks = doc.select("a[href*=uid=]")
        if (uidLinks.size >= 2) {
            var hasUsernames = false
            for (link in uidLinks) {
                if (!TextUtils.isEmpty(link.text().trim())) {
                    hasUsernames = true
                    break
                }
            }
            if (hasUsernames) return false
        }

        
        val threadLinks = doc.select("a[href*=thread-]")
        if (threadLinks.size >= 2) {
            var hasTitles = false
            for (link in threadLinks) {
                if (!TextUtils.isEmpty(link.text().trim())) {
                    hasTitles = true
                    break
                }
            }
            if (hasTitles) return false
        }

        
        if (doc.select("li.forumlist_li").size > 0) return false

        
        if (doc.select(".comiis_space_tx, .comiis_space_profile, .comiis_space_profileico, .comiis_space_profilejf").size > 0) {
            return false
        }

        
        return false
    }

    




    @JvmStatic
    fun parseFollowState(html: String?): Int {
        if (TextUtils.isEmpty(html)) return -1
        val doc = Jsoup.parse(html!!)
        val buttons = doc.select(
            ".comiis_space_flw a#followmod, .comiis_space_flw a.followmod, " +
                    ".comiis_space_tx a#followmod, .comiis_space_tx a.followmod, " +
                    "a#followmod, a.followmod"
        )
        if (buttons.isEmpty()) return -1

        var sawAdd = false
        var sawBg0 = false
        for (button in buttons) {
            
            val href = button.attr("href").replace("&amp;", "&").lowercase()
            val text = button.text().trim()
            val classes = button.className().lowercase()

            
            if (href.contains("op=del") || text.contains("已关注")
                || text.contains("取消关注") || classes.contains("bg_b")
            ) {
                return 1
            }
            
            if (href.contains("op=add") || href.contains("logging&action=login")
                || href.contains("logging%26action%3dlogin")
            ) {
                sawAdd = true
            }
            
            if (classes.contains("bg_0")) sawBg0 = true
        }
        if (sawAdd || sawBg0) return 0
        return -1
    }

    
    @JvmStatic
    fun isFollowingListPage(html: String?): Boolean {
        if (TextUtils.isEmpty(html)) return false
        val doc = Jsoup.parse(html!!)
        if (!doc.select(".comiis_userlist01, .comiis_friend_boxs, .comiis_follow_box, .comiis_followlist").isEmpty()) {
            return true
        }
        
        val bodyText = if (doc.body() == null) "" else doc.body().text()
        val hasFollowTitle = bodyText.contains("我的关注") || bodyText.contains("关注列表")
                || bodyText.contains("正在关注") || bodyText.contains("关注的人")
        val hasUserItem = !doc.select("li a[href*=uid=], a[href*=home.php?mod=space&uid=]").isEmpty()
        val hasEmptyMarker = bodyText.contains("暂无关注") || bodyText.contains("还没有关注")
                || bodyText.contains("没有关注")
        return hasFollowTitle && (hasUserItem || hasEmptyMarker)
    }

    



    @JvmStatic
    fun parseUserProfile(html: String?): UserProfile {
        val profile = UserProfile()
        val doc = Jsoup.parse(html ?: "")

        
        
        

        
        val usernameEl = doc.select(".comiis_space_tx h2, .username, .user_name, h1, .profile_name").first()
        if (usernameEl != null) profile.username = usernameEl.text().trim()

        
        var avatarEl = doc.select(".comiis_space_tx .user_img img, .comiis_space_tx img[src*=avatar], .comiis_space_tx img").first()
        
        if (avatarEl == null) {
            avatarEl = doc.select("img[src*=avatar]").first()
        }
        if (avatarEl != null) {
            var src = avatarEl.attr("src")
            if (!src.startsWith("http")) src = BASE_DOMAIN + src
            profile.avatarUrl = src
        } else {
            
            if (profile.uid != null && !profile.uid!!.isEmpty()) {
                profile.avatarUrl = BASE_DOMAIN + "uc_server/avatar.php?uid=" + profile.uid + "&size=middle"
            }
        }

        
        val uidEl = doc.select(".comiis_space_profile li:contains(用户ID) .profile_rs, .uid, em:contains(UID)").first()
        if (uidEl != null) {
            val text = uidEl.text().trim()
            val m = Pattern.compile("(\\d+)").matcher(text)
            if (m.find()) profile.uid = m.group(1)
        }

        
        
        val levelEl = doc.select(".comiis_space_tx .kmlevs.kmlv, .comiis_space_tx .kmlv, .level, .lv, em:contains(Lv)").first()
        if (levelEl != null) profile.level = levelEl.text().trim()

        
        val groupEl = doc.select(".comiis_space_tx .kmlev").first()
        if (groupEl != null) profile.groupName = groupEl.text().trim()

        
        
        
        val followState = parseFollowState(html)
        if (followState >= 0) {
            profile.followed = followState == 1
            profile.followStateKnown = true
        }

        
        
        

        
        val threadEl = doc.select(".comiis_space_profileico li:contains(帖子) span, em:contains(帖子) + span, .thread_count").first()
        if (threadEl != null) profile.threads = parseIntFromText(threadEl.text())

        
        val postsEl = doc.select(".comiis_space_profileico li:contains(回复) span, em:contains(回复) + span, .posts, .user_posts").first()
        if (postsEl != null) profile.posts = parseIntFromText(postsEl.text())

        
        val friendEl = doc.select(".comiis_space_profileico li:contains(好友) span, em:contains(好友) + span, .friend_count").first()
        if (friendEl != null) profile.friends = parseIntFromText(friendEl.text())

        
        var followingEl: Element? = null
        for (span in doc.select(".comiis_space_tx p span")) {
            val text = span.text().trim()
            if (text.matches(Regex(".*\\d[\\d,，]*\\s*关注$"))) {
                followingEl = span
                break
            }
        }
        if (followingEl == null) {
            followingEl = doc.select(".comiis_space_profileico li:contains(关注) span, em:contains(关注) + span, .following_count").first()
        }
        if (followingEl != null) {
            profile.following = parseIntFromText(followingEl.text())
        }

        
        val followerEl = doc.select(".comiis_space_profileico li:contains(粉丝) span, em:contains(粉丝) + span, .follower_count").first()
        if (followerEl != null) profile.followers = parseIntFromText(followerEl.text())

        
        val viewsEl = doc.select(".comiis_space_profileico li:contains(人气) span").first()
        if (viewsEl != null) profile.views = parseIntFromText(viewsEl.text())

        
        
        
        val profileJfLis = doc.select(".comiis_space_profilejf ul li")

        
        if (profileJfLis.size >= 1) {
            var creditsEl = profileJfLis.get(0).select(".f_0, span").first()
            if (creditsEl == null) creditsEl = profileJfLis.get(0)
            profile.credits = parseIntFromText(creditsEl.text())
        }

        
        if (profileJfLis.size >= 3) {
            var goldEl = profileJfLis.get(2).select(".f_0, span").first()
            if (goldEl == null) goldEl = profileJfLis.get(2)
            profile.gold = parseIntFromText(goldEl.text())
        }

        
        
        

        
        val regLi = doc.select(".comiis_space_profile li:contains(注册时间)").first()
        if (regLi != null) {
            val regVal = regLi.select(".profile_rs").first()
            if (regVal != null) profile.regDate = regVal.text().trim()
        } else {
            val regEl = doc.select("li:contains(注册时间), .regdate").first()
            if (regEl != null) profile.regDate = regEl.text().replace("注册时间:", "").trim()
        }

        
        val lastVisitLi = doc.select(".comiis_space_profile li:contains(最后访问)").first()
        if (lastVisitLi != null) {
            val lastVisitVal = lastVisitLi.select(".profile_rs").first()
            if (lastVisitVal != null) profile.lastVisit = lastVisitVal.text().trim()
        }

        
        val onlineLi = doc.select(".comiis_space_profile li:contains(在线时间)").first()
        if (onlineLi != null) {
            val onlineVal = onlineLi.select(".profile_rs").first()
            if (onlineVal != null) profile.onlineTime = onlineVal.text().trim()
        }

        
        val genderLi = doc.select(".comiis_space_profile li:contains(性别)").first()
        if (genderLi != null) {
            val genderVal = genderLi.select(".profile_rs").first()
            if (genderVal != null) {
                val g = genderVal.text().trim()
                if (g.contains("男")) profile.gender = "boy"
                else if (g.contains("女")) profile.gender = "girl"
            }
        }

        
        
        
        val sigEl = doc.select(".profile_face, .signature, .sigin").first()
        if (sigEl != null) profile.signature = sigEl.text().trim()

        return profile
    }

    


    @JvmStatic
    fun parseMessageList(html: String?): MutableList<Message> {
        val messages = ArrayList<Message>()
        val doc = Jsoup.parse(html ?: "")

        val items = doc.select("li.pm, li.msg, div.message_item, li[data-pmid]")
        for (item in items) {
            try {
                val msg = Message()

                val link = item.select("a[href*=pmid=], a[href*=do=pm]").first()
                if (link != null) {
                    val pmid = extractParam(link.attr("href"), "pmid")
                    if (pmid != null) msg.pmid = pmid
                }

                val titleEl = item.select(".title, .subject, h4").first()
                if (titleEl != null) msg.title = titleEl.text().trim()

                val authorEl = item.select(".author, .by, .from").first()
                if (authorEl != null) msg.author = authorEl.text().trim()

                val timeEl = item.select(".time, .date, .dateline").first()
                if (timeEl != null) msg.time = timeEl.text().trim()

                val summaryEl = item.select(".summary, .message_preview, p").first()
                if (summaryEl != null) msg.summary = summaryEl.text().trim()

                val avatarEl = item.select("img[src*=avatar]").first()
                if (avatarEl != null) msg.avatarUrl = avatarEl.attr("src")

                msg.isRead = item.select("em:contains(已读), .read").size > 0

                messages.add(msg)
            } catch (ignored: Exception) {
            }
        }

        return messages
    }

    





    private fun parseComiisNoticeItems(items: Elements): MutableList<Message> {
        val notices = ArrayList<Message>()
        for (item in items) {
            try {
                val body = item.select("div.ntc_body").first() ?: continue

                val notice = Message()
                notice.type = 0

                
                
                val unread = item.hasClass("ntc_l") || item.hasClass("new")
                        || item.hasClass("unread") || item.hasClass("un_read")
                        || "0" == item.attr("data-read")
                        || "unread".equals(item.attr("data-status"), ignoreCase = true)
                var read = !unread
                if (item.hasClass("read") || item.hasClass("old")
                    || "1" == item.attr("data-read")
                ) read = true
                notice.isRead = read

                
                val ignore = item.select("h2 a[id^=a_note_]").first()
                if (ignore != null) {
                    val idMatcher = Pattern.compile("a_note_(\\d+)").matcher(ignore.id())
                    if (idMatcher.find()) notice.pmid = idMatcher.group(1)
                }

                val author = body.select("a[href*=space-uid-], a[href*=uid=]").first()
                if (author != null) {
                    notice.author = author.text().trim()
                    val href = author.attr("href")
                    val uidMatcher = Pattern.compile("space-uid-(\\d+)").matcher(href)
                    if (uidMatcher.find()) notice.authorUid = uidMatcher.group(1)
                    else {
                        val uid = extractParam(href, "uid")
                        if (!TextUtils.isEmpty(uid)) notice.authorUid = uid
                    }
                }

                val avatar = item.select("a.notice_img img[src*=avatar], img[src*=avatar], img[src*=uc_server]").first()
                if (avatar != null) {
                    var src = avatar.attr("abs:src")
                    if (TextUtils.isEmpty(src)) src = avatar.attr("src")
                    if (!TextUtils.isEmpty(src) && !src.startsWith("http")) src = BASE_DOMAIN + src
                    notice.avatarUrl = src
                }

                var titleLink = body.select("a[href*=forum.php?mod=redirect]").first()
                if (titleLink == null) titleLink = body.select("a[href*=thread-]").first()
                if (titleLink != null) notice.summary = titleLink.text().trim()

                var fullText = body.text().replace('\u00a0', ' ').trim()
                if (fullText.length > 200) fullText = fullText.substring(0, 200)
                notice.title = fullText

                if (titleLink != null) {
                    val tidMatcher = Pattern.compile("(?:ptid=|thread-)(\\d+)").matcher(titleLink.attr("href"))
                    if (tidMatcher.find()) {
                        val summary = notice.summary
                        notice.summary = (if (summary == null) "" else summary) + " tid=" + tidMatcher.group(1)
                    }
                }

                val time = item.select("h2.f_d").first()
                if (time != null) {
                    var timeText = time.ownText().replace('\u00a0', ' ').trim()
                    if (TextUtils.isEmpty(timeText)) timeText = time.text().replace('\u00a0', ' ').trim()
                    notice.time = timeText
                }

                if (fullText.contains("回复了您的") || fullText.contains("评论了您的")) {
                    notice.type = 2
                } else if (fullText.contains("系统") || fullText.contains("管理")
                    || fullText.contains("删除")
                ) {
                    notice.type = 1
                }
                notices.add(notice)
            } catch (ignored: Exception) {
            }
        }
        return notices
    }

    


    @JvmStatic
    fun parseNoticeList(html: String?): MutableList<Message> {
        val notices = ArrayList<Message>()
        if (TextUtils.isEmpty(html)) return notices

        val doc = Jsoup.parse(html!!)

        
        val mobileItems = doc.select("li.b_b.bg_f.cl")
        if (!mobileItems.isEmpty()) {
            return parseComiisNoticeItems(mobileItems)
        }

        
        if (html.contains("没有提醒内容") || html.contains("暂无提醒")
            || html.contains("没有新的") || html.contains("notip")
        ) {
            return notices
        }

        
        var items = doc.select("div.nts > dl.cl")
        if (items.isEmpty()) {
            
            items = doc.select("dl.cl[notice]")
        }
        if (items.isEmpty()) {
            
            items = doc.select("dl.cl")
        }

        for (item in items) {
            try {
                val notice = Message()
                notice.type = 0

                
                notice.isRead = !item.hasClass("ntc_l")

                
                val noticeId = item.attr("notice")
                if (!TextUtils.isEmpty(noticeId)) {
                    notice.pmid = noticeId
                }

                
                var avatarLink = item.select("dd.avt a[href*=space-uid-]").first()
                if (avatarLink == null) {
                    avatarLink = item.select("dd.m a[href*=uid=]").first()
                }
                if (avatarLink != null) {
                    val m = Pattern.compile("uid=(\\d+)").matcher(avatarLink.attr("href"))
                    if (m.find()) {
                        notice.authorUid = m.group(1)
                    }
                }

                
                val avatarImg = item.select("dd.avt img[src*=avatar], img[src*=uc_server]").first()
                if (avatarImg != null) {
                    var src = avatarImg.attr("src")
                    if (!src.startsWith("http")) src = BASE_DOMAIN + src
                    notice.avatarUrl = src
                }

                
                var timeEl = item.select("dt span.xg1 span[title], dt span.xg1").first()
                if (timeEl == null) {
                    timeEl = item.select("span[title]").first()
                }
                if (timeEl != null) {
                    var timeText = timeEl.attr("title")
                    if (TextUtils.isEmpty(timeText)) {
                        timeText = timeEl.text().trim()
                    }
                    notice.time = timeText
                }

                
                val bodyEl = item.select("dd.ntc_body").first() ?: continue

                
                val authorEl = bodyEl.select("a[href*=space-uid-], a[href*=uid=]").first()
                if (authorEl != null) {
                    notice.author = authorEl.text().trim()
                }

                
                val titleLink = bodyEl.select("a[href*=forum.php?mod=redirect]").first()
                if (titleLink != null) {
                    val title = titleLink.text().trim()
                    
                    notice.summary = title
                }

                // ★ 完整通知文本作为标题（如 "ruolin 回复了您的帖子 XXX"）
                var fullText = bodyEl.text().trim()
                
                fullText = Regex("查看\\s*$").replace(fullText, "").trim()
                if (fullText.length > 200) fullText = fullText.substring(0, 200)
                notice.title = fullText

                
                val postLink = bodyEl.select("a[href*=ptid=]").first()
                if (postLink != null) {
                    val href = postLink.attr("href")
                    val m = Pattern.compile("ptid=(\\d+)").matcher(href)
                    if (m.find()) {
                        notice.summary = (if (notice.summary != null) notice.summary + " " else "") + "tid=" + m.group(1)
                    }
                }

                
                if (fullText.contains("回复了您的帖子") || fullText.contains("回复了您的") || fullText.contains("评论了您的")) {
                    notice.type = 2 
                } else if (fullText.contains("删除") || fullText.contains("系统") || fullText.contains("管理")) {
                    notice.type = 1 
                }

                notices.add(notice)
            } catch (ignored: Exception) {
            }
        }

        return notices
    }

    


    @JvmStatic
    fun parsePmList(html: String?): MutableList<Message> {
        val messages = ArrayList<Message>()
        if (TextUtils.isEmpty(html)) return messages

        val doc = Jsoup.parse(html!!)

        
        var items = doc.select("div.comiis_pmlist ul > li")
        
        if (items.isEmpty()) {
            items = doc.select("li.b_b, li.pm, li[data-pmid]")
        }
        
        if (items.isEmpty()) {
            val pmLinks = doc.select("a[href*=subop=view]")
            for (pmLink in pmLinks) {
                val parent = pmLink.closest("li")
                if (parent != null) items.add(parent)
            }
        }

        for (item in items) {
            try {
                val msg = Message()

                
                var link = item.select("a.b_b, a[href*=subop=view]").first()
                if (link == null) link = item.select("a[href*=touid=], a[href*=uid=]").first()
                if (link == null) link = item.select("a").first()
                if (link == null) continue

                val href = link.attr("href")

                
                var touid = extractParam(href, "touid")
                if (touid == null) touid = extractParam(href, "uid")
                msg.pmid = touid 

                
                msg.authorUid = touid

                
                val h2 = link.select("h2").first()
                if (h2 != null) {
                    var fullText = h2.text().trim()
                    val timeSpan = h2.select("span.f_d").first()
                    if (timeSpan != null) {
                        val timeText = timeSpan.text().trim()
                        fullText = fullText.replace(timeText, "").trim()
                    }
                    msg.author = fullText
                } else {
                    
                    val linkText = link.text().trim()
                    if (!linkText.isEmpty()) msg.author = linkText
                }

                
                val deleteEl = item.select("a.kmdel[open_href]").first()
                if (deleteEl != null) {
                    var deleteUrl = deleteEl.attr("abs:open_href")
                    if (TextUtils.isEmpty(deleteUrl)) deleteUrl = deleteEl.attr("open_href")
                    msg.deleteUrl = deleteUrl
                }

                
                val previewEl = link.select("p.f_c").first()
                if (previewEl != null) msg.summary = previewEl.text().trim()

                
                val summary = msg.summary
                msg.title = summary ?: ""

                
                val timeEl = link.select("span.f_d").first()
                if (timeEl != null) msg.time = timeEl.text().trim()

                
                val avatarEl = link.select("img[src*=avatar]").first()
                if (avatarEl != null) {
                    var src = avatarEl.attr("src")
                    if (!src.startsWith("http")) src = BASE_DOMAIN + src
                    msg.avatarUrl = src
                }

                
                val isUnread = item.hasClass("new") || item.hasClass("unread")
                        || item.select(".new, .unread, .un_read, .no_read").size > 0
                
                msg.isRead = !isUnread

                messages.add(msg)
            } catch (ignored: Exception) {
            }
        }

        return messages
    }

    
    @JvmStatic
    fun parseChatOnlineStatus(html: String?): String {
        if (TextUtils.isEmpty(html)) return "离线"
        val doc = Jsoup.parse(html!!)
        val title = doc.select(".comiis_head h2, #comiis_head h2, h2.flex").first()
        val text = if (title != null) title.text().trim() else doc.title()
        if (text.contains("在线")) return "在线"
        if (text.contains("离线")) return "离线"
        return "离线"
    }

    


    @JvmStatic
    fun parseChatMessages(html: String?, selfUid: String?, fallbackAvatar: String?): MutableList<ChatMessage> {
        val result = ArrayList<ChatMessage>()
        if (TextUtils.isEmpty(html)) return result
        val doc = Jsoup.parse(html!!)
        val seen = HashSet<String>()

        
        var items = doc.select("div.comiis_friend_msg, div.comiis_self_msg")
        
        if (items.isEmpty()) {
            items = doc.select("div.pmbox, div.pm_c, div.pm_body, li.pm, li.pm_list, li.cl, .comiis_pm, .comiis_pmitem, [data-pmid]")
        }

        for (item in items) {
            try {
                val isOutgoing = item.hasClass("comiis_self_msg")

                
                var body = item.select("div.msg_mes").first()
                if (body == null) {
                    
                    body = item.select(".pm_content, .pm_body, .message, .content, .pmbox_content, .t_f, .msgtext, .f_c").first()
                }
                if (body == null) body = item
                val content = body.text().trim()
                if (content.isEmpty() || content.length > 5000) continue

                
                val avatarEl = item.select("img.msg_avt, img[src*=avatar], img[src*=uc_server], img.top_tximg").first()
                var avatar = if (avatarEl != null) avatarEl.attr("src") else fallbackAvatar
                if (avatar != null && !avatar!!.isEmpty() && !avatar!!.startsWith("http")) avatar = BASE_DOMAIN + avatar

                
                val userLink = item.select("a[href*=uid=]").first()
                val authorUid = if (userLink != null) extractParam(userLink.attr("href"), "uid") else null

                
                var date = ""
                var previous = item.previousElementSibling()
                while (previous != null) {
                    if (previous.hasClass("comiis_msg_date")) {
                        date = previous.text().trim()
                        break
                    }
                    previous = previous.previousElementSibling()
                }

                
                val timeEl = item.select("div.msg_time.f_d, .time, .date, .dateline, .kmtime, .pm_time, .f_g").first()
                val time = if (timeEl != null) timeEl.text().trim() else ""

                
                val key = (if (isOutgoing) "self" else "friend") + "|" + time + "|" + content
                if (!seen.add(key)) continue

                val message = ChatMessage()
                message.content = content
                message.avatarUrl = avatar
                message.authorUid = authorUid
                message.date = date
                message.time = time
                
                message.outgoing = isOutgoing

                
                if (isOutgoing) {
                    message.author = selfUid 
                } else {
                    val img = item.select("img[alt]").first()
                    val author = if (img != null) img.attr("alt").trim() else ""
                    message.author = author
                }

                result.add(message)
            } catch (ignored: Exception) {
            }
        }
        return result
    }

    





    @JvmStatic
    fun parseFollowerList(html: String?): MutableList<Message> {
        val followers = ArrayList<Message>()
        if (TextUtils.isEmpty(html)) return followers

        val doc = Jsoup.parse(html!!)
        var items = doc.select("div.comiis_userlist01 > li.b_t")
        if (items.isEmpty()) {
            
            items = doc.select("li.b_t")
        }

        val seenUids = HashSet<String>()
        for (item in items) {
            try {
                var userLink = item.select("p.tit > a[href*=uid=]").first()
                if (userLink == null) userLink = item.select("a[href*=uid=]").first()
                if (userLink == null) continue

                val href = userLink.attr("href")
                var uid = extractParam(href, "uid")
                if (TextUtils.isEmpty(uid)) {
                    val uidMatcher = Pattern.compile("(?:^|[?&])uid=(\\d+)").matcher(href)
                    if (uidMatcher.find()) uid = uidMatcher.group(1)
                }
                if (TextUtils.isEmpty(uid) || !seenUids.add(uid!!)) continue

                val author = userLink.text().trim()
                if (TextUtils.isEmpty(author)) continue

                val follower = Message()
                follower.type = 1
                follower.pmid = uid
                follower.authorUid = uid
                follower.author = author
                follower.summary = "关注了你"
                follower.title = author + " 关注了你"
                follower.time = ""
                follower.isRead = true

                val avatarEl = item.select("a.list01_limg img[src*=avatar], a.list01_limg img, img[src*=avatar]").first()
                if (avatarEl != null) {
                    var src = avatarEl.attr("abs:src")
                    if (TextUtils.isEmpty(src)) src = avatarEl.attr("src")
                    if (!TextUtils.isEmpty(src) && !src.startsWith("http")) {
                        src = BASE_DOMAIN + (if (src.startsWith("/")) src.substring(1) else src)
                    }
                    follower.avatarUrl = src
                }

                followers.add(follower)
            } catch (ignored: Exception) {
            }
        }
        return followers
    }

    








    @JvmStatic
    fun normalizeCodeBlocks(html: String?): String? {
        if (html == null || html.isEmpty()) return html
        if (html.indexOf("comiis_blockcode") < 0 && html.indexOf("<pre") < 0
            && html.indexOf("blockcode") < 0
        ) {
            return html
        }
        try {
            val doc = Jsoup.parseBodyFragment(html)
            
            for (pre in doc.select("pre")) {
                escapeSpacesInTree(pre)
            }
            
            for (code in doc.select("div.comiis_blockcode, div.blockcode")) {
                var target = code
                val lines = ArrayList<String>()
                val lis = code.select("ol > li")
                if (!lis.isEmpty()) {
                    var lineNo = 1
                    for (li in lis) {
                        val t = collectRawText(li).replace('\u00a0', ' ').trim()
                        if (t.isEmpty()) {
                            lineNo++
                            continue
                        }
                        val lineMatcher = Pattern.compile("^([0-9]{1,3})\\.?\\s*(.*)$").matcher(t)
                        val numStr: String
                        val codeText: String
                        if (lineMatcher.find()) {
                            val n = lineMatcher.group(1)?.toIntOrNull() ?: lineNo
                            numStr = String.format(Locale.US, "%02d.", n)
                            codeText = lineMatcher.group(2) ?: ""
                        } else {
                            numStr = String.format(Locale.US, "%02d.", lineNo)
                            codeText = t
                        }
                        val escapedCode = escapeHtml(codeText).replace(" ", "&nbsp;")
                        lines.add("<font color=\"#94A3B8\">$numStr</font>&nbsp;&nbsp;$escapedCode")
                        lineNo++
                    }
                } else {
                    val inner = code.selectFirst("pre")
                    if (inner != null) target = inner
                    val raw = splitLinesByBr(target)
                    var lineNo = 1
                    for (s in raw) {
                        val t = s.replace('\u00a0', ' ').trim()
                        if (t.isEmpty()) {
                            lineNo++
                            continue
                        }
                        val lineMatcher = Pattern.compile("^([0-9]{1,3})\\.?\\s*(.*)$").matcher(t)
                        val numStr: String
                        val codeText: String
                        if (lineMatcher.find()) {
                            val n = lineMatcher.group(1)?.toIntOrNull() ?: lineNo
                            numStr = String.format(Locale.US, "%02d.", n)
                            codeText = lineMatcher.group(2) ?: ""
                        } else {
                            numStr = String.format(Locale.US, "%02d.", lineNo)
                            codeText = t
                        }
                        val escapedCode = escapeHtml(codeText).replace(" ", "&nbsp;")
                        lines.add("<font color=\"#94A3B8\">$numStr</font>&nbsp;&nbsp;$escapedCode")
                        lineNo++
                    }
                }
                val sb = StringBuilder("<br><pre class=\"comiis_blockcode\">")
                for (line in lines) {
                    sb.append(line).append("<br>")
                }
                sb.append("</pre><br>")
                val parsed = Jsoup.parseBodyFragment(sb.toString()).body()
                val nodes = ArrayList(parsed.childNodes())
                for (node in nodes) {
                    code.before(node)
                }
                code.remove()
            }
            return doc.body().html()
        } catch (e: Exception) {
            return html
        }
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    
    private fun collectRawText(el: Element): String {
        val sb = StringBuilder()
        collectRawTextNode(el, sb)
        return sb.toString()
    }

    private fun collectRawTextNode(node: Node, sb: StringBuilder) {
        if (node is TextNode) {
            sb.append(node.wholeText)
        } else if (node is Element) {
            for (child in node.childNodes()) {
                collectRawTextNode(child, sb)
            }
        }
    }

    
    private fun escapeSpacesInTree(root: Element) {
        for (node in root.childNodes()) {
            if (node is TextNode) {
                val text = node.wholeText
                if (text.indexOf(' ') >= 0) {
                    node.text(text.replace(" ", "\u00a0"))
                }
            } else if (node is Element) {
                escapeSpacesInTree(node)
            }
        }
    }

    
    private fun splitLinesByBr(root: Element): MutableList<String> {
        val lines = ArrayList<String>()
        val cur = StringBuilder()
        for (node in root.childNodes()) {
            if (node is TextNode) {
                cur.append(node.wholeText)
            } else if (node.nodeName() == "br") {
                lines.add(cur.toString())
                cur.setLength(0)
            } else if (node is Element) {
                cur.append(node.text())
            }
        }
        lines.add(cur.toString())
        return lines
    }

    
    private fun escapeNbsp(text: String): String {
        val sb = StringBuilder(text.length)
        for (i in text.indices) {
            val ch = text[i]
            if (ch == ' ') sb.append("&nbsp;")
            else sb.append(ch)
        }
        return sb.toString()
    }

    





    @JvmStatic
    fun parseThreadDetail(html: String?): PostDetail {
        val detail = PostDetail()

        
        if (TextUtils.isEmpty(html)) {
            detail.replies = ArrayList<ReplyItem>()
            detail.imageUrls = ArrayList<String>()
            detail.currentPage = 1
            detail.totalPages = 1
            return detail
        }

        val doc = Jsoup.parse(html!!)

        
        val forumLink = doc.select("div.comiis_head a.kmtit[href*=forum-]").first()
        if (forumLink != null) {
            detail.forumName = forumLink.ownText().trim()
            val href = forumLink.attr("href")
            val m = Pattern.compile("forum-(\\d+)-").matcher(href)
            if (m.find()) detail.forumFid = m.group(1)
        }

        
        val titleEl = doc.select("div.comiis_viewtit h2 div.km_tits").first()
        if (titleEl != null) {
            detail.title = titleEl.text().trim()
        }

        
        val fhInput = doc.select("input[name=formhash]").first()
        if (fhInput != null) {
            detail.formhash = fhInput.attr("value")
        }
        if (detail.formhash == null || detail.formhash!!.isEmpty()) {
            val fhM = Pattern.compile("formhash\\s*=\\s*['\"]([^'\"]+)['\"]").matcher(html)
            if (fhM.find()) detail.formhash = fhM.group(1)
        }
        
        val naInput = doc.select("input[name=noticeauthor]").first()
        if (naInput != null) {
            detail.noticeauthor = naInput.attr("value")
        }
        val tidM = Pattern.compile("tid\\s*=\\s*['\"](\\d+)['\"]").matcher(html)
        if (tidM.find()) detail.tid = tidM.group(1)

        
        parseCurrentActionStates(doc, html, detail)

        
        var allPostlis = doc.select("div.comiis_postli")
        
        
        if (allPostlis.isEmpty()) {
            allPostlis = doc.select(
                "article:has(div.comiis_message), article:has(td.t_f), " +
                        "div[class*=post]:has(div.comiis_message), " +
                        "div[class*=post]:has(td.t_f), " +
                        "div[class*=thread]:has(div.comiis_message), " +
                        "div[class*=thread]:has(td.t_f)"
            )
        }
        if (allPostlis.isEmpty()) {
            
            
            val fallbackContent = doc.select(
                "div.comiis_message, td.t_f, div.message, div.postbody, article"
            ).first()
            if (fallbackContent != null) {
                val clone = fallbackContent.clone()
                clone.select("script, style, div.comiis_favshare, div.comiis_postli_time, a.followmod")
                    .remove()
                val fallbackHtml = clone.html().trim()
                if (!TextUtils.isEmpty(fallbackHtml)) {
                    detail.contentHtml = fallbackHtml
                }
            }
            detail.replies = ArrayList<ReplyItem>()
            detail.imageUrls = ArrayList<String>()
            detail.currentPage = 1
            detail.totalPages = 1
            return detail
        }

        
        val opPostli = allPostlis.first()!!
        
        
        val opPid = opPostli.id()
        if (!TextUtils.isEmpty(opPid) && opPid.startsWith("pid")) {
            detail.postPid = opPid.substring(3)
        }

        
        val opTop = opPostli.select("div.comiis_postli_top").first()
        if (opTop != null) {
            
            val avatarImg = opTop.select("a.postli_top_tximg img.top_tximg").first()
            if (avatarImg != null) {
                var src = avatarImg.attr("src")
                if (!src.startsWith("http")) src = BASE_DOMAIN + src
                detail.avatarUrl = src
            }
            
            val authorEl = opTop.select("a.top_user.f_b").first()
            if (authorEl != null) {
                detail.author = authorEl.text().trim()
                val m = Pattern.compile("uid=(\\d+)").matcher(authorEl.attr("href"))
                if (m.find()) detail.authorUid = m.group(1)
            }
            
            val levelEl = opTop.select("a.top_lev.bg_a").first()
            if (levelEl != null) detail.authorLevel = levelEl.text().trim()
            
            val genderEl = opTop.select("i.comiis_font.top_gender").first()
            if (genderEl != null) {
                val cls = genderEl.className()
                if (cls.contains("bg_boy")) detail.gender = "boy"
                else if (cls.contains("bg_girl")) detail.gender = "girl"
            }
            
            val followEl = opTop.select("a.followmod").first()
            if (followEl != null) {
                detail.isFollowed = followEl.text().trim().contains("已关注")
            }
            
            val opTimeArea = opTop.select("div.comiis_postli_time").first()
            if (opTimeArea != null) {
                val timeEl = opTimeArea.select("span.kmtime").first()
                var rawTime = timeEl?.text()?.replace('\u00a0', ' ')?.trim() ?: ""
                
                var locFromTime: String? = null
                val locIdx = rawTime.indexOf("来自")
                if (locIdx > 0) {
                    locFromTime = rawTime.substring(locIdx).trim()
                    rawTime = rawTime.substring(0, locIdx).trim()
                }
                if (rawTime.isNotEmpty()) detail.publishTime = rawTime
                val locCode = opTimeArea.select("code.comiis_iplocality").first()
                val locText = locCode?.text()?.replace('\u00a0', ' ')?.trim()
                detail.location = when {
                    !locText.isNullOrEmpty() -> locText
                    !locFromTime.isNullOrEmpty() -> locFromTime
                    else -> null
                }
            }
        }

        
        val opMsg = opPostli.select("div.comiis_message").first()
        if (opMsg != null) {
            
            var contentDiv = opMsg.select("div.comiis_a.comiis_message_table.cl").first()
            if (contentDiv == null) {
                contentDiv = opMsg.select("div.comiis_a").first()
            }
            if (contentDiv == null) {
                contentDiv = opMsg.select("div.comiis_message_table").first()
            }
            if (contentDiv == null && !TextUtils.isEmpty(opMsg.html())) {
                
                val msgClone = opMsg.clone()
                msgClone.select("div.comiis_favshare").remove()
                msgClone.select("a.followmod").remove()
                msgClone.select("div.comiis_postli_time").remove()
                msgClone.select("div.comiis_message_table.cl").remove()
                val remaining = msgClone.html().trim()
                if (!TextUtils.isEmpty(remaining) && remaining.length > 50) {
                    contentDiv = opMsg 
                }
            }
            if (contentDiv != null) {
                
                
                val cleanDiv = contentDiv.clone()
                cleanDiv.select(
                    "div.comiis_rate, div[class*=comiis_rate], " +
                    "div.comiis_praise, div[class*=praise], " +
                    "ul.comiis_recommend_list_a, ul.comiis_recommend_list_t, ul[class*=recommend_list], " +
                    "em.comiis_recommend_num, a.comiis_recommend_addkey, " +
                    "div.comiis_favshare, div[class*=favshare], a.followmod, " +
                    "div.comiis_postli_time, div.manage, div.modact"
                ).remove()

                var content = cleanDiv.html().trim()
                
                val tf = opMsg.select("td.t_f").first()
                if (content.length < 10 && tf != null) {
                    content = tf.html().trim()
                }
                detail.contentHtml = normalizeCodeBlocks(content)
            } else {
                
                val msgClone = opMsg.clone()
                msgClone.select(
                    "div.comiis_rate, div[class*=comiis_rate], " +
                    "div.comiis_praise, div[class*=praise], " +
                    "ul.comiis_recommend_list_a, ul.comiis_recommend_list_t, ul[class*=recommend_list], " +
                    "em.comiis_recommend_num, a.comiis_recommend_addkey, " +
                    "div.comiis_favshare, div[class*=favshare], a.followmod, " +
                    "div.comiis_postli_time, div.manage, div.modact"
                ).remove()
                val fallbackHtml = msgClone.html().trim()
                if (fallbackHtml.length > 30) {
                    detail.contentHtml = normalizeCodeBlocks(fallbackHtml)
                }
            }

            
            var messagesDiv = opMsg.select("div.comiis_messages").first()
            if (messagesDiv == null) messagesDiv = opMsg
            val attachImageUrls = ArrayList<String>()
            val postImages = opPostli.select("div.comiis_messages img, div.comiis_message_table img, div.comiis_attach img, div.viewimg img, .comiis_pyqlist_imgs img, .mmlist_li_box img, .attach_image img, img.comiis_loadimages, img[zoomfile], img[file]")
            val targetImgs = if (postImages.isNotEmpty()) postImages else messagesDiv.select("img")
            for (img in targetImgs) {
                if (img.hasClass("top_tximg") || img.closest(".comiis_postli_top") != null) continue
                val realSrc = firstNonEmptyAttr(
                    img,
                    "zoomfile", "file", "comiis_loadimages", "data-original", "data-src",
                    "data-file", "data-lazy-src", "src"
                )
                val fullUrl = resolveAttachmentUrl(realSrc)
                if (isPostImageUrl(fullUrl) && !attachImageUrls.contains(fullUrl)) {
                    attachImageUrls.add(fullUrl!!)
                }
            }
            detail.imageUrls = attachImageUrls

            
            val quoteDiv = opMsg.select("div.comiis_quote").first()
            if (quoteDiv != null) {
                detail.hasHiddenContent = true
                detail.hiddenContentHtml = normalizeCodeBlocks(quoteDiv.html().trim())
            }
            
            val recommendNum = opMsg.selectFirst("em.comiis_recommend_num")
            if (recommendNum != null) {
                detail.likeCount = parseIntFromText(recommendNum.text())
            } else {
                val recommendLis = opMsg.select("ul.comiis_recommend_list_a li")
                detail.likeCount = recommendLis.size
            }
            
            val likeUids = ArrayList<String>()
            val likeAvatars = ArrayList<String>()
            val likeNames = ArrayList<String>()
            val avatarLis = opMsg.select("ul.comiis_recommend_list_a li")
            for (li in avatarLis) {
                val liId = li.id()
                var uid: String? = null
                if (liId != null && liId.startsWith("comiis_recommend_list_a")) {
                    uid = liId.substring("comiis_recommend_list_a".length)
                }
                val img = li.selectFirst("img")
                var avatar = if (img != null) img.absUrl("src") else ""
                if (avatar.isEmpty()) {
                    avatar = if (img != null) img.attr("src") else ""
                }
                
                if (!avatar.isEmpty() && avatar.startsWith("/") && !avatar.startsWith("//")) {
                    avatar = BASE_DOMAIN + avatar
                }
                if (uid == null || uid.isEmpty()) {
                    
                    val link = li.selectFirst("a[href*=uid=]")
                    val um = Pattern.compile("uid=(\\d+)").matcher(if (link != null) link.attr("href") else "")
                    if (um.find()) uid = um.group(1)
                }
                if (uid != null && !uid.isEmpty()) {
                    likeUids.add(uid)
                    likeAvatars.add(avatar)
                }
            }
            
            var nameSpans = opMsg.select("ul.comiis_recommend_list_t span[id^=comiis_recommend_list_t]")
            if (nameSpans.isEmpty()) {
                nameSpans = doc.select("ul.comiis_recommend_list_t span[id^=comiis_recommend_list_t]")
            }
            for (sp in nameSpans) {
                val a = sp.selectFirst("a")
                if (a != null && !a.text().trim().isEmpty()) {
                    likeNames.add(a.text().trim())
                }
            }
            if (!likeUids.isEmpty()) {
                detail.likeUserUids = likeUids
                detail.likeUserAvatars = likeAvatars
            }
            if (!likeNames.isEmpty()) {
                detail.likeUserNames = likeNames
            }
        }

        
        
        val favoriteNum = doc.selectFirst("#comiis_favorite_a span.comiis_favorite_a_num")
        if (favoriteNum != null) {
            detail.favoriteCount = parseIntFromText(favoriteNum.text())
        }
        
        if (detail.favoriteCount <= 0) {
            val favAlt = doc.selectFirst("span.comiis_favorite_a_num")
            if (favAlt != null) {
                detail.favoriteCount = parseIntFromText(favAlt.text())
            }
        }

        
        val pltit = doc.select("div.comiis_pltit").first()
        if (pltit != null) {
            val countSpan = pltit.select("span.f_d").first()
            if (countSpan != null) {
                detail.replyCount = parseIntFromText(countSpan.text())
            }
        }

        
        val pageInput = doc.select("input[name=page]").first()
        if (pageInput != null) {
            val curPage = parseIntFromText(pageInput.attr("value"))
            detail.currentPage = if (curPage > 0) curPage else 1
        } else {
            detail.currentPage = 1
        }

        
        
        
        
        
        val replies = ArrayList<ReplyItem>()
        val uidPtn = Pattern.compile("uid=(\\d+)")
        val startIndex = if (detail.currentPage > 1) 0 else 1
        for (i in startIndex until allPostlis.size) {
            try {
                val rp = allPostlis.get(i)
                val reply = ReplyItem()

                
                val pidAttr = rp.id()
                if (pidAttr != null && pidAttr.startsWith("pid")) {
                    reply.pid = pidAttr.substring(3)
                }

                val rTop = rp.select("div.comiis_postli_top").first() ?: continue

                
                val floorEl = rTop.select("span.f_d.y").first()
                if (floorEl != null) reply.floorLabel = floorEl.text().trim()

                
                val userEl = rTop.select("a.top_user.f_b").first()
                if (userEl != null) {
                    reply.author = userEl.text().trim()
                    val m = uidPtn.matcher(userEl.attr("href"))
                    if (m.find()) reply.authorUid = m.group(1)
                }
                
                val levEl = rTop.select("a.top_lev.bg_a").first()
                if (levEl != null) reply.authorLevel = levEl.text().trim()
                
                val gEl = rTop.select("i.comiis_font.top_gender").first()
                if (gEl != null) {
                    val cls = gEl.className()
                    if (cls.contains("bg_boy")) reply.gender = "boy"
                    else if (cls.contains("bg_girl")) reply.gender = "girl"
                }
                
                val opTag = rTop.select("span.top_lev.bg_c.f_f").first()
                if (opTag != null && opTag.text().contains("楼主")) {
                    reply.isOP = true
                }
                
                val rAvatar = rTop.select("a.postli_top_tximg img.top_tximg").first()
                if (rAvatar != null) {
                    var src = rAvatar.attr("src")
                    if (!src.startsWith("http")) src = BASE_DOMAIN + src
                    reply.avatarUrl = src
                }

                
                var rContent = rp.select("div.comiis_message div.comiis_a.comiis_message_table.cl").first()
                if (rContent == null) {
                    rContent = rp.select("div.comiis_message div.comiis_a").first()
                }
                if (rContent == null) {
                    rContent = rp.select("div.comiis_message").first()
                }
                if (rContent != null) {
                    
                    
                    val quote = rContent.select("div.comiis_quote, blockquote, .quote").first()
                    if (quote != null) {
                        
                        
                        
                        val quoteHead = quote.selectFirst("a.quote_author, .quote_author, cite")
                            ?: quote.selectFirst("a[href*=pid=]")
                        if (quoteHead != null) {
                            val headText = quoteHead.text().replace('\u00a0', ' ').trim()
                            if (!TextUtils.isEmpty(headText)) {
                                reply.quotedAuthorName = headText
                                    .replace(Regex("^回复\\s*"), "")
                                    .replace(Regex("\\s*发表于.*$"), "")
                                    .trim()
                            }
                            
                            
                            val href = quoteHead.attr("href")
                            val pidM = Pattern.compile("pid=(\\d+)").matcher(href)
                            if (pidM.find()) reply.quotedPid = pidM.group(1)
                            val uidM = uidPtn.matcher(href)
                            if (uidM.find()) reply.quotedUid = uidM.group(1)
                        }
                        
                        
                        
                        if (TextUtils.isEmpty(reply.quotedPid)) {
                            val link = quote.selectFirst("a[href*=\"goto=findpost\"][href*=pid=]")
                                ?: quote.selectFirst("a[href*=pid=]")
                            if (link != null) {
                                val pidM = Pattern.compile("pid=(\\d+)").matcher(link.attr("href"))
                                if (pidM.find()) reply.quotedPid = pidM.group(1)
                            }
                        }
                        reply.quotedContentHtml = quote.html().trim()
                        reply.quotedContentText = quote.text().replace('\u00a0', ' ').trim()
                        quote.remove()
                    }
                    reply.contentHtml = normalizeCodeBlocks(rContent.html().trim())
                    reply.contentText = rContent.text().replace('\u00a0', ' ').trim()
                }

                
                val replyImages = rp.select("div.comiis_messages img, div.comiis_message_table img, div.comiis_attach img, div.viewimg img, .attach_image img, img.comiis_loadimages, img[zoomfile], img[file]")
                for (img in replyImages) {
                    if (img.hasClass("top_tximg") || img.closest(".comiis_postli_top") != null) continue
                    val realSrc = firstNonEmptyAttr(
                        img,
                        "zoomfile", "file", "comiis_loadimages", "data-original", "data-src",
                        "data-file", "data-lazy-src", "src"
                    )
                    val fullUrl = resolveAttachmentUrl(realSrc)
                    if (isPostImageUrl(fullUrl) && !reply.imageUrls.contains(fullUrl)) {
                        reply.imageUrls.add(fullUrl!!)
                    }
                }

                
                val rTimes = rp.select("div.comiis_postli_times").first()
                if (rTimes != null) {
                    val timeEl = rTimes.select("span.comiis_tm").first()
                    var rawTime = timeEl?.text()?.replace('\u00a0', ' ')?.trim() ?: ""
                    
                    
                    var locFromTime: String? = null
                    val locIdx = rawTime.indexOf("来自")
                    if (locIdx > 0) {
                        locFromTime = rawTime.substring(locIdx).trim()
                        rawTime = rawTime.substring(0, locIdx).trim()
                    }
                    if (rawTime.isNotEmpty()) reply.time = rawTime
                    val locCode = rTimes.select("code.comiis_iplocality").first()
                    val locText = locCode?.text()?.replace('\u00a0', ' ')?.trim()
                    reply.location = when {
                        !locText.isNullOrEmpty() -> locText
                        !locFromTime.isNullOrEmpty() -> locFromTime
                        else -> null
                    }
                }

                replies.add(reply)
            } catch (ignored: Exception) {
            }
        }
        detail.replies = replies

        
        var maxPage = 1
        
        val pgDiv = doc.select("div.pg").first()
        if (pgDiv != null) {
            val span = pgDiv.select("span[title*=共]").first()
            if (span != null) {
                val title = span.attr("title")
                val titleM = Pattern.compile("共\\s*(\\d+)\\s*页").matcher(title)
                if (titleM.find()) {
                    maxPage = parseIntFromText(titleM.group(1))
                } else {
                    
                    val textM = Pattern.compile("/?\\s*(\\d+)\\s*页").matcher(span.text())
                    if (textM.find()) {
                        maxPage = parseIntFromText(textM.group(1))
                    }
                }
            }
            
            if (maxPage <= 1) {
                val numLinks = pgDiv.select("a[href*=-1.html]")
                val tidPagePtn = Pattern.compile("thread-\\d+-(\\d+)-1\\.html")
                for (pl in numLinks) {
                    val pm = tidPagePtn.matcher(pl.attr("href"))
                    if (pm.find()) {
                        try {
                            val p = pm.group(1).toInt()
                            if (p > maxPage) maxPage = p
                        } catch (ignored: Exception) {
                        }
                    }
                }
            }
        }
        
        if (maxPage <= 1) {
            val pageLinks = doc.select("a[href*=page=]")
            val pagePtn = Pattern.compile("[&?]page=(\\d+)")
            for (pl in pageLinks) {
                val pm = pagePtn.matcher(pl.attr("href"))
                if (pm.find()) {
                    try {
                        val p = pm.group(1).toInt()
                        if (p > maxPage) maxPage = p
                    } catch (ignored: Exception) {
                    }
                }
            }
        }
        detail.totalPages = maxPage

        
        
        
        val rateDiv = doc.select("div.comiis_rate").first()
        val rewardAvatars = ArrayList<String>()
        val goodReviewAvatars = ArrayList<String>()
        if (rateDiv != null) {
            val rateTip = rateDiv.select("p.rate_tip").first()
            val rateTipText = if (rateTip != null) rateTip.text().trim() else rateDiv.text().trim()
            val rewardCountMatcher = Pattern.compile("(\\d+)\\s*人?打赏").matcher(rateTipText)
            if (rewardCountMatcher.find()) {
                detail.rewardCount = parseIntFromText(rewardCountMatcher.group(1))
            } else {
                val rateBtn = rateDiv.select("h2.rate_btn").first()
                if (rateBtn != null && rateBtn.text().contains("赞赏")) {
                    detail.rewardCount = parseIntFromText(rateBtn.text())
                }
            }

            val goodCountMatcher = Pattern.compile("(\\d+)\\s*好评").matcher(rateTipText)
            if (goodCountMatcher.find()) {
                detail.goodReviewCount = parseIntFromText(goodCountMatcher.group(1))
            }

            if (rateTip != null) {
                val rewardLink = rateTip.select("a[href*=viewratings]").first()
                if (rewardLink != null) {
                    val href = rewardLink.attr("href")
                    detail.rewardDetailUrl = resolveAttachmentUrl(href)
                }
            }

            for (img in rateDiv.select("ul > li img")) {
                val avatar = resolveAvatarUrl(
                    firstNonEmptyAttr(img, "src", "data-src", "data-original", "data-lazy-src")
                )
                if (!TextUtils.isEmpty(avatar) && !rewardAvatars.contains(avatar)) {
                    rewardAvatars.add(avatar!!)
                }
            }
        }
        detail.rewardUserAvatars = rewardAvatars
        detail.goodReviewUserAvatars = goodReviewAvatars

        return detail
    }

    



    @JvmStatic
    fun parseRewardCoins(html: String?): Int {
        if (TextUtils.isEmpty(html)) return 0
        val text = Jsoup.parse(html!!).text()
        val matcher = Pattern.compile("金币\\s*\\+\\s*(\\d+)").matcher(text)
        if (matcher.find()) return parseIntFromText(matcher.group(1))
        return 0
    }

    



    @JvmStatic
    fun parseGoodReviewAvatarUrls(html: String?): MutableList<String> {
        val avatars = ArrayList<String>()
        if (TextUtils.isEmpty(html)) return avatars
        val doc = Jsoup.parse(html!!)
        for (row in doc.select("tr[id^=rate_]")) {
            val cells = row.select("> td")
            if (cells.size >= 2 && cells.get(1).text().contains("+")) {
                val img = row.select("td:first-child img").first()
                val avatar = resolveAvatarUrl(
                    firstNonEmptyAttr(img, "src", "data-src", "data-original", "data-lazy-src")
                )
                if (!TextUtils.isEmpty(avatar) && !avatars.contains(avatar)) {
                    avatars.add(avatar!!)
                }
            }
        }
        return avatars
    }

    



    private fun parseCurrentActionStates(doc: Document?, html: String?, detail: PostDetail?) {
        if (doc == null || detail == null || TextUtils.isEmpty(html)) return

        val uidMatcher = Pattern.compile(
            "(?:var\\s+)?uid\\s*=\\s*['\"](\\d+)['\"]",
            Pattern.CASE_INSENSITIVE
        ).matcher(html!!)
        if (!uidMatcher.find() || "0" == uidMatcher.group(1)) return

        val recommend = doc.select(
            "a.comiis_recommend_addkey, a.comiis_recommend_new, " +
                    ".comiis_recommend_addkey"
        ).first()
        if (recommend != null) {
            val icon = recommend.select("i.comiis_recommend_color").first()
            val classes = recommend.className() + " " +
                    (if (icon != null) icon.className() else "")
            val iconHtml = if (icon != null) icon.html() else ""
            val liked = classes.contains("f_a") || iconHtml.contains("e654")
            detail.isLiked = liked
            detail.likedStateKnown = true
        }

        val favoriteIcon = doc.select("#comiis_favorite_a i.comiis_favorite_a_color").first()
        if (favoriteIcon != null) {
            val classes = favoriteIcon.className()
            val iconHtml = favoriteIcon.html()
            val favorited = classes.contains("f_a") || iconHtml.contains("e64c")
            detail.isFavorited = favorited
            detail.favoritedStateKnown = true
        }
    }

    



    @JvmStatic
    fun parseFormhash(html: String?): String? {
        if (TextUtils.isEmpty(html)) return null
        val doc = Jsoup.parse(html!!)
        val input = doc.select("input[name=formhash]").first()
        if (input != null) return input.attr("value")

        
        val m = Pattern.compile("formhash\\s*=\\s*['\"]([^'\"]+)['\"]").matcher(html)
        if (m.find()) return m.group(1)

        return null
    }

    




    @JvmStatic
    fun parseFavoriteFormhash(html: String?): String? {
        if (TextUtils.isEmpty(html)) return null
        val doc = Jsoup.parse(html!!)
        
        val form = doc.select("form[id*=favoriteform]").first()
        if (form != null) {
            val input = form.select("input[name=formhash]").first()
            if (input != null) {
                val v = input.attr("value")
                if (!TextUtils.isEmpty(v)) return v
            }
        }
        
        val fallback = doc.select("input[name=formhash]").first()
        if (fallback != null) {
            val v = fallback.attr("value")
            if (!TextUtils.isEmpty(v)) return v
        }
        
        val m = Pattern.compile("formhash\\s*=\\s*['\"]([^'\"]+)['\"]").matcher(html)
        if (m.find()) return m.group(1)
        return null
    }

    


    private fun extractParam(url: String?, param: String): String? {
        if (url == null || url.isEmpty()) return null
        try {
            val p = Pattern.compile(param + "=(\\d+)")
            val m = p.matcher(url)
            if (m.find()) return m.group(1)

            
            val p2 = Pattern.compile(param + "=([^&]+)")
            val m2 = p2.matcher(url)
            if (m2.find()) return m2.group(1)
        } catch (ignored: Exception) {
        }
        return null
    }

    
    @JvmStatic
    fun extractFollowingUids(html: String?): MutableSet<String> {
        val uids = HashSet<String>()
        if (TextUtils.isEmpty(html)) return uids
        val doc = Jsoup.parse(html!!)
        var links = doc.select("div.comiis_userlist01 > li.b_t > p.tit > a[href*=uid=], li.b_t p.tit a[href*=uid=]")
        if (links.isEmpty()) links = doc.select("a[href*=home.php?mod=space&uid=]")
        for (link in links) {
            val uid = extractParam(link.attr("href"), "uid")
            if (!TextUtils.isEmpty(uid)) uids.add(uid!!)
        }
        return uids
    }

    
    @JvmStatic
    fun hasUserListPageAfter(html: String?, currentPage: Int): Boolean {
        if (TextUtils.isEmpty(html)) return false
        val doc = Jsoup.parse(html!!)
        for (link in doc.select("a[href*=page=]")) {
            val matcher = Pattern.compile("(?:^|[?&])page=(\\d+)")
                .matcher(link.attr("href"))
            if (matcher.find()) {
                try {
                    if (matcher.group(1).toInt() > currentPage) return true
                } catch (ignored: Exception) {
                }
            }
        }
        return false
    }

    










    @JvmStatic
    fun parseFriendList(html: String?): MutableList<Friend> {
        val friends = ArrayList<Friend>()
        if (TextUtils.isEmpty(html)) return friends
        val doc = Jsoup.parse(html!!)

        
        var userItems = doc.select("div.comiis_userlist01 > li.b_t")
        if (userItems.isEmpty()) {
            
            userItems = doc.select("li.b_t")
        }

        for (li in userItems) {
            try {
                
                var usernameLink = li.select("p.tit > a[href*=uid=]").first()
                if (usernameLink == null) {
                    
                    usernameLink = li.select("a[href*=uid=]").first()
                }
                if (usernameLink == null) continue

                val username = usernameLink.text().trim()
                if (TextUtils.isEmpty(username)) continue

                val href = usernameLink.attr("href")
                val m = Pattern.compile("uid=(\\d+)").matcher(href)
                if (!m.find()) continue
                val uid = m.group(1)

                
                var duplicate = false
                for (existing in friends) {
                    if (uid == existing.uid) {
                        duplicate = true
                        break
                    }
                }
                if (duplicate) continue

                val f = Friend()
                f.uid = uid
                f.username = username

                
                val avatarImg = li.select("a.list01_limg img[src*=avatar], img[src*=avatar]").first()
                if (avatarImg != null) {
                    var src = avatarImg.attr("src")
                    if (!src.startsWith("http")) src = BASE_DOMAIN + src
                    f.avatarUrl = src
                }

                
                val levelEl = li.select(".kmlevs, .kmlv, span:contains(Lv)").first()
                if (levelEl == null) {
                    
                    val txtFonts = li.select("p.txt > font")
                    for (font in txtFonts) {
                        var t = font.text().trim()
                        if (t.contains("Lv") || t.contains("硕士") || t.contains("博士")
                            || t.contains("大学") || t.contains("高中") || t.contains("初中")
                            || t.contains("小学") || t.contains("学前")
                        ) {
                            if (t.contains("积分")) {
                                t = Regex("积分.*").replace(t, "").trim()
                            }
                            f.level = t
                            break
                        }
                    }
                } else {
                    f.level = levelEl.text().trim()
                }

                
                val creditsEl = li.select("p.txt > font:contains(积分)").first()
                if (creditsEl != null) {
                    f.credits = creditsEl.text().trim()
                }

                friends.add(f)
            } catch (ignored: Exception) {
            }
        }

        return friends
    }

    



    @JvmStatic
    fun parseFavoriteList(html: String?): MutableList<Thread> {
        val favorites = ArrayList<Thread>()
        if (TextUtils.isEmpty(html)) return favorites
        val doc = Jsoup.parse(html!!)

        
        
        val links = doc.select("a[href*=thread-], a[href*=thread.php], a[href*=viewthread]")
        val tidPretty = Pattern.compile("(?:^|/)thread-(\\d+)(?:-[^./?#]+)*\\.html", Pattern.CASE_INSENSITIVE)
        val tidQuery = Pattern.compile("[?&](?:tid|threadid)=(\\d+)", Pattern.CASE_INSENSITIVE)
        val favidPattern = Pattern.compile("(?:favid|fav_id)\\s*[=:/]\\s*['\"]?(\\d+)", Pattern.CASE_INSENSITIVE)
        val favidHrefPattern = Pattern.compile("[?&](?:favid|fav_id)=(\\d+)", Pattern.CASE_INSENSITIVE)
        val seen = HashSet<String>()

        for (link in links) {
            try {
                val href = link.attr("href")
                var m = tidPretty.matcher(href)
                var tid = if (m.find()) m.group(1) else ""
                if (TextUtils.isEmpty(tid)) {
                    m = tidQuery.matcher(href)
                    if (m.find()) tid = m.group(1)
                }
                if (TextUtils.isEmpty(tid) || seen.contains(tid)) continue

                var title = link.attr("title").trim()
                if (TextUtils.isEmpty(title)) title = link.text().trim()
                if (TextUtils.isEmpty(title)) continue

                val t = Thread()
                t.tid = tid
                t.title = title

                var favid = firstNonEmpty(
                    link.attr("data-favid"), link.attr("data-fav-id"),
                    link.attr("favid"), link.attr("data-id")
                )
                val hrefFav = favidHrefPattern.matcher(href)
                if (TextUtils.isEmpty(favid) && hrefFav.find()) favid = hrefFav.group(1)

                
                var parent: Element? = link
                var depth = 0
                while (parent != null && depth < 6 && TextUtils.isEmpty(favid)) {
                    val deleteLinks = parent.select(
                        "a[href*=spacecp][href*=favorite], " +
                                "a[href*=favid], a[data-favid], a[data-fav-id]"
                    )
                    for (deleteLink in deleteLinks) {
                        val deleteHref = deleteLink.attr("href")
                        val dm = favidHrefPattern.matcher(deleteHref)
                        if (dm.find()) {
                            favid = dm.group(1)
                            break
                        }
                        favid = firstNonEmpty(
                            deleteLink.attr("data-favid"),
                            deleteLink.attr("data-fav-id"), deleteLink.attr("favid")
                        )
                        if (!TextUtils.isEmpty(favid)) break
                    }
                    if (TextUtils.isEmpty(favid)) {
                        val fm = favidPattern.matcher(parent.outerHtml())
                        if (fm.find()) favid = fm.group(1)
                    }
                    parent = parent.parent()
                    depth++
                }

                t.favid = favid
                seen.add(tid)
                favorites.add(t)
            } catch (ignored: Exception) {
            }
        }
        return favorites
    }

    private fun firstNonEmpty(vararg values: String?): String {
        for (value in values) {
            if (!TextUtils.isEmpty(value)) return value!!
        }
        return ""
    }

    



    @JvmStatic
    fun parseCreditDetails(html: String?): MutableMap<String, String> {
        val credits = HashMap<String, String>()
        if (TextUtils.isEmpty(html)) return credits
        val doc = Jsoup.parse(html!!)
        val jfLis = doc.select(".comiis_space_profilejf ul li")
        for (i in 0 until jfLis.size) {
            try {
                val li = jfLis.get(i)
                val valEl = li.select(".f_0, span").first()
                val value = if (valEl != null) valEl.text().trim() else li.text().trim()
                
                var label = li.ownText().trim()
                if (label.isEmpty()) label = "项目" + (i + 1)
                credits[label] = value
            } catch (ignored: Exception) {
            }
        }
        
        val profileLis = doc.select(".comiis_space_profile li")
        for (li in profileLis) {
            try {
                val labelSpan = li.select("span").first()
                val label = if (labelSpan != null) labelSpan.text().trim() else ""
                val valueEl = li.select(".profile_rs").first()
                val value = if (valueEl != null) valueEl.text().trim() else ""
                if (!label.isEmpty() && !value.isEmpty()) {
                    credits[label] = value
                }
            } catch (ignored: Exception) {
            }
        }
        return credits
    }

    


    @JvmStatic
    fun parseEditableProfile(html: String?): MutableMap<String, String> {
        val fields = HashMap<String, String>()
        if (TextUtils.isEmpty(html)) return fields
        val doc = Jsoup.parse(html!!)
        
        val inputs = doc.select("input[type=text], input[type=email], input[type=url], textarea, select")
        for (input in inputs) {
            try {
                val name = input.attr("name")
                val value = input.`val`().trim()
                if (!name.isEmpty()) {
                    fields[name] = value
                }
            } catch (ignored: Exception) {
            }
        }
        return fields
    }

    private fun parseIntFromText(text: String?): Int {
        if (text == null || text.isEmpty()) return 0
        try {
            val m = Pattern.compile("(\\d+)").matcher(text)
            if (m.find()) return m.group(1).toInt()
        } catch (ignored: Exception) {
        }
        return 0
    }

    private fun firstNonEmptyAttr(element: Element?, vararg names: String?): String? {
        if (element == null) return null
        for (name in names) {
            if (name != null && element.hasAttr(name)) {
                val value = element.attr(name)
                if (!TextUtils.isEmpty(value)) return value.trim()
            }
        }
        return null
    }

    private fun resolveAvatarUrl(url: String?): String? {
        if (TextUtils.isEmpty(url)) return null
        if (url!!.startsWith("http://") || url.startsWith("https://")) return url
        if (url.startsWith("//")) return "https:$url"
        if (url.startsWith("/")) return BASE_DOMAIN + url.substring(1)
        return BASE_DOMAIN + url
    }

    private fun isPostImageUrl(url: String?): Boolean {
        if (TextUtils.isEmpty(url)) return false
        val lower = url!!.lowercase()
        if (lower.contains("none.gif") || lower.contains("none.png") || lower.contains("blank.gif")
            || lower.contains("loading") || lower.contains("avatar.php")
            || lower.contains("/static/image/common/") || lower.contains("/static/image/filetype/")
            || lower.contains("/static/image/smiley/")) {
            return false
        }
        if (lower.contains("smiley") || lower.contains("emoticon")) {
            return false
        }
        return true
    }

    



    private fun resolveAttachmentUrl(url: String?): String? {
        if (TextUtils.isEmpty(url)) return null
        
        if (url!!.startsWith("http://") || url.startsWith("https://")) {
            return url
        }
        if (url.startsWith("//")) {
            return "https:$url"
        }
        if (url.startsWith("/")) {
            return BASE_DOMAIN + url.substring(1)
        }
        
        return BASE_DOMAIN + url
    }

    


    private fun populateThreadImages(item: Element, thread: Thread) {
        val imageUrls = ArrayList<String>()
        val imageElements = item.select(
            ".comiis_pyqlist_imgs img, .comiis_pyqlist_img img"
        )
        for (image in imageElements) {
            val src = firstNonEmptyAttr(
                image,
                "comiis_loadimages", "data-original", "data-src",
                "data-file", "file", "data-lazy-src", "src"
            )
            val fullUrl = resolveAttachmentUrl(src)
            if (isPostImageUrl(fullUrl) && !imageUrls.contains(fullUrl)) {
                imageUrls.add(fullUrl!!)
                if (imageUrls.size >= 9) break
            }
        }

        
        if (imageUrls.isEmpty()) {
            val fallbackImages = item.select(".mmlist_li_box img")
            for (image in fallbackImages) {
                val src = firstNonEmptyAttr(
                    image,
                    "comiis_loadimages", "data-original", "data-src",
                    "data-file", "file", "data-lazy-src", "src"
                )
                val fullUrl = resolveAttachmentUrl(src)
                if (isPostImageUrl(fullUrl) && !imageUrls.contains(fullUrl)) {
                    imageUrls.add(fullUrl!!)
                    if (imageUrls.size >= 9) break
                }
            }
        }

        thread.imageUrls = imageUrls
        if (!imageUrls.isEmpty()) {
            thread.hasImage = true
            if (TextUtils.isEmpty(thread.thumbnailUrl)) {
                thread.thumbnailUrl = imageUrls.get(0)
            }
        }
    }

    


    @JvmStatic
    fun getThreadListUrl(fid: String?, page: Int): String {
        return BASE_DOMAIN + "forum.php?mod=forumdisplay&fid=" + fid + "&page=" + page + "&mobile=2"
    }

    



    @JvmStatic
    fun getThreadListUrlDesktop(fid: String?, page: Int): String {
        return BASE_DOMAIN + "forum.php?mod=forumdisplay&fid=" + fid + "&page=" + page
    }

    


    @JvmStatic
    fun getHomeUrl(page: Int): String {
        return BASE_DOMAIN + "forum.php?mod=guide&view=newthread&page=" + page + "&mobile=2"
    }

    


    @JvmStatic
    fun getGuideUrl(view: String?, page: Int): String {
        return BASE_DOMAIN + "forum.php?mod=guide&view=" + view + "&page=" + page + "&mobile=2"
    }

    


    @JvmStatic
    fun getLoginUrl(): String {
        return BASE_DOMAIN + "member.php?mod=logging&action=login&mobile=2"
    }

    




    @JvmStatic
    fun getLoginPostUrl(): String {
        return BASE_DOMAIN + "member.php?mod=logging&action=login&loginsubmit=yes&loginhash=" + "&mobile=2"
    }

    


    @JvmStatic
    fun extractLoginPostUrl(loginPageHtml: String?): String {
        if (TextUtils.isEmpty(loginPageHtml)) return getLoginPostUrl()
        val doc = Jsoup.parse(loginPageHtml!!)
        var form = doc.select("form[id=loginform]").first()
        if (form == null) {
            form = doc.select("form[method=post][action*=login]").first()
        }
        if (form != null) {
            val action = form.attr("action")
            if (!TextUtils.isEmpty(action)) {
                if (action.startsWith("http")) return action
                return BASE_DOMAIN + action
            }
        }
        return getLoginPostUrl()
    }

    


    @JvmStatic
    fun getUserSpaceUrl(uid: String?): String {
        return BASE_DOMAIN + "home.php?mod=space&uid=" + uid + "&do=profile&mobile=2"
    }

    


    @JvmStatic
    fun getMessageUrl(): String {
        return BASE_DOMAIN + "home.php?mod=space&do=pm"
    }

    





    @JvmStatic
    fun getThreadDetailUrl(tid: String?): String {
        return BASE_DOMAIN + "forum.php?mod=viewthread&tid=" + tid + "&mobile=2"
    }

    @JvmStatic
    fun getThreadDetailUrl(tid: String?, order: String?): String {
        val url = StringBuilder(BASE_DOMAIN)
            .append("forum.php?mod=viewthread&tid=").append(tid)
            .append("&mobile=2")
        if ("desc".equals(order, ignoreCase = true)) {
            url.append("&ordertype=1")
        }
        return url.toString()
    }

    
    @JvmStatic
    fun getThreadDetailUrl(tid: String?, page: Int, order: String?): String {
        val url = StringBuilder(BASE_DOMAIN)
            .append("forum.php?mod=viewthread&tid=").append(tid)
            .append("&page=").append(page)
            .append("&mobile=2")
        if ("desc".equals(order, ignoreCase = true)) {
            url.append("&ordertype=1")
        }
        return url.toString()
    }

    


    @JvmStatic
    fun getThreadDesktopDetailUrl(tid: String?): String {
        return BASE_DOMAIN + "forum.php?mod=viewthread&tid=" + tid
    }

    


    @JvmStatic
    fun getNewThreadUrl(fid: String?): String {
        return BASE_DOMAIN + "forum.php?mod=post&action=newthread&fid=" + fid + "&mobile=2"
    }

    




    @JvmStatic
    fun buildSortQuery(orderby: String?): String {
        if ("replies" == orderby) {
            return "&orderby=replies&ascdesc=desc"
        } else if ("dateline" == orderby) {
            return "&orderby=dateline&ascdesc=desc"
        }
        return "&orderby=lastpost&ascdesc=desc" 
    }

    





    @JvmStatic
    fun getSearchUrl(keyword: String?, page: Int, orderby: String?): String {
        try {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            var url = BASE_DOMAIN + "search.php?mod=forum&searchsubmit=yes&srchtxt=" + encoded +
                    buildSortQuery(orderby) + "&mobile=2"
            if (page > 1) {
                url += "&page=$page"
            }
            return url
        } catch (e: UnsupportedEncodingException) {
            e.printStackTrace()
            var url = BASE_DOMAIN + "search.php?mod=forum&searchsubmit=yes&mobile=2"
            if (page > 1) {
                url += "&page=$page"
            }
            return url
        }
    }

    




    @JvmStatic
    fun getSearchUrl(keyword: String?, page: Int): String {
        return getSearchUrl(keyword, page, "lastpost")
    }

    



    @JvmStatic
    fun parseSearchTotalPages(html: String?): Int {
        if (TextUtils.isEmpty(html)) return 1
        val doc = Jsoup.parse(html!!)
        // 搜索分页链接: <a href="search.php?mod=forum&searchid=XXX&page=N"
        var pageLinks = doc.select("a[href*=searchid]")
        if (pageLinks.isEmpty()) {
            
            pageLinks = doc.select("a[href*=page=]")
        }
        var maxPage = 1
        val pagePtn = Pattern.compile("[&?]page=(\\d+)")
        for (pl in pageLinks) {
            val pm = pagePtn.matcher(pl.attr("href"))
            if (pm.find()) {
                try {
                    val p = pm.group(1).toInt()
                    if (p > maxPage) maxPage = p
                } catch (ignored: Exception) {
                }
            }
        }
        return maxPage
    }

    



    @JvmStatic
    fun extractSearchId(html: String?): String? {
        if (TextUtils.isEmpty(html)) return null
        val m = Pattern.compile("searchid=(\\d+)").matcher(html!!)
        if (m.find()) {
            return m.group(1)
        }
        return null
    }

    








    @JvmStatic
    fun getSearchPageUrl(searchId: String?, page: Int, orderby: String?): String {
        return BASE_DOMAIN + "search.php?mod=forum&searchid=" + searchId +
                buildSortQuery(orderby) + "&searchsubmit=yes&page=" + page + "&mobile=2"
    }

    





    @JvmStatic
    fun getSearchPageUrl(searchId: String?, page: Int): String {
        return getSearchPageUrl(searchId, page, "lastpost")
    }

    



    @JvmStatic
    fun getForumlistMobileUrl(): String {
        return BASE_DOMAIN + "forum.php?forumlist=1&mobile=2"
    }

    






    @JvmStatic
    fun parseCommunityPage(html: String?): CommunityPageData {
        val data = CommunityPageData()
        val doc = Jsoup.parse(html ?: "")

        
        
        val fhInput = doc.select("input[name=formhash]").first()
        if (fhInput != null) {
            val fh = fhInput.attr("value")
            if (!fh.isEmpty()) {
                data.formhash = fh
            }
        }
        
        if (data.formhash == null || data.formhash!!.isEmpty()) {
            val fhMatcher = Pattern.compile("formhash\\s*=\\s*['\"]?([a-f0-9]{8})['\"]?").matcher(html ?: "")
            if (fhMatcher.find()) {
                data.formhash = fhMatcher.group(1)
            }
        }

        
        
        
        

        
        val signBtn = doc.select("a.signBtn").first()
        if (signBtn != null) {
            var signHref = signBtn.attr("href")
            data.signInText = signBtn.text().replace(Regex("[^\\u4e00-\\u9fa5]"), "").trim()
            if (!signHref.isEmpty()) {
                if (!signHref.startsWith("http")) {
                    signHref = BASE_DOMAIN + signHref
                }
                data.signInUrl = signHref
            }
            
            if (signHref.contains("member.php?mod=logging")) {
                data.loginRequired = true
            }
        } else {
            
            val signInLink = doc.select("a:containsOwn(签到)").first()
            if (signInLink != null) {
                val signText = signInLink.text().trim()
                data.signInText = signText.replace(Regex("[^\\u4e00-\\u9fa5]"), "").trim()
                var signHref = signInLink.attr("href")
                if (!signHref.isEmpty()) {
                    if (!signHref.startsWith("http")) {
                        signHref = BASE_DOMAIN + signHref
                    }
                    data.signInUrl = signHref
                }
                if (signHref.contains("member.php?mod=logging")) {
                    data.loginRequired = true
                }
            } else {
                
                val signIcon = doc.select("i:contains(签到), span:contains(签到), em:contains(签到)").first()
                if (signIcon != null) {
                    data.signInText = signIcon.text().replace(Regex("[^\\u4e00-\\u9fa5]"), "").trim()
                }
            }
        }

        
        val signUrlMatcher = Pattern.compile("(plugin\\.php\\?id=k_misign[^'\"\\s]*)").matcher(html ?: "")
        if (signUrlMatcher.find()) {
            var ajaxUrl = signUrlMatcher.group(1)
            if (!ajaxUrl.startsWith("http")) {
                ajaxUrl = BASE_DOMAIN + ajaxUrl
            }
            
            val fh = data.formhash
            if (fh != null && !fh.isEmpty()) {
                ajaxUrl = Regex("formhash=[a-f0-9]*").replace(ajaxUrl, "formhash=" + fh)
            }
            data.signInUrl = ajaxUrl
        }

        
        
        val pageText = if (doc.body() != null) doc.body().text() else ""

        
        val todayPtn = Pattern.compile("今日[：:\\s]*([0-9,，]+)")
        var m = todayPtn.matcher(pageText)
        if (m.find()) {
            data.todayPosts = parseIntFromText(m.group(1))
        }

        
        val yesterdayPtn = Pattern.compile("昨日[：:\\s]*([0-9,，]+)")
        m = yesterdayPtn.matcher(pageText)
        if (m.find()) {
            data.yesterdayPosts = parseIntFromText(m.group(1))
        }

        
        val postsPtn = Pattern.compile("帖子[：:\\s]*([0-9,，]+)")
        m = postsPtn.matcher(pageText)
        if (m.find()) {
            data.totalPosts = parseIntFromText(m.group(1))
        }

        
        val membersPtn = Pattern.compile("会员[：:\\s]*([0-9,，]+)")
        m = membersPtn.matcher(pageText)
        if (m.find()) {
            data.totalMembers = parseIntFromText(m.group(1))
        }

        
        if (data.todayPosts == 0 && data.yesterdayPosts == 0) {
            
            val statsEl = doc.select("div.comiis_stats, div.stats, .forum_stats, .statistic").first()
            if (statsEl != null) {
                val statsText = statsEl.text()
                m = todayPtn.matcher(statsText)
                if (m.find()) data.todayPosts = parseIntFromText(m.group(1))
                m = yesterdayPtn.matcher(statsText)
                if (m.find()) data.yesterdayPosts = parseIntFromText(m.group(1))
                m = postsPtn.matcher(statsText)
                if (m.find()) data.totalPosts = parseIntFromText(m.group(1))
                m = membersPtn.matcher(statsText)
                if (m.find()) data.totalMembers = parseIntFromText(m.group(1))
            }
        }

        
        
        val categories = parseForumCategories(html)
        val allForums = ArrayList<ForumCategory.Forum>()
        for (cat in categories) {
            if (cat.forums != null) {
                for (forum in cat.forums!!) {
                    allForums.add(forum)
                }
            }
        }
        data.forums = allForums
        data.categories = categories

        return data
    }

    







    @JvmStatic
    fun parseDesktopForumStats(html: String?): MutableMap<String, LongArray> {
        val result = HashMap<String, LongArray>()
        if (TextUtils.isEmpty(html)) return result
        try {
            val doc = Jsoup.parse(html!!)
            val dts = doc.select("dl dt")
            val fidPtn = Pattern.compile("forum-(\\d+)-1\\.html")
            for (dt in dts) {
                val a = dt.select("a[href*=forum-]").first() ?: continue
                val m = fidPtn.matcher(a.attr("href"))
                if (!m.find()) continue
                val fid = m.group(1)
                val stats = LongArray(3)
                
                val todayEm = dt.select("em[title=今日]").first()
                if (todayEm != null) {
                    val tText = Regex("[^0-9]").replace(todayEm.text(), "").trim()
                    if (!tText.isEmpty()) stats[2] = tText.toLong()
                }
                
                val dlEl = dt.parent()?.parent()
                var dd: Element? = null
                if (dlEl != null) dd = dlEl.select("dd").first()
                if (dd != null) {
                    val mt = Pattern.compile("主题[::\\s]*([0-9,,]+)").matcher(dd.text())
                    if (mt.find()) stats[0] = parseLongFromText(mt.group(1))
                    
                    val postSpan = dd.select("span[title]").first()
                    if (postSpan != null && !postSpan.attr("title").isEmpty()) {
                        stats[1] = parseLongFromText(postSpan.attr("title"))
                    } else {
                        val mp = Pattern.compile("帖[数子][::\\s]*([0-9,,]+)").matcher(dd.text())
                        if (mp.find()) stats[1] = parseLongFromText(mp.group(1))
                    }
                }
                result[fid] = stats
            }
        } catch (ignored: Exception) {
        }
        return result
    }

    







    @JvmStatic
    fun parseForumHeaderInfo(html: String?): Array<String?> {
        val info = arrayOfNulls<String>(3)
        if (TextUtils.isEmpty(html)) return info
        try {
            val doc = Jsoup.parse(html!!)
            var statsLine = ""

            
            var statsP: Element? = null
            for (el in doc.allElements) {
                if (el.tagName() == "p" && el.hasClass("comiis_tm8")) {
                    val t = el.text()
                    if (t.contains("今日") || t.contains("帖子")) {
                        statsP = el
                        break
                    }
                }
            }

            if (statsP != null) {
                statsLine = statsP.text().trim()
                
                var next = statsP.nextElementSibling()
                while (next != null && next.tagName() != "p") {
                    next = next.nextElementSibling()
                }
                if (next != null) {
                    val d = next.text().trim()
                    if (!d.isEmpty()) info[0] = d
                }
            }

            
            if (info[0] == null || info[0]!!.isEmpty()) {
                val h2 = doc.select("h2.f_f").first()
                val h2Parent = if (h2 != null) h2.parent() else null
                if (h2Parent != null) {
                    val ps = h2Parent.select(":scope > p")
                    for (p in ps) {
                        val t = p.ownText().trim()
                        if (t.isEmpty()) continue
                        if (statsLine.isEmpty() && (t.contains("今日") || t.contains("帖子"))) {
                            statsLine = t
                        } else if (info[0] == null || info[0]!!.isEmpty()) {
                            info[0] = t
                        }
                    }
                }
            }

            
            if (!statsLine.isEmpty()) {
                var m = Pattern.compile("今日[\\s\\u00A0]*([0-9,,]+)").matcher(statsLine)
                if (m.find()) info[1] = parseLongFromText(m.group(1)).toString()
                m = Pattern.compile("帖子[\\s\\u00A0]*([0-9,,]+)").matcher(statsLine)
                if (m.find()) info[2] = parseLongFromText(m.group(1)).toString()
            }
        } catch (ignored: Exception) {
        }
        return info
    }

    


    private fun parseLongFromText(text: String?): Long {
        if (text == null) return 0
        val cleaned = Regex("[^0-9]").replace(text, "")
        if (cleaned.isEmpty()) return 0
        try {
            return cleaned.toLong()
        } catch (e: NumberFormatException) {
            return 0
        }
    }

    


    class CommunityPageData {
        var signInText: String = ""
        var signInUrl: String = ""
        var formhash: String = ""
        var alreadySignedIn: Boolean = false
        var loginRequired: Boolean = false
        var todayPosts: Int = 0
        var yesterdayPosts: Int = 0
        var totalPosts: Int = 0
        var totalMembers: Int = 0
        var forums: MutableList<ForumCategory.Forum> = ArrayList<ForumCategory.Forum>()
        var categories: MutableList<ForumCategory> = ArrayList<ForumCategory>()
    }

    

    
    class SmileySet {
        var name: String = ""
        var items: MutableList<Smiley> = ArrayList<Smiley>()
    }

    
    class Smiley {
        var code: String = ""
        var url: String = ""
    }

    







    @JvmStatic
    fun parseSmileyCatalog(html: String?): MutableList<SmileySet> {
        val result = ArrayList<SmileySet>()
        if (TextUtils.isEmpty(html)) return result
        try {
            val src = html!!
            
            val typeNames = HashMap<String, String>()
            val typeDirs = HashMap<String, String>()
            val tm = Pattern.compile(
                "smilies_type\\s*\\[\\s*['\"]?(\\w+)['\"]?\\s*\\]\\s*=\\s*\\[\\s*'([^']*)'(?:\\s*,\\s*'([^']*)')?"
            ).matcher(src)
            while (tm.find()) {
                val idx = tm.group(1)?.trimStart('_') ?: continue
                typeNames[idx] = tm.group(2) ?: ""
                typeDirs[idx] = tm.group(3) ?: ""
            }
            if (typeNames.isEmpty()) return result
            
            
            val bySet = LinkedHashMap<String, SmileySet>()
            val blockRe = Pattern.compile(
                "smilies_array\\s*\\[\\s*['\"]?(\\w+)['\"]?\\s*\\]\\s*\\[\\s*['\"]?(\\w+)['\"]?\\s*\\]\\s*=\\s*\\[([\\s\\S]*?)\\]\\s*;"
            ).matcher(src)
            while (blockRe.find()) {
                val setId = blockRe.group(1)?.trimStart('_') ?: continue
                val set = bySet.getOrPut(setId) {
                    val s = SmileySet()
                    s.name = typeNames[setId] ?: ""
                    s
                }
                val itemRe = Pattern.compile("\\[\\s*'([^']*)'\\s*,\\s*'([^']*)'\\s*,\\s*'([^']*)'").matcher(blockRe.group(3) ?: "")
                while (itemRe.find()) {
                    val code = itemRe.group(2) ?: ""
                    val image = itemRe.group(3) ?: ""
                    if (code.isEmpty() || image.isEmpty()) continue
                    val smiley = Smiley()
                    smiley.code = code
                    smiley.url = resolveSmileyUrl(image, typeDirs[setId])
                    set.items.add(smiley)
                }
            }
            for (set in bySet.values) {
                if (set.items.isNotEmpty()) result.add(set)
            }
        } catch (ignored: Exception) {
        }
        if (result.isEmpty()) {
            parseSmileyCatalogFromHtml(html, result)
        }
        return result
    }

    
    private fun parseSmileyCatalogFromHtml(html: String?, out: MutableList<SmileySet>) {
        if (TextUtils.isEmpty(html)) return
        try {
            val doc = Jsoup.parse(html!!)
            val imgs = doc.select("img[src*=smiley], img[src*=emoticon]")
            if (imgs.isEmpty()) return
            val set = SmileySet()
            set.name = "表情"
            for (img in imgs) {
                val src = img.attr("src")
                if (TextUtils.isEmpty(src)) continue
                val onclick = img.parent()?.attr("onclick") ?: ""
                val code = extractSmileyCode(onclick + " " + img.attr("alt") + " " + img.attr("data-code"))
                if (TextUtils.isEmpty(code)) continue
                val smiley = Smiley()
                smiley.code = code!!
                smiley.url = resolveSmileyUrl(src, null)
                set.items.add(smiley)
            }
            if (set.items.isNotEmpty()) out.add(set)
        } catch (ignored: Exception) {
        }
    }

    
    private fun extractSmileyCode(text: String?): String? {
        if (TextUtils.isEmpty(text)) return null
        var m = Pattern.compile("\\{:\\d+_\\d+:\\}").matcher(text!!)
        if (m.find()) return m.group()
        m = Pattern.compile("\\[em:\\d+:\\]").matcher(text)
        if (m.find()) return m.group()
        return null
    }

    
    private const val SMILEY_CDN_BASE = "https://cdn.binmt.cc/"

    
    private fun resolveSmileyUrl(image: String, dir: String?): String {
        if (image.startsWith("http://") || image.startsWith("https://")) return image
        if (image.startsWith("/")) return SMILEY_CDN_BASE.trimEnd('/') + image
        val folder = if (TextUtils.isEmpty(dir)) "" else dir!!.trim('/') + "/"
        return SMILEY_CDN_BASE + "static/image/smiley/" + folder + image
    }

    





    @JvmStatic
    fun getSmileyScriptUrlCandidates(): MutableList<String> {
        val list = ArrayList<String>()
        list.add(SMILEY_CDN_BASE + "data/cache/common_smilies_var.js")
        return list
    }

    





    @JvmStatic
    fun extractSmileyScriptUrls(html: String?): MutableList<String> {
        val result = ArrayList<String>()
        if (TextUtils.isEmpty(html)) return result
        try {
            val m = Pattern.compile(
                "<script[^>]+src\\s*=\\s*['\"]([^'\"]+?)(?:\\?[^'\"]*)?['\"]",
                Pattern.CASE_INSENSITIVE
            ).matcher(html!!)
            while (m.find()) {
                val src = m.group(1) ?: continue
                val lower = src.lowercase(Locale.ROOT)
                if (!(lower.contains("smiley") || lower.contains("smilies"))) continue
                val url = when {
                    src.startsWith("http") -> src
                    src.startsWith("/") -> SMILEY_CDN_BASE.trimEnd('/') + src
                    else -> SMILEY_CDN_BASE + src
                }
                if (!result.contains(url)) result.add(url)
            }
        } catch (ignored: Exception) {
        }
        return result
    }

    



    @JvmStatic
    fun parseFavoriteForums(html: String?): MutableList<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        if (TextUtils.isEmpty(html)) return out
        val doc = Jsoup.parse(html!!)
        val fidPattern = Pattern.compile("(?:forum-|fid=)(\\d+)")
        val links = doc.select("a[href*=forum-], a[href*=fid=], a[href*=mod=forumdisplay]")
        val seen = HashSet<String>()
        for (link in links) {
            val href = link.attr("href")
            val m = fidPattern.matcher(href) ?: continue
            if (!m.find()) continue
            val fid = m.group(1) ?: continue
            if (seen.contains(fid)) continue
            val name = link.text().trim()
            if (TextUtils.isEmpty(name)) continue
            seen.add(fid)
            out.add(fid to name)
        }
        return out
    }

}
