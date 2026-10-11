package com.solosu.mtforum

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import com.solosu.mtforum.util.ToastUtil as Toast

import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.viewpager2.widget.ViewPager2

import com.google.android.material.switchmaterial.SwitchMaterial
import com.solosu.mtforum.util.AiLog
import com.solosu.mtforum.databinding.ActivityMainBinding
import com.solosu.mtforum.network.ForumParser
import com.solosu.mtforum.network.HttpClient
import com.solosu.mtforum.network.NoticeBadgeManager
import com.solosu.mtforum.network.RateLimiter
import com.solosu.mtforum.ui.MainPagerAdapter
import com.solosu.mtforum.ui.post.PostActivity
import com.solosu.mtforum.session.UserSessionManager
import com.solosu.mtforum.ui.widget.FrostedGlassDrawable

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var mainPager: ViewPager2? = null
    private var pagerAdapter: MainPagerAdapter? = null

    
    private var navHidden = false
    private var autoHideNavEnabled = true 
    private var lastNavScrollAt = 0L
    private var navScrollAccum = 0

    
    private var composeBottomNav: androidx.compose.ui.platform.ComposeView? = null
    private val currentSelectedNavTab = androidx.compose.runtime.mutableIntStateOf(0)
    private val currentNavThemeColor = androidx.compose.runtime.mutableStateOf(androidx.compose.ui.graphics.Color(0xFF0088FF.toInt()))

    
    private val hasUnreadMessage = androidx.compose.runtime.mutableStateOf(false)
    private var mainHandler: Handler? = null
    private var executor: ExecutorService? = null

    private val previousUnreadCounts: MutableMap<String, Int> = java.util.concurrent.ConcurrentHashMap()
    private val previousUnreadFingerprints: MutableMap<String, String> = java.util.concurrent.ConcurrentHashMap()
    private val badgeRefreshInFlight = AtomicBoolean(false)
    private var lastBadgeRefreshAt = 0L 
    private var unreadBaselineReady = false
    private var lastWafPromptAt = 0L 

    
    private var lastBackPressTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {

        try {
            com.solosu.mtforum.session.BlacklistSyncer.syncIfNeeded(
                this,
                object : com.solosu.mtforum.session.BlacklistSyncer.Callback {
                    override fun onSynced(count: Int, error: String?) {
                        com.solosu.mtforum.session.BlacklistSyncer.syncPendingAsync(this@MainActivity, false)
                        try {
                            com.solosu.mtforum.ui.home.HomeFragment.refreshIfActive()
                        } catch (ignored: Exception) {
                        }
                    }
                },
                true,
            )
        } catch (ignored: Exception) {
        }

        com.solosu.mtforum.util.UiStyleManager.applyTheme(this)
        com.solosu.mtforum.util.ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.mainDrawer.setStatusBarBackgroundColor(ContextCompat.getColor(this, R.color.top_bar))
        com.solosu.mtforum.util.ThemeManager.setupWindow(this)

        

        
        mainPager = binding.mainPager
        pagerAdapter = MainPagerAdapter(this)
        mainPager!!.adapter = pagerAdapter
        mainPager!!.offscreenPageLimit = MainPagerAdapter.PAGE_COUNT - 1 
        mainPager!!.isUserInputEnabled = false
        // 允许内部 Fragment 的 ScrollView 接收竖直手势
        mainPager!!.post {
            (mainPager!!.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView)?.apply {
                isNestedScrollingEnabled = false
            }
        } 
        mainPager!!.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                
                val tabIdx = pagerToTabIndex(position)
                currentSelectedNavTab.intValue = tabIdx
                updateHamburgerVisibility(position)
                updateStatusBarColor(position)
                
                setBottomNavVisible(true)
            }
        })

        
        HttpClient.getInstance().init(this)

        
        mainHandler = Handler(Looper.getMainLooper())
        executor = Executors.newFixedThreadPool(7)
        
        sInstance = this
        NoticeBadgeManager.setOnViewedListener(object : NoticeBadgeManager.OnViewedListener {
            override fun onViewed(viewType: String?) {
                onNoticeViewed(viewType)
            }
        })
        refreshMessageBadge()

        
        initNavigationBar()

        
        initDrawer()

        
        updateHamburgerVisibility(mainPager?.currentItem ?: 0)
        updateStatusBarColor(mainPager?.currentItem ?: 0)
    }

    

    private var drawerLayout: DrawerLayout? = null
    private var drawerPanel: View? = null
    private var swAutoHideNav: SwitchMaterial? = null
    private var tvDrawerName: TextView? = null
    private var tvDrawerSubtitle: TextView? = null
    private var ivDrawerAvatar: ImageView? = null

    
    private fun initDrawer() {
        val root = findViewById<View>(R.id.main_drawer)
        if (root !is DrawerLayout) {
            android.util.Log.e("MainActivity", "DrawerLayout not found")
            return
        }
        drawerLayout = root
        
        drawerLayout!!.setScrimColor(0xC0000000.toInt())
        
        drawerLayout!!.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)

        
        drawerLayout!!.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(dv: View) {
                
                setBottomNavVisible(false)
                
                drawerLayout?.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            }

            override fun onDrawerClosed(dv: View) {
                
                setBottomNavVisible(true)
                
                drawerLayout?.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                findViewById<View>(R.id.bottom_nav_container)?.postInvalidate()
            }
        })

        drawerPanel = findViewById(R.id.drawer_panel)
        swAutoHideNav = findViewById(R.id.drawer_switch_auto_hide_nav)
        tvDrawerName = findViewById(R.id.drawer_username)
        tvDrawerSubtitle = findViewById(R.id.drawer_subtitle)
        ivDrawerAvatar = findViewById(R.id.drawer_avatar)
        
        val ownProfile = View.OnClickListener { openOwnProfile() }
        ivDrawerAvatar!!.setOnClickListener(ownProfile)
        if (tvDrawerName != null) tvDrawerName!!.setOnClickListener(ownProfile)
        if (tvDrawerSubtitle != null) tvDrawerSubtitle!!.setOnClickListener(ownProfile)

        
        val openBtn = findViewById<View>(R.id.btn_open_drawer)
        if (openBtn != null) {
            openBtn.setOnClickListener { drawerLayout!!.openDrawer(drawerPanel!!) }
        }

        
        bindSwitchRow(R.id.drawer_auto_hide_nav_row, swAutoHideNav)
        if (swAutoHideNav != null) {
            autoHideNavEnabled = isAutoHideNavEnabled()
            swAutoHideNav!!.isChecked = autoHideNavEnabled
            if (!autoHideNavEnabled) {
                setBottomNavVisible(true)
            }
            swAutoHideNav!!.setOnCheckedChangeListener { v, checked ->
                autoHideNavEnabled = checked
                setAutoHideNavEnabled(checked)
                if (checked) {
                    setBottomNavVisible(true)
                    Toast.makeText(this, "已开启：刷帖时底部栏自动隐藏", Toast.LENGTH_SHORT).show()
                } else {
                    setBottomNavVisible(true)
                    Toast.makeText(this, "已关闭：底部栏常驻显示", Toast.LENGTH_SHORT).show()
                }
            }
        }


        
        val wafVerifyRow = findViewById<View>(R.id.drawer_waf_verify)
        if (wafVerifyRow != null) {
            updateDrawerWafDesc()
            wafVerifyRow.setOnClickListener {
                if (drawerLayout != null && drawerPanel != null && drawerLayout!!.isDrawerOpen(drawerPanel!!)) {
                    drawerLayout!!.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
                        override fun onDrawerClosed(drawerView: View) {
                            drawerLayout?.removeDrawerListener(this)
                            startActivity(
                                com.solosu.mtforum.ui.security.WafVerificationActivity.intent(this@MainActivity)
                            )
                        }
                    })
                    drawerLayout!!.closeDrawer(drawerPanel!!)
                } else {
                    startActivity(
                        com.solosu.mtforum.ui.security.WafVerificationActivity.intent(this)
                    )
                }
            }
        }

        
        val logView = findViewById<View>(R.id.drawer_log)
        if (logView != null) {
            logView.setOnClickListener { showRunLog() }
        }

        
        val settingsView = findViewById<View>(R.id.drawer_settings)
        if (settingsView != null) {
            settingsView.setOnClickListener {
                closeDrawerThen { startActivity(Intent(this, com.solosu.mtforum.ui.space.SettingsActivity::class.java)) }
            }
        }

        
        val themeView = findViewById<View>(R.id.drawer_theme_setting)
        if (themeView != null) {
            updateDrawerThemeDesc()
            themeView.setOnClickListener {
                com.solosu.mtforum.util.ThemeManager.showColorPickerDialog(this) { updateDrawerThemeDesc() }
            }
        }

        refreshDrawerHeader()
    }

    
    private fun updateDrawerThemeDesc() {
        val desc = findViewById<TextView>(R.id.drawer_theme_desc) ?: return
        val cur = com.solosu.mtforum.util.ThemeManager.getCurrentThemeColor(this)
        desc.text = "当前色彩：" + cur.name
    }

    
    private fun closeDrawerThen(action: () -> Unit) {
        if (drawerLayout != null && drawerPanel != null && drawerLayout!!.isDrawerOpen(drawerPanel!!)) {
            drawerLayout!!.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
                override fun onDrawerClosed(drawerView: View) {
                    drawerLayout?.removeDrawerListener(this)
                    action()
                }
            })
            drawerLayout!!.closeDrawer(drawerPanel!!)
        } else {
            action()
        }
    }

    private fun updateDrawerWafDesc() {
        val desc = findViewById<TextView>(R.id.drawer_waf_desc) ?: return
        if (com.solosu.mtforum.network.HttpClient.getInstance().hasPendingWafChallenge()) {
            desc.text = "检测到站点要求人机验证，点击通过"
        } else {
            desc.text = "内容加载不出时，在此通过网页验证"
        }
    }

    
    private fun bindSwitchRow(rowId: Int, sw: SwitchMaterial?) {
        if (sw == null) return
        val row = findViewById<View>(rowId)
        if (row == null) return
        row.setOnClickListener { sw.isChecked = !sw.isChecked }
    }

    
    private fun isAutoHideNavEnabled(): Boolean =
        getSharedPreferences("mtforum_ai_config", MODE_PRIVATE)
            .getBoolean("auto_hide_nav", false)

    private fun setAutoHideNavEnabled(v: Boolean) {
        getSharedPreferences("mtforum_ai_config", MODE_PRIVATE)
            .edit().putBoolean("auto_hide_nav", v).apply()
    }

    private fun openOwnProfile() {
        val session = UserSessionManager.getInstance()
        if (!session.isLoggedIn(this)) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show()
            return
        }
        val uid = session.getUid(this)
        if (uid == null || uid.isEmpty()) {
            Toast.makeText(this, "缺少UID", Toast.LENGTH_SHORT).show()
            return
        }
        val it = Intent(this, com.solosu.mtforum.ui.space.UserProfileActivity::class.java)
        it.putExtra("uid", uid)
        it.putExtra("username", session.getUsername(this))
        if (drawerLayout != null && drawerPanel != null) {
            drawerLayout!!.closeDrawer(drawerPanel!!)
        }
        startActivity(it)
    }

    private fun refreshDrawerHeader() {
        if (tvDrawerName == null) return
        val session = UserSessionManager.getInstance()
        val logged = session.isLoggedIn(this)
        val name = session.getUsername(this)
        tvDrawerName!!.text = if (!logged || android.text.TextUtils.isEmpty(name)) "未登录" else name

        if (tvDrawerSubtitle != null) {
            val uid = session.getUid(this)
            if (logged && !android.text.TextUtils.isEmpty(uid)) {
                val shield = session.getLevel(this)
                
                var lv = if (shield == null) "" else shield.trim()
                if (lv.length >= 2 && (lv[0] == 'L' || lv[0] == 'l')
                    && (lv[1] == 'V' || lv[1] == 'v')
                ) {
                    lv = lv.substring(2)
                    while (lv.startsWith(".") || lv.startsWith(".")) lv = lv.substring(1)
                    lv = lv.trim()
                }
                tvDrawerSubtitle!!.text = if (android.text.TextUtils.isEmpty(lv))
                    "UID " + uid else "UID " + uid + " · Lv." + lv
            } else {
                tvDrawerSubtitle!!.text = "点击查看个人主页"
            }
        }


        if (ivDrawerAvatar != null) {
            val avatar = session.getAvatarUrl(this)
            if (logged && !android.text.TextUtils.isEmpty(avatar)) {
                com.bumptech.glide.Glide.with(this)
                    .load(avatar)
                    .placeholder(R.drawable.ic_account)
                    .error(R.drawable.ic_account)
                    .circleCrop()
                    .into(ivDrawerAvatar!!)
            }
        }
    }

    
    private fun showRunLog() {
        val text = AiLog.dump()
        val sv = android.widget.ScrollView(this)
        val tv = TextView(this)
        tv.text = text
        tv.setTextSize(11f)
        tv.setTextIsSelectable(true)
        tv.typeface = android.graphics.Typeface.MONOSPACE
        val pad = (12 * resources.displayMetrics.density).toInt()
        tv.setPadding(pad, pad, pad, pad)
        sv.addView(tv)

        val path = AiLog.filePath()
        val hint = if (android.text.TextUtils.isEmpty(path))
            "" else "\n\n日志文件：\n" + path

        val dlg = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("运行日志（" + AiLog.size() + " 条）")
            .setView(sv)
            .setNeutralButton("复制") { d, w ->
                val cm = getSystemService(CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                if (cm != null) {
                    cm.setPrimaryClip(
                        android.content.ClipData.newPlainText(
                            "mtforum-log", text + hint
                        )
                    )
                    Toast.makeText(this, "日志已复制", Toast.LENGTH_SHORT).show()
                }
            }
            .setPositiveButton("清空") { d, w ->
                AiLog.clear()
                Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("关闭", null)
            .show()

        com.solosu.mtforum.ui.widget.DialogHelper.applyToAlertDialog(dlg, this)
        val themeColor = com.solosu.mtforum.util.ThemeManager.getThemeColor(this)
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(0xFFEF4444.toInt()) 
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)?.setTextColor(themeColor)
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)?.setTextColor(
            androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary)
        )
    }

    


    private fun initNavigationBar() {
        composeBottomNav = findViewById(R.id.bottom_nav_container)
        if (composeBottomNav == null) {
            android.util.Log.e("MainActivity", "ComposeView (bottom_nav_container) is null")
            return
        }

        
        composeBottomNav!!.setViewCompositionStrategy(
            androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )

        val themeColor = com.solosu.mtforum.util.ThemeManager.getThemeColor(this)
        currentNavThemeColor.value = androidx.compose.ui.graphics.Color(themeColor)
        currentSelectedNavTab.intValue = pagerToTabIndex(MainPagerAdapter.PAGE_HOME)

        composeBottomNav!!.setContent {
            com.solosu.mtforum.ui.widget.MTForumLiquidNavBar(
                selectedTabIndex = currentSelectedNavTab.intValue,
                onTabSelected = { pos ->
                    currentSelectedNavTab.intValue = pos
                    when (pos) {
                        0 -> mainPager?.setCurrentItem(MainPagerAdapter.PAGE_HOME, false)
                        1 -> mainPager?.setCurrentItem(MainPagerAdapter.PAGE_COMMUNITY, false)
                        3 -> mainPager?.setCurrentItem(MainPagerAdapter.PAGE_MESSAGE, false)
                        4 -> mainPager?.setCurrentItem(MainPagerAdapter.PAGE_PROFILE, false)
                    }
                    setBottomNavVisible(true)
                },
                onPostClicked = {
                    openPost()
                },
                themeColor = currentNavThemeColor.value,
                backdropSourceView = mainPager,
                hasUnreadMessage = hasUnreadMessage.value,
                isIos = com.solosu.mtforum.util.UiStyleManager.isIos(this@MainActivity)
            )
        }
    }

    
    private fun openPost() {
        startActivity(Intent(this@MainActivity, PostActivity::class.java))
    }

    
    private fun pagerToTabIndex(pagerPos: Int): Int = when (pagerPos) {
        MainPagerAdapter.PAGE_HOME -> 0
        MainPagerAdapter.PAGE_COMMUNITY -> 1
        MainPagerAdapter.PAGE_MESSAGE -> 3
        MainPagerAdapter.PAGE_PROFILE -> 4
        else -> 0
    }

    
    private fun updateHamburgerVisibility(pagerPos: Int) {
        val btn = findViewById<View>(R.id.btn_open_drawer) ?: return
        btn.visibility = if (pagerPos == MainPagerAdapter.PAGE_PROFILE) View.GONE else View.VISIBLE
    }

    
    private fun updateStatusBarColor(pagerPos: Int) {
        val colorRes = if (pagerPos == MainPagerAdapter.PAGE_PROFILE) R.color.background else R.color.top_bar
        val color = ContextCompat.getColor(this, colorRes)
        window.statusBarColor = color
        binding.mainDrawer.setStatusBarBackgroundColor(color)
    }

    

    
    private fun setBottomNavVisible(show: Boolean) {
        val nav = findViewById<View>(R.id.bottom_nav_container) ?: return
        if (show) {
            if (!navHidden && nav.translationY == 0f) return
            navHidden = false
            nav.visibility = View.VISIBLE
            nav.animate().cancel()
            nav.animate().translationY(0f).setDuration(NAV_HIDE_ANIM_MS)
                .withEndAction {
                    nav.postInvalidate()
                }.start()
        } else {
            if (navHidden) return
            navHidden = true
            val hidden = if (nav.height > 0) nav.height + dp(24f) else dp(92f)
            nav.animate().cancel()
            nav.animate().translationY(hidden).setDuration(NAV_HIDE_ANIM_MS).start()
        }
    }

    private fun dp(v: Float): Float {
        return v * resources.displayMetrics.density
    }

    





    fun onNavScroll(dy: Int) {
        
        if (!autoHideNavEnabled) return
        val now = System.currentTimeMillis()
        if (now - lastNavScrollAt > 400L) {
            navScrollAccum = 0
        }
        lastNavScrollAt = now
        if (dy > 0) {
            if (navScrollAccum < 0) navScrollAccum = 0
            navScrollAccum += dy
            if (navScrollAccum > 24) {
                setBottomNavVisible(false)
            }
        } else if (dy < 0) {
            if (navScrollAccum > 0) navScrollAccum = 0
            navScrollAccum += dy
            if (navScrollAccum < -8) {
                setBottomNavVisible(true)
            }
        }
    }

    

    


    private fun refreshMessageBadge() {
        if (isFinishing() || (android.os.Build.VERSION.SDK_INT >= 17 && isDestroyed())) return
        if (!HttpClient.getInstance().isLoggedIn()) {
            badgeRefreshInFlight.set(false)
            hasUnreadMessage.value = false
            scheduleNextBadgeRefresh()
            return
        }
        
        val coolRemain = RateLimiter.circuitRemainingMs()
        if (coolRemain > 0) {
            badgeRefreshInFlight.set(false)
            scheduleNextBadgeRefresh(coolRemain)
            return
        }
        
        if (!badgeRefreshInFlight.compareAndSet(false, true)) return
        lastBadgeRefreshAt = System.currentTimeMillis() 
        if (executor == null || executor!!.isShutdown || executor!!.isTerminated) {
            badgeRefreshInFlight.set(false)
            scheduleNextBadgeRefresh()
            return
        }

        
        try {
            executor!!.execute {
                val counts: MutableMap<String, Int> = java.util.concurrent.ConcurrentHashMap()
                val fingerprints: MutableMap<String, String> = java.util.concurrent.ConcurrentHashMap()
                val types = arrayOf("pm", "follower", "mypost", "interactive", "system", "app")
                try {
                    HttpClient.getInstance().syncFromCookieManager()
                    val latch = CountDownLatch(types.size)
                    for (type in types) {
                        val viewType = type
                        val url: String
                        if ("pm" == viewType) {
                            url = HttpClient.BASE_URL + "home.php?mod=space&do=pm&mobile=2"
                        } else if ("follower" == viewType) {
                            url = HttpClient.BASE_URL + "home.php?mod=follow&do=follower&uid=" +
                                    currentUid + "&mobile=2"
                        } else {
                            url = HttpClient.BASE_URL + "home.php?mod=space&do=notice&view=" + viewType
                        }
                        try {
                            executor!!.execute {
                                try {
                                    putUnreadData(counts, fingerprints, viewType, url)
                                } finally {
                                    latch.countDown()
                                }
                            }
                        } catch (rejected: java.util.concurrent.RejectedExecutionException) {
                            latch.countDown()
                        }
                    }
                    latch.await(25, java.util.concurrent.TimeUnit.SECONDS)
                    runOnUiThread {
                        badgeRefreshInFlight.set(false)
                        if (isFinishing() || (android.os.Build.VERSION.SDK_INT >= 17 && isDestroyed())) return@runOnUiThread
                        applyUnreadCounts(counts, fingerprints)
                    }
                } catch (ignored: InterruptedException) {
                    Thread.currentThread().interrupt()
                    runOnUiThread {
                        badgeRefreshInFlight.set(false)
                        if (!isFinishing()) scheduleNextBadgeRefresh()
                    }
                } catch (ignored: Exception) {
                    runOnUiThread {
                        badgeRefreshInFlight.set(false)
                        if (!isFinishing()) scheduleNextBadgeRefresh()
                    }
                }
            }
        } catch (rejected: java.util.concurrent.RejectedExecutionException) {
            badgeRefreshInFlight.set(false)
            scheduleNextBadgeRefresh()
        }
    }

    private val currentUid: String
        get() {
            val uid = com.solosu.mtforum.session.UserSessionManager
                .getInstance().getUid(this)
            return if (uid == null || uid.isEmpty()) "0" else uid
        }

    private fun fetchUnreadCount(url: String, viewType: String): Int {
        try {
            val html = if ("pm" == viewType || "follower" == viewType)
                HttpClient.getInstance().get(url)
            else
                HttpClient.getInstance().getDesktop(url)
            if (html == null || html.isEmpty() || ForumParser.isLoginPage(html)) return 0
            var items: MutableList<com.solosu.mtforum.model.Message>
            if ("pm" == viewType) items = ForumParser.parsePmList(html)
            else if ("follower" == viewType) items = ForumParser.parseFollowerList(html)
            else items = ForumParser.parseNoticeList(html)
            var count = 0
            for (item in items) {
                if (!item.isRead) count++
            }
            return count
        } catch (ignored: Exception) {
            return 0
        }
    }

    private fun putUnreadData(
        counts: MutableMap<String, Int>,
        fingerprints: MutableMap<String, String>,
        viewType: String, url: String
    ) {
        try {
            val html = if ("pm" == viewType || "follower" == viewType)
                HttpClient.getInstance().get(url)
            else
                HttpClient.getInstance().getDesktop(url)
            if (html == null || html.isEmpty() || ForumParser.isLoginPage(html)) {
                counts[viewType] = 0
                fingerprints[viewType] = ""
                return
            }

            
            val snapshot = NoticeBadgeManager.buildSnapshot(
                viewType, html, HttpClient.getInstance()
            )
            val count = NoticeBadgeManager.saveCurrentAndGetNewCount(
                this@MainActivity, viewType, snapshot
            )
            counts[viewType] = count
            fingerprints[viewType] = snapshot
        } catch (ignored: Exception) {
            counts[viewType] = 0
            fingerprints[viewType] = ""
        }
    }

    private fun applyUnreadCounts(
        counts: MutableMap<String, Int>,
        fingerprints: MutableMap<String, String>
    ) {
        
        var totalCount = 0
        val allTypes = arrayOf("pm", "follower", "mypost", "interactive", "system", "app")
        for (type in allTypes) {
            totalCount += getCount(counts, type)
        }
        updateBadgeDisplay(totalCount)

        if (unreadBaselineReady) {
            val hasNewPrivateChat = hasNewUnread("pm", counts, fingerprints)
            var hasNewOtherNotice = false
            val otherTypes = arrayOf("follower", "mypost", "interactive", "system", "app")
            for (type in otherTypes) {
                if (hasNewUnread(type, counts, fingerprints)) {
                    hasNewOtherNotice = true
                    break
                }
            }
            
        }

        previousUnreadCounts.clear()
        previousUnreadCounts.putAll(counts)
        previousUnreadFingerprints.clear()
        previousUnreadFingerprints.putAll(fingerprints)
        unreadBaselineReady = true
        scheduleNextBadgeRefresh()
    }

    private fun hasNewUnread(
        type: String, counts: MutableMap<String, Int>,
        fingerprints: MutableMap<String, String>
    ): Boolean {
        val currentCount = getCount(counts, type)
        val oldCount = getCount(previousUnreadCounts, type)
        val currentFingerprint = fingerprints[type]
        val oldFingerprint = previousUnreadFingerprints[type]
        return currentCount > oldCount ||
                (currentCount > 0 && java.util.Objects.equals(currentFingerprint, oldFingerprint).not())
    }

    private fun getCount(counts: Map<String, Int>, key: String): Int {
        val value = counts[key]
        return value ?: 0
    }

    private fun updateBadgeDisplay(count: Int) {
        renderBadge(count)
        scheduleNextBadgeRefresh()
    }

    
    private fun renderBadge(count: Int) {
        hasUnreadMessage.value = (count > 0)
    }

    
    private fun onNoticeViewed(viewType: String?) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            val t = viewType
            runOnUiThread { onNoticeViewed(t) }
            return
        }
        if (isFinishing() || (android.os.Build.VERSION.SDK_INT >= 17 && isDestroyed())) return
        if ("*" == viewType) {
            for (type in BADGE_TYPES) previousUnreadCounts[type] = 0
        } else if (viewType != null && !viewType.isEmpty()) {
            previousUnreadCounts[viewType] = 0
        } else {
            return
        }
        var total = 0
        for (type in BADGE_TYPES) total += getCount(previousUnreadCounts, type)
        renderBadge(total)
    }

    private fun scheduleNextBadgeRefresh(delayMs: Long = BADGE_REFRESH_INTERVAL_MS) {
        mainHandler!!.removeCallbacks(badgeRunnable)
        mainHandler!!.postDelayed(badgeRunnable, delayMs)
    }

    



    private fun maybePromptWafIfNeeded() {
        if (!HttpClient.getInstance().hasPendingWafChallenge()) return
        val now = System.currentTimeMillis()
        if (now - lastWafPromptAt < WAF_PROMPT_THROTTLE_MS) return
        lastWafPromptAt = now
        val dlg = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("需要人机验证")
            .setMessage("站点要求完成一次人机验证才能继续加载内容。是否现在通过网页验证？")
            .setPositiveButton("去验证") { _, _ ->
                startActivity(
                    com.solosu.mtforum.ui.security.WafVerificationActivity.intent(this)
                )
            }
            .setNegativeButton("稍后", null)
            .show()
        com.solosu.mtforum.ui.widget.DialogHelper.applyToAlertDialog(dlg, this)
    }

    private val badgeRunnable = Runnable { refreshMessageBadge() }

    override fun onStart() {
        super.onStart()
    }

    override fun onResume() {
        super.onResume()
        
        
        if (mainHandler != null) {
            val now = System.currentTimeMillis()
            if (now - lastBadgeRefreshAt >= BADGE_RESUME_THROTTLE_MS) {
                mainHandler!!.removeCallbacks(badgeRunnable)
                refreshMessageBadge()
            }
        }
        
        refreshDrawerHeader()
        updateDrawerWafDesc()
        updateDrawerThemeDesc()
        
        maybePromptWafIfNeeded()
        val themeColor = com.solosu.mtforum.util.ThemeManager.getThemeColor(this)
        currentNavThemeColor.value = androidx.compose.ui.graphics.Color(themeColor)
    }

    override fun onStop() {
        if (mainHandler != null) mainHandler!!.removeCallbacks(badgeRunnable)
        super.onStop()
    }

    override fun onDestroy() {
        if (sInstance === this) {
            sInstance = null
            NoticeBadgeManager.setOnViewedListener(null)
        }
        if (mainHandler != null) {
            mainHandler!!.removeCallbacksAndMessages(null)
        }
        if (executor != null) {
            executor!!.shutdownNow()
        }
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        
        if (drawerLayout != null && drawerPanel != null
            && drawerLayout!!.isDrawerOpen(drawerPanel!!)
        ) {
            drawerLayout!!.closeDrawer(drawerPanel!!)
            return
        }
        
        val now = System.currentTimeMillis()
        if (lastBackPressTime > 0 && now - lastBackPressTime < EXIT_INTERVAL_MS) {
            finishAffinity() 
            super.onBackPressed()
            return
        }
        lastBackPressTime = now
        Toast.makeText(this, "再按一次返回键退出", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val NAV_HIDE_ANIM_MS = 200L

        private const val BADGE_REFRESH_INTERVAL_MS = 300000L   
        private const val BADGE_RESUME_THROTTLE_MS = 15000L 
        private const val WAF_PROMPT_THROTTLE_MS = 5 * 60 * 1000L 

        
        private val BADGE_TYPES = arrayOf("pm", "follower", "mypost", "interactive", "system", "app")

        private var sInstance: MainActivity? = null

        
        private const val EXIT_INTERVAL_MS = 2000L
    }
}
