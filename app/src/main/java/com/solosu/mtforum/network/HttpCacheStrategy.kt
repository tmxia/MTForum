package com.solosu.mtforum.network

import okhttp3.Interceptor
import okhttp3.Response









object HttpCacheStrategy {

    
    private val NO_CACHE = arrayOf(
        "mod=post",             
        "mod=swfupload",        
        "ac=avatar",            
        "action=login",         
        "logging",              
        "qiandao",              
        "op=delete",            
        "op=add",               
        "mod=report",           
        "mod=spacecp",          
        "mod=misc",             
        "misc.php",             
        "sendpm",               
        "chat",                 
    )

    
    private val CACHE_24H = arrayOf(
        "forum.php?mod=forumdisplay&",     
        "home.php?mod=space&uid=",          
        "home.php?mod=space&do=profile",    
        "home.php?mod=space&do=favorite",   
        "home.php?mod=follow&do=follower",  
        "home.php?mod=follow&do=following", 
        "home.php?mod=space&do=friend",     
    )

    
    private val CACHE_5M = arrayOf(
        "home.php?mod=space&do=thread",     
        "home.php?mod=space&do=notice",     
        "home.php?mod=space&do=pm",         
    )

    
    private val CACHE_60S = arrayOf(
        "mod=guide",            
        "forumlist",            
    )

    
    private val TIMESTAMP_PARAM = Regex(
        "[?&](_refresh|_load|_favorite_refresh|_favorite_remove_hash|_favorite_verify|_action_refresh|_t|_)=" +
        "\\d+"
    )

    
    fun maxAgeSeconds(url: String): Int? {
        
        for (p in NO_CACHE) if (url.contains(p)) return null

        
        if (url.contains("/thread-") || url.matches(Regex(".*thread-\\d+.*"))) return 300

        
        for (p in CACHE_24H) if (url.contains(p)) return 86400

        
        for (p in CACHE_5M) if (url.contains(p)) return 300

        
        for (p in CACHE_60S) if (url.contains(p)) return 60

        return null
    }

    
    fun shouldCache(url: String): Boolean = maxAgeSeconds(url) != null

    
    fun prefixesToEvictForWrite(url: String): List<String> {
        val out = ArrayList<String>()
        when {
            url.contains("action=newthread") -> {
                out.add("forum.php?mod=forumdisplay")
                out.add("forum.php?mod=guide")
                out.add("home.php?mod=space&do=thread")
            }
            url.contains("action=reply") || url.contains("action=edit") -> {
                out.add("thread-")
                out.add("forum.php?mod=forumdisplay")
                out.add("forum.php?mod=guide")
                out.add("home.php?mod=space&do=thread")
            }
            url.contains("op=delete") -> {
                out.add("thread-")
                out.add("forum.php?mod=forumdisplay")
                out.add("home.php?mod=space&do=thread")
                out.add("home.php?mod=space&do=favorite")
            }
            url.contains("ac=favorite") -> {
                out.add("home.php?mod=space&do=favorite")
            }
            url.contains("ac=avatar") || url.contains("ac=profile") -> {
                out.add("home.php?mod=space&uid=")
                out.add("home.php?mod=space&do=profile")
            }
            url.contains("qiandao") -> {
                out.add("home.php?mod=space&do=profile")
            }
        }
        return out
    }

    
    class CacheInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val method = request.method
            val url = request.url.toString()

            
            if (method != "GET") {
                try {
                    HttpClient.getInstance().evictByPrefixes(prefixesToEvictForWrite(url))
                } catch (_: Exception) {
                }
                return chain.proceed(request)
            }

            
            val hasTimestamp = TIMESTAMP_PARAM.containsMatchIn(url)
            val cleanUrl = if (hasTimestamp) {
                url.replace(TIMESTAMP_PARAM, "")
                    .replace("?&", "?")
                    .replace("&&", "&")
                    .trimEnd('?', '&')
            } else url

            val maxAge = maxAgeSeconds(cleanUrl)
            val newReq = request.newBuilder()

            if (hasTimestamp) {
                
                newReq.url(cleanUrl)
                newReq.header("Cache-Control", "no-cache")
            } else if (maxAge == null) {
                newReq.header("Cache-Control", "no-cache, no-store")
            } else {
                newReq.header("Cache-Control", "max-age=$maxAge")
            }
            return chain.proceed(newReq.build())
        }
    }
}
