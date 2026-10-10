package com.solosu.mtforum.ui.space

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.solosu.mtforum.R
import com.solosu.mtforum.adapter.ThreadAdapter
import com.solosu.mtforum.model.Thread
import com.solosu.mtforum.network.ForumParser
import com.solosu.mtforum.network.HttpClient
import com.solosu.mtforum.session.BrowseHistoryManager
import com.solosu.mtforum.session.FavoriteEnrichCache
import com.solosu.mtforum.util.NavigationHelper
import com.solosu.mtforum.util.ToastUtil as Toast

class BrowseHistoryActivity : AppCompatActivity() {

    private lateinit var adapter: ThreadAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView

    
    private val enrichedTids: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val enrichQueue: ConcurrentLinkedQueue<Pair<Int, Thread>> = ConcurrentLinkedQueue()
    private val enrichRunning = AtomicBoolean(false)
    private val enrichHandler = Handler(Looper.getMainLooper())
    private var lastEnrichAt = 0L
    private var enrichCount = 0
    private val MAX_ENRICH_PER_SESSION = 30
    private val ENRICH_INTERVAL_MS = 1200L

    override fun onCreate(savedInstanceState: Bundle?) {
        com.solosu.mtforum.util.UiStyleManager.applyTheme(this)
        com.solosu.mtforum.util.ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browse_history)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = "浏览历史"
        toolbar.setNavigationIcon(R.drawable.ic_back)
        toolbar.setNavigationOnClickListener { finish() }

        recyclerView = findViewById(R.id.recycler_view)
        tvEmpty = findViewById(R.id.tv_empty)

        adapter = ThreadAdapter(this)
        adapter.setOnItemClickListener(object : ThreadAdapter.OnItemClickListener {
            override fun onItemClick(thread: Thread?, position: Int) {
                if (thread?.tid != null) {
                    NavigationHelper.openThread(this@BrowseHistoryActivity, thread.tid)
                }
            }
        })
        adapter.setOnUserClickListener(object : ThreadAdapter.OnUserClickListener {
            override fun onUserClick(thread: Thread?) {
                if (thread == null || thread.authorUid.isNullOrEmpty()) return
                val intent = android.content.Intent(this@BrowseHistoryActivity, UserProfileActivity::class.java)
                intent.putExtra("uid", thread.authorUid)
                intent.putExtra("username", thread.author)
                startActivity(intent)
            }
        })

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        
        val helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) {
                val position = holder.bindingAdapterPosition
                if (position < 0) return
                val thread = adapter.getItem(position) ?: return
                BrowseHistoryManager.remove(this@BrowseHistoryActivity, thread.tid)
                Toast.makeText(this@BrowseHistoryActivity, "已移出", Toast.LENGTH_SHORT).show()
                render()
            }
        })
        helper.attachToRecyclerView(recyclerView)

        render()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "清空").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("清空浏览历史")
                .setMessage("确定清空全部浏览历史？")
                .setPositiveButton("清空") { _, _ ->
                    BrowseHistoryManager.clear(this)
                    Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show()
                    render()
                }
                .setNegativeButton("取消", null)
                .show()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun render() {
        val history = BrowseHistoryManager.list(this)
        val threads = ArrayList<Thread>()
        for (it in history) {
            val t = Thread().apply {
                tid = it.tid
                title = it.title
                author = it.author
                forumName = it.forumName
            }
            threads.add(t)
        }
        adapter.setThreadList(threads)
        if (threads.isEmpty()) {
            tvEmpty.visibility = android.view.View.VISIBLE
            recyclerView.visibility = android.view.View.GONE
        } else {
            tvEmpty.visibility = android.view.View.GONE
            recyclerView.visibility = android.view.View.VISIBLE
            startLazyEnrich()
        }
    }

    
    private fun startLazyEnrich() {
        recyclerView.clearOnScrollListeners()
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(r: RecyclerView, dx: Int, dy: Int) {
                enqueueVisibleForEnrich()
            }
        })
        recyclerView.post { enqueueVisibleForEnrich() }
    }

    private fun enqueueVisibleForEnrich() {
        if (enrichCount >= MAX_ENRICH_PER_SESSION) return
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val first = lm.findFirstVisibleItemPosition()
        val last = lm.findLastVisibleItemPosition()
        if (first < 0 || last < 0) return
        for (i in first..last) {
            if (enrichCount + enrichQueue.size >= MAX_ENRICH_PER_SESSION) break
            val t = adapter.getItem(i) ?: continue
            val tid = t.tid ?: continue
            if (!enrichedTids.add(tid)) continue
            
            val cached = FavoriteEnrichCache.get(this, tid)
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
                    val enriched = enrichThread(next.second)
                    if (enriched.tid == next.second.tid && enriched !== next.second) {
                        FavoriteEnrichCache.put(this, enriched)
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

    
    private fun enrichThread(basic: Thread): Thread {
        return try {
            val httpClient = HttpClient.getInstance()
            val detailHtml = httpClient.get(ForumParser.getThreadDetailUrl(basic.tid))
            if (TextUtils.isEmpty(detailHtml) || ForumParser.isLoginPage(detailHtml)) return basic
            val detail = ForumParser.parseThreadDetail(detailHtml) ?: return basic
            Thread().apply {
                tid = basic.tid
                title = if (!TextUtils.isEmpty(detail.title)) detail.title else basic.title
                author = detail.author
                authorUid = detail.authorUid
                authorLevel = detail.authorLevel
                avatarUrl = detail.avatarUrl
                forumName = detail.forumName
                forumFid = detail.forumFid
                publishTime = detail.publishTime
                replies = detail.replyCount
                likes = detail.likeCount
                val imgs = detail.imageUrls
                if (imgs != null) imageUrls = imgs
                hasImage = imgs != null && imgs.isNotEmpty()
                hasHiddenContent = detail.hasHiddenContent
            }
        } catch (_: Exception) {
            basic
        }
    }
}
