package com.solosu.mtforum.network

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.webkit.CookieManager
import android.webkit.CookieSyncManager

import org.json.JSONArray
import org.json.JSONObject

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

import okhttp3.OkHttpClient
import okhttp3.Cache
import okhttp3.Request
import okhttp3.Response
import okhttp3.FormBody
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

import com.solosu.mtforum.util.AiLog

import java.util.ArrayList
import java.util.HashMap
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap





class HttpClient private constructor() {
    private var client: OkHttpClient
    private val cookieStore: MutableMap<String, MutableList<Cookie>>

    
    private var appContext: Context? = null

    

    










    
    private fun isWriteOperation(url: String): Boolean {
        return url.contains("ac=")
            || url.contains("op=blacklist")
            || url.contains("operation=")
            || url.contains("action=")
            || url.contains("loginsubmit")
            || url.contains("blacklistsubmit")
            || url.contains("fastloginfield")
    }

    
    private fun hasValidWafCookie(): Boolean {
        return try {
            val httpUrl = BASE_URL.toHttpUrlOrNull() ?: return false
            val cookies = client.cookieJar.loadForRequest(httpUrl)
            val now = System.currentTimeMillis()
            cookies.any {
                it.name == WafChallenge.COOKIE_NAME &&
                    (it.expiresAt == Long.MAX_VALUE || it.expiresAt > now)
            }
        } catch (e: Exception) {
            false
        }
    }

    
    private fun ensureWafCookie(): Boolean {
        if (hasValidWafCookie()) return true
        return try {
            if (DEBUG_WAF) AiLog.i("waf", "写前预热：cookie 不存在，探测中")
            val probe = Request.Builder()
                .url(BASE_URL + "home.php?mod=space&do=notice&mobile=2")
                .header("User-Agent", USER_AGENT)
                .header("Referer", BASE_URL)
                .header("X-Requested-With", "com.discuz.mobile")
                .get()
                .build()
            val body = executeBody(probe)
            if (WafChallenge.looksLikeChallenge(body)) {
                solveWafChallenge(probe.url.toString(), body)
            }
            hasValidWafCookie()
        } catch (e: Exception) {
            if (DEBUG_WAF) AiLog.e("waf", "预热失败: " + e.message)
            false
        }
    }

    private val lastWebViewPromptAt = java.util.concurrent.atomic.AtomicLong(0L)

    
    private fun triggerWebViewFallback(url: String) {
        val now = System.currentTimeMillis()
        val last = lastWebViewPromptAt.get()
        if (now - last < 60_000L) return
        if (!lastWebViewPromptAt.compareAndSet(last, now)) return

        val ctx = appContext ?: return
        try {
            val intent = com.solosu.mtforum.ui.security.WafVerificationActivity.intent(ctx)
            if (ctx !is android.app.Activity) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            if (DEBUG_WAF) AiLog.i("waf", "算法失效 → 启动 WebView 验证")
        } catch (e: Exception) {
            if (DEBUG_WAF) AiLog.e("waf", "启动 WebView 失败: " + e.message)
        }
    }

    private fun solveWafChallenge(url: String, body: String?): Boolean {
        if (!WafChallenge.looksLikeChallenge(body)) return false
        val value = WafChallenge.solveFromPage(body)
        if (value == null || value.isEmpty()) {
            if (DEBUG_WAF) {
                AiLog.e("waf", "命中挑战页但求解失败（算法可能已失配）url=$url")
            }
            
            triggerWebViewFallback(url)
            return false
        }
        val httpUrl = url.toHttpUrlOrNull() ?: return false
        try {
            val cookie = Cookie.Builder()
                .name(WafChallenge.COOKIE_NAME)
                .value(value)
                .domain(httpUrl.host)
                .path("/")
                .expiresAt(Long.MAX_VALUE)
                .build()
            
            client.cookieJar.saveFromResponse(
                httpUrl,
                Collections.singletonList(cookie)
            )
            if (appContext != null) commitCookieStore(appContext!!)
            if (DEBUG_WAF) {
                AiLog.i("waf", "已求解挑战 Cookie，重试请求 url=$url")
            }
            return true
        } catch (e: Exception) {
            if (DEBUG_WAF) AiLog.e("waf", "写入挑战 Cookie 失败: " + e.message)
            return false
        }
    }

    


