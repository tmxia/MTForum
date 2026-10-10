package com.solosu.mtforum.ui.forum

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.bumptech.glide.Glide
import com.solosu.mtforum.R
import com.solosu.mtforum.adapter.ThreadAdapter
import com.solosu.mtforum.databinding.ActivityForumDetailBinding
import com.solosu.mtforum.model.Thread
import com.solosu.mtforum.network.ForumParser
import com.solosu.mtforum.ui.widget.FrostedGlassHelper
import com.solosu.mtforum.network.HttpClient
import com.solosu.mtforum.util.NavigationHelper
import com.solosu.mtforum.ui.space.UserProfileActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout

import java.util.ArrayList
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.drawable.GradientDrawable
import com.solosu.mtforum.util.ToastUtil as Toast





class ForumDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForumDetailBinding
    private lateinit var httpClient: HttpClient
    private lateinit var threadAdapter: ThreadAdapter
    private val allThreads: MutableList<Thread> = ArrayList()

    
    private var fid: String? = null
    private var forumName: String? = null
    private var description: String? = null
    private var iconUrl: String? = null
    private var totalPosts = 0
    private var totalThreads = 0

    private var currentPage = 1
    private var isLoading = false
    private var hasMore = true
    private val pendingBuffer: MutableList<Thread> = ArrayList()

    companion object {
        private const val BATCH_STEP = 10
        private const val PRELOAD_THRESHOLD = 4
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.solosu.mtforum.util.UiStyleManager.applyTheme(this)
        com.solosu.mtforum.util.ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityForumDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        FrostedGlassHelper.applyToCardViews(binding.root, this)

        httpClient = HttpClient.getInstance()

        
        fid = intent.getStringExtra("fid")
        forumName = intent.getStringExtra("forumName")
        description = intent.getStringExtra("description")
        iconUrl = intent.getStringExtra("iconUrl")
        totalPosts = intent.getIntExtra("totalPosts", 0)
        totalThreads = intent.getIntExtra("totalThreads", 0)

        
        binding.tvForumName.text = forumName ?: ""
        binding.tvForumDesc.text = if (description != null && !description!!.isEmpty()) description else "暂无描述"

        
        if (iconUrl != null && !iconUrl!!.isEmpty()) {
            Glide.with(this)
                .load(iconUrl)
                .placeholder(R.drawable.ic_circle)
                .circleCrop()
                .into(binding.ivForumIcon)
        } else {
            binding.ivForumIcon.setImageResource(R.drawable.ic_circle)
            
            if (!fid.isNullOrEmpty()) {
                fetchForumIconFromServer(fid!!)
            }
        }

        
        com.solosu.mtforum.util.ScrollToTopHelper.attachRecyclerView(binding.headerForum, binding.recyclerView)
        
        binding.root.onBack = { finish() }

        
        val layoutManager = LinearLayoutManager(this)
        binding.recyclerView.layoutManager = layoutManager

        threadAdapter = ThreadAdapter(this)
        threadAdapter.setOnItemClickListener(object : ThreadAdapter.OnItemClickListener {
            override fun onItemClick(thread: Thread?, position: Int) {
                NavigationHelper.openThread(this@ForumDetailActivity, thread)
            }
        })
        threadAdapter.setOnUserClickListener(object : ThreadAdapter.OnUserClickListener {
            override fun onUserClick(thread: Thread?) {
                if (thread == null || TextUtils.isEmpty(thread.authorUid)) return
                val intent = Intent(this@ForumDetailActivity, UserProfileActivity::class.java)
                intent.putExtra("uid", thread.authorUid)
                intent.putExtra("username", thread.author)
                startActivity(intent)
            }
        })
        binding.recyclerView.adapter = threadAdapter


        
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                if (dy <= 0) return
                val lm = rv.layoutManager as? LinearLayoutManager ?: return
                val lastVisiblePosition = lm.findLastVisibleItemPosition()
                val totalItemCount = lm.itemCount
                if (totalItemCount > 0 && lastVisiblePosition >= totalItemCount - PRELOAD_THRESHOLD) {
                    checkAndTriggerNextBatch()
                }
            }
        })

        
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                refreshThreads()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
                refreshThreads()
            }
        })

        
        refreshThreads()
    }

    private fun refreshThreads() {
        currentPage = 1
        hasMore = true
        pendingBuffer.clear()
        allThreads.clear()
        threadAdapter.setThreadList(ArrayList())
        fetchNetworkPage(currentPage, true)
    }

    private fun checkAndTriggerNextBatch() {
        if (isLoading) return
        if (pendingBuffer.size >= BATCH_STEP) {
            dispatchNextBatch()
        } else if (hasMore) {
            currentPage++
            fetchNetworkPage(currentPage, false)
        } else if (pendingBuffer.isNotEmpty()) {
            dispatchNextBatch()
        }
    }

    private fun dispatchNextBatch() {
        if (pendingBuffer.isEmpty()) return
        val countToTake = minOf(BATCH_STEP, pendingBuffer.size)
        val batch = ArrayList(pendingBuffer.subList(0, countToTake))
        for (i in 0 until countToTake) {
            pendingBuffer.removeAt(0)
        }
        allThreads.addAll(batch)
        threadAdapter.addThreads(batch)
    }

    






    



    private fun fetchForumIconFromServer(targetFid: String) {
        java.lang.Thread {
            try {
                val html = httpClient.get(ForumParser.getForumlistMobileUrl())
                if (html.isNullOrEmpty()) return@Thread
                val categories = ForumParser.parseForumCategories(html)
                var iconUrl: String? = null
                for (cat in categories) {
                    for (f in cat.forums) {
                        if (f.fid == targetFid) {
                            iconUrl = f.iconUrl
                            break
                        }
                    }
                    if (iconUrl != null) break
                }
                if (!iconUrl.isNullOrEmpty()) {
                    val finalUrl = iconUrl
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        Glide.with(this@ForumDetailActivity)
                            .load(finalUrl)
                            .placeholder(R.drawable.ic_circle)
                            .circleCrop()
                            .into(binding.ivForumIcon)
                    }
                }
            } catch (_: Exception) {
            }
        }.start()
    }

    private fun fetchNetworkPage(page: Int, isRefresh: Boolean) {
        if (isLoading) return
        isLoading = true
        if (isRefresh) {
            binding.tvEmpty.visibility = View.GONE
        }

        val tabPosition = binding.tabLayout.selectedTabPosition

        java.lang.Thread {
            try {
                
                var sortParam = ""
                if (tabPosition == 3) {
                    
                    sortParam = "&filter=digest&digest=1"
                } else if (tabPosition == 1) {
                    
                    sortParam = "&filter=author&orderby=dateline"
                } else if (tabPosition == 2) {
                    
                    sortParam = "&filter=heat&orderby=heats"
                }

                val url = ForumParser.getThreadListUrl(fid + sortParam, page)
                val html = httpClient.get(url)
                val threads: MutableList<Thread> = ForumParser.parseForumThreadList(html)
                
                val black = com.solosu.mtforum.session.BlacklistManager.uidSet(this@ForumDetailActivity)
                if (black.isNotEmpty()) {
                    val it = threads.iterator()
                    while (it.hasNext()) {
                        val t = it.next()
                        if (t != null && t.authorUid != null && black.contains(t.authorUid)) it.remove()
                    }
                }
                val resultThreads = threads

                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    isLoading = false

                    if (resultThreads.isEmpty()) {
                        hasMore = false
                        if (pendingBuffer.isNotEmpty()) {
                            dispatchNextBatch()
                        } else if (isRefresh && allThreads.isEmpty()) {
                            threadAdapter.setThreadList(ArrayList())
                            binding.tvEmpty.setText(R.string.forum_empty)
                            binding.tvEmpty.visibility = View.VISIBLE
                        }
                        return@runOnUiThread
                    }

                    
                    hasMore = true
                    pendingBuffer.addAll(resultThreads)

                    if (isRefresh) {
                        val countToTake = minOf(BATCH_STEP, pendingBuffer.size)
                        val firstBatch = ArrayList(pendingBuffer.subList(0, countToTake))
                        for (i in 0 until countToTake) {
                            pendingBuffer.removeAt(0)
                        }
                        allThreads.clear()
                        allThreads.addAll(firstBatch)
                        threadAdapter.setThreadList(firstBatch)
                        binding.tvEmpty.visibility = View.GONE
                    } else {
                        dispatchNextBatch()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    isLoading = false
                    if (isRefresh && allThreads.isEmpty() && pendingBuffer.isEmpty()) {
                        binding.tvEmpty.setText(R.string.forum_empty)
                        binding.tvEmpty.visibility = View.VISIBLE
                    }
                }
            }
        }.start()
    }
}
