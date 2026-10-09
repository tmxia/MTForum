package com.solosu.mtforum.ui.message

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.View
import android.widget.TextView

import androidx.appcompat.app.AppCompatActivity

import com.solosu.mtforum.R
import com.solosu.mtforum.network.ForumParser
import com.solosu.mtforum.network.HttpClient
import com.solosu.mtforum.network.NoticeBadgeManager
import com.solosu.mtforum.session.UserSessionManager
import com.solosu.mtforum.ui.space.FriendListActivity

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors






class NoticeActivity : AppCompatActivity() {
    private var badgeMessages: TextView? = null
    private var badgeFans: TextView? = null
    private var badgePosts: TextView? = null
    private var badgeInteractive: TextView? = null
    private var badgeSystem: TextView? = null
    private var badgeApp: TextView? = null
    private var tvClearAll: TextView? = null
    private var httpClient: HttpClient? = null
    private var mainHandler: Handler? = null
    private var executor: ExecutorService? = null
    private var lastBadgeLoadAt = 0L 

    override fun onCreate(savedInstanceState: Bundle?) {
        com.solosu.mtforum.util.UiStyleManager.applyTheme(this)
        com.solosu.mtforum.util.ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notice)

        httpClient = HttpClient.getInstance()
        mainHandler = Handler(Looper.getMainLooper())
        executor = Executors.newFixedThreadPool(4)