    @Throws(IOException::class)
    private fun executeGetWithChallenge(request: Request): String {
        val body = executeBody(request)
        if (!WafChallenge.looksLikeChallenge(body)) {
            wafChallengePending.set(false)
            return body
        }
        
        
        
        RateLimiter.reportChallenge(request.url.toString(), isWrite = false)
        wafChallengePending.set(true)
        
        
        wafChallengeUa.set(request.header("User-Agent"))
        val url = request.url.toString()
        if (!solveWafChallenge(url, body)) return body
        val retry = request.newBuilder().build()
        val retried = executeBody(retry)
        if (!WafChallenge.looksLikeChallenge(retried)) wafChallengePending.set(false)
        return retried
    }

    
    fun hasPendingWafChallenge(): Boolean = wafChallengePending.get()

    



    fun getWafChallengeUserAgent(): String = wafChallengeUa.get() ?: USER_AGENT

    
    fun clearWafChallenge() {
        wafChallengePending.set(false)
    }

    
    @Throws(IOException::class)
    private fun executeBody(request: Request): String {
        client.newCall(request).execute().use { response ->
            val body = if (response.body != null) response.body!!.string() else ""
            if (appContext != null) commitCookieStore(appContext!!)
            return body
        }
    }

    
    private val pendingGets = ConcurrentHashMap<String, CompletableFuture<String>>()
    private val pendingPosts = ConcurrentHashMap<String, CompletableFuture<String>>()

    
    private val wafChallengePending = java.util.concurrent.atomic.AtomicBoolean(false)

    
    private val wafChallengeUa = java.util.concurrent.atomic.AtomicReference<String?>(null)

