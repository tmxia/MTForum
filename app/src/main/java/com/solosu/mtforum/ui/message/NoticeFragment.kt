package com.solosu.mtforum.ui.message

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView

import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.fragment.app.Fragment

import com.solosu.mtforum.R
import com.solosu.mtforum.databinding.FragmentNoticeBinding
import com.solosu.mtforum.ui.widget.FrostedGlassDrawable
import com.solosu.mtforum.network.ForumParser
import com.solosu.mtforum.network.HttpClient
import com.solosu.mtforum.network.NoticeBadgeManager
import com.solosu.mtforum.network.RateLimiter
import com.solosu.mtforum.session.UserSessionManager
import com.solosu.mtforum.ui.space.FriendListActivity

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors






class NoticeFragment : Fragment() {
    private var binding: FragmentNoticeBinding? = null
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

    @Nullable
    @Override
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentNoticeBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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
        val b = binding ?: return
        badgeMessages = b.badgeMessages
        badgeFans = b.badgeFans
        badgePosts = b.badgePosts
        badgeInteractive = b.badgeInteractive
        badgeSystem = b.badgeSystem
        badgeApp = b.badgeApp
        tvClearAll = b.tvClearAll

        
        com.solosu.mtforum.util.ScrollToTopHelper.attachNestedScrollView(b.toolbar, b.scrollNotice)

        
        applyFrostedGlassToIcon(b.ivEmojiMessages)
        applyFrostedGlassToIcon(b.ivEmojiFans)
        applyFrostedGlassToIcon(b.ivEmojiPosts)
        applyFrostedGlassToIcon(b.ivEmojiInteractive)
        applyFrostedGlassToIcon(b.ivEmojiSystem)
        applyFrostedGlassToIcon(b.ivEmojiApp)
    }

    private fun applyFrostedGlassToIcon(icon: ImageView?) {
        if (icon != null) {
            icon.background = FrostedGlassDrawable.createSubtle(requireContext(), 10f)
        }
    }

    private fun setupClickListeners() {
        val b = binding ?: return
        b.llMyMessages.setOnClickListener { openNativeDetail("pm", "我的消息") }
        
        b.llMyFans.setOnClickListener {
            NoticeBadgeManager.markViewed(requireContext(), "follower")
            val intent = Intent(requireContext(), FriendListActivity::class.java)
            intent.putExtra("mode", "followers")
            startActivity(intent)
        }
        b.llMyPosts.setOnClickListener { openNativeDetail("mypost", "我的帖子") }
        b.llInteractive.setOnClickListener { openNativeDetail("interactive", "坛友互动") }
        b.llSystem.setOnClickListener { openNativeDetail("system", "系统提醒") }
        b.llApp.setOnClickListener { openNativeDetail("app", "应用提醒") }
        
        tvClearAll!!.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f)
                    .setDuration(120).start()
                else -> {
                }
            }
            false 
        }
        tvClearAll!!.setOnClickListener { v ->
            val anim: Animation = AnimationUtils.loadAnimation(requireContext(), android.R.anim.fade_in)
            anim.duration = 150
            v.startAnimation(anim)
            clearAllBadges()
        }
    }

    
    private fun openNativeDetail(view: String, title: String) {
        NoticeBadgeManager.markViewed(requireContext(), view)
        val url: String
        if ("pm" == view) {
            url = HttpClient.BASE_URL + "home.php?mod=space&do=pm&mobile=2"
        } else if ("follower" == view) {
            url = HttpClient.BASE_URL + "home.php?mod=follow&do=follower&uid=" + uid + "&mobile=2"
        } else {
            url = HttpClient.BASE_URL + "home.php?mod=space&do=notice&view=" + view
        }
        val intent = Intent(requireContext(), NoticeDetailActivity::class.java)
        intent.putExtra("url", url)
        intent.putExtra(NoticeDetailActivity.EXTRA_VIEW_TYPE, view)
        intent.putExtra(NoticeDetailActivity.EXTRA_TITLE, title)
        requireActivity().startActivityForResult(intent, REQUEST_CODE_DETAIL)
    }

    private val uid: String
        get() {
            val uid = UserSessionManager.getInstance().getUid(requireContext())
            return if (TextUtils.isEmpty(uid)) "0" else uid!!
        }

    private fun loadAllBadgeCounts() {
        lastBadgeLoadAt = System.currentTimeMillis() 
        
        if (RateLimiter.circuitRemainingMs() > 0) return
        loadCountBySnapshot(HttpClient.BASE_URL + "home.php?mod=space&do=pm&mobile=2", badgeMessages, "pm")
        loadCountBySnapshot(
            HttpClient.BASE_URL + "home.php?mod=follow&do=follower&uid=" + uid + "&mobile=2",
            badgeFans, "follower"
        )
        loadCountBySnapshot(HttpClient.BASE_URL + "home.php?mod=space&do=notice&view=mypost", badgePosts, "mypost")
        loadCountBySnapshot(
            HttpClient.BASE_URL + "home.php?mod=space&do=notice&view=interactive",
            badgeInteractive, "interactive"
        )
        loadCountBySnapshot(HttpClient.BASE_URL + "home.php?mod=space&do=notice&view=system", badgeSystem, "system")
        loadCountBySnapshot(HttpClient.BASE_URL + "home.php?mod=space&do=notice&view=app", badgeApp, "app")
    }

    


    private fun loadCountBySnapshot(url: String, badgeView: TextView?, viewType: String) {
        if (badgeView == null) return
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
                    requireContext(), viewType, snapshot
                )
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
        updateBadge(badgeMessages, 0)
        updateBadge(badgeFans, 0)
        updateBadge(badgePosts, 0)
        updateBadge(badgeInteractive, 0)
        updateBadge(badgeSystem, 0)
        updateBadge(badgeApp, 0)
    }

    private fun clearAllBadges() {
        hideAllBadges()
        NoticeBadgeManager.markAllViewed(requireContext())
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CODE_DETAIL || resultCode != android.app.Activity.RESULT_OK || data == null) return
        if (!data.getBooleanExtra(NoticeDetailActivity.RESULT_CLEARED, false)) return
        val type = data.getStringExtra(NoticeDetailActivity.RESULT_VIEW_TYPE)
        if (!TextUtils.isEmpty(type)) NoticeBadgeManager.markViewed(requireContext(), type)
        if ("pm" == type) updateBadge(badgeMessages, 0)
        else if ("follower" == type) updateBadge(badgeFans, 0)
        else if ("mypost" == type) updateBadge(badgePosts, 0)
        else if ("interactive" == type) updateBadge(badgeInteractive, 0)
        else if ("system" == type) updateBadge(badgeSystem, 0)
        else if ("app" == type) updateBadge(badgeApp, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (executor != null) executor!!.shutdownNow()
        executor = null
        binding = null
    }

    companion object {
        private const val REQUEST_CODE_DETAIL = 1001

        
        private const val BADGE_RESUME_THROTTLE_MS = 300000L   
    }
}
