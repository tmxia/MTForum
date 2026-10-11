package com.solosu.mtforum.ui.profile

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.fragment.app.Fragment

import com.bumptech.glide.Glide
import com.solosu.mtforum.R
import com.solosu.mtforum.databinding.FragmentProfileBinding
import com.solosu.mtforum.model.UserProfile
import com.solosu.mtforum.network.ForumParser
import com.solosu.mtforum.network.HttpClient
import com.solosu.mtforum.session.UserSessionManager
import com.solosu.mtforum.ui.widget.FrostedGlassDrawable
import com.solosu.mtforum.ui.widget.FrostedGlassHelper
import com.solosu.mtforum.ui.widget.NavBarAutoHideHelper
import com.solosu.mtforum.ui.login.LoginBottomSheet
import com.solosu.mtforum.ui.space.SpaceThreadListActivity
import com.solosu.mtforum.ui.space.BrowseHistoryActivity
import com.solosu.mtforum.ui.space.FriendListActivity
import com.solosu.mtforum.ui.space.CreditDetailActivity
import com.solosu.mtforum.ui.space.EditProfileActivity
import com.solosu.mtforum.ui.BlacklistActivity
import com.solosu.mtforum.session.AccountManager







class ProfileFragment : Fragment() {

    private var binding: FragmentProfileBinding? = null
    private lateinit var httpClient: HttpClient