    init {
        cookieStore = HashMap()
        client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            
            .addInterceptor(RateLimiter.interceptor)
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    val host = url.host
                    var existing = cookieStore[host]
                    if (existing == null) {
                        existing = ArrayList()
                        cookieStore[host] = existing
                    }
                    for (cookie in cookies) {
                        var found = false
                        for (i in existing.indices) {
                            if (existing[i].name == cookie.name) {
                                existing[i] = cookie
                                found = true
                                break
                            }
                        }
                        if (!found) {
                            existing.add(cookie)
                        }
                    }
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    val host = url.host
                    val cookies = cookieStore[host]
                    return cookies ?: Collections.emptyList()
                }
            })
            .build()
    }

    


    @Throws(Exception::class)
    fun get(url: String): String {
        
        val existing = pendingGets[url]
        if (existing != null) {
            try {
                return existing.get()
            } catch (e: Exception) {
                
            }
        }
        val future = CompletableFuture<String>()
        val prev = pendingGets.putIfAbsent(url, future)
        if (prev != null) {
            try {
                return prev.get()
            } catch (e: Exception) {
                
            }
        }
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", HttpClient.BASE_URL)
                .header("X-Requested-With", "com.discuz.mobile")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .get()
                .build()
            val body = executeGetWithChallenge(request)
            future.complete(body)
            return body
        } catch (e: Exception) {
            future.completeExceptionally(e)
            throw e
        } finally {
            pendingGets.remove(url, future)
        }
    }

    


    @Throws(Exception::class)
    fun getBytes(url: String): ByteArray {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
                .header("Referer", HttpClient.BASE_URL)
                .header("X-Requested-With", "com.discuz.mobile")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            return if (response.body != null) response.body!!.bytes() else ByteArray(0)
        }
    }

    


    @Throws(Exception::class)
    fun getDesktop(url: String): String {
        
        val existing = pendingGets[url]
        if (existing != null) {
            try {
                return existing.get()
            } catch (e: Exception) {
                
            }
        }
        val future = CompletableFuture<String>()
        val prev = pendingGets.putIfAbsent(url, future)
        if (prev != null) {
            try {
                return prev.get()
            } catch (e: Exception) {
                
            }
        }
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .get()
                .build()
            val body = executeGetWithChallenge(request)
            future.complete(body)
            return body
        } catch (e: Exception) {
            future.completeExceptionally(e)
            throw e
        } finally {
            pendingGets.remove(url, future)
        }
    }

    


    @Throws(Exception::class)
    fun post(url: String, params: Map<String, String>?): String {
        if (isWriteOperation(url)) ensureWafCookie()
        
        val key = url + (if (params != null) params.toString() else "")
        val existing = pendingPosts[key]
        if (existing != null) {
            try {
                return existing.get()
            } catch (e: Exception) {
                
            }
        }
        val future = CompletableFuture<String>()
        val prev = pendingPosts.putIfAbsent(key, future)
        if (prev != null) {
            try {
                return prev.get()
            } catch (e: Exception) {
                
            }
        }
        try {
            val formBuilder = FormBody.Builder()
            if (params != null) {
                for ((k, v) in params) {
                    formBuilder.add(k, v)
                }
            }
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", HttpClient.BASE_URL)
                .header("X-Requested-With", "com.discuz.mobile")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(formBuilder.build())
                .build()
            val body = executeBody(request)
            
            
            
            if (WafChallenge.looksLikeChallenge(body)) {
                RateLimiter.reportChallenge(url, isWrite = true)
                wafChallengePending.set(true)
                wafChallengeUa.set(USER_AGENT)
                if (solveWafChallenge(url, body) && DEBUG_WAF) {
                    AiLog.e("waf", "POST 命中挑战页，已存 Cookie 但未重放（防重复提交）url=$url")
                }
            }
            future.complete(body)
            return body
        } catch (e: Exception) {
            future.completeExceptionally(e)
            throw e
        } finally {
            pendingPosts.remove(key, future)
        }
    }

    



    @Throws(Exception::class)
    fun postWithReferer(url: String, params: Map<String, String>?, referer: String?): String {
        if (isWriteOperation(url)) ensureWafCookie()
        val key = url + "@ref@" + (if (params != null) params.toString() else "")
        val existing = pendingPosts[key]
        if (existing != null) {
            try {
                return existing.get()
            } catch (e: Exception) {
                
            }
        }
        val future = CompletableFuture<String>()
        val prev = pendingPosts.putIfAbsent(key, future)
        if (prev != null) {
            try {
                return prev.get()
            } catch (e: Exception) {
                
            }
        }
        try {
            val formBuilder = FormBody.Builder()
            if (params != null) {
                for ((k, v) in params) {
                    formBuilder.add(k, v)
                }
            }
            val rb = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", HttpClient.BASE_URL)
                .header("X-Requested-With", "com.discuz.mobile")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Content-Type", "application/x-www-form-urlencoded")
            if (!android.text.TextUtils.isEmpty(referer)) rb.header("Referer", referer!!)
            val request = rb.post(formBuilder.build()).build()
            val body = executeBody(request)
            
            if (WafChallenge.looksLikeChallenge(body)) {
                RateLimiter.reportChallenge(url, isWrite = true)
                wafChallengePending.set(true)
                wafChallengeUa.set(USER_AGENT)
                if (solveWafChallenge(url, body) && DEBUG_WAF) {
                    AiLog.e("waf", "POST(带Referer) 命中挑战页，已存 Cookie 但未重放 url=$url")
                }
            }
            future.complete(body)
            return body
        } catch (e: Exception) {
            future.completeExceptionally(e)
            throw e
        } finally {
            pendingPosts.remove(key, future)
        }
    }

    



    @Throws(IOException::class)
    fun uploadFile(url: String, file: File, fieldName: String, extraFields: Map<String, String>?): String {
        return uploadFileWithUserAgent(url, file, fieldName, extraFields, USER_AGENT)
    }

    



    @Throws(IOException::class)
    fun uploadFileWithUserAgent(
        url: String, file: File, fieldName: String,
        extraFields: Map<String, String>?, userAgent: String?
    ): String {
        return uploadFileWithUserAgent(
            url, file, fieldName, extraFields, userAgent,
            guessContentType(file)
        )
    }

    




    @Throws(IOException::class)
    fun uploadFileWithUserAgent(
        url: String, file: File, fieldName: String,
        extraFields: Map<String, String>?, userAgent: String?,
        contentType: String?
    ): String {
        if (!file.isFile || file.length() <= 0) {
            throw IOException("上传文件不存在或为空")
        }

        
        
        if (!isLoggedIn()) syncFromCookieManager()
        val cookieHeader = getCookieHeader()

        val mediaType = (if (contentType == null || contentType.trim().isEmpty())
            "application/octet-stream" else contentType).toMediaTypeOrNull()
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(fieldName, file.name, file.asRequestBody(mediaType))

        if (extraFields != null) {
            for ((k, v) in extraFields) {
                if (k != null && v != null) {
                    builder.addFormDataPart(k, v)
                }
            }
        }

        val requestBody = builder.build()
        val requestBuilder = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                if (userAgent == null || userAgent.isEmpty()) USER_AGENT else userAgent
            )
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Referer", BASE_URL)
            .post(requestBody)
        
        if (cookieHeader.isNotEmpty()) {
            requestBuilder.header("Cookie", cookieHeader)
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val body = if (response.body != null) response.body!!.string() else ""
            if (appContext != null) {
                commitCookieStore(appContext!!)
            }
            if (!response.isSuccessful) {
                throw IOException("上传请求失败（HTTP " + response.code + "）")
            }
            return body
        }
    }

    




    @Throws(Exception::class)
    fun executeDirect(request: okhttp3.Request): okhttp3.Response {
        val response = client.newCall(request).execute()
        
        if (appContext != null) {
            commitCookieStore(appContext!!)
        }
        return response
    }

    


    fun getCookieValue(host: String, name: String): String? {
        val cookies = cookieStore[host]
        if (cookies != null) {
            for (c in cookies) {
                if (c.name == name) {
                    return c.value
                }
            }
        }
        return null
    }

    



    fun getCookieString(): String {
        val host = BASE_URL.toHttpUrlOrNull()!!.host
        val cookies = cookieStore[host]
        if (cookies == null || cookies.isEmpty()) return ""

        val sb = StringBuilder()
        for (c in cookies) {
            if (sb.length > 0) sb.append("; ")
            sb.append(c.name).append("=").append(c.value)
        }
        return sb.toString()
    }

    




    fun isLoggedIn(): Boolean {
        val host = BASE_URL.toHttpUrlOrNull()!!.host
        val cookies = cookieStore[host]
        if (cookies != null) {
            for (c in cookies) {
                if (c.name.endsWith("_auth") && !c.value.isEmpty()) {
                    return true
                }
            }
        }
        return false
    }

    
    @Synchronized
    fun getCookieHeader(): String {
        return getCookieString()
    }

    



    @Synchronized
    fun syncFromCookieManager() {
        try {
            val manager = CookieManager.getInstance()
            val urls = arrayOf(
                BASE_URL,
                "https://bbs.binmt.cc",
                "https://bbs.binmt.cc/",
                "http://bbs.binmt.cc/"
            )
            val merged = StringBuilder()
            for (url in urls) {
                val value = manager.getCookie(url)
                if (value == null || value.trim().isEmpty()) continue
                if (merged.length > 0) merged.append("; ")
                merged.append(value)
            }
            if (merged.length == 0) return

            val host = BASE_URL.toHttpUrlOrNull()!!.host
            var existing = cookieStore[host]
            if (existing == null) {
                existing = ArrayList()
                cookieStore[host] = existing
            }
            val pairs = merged.toString().split(";\\s*".toRegex(), 0).toTypedArray()
            for (pair in pairs) {
                val eq = pair.indexOf('=')
                if (eq <= 0) continue
                val name = pair.substring(0, eq).trim()
                val value = pair.substring(eq + 1).trim()
                if (name.isEmpty() || value.isEmpty()) continue
                var old: Cookie? = null
                for (c in existing) {
                    if (c.name == name) {
                        old = c
                        break
                    }
                }
                val b = Cookie.Builder().name(name).value(value)
                    .domain(host).path("/").expiresAt(Long.MAX_VALUE)
                if (old != null && old.secure) b.secure()
                var replaced = false
                for (i in existing.indices) {
                    if (existing[i].name == name) {
                        existing[i] = b.build()
                        replaced = true
                        break
                    }
                }
                if (!replaced) existing.add(b.build())
            }
            if (appContext != null) commitCookieStore(appContext!!)
        } catch (ignored: Exception) {
        }
    }

    



    @Synchronized
    fun syncToCookieManager() {
        try {
            val webViewCookieMgr = CookieManager.getInstance()
            val cookies = getCookieString()
            if (cookies == null || cookies.isEmpty()) return
            val pairs = cookies.split(";\\s*".toRegex(), 0).toTypedArray()
            for (pair in pairs) {
                if (pair == null || pair.trim().isEmpty()) continue
                webViewCookieMgr.setCookie(BASE_URL, pair.trim())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                webViewCookieMgr.flush()
            } else {
                CookieSyncManager.getInstance().sync()
            }
        } catch (ignored: Exception) {
            
        }
    }

    




    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        this.appContext = context
        restoreCookieStore(context)

        
        try {
            rebuildClientWithCache(context.applicationContext)
        } catch (e: Exception) {
            android.util.Log.e("HttpClient", "cache init failed", e)
        }

        syncFromCookieManager()
    }
    @Synchronized
    fun commitCookieStore(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val cookiesArray = JSONArray()

            for ((host, value) in cookieStore) {
                for (cookie in value) {
                    val obj = JSONObject()
                    obj.put("host", host)
                    obj.put("name", cookie.name)
                    obj.put("value", cookie.value)
                    obj.put("domain", cookie.domain)
                    obj.put("path", cookie.path)
                    obj.put("expiresAt", cookie.expiresAt)
                    obj.put("secure", cookie.secure)
                    obj.put("httpOnly", cookie.httpOnly)
                    obj.put("persistent", cookie.persistent)
                    if (cookie.persistent) {
                        
                        cookiesArray.put(obj)
                    } else {
                        
                        cookiesArray.put(obj)
                    }
                }
            }

            prefs.edit().putString(KEY_COOKIES, cookiesArray.toString()).apply()
        } catch (ignored: Exception) {
            
        }
    }

    



    @Synchronized
    fun restoreCookieStore(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_COOKIES, null)
            if (json == null || json.isEmpty()) return

            val cookiesArray = JSONArray(json)
            cookieStore.clear()

            for (i in 0 until cookiesArray.length()) {
                val obj = cookiesArray.getJSONObject(i)
                val host = obj.optString("host", BASE_URL.toHttpUrlOrNull()!!.host)

                val builder = Cookie.Builder()
                    .name(obj.getString("name"))
                    .value(obj.getString("value"))
                    .domain(obj.optString("domain", BASE_URL.toHttpUrlOrNull()!!.host))
                    .path(obj.optString("path", "/"))
                    .expiresAt(obj.optLong("expiresAt", Long.MAX_VALUE))

                if (obj.optBoolean("secure")) {
                    builder.secure()
                }
                if (obj.optBoolean("httpOnly")) {
                    
                }

                val cookie = builder.build()

                var cookies = cookieStore[host]
                if (cookies == null) {
                    cookies = ArrayList()
                    cookieStore[host] = cookies
                }
                cookies.add(cookie)
            }
        } catch (ignored: Exception) {
            
        }
    }

    


    




    @Synchronized
    fun applyCookiesFromString(cookieString: String?) {
        if (cookieString == null || cookieString.trim().isEmpty()) return
        val host = BASE_URL.toHttpUrlOrNull()!!.host
        var existing = cookieStore[host]
        if (existing == null) {
            existing = ArrayList()
            cookieStore[host] = existing
        }
        val pairs = cookieString.split(";\\s*".toRegex(), 0).toTypedArray()
        for (pair in pairs) {
            val eq = pair.indexOf('=')
            if (eq <= 0) continue
            val name = pair.substring(0, eq).trim()
            val value = pair.substring(eq + 1).trim()
            if (name.isEmpty()) continue
            val b = Cookie.Builder().name(name).value(value)
                .domain(host).path("/").expiresAt(Long.MAX_VALUE)
            var replaced = false
            for (i in existing.indices) {
                if (existing[i].name == name) {
                    existing[i] = b.build()
                    replaced = true
                    break
                }
            }
            if (!replaced) existing.add(b.build())
        }
    }

    fun clearCookies() {
        cookieStore.clear()
    }

    



    fun clearCookies(context: Context) {
        cookieStore.clear()
        try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_COOKIES).apply()
        } catch (ignored: Exception) {
        }
        
        try {
            val cm = android.webkit.CookieManager.getInstance()
            cm.removeAllCookies(null)
            cm.flush()
        } catch (ignored: Exception) {
        }
    }

    companion object {
        @JvmField
        val BASE_URL = "https://bbs.binmt.cc/"
        @JvmField
        val MOBILE_SUFFIX = "&mobile=2"
        @JvmField
        val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        @JvmField
        val DESKTOP_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        @Volatile
        private var instance: HttpClient? = null

        private const val PREF_NAME = "sqapp_cookies"
        private const val KEY_COOKIES = "cookies_json"

        @Volatile
        private var initialized = false

        
        private const val DEBUG_WAF = true

        @JvmStatic
        fun getInstance(): HttpClient {
            val i = instance
            if (i == null) {
                synchronized(HttpClient::class.java) {
                    if (instance == null) {
                        instance = HttpClient()
                    }
                }
            }
            return instance!!
        }

        private fun guessContentType(file: File?): String {
            val name = if (file == null) "" else file.name.lowercase(java.util.Locale.ROOT)
            if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg"
            if (name.endsWith(".png")) return "image/png"
            if (name.endsWith(".gif")) return "image/gif"
            if (name.endsWith(".webp")) return "image/webp"
            if (name.endsWith(".bmp")) return "image/bmp"
            if (name.endsWith(".heic") || name.endsWith(".heif")) return "image/heic"
            return "application/octet-stream"
        }
    }


    
    
    
    private var currentUid: String = "anon"

    private fun rebuildClientWithCache(context: Context) {
        val uid = try {
            com.solosu.mtforum.session.UserSessionManager
                .getInstance().getUid(context).let {
                    if (it.isNullOrEmpty()) "anon" else it
                }
        } catch (_: Exception) { "anon" }
        currentUid = uid

        val cacheDir = java.io.File(context.cacheDir, "http_cache_" + uid)
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val cache = Cache(cacheDir, 50L * 1024 * 1024)  

        client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .cache(cache)
            .addInterceptor(HttpCacheStrategy.CacheInterceptor())
            .addInterceptor(RateLimiter.interceptor)
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    val host = url.host
                    var existing = cookieStore[host]
                    if (existing == null) {
                        existing = ArrayList()
                        cookieStore[host] = existing
                    }
                    for (cookie in cookies) {
                        var found = false
                        for (i in existing.indices) {
                            if (existing[i].name == cookie.name) {
                                existing[i] = cookie
                                found = true
                                break
                            }
                        }
                        if (!found) existing.add(cookie)
                    }
                }
                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    val cookies = cookieStore[url.host]
                    return cookies ?: java.util.Collections.emptyList()
                }
            })
            .build()
        android.util.Log.i("HttpClient", "HTTP cache 已启用, uid=$uid, dir=$cacheDir")
    }

    
    fun evictCacheByPrefix(prefix: String) {
        
        try { client.cache?.evictAll() } catch (_: Exception) {}
    }

    fun evictByPrefixes(prefixes: List<String>) {
        for (p in prefixes) evictCacheByPrefix(p)
    }

    
    fun evictAllCache() {
        try { client.cache?.evictAll() } catch (_: Exception) {}
    }

    
    fun switchAccount(context: Context) {
        evictAllCache()
        try { rebuildClientWithCache(context.applicationContext) } catch (_: Exception) {}
    }

    
    fun preconnect() {
        try {
            val host = BASE_URL.toHttpUrlOrNull() ?: return
            val conn = client.connectionPool
            
            @Suppress("UNUSED_EXPRESSION")
            conn
            
            java.lang.Thread {
                try {
                    val req = okhttp3.Request.Builder()
                        .url(BASE_URL + "misc.php?mod=seccode")
                        .head()
                        .build()
                    client.newCall(req).execute().close()
                } catch (_: Exception) {
                }
            }.start()
        } catch (_: Exception) {
        }
    }

}
