package com.solosu.mtforum.ui.community

import android.content.Intent
import android.text.TextUtils
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.solosu.mtforum.util.ToastUtil as Toast

import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager

import com.solosu.mtforum.R
import com.solosu.mtforum.adapter.ForumGridAdapter
import com.solosu.mtforum.databinding.FragmentCommunityBinding
import com.solosu.mtforum.ui.widget.FrostedGlassDrawable
import com.solosu.mtforum.ui.widget.FrostedGlassHelper
import com.solosu.mtforum.ui.widget.NavBarAutoHideHelper
import com.solosu.mtforum.model.ForumCategory
import com.solosu.mtforum.network.ForumParser
import com.solosu.mtforum.network.HttpClient
import com.solosu.mtforum.session.UserSessionManager
import com.solosu.mtforum.ui.forum.ForumDetailActivity

import java.util.HashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CommunityFragment : Fragment() {

    private var binding: FragmentCommunityBinding? = null
    private lateinit var httpClient: HttpClient
    private lateinit var forumGridAdapter: ForumGridAdapter
    private var currentFormhash: String? = null

    @Nullable
    override fun onCreateView(
        @NonNull inflater: LayoutInflater,
        @Nullable container: ViewGroup?,
        @Nullable savedInstanceState: Bundle?
    ): View {
        binding = FragmentCommunityBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(@NonNull view: View, @Nullable savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        FrostedGlassHelper.applyToCardViews(view, requireContext())

        httpClient = HttpClient.getInstance()
        

        
        forumGridAdapter = ForumGridAdapter(requireContext())
        binding!!.rvForumGrid.layoutManager = GridLayoutManager(requireContext(), 2)
        binding!!.rvForumGrid.adapter = forumGridAdapter
        
        val navScroll = view.findViewById<androidx.core.widget.NestedScrollView>(R.id.nav_scroll_community)
        if (navScroll != null) {
            
            navScroll.setOnScrollChangeListener { v, sx, sy, osx, osy ->
                NavBarAutoHideHelper.onScrolled(activity, sy - osy)
            }
        }
        forumGridAdapter.setOnForumClickListener(object : ForumGridAdapter.OnForumClickListener {
            override fun onForumClick(forum: ForumCategory.Forum?, position: Int) {
                val intent = Intent(requireContext(), ForumDetailActivity::class.java)
                intent.putExtra("fid", forum!!.fid)
                intent.putExtra("forumName", forum.name)
                intent.putExtra("description", forum.description)
                intent.putExtra("iconUrl", forum.iconUrl)
                intent.putExtra("totalPosts", forum.totalPosts)
                intent.putExtra("totalThreads", forum.totalThreads)
                startActivity(intent)
            }
        })

        
        com.solosu.mtforum.util.ScrollToTopHelper.attachNestedScrollView(binding!!.layoutTitleBar, binding!!.navScrollCommunity)
        com.solosu.mtforum.util.ScrollToTopHelper.attachNestedScrollView(binding!!.tvTitleDiscover, binding!!.navScrollCommunity)

        
        binding!!.btnSignIn.setOnClickListener {
            
            if (!httpClient.isLoggedIn()) {
                Toast.makeText(requireContext(), R.string.login_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (currentFormhash == null || currentFormhash!!.isEmpty()) {
                Toast.makeText(requireContext(), "formhash 获取失败，请刷新页面", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            binding!!.btnSignIn.isEnabled = false
            binding!!.btnSignIn.text = "签到中..."
            performSignIn()
        }

        
        loadCommunityData()
    }

    



    


    




    private fun querySignInStatus(): Boolean {
        return try {
            val url = HttpClient.BASE_URL + "plugin.php?id=k_misign:sign&mobile=2"
            val signHtml = httpClient.get(url) ?: return false

            val signedExact = listOf(
                ">已签到</a>",
                ">已签到</div>",
                "class=\"comiis_btn bg_b f_c\">已签到",
                "您今天已经签到",
                "今日已签到",
                "已经签到过",
            )
            val result = signedExact.any { signHtml.contains(it) }
            android.util.Log.d("SignCheck", "len=${signHtml.length} result=$result")
            result
        } catch (e: Exception) {
            android.util.Log.e("SignCheck", "query failed: ${e.message}")
            false
        }
    }

    private fun loadCommunityData() {
        

        java.lang.Thread {
            try {
                val html = httpClient.get(ForumParser.getForumlistMobileUrl())
                val data = ForumParser.parseCommunityPage(html)

                
                val serverSignedIn = querySignInStatus()

                activity?.runOnUiThread {
                    if (binding == null) return@runOnUiThread

                    
                    currentFormhash = data.formhash
                    if (currentFormhash != null && !currentFormhash!!.isEmpty()) {
                        binding!!.btnSignIn.isEnabled = true
                    }

                    
                    val alreadySignedIn = serverSignedIn  
                    if (alreadySignedIn) {
                        showAlreadySignedIn()
                    } else if (data.signInText != null && !data.signInText.isEmpty()) {
                        val isSigned = data.signInText.contains("已")
                        val cleanText = if (isSigned) "已签到" else "签到"
                        binding!!.tvSignInStatus.text = cleanText
                        binding!!.btnSignIn.text = cleanText
                        
                        if (isSigned) {
                            binding!!.btnSignIn.isEnabled = false
                            applySignedInButtonStyle()
                            
                            UserSessionManager.getInstance().saveSignInDate(requireContext())
                        }
                    }

                    
                    val dedupMap = LinkedHashMap<String, ForumCategory.Forum>()
                    val forums = data.forums
                    if (forums != null && !forums.isEmpty()) {
                        for (f in forums) {
                            dedupMap[f.fid!!] = f
                        }
                    } else {
                        
                        val categories = data.categories
                        if (categories != null) {
                            for (cat in categories) {
                                if (cat.forums != null) {
                                    for (f in cat.forums!!) {
                                        dedupMap[f.fid!!] = f
                                    }
                                }
                            }
                        }
                    }
                    forumGridAdapter.setForumList(ArrayList<ForumCategory.Forum>(dedupMap.values))

                    
                    val gridForums = forumGridAdapter.getForumList()
                    if (gridForums != null && !gridForums.isEmpty()) {
                        enrichForumsWithStatsAndDescription(gridForums)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                activity?.runOnUiThread {
                    if (binding != null) {
                        binding!!.tvSignInStatus.setText(R.string.network_error)
                    }
                }
            }
        }.start()
    }

    



    





    private fun enrichForumsWithStatsAndDescription(forums: MutableList<ForumCategory.Forum>) {
        java.lang.Thread {
            try {
                
                val desktopHtml = httpClient.get(ForumParser.getBaseDomain() + "forum.php?forumlist=1&mobile=no")
                val statsMap = ForumParser.parseDesktopForumStats(desktopHtml)
                if (statsMap != null && !statsMap.isEmpty()) {
                    for (i in forums.indices) {
                        val f = forums[i]
                        val st = statsMap[f.fid]
                        if (st == null) continue
                        if (st[1] > 0) f.totalPosts = Math.min(st[1], Int.MAX_VALUE.toLong()).toInt()
                        if (st[0] > 0) f.totalThreads = Math.min(st[0], Int.MAX_VALUE.toLong()).toInt()
                        if (st[2] > 0) f.todayPosts = Math.min(st[2], Int.MAX_VALUE.toLong()).toInt()
                    }
                    val snapshot = ArrayList<ForumCategory.Forum>(forums)
                    activity?.runOnUiThread {
                        if (binding != null && forumGridAdapter != null) {
                            forumGridAdapter.setForumList(snapshot)
                        }
                    }
                }
            } catch (ignored: Exception) {
                
            }
        }.start()
    }

    private fun extractSignMessage(raw: String?): String {
        if (raw == null || raw.isEmpty()) return ""
        
        var matcher = java.util.regex.Pattern.compile(
            "<!\\[CDATA\\[(.*?)\\]\\]>",
            java.util.regex.Pattern.DOTALL
        ).matcher(raw)
        if (matcher.find()) {
            return matcher.group(1).trim()
        }
        
        matcher = java.util.regex.Pattern.compile(
            "<root>(.*?)</root>",
            java.util.regex.Pattern.DOTALL
        ).matcher(raw)
        if (matcher.find()) {
            return matcher.group(1).trim()
        }
        
        val cleaned = raw.replace(Regex("<[^>]+>"), "").trim()
        if (!cleaned.isEmpty()) return cleaned
        return raw.trim()
    }

    private fun showAlreadySignedIn() {
        if (binding == null) return
        binding!!.tvSignInStatus.text = "已签到"
        binding!!.btnSignIn.text = "已签到"
        binding!!.btnSignIn.isEnabled = false
        applySignedInButtonStyle()
    }

    




    private fun applySignedInButtonStyle() {
        val ctx = requireContext()
        val isIos = com.solosu.mtforum.util.UiStyleManager.isIos(ctx)
        val density = resources.displayMetrics.density
        val bg = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 20 * density
            if (isIos) {
                setColor(0xFFE5E5EA.toInt())
                binding!!.btnSignIn.setTextColor(0xFF8E8E93.toInt())
            } else {
                val themeColor = com.solosu.mtforum.util.ThemeManager.getThemeColor(ctx)
                setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(themeColor, 0x26))
                binding!!.btnSignIn.setTextColor(themeColor)
            }
        }
        binding!!.btnSignIn.background = bg
    }

    


    private fun performSignIn() {
        java.lang.Thread {
            try {
                val signUrl = HttpClient.BASE_URL + "plugin.php?id=k_misign:sign&operation=qiandao&format=text"
                val params = HashMap<String, String>()
                params["formhash"] = currentFormhash!!
                val result = httpClient.post(signUrl, params)

                activity?.runOnUiThread {
                    if (binding == null) return@runOnUiThread

                    
                    val msg = if (result != null) extractSignMessage(result) else ""
                    
                    val alreadySigned = isAlreadySignedMessage(msg)
                    val isSuccess = !alreadySigned && isSuccessfulSignResponse(msg, result)

                    if (isSuccess || alreadySigned) {
                        val displayMsg = if (msg.isEmpty()) (if (alreadySigned) "今日已签到" else "签到成功") else msg
                        Toast.makeText(requireContext(), displayMsg, Toast.LENGTH_SHORT).show()
                        showAlreadySignedIn()
                        if (isSuccess) {
                            playRandomSignInSound()
                        }
                        
                        UserSessionManager.getInstance().saveSignInDate(requireContext())
                    } else {
                        val displayMsg = if (!msg.isEmpty()) msg else (if (result != null) result.trim() else "签到失败")
                        Toast.makeText(requireContext(), displayMsg, Toast.LENGTH_SHORT).show()
                        binding!!.btnSignIn.isEnabled = true
                        binding!!.btnSignIn.setText(R.string.action_sign_in)
                    }
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    if (binding == null) return@runOnUiThread
                    Toast.makeText(requireContext(), "签到失败: " + e.message, Toast.LENGTH_SHORT).show()
                    binding!!.btnSignIn.isEnabled = true
                    binding!!.btnSignIn.setText(R.string.action_sign_in)
                }
            }
        }.start()
    }

    private fun isAlreadySignedMessage(text: String?): Boolean {
        if (text == null) return false
        return text.contains("今日已签") || text.contains("已签到")
                || text.contains("已经签到") || text.contains("已签")
    }

    private fun isSuccessfulSignResponse(message: String?, rawResult: String?): Boolean {
        val text = if (message == null) "" else message.trim()
        val raw = if (rawResult == null) "" else rawResult.trim()
        val lower = text.lowercase()
        if (text.isEmpty() && raw.isEmpty()) return false
        return !(text.contains("失败") || text.contains("错误") || text.contains("异常")
                || text.contains("请先登录") || text.contains("没有权限") || text.contains("非法操作")
                || lower.contains("fail") || lower.contains("error"))
    }

    private fun playRandomSignInSound() {
        if (isAdded && context != null) {
        }
    }

    



    override fun onResume() {
        super.onResume()
        currentInstance = this
    }

    override fun onPause() {
        super.onPause()
        currentInstance = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    companion object {

        private var currentInstance: CommunityFragment? = null

        


        @JvmStatic
        fun refreshSignIn() {
            val instance = currentInstance ?: return
            if (instance.binding == null) return
            instance.activity?.runOnUiThread {
                if (instance.binding == null) return@runOnUiThread
                if (UserSessionManager.getInstance().isSignedInToday(instance.requireContext())) {
                    instance.showAlreadySignedIn()
                } else {
                    instance.binding!!.btnSignIn.isEnabled = true
                    instance.binding!!.btnSignIn.setText(R.string.action_sign_in)
                    instance.binding!!.btnSignIn.setBackgroundResource(R.drawable.rounded_btn_primary)
                }
            }
        }
    }
}