        initViews()
        setupClickListeners()
        
        
        loadAllBadgeCounts()
    }

    override fun onResume() {
        super.onResume()
        
        val now = System.currentTimeMillis()
        if (now - lastBadgeLoadAt < BADGE_RESUME_THROTTLE_MS) {
            return
        }
        if (mainHandler != null) {
            mainHandler!!.removeCallbacksAndMessages(null)
        }
        if (executor != null && !executor!!.isShutdown) {
            loadAllBadgeCounts()
        }
    }

    private fun initViews() {
        findViewById<View>(R.id.tv_back).setOnClickListener { v -> finish() }
        badgeMessages = findViewById<TextView>(R.id.badge_messages)
        badgeFans = findViewById<TextView>(R.id.badge_fans)
        badgePosts = findViewById<TextView>(R.id.badge_posts)
        badgeInteractive = findViewById<TextView>(R.id.badge_interactive)
        badgeSystem = findViewById<TextView>(R.id.badge_system)
        badgeApp = findViewById<TextView>(R.id.badge_app)
        tvClearAll = findViewById<TextView>(R.id.tv_clear_all)
    }

    private fun setupClickListeners() {
        findViewById<View>(R.id.ll_my_messages).setOnClickListener { v -> openNativeDetail("pm", "我的消息") }
        
        findViewById<View>(R.id.ll_my_fans).setOnClickListener { v ->
            NoticeBadgeManager.markViewed(this, "follower")
            val intent = Intent(this, FriendListActivity::class.java)
            intent.putExtra("mode", "followers")
            startActivity(intent)
        }
        findViewById<View>(R.id.ll_my_posts).setOnClickListener { v -> openNativeDetail("mypost", "我的帖子") }
        findViewById<View>(R.id.ll_interactive).setOnClickListener { v -> openNativeDetail("interactive", "坛友互动") }
        findViewById<View>(R.id.ll_system).setOnClickListener { v -> openNativeDetail("system", "系统提醒") }
        findViewById<View>(R.id.ll_app).setOnClickListener { v -> openNativeDetail("app", "应用提醒") }
        tvClearAll!!.setOnClickListener { v -> clearAllBadges() }
    }

    
    private fun openNativeDetail(view: String, title: String) {
        NoticeBadgeManager.markViewed(this, view)
        val url: String
        if ("pm" == view) {
            url = HttpClient.BASE_URL + "home.php?mod=space&do=pm&mobile=2"
        } else if ("follower" == view) {
            url = HttpClient.BASE_URL + "home.php?mod=follow&do=follower&uid=" + getUid() + "&mobile=2"
        } else {
            url = HttpClient.BASE_URL + "home.php?mod=space&do=notice&view=" + view
        }
        val intent = Intent(this, NoticeDetailActivity::class.java)
        intent.putExtra("url", url)
        intent.putExtra(NoticeDetailActivity.EXTRA_VIEW_TYPE, view)
        intent.putExtra(NoticeDetailActivity.EXTRA_TITLE, title)
        startActivityForResult(intent, REQUEST_CODE_DETAIL)
    }

    private fun getUid(): String {
        val uid = UserSessionManager.getInstance().getUid(this)
        return if (TextUtils.isEmpty(uid)) "0" else uid!!
    }

    private fun loadAllBadgeCounts() {
        lastBadgeLoadAt = System.currentTimeMillis() 
        loadCountBySnapshot(HttpClient.BASE_URL + "home.php?mod=space&do=pm&mobile=2", badgeMessages, "pm")
        loadCountBySnapshot(HttpClient.BASE_URL + "home.php?mod=follow&do=follower&uid=" + getUid() + "&mobile=2", badgeFans, "follower")
        loadCountBySnapshot(HttpClient.BASE_URL + "home.php?mod=space&do=notice&view=mypost", badgePosts, "mypost")
        loadCountBySnapshot(HttpClient.BASE_URL + "home.php?mod=space&do=notice&view=interactive", badgeInteractive, "interactive")
        loadCountBySnapshot(HttpClient.BASE_URL + "home.php?mod=space&do=notice&view=system", badgeSystem, "system")
        loadCountBySnapshot(HttpClient.BASE_URL + "home.php?mod=space&do=notice&view=app", badgeApp, "app")
    }

    


    private fun loadCountBySnapshot(url: String, badgeView: TextView?, viewType: String) {
        executor!!.execute {
            try {
                httpClient!!.syncFromCookieManager()
                val html = if ("pm" == viewType || "follower" == viewType)
                        httpClient!!.get(url) else httpClient!!.getDesktop(url)
                if (TextUtils.isEmpty(html) || ForumParser.isLoginPage(html)) {
                    mainHandler!!.post { updateBadge(badgeView, 0) }
                    return@execute
                }
                val snapshot = NoticeBadgeManager.buildSnapshot(viewType, html, httpClient)
                val count = NoticeBadgeManager.saveCurrentAndGetNewCount(
                        this@NoticeActivity, viewType, snapshot)
                mainHandler!!.post { updateBadge(badgeView, count) }
            } catch (ignored: Exception) {
                mainHandler!!.post { updateBadge(badgeView, 0) }
            }
        }
    }

    private fun updateBadge(badgeView: TextView?, count: Int) {
        if (badgeView == null) return
        if (count > 0) {
            badgeView.text = if (count > 99) "99+" else count.toString()
            badgeView.visibility = View.VISIBLE
        } else badgeView.visibility = View.GONE
    }

    private fun hideAllBadges() {
        updateBadge(badgeMessages, 0); updateBadge(badgeFans, 0); updateBadge(badgePosts, 0)
        updateBadge(badgeInteractive, 0); updateBadge(badgeSystem, 0); updateBadge(badgeApp, 0)
    }

    private fun clearAllBadges() {
        hideAllBadges()
        NoticeBadgeManager.markAllViewed(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CODE_DETAIL || resultCode != android.app.Activity.RESULT_OK || data == null) return
        if (!data.getBooleanExtra(NoticeDetailActivity.RESULT_CLEARED, false)) return
        val type = data.getStringExtra(NoticeDetailActivity.RESULT_VIEW_TYPE)
        if (!TextUtils.isEmpty(type)) NoticeBadgeManager.markViewed(this, type)
        if ("pm" == type) updateBadge(badgeMessages, 0)
        else if ("follower" == type) updateBadge(badgeFans, 0)
        else if ("mypost" == type) updateBadge(badgePosts, 0)
        else if ("interactive" == type) updateBadge(badgeInteractive, 0)
        else if ("system" == type) updateBadge(badgeSystem, 0)
        else if ("app" == type) updateBadge(badgeApp, 0)
    }

    override fun onDestroy() {
        if (executor != null) executor!!.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_CODE_DETAIL = 1001

        
        private const val BADGE_RESUME_THROTTLE_MS = 15000L
    }
}