    @Nullable
    override fun onCreateView(
        @NonNull inflater: LayoutInflater,
        @Nullable container: ViewGroup?,
        @Nullable savedInstanceState: Bundle?
    ): View {
        binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(@NonNull view: View, @Nullable savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        FrostedGlassHelper.applyToCardViews(view, requireContext())

        
        applyFrostedGlassToIcon(binding!!.ivEmojiThreads)
        applyFrostedGlassToIcon(binding!!.ivEmojiFavorites)
        applyFrostedGlassToIcon(binding!!.ivEmojiFriends)
        applyFrostedGlassToIcon(binding!!.ivEmojiCredits)
        applyFrostedGlassToIcon(binding!!.ivEmojiEdit)
        applyFrostedGlassToIcon(binding!!.ivEmojiBlacklist)
        applyFrostedGlassToIcon(binding!!.ivEmojiBrowseHistory)
        applyFrostedGlassToIcon(binding!!.ivEmojiIosStyle)

        httpClient = HttpClient.getInstance()

        
        val navScroll = view.findViewById<androidx.core.widget.NestedScrollView>(R.id.nav_scroll_profile)
        if (navScroll != null) {
            navScroll.setOnScrollChangeListener { v, sx, sy, osx, osy ->
                NavBarAutoHideHelper.onScrolled(activity, sy - osy)
            }

        }

        
        binding!!.layoutThreads.setOnClickListener {
            val intent = Intent(requireContext(), SpaceThreadListActivity::class.java)
            intent.putExtra("mode", "my_threads")
            startActivity(intent)
        }
        binding!!.layoutReplies.setOnClickListener {
            val intent = Intent(requireContext(), SpaceThreadListActivity::class.java)
            intent.putExtra("mode", "my_replies")
            startActivity(intent)
        }
        binding!!.layoutFriends.setOnClickListener {
            val intent = Intent(requireContext(), FriendListActivity::class.java)
            intent.putExtra("mode", "friends")
            startActivity(intent)
        }
        binding!!.layoutFollowing.setOnClickListener {
            val intent = Intent(requireContext(), FriendListActivity::class.java)
            intent.putExtra("mode", "following")
            startActivity(intent)
        }
        binding!!.layoutFollowers.setOnClickListener {
            val intent = Intent(requireContext(), FriendListActivity::class.java)
            intent.putExtra("mode", "followers")
            startActivity(intent)
        }

        
        binding!!.layoutMyThreads.setOnClickListener {
            val intent = Intent(requireContext(), SpaceThreadListActivity::class.java)
            intent.putExtra("mode", "my_threads")
            startActivity(intent)
        }

        binding!!.layoutMyFavorites.setOnClickListener {
            val intent = Intent(requireContext(), SpaceThreadListActivity::class.java)
            intent.putExtra("mode", "favorites")
            startActivity(intent)
        }

        binding!!.layoutBrowseHistory.setOnClickListener {
            val intent = Intent(requireContext(), BrowseHistoryActivity::class.java)
            startActivity(intent)
        }

        binding!!.layoutMyFriends.setOnClickListener {
            val intent = Intent(requireContext(), FriendListActivity::class.java)
            intent.putExtra("mode", "friends")
            startActivity(intent)
        }

        binding!!.layoutCreditsDetail.setOnClickListener {
            val intent = Intent(requireContext(), CreditDetailActivity::class.java)
            startActivity(intent)
        }

        binding!!.layoutEditProfile.setOnClickListener {
            val intent = Intent(requireContext(), EditProfileActivity::class.java)
            startActivity(intent)
        }

        binding!!.swIosStyle.isChecked = com.solosu.mtforum.util.UiStyleManager.isIos(requireContext())
        binding!!.swIosStyle.setOnCheckedChangeListener { _, checked ->
            com.solosu.mtforum.util.UiStyleManager.setIos(requireContext(), checked)
            requireActivity().recreate()
        }
        binding!!.layoutBlacklist.setOnClickListener {
            val intent = Intent(requireContext(), BlacklistActivity::class.java)
            startActivity(intent)
        }

        
        binding!!.btnSwitchAccount.setOnClickListener {
            AccountManager.showAccountSwitcherDialog(requireActivity()) {
                if (isAdded) {
                    updateLoginState()
                }
            }
        }

        
        binding!!.btnAccount.setOnClickListener {
            if (!isActuallyLoggedIn()) {
                startLogin()
                return@setOnClickListener
            }
            httpClient.clearCookies(requireContext())
            UserSessionManager.getInstance().clearLoginInfo(requireContext())
            updateLoginState()
            com.solosu.mtforum.util.ToastUtil.makeText(
                requireContext(),
                "已退出登录", com.solosu.mtforum.util.ToastUtil.LENGTH_SHORT
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateLoginState()
    }

    


    private fun isActuallyLoggedIn(): Boolean {
        return httpClient.isLoggedIn() &&
                UserSessionManager.getInstance().isLoggedIn(requireContext())
    }

    private fun applyFrostedGlassToIcon(icon: View?) {
        if (icon != null) {
            icon.background = FrostedGlassDrawable.create(requireContext(), 10f)
        }
    }

    private fun startLogin() {
        if (!isAdded) return
        LoginBottomSheet.show(requireActivity()) {
            if (isAdded) {
                updateLoginState()
            }
        }
    }

    private fun updateLoginState() {
        if (!isActuallyLoggedIn()) {
            
            binding!!.root.visibility = View.VISIBLE
            binding!!.tvUsername.text = "未登录"
            binding!!.tvUid.text = "登录后查看个人资料"
            binding!!.tvLevel.text = ""
            binding!!.tvGroup.text = ""
            binding!!.ivAvatar.setImageResource(R.drawable.ic_account)
            binding!!.ivAvatar.setOnClickListener { startLogin() }
            binding!!.tvUsername.setOnClickListener { startLogin() }
            binding!!.tvUid.setOnClickListener { startLogin() }
            binding!!.btnAccount.setImageResource(R.drawable.ic_login)
            binding!!.btnAccount.contentDescription = "登录"
            return
        }
        binding!!.root.visibility = View.VISIBLE
        binding!!.btnAccount.setImageResource(R.drawable.ic_logout)
        binding!!.btnAccount.contentDescription = "退出登录"
        loadProfile()
    }

    



    private fun loadProfile() {
        Thread {
            try {
                
                
                val loginUid = UserSessionManager.getInstance().getUid(requireContext())
                val profileUrl: String
                if (!TextUtils.isEmpty(loginUid)) {
                    profileUrl = HttpClient.BASE_URL + "home.php?mod=space&uid=" + loginUid + "&mobile=2"
                } else {
                    profileUrl = HttpClient.BASE_URL + "home.php?mod=space&mobile=2"
                }
                val html = httpClient.get(profileUrl)

                
                if (ForumParser.isLoginPage(html)) {
                    
                    if (!isAdded) return@Thread
                    activity?.runOnUiThread {
                        if (!isAdded) return@runOnUiThread
                        httpClient.clearCookies(requireContext())
                        UserSessionManager.getInstance().clearLoginInfo(requireContext())
                        LoginBottomSheet.show(requireActivity()) {
                            if (isAdded) {
                                updateLoginState()
                            }
                        }
                        com.solosu.mtforum.util.ToastUtil.makeText(
                            requireContext(),
                            "登录已过期，请重新登录", com.solosu.mtforum.util.ToastUtil.LENGTH_SHORT
                        ).show()
                    }
                    return@Thread
                }

                
                var profile = ForumParser.parseUserProfile(html)

                if (profile == null || profile.username == null) {
                    val altUrl = HttpClient.BASE_URL + "home.php?mod=space&do=profile&mobile=2"
                    val altHtml = httpClient.get(altUrl)
                    if (!isAdded) return@Thread
                    if (ForumParser.isLoginPage(altHtml)) {
                        activity?.runOnUiThread {
                            if (!isAdded) return@runOnUiThread
                            httpClient.clearCookies(requireContext())
                            UserSessionManager.getInstance().clearLoginInfo(requireContext())
                            LoginBottomSheet.show(requireActivity()) {
                                if (isAdded) {
                                    updateLoginState()
                                }
                            }
                        }
                        return@Thread
                    }
                    profile = ForumParser.parseUserProfile(altHtml)
                }

                if (!isAdded) return@Thread
                val finalProfile = profile
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    if (finalProfile != null && finalProfile.username != null) {
                        displayProfile(finalProfile)
                    } else {
                        com.solosu.mtforum.util.ToastUtil.makeText(
                            requireContext(),
                            "无法加载用户资料，请确认已登录", com.solosu.mtforum.util.ToastUtil.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                if (!isAdded) return@Thread
                activity?.runOnUiThread {
                    if (!isAdded) return@runOnUiThread
                    com.solosu.mtforum.util.ToastUtil.makeText(
                        requireContext(),
                        "加载资料失败: " + e.message, com.solosu.mtforum.util.ToastUtil.LENGTH_SHORT
                    ).show()
                }
            }
        }.start()
    }

    


    private fun displayProfile(profile: UserProfile) {
        
        val loginInfo = HashMap<String, String>()
        if (profile.username != null) loginInfo["username"] = profile.username!!
        if (profile.uid != null) loginInfo["uid"] = profile.uid!!
        if (profile.avatarUrl != null) loginInfo["avatarUrl"] = profile.avatarUrl!!
        if (profile.level != null) loginInfo["level"] = profile.level!!
        UserSessionManager.getInstance().saveLoginInfo(requireContext(), loginInfo)

        
        val avatarUrl = profile.avatarUrl
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            Glide.with(this)
                .load(avatarUrl)
                .placeholder(R.drawable.ic_account)
                .error(R.drawable.ic_account)
                .circleCrop()
                .into(binding!!.ivAvatar)
        } else {
            binding!!.ivAvatar.setImageResource(R.drawable.ic_account)
        }

        
        binding!!.tvUsername.text = if (profile.username != null) profile.username else "未知用户"

        
        binding!!.tvUid.text = "UID: " + (if (profile.uid != null) profile.uid else "—")

        
        binding!!.tvLevel.text = if (profile.level != null) profile.level else ""

        
        binding!!.tvGroup.text = if (profile.groupName != null) profile.groupName else ""

        
        binding!!.tvThreads.text = profile.threads.toString()
        binding!!.tvReplies.text = profile.posts.toString()
        binding!!.tvFriends.text = profile.friends.toString()
        
        
        binding!!.tvFollowing.text = profile.following.toString()
        binding!!.tvFollowers.text = profile.followers.toString()

        
        binding!!.tvCredits.text = profile.credits.toString()
        binding!!.tvGold.text = profile.gold.toString()
        binding!!.tvOnlineTime.text = if (profile.onlineTime != null) profile.onlineTime else "0小时"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
