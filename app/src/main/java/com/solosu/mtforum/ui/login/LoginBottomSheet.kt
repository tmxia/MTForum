package com.solosu.mtforum.ui.login

import android.app.Activity
import android.text.TextUtils
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.solosu.mtforum.R
import com.solosu.mtforum.model.UserProfile
import com.solosu.mtforum.network.ForumParser
import com.solosu.mtforum.network.HttpClient
import com.solosu.mtforum.session.UserSessionManager
import com.solosu.mtforum.session.RememberedCredentials
import com.google.android.material.checkbox.MaterialCheckBox
import com.solosu.mtforum.ui.widget.DialogHelper
import com.solosu.mtforum.ui.widget.FrostedGlassHelper

import java.lang.ref.WeakReference
import java.util.HashMap





object LoginBottomSheet {

    fun interface OnLoginListener {
        fun onLoginSuccess()
    }

    private var current: BottomSheetDialog? = null

    @JvmStatic
    fun show(activity: Activity?, listener: OnLoginListener?) {
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            return
        }
        dismissCurrent()
        val dialog = BottomSheetDialog(activity)
        current = dialog
        val activityRef = WeakReference(activity)
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_login_bottom_sheet, null)

        val dialogCard = dialogView.findViewById<MaterialCardView>(R.id.dialog_card)
        val ivClose = dialogView.findViewById<ImageView>(R.id.iv_close)
        val formNormal = dialogView.findViewById<LinearLayout>(R.id.login_form_normal)
        val formCookie = dialogView.findViewById<LinearLayout>(R.id.login_form_cookie)
        val etUsername = dialogView.findViewById<TextInputEditText>(R.id.et_username)
        val etPassword = dialogView.findViewById<TextInputEditText>(R.id.et_password)
        val etCookie = dialogView.findViewById<TextInputEditText>(R.id.et_cookie)
        val tilUsername = dialogView.findViewById<TextInputLayout>(R.id.til_username)
        val tilPassword = dialogView.findViewById<TextInputLayout>(R.id.til_password)
        val tilCookie = dialogView.findViewById<TextInputLayout>(R.id.til_cookie)
        val btnLogin = dialogView.findViewById<MaterialButton>(R.id.btn_login)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progress_bar)
        val tvError = dialogView.findViewById<TextView>(R.id.tv_error)
        val tvToggle = dialogView.findViewById<TextView>(R.id.tv_toggle_mode)
        val cbRemember = dialogView.findViewById<MaterialCheckBox>(R.id.cb_remember)

        
        val isCookieMode = booleanArrayOf(false)

        if (dialogCard != null) {
            FrostedGlassHelper.applyToCardViews(dialogCard, activity)
        }

        dialog.setContentView(dialogView)
        DialogHelper.applyToBottomSheet(dialog, dialogView, activity)
        dialog.setOnDismissListener {
            if (current === dialog) {
                current = null
            }
        }

        
        dialog.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        
        
        val formhashRef = arrayOfNulls<String>(2)
        Thread {
            try {
                
                
                if (HttpClient.getInstance().isLoggedIn()) {
                    HttpClient.getInstance().clearCookies(activity)
                }
                val html = HttpClient.getInstance().get(ForumParser.getLoginUrl())
                val fh = ForumParser.parseFormhash(html)
                if (!TextUtils.isEmpty(fh)) {
                    formhashRef[0] = fh
                    formhashRef[1] = ForumParser.extractLoginPostUrl(html)
                }
            } catch (ignored: Exception) {
            }
        }.start()

        ivClose!!.setOnClickListener { dialog.dismiss() }

        
        tvToggle!!.setOnClickListener {
            isCookieMode[0] = !isCookieMode[0]
            val cookie = isCookieMode[0]
            formNormal!!.visibility = if (cookie) View.GONE else View.VISIBLE
            formCookie!!.visibility = if (cookie) View.VISIBLE else View.GONE
            cbRemember?.let { it.visibility = if (cookie) View.GONE else View.VISIBLE }
            tvError!!.visibility = View.GONE
            if (cookie) {
                tvToggle.text = "账号密码登录"
                btnLogin!!.text = "Cookie 登录"
            } else {
                tvToggle.text = "Cookie 登录"
                btnLogin!!.text = activity.getString(R.string.action_login)
            }
        }

        
        try {
            val saved = RememberedCredentials.load(activity)
            if (saved != null) {
                etUsername?.setText(saved.username)
                etPassword?.setText(saved.password)
                cbRemember?.isChecked = true
            }
        } catch (ignored: Exception) {
        }

        
        etPassword!!.setOnEditorActionListener { v, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                performLogin(
                    dialog, activityRef, formNormal!!, formCookie!!, etUsername!!, etPassword,
                    etCookie!!, tilUsername!!, tilPassword!!, tilCookie!!, btnLogin!!, progressBar!!,
                    tvError!!, formhashRef, isCookieMode, listener
                )
                return@setOnEditorActionListener true
            }
            false
        }

        btnLogin!!.setOnClickListener {
            performLogin(
                dialog, activityRef, formNormal!!, formCookie!!,
                etUsername!!, etPassword, etCookie!!, tilUsername, tilPassword!!, tilCookie!!, btnLogin,
                progressBar!!, tvError!!, formhashRef, isCookieMode, listener
            )
        }

        dialog.setOnShowListener {
            val parent = dialogView.parent as? View
            if (parent != null) {
                parent.setBackgroundResource(android.R.color.transparent)
                val behavior = BottomSheetBehavior.from(parent)
                behavior.skipCollapsed = true
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                parent.viewTreeObserver.addOnGlobalLayoutListener(
                    object : ViewTreeObserver.OnGlobalLayoutListener {
                        override fun onGlobalLayout() {
                            parent.viewTreeObserver.removeOnGlobalLayoutListener(this)
                            val contentHeight = dialogView.height
                            if (contentHeight > 0) {
                                behavior.peekHeight = contentHeight + dp(activity, 48f)
                            }
                        }
                    }
                )
            }
        }

        dialog.show()
    }

    private fun dp(activity: Activity, dp: Float): Int {
        return Math.round(dp * activity.resources.displayMetrics.density)
    }

    private fun dismissCurrent() {
        val c = current
        if (c != null && c.isShowing) {
            c.dismiss()
        }
        current = null
    }

    private fun performLogin(
        dialog: BottomSheetDialog,
        activityRef: WeakReference<Activity>?,
        formNormal: LinearLayout,
        formCookie: LinearLayout,
        etUsername: TextInputEditText,
        etPassword: TextInputEditText,
        etCookie: TextInputEditText,
        tilUsername: TextInputLayout,
        tilPassword: TextInputLayout,
        tilCookie: TextInputLayout,
        btnLogin: MaterialButton,
        progressBar: ProgressBar,
        tvError: TextView,
        formhashRef: Array<String?>,
        isCookieMode: BooleanArray,
        listener: OnLoginListener?
    ) {
        val activity = if (activityRef != null) activityRef.get() else null
        if (activity == null || activity.isFinishing) {
            return
        }

        if (isCookieMode[0]) {
            doCookieLogin(
                dialog, activity, formNormal, formCookie, etCookie, tilCookie,
                btnLogin, progressBar, tvError, listener
            )
        } else {
            doPasswordLogin(
                dialog, activity, etUsername, etPassword, tilUsername, tilPassword,
                btnLogin, progressBar, tvError, formhashRef, listener
            )
        }
    }

    
    private fun snippet(html: String?): String {
        if (html == null) return "(无响应)"
        return try {
            val text = if (TextUtils.isEmpty(html)) "" else html
                .replace(Regex("(?s)<script.*?</script>"), " ")
                .replace(Regex("(?s)<style.*?</style>"), " ")
                .replace(Regex("<[^>]+>"), " ")
                .replace(Regex("&#\\d+;"), " ").replace(Regex("&[a-z]+;"), " ")
                .replace(Regex("\\s+"), " ").trim()
            if (text.length > 80) text.substring(0, 80) else text
        } catch (e: Exception) {
            "(解析失败)"
        }
    }

    
    private fun doPasswordLogin(
        dialog: BottomSheetDialog,
        activity: Activity,
        etUsername: TextInputEditText,
        etPassword: TextInputEditText,
        tilUsername: TextInputLayout,
        tilPassword: TextInputLayout,
        btnLogin: MaterialButton,
        progressBar: ProgressBar,
        tvError: TextView,
        formhashRef: Array<String?>,
        listener: OnLoginListener?
    ) {
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString()

        if (TextUtils.isEmpty(username)) {
            tilUsername.error = "请输入帐号"
            etUsername.requestFocus()
            return
        }
        tilUsername.error = null
        if (TextUtils.isEmpty(password)) {
            tilPassword.error = "请输入密码"
            etPassword.requestFocus()
            return
        }
        tilPassword.error = null

        setLoading(btnLogin, progressBar, true)
        tvError.visibility = View.GONE

        Thread {
            try {
                if (TextUtils.isEmpty(formhashRef[0])) {
                    
                    
                    if (HttpClient.getInstance().isLoggedIn()) {
                        HttpClient.getInstance().clearCookies(activity)
                    }
                    val loginHtml = HttpClient.getInstance().get(ForumParser.getLoginUrl())
                    formhashRef[0] = ForumParser.parseFormhash(loginHtml)
                    if (!TextUtils.isEmpty(formhashRef[0])) {
                        formhashRef[1] = ForumParser.extractLoginPostUrl(loginHtml)
                    }
                    
                    if (TextUtils.isEmpty(formhashRef[0])) {
                        val msg: String
                        if (loginHtml == null) {
                            msg = "网络错误,取不到登录页(检查网络后重试)"
                        } else if (loginHtml.contains("you have been blocked")
                            || loginHtml.contains("403 Forbidden")
                        ) {
                            msg = "被站点防护拦截(403),请求太频繁,稍等几分钟再试"
                        } else if (!loginHtml.contains("formhash")) {
                            msg = "登录页异常(可能被风控),稍后再试或换Cookie方式登录"
                        } else {
                            msg = "解析登录页失败,请重新点登录"
                        }
                        runOnUi(activity) {
                            setLoading(btnLogin, progressBar, false)
                            showError(tvError, msg)
                        }
                        return@Thread
                    }
                }
                val params = HashMap<String, String>()
                params["formhash"] = formhashRef[0]!!
                params["username"] = username
                params["password"] = password
                params["loginsubmit"] = "yes"
                params["fastloginfield"] = "username"
                params["cookietime"] = "2592000"

                
                val postUrl = if (TextUtils.isEmpty(formhashRef[1]))
                    ForumParser.getLoginPostUrl() else formhashRef[1]!!
                val result = HttpClient.getInstance().post(postUrl, params)

                if (HttpClient.getInstance().isLoggedIn()) {
                    finishLoginSuccess(dialog, activity, username, btnLogin, progressBar, listener)
                } else {
                    
                    var errorMsg = "登录失败:" + snippet(result)
                    if (result.contains("密码错误") || (result.contains("密码") && result.contains("错误"))) {
                        errorMsg = "密码错误"
                    } else if (result.contains("用户名") && result.contains("不存在")) {
                        errorMsg = "用户名不存在"
                    } else if (result.contains("次数过多") || result.contains("次数超")) {
                        errorMsg = "密码错误次数过多,临时锁定,请稍后再试"
                    } else if (result.contains("您已经被禁止访问") || result.contains("禁止登录")
                        || result.contains("禁止访问")
                    ) {
                        errorMsg = "账号被禁/封禁,无法登录"
                    } else if (result.contains("您已经登录") || result.contains("已经登录")) {
                        errorMsg = "服务器认为当前会话已登录(切换账号场景):重试一次即可"
                    } else if (result.contains("you have been blocked") || result.contains("403 Forbidden")) {
                        errorMsg = "被站点防护拦截(403),请求太频繁,稍等几分钟再试"
                    }
                    val msg = errorMsg
                    runOnUi(activity) {
                        setLoading(btnLogin, progressBar, false)
                        showError(tvError, msg)
                    }
                }
            } catch (e: Exception) {
                runOnUi(activity) {
                    setLoading(btnLogin, progressBar, false)
                    showError(
                        tvError, "网络错误:" + (if (TextUtils.isEmpty(e.message))
                            "请稍后重试" else e.message)
                    )
                }
            }
        }.start()
    }

    

    private fun doCookieLogin(
        dialog: BottomSheetDialog,
        activity: Activity,
        formNormal: LinearLayout,
        formCookie: LinearLayout,
        etCookie: TextInputEditText,
        tilCookie: TextInputLayout,
        btnLogin: MaterialButton,
        progressBar: ProgressBar,
        tvError: TextView,
        listener: OnLoginListener?
    ) {
        val cookieStr = etCookie.text.toString().trim()

        if (TextUtils.isEmpty(cookieStr)) {
            tilCookie.error = "请粘贴 Cookie 内容"
            etCookie.requestFocus()
            return
        }
        tilCookie.error = null

        setLoading(btnLogin, progressBar, true)
        tvError.visibility = View.GONE

        Thread {
            try {
                
                HttpClient.getInstance().clearCookies()
                HttpClient.getInstance().applyCookiesFromString(cookieStr)

                
                if (!HttpClient.getInstance().isLoggedIn()) {
                    runOnUi(activity) {
                        setLoading(btnLogin, progressBar, false)
                        showError(tvError, "Cookie 无效或已过期,请检查后重试")
                    }
                    return@Thread
                }

                
                val verifyUrl = ForumParser.getBaseDomain() + "home.php?mod=space&do=profile&mobile=2"
                val verifyHtml: String
                try {
                    verifyHtml = HttpClient.getInstance().get(verifyUrl)
                } catch (e: Exception) {
                    runOnUi(activity) {
                        setLoading(btnLogin, progressBar, false)
                        showError(tvError, "Cookie 验证失败:" + (if (e.message != null) e.message else "网络异常"))
                    }
                    return@Thread
                }

                if (ForumParser.isLoginPage(verifyHtml)) {
                    runOnUi(activity) {
                        setLoading(btnLogin, progressBar, false)
                        showError(tvError, "Cookie 已过期,请重新获取")
                    }
                    return@Thread
                }

                
                HttpClient.getInstance().commitCookieStore(activity)
                try {
                    val profile = ForumParser.parseUserProfile(verifyHtml)
                    val loginInfo = HashMap<String, String>()
                    loginInfo["username"] = if (profile != null && profile.username != null)
                        profile.username!! else "用户"
                    loginInfo["uid"] = if (profile != null && profile.uid != null) profile.uid!! else "0"
                    loginInfo["avatarUrl"] = if (profile != null && profile.avatarUrl != null)
                        profile.avatarUrl!! else ""
                    loginInfo["level"] = if (profile != null && profile.level != null) profile.level!! else ""
                    UserSessionManager.getInstance().saveLoginInfo(activity, loginInfo)
                } catch (ignored: Exception) {
                }

                runOnUi(activity) {
                    setLoading(btnLogin, progressBar, false)
                    if (dialog.isShowing) {
                        dialog.dismiss()
                    }
                    listener?.onLoginSuccess()
                }
            } catch (e: Exception) {
                runOnUi(activity) {
                    setLoading(btnLogin, progressBar, false)
                    showError(
                        tvError, "Cookie 登录失败:" + (if (TextUtils.isEmpty(e.message))
                            "请稍后重试" else e.message)
                    )
                }
            }
        }.start()
    }

    

    private fun finishLoginSuccess(
        dialog: BottomSheetDialog, activity: Activity,
        username: String, btnLogin: MaterialButton,
        progressBar: ProgressBar, listener: OnLoginListener?
    ) {
        
        try {
            val cb = dialog.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cb_remember)
            val etU = dialog.findViewById<TextInputEditText>(R.id.et_username)
            val etP = dialog.findViewById<TextInputEditText>(R.id.et_password)
            if (cb?.isChecked == true) {
                val u = etU?.text?.toString()?.trim().orEmpty()
                val pw = etP?.text?.toString().orEmpty()
                if (u.isNotEmpty() && pw.isNotEmpty()) {
                    RememberedCredentials.save(activity, u, pw)
                }
            } else {
                RememberedCredentials.clear(activity)
            }
        } catch (ignored: Exception) {
        }

        
        
        HttpClient.getInstance().commitCookieStore(activity)
        
        try {
            val spaceHtml = HttpClient.getInstance().get(
                ForumParser.getBaseDomain() + "home.php?mod=space&do=profile&mobile=2"
            )
            val profile = ForumParser.parseUserProfile(spaceHtml)
            if (profile != null && profile.username == null) {
                profile.username = username
            }
            val loginInfo = HashMap<String, String>()
            loginInfo["username"] = if (profile != null && profile.username != null)
                profile.username!! else username
            loginInfo["uid"] = if (profile != null && profile.uid != null) profile.uid!! else "0"
            loginInfo["avatarUrl"] = if (profile != null && profile.avatarUrl != null)
                profile.avatarUrl!! else ""
            loginInfo["level"] = if (profile != null && profile.level != null) profile.level!! else ""
            UserSessionManager.getInstance().saveLoginInfo(activity, loginInfo)
        } catch (ignored: Exception) {
        }

        runOnUi(activity) {
            setLoading(btnLogin, progressBar, false)
            if (dialog.isShowing) {
                dialog.dismiss()
            }
            listener?.onLoginSuccess()
        }
    }

    private fun setLoading(btn: MaterialButton?, pb: ProgressBar?, show: Boolean) {
        if (btn == null || pb == null) return
        pb.visibility = if (show) View.VISIBLE else View.GONE
        btn.isEnabled = !show
        btn.text = if (show) "" else btn.context.getString(R.string.action_login)
    }

    private fun showError(tv: TextView?, msg: String) {
        if (tv == null) return
        tv.text = msg
        tv.visibility = View.VISIBLE
    }

    private fun runOnUi(activity: Activity?, runnable: Runnable) {
        if (activity != null && !activity.isFinishing) {
            activity.runOnUiThread(runnable)
        }
    }
}
