package com.solosu.mtforum.ui.search

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.solosu.mtforum.R
import com.solosu.mtforum.adapter.ThreadAdapter
import com.solosu.mtforum.model.Thread
import com.solosu.mtforum.network.ForumParser
import com.solosu.mtforum.network.HttpClient
import com.solosu.mtforum.ui.detail.ThreadDetailActivity
import com.solosu.mtforum.ui.space.UserProfileActivity
import com.solosu.mtforum.util.SearchHistory
import com.solosu.mtforum.util.ThemeManager

import java.util.ArrayList
import java.util.HashSet




class SearchActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var etSearch: EditText
    private lateinit var ivClear: ImageView
    private lateinit var btnSearch: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var ivEmptyIcon: ImageView
    private lateinit var tvEmpty: TextView
    private lateinit var tvError: TextView
    private lateinit var threadAdapter: ThreadAdapter
    private lateinit var layoutHistory: LinearLayout
    private lateinit var rvHistory: RecyclerView
    private lateinit var ivClearAllHistory: ImageView
    private lateinit var historyAdapter: HistoryAdapter

    
    private lateinit var layoutSortBar: LinearLayout
    private lateinit var vSortDivider: View
    private lateinit var btnSortLastpost: TextView
    private lateinit var btnSortDateline: TextView
    private lateinit var btnSortReplies: TextView
    private var currentSortBy = "lastpost" 
    private var pendingKeyword: String? = null
    private val pendingBuffer: MutableList<Thread> = ArrayList()
    private val displayedResults: MutableList<Thread> = ArrayList()

    companion object {
        private const val BATCH_STEP = 10
        private const val PRELOAD_THRESHOLD = 4
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.solosu.mtforum.util.UiStyleManager.applyTheme(this)
        com.solosu.mtforum.util.ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        
        setupWhiteStatusBar()

        toolbar = findViewById(R.id.toolbar)
        etSearch = findViewById(R.id.et_search)
        ivClear = findViewById(R.id.iv_clear)
        btnSearch = findViewById(R.id.btn_search)
        progressBar = findViewById(R.id.progress_bar)
        recyclerView = findViewById(R.id.recycler_view)
        layoutEmptyState = findViewById(R.id.layout_empty_state)
        ivEmptyIcon = findViewById(R.id.iv_empty_icon)
        tvEmpty = findViewById(R.id.tv_empty)
        tvError = findViewById(R.id.tv_error)
        layoutSortBar = findViewById(R.id.layout_sort_bar)
        vSortDivider = findViewById(R.id.v_sort_divider)
        btnSortLastpost = findViewById(R.id.btn_sort_lastpost)
        btnSortDateline = findViewById(R.id.btn_sort_dateline)
        btnSortReplies = findViewById(R.id.btn_sort_replies)
        layoutHistory = findViewById(R.id.layout_history)
        rvHistory = findViewById(R.id.rv_history)
        ivClearAllHistory = findViewById(R.id.iv_clear_all_history)

        
        applySearchButtonTheme()

        
        setSupportActionBar(toolbar)
        if (supportActionBar != null) {
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            supportActionBar!!.setDisplayShowHomeEnabled(true)
        }
        toolbar.setNavigationIcon(R.drawable.ic_arrow_left)
        toolbar.setNavigationOnClickListener { finish() }

        
        com.solosu.mtforum.util.ScrollToTopHelper.attachRecyclerView(toolbar, recyclerView)

        
        ivClear.setOnClickListener {
            etSearch.text?.clear()
            etSearch.requestFocus()
            showKeyboard(etSearch)
        }

        
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                ivClear.visibility = if (!s.isNullOrEmpty()) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        
        historyAdapter = HistoryAdapter(
            onItemClick = { kw ->
                etSearch.setText(kw)
                etSearch.setSelection(kw.length)
                hideKeyboard()
                performSearch()
            },
            onItemDelete = { kw ->
                SearchHistory.remove(this, kw)
                refreshHistoryView()
            }
        )
        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = historyAdapter

        ivClearAllHistory.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setMessage(R.string.search_history_confirm_clear)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    SearchHistory.clear(this)
                    refreshHistoryView()
                }
                .show()
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.isNullOrEmpty()) refreshHistoryView()
                else layoutHistory.visibility = View.GONE
            }
        })

        
        etSearch.postDelayed({
            if (!isFinishing && !isDestroyed) {
                etSearch.requestFocus()
                showKeyboard(etSearch)
            }
        }, 200)

        
        threadAdapter = ThreadAdapter(this)
        threadAdapter.setOnItemClickListener(object : ThreadAdapter.OnItemClickListener {
            override fun onItemClick(thread: Thread?, position: Int) {
                val intent = Intent(this@SearchActivity, ThreadDetailActivity::class.java)
                intent.putExtra("tid", thread!!.tid)
                startActivity(intent)
            }
        })
        threadAdapter.setOnUserClickListener(object : ThreadAdapter.OnUserClickListener {
            override fun onUserClick(thread: Thread?) {
                if (thread == null || TextUtils.isEmpty(thread.authorUid)) return
                val intent = Intent(this@SearchActivity, UserProfileActivity::class.java)
                intent.putExtra("uid", thread.authorUid)
                intent.putExtra("username", thread.author)
                startActivity(intent)
            }
        })
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = threadAdapter

        
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                if (dy <= 0) return
                val lm = rv.layoutManager as? LinearLayoutManager ?: return
                val lastVisiblePosition = lm.findLastVisibleItemPosition()
                val totalItemCount = lm.itemCount
                if (totalItemCount > 0 && lastVisiblePosition >= totalItemCount - PRELOAD_THRESHOLD) {
                    dispatchNextSearchBatch()
                }
            }
        })

        
        btnSearch.setOnClickListener {
            hideKeyboard()
            performSearch()
        }

        
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                performSearch()
                return@setOnEditorActionListener true
            }
            false
        }

        
        btnSortLastpost.setOnClickListener { switchSort("lastpost") }
        btnSortDateline.setOnClickListener { switchSort("dateline") }
        btnSortReplies.setOnClickListener { switchSort("replies") }

        
        val keyword = intent.getStringExtra("keyword")
        if (!TextUtils.isEmpty(keyword)) {
            etSearch.setText(keyword)
            performSearch()
        } else {
            refreshHistoryView()
        }
    }

    private fun setupWhiteStatusBar() {
        val topBarColor = ContextCompat.getColor(this, R.color.top_bar)
        window.statusBarColor = topBarColor
        WindowInsetsControllerCompat(window, window.decorView).apply {
            val isDark = ThemeManager.isDarkMode(this@SearchActivity)
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
    }

    private fun applySearchButtonTheme() {
        val themeColor = ThemeManager.getThemeColor(this)
        val bg = GradientDrawable().apply {
            cornerRadius = (resources.displayMetrics.density * 18 + 0.5f)
            setColor(themeColor)
        }
        btnSearch.background = bg
    }

    private fun showKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        currentFocus?.let {
            imm?.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    


    private fun switchSort(orderby: String) {
        if (orderby == currentSortBy) return
        currentSortBy = orderby
        updateSortChips()
        if (!TextUtils.isEmpty(pendingKeyword)) {
            fetchAllResults(pendingKeyword!!, currentSortBy)
        }
    }

    


    private fun updateSortChips() {
        updateSegmentItem(btnSortLastpost, "lastpost" == currentSortBy)
        updateSegmentItem(btnSortDateline, "dateline" == currentSortBy)
        updateSegmentItem(btnSortReplies, "replies" == currentSortBy)
    }

    private fun updateSegmentItem(tv: TextView, isSelected: Boolean) {
        if (isSelected) {
            tv.setBackgroundResource(R.drawable.bg_segment_pill_selected)
            tv.setTextColor(getColor(R.color.text_primary))
            tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
        } else {
            tv.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            tv.setTextColor(getColor(R.color.text_secondary))
            tv.setTypeface(null, Typeface.NORMAL)
        }
    }

    private fun performSearch() {
        val keyword = etSearch.text.toString().trim()
        if (TextUtils.isEmpty(keyword)) {
            etSearch.error = getString(R.string.search_hint)
            return
        }
        
        SearchHistory.add(this, keyword)
        layoutHistory.visibility = View.GONE
        threadAdapter.setThreadList(null)
        currentSortBy = "lastpost"
        updateSortChips()
        layoutSortBar.visibility = View.VISIBLE
        vSortDivider.visibility = View.VISIBLE
        fetchAllResults(keyword, currentSortBy)
    }

    


    private fun fetchAllResults(keyword: String, orderby: String) {
        pendingKeyword = keyword
        if (!HttpClient.getInstance().isLoggedIn()) {
            tvError.setText(R.string.search_login_required)
            tvError.visibility = View.VISIBLE
            layoutEmptyState.visibility = View.GONE
            return
        }

        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        layoutEmptyState.visibility = View.GONE
        tvError.visibility = View.GONE

        val requestId = ++searchRequestId
        pendingBuffer.clear()
        displayedResults.clear()

        java.lang.Thread {
            try {
                
                val htmlP1 = HttpClient.getInstance().get(ForumParser.getSearchUrl(keyword, 1, orderby))
                if (requestId != searchRequestId) return@Thread  
                val page1 = ForumParser.parseSearchResults(htmlP1)

                runOnUiThread {
                    if (isFinishing || isDestroyed || requestId != searchRequestId) return@runOnUiThread
                    progressBar.visibility = View.GONE
                    if (page1.isNotEmpty()) {
                        layoutEmptyState.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                        threadAdapter.setThreadList(ArrayList(page1))
                        displayedResults.addAll(page1)
                    }
                }

                
                val searchId = ForumParser.extractSearchId(htmlP1)
                val totalPages = ForumParser.parseSearchTotalPages(htmlP1)
                if (searchId == null || totalPages <= 1) return@Thread

                
                val seenTids: MutableSet<Any> = HashSet<Any>()
                for (t in page1) t.tid?.let { seenTids.add(it) }

                
                val maxPages = minOf(totalPages, 3)
                for (p in 2..maxPages) {
                    if (requestId != searchRequestId) break
                    try {
                        java.lang.Thread.sleep(500)
                    } catch (ie: InterruptedException) {
                        java.lang.Thread.currentThread().interrupt()
                        break
                    }
                    val html = try {
                        HttpClient.getInstance().get(
                            ForumParser.getSearchPageUrl(searchId, p, orderby)
                        )
                    } catch (ignored: Exception) { null }
                    if (html == null) continue
                    val results = ForumParser.parseSearchResults(html)
                    if (results.isNullOrEmpty()) continue

                    val fresh = ArrayList<Thread>()
                    for (t in results) {
                        val tid = t.tid
                        if (tid == null || seenTids.add(tid)) fresh.add(t)
                    }
                    if (fresh.isEmpty()) continue
                    runOnUiThread {
                        if (isFinishing || isDestroyed || requestId != searchRequestId) return@runOnUiThread
                        threadAdapter.addThreads(fresh)
                        displayedResults.addAll(fresh)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (isFinishing || isDestroyed || requestId != searchRequestId) return@runOnUiThread
                    progressBar.visibility = View.GONE
                    if (displayedResults.isEmpty()) {
                        tvError.visibility = View.VISIBLE
                        layoutEmptyState.visibility = View.GONE
                    }
                }
            }
        }.start()
    }

    
    @Volatile
    private var searchRequestId = 0

    private fun refreshHistoryView() {
        val list = SearchHistory.getAll(this)
        if (list.isEmpty()) {
            layoutHistory.visibility = View.GONE
            layoutEmptyState.visibility = View.VISIBLE
            return
        }
        layoutEmptyState.visibility = View.GONE
        layoutHistory.visibility = View.VISIBLE
        historyAdapter.submit(list)
    }

    private fun dispatchNextSearchBatch() {
        if (pendingBuffer.isEmpty()) return
        val countToTake = minOf(BATCH_STEP, pendingBuffer.size)
        val batch = ArrayList(pendingBuffer.subList(0, countToTake))
        for (i in 0 until countToTake) {
            pendingBuffer.removeAt(0)
        }
        displayedResults.addAll(batch)
        threadAdapter.addThreads(batch)
    }



class HistoryAdapter(
    private val onItemClick: (String) -> Unit,
    private val onItemDelete: (String) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    private val items = ArrayList<String>()

    fun submit(list: List<String>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvKeyword: TextView = view.findViewById(R.id.tv_history_keyword)
        val ivDelete: ImageView = view.findViewById(R.id.iv_history_delete)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * d + 0.5f).toInt()

        val wrapper = android.widget.FrameLayout(ctx).apply {
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
        }

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(16), 0, dp(8), 0)
            setBackgroundResource(android.R.drawable.list_selector_background)
        }

        val ivIcon = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
            setImageResource(R.drawable.ic_history)
            setColorFilter(androidx.core.content.ContextCompat.getColor(ctx, R.color.text_hint))
        }
        row.addView(ivIcon)

        val tvKeyword = TextView(ctx).apply {
            id = R.id.tv_history_keyword
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = dp(12) }
            textSize = 14f
            setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.text_primary))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(14), 0, dp(14))
        }
        row.addView(tvKeyword)

        val ivDelete = ImageView(ctx).apply {
            id = R.id.iv_history_delete
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setImageResource(R.drawable.ic_cross)
            setColorFilter(androidx.core.content.ContextCompat.getColor(ctx, R.color.text_hint))
            setBackgroundResource(android.R.drawable.list_selector_background)
        }
        row.addView(ivDelete)

        wrapper.addView(row)

        val divider = View(ctx).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                gravity = android.view.Gravity.BOTTOM
                marginStart = dp(46)
            }
            setBackgroundColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.divider))
        }
        wrapper.addView(divider)

        return VH(wrapper)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val kw = items[position]
        holder.tvKeyword.text = kw
        holder.itemView.setOnClickListener { onItemClick(kw) }
        holder.ivDelete.setOnClickListener { onItemDelete(kw) }
    }

    override fun getItemCount(): Int = items.size
}

}
