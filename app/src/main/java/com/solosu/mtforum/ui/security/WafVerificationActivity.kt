package com.solosu.mtforum.ui.security

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

import com.solosu.mtforum.R
import com.solosu.mtforum.databinding.ActivityWafVerificationBinding
import com.solosu.mtforum.network.HttpClient
import com.solosu.mtforum.network.WafChallenge
import com.solosu.mtforum.util.ToastUtil as Toast











class WafVerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWafVerificationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        com.solosu.mtforum.util.UiStyleManager.applyTheme(this)
        com.solosu.mtforum.util.ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityWafVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_left)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnDone.setOnClickListener { syncBackAndFinish() }

        
        binding.root.post {
            if (isFinishing || (android.os.Build.VERSION.SDK_INT >= 17 && isDestroyed)) return@post
            setupAndLoadWebView()
        }
    }

    private fun setupAndLoadWebView() {
        
        HttpClient.getInstance().syncToCookieManager()

        val webView = binding.webView
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(webView, true)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        
        
        
        webView.settings.userAgentString = HttpClient.getInstance().getWafChallengeUserAgent()

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                binding.progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.progress.visibility = View.GONE
                onPageSettled(view)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                
                return false
            }
        }

        webView.loadUrl(HttpClient.BASE_URL)
    }

    
    private fun onPageSettled(view: WebView?) {
        val webView = view ?: return
        
        CookieManager.getInstance().flush()
        
        HttpClient.getInstance().syncFromCookieManager()
        webView.evaluateJavascript("document.cookie") { cookie ->
            if (isFinishing || (android.os.Build.VERSION.SDK_INT >= 17 && isDestroyed)) return@evaluateJavascript
            if (cookie != null && cookie.contains(WafChallenge.COOKIE_NAME)) {
                binding.tvStatus.text = "验证已通过，可返回继续使用"
                HttpClient.getInstance().clearWafChallenge()
            } else {
                binding.tvStatus.text = "若页面出现人机验证，请在此完成"
            }
        }
    }

    private fun syncBackAndFinish() {
        CookieManager.getInstance().flush()
        HttpClient.getInstance().syncFromCookieManager()
        Toast.makeText(this, "已同步验证结果", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        (binding.webView.parent as? ViewGroup)?.removeView(binding.webView)
        binding.webView.destroy()
        super.onDestroy()
    }

    companion object {
        @JvmStatic
        fun intent(context: android.content.Context): Intent {
            return Intent(context, WafVerificationActivity::class.java)
        }
    }
}
