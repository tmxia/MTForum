package com.solosu.mtforum.ui.space

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.View
import android.widget.EditText
import com.solosu.mtforum.util.ToastUtil as Toast

import androidx.annotation.Nullable
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

import com.bumptech.glide.Glide
import com.solosu.mtforum.R
import com.solosu.mtforum.databinding.ActivityUserProfileBinding
import com.solosu.mtforum.model.UserProfile
import com.solosu.mtforum.network.ForumParser
import com.solosu.mtforum.network.HttpClient
import com.solosu.mtforum.ui.message.ChatActivity
import com.solosu.mtforum.ui.widget.DialogHelper
import com.solosu.mtforum.ui.widget.FrostedGlassHelper
import com.solosu.mtforum.session.DiscuzUserActionManager
import com.solosu.mtforum.session.FollowStateManager
import com.solosu.mtforum.session.UserSessionManager






class UserProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserProfileBinding
    private lateinit var httpClient: HttpClient
    private var targetUid: String? = null
    
    private var serverFollowed = false
    private var serverFollowStateKnown = false
    private var requestInFlight = false

    @Volatile
    private var destroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        com.solosu.mtforum.util.UiStyleManager.applyTheme(this)
        com.solosu.mtforum.util.ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityUserProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        FrostedGlassHelper.applyToCardViews(binding.root, this)

        httpClient = HttpClient.getInstance()

        targetUid = intent.getStringExtra("uid")
        val username = intent.getStringExtra("username")

        
        binding.toolbar.title = if (username != null) username else "用户资料"
        
        binding.toolbar.setNavigationIcon(null)
        binding.toolbar.setNavigationOnClickListener { finish() }
        com.solosu.mtforum.util.ScrollToTopHelper.attachScrollView(binding.toolbar, binding.scrollView)

        if (targetUid == null) {
            Toast.makeText(this, "缺少用户ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        
        setupProfileActions()
        loadUserProfile()
    }

    private fun canUpdateUi(): Boolean {
        return !destroyed && !isFinishing() &&
                (android.os.Build.VERSION.SDK_INT < 17 || !isDestroyed())
    }

    private fun loadUserProfile() {
        if (!canUpdateUi() || requestInFlight || TextUtils.isEmpty(targetUid)) return
        requestInFlight = true
        binding.progressBar.visibility = View.VISIBLE

        Thread({
            var profile: UserProfile? = null
            var error: String? = null
            try {
                httpClient.syncFromCookieManager()
                val cacheBust = "&_ts=" + System.currentTimeMillis()
                val profileUrl = HttpClient.BASE_URL +
                        "home.php?mod=space&uid=" + targetUid + "&mobile=2" + cacheBust
                val html = httpClient.get(profileUrl)

                if (ForumParser.isLoginPage(html)) {
                    error = "登录已过期，请重新登录"
                } else {
                    profile = ForumParser.parseUserProfile(html)
                    if (profile == null || profile.username == null) {
                        val altUrl = HttpClient.BASE_URL +
                                "home.php?mod=space&uid=" + targetUid +
                                "&do=profile&mobile=2" + cacheBust
                        val altHtml = httpClient.get(altUrl)
                        if (ForumParser.isLoginPage(altHtml)) {
                            error = "登录已过期"
                        } else {
                            profile = ForumParser.parseUserProfile(altHtml)
                        }
                    }

                    if (error == null && profile != null) {
                        
                        
                        
                        if (!isOwnProfile() && !profile.followStateKnown) {
                            val followState = FollowStateManager.queryServerFollowState(
                                applicationContext, targetUid!!
                            )
                            if (followState >= 0) {
                                profile.followed = followState == 1
                                profile.followStateKnown = true
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                error = "加载失败，请稍后重试"
            }

            val resultProfile = profile
            val resultError = error
            runOnUiThread {
                if (!canUpdateUi()) return@runOnUiThread
                requestInFlight = false
                binding.progressBar.visibility = View.GONE
                if (resultError != null) {
                    Toast.makeText(this, resultError, Toast.LENGTH_SHORT).show()
                    if (resultError.contains("登录已过期")) finish()
                } else if (resultProfile != null && resultProfile.username != null) {
                    serverFollowStateKnown = resultProfile.followStateKnown
                    if (serverFollowStateKnown) serverFollowed = resultProfile.followed
                    displayProfile(resultProfile)
                } else {
                    Toast.makeText(this, "无法加载用户资料", Toast.LENGTH_SHORT).show()
                }
            }
        }, "user-profile-load").start()
    }

    
    private fun isOwnProfile(): Boolean {
        if (TextUtils.isEmpty(targetUid)) return false
        val currentUid = UserSessionManager.getInstance().getUid(this)
        return !TextUtils.isEmpty(currentUid) &&
                "0" != currentUid &&
                currentUid == targetUid
    }

    private fun setupProfileActions() {
        binding.layoutProfileActions.visibility = View.GONE
        binding.btnProfileFriend.setOnClickListener { showFriendDialog() }
        binding.btnProfilePoke.setOnClickListener { showPokeDialog() }
        binding.btnProfileMessage.setOnClickListener { openChat() }
        binding.btnProfileBlock.setOnClickListener { showBlockDialog() }
    }

    
    private fun openChat() {
        if (!canUseProfileAction()) return

        var chatName = if (binding.tvUsername.text == null)
            "用户"
        else
            binding.tvUsername.text.toString().trim()
        if (TextUtils.isEmpty(chatName)) chatName = "用户"

        val intent = Intent(this, ChatActivity::class.java)
        
        intent.putExtra(ChatActivity.EXTRA_PMID, targetUid)
        intent.putExtra(ChatActivity.EXTRA_UID, targetUid)
        intent.putExtra(ChatActivity.EXTRA_NAME, chatName)
        startActivity(intent)
    }

    private fun canUseProfileAction(): Boolean {
        if (TextUtils.isEmpty(targetUid)) return false
        if (!FollowStateManager.isLoggedIn(this)) {
            com.solosu.mtforum.ui.login.LoginBottomSheet.show(this, null)
            return false
        }
        return true
    }

    private fun makeInput(hint: String, multiLine: Boolean): EditText {
        val input = EditText(this)
        input.setHint(hint)
        input.isSingleLine = !multiLine
        if (multiLine) {
            input.setMinLines(3)
            input.gravity = android.view.Gravity.TOP
            input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        val pad = (16 * resources.displayMetrics.density).toInt()
        input.setPadding(pad, pad / 2, pad, pad / 2)
        return input
    }

    private fun showFriendDialog() {
        if (!canUseProfileAction()) return
        val input = makeInput("附加留言（可选）", true)
        val alertDialog1: android.app.Dialog = AlertDialog.Builder(this)
            .setTitle("添加好友")
            .setView(input)
            .setPositiveButton("发送申请") { dialog, which ->
                val note = input.text.toString().trim()
                runUserAction(
                    "正在发送好友申请…",
                    { DiscuzUserActionManager.addFriend(this, targetUid, note) },
                    "好友申请已发送", "好友申请发送失败"
                )
            }
            .show()
        DialogHelper.applyToAlertDialog(alertDialog1, this)
    }

    private fun showPokeDialog() {
        if (!canUseProfileAction()) return
        val input = makeInput("招呼内容，例如：你好！", false)
        input.setText("你好！")
        val alertDialog2: android.app.Dialog = AlertDialog.Builder(this)
            .setTitle("打招呼")
            .setView(input)
            .setPositiveButton("发送") { dialog, which ->
                val message = input.text.toString().trim()
                if (TextUtils.isEmpty(message)) {
                    Toast.makeText(this, "请输入招呼内容", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                runUserAction(
                    "正在发送招呼…",
                    { DiscuzUserActionManager.sendPoke(this, targetUid, message) },
                    "打招呼成功", "打招呼失败"
                )
            }
            .show()
        DialogHelper.applyToAlertDialog(alertDialog2, this)
    }

    private fun showBlockDialog() {
        if (!canUseProfileAction()) return
        val name = if (binding.tvUsername.text == null)
            "该用户"
        else
            binding.tvUsername.text.toString()
        val alertDialog3: android.app.Dialog = AlertDialog.Builder(this)
            .setTitle("屏蔽用户")
            .setMessage(
                "屏蔽“" + name + "”后将减少看到该用户的内容（加入黑名单）。" +
                        "如需恢复，可再次打开本窗口选择“取消屏蔽”。"
            )
            .setPositiveButton("屏蔽") { dialog, which ->
                runUserAction(
                    "正在屏蔽用户…",
                    { DiscuzUserActionManager.blockUser(this, targetUid) },
                    "已屏蔽该用户", "屏蔽用户失败"
                )
            }
            .setNeutralButton("取消屏蔽") { dialog, which ->
                runUserAction(
                    "正在取消屏蔽…",
                    { DiscuzUserActionManager.unblockUser(this, targetUid) },
                    "已取消屏蔽", "取消屏蔽失败"
                )
            }
            .show()
        DialogHelper.applyToAlertDialog(alertDialog3, this)
    }

    private fun interface UserAction {
        fun run(): Boolean
    }

    private fun runUserAction(
        loadingText: String, action: UserAction,
        successText: String, failureText: String
    ) {
        binding.layoutProfileActions.isEnabled = false
        Toast.makeText(this, loadingText, Toast.LENGTH_SHORT).show()
        Thread {
            val success = action.run()
            runOnUiThread {
                binding.layoutProfileActions.isEnabled = true
                Toast.makeText(
                    this, if (success) successText else failureText,
                    Toast.LENGTH_SHORT
                ).show()
                if (success && "已屏蔽该用户" == successText) finish()
            }
        }.start()
    }

    private fun displayProfile(profile: UserProfile) {
        
        val ownProfile = isOwnProfile()
        val loggedIn = !ownProfile &&
                FollowStateManager.isLoggedIn(this) &&
                !TextUtils.isEmpty(targetUid)
        binding.layoutProfileActions.visibility = if (loggedIn) View.VISIBLE else View.GONE
        binding.btnProfileFollow.visibility = if (loggedIn) View.VISIBLE else View.GONE
        if (loggedIn) {
            if (serverFollowStateKnown) {
                binding.btnProfileFollow.isEnabled = !requestInFlight
                binding.btnProfileFollow.setText(
                    if (serverFollowed)
                        R.string.action_followed else R.string.action_follow
                )
                binding.btnProfileFollow.setOnClickListener { toggleFollowProfile() }
            } else {
                
                binding.btnProfileFollow.isEnabled = false
                binding.btnProfileFollow.text = "状态同步中"
                binding.btnProfileFollow.setOnClickListener(null)
            }
        } else {
            binding.btnProfileFollow.setOnClickListener(null)
            binding.btnProfileFollow.isEnabled = false
        }

        
        if (profile.username != null) {
            binding.toolbar.title = profile.username
        }

        
        val avatarUrl = profile.avatarUrl
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            Glide.with(this)
                .load(avatarUrl)
                .placeholder(R.drawable.ic_account)
                .error(R.drawable.ic_account)
                .circleCrop()
                .into(binding.ivAvatar)
        } else {
            binding.ivAvatar.setImageResource(R.drawable.ic_account)
        }

        
        binding.tvUsername.text = if (profile.username != null) profile.username else "未知用户"

        
        binding.tvUid.text = "UID: " + (if (profile.uid != null) profile.uid else "—")

        
        binding.tvLevel.text = if (profile.level != null) profile.level else ""

        
        binding.tvGroup.text = if (profile.groupName != null) profile.groupName else ""

        
        binding.tvThreads.text = profile.threads.toString()
        binding.tvReplies.text = profile.posts.toString()
        binding.tvFriends.text = profile.friends.toString()
        
        binding.tvFollowing.text = profile.following.toString()
        binding.tvFollowers.text = profile.followers.toString()

        
        binding.tvCredits.text = profile.credits.toString()
        binding.tvGold.text = profile.gold.toString()
        binding.tvOnlineTime.text = if (profile.onlineTime != null) profile.onlineTime else "0小时"

        
        binding.tvRegDate.text = if (profile.regDate != null) profile.regDate else "—"
        binding.tvLastVisit.text = if (profile.lastVisit != null) profile.lastVisit else "—"

        
        binding.layoutThreads.setOnClickListener {
            val intent = Intent(this, SpaceThreadListActivity::class.java)
            intent.putExtra("mode", "uid_threads")
            intent.putExtra("uid", targetUid)
            intent.putExtra("username", binding.tvUsername.text.toString())
            startActivity(intent)
        }

        
        var genderText = "保密"
        if ("boy" == profile.gender) {
            genderText = "男 ♂"
        } else if ("girl" == profile.gender) {
            genderText = "女 ♀"
        }
        binding.tvGender.text = genderText
    }

    private fun toggleFollowProfile() {
        if (TextUtils.isEmpty(targetUid) || requestInFlight || !serverFollowStateKnown) return
        
        val targetState = !serverFollowed
        requestInFlight = true
        binding.btnProfileFollow.isEnabled = false
        Thread {
            val success = FollowStateManager.syncFollow(this@UserProfileActivity, targetUid!!, targetState)
            if (success) {
                
                runOnUiThread {
                    Toast.makeText(
                        this, if (targetState)
                            R.string.action_follow_success
                        else
                            R.string.action_unfollow_success, Toast.LENGTH_SHORT
                    ).show()
                    requestInFlight = false
                    loadUserProfile()
                }
            } else {
                runOnUiThread {
                    requestInFlight = false
                    binding.btnProfileFollow.isEnabled = serverFollowStateKnown
                    Toast.makeText(this, "关注操作失败，请稍后重试", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    override fun onDestroy() {
        destroyed = true
        requestInFlight = false
        super.onDestroy()
    }
}
