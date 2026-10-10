package com.solosu.mtforum.ui.space

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import com.solosu.mtforum.util.ToastUtil as Toast

import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.solosu.mtforum.R
import com.solosu.mtforum.adapter.ThreadAdapter
import com.solosu.mtforum.databinding.ActivitySpaceThreadListBinding
import com.solosu.mtforum.model.Thread
import com.solosu.mtforum.network.ForumParser
import com.solosu.mtforum.network.HttpClient
import com.solosu.mtforum.ui.detail.ThreadDetailActivity

import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet
import java.util.Set
import com.solosu.mtforum.ui.widget.DialogHelper





class SpaceThreadListActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySpaceThreadListBinding
    private lateinit var httpClient: HttpClient

    
    private val enrichedTids: MutableSet<String> =
        java.util.concurrent.ConcurrentHashMap.newKeySet()
    private val enrichQueue: java.util.concurrent.ConcurrentLinkedQueue<Pair<Int, com.solosu.mtforum.model.Thread>> =
        java.util.concurrent.ConcurrentLinkedQueue()
    private val enrichRunning = java.util.concurrent.atomic.AtomicBoolean(false)
    private val enrichHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var lastEnrichAt = 0L
    private var enrichCount = 0
    private val MAX_ENRICH_PER_SESSION = 20
    private val ENRICH_INTERVAL_MS = 1500L
    private lateinit var adapter: ThreadAdapter
    private var mode: String? = null
    private lateinit var listUrl: String
    private var targetUid: String? = null
    private var currentFormhash: String? = null

    private var initialLoadFinished = false

    private val removedFavoriteKeys: MutableSet<String>
        get() {
            val prefs = getSharedPreferences(FAVORITE_STATE_PREFS, MODE_PRIVATE)
            return HashSet(prefs.getStringSet(KEY_REMOVED_FAVORITES, HashSet())!!)
        }

    private fun favoriteLocalKeys(thread: Thread?): MutableSet<String> {
        val keys = HashSet<String>()
        if (thread == null) return keys
        if (!TextUtils.isEmpty(thread.favid)) keys.add("favid:" + thread.favid)
        if (!TextUtils.isEmpty(thread.tid)) keys.add("tid:" + thread.tid)
        return keys
    }

    private fun favoriteLocalKey(thread: Thread?): String {
        if (thread == null) return ""
        
        if (!TextUtils.isEmpty(thread.tid)) return "tid:" + thread.tid
        if (!TextUtils.isEmpty(thread.favid)) return "favid:" + thread.favid
        return ""
    }

    private fun sharesFavoriteKey(first: Thread?, second: Thread?): Boolean {
        if (first == null || second == null) return false
        val firstKeys = favoriteLocalKeys(first)
        firstKeys.retainAll(favoriteLocalKeys(second))
        return firstKeys.isNotEmpty()
    }

    private fun rememberRemovedFavorite(thread: Thread) {
        val key = favoriteLocalKey(thread)
        if (TextUtils.isEmpty(key)) return
        val prefs = getSharedPreferences(FAVORITE_STATE_PREFS, MODE_PRIVATE)
        val keys = removedFavoriteKeys
        keys.addAll(favoriteLocalKeys(thread))
        prefs.edit().putStringSet(KEY_REMOVED_FAVORITES, keys).apply()
    }

    private fun filterRemovedFavorites(source: MutableList<Thread>?): MutableList<Thread> {
        if (source == null || source.isEmpty()) return ArrayList()
        val removed = removedFavoriteKeys
        val result = ArrayList<Thread>()
        for (thread in source) {
            var removedItem = false
            for (key in favoriteLocalKeys(thread)) {
                if (removed.contains(key)) {
                    removedItem = true
                    break
                }
            }
            if (!removedItem) result.add(thread)
        }
        return result
    }

    private fun isFavoriteDeleteResponseSuccessful(result: String?): Boolean {
        if (TextUtils.isEmpty(result) || ForumParser.isLoginPage(result)) return false
        val text = result!!.trim().lowercase(java.util.Locale.ROOT)
        
        
        if (text.contains("formhash错误") || text.contains("请先登录")
            || text.contains("没有权限") || text.contains("操作失败")
            || text.contains("删除失败") || text.contains("ajaxerror")
            || text.contains("error") || text.contains("非法操作")
        ) return false
        return text.contains("\"success\":true")
                || text.contains("\"status\":1")
                || text.contains("\"code\":0")
                || text.contains("success=1")
                || text.contains("succeed")
                || text.contains("删除成功")
                || text.contains("取消收藏成功")
    }

    private fun containsFavorite(list: MutableList<Thread>?, target: Thread?): Boolean {
        if (list == null || target == null) return false
        val key = favoriteLocalKey(target)
        for (item in list) {
            if (!TextUtils.isEmpty(key) && key == favoriteLocalKey(item)) return true
            
            if (!TextUtils.isEmpty(target.tid) && target.tid == item.tid) return true
        }
        return false
    }

    




    private fun loadFavoriteThreads(favoriteHtml: String?): MutableList<Thread> {
        
        val basicList = ForumParser.parseFavoriteList(favoriteHtml)
        if (basicList == null || basicList.isEmpty()) return ArrayList()

        val result = ArrayList<Thread>()
        for (basic in basicList) {
            if (basic == null || TextUtils.isEmpty(basic.tid)) continue
            
            result.add(basic)
        }
        return result
    }

    




    private fun loadFavoriteThreadsProgressive(
        favoriteHtml: String?,
        onBasicReady: (List<Thread>) -> Unit,
        onItemEnriched: (index: Int, thread: Thread) -> Unit
    ) {
        val basicList = ForumParser.parseFavoriteList(favoriteHtml)
        if (basicList == null || basicList.isEmpty()) {
            onBasicReady(emptyList())
            return
        }

        
        val validList = ArrayList<Thread>()
        for (b in basicList) {
            if (b != null && !TextUtils.isEmpty(b.tid)) validList.add(b)
        }
        onBasicReady(validList)
        if (validList.isEmpty()) return
        
    }

    
    private fun startLazyEnrich() {
        val rv = binding.recyclerView
        rv.clearOnScrollListeners()
        rv.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(r: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                enqueueVisibleForEnrich(r)
            }
        })
        
        rv.post { enqueueVisibleForEnrich(rv) }
    }

    private fun enqueueVisibleForEnrich(rv: androidx.recyclerview.widget.RecyclerView) {
        if (enrichCount >= MAX_ENRICH_PER_SESSION) return
        val lm = rv.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager ?: return
        val first = lm.findFirstVisibleItemPosition()
        val last = lm.findLastVisibleItemPosition()
        if (first < 0 || last < 0) return
        for (i in first..last) {
            if (enrichCount + enrichQueue.size >= MAX_ENRICH_PER_SESSION) break
            val t = adapter.getItem(i) ?: continue
            val tid = t.tid ?: continue
            if (!enrichedTids.add(tid)) continue
            
            val cached = com.solosu.mtforum.session.FavoriteEnrichCache.get(this, tid)
            if (cached != null) {
                adapter.updateItem(i, cached)
                continue
            }
            enrichQueue.offer(i to t)
        }
        drainEnrichQueue()
    }

    private fun drainEnrichQueue() {
        if (!enrichRunning.compareAndSet(false, true)) return
        val next = enrichQueue.poll()
        if (next == null) {
            enrichRunning.set(false)
            return
        }
        val now = System.currentTimeMillis()
        val wait = if (now - lastEnrichAt >= ENRICH_INTERVAL_MS) 0L
                   else ENRICH_INTERVAL_MS - (now - lastEnrichAt)
        enrichHandler.postDelayed({
            Thread {
                try {
                    val enriched = enrichFavoriteThread(next.second)
                    if (enriched !== next.second && enriched.tid == next.second.tid) {
                        
                        com.solosu.mtforum.session.FavoriteEnrichCache.put(
                            this@SpaceThreadListActivity, enriched)
                        runOnUiThread {
                            
                            var pos = next.first
                            if (pos >= adapter.itemCount || adapter.getItem(pos)?.tid != enriched.tid) {
                                for (k in 0 until adapter.itemCount) {
                                    if (adapter.getItem(k)?.tid == enriched.tid) { pos = k; break }
                                }
                            }
                            if (pos in 0 until adapter.itemCount
                                && adapter.getItem(pos)?.tid == enriched.tid) {
                                adapter.updateItem(pos, enriched)
                            }
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    lastEnrichAt = System.currentTimeMillis()
                    enrichCount++
                    enrichRunning.set(false)
                    enrichHandler.post { drainEnrichQueue() }
                }
            }.start()
        }, wait)
    }

    private fun enrichFavoriteThread(basic: Thread): Thread {
        try {
            val detailHtml = httpClient.get(ForumParser.getThreadDetailUrl(basic.tid))
            if (TextUtils.isEmpty(detailHtml) || ForumParser.isLoginPage(detailHtml)) return basic

            val detail = ForumParser.parseThreadDetail(detailHtml) ?: return basic

            val thread = Thread()
            thread.tid = basic.tid
            thread.favid = basic.favid
            thread.title = if (!TextUtils.isEmpty(detail.title)) detail.title else basic.title
            thread.author = detail.author
            thread.authorUid = detail.authorUid
            thread.authorLevel = detail.authorLevel
            thread.avatarUrl = detail.avatarUrl
            thread.forumName = detail.forumName
            thread.forumFid = detail.forumFid
            thread.publishTime = detail.publishTime
            thread.replies = detail.replyCount
            thread.likes = detail.likeCount
            val imgs = detail.imageUrls
            if (imgs != null) thread.imageUrls = imgs
            thread.hasImage = imgs != null && imgs.isNotEmpty()
            thread.hasHiddenContent = detail.hasHiddenContent
            return thread
        } catch (ignored: Exception) {
            return basic
        }
    }

    @Throws(Exception::class)
    private fun fetchLatestFavorites(): MutableList<Thread> {
        val verifyUrl = listUrl + (if (listUrl.contains("?")) "&" else "?") +
                "_refresh=" + System.currentTimeMillis()
        val verifyHtml = httpClient.get(verifyUrl)
        return ForumParser.parseFavoriteList(verifyHtml)
    }

    



    private fun resolveFavoriteId(target: Thread?, html: String?): String {
        if (target == null || TextUtils.isEmpty(html)) return ""
        val parsed = ForumParser.parseFavoriteList(html)
        if (parsed != null) {
            for (item in parsed) {
                if (item != null && !TextUtils.isEmpty(target.tid)
                    && target.tid == item.tid
                    && !TextUtils.isEmpty(item.favid)
                ) {
                    return item.favid!!
                }
            }
        }
        return target.favid ?: ""
    }

    




    private fun extractFavoriteFormInfo(html: String?): Array<String?>? {
        if (TextUtils.isEmpty(html)) return null
        try {
            val doc = org.jsoup.Jsoup.parse(html!!)
            
            val form = doc.select("form[id*=favoriteform]").first()
            if (form != null) {
                
                val action = form.attr("action")
                var favid = ""
                val fm = java.util.regex.Pattern.compile("[?&]favid=(\\d+)").matcher(action)
                if (fm.find()) favid = fm.group(1)
                
                val fhInput = form.select("input[name=formhash]").first()
                val formhash = if (fhInput != null) fhInput.attr("value") else ""
                return arrayOf(favid, formhash)
            }
        } catch (ignored: Exception) {
        }
        return null
    }

    private fun requestDeleteFavorite(target: Thread): Boolean {
        try {
            
            if (!httpClient.isLoggedIn()) httpClient.syncFromCookieManager()
            val page = httpClient.get(listUrl + "&_refresh=" + System.currentTimeMillis())

            
            var formhash: String?
            val favid: String

            
            val formInfo = extractFavoriteFormInfo(page)
            if (formInfo != null && !TextUtils.isEmpty(formInfo[0]) && !TextUtils.isEmpty(formInfo[1])) {
                favid = formInfo[0]!!
                formhash = formInfo[1]
            } else {
                
                formhash = ForumParser.parseFormhash(page)
                if (TextUtils.isEmpty(formhash)) {
                    
                    formhash = ForumParser.parseFormhash(
                        httpClient.get(
                            HttpClient.BASE_URL + "forum.php?mobile=2&_refresh=" +
                                    System.currentTimeMillis()
                        )
                    )
                }
                
                if (TextUtils.isEmpty(formhash) && !TextUtils.isEmpty(target.tid)) {
                    val detailHtml = httpClient.getDesktop(
                        HttpClient.BASE_URL +
                                "forum.php?mod=viewthread&tid=" + target.tid
                    )
                    formhash = ForumParser.parseFormhash(detailHtml)
                }
                favid = resolveFavoriteId(target, page)
            }
            currentFormhash = formhash
            if (TextUtils.isEmpty(favid)) return false

            
            
            
            
            
            
            val deleteUrl = HttpClient.BASE_URL + "home.php?mod=spacecp&ac=favorite&op=delete" +
                    "&favid=" + favid + "&type=all&mobile=2"

            val params = HashMap<String, String>()
            params["referer"] = listUrl
            params["deletesubmit"] = "true"
            if (!TextUtils.isEmpty(formhash)) params["formhash"] = formhash!!
            params["handlekey"] = "comiis"

            val postResult = httpClient.post(deleteUrl, params)
            var latest = fetchLatestFavorites()
            if (!containsFavorite(latest, target)) return true
            if (isFavoriteDeleteResponseSuccessful(postResult)) return true

            
            val getUrl = deleteUrl + "&formhash=" + (if (formhash == null) "" else formhash) + "&inajax=1"
            val getResult = httpClient.get(getUrl)
            latest = fetchLatestFavorites()
            return !containsFavorite(latest, target) ||
                    isFavoriteDeleteResponseSuccessful(getResult)
        } catch (ignored: Exception) {
            return false
        }
    }

    override fun onResume() {
        super.onResume()
        
        if (initialLoadFinished && "favorites" == mode) {
            loadData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.solosu.mtforum.util.UiStyleManager.applyTheme(this)
        com.solosu.mtforum.util.ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivitySpaceThreadListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        httpClient = HttpClient.getInstance()

        mode = intent.getStringExtra("mode")
        if (mode == null) mode = "my_threads"
        targetUid = intent.getStringExtra("uid")

        val title: String
        when (mode) {
            "favorites" -> {
                title = "我的收藏"
                listUrl = HttpClient.BASE_URL + "home.php?mod=space&do=favorite&mobile=2"
            }

            "uid_threads" -> {
                val username = intent.getStringExtra("username")
                title = if (username == null || username.isEmpty()) "用户的帖子" else username + "的帖子"
                listUrl = if (targetUid == null || targetUid!!.isEmpty()) {
                    HttpClient.BASE_URL + "home.php?mod=space&do=thread&view=me&mobile=2"
                } else {
                    HttpClient.BASE_URL + "home.php?mod=space&uid=" + targetUid +
                            "&do=thread&view=me&mobile=2"
                }
            }

            "my_replies" -> {
                title = "我的回复"
                listUrl = HttpClient.BASE_URL + "home.php?mod=space&do=thread&view=me&mobile=2&type=reply"
            }

            else -> {
                title = "我的帖子"
                listUrl = HttpClient.BASE_URL + "home.php?mod=space&do=thread&view=me&mobile=2"
            }
        }

        binding.toolbar.title = title
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_left)
        binding.toolbar.setNavigationOnClickListener { finish() }
        com.solosu.mtforum.util.ScrollToTopHelper.attachRecyclerView(binding.toolbar, binding.recyclerView)

        adapter = ThreadAdapter(this)
        adapter.setOnItemClickListener(object : ThreadAdapter.OnItemClickListener {
            override fun onItemClick(thread: Thread?, position: Int) {
                if (thread != null && thread.tid != null) {
                    val intent = Intent(this@SpaceThreadListActivity, ThreadDetailActivity::class.java)
                    intent.putExtra("tid", thread.tid)
                    startActivity(intent)
                }
            }
        })
        adapter.setOnUserClickListener(object : ThreadAdapter.OnUserClickListener {
            override fun onUserClick(thread: Thread?) {
                if (thread == null || TextUtils.isEmpty(thread.authorUid)) return
                val intent = Intent(this@SpaceThreadListActivity, UserProfileActivity::class.java)
                intent.putExtra("uid", thread.authorUid)
                intent.putExtra("username", thread.author)
                startActivity(intent)
            }
        })
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        if ("favorites" == mode) {
            val helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    return false
                }

                override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) {
                    val position = holder.bindingAdapterPosition
                    val thread = adapter.getItem(position)
                    if (thread == null) return
                    confirmDeleteFavorite(thread, position)
                }
            })
            helper.attachToRecyclerView(binding.recyclerView)
            binding.recyclerView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
                private val detector = android.view.GestureDetector(
                    this@SpaceThreadListActivity,
                    object : android.view.GestureDetector.SimpleOnGestureListener() {
                        override fun onLongPress(e: MotionEvent) {
                            val child = binding.recyclerView.findChildViewUnder(e.x, e.y)
                            if (child == null) return
                            val position = binding.recyclerView.getChildAdapterPosition(child)
                            val thread = adapter.getItem(position)
                            if (thread != null) confirmDeleteFavorite(thread, position)
                        }

                        override fun onDown(e: MotionEvent): Boolean {
                            return true
                        }
                    }
                )

                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    detector.onTouchEvent(e)
                    return false
                }
            })
        }

        binding.swipeRefresh.setOnRefreshListener {
            enrichedTids.clear()
            enrichQueue.clear()
            enrichCount = 0
            lastEnrichAt = 0L
            loadData()
        }
        binding.swipeRefresh.setColorSchemeColors(com.solosu.mtforum.util.ThemeManager.getThemeColor(this))

        loadData()
    }

    private fun confirmDeleteFavorite(thread: Thread, position: Int) {
        val alertDialog: android.app.Dialog = AlertDialog.Builder(this)
            .setTitle("删除收藏")
            .setMessage("确定取消收藏“" + (if (thread.title == null) "此帖子" else thread.title) + "”吗？")
            .setNegativeButton("取消", null)
            .show()
        DialogHelper.applyToAlertDialog(alertDialog, this)
    }

    private fun deleteFavorite(thread: Thread?, position: Int) {
        if (thread == null) {
            Toast.makeText(this, "收藏项为空，无法删除", Toast.LENGTH_SHORT).show()
            return
        }
        if (TextUtils.isEmpty(thread.favid) && TextUtils.isEmpty(thread.tid)) {
            Toast.makeText(this, "未获取到收藏记录ID或帖子ID，无法删除", Toast.LENGTH_SHORT).show()
            return
        }
        binding.progressBar.visibility = View.VISIBLE
        java.lang.Thread {
            val success = requestDeleteFavorite(thread)
            val message = if (success) "已取消收藏" else "删除失败"
            val ok = success
            val msg = message
            runOnUiThread {
                binding.progressBar.visibility = View.GONE
                if (ok) {
                    rememberRemovedFavorite(thread)
                    adapter.removeItem(position)
                    if (adapter.itemCount == 0) {
                        binding.recyclerView.visibility = View.GONE
                        binding.tvEmpty.visibility = View.VISIBLE
                    }
                } else {
                    adapter.notifyItemChanged(position)
                }
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun loadData() {
        binding.progressBar.visibility = View.VISIBLE
        binding.swipeRefresh.isEnabled = false

        java.lang.Thread {
            try {
                httpClient.syncFromCookieManager()
                val html = httpClient.get(listUrl)

                
                if (ForumParser.isLoginPage(html)) {
                    runOnUiThread {
                        binding.progressBar.visibility = View.GONE
                        binding.swipeRefresh.isRefreshing = false
                        binding.swipeRefresh.isEnabled = true
                        Toast.makeText(this, "登录已过期，请重新登录", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    return@Thread
                }

                if ("favorites" == mode) {
                    
                    loadFavoriteThreadsProgressive(html,
                        onBasicReady = { basicList ->
                            runOnUiThread {
                                binding.progressBar.visibility = View.GONE
                                binding.swipeRefresh.isRefreshing = false
                                binding.swipeRefresh.isEnabled = true
                                if (basicList.isEmpty()) {
                                    binding.recyclerView.visibility = View.GONE
                                    binding.tvEmpty.visibility = View.VISIBLE
                                } else {
                                    binding.recyclerView.visibility = View.VISIBLE
                                    binding.tvEmpty.visibility = View.GONE
                                    adapter.setThreadList(ArrayList(basicList))
                                    
                                    startLazyEnrich()
                                }
                                initialLoadFinished = true
                            }
                        },
                        onItemEnriched = { index, thread ->
                            adapter.updateItem(index, thread)
                        }
                    )
                    return@Thread
                }

                val items = ArrayList<Thread>()
                val threadList = ForumParser.parseForumThreadList(html)
                if (threadList != null) items.addAll(threadList)

                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    binding.swipeRefresh.isEnabled = true

                    if (items.isEmpty()) {
                        binding.recyclerView.visibility = View.GONE
                        binding.tvEmpty.visibility = View.VISIBLE
                    } else {
                        binding.recyclerView.visibility = View.VISIBLE
                        binding.tvEmpty.visibility = View.GONE
                        adapter.setThreadList(items)
                    }
                    initialLoadFinished = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    binding.swipeRefresh.isEnabled = true
                    Toast.makeText(this, "加载失败: " + e.message, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    companion object {
        private const val FAVORITE_STATE_PREFS = "favorite_local_state"
        private const val KEY_REMOVED_FAVORITES = "removed_favorite_keys"
    }
}
